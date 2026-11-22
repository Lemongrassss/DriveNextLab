package com.example.drivenext.presentation.main

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

internal class MainUi(private val context: Context) {
    val purple = Color.rgb(43, 19, 70)
    val ink = Color.rgb(22, 28, 40)
    val muted = Color.rgb(110, 113, 120)
    val pale = Color.rgb(211, 216, 227)
    val lavender = Color.rgb(247, 243, 253)
    val white = Color.WHITE

    fun dp(value: Int) = if (value < 0) value else
        (value * context.resources.displayMetrics.density).roundToInt()

    fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    fun row() = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    fun size(width: Int, height: Int) = LinearLayout.LayoutParams(dp(width), dp(height))

    fun label(value: CharSequence, size: Float, color: Int = ink, bold: Boolean = false) =
        TextView(context).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
        }

    fun icon(drawable: Int) = ImageView(context).apply {
        setImageResource(drawable)
        scaleType = ImageView.ScaleType.FIT_CENTER
    }

    fun box(fill: Int, radius: Int, stroke: Int? = null, width: Int = 1) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(width), stroke)
    }

    fun gap(parent: LinearLayout, height: Int) = parent.addView(View(context), size(1, height))

    fun rule(parent: LinearLayout, color: Int = pale) {
        parent.addView(View(context).apply { setBackgroundColor(color) }, LinearLayout.LayoutParams(-1, 1))
    }

    fun button(title: String, filled: Boolean = true, action: () -> Unit) =
        label(title, 15f, if (filled) white else purple).apply {
            gravity = Gravity.CENTER
            minHeight = dp(40)
            background = if (filled) box(purple, 8) else box(white, 8, purple)
            setOnClickListener { action() }
        }

    fun title(title: String, height: Int, back: (() -> Unit)? = null) = FrameLayout(context).apply {
        addView(label(title, 18f, bold = true).apply { gravity = Gravity.CENTER },
            FrameLayout.LayoutParams(-1, -1))
        if (back != null) {
            addView(label("‹", 29f).apply {
                gravity = Gravity.CENTER
                contentDescription = "Назад"
                setOnClickListener { back() }
            }, FrameLayout.LayoutParams(dp(48), dp(height), Gravity.START or Gravity.CENTER_VERTICAL))
        }
        layoutParams = size(-1, height)
    }
}
