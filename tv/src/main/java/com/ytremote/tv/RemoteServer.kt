package com.ytremote.tv

import android.os.Handler
import android.os.Looper
import fi.iki.elonen.NanoHTTPD

class RemoteServer(
    port: Int,
    private val token: String,
    private val onCmd: (String, Map<String, String>) -> Unit
) : NanoHTTPD(port) {
    private val ui = Handler(Looper.getMainLooper())

    override fun serve(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val p: Map<String, String> = session.parms
        if (p["t"] != token) {
            return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.FORBIDDEN, NanoHTTPD.MIME_PLAINTEXT, "forbidden"
            )
        }
        val cmd = session.uri.trim('/')
        ui.post { onCmd(cmd, p) }
        return NanoHTTPD.newFixedLengthResponse("ok")
    }
}
