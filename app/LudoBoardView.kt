package com.example.ludo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class LudoBoardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var state: GameLogic.State? = null
    var onTokenTapped: ((player: Int, tokenIdx: Int) -> Unit)? = null

    // Board base colors
    private val boardBg = Color.parseColor("#FFF8E1")      // warm cream background
    private val pathCellColor = Color.parseColor("#FFFFFF") // white path cells
    private val gridLineColor = Color.parseColor("#BCAAA4")

    private val gridPaint = Paint().apply {
        color = gridLineColor
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }
    private val bgPaint = Paint().apply { color = boardBg; style = Paint.Style.FILL; isAntiAlias = true }
    private val pathPaint = Paint().apply { color = pathCellColor; style = Paint.Style.FILL; isAntiAlias = true }
    private val tokenBorder = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }
    private val tokenShadow = Paint().apply {
        color = Color.parseColor("#33000000")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val tokenFill = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }

    private var cell = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cell = Math.min(w, h) / 15f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (cell == 0f) cell = Math.min(width, height) / 15f

        // overall background
        canvas.drawRect(0f, 0f, 15 * cell, 15 * cell, bgPaint)

        // four colored corner yards (6x6) with rounded inner panel + 4 token circles
        drawYard(canvas, 0, 0, GameLogic.COLOR_HEX[0])    // Red top-left
        drawYard(canvas, 0, 9, GameLogic.COLOR_HEX[1])    // Green top-right
        drawYard(canvas, 9, 9, GameLogic.COLOR_HEX[2])    // Yellow bottom-right
        drawYard(canvas, 9, 0, GameLogic.COLOR_HEX[3])    // Blue bottom-left

        // main path cells (white)
        for (cellPos in GameLogic.MAIN_PATH) {
            drawCellRect(canvas, cellPos[0], cellPos[1], pathPaint)
        }

        // colored entry/start squares with arrow marker
        drawStartSquares(canvas)

        // safe-cell stars
        for (idx in GameLogic.SAFE_CELLS) {
            val c = GameLogic.MAIN_PATH[idx]
            drawStar(canvas, c[0], c[1])
        }

        // home columns (colored stripes leading to center)
        for (p in 0 until 4) {
            val paint = Paint().apply {
                color = Color.parseColor(GameLogic.COLOR_HEX[p])
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            for (c in GameLogic.HOME_COLUMNS[p]) {
                drawCellRect(canvas, c[0], c[1], paint)
            }
        }

        // center home triangle (4 colored triangles meeting in middle)
        drawCenterHome(canvas)

        // grid lines only on the cross-shaped play area (not inside yards)
        drawGridLines(canvas)

        // tokens
        val st = state ?: return
        for (p in 0 until st.numPlayers) {
            for (t in 0 until GameLogic.TOKENS_PER_PLAYER) {
                val pos = st.positions[p][t]
                val rc = GameLogic.cellForPosition(p, t, pos)
                drawToken(canvas, rc[0], rc[1], p, t, pos)
            }
        }
    }

    // ---------------- Yard (colored corner with circular token slots) ----------------
    private fun drawYard(canvas: Canvas, rowStart: Int, colStart: Int, colorHex: String) {
        val color = Color.parseColor(colorHex)
        val outer = Paint().apply { this.color = color; style = Paint.Style.FILL; isAntiAlias = true }
        val left = colStart * cell
        val top = rowStart * cell
        val size = 6 * cell
        canvas.drawRect(left, top, left + size, top + size, outer)

        // inner white rounded panel
        val inner = Paint().apply { this.color = Color.WHITE; style = Paint.Style.FILL; isAntiAlias = true }
        val pad = size * 0.14f
        val radius = size * 0.12f
        canvas.drawRoundRect(left + pad, top + pad, left + size - pad, top + size - pad, radius, radius, inner)

        // 4 circular slots for tokens, arranged 2x2
        val slotPaint = Paint().apply { this.color = color; alpha = 70; style = Paint.Style.FILL; isAntiAlias = true }
        val innerSize = size - 2 * pad
        val slotRadius = innerSize * 0.16f
        val positions = arrayOf(
            floatArrayOf(0.27f, 0.27f), floatArrayOf(0.73f, 0.27f),
            floatArrayOf(0.27f, 0.73f), floatArrayOf(0.73f, 0.73f)
        )
        for (pos in positions) {
            val cx = left + pad + innerSize * pos[0]
            val cy = top + pad + innerSize * pos[1]
            canvas.drawCircle(cx, cy, slotRadius, slotPaint)
            val ring = Paint().apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true }
            canvas.drawCircle(cx, cy, slotRadius, ring)
        }
    }

    private fun drawCellRect(canvas: Canvas, row: Int, col: Int, paint: Paint) {
        canvas.drawRect(col * cell, row * cell, (col + 1) * cell, (row + 1) * cell, paint)
    }

    // ---------------- Start squares with directional arrow ----------------
    private fun drawStartSquares(canvas: Canvas) {
        // For each player, the start cell on MAIN_PATH gets colored + arrow
        val arrowDir = arrayOf(
            "right", // Red start arrow points right (entering horizontal path)
            "down",  // Green start arrow points down
            "left",  // Yellow start arrow points left
            "up"     // Blue start arrow points up
        )
        for (p in 0 until 4) {
            val idx = GameLogic.START_INDEX[p]
            val c = GameLogic.MAIN_PATH[idx]
            val paint = Paint().apply {
                color = Color.parseColor(GameLogic.COLOR_HEX[p])
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            drawCellRect(canvas, c[0], c[1], paint)
            drawArrow(canvas, c[0], c[1], arrowDir[p])
        }
    }

    private fun drawArrow(canvas: Canvas, row: Int, col: Int, dir: String) {
        val cx = (col + 0.5f) * cell
        val cy = (row + 0.5f) * cell
        val size = cell * 0.28f
        val path = Path()
        when (dir) {
            "right" -> {
                path.moveTo(cx - size, cy - size)
                path.lineTo(cx + size, cy)
                path.lineTo(cx - size, cy + size)
            }
            "left" -> {
                path.moveTo(cx + size, cy - size)
                path.lineTo(cx - size, cy)
                path.lineTo(cx + size, cy + size)
            }
            "down" -> {
                path.moveTo(cx - size, cy - size)
                path.lineTo(cx, cy + size)
                path.lineTo(cx + size, cy - size)
            }
            "up" -> {
                path.moveTo(cx - size, cy + size)
                path.lineTo(cx, cy - size)
                path.lineTo(cx + size, cy + size)
            }
        }
        path.close()
        val paint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL; isAntiAlias = true }
        canvas.drawPath(path, paint)
    }

    // ---------------- Safe cell star marker ----------------
    private fun drawStar(canvas: Canvas, row: Int, col: Int) {
        val cx = (col + 0.5f) * cell
        val cy = (row + 0.5f) * cell
        val outerR = cell * 0.32f
        val innerR = cell * 0.14f
        val path = Path()
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outerR else innerR
            val angle = Math.toRadians((i * 36 - 90).toDouble())
            val x = cx + r * Math.cos(angle).toFloat()
            val y = cy + r * Math.sin(angle).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        val paint = Paint().apply { color = Color.parseColor("#9E9E9E"); alpha = 110; style = Paint.Style.FILL; isAntiAlias = true }
        canvas.drawPath(path, paint)
    }

    // ---------------- Center home triangle (4 colored triangles + crown) ----------------
    private fun drawCenterHome(canvas: Canvas) {
        val left = 6 * cell
        val top = 6 * cell
        val size = 3 * cell
        val cx = left + size / 2f
        val cy = top + size / 2f

        // Red triangle (top)
        drawTri(canvas, left, top, left + size, top, cx, cy, GameLogic.COLOR_HEX[0])
        // Green triangle (right)
        drawTri(canvas, left + size, top, left + size, top + size, cx, cy, GameLogic.COLOR_HEX[1])
        // Yellow triangle (bottom)
        drawTri(canvas, left + size, top + size, left, top + size, cx, cy, GameLogic.COLOR_HEX[2])
        // Blue triangle (left)
        drawTri(canvas, left, top + size, left, top, cx, cy, GameLogic.COLOR_HEX[3])

        // outline
        val outline = Paint().apply { color = Color.parseColor("#5D4037"); style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true }
        canvas.drawRect(left, top, left + size, top + size, outline)
    }

    private fun drawTri(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, colorHex: String) {
        val path = Path()
        path.moveTo(x1, y1)
        path.lineTo(x2, y2)
        path.lineTo(x3, y3)
        path.close()
        val paint = Paint().apply { color = Color.parseColor(colorHex); style = Paint.Style.FILL; isAntiAlias = true }
        canvas.drawPath(path, paint)
    }

    // ---------------- Grid lines only inside cross-shaped play area ----------------
    private fun drawGridLines(canvas: Canvas) {
        // vertical lines: columns 6,7,8,9 across rows 0-15 ; and full width across rows 6-9
        for (col in 6..9) {
            canvas.drawLine(col * cell, 0f, col * cell, 15 * cell, gridPaint)
        }
        for (row in 6..9) {
            canvas.drawLine(0f, row * cell, 15 * cell, row * cell, gridPaint)
        }
        // horizontal arm columns 0-6 and 9-15, rows 6-9
        for (col in 0..6) {
            canvas.drawLine(col * cell, 6 * cell, col * cell, 9 * cell, gridPaint)
        }
        for (col in 9..15) {
            canvas.drawLine(col * cell, 6 * cell, col * cell, 9 * cell, gridPaint)
        }
        for (row in 0..6) {
            canvas.drawLine(6 * cell, row * cell, 9 * cell, row * cell, gridPaint)
        }
        for (row in 9..15) {
            canvas.drawLine(6 * cell, row * cell, 9 * cell, row * cell, gridPaint)
        }
        // outer border
        val border = Paint().apply { color = Color.parseColor("#5D4037"); style = Paint.Style.STROKE; strokeWidth = 5f; isAntiAlias = true }
        canvas.drawRect(2f, 2f, 15 * cell - 2f, 15 * cell - 2f, border)
    }

    // ---------------- Token rendering: glossy 3D-style ball ----------------
    private fun drawToken(canvas: Canvas, row: Int, col: Int, player: Int, tokenIdx: Int, pos: Int) {
        val offsets = if (pos == -1) {
            // tokens sit in their yard slot positions (matches drawYard layout)
            arrayOf(
                floatArrayOf(0.27f, 0.27f), floatArrayOf(0.73f, 0.27f),
                floatArrayOf(0.27f, 0.73f), floatArrayOf(0.73f, 0.73f)
            )
        } else {
            arrayOf(
                floatArrayOf(0.3f, 0.3f), floatArrayOf(0.7f, 0.3f),
                floatArrayOf(0.3f, 0.7f), floatArrayOf(0.7f, 0.7f)
            )
        }
        val off = offsets[tokenIdx % 4]

        val cx: Float
        val cy: Float
        val radius: Float

        if (pos == -1) {
            // place inside the yard's inner white panel area
            val size = 6 * cell
            val pad = size * 0.14f
            val innerSize = size - 2 * pad
            val left = when (player) {
                0 -> 0f
                1 -> 9 * cell
                2 -> 9 * cell
                else -> 0f
            }
            val top = when (player) {
                0 -> 0f
                1 -> 0f
                2 -> 9 * cell
                else -> 9 * cell
            }
            cx = left + pad + innerSize * off[0]
            cy = top + pad + innerSize * off[1]
            radius = innerSize * 0.13f
        } else {
            cx = (col + off[0]) * cell
            cy = (row + off[1]) * cell
            radius = cell * 0.30f
        }

        // shadow
        canvas.drawCircle(cx + radius * 0.15f, cy + radius * 0.2f, radius, tokenShadow)

        // glossy gradient fill
        val baseColor = Color.parseColor(GameLogic.COLOR_HEX[player])
        val lightColor = lighten(baseColor, 0.5f)
        val shader = RadialGradient(
            cx - radius * 0.3f, cy - radius * 0.3f, radius * 1.6f,
            lightColor, baseColor, Shader.TileMode.CLAMP
        )
        tokenFill.shader = shader
        canvas.drawCircle(cx, cy, radius, tokenFill)
        tokenFill.shader = null

        // white border
        canvas.drawCircle(cx, cy, radius, tokenBorder)

        // small highlight dot
        val highlight = Paint().apply { color = Color.parseColor("#55FFFFFF"); style = Paint.Style.FILL; isAntiAlias = true }
        canvas.drawCircle(cx - radius * 0.35f, cy - radius * 0.35f, radius * 0.28f, highlight)
    }

    private fun lighten(color: Int, factor: Float): Int {
        val r = Color.red(color) + ((255 - Color.red(color)) * factor).toInt()
        val g = Color.green(color) + ((255 - Color.green(color)) * factor).toInt()
        val b = Color.blue(color) + ((255 - Color.blue(color)) * factor).toInt()
        return Color.rgb(r.coerceAtMost(255), g.coerceAtMost(255), b.coerceAtMost(255))
    }

    // ---------------- Touch handling ----------------
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true
        val st = state ?: return true
        val col = (event.x / cell).toInt()
        val row = (event.y / cell).toInt()

        for (p in 0 until st.numPlayers) {
            for (t in 0 until GameLogic.TOKENS_PER_PLAYER) {
                val pos = st.positions[p][t]
                val rc = GameLogic.cellForPosition(p, t, pos)
                if (rc[0] == row && rc[1] == col) {
                    onTokenTapped?.invoke(p, t)
                    return true
                }
            }
        }
        return true
    }
}
