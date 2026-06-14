package com.example.ludo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class LudoBoardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var state: GameLogic.State? = null
    var onTokenTapped: ((player: Int, tokenIdx: Int) -> Unit)? = null

    private val gridPaint = Paint().apply { color = Color.LTGRAY; style = Paint.Style.STROKE; strokeWidth = 1f }
    private val bgPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val pathPaint = Paint().apply { color = Color.parseColor("#FFFDE7"); style = Paint.Style.FILL }
    private val centerPaint = Paint().apply { color = Color.parseColor("#CFD8DC"); style = Paint.Style.FILL }
    private val tokenBorder = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f }
    private val tokenFill = Paint().apply { style = Paint.Style.FILL }

    private var cell = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cell = Math.min(w, h) / 15f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (cell == 0f) cell = Math.min(width, height) / 15f

        // background
        canvas.drawRect(0f, 0f, 15 * cell, 15 * cell, bgPaint)

        // yard quadrants (colored 6x6 corners)
        drawYard(canvas, 0, 0, GameLogic.COLOR_HEX[0])   // Red top-left
        drawYard(canvas, 0, 9, GameLogic.COLOR_HEX[1])   // Green top-right
        drawYard(canvas, 9, 9, GameLogic.COLOR_HEX[2])   // Yellow bottom-right
        drawYard(canvas, 9, 0, GameLogic.COLOR_HEX[3])   // Blue bottom-left

        // main path cells
        for (cellPos in GameLogic.MAIN_PATH) {
            drawCellRect(canvas, cellPos[0], cellPos[1], pathPaint)
        }
        // safe cells highlight
        for (idx in GameLogic.SAFE_CELLS) {
            val c = GameLogic.MAIN_PATH[idx]
            val starPaint = Paint().apply { color = Color.parseColor("#FFD54F"); style = Paint.Style.FILL }
            drawCellRect(canvas, c[0], c[1], starPaint)
        }
        // home columns
        for (p in 0 until 4) {
            val colorPaint = Paint().apply { color = Color.parseColor(GameLogic.COLOR_HEX[p]); alpha = 120; style = Paint.Style.FILL }
            for (c in GameLogic.HOME_COLUMNS[p]) {
                drawCellRect(canvas, c[0], c[1], colorPaint)
            }
        }
        // center
        drawCellRect(canvas, 7, 7, centerPaint)

        // grid lines
        for (i in 0..15) {
            canvas.drawLine(0f, i * cell, 15 * cell, i * cell, gridPaint)
            canvas.drawLine(i * cell, 0f, i * cell, 15 * cell, gridPaint)
        }

        // tokens
        val st = state ?: return
        for (p in 0 until st.numPlayers) {
            for (t in 0 until GameLogic.TOKENS_PER_PLAYER) {
                val pos = st.positions[p][t]
                val rc = GameLogic.cellForPosition(p, t, pos)
                drawToken(canvas, rc[0], rc[1], p, t)
            }
        }
    }

    private fun drawYard(canvas: Canvas, rowStart: Int, colStart: Int, colorHex: String) {
        val paint = Paint().apply { color = Color.parseColor(colorHex); alpha = 60; style = Paint.Style.FILL }
        canvas.drawRect(colStart * cell, rowStart * cell, (colStart + 6) * cell, (rowStart + 6) * cell, paint)
    }

    private fun drawCellRect(canvas: Canvas, row: Int, col: Int, paint: Paint) {
        canvas.drawRect(col * cell, row * cell, (col + 1) * cell, (row + 1) * cell, paint)
    }

    private fun drawToken(canvas: Canvas, row: Int, col: Int, player: Int, tokenIdx: Int) {
        // small offset per token within a cell so multiple tokens on same cell are visible
        val offsets = arrayOf(
            floatArrayOf(0.3f, 0.3f), floatArrayOf(0.7f, 0.3f),
            floatArrayOf(0.3f, 0.7f), floatArrayOf(0.7f, 0.7f)
        )
        val off = offsets[tokenIdx % 4]
        val cx = (col + off[0]) * cell
        val cy = (row + off[1]) * cell
        val radius = cell * 0.28f

        tokenFill.color = Color.parseColor(GameLogic.COLOR_HEX[player])
        canvas.drawCircle(cx, cy, radius, tokenFill)
        canvas.drawCircle(cx, cy, radius, tokenBorder)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true
        val st = state ?: return true
        val col = (event.x / cell).toInt()
        val row = (event.y / cell).toInt()

        // find which token (if any) is at this cell
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
