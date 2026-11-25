package com.example.drivenext.presentation.main

import android.content.Context
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.example.drivenext.R
import com.example.drivenext.domain.catalog.Car

internal const val CAR_ADDRESS = "Авиамоторная ул., 8, стр. 2"

internal fun carImage(car: Car) =
    if (car.brand.equals("Tesla", ignoreCase = true)) R.drawable.lab4_tesla else R.drawable.lab3_mercedes

internal class CarDetailsScreen(private val context: Context, private val ui: MainUi) {
    fun show(car: Car, favorite: Boolean, back: () -> Unit, toggle: () -> Unit, rent: () -> Unit) = with(ui) {
        column().apply {
            addView(title("Детали", 60, back).apply {
                val icon = if (favorite) R.drawable.lab4_heart_filled else R.drawable.lab4_heart
                addView(icon(icon).apply {
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    contentDescription = if (favorite) "Удалить из избранного" else "Добавить в избранное"
                    setOnClickListener { toggle() }
                }, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.END or Gravity.CENTER_VERTICAL))
            })
            val content = column().apply {
                addView(icon(carImage(car)).apply {
                    if (carImage(car) == R.drawable.lab4_tesla) scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription = "${car.brand} ${car.model}"
                }, size(-1, 258))
                addView(column().apply {
                    setPadding(dp(20), dp(16), dp(20), dp(16))
                    addView(label("${car.brand} ${car.model}", 20f, bold = true))
                    gap(this, 14)
                    rule(this)
                    gap(this, 16)
                    addView(label("Адрес нахождения", 13f, muted))
                    gap(this, 8)
                    addView(row().apply {
                        addView(icon(R.drawable.lab4_location), size(23, 23))
                        addView(label(CAR_ADDRESS, 14f), size(-2, -2).apply { leftMargin = dp(10) })
                    })
                    gap(this, 14)
                    rule(this)
                    gap(this, 16)
                    addView(label("Описание", 17f, bold = true))
                    gap(this, 12)
                    val description = if (car.brand.equals("Tesla", ignoreCase = true)) {
                        "Tesla Model 3 — электрический седан. В карточке указаны год выпуска и стоимость аренды за день."
                    } else {
                        "${car.brand} ${car.model}. Трансмиссия: ${car.transmission}. Топливо: ${car.fuel}."
                    }
                    addView(label(description, 15f).apply { setLineSpacing(dp(5).toFloat(), 1f) })
                })
            }
            addView(ScrollView(context).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(row().apply {
                setPadding(dp(20), dp(8), dp(20), dp(8))
                addView(label("${car.price} ₽/день", 14f, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
                addView(button("Забронировать", action = rent), size(174, 44))
            }, size(-1, 60))
        }
    }
}
