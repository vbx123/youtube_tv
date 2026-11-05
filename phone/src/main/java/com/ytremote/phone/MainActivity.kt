package com.ytremote.phone

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.codescanner.GmsBarcodeScannerOptions
import com.google.android.gms.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import kotlin.math.abs
import kotlin.math.hypot

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var views: List<View>
    private lateinit var navTabs: List<TextView>
    private lateinit var statusTv: TextView
    private lateinit var pairInfo: TextView
    private val ui = Handler(Looper.getMainLooper())
    private var current = 0
    private var webMode = false

    private val poll = object : Runnable {
        override fun run() {
            Tv.ping { ok -> runOnUiThread { setStatus(ok) } }
            ui.postDelayed(this, 4000)
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        installCrashLogger()
        Tv.load(this)
        if (handleShare(intent)) return

        window.statusBarColor = cBg
        window.navigationBarColor = 0xFF161616.toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(cBg)
            fitsSystemWindows = true
        }
        root.addView(header(), LinearLayout.LayoutParams(-1, dp(52)))

        val content = FrameLayout(this)
        val b = buildBrowse()
        val r = buildRemote()
        val p = buildPair()
        views = listOf(b, r, p)
        views.forEach { content.addView(it, FrameLayout.LayoutParams(-1, -1)) }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(bottomNav(), LinearLayout.LayoutParams(-1, dp(60)))
        setContentView(root)

        select(if (Tv.base == null) 2 else 0)
        setStatus(false)
        showLastCrash()
    }

    // ---------------- header / nav ----------------
    private fun header(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), 0, dp(12), 0)
        setBackgroundColor(cBg)
        addView(TextView(context).apply {
            text = "▶"; textSize = 13f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = rounded(cRed, 8)
        }, LinearLayout.LayoutParams(dp(34), dp(24)))
        addView(TextView(context).apply {
            text = "YouTube Remote"; textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(dp(10), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        statusTv = TextView(context).apply {
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = rounded(cSurf, 16)
            setOnClickListener { select(2) }
        }
        addView(statusTv, LinearLayout.LayoutParams(-2, -2))
    }

    private fun bottomNav(): View {
        val names = listOf("🌐\nتصفح", "🎮\nريموت", "🔗\nربط")
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF161616.toInt())
        }
        navTabs = names.mapIndexed { i, n ->
            val t = TextView(this).apply {
                text = n; textSize = 12f
                gravity = Gravity.CENTER
                isClickable = true
                background = ripple(0xFF161616.toInt(), 0)
                setOnClickListener { select(i) }
            }
            bar.addView(t, LinearLayout.LayoutParams(0, -1, 1f))
            t
        }
        return bar
    }

    private fun select(i0: Int) {
        var i = i0
        if (i == 1 && Tv.base == null) {
            toast("اربط الشاشة أولاً")
            i = 2
        }
        current = i
        views.forEachIndexed { idx, v -> v.visibility = if (idx == i) View.VISIBLE else View.GONE }
        navTabs.forEachIndexed { idx, t -> t.setTextColor(if (idx == i) cRed else cSub) }
        if (i == 2) refreshPair()
    }

    private fun setStatus(ok: Boolean) {
        if (!::statusTv.isInitialized) return
        val text: String
        val color: Int
        if (Tv.base == null) { text = "● غير مرتبط"; color = 0xFF9E9E9E.toInt() }
        else if (ok) { text = "● متصل"; color = 0xFF4CAF50.toInt() }
        else { text = "● لا يوجد اتصال"; color = 0xFFFF7043.toInt() }
        statusTv.text = text
        statusTv.setTextColor(color)
    }

    // ---------------- browse tab ----------------
    @SuppressLint("SetJavaScriptEnabled")
    private fun buildBrowse(): View {
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            setBackgroundColor(cBg)
            webViewClient = WebViewClient()
            loadUrl("https://m.youtube.com")
        }
        val bar = row(
            tap("◀", 0.5f, 52) { if (web.canGoBack()) web.goBack() },
            tap("📺  شغّل على الشاشة", 3f, 52, cRed, 16f) { sendCurrent() },
            tap("⟳", 0.5f, 52) { web.reload() }
        )
        bar.setPadding(dp(6), dp(4), dp(6), dp(4))
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(bar)
        }
    }

    private fun sendCurrent() {
        val u = web.url
        if (Tv.base == null) {
            toast("اربط الشاشة أولاً"); select(2)
        } else if (u != null) {
            Tv.send("open", "url" to u)
            toast("تم الإرسال للشاشة ✓")
        }
    }

    // ---------------- remote tab ----------------
    @SuppressLint("ClickableViewAccessibility")
    private fun buildRemote(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(16))
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        val input = edit("اكتب للبحث في يوتيوب…")
        input.imeOptions = EditorInfo.IME_ACTION_SEARCH
        input.setOnEditorActionListener { v, _, _ ->
            Tv.send("search", "q" to v.text.toString()); true
        }
        col.addView(row(input, tap("🔍", 0.4f, 52, cRed) {
            Tv.send("search", "q" to input.text.toString())
        }))
        col.addView(row(
            tap("🏠 الرئيسية", 1f, 48, size = 12f) { Tv.send("key", "k" to "home") },
            tap("↩ رجوع", 1f, 48, size = 12f) { Tv.send("key", "k" to "back") },
            tap("✍ كتابة", 1f, 48, size = 12f) { Tv.send("text", "v" to input.text.toString()) },
            tap("📟 الكود", 1f, 48, size = 12f) { Tv.send("panel") }
        ))

        col.addView(row(gap(h = 64), tap("▲", 1f, 64, size = 24f) { Tv.send("key", "k" to "up") }, gap(h = 64)))
        col.addView(row(
            tap("◀", 1f, 64, size = 24f) { Tv.send("key", "k" to "left") },
            tap("OK", 1f, 64, cRed, 20f) { Tv.send("key", "k" to "ok") },
            tap("▶", 1f, 64, size = 24f) { Tv.send("key", "k" to "right") }
        ))
        col.addView(row(gap(h = 64), tap("▼", 1f, 64, size = 24f) { Tv.send("key", "k" to "down") }, gap(h = 64)))

        col.addView(row(
            tap("⏪", 1f, 52) { Tv.send("key", "k" to "rw") },
            tap("⏯", 1f, 52) { Tv.send("key", "k" to "play") },
            tap("⏩", 1f, 52) { Tv.send("key", "k" to "ff") },
            tap("🔉", 1f, 52) { Tv.send("vol", "d" to "down") },
            tap("🔊", 1f, 52) { Tv.send("vol", "d" to "up") }
        ))

        val modeBtn = tap("🖱 وضع المؤشر", 2f, 48, size = 13f) {}
        modeBtn.setOnClickListener {
            webMode = !webMode
            Tv.send("mode", "m" to if (webMode) "web" else "tv")
            modeBtn.background = ripple(if (webMode) cRed else cSurf2, 14)
        }
        col.addView(row(
            modeBtn,
            tap("⇧", 1f, 48) { Tv.send("scroll", "dy" to "-400") },
            tap("⇩", 1f, 48) { Tv.send("scroll", "dy" to "400") }
        ))

        val pad = TextView(this).apply {
            text = "لوحة المؤشر\nحرّك إصبعك للتحريك • اضغط للنقر • إصبعين للتمرير"
            setTextColor(cSub); textSize = 14f
            gravity = Gravity.CENTER
            background = rounded(cSurf, 20, 0xFF333333.toInt())
            var lx = 0f; var ly = 0f; var sx = 0f; var sy = 0f
            var ly2 = 0f; var t0 = 0L; var moved = false
            setOnTouchListener { v, e ->
                v.parent?.requestDisallowInterceptTouchEvent(true)
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        lx = e.x; ly = e.y; sx = e.x; sy = e.y
                        t0 = e.eventTime; moved = false
                    }
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        ly2 = (e.getY(0) + e.getY(1)) / 2
                        moved = true
                    }
                    MotionEvent.ACTION_POINTER_UP -> {
                        val k = if (e.actionIndex == 0) 1 else 0
                        lx = e.getX(k); ly = e.getY(k)
                        moved = true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (e.pointerCount >= 2) {
                            val y = (e.getY(0) + e.getY(1)) / 2
                            val d = ((ly2 - y) * 2.5f).toInt()
                            if (abs(d) >= 8) {
                                Tv.send("scroll", "dy" to d.toString())
                                ly2 = y
                            }
                        } else {
                            if (hypot(e.x - sx, e.y - sy) > dp(8)) moved = true
                            val dx = ((e.x - lx) * 2.2f).toInt()
                            val dy = ((e.y - ly) * 2.2f).toInt()
                            if (dx != 0 || dy != 0) {
                                Tv.send("move", "dx" to dx.toString(), "dy" to dy.toString())
                                lx = e.x; ly = e.y
                            }
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved && e.eventTime - t0 < 300) Tv.send("click")
                    }
                }
                true
            }
        }
        col.addView(pad.full(240, 8))

        return ScrollView(this).apply {
            setBackgroundColor(cBg)
            addView(col)
        }
    }

    // ---------------- pair tab ----------------
    private fun buildPair(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        col.addView(lbl("ربط الشاشة", 24f, Color.WHITE, true))
        col.addView(lbl("شغّل تطبيق الشاشة، ثم امسح الـ QR الظاهر عليها، أو اكتب العنوان والكود يدوياً.", 14f, cSub))
        col.addView(tap("📷  مسح رمز QR", 1f, 60, cRed, 18f) { scan() }.full(60, 12))
        col.addView(lbl("أو اكتب بيانات الشاشة يدوياً", 14f, cSub))
        val ipE = edit("عنوان الشاشة  (مثال 192.168.1.5)", true)
        val codeE = edit("الكود", true)
        col.addView(ipE)
        col.addView(codeE)
        col.addView(tap("🔗  اتصال", 1f, 54, cSurf2, 17f) {
            val ip = ipE.text.toString().trim()
            val code = codeE.text.toString().trim()
            if (ip.isEmpty() || code.isEmpty()) toast("اكتب العنوان والكود")
            else { Tv.pairManual(this, ip, code); onPaired() }
        }.full(54, 8))
        pairInfo = lbl("", 14f, cSub)
        col.addView(pairInfo)
        col.addView(tap("فصل الشاشة", 1f, 46, 0xFF3A1A1A.toInt(), 15f) {
            Tv.unpair(this); refreshPair(); setStatus(false)
        }.full(46, 8))
        return ScrollView(this).apply {
            setBackgroundColor(cBg)
            addView(col)
        }
    }

    private fun refreshPair() {
        if (!::pairInfo.isInitialized) return
        pairInfo.text = if (Tv.base == null) "الحالة: غير مرتبط" else "مرتبط مع: ${Tv.base}"
    }

    private fun onPaired() {
        toast("تم الربط ✓")
        refreshPair()
        setStatus(true)
        select(1)
    }

    private fun scan() {
        try {
            val opts = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
            GmsBarcodeScanning.getClient(this, opts).startScan()
                .addOnSuccessListener { b ->
                    val t = b.rawValue ?: ""
                    if (Tv.pair(this, t)) onPaired() else toast("رمز غير صالح")
                }
                .addOnFailureListener {
                    toast("تعذر فتح الماسح، استخدم الكود اليدوي")
                }
        } catch (e: Throwable) {
            toast("تعذر فتح الماسح، استخدم الكود اليدوي")
        }
    }

    // ---------------- share intent ----------------
    private fun handleShare(i: Intent?): Boolean {
        if (i?.action != Intent.ACTION_SEND) return false
        val t = i.getStringExtra(Intent.EXTRA_TEXT) ?: return false
        val url = Regex("https?://\\S+").find(t)?.value ?: return false
        if (Tv.base == null) {
            toast("افتح التطبيق واربط الشاشة أولاً")
        } else {
            Tv.send("open", "url" to url)
            toast("تم الإرسال للشاشة ✓")
        }
        finish()
        return true
    }

    // ---------------- misc ----------------
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    private fun installCrashLogger() {
        val prefs = getSharedPreferences("p", MODE_PRIVATE)
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                prefs.edit().putString("crash", Log.getStackTraceString(e).take(2500)).commit()
            } catch (_: Throwable) { }
            old?.uncaughtException(t, e)
        }
    }

    private fun showLastCrash() {
        val prefs = getSharedPreferences("p", MODE_PRIVATE)
        val c = prefs.getString("crash", null) ?: return
        prefs.edit().remove("crash").apply()
        AlertDialog.Builder(this)
            .setTitle("حصل خطأ في المرة السابقة")
            .setMessage(c)
            .setPositiveButton("تمام", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (::statusTv.isInitialized) { ui.removeCallbacks(poll); ui.post(poll) }
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(poll)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!::web.isInitialized) { super.onBackPressed(); return }
        if (current == 0 && web.canGoBack()) web.goBack()
        else if (current != 0) select(0)
        else super.onBackPressed()
    }
}
