package com.ytremote.phone

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import kotlin.math.hypot

class RemoteActivity : Activity() {
    private var webMode = false

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Tv.load(this)
        if (Tv.base == null) startActivity(Intent(this, PairActivity::class.java))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0F0F0F.toInt())
            setPadding(dp(10), dp(24), dp(10), dp(10))
        }
        val W = LinearLayout.LayoutParams.MATCH_PARENT
        val H = LinearLayout.LayoutParams.WRAP_CONTENT
        fun add(v: View) = root.addView(v, LinearLayout.LayoutParams(W, H))

        // top row
        var modeBtn: android.widget.Button? = null
        modeBtn = btn("🖱 مؤشر: اغلاق") {
            webMode = !webMode
            Tv.send("mode", "m" to if (webMode) "web" else "tv")
            modeBtn?.text = if (webMode) "🖱 مؤشر: تشغيل" else "🖱 مؤشر: اغلاق"
        }
        add(row(
            btn("🏠") { Tv.send("key", "k" to "home") },
            btn("↩") { Tv.send("key", "k" to "back") },
            btn("📷 ربط") { startActivity(Intent(this, PairActivity::class.java)) },
            btn("🌐 تصفح") { finish() }
        ))

        // search / text
        val input = EditText(this).apply {
            hint = "اكتب هنا للبحث"
            setHintTextColor(0xFF888888.toInt())
            setTextColor(Color.WHITE)
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            layoutParams = LinearLayout.LayoutParams(0, dp(50), 3f)
            setOnEditorActionListener { v, _, _ ->
                Tv.send("search", "q" to v.text.toString()); true
            }
        }
        add(row(
            input,
            btn("بحث") { Tv.send("search", "q" to input.text.toString()) },
            btn("كتابة") { Tv.send("text", "v" to input.text.toString()) }
        ))

        // dpad
        add(row(gap(), btn("▲") { Tv.send("key", "k" to "up") }, gap()))
        add(row(
            btn("◀") { Tv.send("key", "k" to "left") },
            btn("OK") { Tv.send("key", "k" to "ok") },
            btn("▶") { Tv.send("key", "k" to "right") }
        ))
        add(row(gap(), btn("▼") { Tv.send("key", "k" to "down") }, gap()))

        // media
        add(row(
            btn("⏪") { Tv.send("key", "k" to "rw") },
            btn("⏯") { Tv.send("key", "k" to "play") },
            btn("⏩") { Tv.send("key", "k" to "ff") },
            btn("🔉") { Tv.send("vol", "d" to "down") },
            btn("🔊") { Tv.send("vol", "d" to "up") }
        ))
        add(row(
            modeBtn,
            btn("⇧") { Tv.send("scroll", "dy" to "-400") },
            btn("⇩") { Tv.send("scroll", "dy" to "400") },
            btn("QR") { Tv.send("panel") }
        ))

        // touchpad (mouse pointer)
        val pad = View(this).apply {
            setBackgroundColor(0xFF1C1C1C.toInt())
            var lx = 0f; var ly = 0f; var sx = 0f; var sy = 0f
            var t0 = 0L; var moved = false
            setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        lx = e.x; ly = e.y; sx = e.x; sy = e.y; t0 = e.eventTime; moved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (hypot(e.x - sx, e.y - sy) > dp(8)) moved = true
                        val dx = ((e.x - lx) * 2.2f).toInt()
                        val dy = ((e.y - ly) * 2.2f).toInt()
                        if (dx != 0 || dy != 0) {
                            Tv.send("move", "dx" to dx.toString(), "dy" to dy.toString())
                            lx = e.x; ly = e.y
                        }
                    }
                    MotionEvent.ACTION_UP ->
                        if (!moved && e.eventTime - t0 < 300) Tv.send("click")
                }
                true
            }
        }
        root.addView(pad, LinearLayout.LayoutParams(W, 0, 1f).apply { topMargin = dp(8) })
        setContentView(root)
    }
}
