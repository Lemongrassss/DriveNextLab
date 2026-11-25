package com.example.drivenext.presentation.main

import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.example.drivenext.R
import com.example.drivenext.domain.catalog.Car

internal class CarAdapter(private val open: (Car, Boolean) -> Unit) :
    RecyclerView.Adapter<CarAdapter.Holder>() {
    private var cars = emptyList<Car>()

    fun show(items: List<Car>) {
        if (cars == items) return
        cars = items
        notifyDataSetChanged()
    }

    override fun getItemCount() = cars.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(MainUi(parent.context))
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(cars[position])

    inner class Holder(ui: MainUi) : RecyclerView.ViewHolder(ui.column()) {
        private val card = itemView as LinearLayout
        private val model = ui.label("", 17f, bold = true)
        private val brand = ui.label("", 13f, ui.muted)
        private val price = ui.label("", 14f, bold = true)
        private val transmission = ui.label("", 12f, ui.muted)
        private val fuel = ui.label("", 12f, ui.muted)
        private val photo = ui.icon(R.drawable.lab3_mercedes).apply { contentDescription = "Автомобиль" }
        private lateinit var car: Car

        init {
            with(ui) {
                card.layoutParams = RecyclerView.LayoutParams(-1, dp(264)).apply { bottomMargin = dp(16) }
                card.background = box(white, 8, ink, 2)
                card.setPadding(dp(24), dp(24), dp(24), dp(24))

                val top = FrameLayout(card.context)
                card.addView(top, LinearLayout.LayoutParams(-1, 0, 1f))
                top.addView(photo, FrameLayout.LayoutParams(dp(174), dp(71), Gravity.END or Gravity.TOP).apply {
                    topMargin = dp(36)
                })
                val info = column()
                top.addView(info, FrameLayout.LayoutParams(-1, -2).apply { topMargin = dp(17) })
                info.addView(model)
                info.addView(brand)
                gap(info, 9)
                info.addView(row().apply {
                    addView(price)
                    addView(label(" в день", 14f, pale, true))
                })
                gap(info, 22)
                info.addView(row().apply {
                    addView(icon(R.drawable.lab3_gearbox), size(18, 18))
                    addView(transmission)
                    addView(icon(R.drawable.lab3_fuel), size(18, 18).apply { leftMargin = dp(14) })
                    addView(fuel)
                })
                card.addView(row().apply {
                    addView(button("Забронировать") { open(car, true) },
                        LinearLayout.LayoutParams(0, dp(42), 1f))
                    addView(button("Детали", false) { open(car, false) },
                        LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(16) })
                })
            }
        }

        fun bind(value: Car) {
            car = value
            photo.setImageResource(carImage(car))
            model.text = car.model
            brand.text = car.brand
            price.text = "${car.price}₽"
            transmission.text = " ${car.transmission}"
            fuel.text = " ${car.fuel}"
        }
    }
}
