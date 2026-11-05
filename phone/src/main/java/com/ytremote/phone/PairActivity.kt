package com.ytremote.phone

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.google.zxing.integration.android.IntentIntegrator

class PairActivity : Activity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        IntentIntegrator(this)
            .setPrompt("وجّه الكاميرا لرمز QR على الشاشة")
            .setBeepEnabled(false)
            .setOrientationLocked(false)
            .initiateScan()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        val r = IntentIntegrator.parseActivityResult(req, res, data)
        val text = r?.contents
        if (text != null) {
            val ok = Tv.pair(this, text)
            Toast.makeText(this, if (ok) "تم الربط ✓" else "رمز غير صالح", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
