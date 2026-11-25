package com.example.drivenext.presentation.main

import android.app.DatePickerDialog
import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import com.example.drivenext.R
import com.example.drivenext.data.booking.BookingRepository
import com.example.drivenext.domain.catalog.Car
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

internal class BookingScreen(private val context: Context, private val ui: MainUi) {
    data class Dates(
        val start: LocalDate = LocalDate.now().plusDays(1),
        val end: LocalDate = start.plusDays(1)
    )

    private val dateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru"))
    private val rentalTime = LocalTime.of(8, 0)

    fun rental(
        car: Car, dates: Dates, back: () -> Unit, changeDates: (Dates) -> Unit, confirm: () -> Unit
    ) = with(ui) {
        column().apply {
            addView(title("Оформление аренды", 60, back))
            val content = column().apply {
                setPadding(dp(20), dp(8), dp(20), dp(16))
                addView(row().apply {
                    background = box(white, 8, ink, 2)
                    setPadding(dp(16), 0, dp(16), 0)
                    addView(column().apply {
                        addView(label(car.model, 16f, bold = true))
                        addView(label(car.brand, 13f, muted))
                        gap(this, 5)
                        addView(label("${car.price} ₽ в день", 14f, purple))
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                    addView(icon(R.drawable.lab3_mercedes), LinearLayout.LayoutParams(0, dp(80), 1f))
                }, size(-1, 166))
                gap(this, 10)
                addView(infoRow("Начало аренды", format(dates.start)).apply {
                    setOnClickListener {
                        pickDate(dates.start, LocalDate.now().plusDays(1)) { start ->
                            changeDates(Dates(start, maxOf(dates.end, start.plusDays(1))))
                        }
                    }
                })
                addView(infoRow("Конец аренды", format(dates.end)).apply {
                    setOnClickListener {
                        pickDate(dates.end, dates.start.plusDays(1)) { end ->
                            changeDates(dates.copy(end = end))
                        }
                    }
                })
                rule(this)
                gap(this, 10)
                addView(label("Авиамоторная ул., 8, стр. 2", 14f))
                gap(this, 4)
                addView(label("Адрес нахождения", 12f, muted))
                gap(this, 10)
                rule(this)
                val days = ChronoUnit.DAYS.between(dates.start, dates.end)
                addView(infoRow("Аренда автомобиля", "${car.price * days} ₽"))
                addView(infoRow("Страховка", "${300 * days} ₽"))
                addView(column().apply {
                    background = box(lavender, 8)
                    setPadding(dp(12), dp(4), dp(12), dp(4))
                    addView(infoRow("Итого", "${(car.price + 300L) * days} ₽"))
                    addView(infoRow("Возвращаемый депозит", "15 000 ₽"))
                })
            }
            addView(ScrollView(context).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(button("Забронировать", action = confirm), size(-1, 48).apply {
                setMargins(dp(20), dp(8), dp(20), dp(16))
            })
        }
    }

    fun bookings(entries: List<BookingRepository.Booking>, back: () -> Unit) = with(ui) {
        column().apply {
            setPadding(dp(20), 0, dp(20), dp(16))
            addView(title("Мои бронирования", 60, back))
            if (entries.isEmpty()) addView(label("У вас пока нет бронирований", 15f, muted))
            val now = LocalDateTime.now()
            for (entry in entries.asReversed()) {
                val completed = !entry.end.atTime(rentalTime).isAfter(now)
                gap(this, 12)
                addView(label(entry.name, 16f, bold = true))
                gap(this, 5)
                val status = if (completed) "Аренда завершена, ${entry.end.format(dateFormat)}"
                    else "Начало аренды: ${format(entry.start)}"
                addView(label(status, 13f, muted))
                gap(this, 12)
                rule(this)
            }
        }
    }

    private fun infoRow(title: String, value: String) = with(ui) {
        row().apply {
            minimumHeight = dp(36)
            gravity = Gravity.CENTER_VERTICAL
            addView(label(title, 13f), LinearLayout.LayoutParams(0, -2, 1f))
            addView(label(value, 13f, purple))
        }
    }

    private fun format(date: LocalDate) = "$rentalTime, ${date.format(dateFormat)}"

    private fun pickDate(initial: LocalDate, minimum: LocalDate, onPick: (LocalDate) -> Unit) {
        val current = maxOf(initial, minimum)
        DatePickerDialog(context, { _, year, month, day ->
            onPick(LocalDate.of(year, month + 1, day))
        }, current.year, current.monthValue - 1, current.dayOfMonth).apply {
            datePicker.minDate = minimum.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.show()
    }
}
