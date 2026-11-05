package com.ytremote.tv

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : Activity() {
    companion object {
        const val PORT = 8765
        const val HOME_TV = "https://www.youtube.com/tv"
        const val HOME_WEB = "https://www.youtube.com"
        const val TV_UA = "Mozilla/5.0 (SMART-TV; Linux; Tizen 5.5) AppleWebKit/537.36 (KHTML, like Gecko) 69.0.3497.106.1 TV Safari/537.36"
        const val WEB_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    private lateinit var web: WebView
    private lateinit var root: FrameLayout
    private lateinit var cursor: CursorView
    private lateinit var panel: View
    private lateinit var pill: TextView
    private lateinit var spinner: ProgressBar
    private lateinit var token: String
    private val ui = Handler(Looper.getMainLooper())
    private var server: RemoteServer? = null
    private var tvMode = true
    private var cx = 300f
    private var cy = 300f
    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null
    private val hideCursor = Runnable { cursor.visibility = View.GONE }

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

    private fun allowed(h: String?): Boolean {
        if (h == null) return false
        return h.endsWith("youtube.com") || h.endsWith("youtu.be") ||
            h.endsWith("google.com") || h.endsWith("gstatic.com") ||
            h.endsWith("googlevideo.com") || h.endsWith("ytimg.com")
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val prefs = getSharedPreferences("p", Context.MODE_PRIVATE)
        token = prefs.getString("t", null) ?: (100000..999999).random().toString().also {
            prefs.edit().putString("t", it).apply()
        }

        root = FrameLayout(this).apply { setBackgroundColor(0xFF0F0F0F.toInt()) }

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.userAgentString = TV_UA
            setBackgroundColor(0xFF0F0F0F.toInt())
            isFocusable = true
            isFocusableInTouchMode = true
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean =
                    !allowed(r.url.host)

                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(v: WebView, url: String): Boolean =
                    !allowed(Uri.parse(url).host)

                override fun onPageFinished(v: WebView, url: String) {
                    spinner.visibility = View.GONE
                }

                @Deprecated("Deprecated in Java")
                override fun onReceivedError(v: WebView, code: Int, desc: String?, url: String?) {
                    ui.postDelayed({ v.reload() }, 5000)
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(v: View, cb: CustomViewCallback) {
                    customView = v; customCb = cb
                    root.addView(v, FrameLayout.LayoutParams(-1, -1))
                    pill.bringToFront(); panel.bringToFront(); cursor.bringToFront()
                }
                override fun onHideCustomView() { hideCustom() }
            }
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true)
        }
        root.addView(web, FrameLayout.LayoutParams(-1, -1))

        spinner = ProgressBar(this)
        root.addView(spinner, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER))

        pill = TextView(this).apply {
            text = "📱 ربط"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply {
                setColor(0xAA000000.toInt()); cornerRadius = dp(20).toFloat()
            }
            alpha = 0.75f
            isClickable = true
            isFocusable = false
            setOnClickListener { togglePanel() }
        }
        root.addView(
            pill,
            FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
                setMargins(dp(12), dp(12), dp(12), dp(12))
            }
        )

        panel = buildPanel(localIp())
        panel.visibility = View.VISIBLE
        root.addView(panel, FrameLayout.LayoutParams(-1, -1))

        cursor = CursorView(this).apply { visibility = View.GONE }
        root.addView(cursor, FrameLayout.LayoutParams(dp(60), dp(60)))

        setContentView(root)
        web.requestFocus()
        web.loadUrl(HOME_TV)

        try {
            server = RemoteServer(PORT, token, ::handle).also { it.start() }
        } catch (_: Exception) { }
    }

    // ---------- pairing panel ----------
    private fun label(t: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = t; textSize = size; setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(4), 0, dp(4))
        }

    private fun buildPanel(ip: String): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(0xFF1E1E1E.toInt()); cornerRadius = dp(24).toFloat()
            }
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        val img = ImageView(this).apply {
            setImageBitmap(qr("ytremote://$ip:$PORT/$token", 480))
            setBackgroundColor(Color.WHITE)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        card.addView(img, LinearLayout.LayoutParams(dp(200), dp(200)))

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), 0, 0, 0)
        }
        info.addView(label("ربط الهاتف بالشاشة", 22f, Color.WHITE, true))
        info.addView(label("في تطبيق الريموت ← تبويب «ربط» ← امسح الرمز", 14f, 0xFF9E9E9E.toInt()))
        info.addView(label("أو اكتب البيانات يدوياً:", 14f, 0xFF9E9E9E.toInt()).apply {
            setPadding(0, dp(12), 0, dp(4))
        })
        info.addView(label("العنوان:  $ip", 20f, Color.WHITE, true))
        info.addView(label("الكود:  $token", 28f, 0xFFFFD54F.toInt(), true))
        val close = TextView(this).apply {
            text = "إغلاق ✕"; textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(10), dp(20), dp(10))
            background = GradientDrawable().apply {
                setColor(0xFFE62117.toInt()); cornerRadius = dp(14).toFloat()
            }
            isClickable = true
            setOnClickListener { panel.visibility = View.GONE }
        }
        info.addView(close, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(14) })
        card.addView(info)

        return FrameLayout(this).apply {
            setBackgroundColor(0xEE000000.toInt())
            isClickable = true
            addView(card, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        }
    }

    private fun togglePanel() {
        panel.visibility = if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    // ---------- commands from phone ----------
    private fun handle(cmd: String, p: Map<String, String>) {
        val dx = p["dx"]?.toFloatOrNull() ?: 0f
        val dy = p["dy"]?.toFloatOrNull() ?: 0f
        when (cmd) {
            "paired" -> panel.visibility = View.GONE
            "panel" -> togglePanel()
            "key" -> key(p["k"] ?: "")
            "move" -> moveCursor(dx, dy)
            "click" -> click()
            "scroll" -> web.evaluateJavascript("window.scrollBy(0,${dy.toInt()})", null)
            "text" -> typeText(p["v"] ?: "")
            "search" -> search(p["q"] ?: "")
            "open" -> open(p["url"] ?: "")
            "mode" -> setMode(p["m"] == "web")
            "vol" -> (getSystemService(Context.AUDIO_SERVICE) as AudioManager).adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (p["d"] == "up") AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI
            )
        }
    }

    private fun sendKey(code: Int) {
        web.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        web.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun video(js: String) =
        web.evaluateJavascript("(function(){var v=document.querySelector('video');if(v){$js}})()", null)

    private fun key(k: String) {
        when (k) {
            "up" -> sendKey(KeyEvent.KEYCODE_DPAD_UP)
            "down" -> sendKey(KeyEvent.KEYCODE_DPAD_DOWN)
            "left" -> sendKey(KeyEvent.KEYCODE_DPAD_LEFT)
            "right" -> sendKey(KeyEvent.KEYCODE_DPAD_RIGHT)
            "ok" -> sendKey(KeyEvent.KEYCODE_ENTER)
            "back" -> goBackCmd()
            "home" -> web.loadUrl(if (tvMode) HOME_TV else HOME_WEB)
            "play" -> video("v.paused?v.play():v.pause()")
            "ff" -> video("v.currentTime+=10")
            "rw" -> video("v.currentTime-=10")
        }
    }

    private fun goBackCmd() {
        when {
            customView != null -> hideCustom()
            tvMode -> sendKey(KeyEvent.KEYCODE_ESCAPE)
            web.canGoBack() -> web.goBack()
        }
    }

    private fun moveCursor(dx: Float, dy: Float) {
        cx = (cx + dx).coerceIn(0f, (root.width - 2).toFloat())
        cy = (cy + dy).coerceIn(0f, (root.height - 2).toFloat())
        cursor.x = cx; cursor.y = cy
        cursor.visibility = View.VISIBLE
        cursor.bringToFront()
        ui.removeCallbacks(hideCursor)
        ui.postDelayed(hideCursor, 5000)
    }

    private fun click() {
        val t = SystemClock.uptimeMillis()
        val d = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, cx, cy, 0)
        root.dispatchTouchEvent(d); d.recycle()
        val u = MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, cx, cy, 0)
        root.dispatchTouchEvent(u); u.recycle()
    }

    private fun typeText(text: String) {
        val js = "(function(t){var e=document.activeElement;if(e&&('value' in e)){" +
            "var d=Object.getOwnPropertyDescriptor(Object.getPrototypeOf(e),'value');" +
            "if(d&&d.set){d.set.call(e,t)}else{e.value=t}" +
            "e.dispatchEvent(new Event('input',{bubbles:true}));}})(${JSONObject.quote(text)})"
        web.evaluateJavascript(js, null)
    }

    private fun search(q: String) {
        if (q.isBlank()) return
        web.loadUrl(
            if (tvMode) "$HOME_TV#/search?q=${Uri.encode(q)}"
            else "$HOME_WEB/results?search_query=${Uri.encode(q)}"
        )
    }

    private fun open(url: String) {
        val u = Uri.parse(url)
        val host = u.host ?: return
        val id = when {
            host.contains("youtu.be") -> u.lastPathSegment
            u.path?.startsWith("/shorts/") == true -> u.lastPathSegment
            else -> u.getQueryParameter("v")
        }
        if (id == null) {
            if (host.endsWith("youtube.com")) web.loadUrl(url)
            return
        }
        web.loadUrl(if (tvMode) "$HOME_TV#/watch?v=$id" else "$HOME_WEB/watch?v=$id")
    }

    private fun setMode(webMode: Boolean) {
        tvMode = !webMode
        web.settings.userAgentString = if (tvMode) TV_UA else WEB_UA
        web.loadUrl(if (tvMode) HOME_TV else HOME_WEB)
    }

    private fun hideCustom() {
        customView?.let { root.removeView(it) }
        customCb?.onCustomViewHidden()
        customView = null
    }

    // ---------- helpers ----------
    private fun localIp(): String = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
            ?.hostAddress ?: "؟؟؟"
    } catch (e: Exception) { "؟؟؟" }

    private fun qr(text: String, size: Int): Bitmap {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) for (y in 0 until size)
            bmp.setPixel(x, y, if (m[x, y]) Color.BLACK else Color.WHITE)
        return bmp
    }

    override fun onKeyDown(k: Int, e: KeyEvent): Boolean {
        if (k == KeyEvent.KEYCODE_MENU) { togglePanel(); return true }
        return super.onKeyDown(k, e)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (panel.visibility == View.VISIBLE) panel.visibility = View.GONE
        else if (customView != null || tvMode || web.canGoBack()) goBackCmd()
        else super.onBackPressed()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    override fun onPause() { super.onPause(); web.onPause() }
    override fun onResume() { super.onResume(); web.onResume() }
    override fun onDestroy() { server?.stop(); web.destroy(); super.onDestroy() }
}
