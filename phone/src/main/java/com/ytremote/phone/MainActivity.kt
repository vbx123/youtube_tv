package com.ytremote.phone

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        Tv.load(this)
        if (handleShare(intent)) return

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0F0F0F.toInt())
            setPadding(0, dp(24), 0, 0)
        }
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = WebViewClient()
            loadUrl("https://m.youtube.com")
        }
        root.addView(row(
            btn("🎮 ريموت", h = 44) { startActivity(Intent(this, RemoteActivity::class.java)) },
            btn("📺 شغّل على الشاشة", 1.6f, 44) {
                val u = web.url
                if (Tv.base == null) {
                    startActivity(Intent(this, PairActivity::class.java))
                } else if (u != null) {
                    Tv.send("open", "url" to u)
                    Toast.makeText(this, "تم الارسال للشاشة", Toast.LENGTH_SHORT).show()
                }
            },
            btn("📷", 0.6f, 44) { startActivity(Intent(this, PairActivity::class.java)) }
        ), LinearLayout.LayoutParams(-1, -2))
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    // share a YouTube link from any app -> plays on the screen
    private fun handleShare(i: Intent?): Boolean {
        if (i?.action != Intent.ACTION_SEND) return false
        val t = i.getStringExtra(Intent.EXTRA_TEXT) ?: return false
        val url = Regex("https?://\\S+").find(t)?.value ?: return false
        Tv.load(this)
        Tv.send("open", "url" to url)
        Toast.makeText(this, "تم الارسال للشاشة", Toast.LENGTH_SHORT).show()
        finish()
        return true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::web.isInitialized && web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
