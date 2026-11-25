package com.example.drivenext.presentation.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.addCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.os.bundleOf
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.drivenext.R
import com.example.drivenext.data.auth.AccountRepository
import com.example.drivenext.data.booking.BookingRepository
import com.example.drivenext.data.catalog.CarRepository
import com.example.drivenext.data.cloud.Supabase
import com.example.drivenext.domain.catalog.Car
import com.example.drivenext.presentation.auth.AuthActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : AppCompatActivity() {
    private enum class Page {
        HOME, RESULTS, FAVORITES, SETTINGS, PROFILE, BOOKINGS, BOOKING_DETAIL, ADD_CAR, DETAILS, RENT, SUCCESS
    }

    private val model by viewModels<CatalogViewModel>()
    private val account by lazy { AccountRepository(applicationContext) }
    private val ui by lazy { MainUi(this) }
    private val profile by lazy { ProfileScreen(ui, account) }
    private val bookings by lazy { BookingRepository(account) }
    private val bookingScreen by lazy { BookingScreen(this, ui) }
    private val detailsScreen by lazy { CarDetailsScreen(this, ui) }
    private val cars = CarRepository()
    private val adapter = CarAdapter(::openCar)
    private val preferences by lazy { getSharedPreferences("catalog", MODE_PRIVATE) }
    private val favoritePrefs by lazy { getSharedPreferences("favorites", MODE_PRIVATE) }
    private val favoriteKey by lazy { "${Supabase.url}/${account.id}" }
    private val favorites get() = favoritePrefs.getStringSet(favoriteKey, emptySet()).orEmpty()
    private var route = listOf(Page.HOME)
    private val page get() = route.last()
    private var query = ""
    private var car: Car? = null
    private var dates = BookingScreen.Dates()
    private var bookingId = 0L
    private var typing: Job? = null
    private var pageRequest: Job? = null
    private var avatarRequest: Job? = null
    private var avatarLoaded = false
    private var bookingPending = false
    private lateinit var body: FrameLayout
    private lateinit var navigation: LinearLayout
    private var catalog: FrameLayout? = null

    private val avatarPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && page == Page.PROFILE) loadAvatar(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!account.hasSession()) {
            openLogin()
            return
        }
        query = savedInstanceState?.getString("draft") ?: preferences.getString("query", "").orEmpty()
        route = savedInstanceState?.getStringArrayList("route")?.map(Page::valueOf)
            ?: if (query.isBlank()) listOf(Page.HOME) else listOf(Page.HOME, Page.RESULTS)
        bookingId = savedInstanceState?.getLong("booking_id") ?: 0
        savedInstanceState?.getBundle("selected_car")?.let {
            car = Car(it.getInt("id"), it.getString("model").orEmpty(), it.getString("brand").orEmpty(),
                it.getInt("price"), it.getString("transmission").orEmpty(), it.getString("fuel").orEmpty())
        }
        savedInstanceState?.getString("rent_start")?.let { start ->
            val end = savedInstanceState.getString("rent_end") ?: return@let
            dates = BookingScreen.Dates(LocalDate.parse(start), LocalDate.parse(end))
        }
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(ui.column().apply {
            setBackgroundColor(ui.white)
            isFocusableInTouchMode = true
            body = FrameLayout(this@MainActivity)
            addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
            navigation = ui.row()
            addView(navigation, ui.size(-1, 55))
            requestFocus()
        })
        onBackPressedDispatcher.addCallback(this) { back() }
        lifecycleScope.launch {
            model.state.collect { state -> catalog?.let { paintCars(it, state) } }
        }
        navigate(route)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val selected = car?.takeIf { page == Page.DETAILS || page == Page.RENT }?.let {
            bundleOf("id" to it.id, "model" to it.model, "brand" to it.brand,
                "price" to it.price, "transmission" to it.transmission, "fuel" to it.fuel)
        }
        outState.putAll(bundleOf("route" to ArrayList(route.map(Page::name)), "draft" to query,
            "booking_id" to bookingId, "selected_car" to selected,
            "rent_start" to dates.start.toString(), "rent_end" to dates.end.toString()))
        super.onSaveInstanceState(outState)
    }

    private fun open(next: Page) = navigate(route + next)
    private fun back() = when {
        route.size == 1 -> finish()
        page == Page.SUCCESS -> switchTab(Page.HOME)
        else -> navigate(route.dropLast(1))
    }
    private fun switchTab(next: Page) {
        if (next == Page.HOME) query = ""
        navigate(listOf(Page.HOME, next).distinct())
    }

    private fun navigate(next: List<Page>) {
        typing?.cancel()
        pageRequest?.cancel()
        route = next
        when (page) {
            Page.HOME -> {
                preferences.edit().remove("query").apply()
                model.load("")
            }
            Page.RESULTS -> model.load(query)
            Page.SETTINGS, Page.PROFILE -> loadAvatar()
            else -> Unit
        }
        render()
    }

    private fun render(): Unit = with(ui) {
        body.removeAllViews()
        catalog = null
        window.statusBarColor = if (page == Page.HOME) ui.lavender else ui.white
        drawNavigation()
        when (page) {
            Page.HOME, Page.RESULTS -> {
                catalog = carList().also { paintCars(it, model.state.value) }
            }
            Page.FAVORITES -> loadScreen("Избранное", null, {
                cars.favorites(favorites.mapNotNull(String::toIntOrNull).toSet())
            }) { paintCars(carList(), CatalogViewModel.State(cars = it, loading = false), "Нет избранных автомобилей") }
            Page.SETTINGS -> scroll(profile.settings { setting ->
                open(when (setting) {
                    "Профиль" -> Page.PROFILE
                    "Мои бронирования" -> Page.BOOKINGS
                    else -> Page.ADD_CAR
                })
            })
            Page.PROFILE -> scroll(profile.profile({
                avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }, ::signOut))
            Page.DETAILS -> car?.let { loadScreen("Детали", ::back, { cars.details(it.id) }, ::showDetails) }
            Page.RENT -> car?.let { selected -> body.addView(bookingScreen.rental(selected, dates, ::back, {
                dates = it
                render()
            }) { rent(selected) }) }
            Page.SUCCESS -> body.addView(bookingScreen.success({ switchTab(Page.HOME) }) { open(Page.BOOKINGS) })
            Page.BOOKINGS -> loadScreen("Мои бронирования", ::back, bookings::list) { items ->
                scroll(bookingScreen.bookings(items, ::back) { id ->
                    bookingId = id
                    open(Page.BOOKING_DETAIL)
                })
            }
            Page.BOOKING_DETAIL -> showBooking()
            Page.ADD_CAR -> scroll(column().apply {
                setPadding(dp(20), 0, dp(20), dp(16))
                addView(title("Добавление автомобиля", 54, ::back))
                gap(this, 12)
                addView(label("Марка, модель и характеристики", 16f))
                for (field in listOf("Марка", "Модель", "Цена в день")) {
                    addView(EditText(this@MainActivity).apply { hint = field }, size(-1, 50))
                }
            })
        }
        Unit
    }

    private fun rent(selected: Car) {
        val chosen = dates
        changeBooking("Не удалось подтвердить бронирование.") { previous ->
            bookings.book(selected, chosen.start, chosen.end)
            if (route === previous && dates == chosen) navigate(listOf(Page.HOME, Page.SUCCESS))
        }
    }

    private fun showBooking() = loadScreen("Бронирование # $bookingId", ::back, {
        bookings.details(bookingId) to bookings.driver()
    }) { (booking, driver) ->
        body.addView(bookingScreen.details(booking, driver, ::back) {
            changeBooking("Не удалось отменить бронирование.") { previous ->
                val cancelled = bookings.cancel(booking.id)
                if (route === previous) back()
                check(cancelled) { "Бронирование уже завершилось или было удалено." }
            }
        })
    }

    private fun changeBooking(error: String, action: suspend (List<Page>) -> Unit) {
        if (bookingPending) return
        bookingPending = true
        val previous = route
        request({ notice((it as? IllegalStateException)?.message ?: error) }) {
            try {
                action(previous)
            } finally {
                bookingPending = false
            }
        }
    }

    private fun openCar(selected: Car, rent: Boolean) {
        car = selected
        dates = BookingScreen.Dates()
        open(if (rent) Page.RENT else Page.DETAILS)
    }

    private fun showDetails(selected: Car) {
        car = selected
        val key = selected.id.toString()
        val saved = key in favorites
        body.removeAllViews()
        body.addView(detailsScreen.show(selected, saved, ::back, {
            val updated = if (saved) favorites - key else favorites + key
            if (favoritePrefs.edit().putStringSet(favoriteKey, updated).commit()) showDetails(selected)
            else notice("Не удалось обновить избранное. Попробуйте снова.")
        }, {
            dates = BookingScreen.Dates()
            open(Page.RENT)
        }))
    }

    private fun loadAvatar(uri: Uri? = null) {
        if (uri == null && (avatarLoaded || avatarRequest?.isActive == true)) return
        avatarRequest?.cancel()
        avatarRequest = request({ if (uri != null) notice("Не удалось загрузить фото.") }) {
            if (uri != null) account.saveAvatar(uri)
            profile.photo = account.avatar()
            avatarLoaded = true
            if (page == Page.PROFILE || page == Page.SETTINGS) render()
        }
    }

    private fun signOut() {
        avatarRequest?.cancel()
        request({ notice("Сессия на устройстве завершена.") }) {
            try {
                account.signOut()
            } finally {
                preferences.edit().clear().apply()
                if (!isDestroyed) openLogin()
            }
        }    }

    private fun openLogin() {
        startActivity(Intent(this, AuthActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    private fun request(fail: (Exception) -> Unit, action: suspend () -> Unit) = lifecycleScope.launch {
        try {
            action()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            fail(error)
        }
    }

    private fun notice(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun <T> loadScreen(name: String, back: (() -> Unit)?, load: suspend () -> T, show: (T) -> Unit) {
        val panel = area(ui.title(name, 60, back))
        panel.addView(feedback())
        val previous = route
        pageRequest = request({
            panel.removeAllViews()
            panel.addView(feedback("Не удалось загрузить данные. Попробуйте снова.", ::render))
        }) {
            val result = load()
            if (route === previous) {
                body.removeAllViews()
                show(result)
            }
        }
    }

    private fun feedback(message: String? = null, retry: (() -> Unit)? = null) = with(ui) {
        column().apply {
            gravity = Gravity.CENTER
            setPadding(dp(22), 0, dp(22), 0)
            if (message == null) addView(ProgressBar(this@MainActivity), size(40, 40))
            else addView(label(message, 16f).apply { gravity = Gravity.CENTER })
            if (retry != null) {
                gap(this, 16)
                addView(button("Повторить", action = retry), size(160, 42))
            }
        }
    }

    private fun drawNavigation() = with(ui) {
        navigation.removeAllViews()
        val currentTab = route.getOrNull(1) ?: Page.HOME
        navigation.visibility = when (page) {
            Page.RENT, Page.BOOKINGS, Page.BOOKING_DETAIL, Page.SUCCESS -> View.GONE
            else -> View.VISIBLE
        }
        navigation.setBackgroundColor(white)
        navigation.elevation = dp(5).toFloat()
        navigation.setPadding(dp(44), 0, dp(44), 0)
        val active = if (currentTab == Page.RESULTS) Page.HOME else currentTab
        nav(Page.HOME, "Главная", R.drawable.lab3_home_outline, R.drawable.lab3_home_filled, active)
        nav(Page.FAVORITES, "Избранное", R.drawable.lab3_favorite, R.drawable.lab4_favorite_filled, active)
        nav(Page.SETTINGS, "Настройки", R.drawable.lab3_settings_outline, R.drawable.lab3_settings_filled, active)
    }

    private fun nav(tab: Page, name: String, outline: Int, filled: Int, active: Page) = with(ui) {
        val item = icon(if (tab == active) filled else outline).apply {
            contentDescription = name
            isSelected = tab == active
            setPadding(0, dp(15), 0, dp(15))
            setOnClickListener { switchTab(tab) }
        }
        navigation.addView(item, LinearLayout.LayoutParams(0, -1, 1f))
    }

    private fun carList() = with(ui) {
        val header = when (page) {
            Page.HOME -> searchHeader()
            Page.FAVORITES -> title("Избранное", 76)
            else -> column().apply {
                gap(this, 18)
                addView(title("Результаты поиска", 76, ::back))
            }
        }
        area(header).apply {
            addView(RecyclerView(this@MainActivity).apply {
                layoutManager = LinearLayoutManager(this@MainActivity)
                adapter = this@MainActivity.adapter
                clipToPadding = false
                setPadding(dp(20), dp(if (page == Page.HOME) 32 else 0), dp(20), 0)
            })
        }
    }

    private fun paintCars(panel: FrameLayout, state: CatalogViewModel.State, empty: String = "Автомобили не найдены") {
        adapter.show(state.cars)
        if (panel.childCount > 1) panel.removeViewAt(1)
        when {
            state.loading -> panel.addView(feedback())
            state.error != null -> panel.addView(feedback(state.error) { model.load(state.query) })
            state.cars.isEmpty() -> panel.addView(feedback(empty))
        }
    }

    private fun searchHeader() = with(ui) {
        column().apply {
            setBackgroundColor(lavender)
            setPadding(dp(20), dp(56), dp(20), dp(27))
            addView(SearchView(this@MainActivity).apply {
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
                findViewById<View>(androidx.appcompat.R.id.search_plate).background = null
                setQuery(this@MainActivity.query, false)
                clearFocus()
                setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(value: String) = search(value, wait = false)
                    override fun onQueryTextChange(value: String) = search(value, wait = true)
                })
            }, size(-1, 45))
            gap(this, 39)
            addView(label("Давайте найдем автомобиль", 17f, bold = true))
        }
    }

    private fun search(value: String, wait: Boolean): Boolean {
        val typed = value.trim()
        query = typed
        typing?.cancel()
        if (typed.isNotEmpty()) typing = lifecycleScope.launch {
            if (wait) delay(850)
            getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(body.windowToken, 0)
            preferences.edit().putString("query", typed).apply()
            open(Page.RESULTS)
        }
        return true
    }

    private fun area(header: View) = FrameLayout(this).also { content ->
        body.addView(ui.column().apply {
            addView(header)
            addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        })
    }

    private fun scroll(content: View) = body.addView(ScrollView(this).apply { addView(content) })
}
