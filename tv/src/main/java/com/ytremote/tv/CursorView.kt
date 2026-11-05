package com.ytremote.tv

import android.content.Context
import android.graphics.*
import android.view.View

class CursorView(c: Context) : View(c) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val path = Path().apply {
        moveTo(0f, 0f); lineTo(0f, 42f); lineTo(11f, 32f); lineTo(19f, 50f)
        lineTo(27f, 46f); lineTo(19f, 29f); lineTo(34f, 29f); close()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.translate(2f, 2f)
        canvas.scale(1.6f, 1.6f)
        canvas.drawPath(path, fill)
        canvas.drawPath(path, line)
    }
}
