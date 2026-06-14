package com.example.ludo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/**
 * Renders a Ludo-King style dice face (1-6) with rounded body and pips.
 * Set `value` to 0 to show an empty/idle die.
 */
class DiceView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var value: Int = 0
        set(v) {
            field = v
            invalidate()
        }

    private val bodyPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val borderPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#5D4037")
    }
    private val pipPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.parseColor("#37474F")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val size = Math.min(w, h)
        val pad = size * 0.06f
        val left = (w - size) / 2 + pad
        val top = (h - size) / 2 + pad
        val right = left + size - 2 * pad
        val bottom = top + size - 2 * pad
        val radius = size * 0.16f

        // glossy gradient body
        val shader = LinearGradient(
            left, top, right, bottom,
            Color.parseColor("#FFFFFF"), Color.parseColor("#E0E0E0"),
            Shader.TileMode.CLAMP
        )
        bodyPaint.shader = shader
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, bodyPaint)
        bodyPaint.shader = null
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, borderPaint)

        if (value in 1..6) {
            drawPips(canvas, left, top, right - left, value)
        }
    }

    private fun drawPips(canvas: Canvas, left: Float, top: Float, size: Float, value: Int) {
        val r = size * 0.07f
        // 3x3 grid positions (fractions of size)
        val c = 0.5f
        val lft = 0.22f
        val rgt = 0.78f
        val topY = 0.22f
        val midY = 0.5f
        val botY = 0.78f

        fun pip(fx: Float, fy: Float) {
            canvas.drawCircle(left + size * fx, top + size * fy, r, pipPaint)
        }

        when (value) {
            1 -> pip(c, midY)
            2 -> { pip(lft, topY); pip(rgt, botY) }
            3 -> { pip(lft, topY); pip(c, midY); pip(rgt, botY) }
            4 -> { pip(lft, topY); pip(rgt, topY); pip(lft, botY); pip(rgt, botY) }
            5 -> { pip(lft, topY); pip(rgt, topY); pip(c, midY); pip(lft, botY); pip(rgt, botY) }
            6 -> { pip(lft, topY); pip(rgt, topY); pip(lft, midY); pip(rgt, midY); pip(lft, botY); pip(rgt, botY) }
        }
    }
}
