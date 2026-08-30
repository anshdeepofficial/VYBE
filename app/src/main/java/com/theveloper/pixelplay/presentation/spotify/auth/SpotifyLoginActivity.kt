package com.theveloper.pixelplay.presentation.spotify.auth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.theveloper.pixelplay.data.spotify.SpotifyAccountRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SpotifyLoginActivity : ComponentActivity() {
    @Inject lateinit var repository: SpotifyAccountRepository
    private var loginWebView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val callback = intent.data?.takeIf {
            it.toString().startsWith(SpotifyAccountRepository.REDIRECT_URI.substringBefore('?'))
        }
        if (callback != null) {
            lifecycleScope.launch {
                repository.completeAuthorization(callback)
                    .onSuccess { Toast.makeText(this@SpotifyLoginActivity, "Spotify connected", Toast.LENGTH_SHORT).show() }
                    .onFailure { Toast.makeText(this@SpotifyLoginActivity, it.message, Toast.LENGTH_LONG).show() }
                finish()
            }
            return
        }

        runCatching { repository.createAuthorizationUri() }
            .onSuccess { showSpotifyLogin(it) }
            .onFailure {
                Toast.makeText(this, it.message, Toast.LENGTH_LONG).show()
                finish()
            }
    }

    @Suppress("SetJavaScriptEnabled")
    private fun showSpotifyLogin(uri: Uri) {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            flush()
        }
        loginWebView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    consumeSpotifyCallback(request.url)

                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                    consumeSpotifyCallback(Uri.parse(url))
            }
            loadUrl(uri.toString())
        }
        setContentView(loginWebView)
    }

    private fun consumeSpotifyCallback(uri: Uri): Boolean {
        if (!uri.toString().startsWith(SpotifyAccountRepository.REDIRECT_URI.substringBefore('?'))) return false
        lifecycleScope.launch {
            repository.completeAuthorization(uri)
                .onSuccess { Toast.makeText(this@SpotifyLoginActivity, "Spotify connected", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(this@SpotifyLoginActivity, it.message, Toast.LENGTH_LONG).show() }
            finish()
        }
        return true
    }

    override fun onDestroy() {
        loginWebView?.apply { stopLoading(); destroy() }
        loginWebView = null
        super.onDestroy()
    }
}
