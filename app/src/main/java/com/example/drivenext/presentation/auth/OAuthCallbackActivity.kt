package com.example.drivenext.presentation.auth

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle

class OAuthCallbackActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data
        if (uri?.scheme == "com.example.drivenext" && uri.host == "auth" && uri.path == "/callback") {
            val fragment = Uri.Builder().encodedQuery(uri.encodedFragment).build()
            startActivity(Intent(this, AuthActivity::class.java)
                .putExtra("oauth_code", uri.getQueryParameter("code"))
                .putExtra("oauth_error", uri.getQueryParameter("error_description")
                    ?: fragment.getQueryParameter("error_description"))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
        finish()
    }
}
