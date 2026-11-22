package com.example.drivenext.presentation.main

import android.net.Uri
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import com.example.drivenext.R
import com.example.drivenext.data.auth.LocalAccountRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class ProfileScreen(private val ui: MainUi, private val account: LocalAccountRepository) {
    fun settings(open: (String) -> Unit) = with(ui) {
        column().apply {
            setPadding(dp(14), 0, dp(14), 0)
            gap(this, 23)
            addView(title("Настройки", 87))
            val profile = row().apply {
                addView(avatar(false), size(74, 74))
                addView(column().apply {
                    addView(label(account.name, 15f, purple, true))
                    gap(this, 7)
                    addView(label(account.email, 12f, muted))
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(20) })
                addView(icon(R.drawable.lab3_chevron), size(22, 22))
                setOnClickListener { open("Профиль") }
            }
            addView(profile, size(-1, 76))
            gap(this, 23)
            preference(this, "Мои бронирования", R.drawable.lab3_booking, open)
            gap(this, 17)
            preference(this, "Тема", R.drawable.lab3_theme)
            preference(this, "Уведомления", R.drawable.lab3_bell)
            preference(this, "Подключить свой автомобиль", R.drawable.lab3_connect_car, open)
            gap(this, 5)
            rule(this, pale)
            gap(this, 4)
            preference(this, "Помощь", R.drawable.lab3_help)
            preference(this, "Пригласи друга", R.drawable.lab3_invite)
        }
    }

    fun profile(pickAvatar: () -> Unit, signOut: () -> Unit) = with(ui) {
        column().apply {
            addView(title("Профиль", 70))
            gap(this, 19)
            addView(avatar(true).apply {
                contentDescription = "Загрузить аватар"
                setOnClickListener { pickAvatar() }
            }, size(103, 103).apply { gravity = Gravity.CENTER_HORIZONTAL })
            gap(this, 16)
            addView(label(account.name, 20f, bold = true).apply { gravity = Gravity.CENTER }, size(-1, -2))
            gap(this, 5)
            if (account.joinedAt != 0L) {
                val date = SimpleDateFormat("LLLL yyyy", Locale.forLanguageTag("ru")).format(Date(account.joinedAt))
                addView(label("Дата регистрации · $date", 14f, muted).apply { gravity = Gravity.CENTER }, size(-1, -2))
            }
            gap(this, 31)
            field(this, "Электронная почта", account.email)
            field(this, "Пароль", "Поменять пароль", purple)
            field(this, "Пол", account.gender)
            field(this, "Google", "—")
            addView(label("Выйти из профиля", 14f).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(21), 0, dp(21), 0)
                setOnClickListener { signOut() }
            }, size(-1, 53))
        }
    }

    private fun avatar(profile: Boolean): ImageView = with(ui) {
        val image = icon(if (profile) R.drawable.lab3_avatar else R.drawable.lab3_avatar_empty)
        account.avatar?.let {
            image.background = box(white, 100, purple)
            image.clipToOutline = true
            image.scaleType = ImageView.ScaleType.CENTER_CROP
            image.setImageURI(Uri.fromFile(it))
        }
        image
    }

    private fun preference(
        parent: LinearLayout, title: String, icon: Int, open: ((String) -> Unit)? = null
    ) = with(ui) {
        parent.addView(row().apply {
            addView(icon(icon), size(26, 26))
            addView(label(title, 15f, purple), LinearLayout.LayoutParams(0, -2, 1f).apply {
                leftMargin = dp(5)
            })
            addView(icon(R.drawable.lab3_chevron), size(22, 22))
            if (open != null) setOnClickListener { open(title) }
        }, size(-1, 52))
    }

    private fun field(parent: LinearLayout, title: String, value: String, color: Int = ui.muted) = with(ui) {
        parent.addView(column().apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(21), 0, dp(21), 0)
            addView(label(title, 14f))
            gap(this, 5)
            addView(label(value, 12f, color))
        }, size(-1, 72))
        rule(parent)
    }
}
