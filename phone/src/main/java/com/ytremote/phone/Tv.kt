package com.ytremote.phone

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

object Tv {
    var base: String? = null
    var token = ""
    private val ex = Executors.newSingleThreadExecutor()
    private val pending = AtomicInteger(0)

    fun load(c: Context) {
        val p = c.getSharedPreferences("p", Context.MODE_PRIVATE)
        base = p.getString("b", null)
        token = p.getString("t", "") ?: ""
    }

    // QR format: ytremote://IP:PORT/TOKEN
    fun pair(c: Context, qr: String): Boolean {
        val u = Uri.parse(qr)
        if (u.scheme != "ytremote" || u.host == null) return false
        base = "http://${u.host}:${u.port}"
        token = u.lastPathSegment ?: ""
        c.getSharedPreferences("p", Context.MODE_PRIVATE).edit()
            .putString("b", base).putString("t", token).apply()
        send("ping")
        return true
    }

    fun send(cmd: String, vararg kv: Pair<String, String>) {
        val b = base ?: return
        if (cmd == "move" && pending.get() > 2) return
        pending.incrementAndGet()
        ex.execute {
            try {
                val q = (listOf("t" to token) + kv).joinToString("&") {
                    "${it.first}=${URLEncoder.encode(it.second, "UTF-8")}"
                }
                val c = URL("$b/$cmd?$q").openConnection() as HttpURLConnection
                c.connectTimeout = 1500; c.readTimeout = 1500
                c.inputStream.readBytes()
                c.disconnect()
            } catch (_: Exception) {
            } finally {
                pending.decrementAndGet()
            }
        }
    }
}

fun Context.dp(x: Int) = (x * resources.displayMetrics.density).toInt()

fun Context.btn(label: String, w: Float = 1f, h: Int = 50, onClick: () -> Unit) = Button(this).apply {
    text = label
    isAllCaps = false
    textSize = 15f
    setTextColor(Color.WHITE)
    setBackgroundColor(0xFF2A2A2A.toInt())
    setOnClickListener {
        it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        onClick()
    }
    layoutParams = LinearLayout.LayoutParams(0, dp(h), w).apply {
        setMargins(dp(3), dp(3), dp(3), dp(3))
    }
}

fun Context.gap(w: Float = 1f, h: Int = 50) = View(this).apply {
    layoutParams = LinearLayout.LayoutParams(0, dp(h), w)
}

fun Context.row(vararg v: View) = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    v.forEach { addView(it) }
}
