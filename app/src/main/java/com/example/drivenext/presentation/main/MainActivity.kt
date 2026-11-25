package com.example.drivenext.presentation.main

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.drivenext.R
import com.example.drivenext.data.auth.AccountRepository
import com.example.drivenext.data.booking.BookingRepository
import com.example.drivenext.domain.catalog.Car
import com.example.drivenext.presentation.auth.AuthActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : AppCompatActivity() {
    private enum class Page { HOME, RESULTS, FAVORITES, SETTINGS, PROFILE, BOOKINGS, ADD_CAR, DETAILS, RENT }

    private val model by viewModels<CatalogViewModel>()
    private val account by lazy { AccountRepository(applicationContext) }
    private val preferences by lazy { getSharedPreferences("catalog", MODE_PRIVATE) }
    private val ui by lazy { MainUi(this) }
    private val profile by lazy { ProfileScreen(ui, account) }
    private val bookings by lazy { BookingRepository(account) }
    private val bookingScreen by lazy { BookingScreen(this, ui) }
    private var page = Page.HOME
    private var query = ""
    private var typing: Job? = null
    private var carPageFrom = Page.HOME
    private var selectedCar: Car? = null
    private var rentalDates = BookingScreen.Dates()
    private lateinit var body: FrameLayout
    private lateinit var navigation: LinearLayout
    private var catalog: FrameLayout? = null
    private var list: RecyclerView? = null
    private var message: LinearLayout? = null
    private val adapter = CarAdapter(::openCar)
    private var avatarLoaded = false
    private var avatarRequest: Job? = null
    private var bookingPending = false
    private val avatarPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && page == Page.PROFILE) {
            avatarRequest?.cancel()
            avatarRequest = cloud("Не удалось загрузить фото.") {
                account.saveAvatar(uri)
                profile.photo = account.avatar()
                avatarLoaded = true
                if (page == Page.PROFILE || page == Page.SETTINGS) render()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!account.hasSession()) {
            openLogin()
            return
        }
        query = savedInstanceState?.getString("draft") ?: preferences.getString("query", "").orEmpty()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        page = savedInstanceState?.getString("page")?.let(Page::valueOf)
            ?: if (query.isBlank()) Page.HOME else Page.RESULTS
        carPageFrom = savedInstanceState?.getString("car_page_from")?.let(Page::valueOf) ?: Page.HOME
        selectedCar = savedInstanceState?.getBundle("selected_car")?.let {
            Car(it.getInt("id"), it.getString("model").orEmpty(), it.getString("brand").orEmpty(),
                it.getInt("price"), it.getString("transmission").orEmpty(), it.getString("fuel").orEmpty())
        }
        savedInstanceState?.getString("rent_start")?.let { start ->
            val end = savedInstanceState.getString("rent_end") ?: return@let
            rentalDates = BookingScreen.Dates(LocalDate.parse(start), LocalDate.parse(end))
        }
        if ((page == Page.DETAILS || page == Page.RENT) && selectedCar == null) {
            page = if (query.isBlank()) Page.HOME else Page.RESULTS
        }
        setContentView(ui.column().apply {
            setBackgroundColor(ui.white)
            isFocusableInTouchMode = true
            body = FrameLayout(this@MainActivity)
            addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
            navigation = ui.row()
            addView(navigation, ui.size(-1, 55))
            requestFocus()
        })
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = back()
        })
        lifecycleScope.launch { model.state.collect { showCatalog(it) } }
        select(page)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("page", page.name)
        outState.putString("draft", query)
        outState.putString("car_page_from", carPageFrom.name)
        selectedCar?.let { car ->
            outState.putBundle("selected_car", Bundle().apply {
                putInt("id", car.id)
                putString("model", car.model)
                putString("brand", car.brand)
                putInt("price", car.price)
                putString("transmission", car.transmission)
                putString("fuel", car.fuel)
            })
            outState.putString("rent_start", rentalDates.start.toString())
            outState.putString("rent_end", rentalDates.end.toString())
        }
        super.onSaveInstanceState(outState)
    }

    private fun select(next: Page) {
        typing?.cancel()
        page = next
        if (next == Page.PROFILE || next == Page.SETTINGS) loadAvatar()
        if (next == Page.HOME || next == Page.RESULTS) {
            if (next == Page.HOME) preferences.edit().remove("query").apply()
            model.load(if (next == Page.HOME) "" else query)
        }
        render()
    }

    private fun render() {
        body.removeAllViews()
        catalog = null
        list = null
        message = null
        window.statusBarColor = if (page == Page.HOME) ui.lavender else ui.white
        navigation.visibility = if (page == Page.RENT || page == Page.BOOKINGS) View.GONE else View.VISIBLE
        drawNavigation()
        when (page) {
            Page.HOME, Page.RESULTS -> renderCatalog()
            Page.SETTINGS -> scroll(profile.settings { setting ->
                select(when (setting) {
                    "Профиль" -> Page.PROFILE
                    "Мои бронирования" -> Page.BOOKINGS
                    else -> Page.ADD_CAR
                })
            })
            Page.PROFILE -> scroll(profile.profile({
                avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }, {
                avatarRequest?.cancel()
                lifecycleScope.launch {
                    try {
                        account.signOut()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        Toast.makeText(this@MainActivity, "Сессия на устройстве завершена.", Toast.LENGTH_SHORT).show()
                    }
                    preferences.edit().clear().apply()
                    openLogin()
                }
            }))
            Page.FAVORITES -> renderDestination("Избранное", "Нет избранных автомобилей")
            Page.BOOKINGS -> renderBookings()
            Page.ADD_CAR -> renderDestination("Добавление автомобиля", "Марка, модель и характеристики", addCar = true)
            Page.DETAILS -> selectedCar?.let { car ->
                renderDestination("Детали",
                    "${car.brand} ${car.model}\n${car.price} ₽ в день\n${car.transmission} · ${car.fuel}", car = true)
            }
            Page.RENT -> selectedCar?.let { car ->
                val screen = bookingScreen.rental(car, rentalDates, ::back, { dates ->
                    rentalDates = dates
                    render()
                }, {
                    if (!bookingPending) {
                        bookingPending = true
                        val dates = rentalDates
                        cloud("Не удалось подтвердить бронирование.") {
                            try {
                                bookings.book(car, dates.start, dates.end)
                                if (page == Page.RENT && selectedCar == car) select(Page.BOOKINGS)
                            } finally {
                                bookingPending = false
                            }
                        }
                    }
                })
                body.addView(screen, FrameLayout.LayoutParams(-1, -1))
            }
        }
    }

    private fun loadAvatar() {
        if (avatarLoaded || avatarRequest?.isActive == true) return
        avatarRequest = lifecycleScope.launch {
            try {
                profile.photo = account.avatar()
                avatarLoaded = true
                if (page == Page.PROFILE || page == Page.SETTINGS) render()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A temporary connection error leaves the default avatar visible.
            }
        }
    }

    private fun renderBookings() = with(ui) {
        val content = column().apply {
            setPadding(dp(20), 0, dp(20), 0)
            addView(title("Мои бронирования", 60, ::back))
            addView(ProgressBar(this@MainActivity), size(40, 40).apply { gravity = Gravity.CENTER_HORIZONTAL })
        }
        scroll(content)
        lifecycleScope.launch {
            try {
                val items = bookings.list()
                if (content.isAttachedToWindow) {
                    body.removeAllViews()
                    scroll(bookingScreen.bookings(items, ::back))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (content.isAttachedToWindow) {
                    content.removeViewAt(1)
                    content.addView(label("Не удалось загрузить бронирования.", 15f))
                    gap(content, 12)
                    content.addView(button("Повторить", action = ::render), size(-1, 48))
                }
            }
        }
    }

    private fun cloud(message: String, action: suspend () -> Unit) = lifecycleScope.launch {
        try {
            action()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val text = if (error is IllegalStateException) error.message ?: message else message
            Toast.makeText(this@MainActivity, text, Toast.LENGTH_SHORT).show()
        }
    }

    private fun drawNavigation() = with(ui) {
        navigation.removeAllViews()
        navigation.setBackgroundColor(white)
        navigation.elevation = dp(5).toFloat()
        navigation.setPadding(dp(44), 0, dp(44), 0)
        val home = page in listOf(Page.HOME, Page.RESULTS, Page.DETAILS, Page.RENT)
        val settings = page in listOf(Page.SETTINGS, Page.PROFILE, Page.BOOKINGS, Page.ADD_CAR)
        navItem("Главная", if (home) R.drawable.lab3_home_filled else R.drawable.lab3_home_outline, home) {
            query = ""
            select(Page.HOME)
        }
        navItem("Избранное", R.drawable.lab3_favorite, page == Page.FAVORITES) { select(Page.FAVORITES) }
        navItem("Настройки", if (settings) R.drawable.lab3_settings_filled else R.drawable.lab3_settings_outline, settings) {
            select(Page.SETTINGS)
        }
    }

    private fun navItem(title: String, icon: Int, selected: Boolean, action: () -> Unit) = with(ui) {
        val item = FrameLayout(this@MainActivity).apply {
            contentDescription = title
            isSelected = selected
            setOnClickListener { action() }
            addView(icon(icon), FrameLayout.LayoutParams(dp(25), dp(25), Gravity.CENTER))
        }
        navigation.addView(item, LinearLayout.LayoutParams(0, -1, 1f))
    }

    private fun renderCatalog() = with(ui) {
        val content = column()
        body.addView(content, FrameLayout.LayoutParams(-1, -1))
        if (page == Page.HOME) content.addView(searchHeader())
        else {
            gap(content, 18)
            content.addView(title("Результаты поиска", 76, ::back))
        }

        catalog = FrameLayout(this@MainActivity).also {
            content.addView(it, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        list = RecyclerView(this@MainActivity).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
            clipToPadding = false
            setPadding(dp(20), dp(if (page == Page.HOME) 32 else 0), dp(20), 0)
        }
        catalog?.addView(list, FrameLayout.LayoutParams(-1, -1))
        showCatalog(model.state.value)
    }

    private fun searchHeader() = with(ui) {
        column().apply {
            setBackgroundColor(lavender)
            setPadding(dp(20), dp(56), dp(20), dp(27))
            val search = SearchView(this@MainActivity).apply {
                isIconified = false
                setIconifiedByDefault(false)
                queryHint = "Введите марку автомобиля"
                background = box(white, 9)
                elevation = dp(4).toFloat()
                findViewById<EditText>(androidx.appcompat.R.id.search_src_text).apply {
                    textSize = 15f
                    setTextColor(ink)
                    setHintTextColor(pale)
                }
                findViewById<ImageView>(androidx.appcompat.R.id.search_mag_icon).apply {
                    setImageResource(R.drawable.lab3_search)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    layoutParams = layoutParams.apply {
                        width = dp(24)
                        height = dp(24)
                    }
                }
                findViewById<android.view.View>(androidx.appcompat.R.id.search_plate).background = null
                setQuery(this@MainActivity.query, false)
                clearFocus()
                setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(value: String): Boolean {
                        startSearch(value)
                        return true
                    }

                    override fun onQueryTextChange(value: String): Boolean {
                        typing?.cancel()
                        this@MainActivity.query = value.trim()
                        if (value.isNotBlank()) typing = lifecycleScope.launch {
                            delay(850)
                            startSearch(value)
                        }
                        return true
                    }
                })
            }
            addView(search, size(-1, 45))
            gap(this, 39)
            addView(label("Давайте найдем автомобиль", 17f, bold = true))
        }
    }

    private fun startSearch(value: String) {
        query = value.trim()
        if (query.isBlank()) return
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(body.windowToken, 0)
        preferences.edit().putString("query", query).apply()
        select(Page.RESULTS)
    }

    private fun showCatalog(state: CatalogViewModel.State) = with(ui) {
        val frame = catalog ?: return@with
        adapter.show(state.cars)
        message?.let { frame.removeView(it) }
        if (!state.loading && state.error == null && state.cars.isNotEmpty()) return@with
        message = column().apply {
            gravity = Gravity.CENTER
            setPadding(dp(22), 0, dp(22), 0)
            if (state.loading) addView(ProgressBar(this@MainActivity), size(40, 40))
            else {
                addView(label(state.error ?: "Автомобили не найдены", 16f).apply { gravity = Gravity.CENTER })
                if (state.error != null) {
                    gap(this, 16)
                    addView(button("Повторить") { model.load(state.query) }, size(160, 42))
                }
            }
        }
        frame.addView(message, FrameLayout.LayoutParams(-1, -1))
    }

    private fun openCar(car: Car, rent: Boolean) {
        carPageFrom = page
        selectedCar = car
        rentalDates = BookingScreen.Dates()
        select(if (rent) Page.RENT else Page.DETAILS)
    }

    private fun renderDestination(title: String, text: String, car: Boolean = false, addCar: Boolean = false) = with(ui) {
        scroll(column().apply {
            setPadding(dp(20), 0, dp(20), dp(16))
            addView(title(title, 54, if (page == Page.FAVORITES) null else ::back))
            if (car) addView(icon(R.drawable.lab3_mercedes), size(-1, 160))
            gap(this, 12)
            addView(label(text, 16f))
            if (addCar) listOf("Марка", "Модель", "Цена в день").forEach { hint ->
                addView(EditText(this@MainActivity).apply { this.hint = hint }, size(-1, 50))
            }
        })
    }

    private fun scroll(content: LinearLayout) {
        body.addView(ScrollView(this).apply { addView(content) }, FrameLayout.LayoutParams(-1, -1))
    }

    private fun back() {
        when (page) {
            Page.PROFILE, Page.BOOKINGS, Page.ADD_CAR -> select(Page.SETTINGS)
            Page.DETAILS, Page.RENT -> select(carPageFrom)
            Page.HOME -> finish()
            else -> select(Page.HOME)
        }
    }

    private fun openLogin() {
        startActivity(Intent(this, AuthActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }
}
