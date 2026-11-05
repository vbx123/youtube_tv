package com.ytremote.phone

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

val cBg = 0xFF0F0F0F.toInt()
val cRed = 0xFFE62117.toInt()
val cSurf = 0xFF1E1E1E.toInt()
val cSurf2 = 0xFF2C2C2C.toInt()
val cSub = 0xFF9E9E9E.toInt()

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

    private fun save(c: Context) {
        c.getSharedPreferences("p", Context.MODE_PRIVATE).edit()
            .putString("b", base).putString("t", token).apply()
    }

    // QR format: ytremote://IP:PORT/TOKEN
    fun pair(c: Context, qr: String): Boolean {
        val u = Uri.parse(qr.trim())
        if (u.scheme != "ytremote" || u.host == null) return false
        val port = if (u.port > 0) u.port else 8765
        base = "http://${u.host}:$port"
        token = u.lastPathSegment ?: ""
        save(c)
        send("paired")
        return true
    }

    fun pairManual(c: Context, ip: String, code: String) {
        val host = ip.trim().removePrefix("http://")
        base = "http://" + if (host.contains(":")) host else "$host:8765"
        token = code.trim()
        save(c)
        send("paired")
    }

    fun unpair(c: Context) {
        base = null; token = ""
        c.getSharedPreferences("p", Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun ping(cb: (Boolean) -> Unit) {
        val b = base
        if (b == null) { cb(false); return }
        ex.execute {
            val ok = try {
                val c = URL("$b/ping?t=$token").openConnection() as HttpURLConnection
                c.connectTimeout = 1500; c.readTimeout = 1500
                val r = c.responseCode == 200
                c.disconnect()
                r
            } catch (_: Exception) { false }
            cb(ok)
        }
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

// ---------------- UI helpers ----------------
fun Context.dp(x: Int) = (x * resources.displayMetrics.density).toInt()

fun Context.rounded(color: Int, r: Int, stroke: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(r).toFloat()
        if (stroke != 0) setStroke(dp(1), stroke)
    }

fun Context.ripple(color: Int, r: Int): Drawable =
    RippleDrawable(ColorStateList.valueOf(0x55FFFFFF), rounded(color, r), null)

fun Context.tap(
    text0: String, w: Float = 1f, h: Int = 54, bg: Int = cSurf2, size: Float = 18f,
    onClick: () -> Unit
): TextView = TextView(this).apply {
    text = text0
    textSize = size
    setTextColor(Color.WHITE)
    gravity = Gravity.CENTER
    typeface = Typeface.DEFAULT_BOLD
    background = ripple(bg, 14)
    isClickable = true
    setOnClickListener {
        it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        onClick()
    }
    layoutParams = LinearLayout.LayoutParams(0, dp(h), w).apply {
        setMargins(dp(4), dp(4), dp(4), dp(4))
    }
}

fun Context.gap(w: Float = 1f, h: Int = 54) = View(this).apply {
    layoutParams = LinearLayout.LayoutParams(0, dp(h), w)
}

fun Context.row(vararg v: View) = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    layoutParams = LinearLayout.LayoutParams(-1, -2)
    v.forEach { addView(it) }
}

fun Context.lbl(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
    text = t
    textSize = size
    setTextColor(color)
    if (bold) typeface = Typeface.DEFAULT_BOLD
    setPadding(dp(4), dp(6), dp(4), dp(6))
}

fun Context.edit(hint0: String, full: Boolean = false) = EditText(this).apply {
    hint = hint0
    setHintTextColor(cSub)
    setTextColor(Color.WHITE)
    textSize = 16f
    setSingleLine()
    background = rounded(cSurf, 14)
    setPadding(dp(16), 0, dp(16), 0)
    layoutParams = (if (full) LinearLayout.LayoutParams(-1, dp(52))
    else LinearLayout.LayoutParams(0, dp(52), 1f)).apply {
        setMargins(dp(4), dp(4), dp(4), dp(4))
    }
}

fun <T : View> T.full(h: Int, top: Int = 6): T {
    val d = resources.displayMetrics.density
    layoutParams = LinearLayout.LayoutParams(-1, (h * d).toInt()).apply {
        setMargins((4 * d).toInt(), (top * d).toInt(), (4 * d).toInt(), (top * d).toInt())
    }
    return this
}
