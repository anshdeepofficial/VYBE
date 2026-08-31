package com.theveloper.pixelplay.presentation.spotify.auth

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.theveloper.pixelplay.data.spotify.SpotifyAccountRepository
import com.theveloper.pixelplay.ui.theme.PixelPlayTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SpotifyLoginStep { SIGNING_IN, CONNECTING, ERROR }

@AndroidEntryPoint
class SpotifyLoginActivity : ComponentActivity() {
    @Inject lateinit var repository: SpotifyAccountRepository

    private var loginWebView: WebView? = null
    private var loginStep by mutableStateOf(SpotifyLoginStep.SIGNING_IN)
    private var authorizationUri by mutableStateOf<Uri?>(null)
    private var errorMessage by mutableStateOf<String?>(null)
    private var loadProgress by mutableIntStateOf(0)
    private var pageCommitted by mutableStateOf(false)
    private var completingAuthorization = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val browser = loginWebView
                if (loginStep == SpotifyLoginStep.SIGNING_IN && browser?.canGoBack() == true) {
                    browser.goBack()
                } else {
                    finish()
                }
            }
        })
        setContent {
            PixelPlayTheme {
                SpotifyLoginScreen(
                    step = loginStep,
                    errorMessage = errorMessage,
                    progress = loadProgress,
                    pageCommitted = pageCommitted,
                    authorizationUri = authorizationUri,
                    onBack = onBackPressedDispatcher::onBackPressed,
                    onRetry = ::startLogin,
                    browserFactory = ::createLoginWebView,
                )
            }
        }
        intent.data?.takeIf(::isSpotifyCallback)?.let(::completeAuthorization) ?: startLogin()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.takeIf(::isSpotifyCallback)?.let(::completeAuthorization)
    }

    private fun startLogin() {
        destroyBrowser()
        completingAuthorization = false
        errorMessage = null
        loadProgress = 0
        pageCommitted = false
        loginStep = SpotifyLoginStep.SIGNING_IN
        authorizationUri = runCatching { repository.createAuthorizationUri() }
            .onFailure { showError(it.message ?: "Spotify sign-in could not be started.") }
            .getOrNull()
        val expectedUri = authorizationUri
        lifecycleScope.launch {
            delay(LOGIN_LOAD_TIMEOUT_MS)
            if (loginStep == SpotifyLoginStep.SIGNING_IN && authorizationUri == expectedUri && !pageCommitted) {
                showError("Spotify sign-in did not finish loading. Check Android System WebView and your connection, then try again.")
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createLoginWebView(uri: Uri): WebView = WebView(this).apply webView@ {
        loginWebView = this
        setBackgroundColor(Color.WHITE)
        // Spotify's login relies on accelerated compositing; forced software rendering creates
        // a black surface on several Samsung Android System WebView versions.
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            blockNetworkImage = false
            cacheMode = WebSettings.LOAD_DEFAULT
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = false
            userAgentString = SPOTIFY_LOGIN_USER_AGENT
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(this@webView, true)
            flush()
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                loadProgress = newProgress.coerceIn(0, 100)
            }
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                consumeSpotifyCallback(request.url)

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                consumeSpotifyCallback(Uri.parse(url))

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                pageCommitted = false
                errorMessage = null
            }

            override fun onPageCommitVisible(view: WebView, url: String?) {
                super.onPageCommitVisible(view, url)
                pageCommitted = true
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                pageCommitted = true
                loadProgress = 100
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) showError("Spotify sign-in could not be loaded. Check your connection and try again.")
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                super.onReceivedHttpError(view, request, response)
                if (request.isForMainFrame && response.statusCode >= 400) {
                    showError("Spotify sign-in returned an error (${response.statusCode}). Please try again.")
                }
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                showError("Spotify's secure connection could not be verified. Check your date, connection, and Android System WebView.")
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                loginWebView = null
                showError("The secure Spotify login renderer stopped. VYBE recovered safely; tap Try again.")
                return true
            }
        }
        loadUrl(uri.toString(), mapOf("Accept-Language" to java.util.Locale.getDefault().toLanguageTag()))
    }

    private fun consumeSpotifyCallback(uri: Uri): Boolean {
        if (!isSpotifyCallback(uri)) return false
        completeAuthorization(uri)
        return true
    }

    private fun isSpotifyCallback(uri: Uri): Boolean {
        if (uri.scheme.equals("vybe", true) && uri.host.equals("spotify-callback", true)) return true
        val expected = Uri.parse(SpotifyAccountRepository.REDIRECT_URI)
        return uri.scheme.equals(expected.scheme, true) &&
            uri.host.equals(expected.host, true) &&
            uri.port == expected.port &&
            uri.path == expected.path
    }

    private fun completeAuthorization(uri: Uri) {
        if (completingAuthorization) return
        completingAuthorization = true
        loginStep = SpotifyLoginStep.CONNECTING
        lifecycleScope.launch {
            repository.completeAuthorization(uri)
                .onSuccess {
                    Toast.makeText(this@SpotifyLoginActivity, "Spotify connected and playlists synchronized", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .onFailure {
                    completingAuthorization = false
                    showError(it.message ?: "Spotify could not complete sign-in.")
                }
        }
    }

    private fun showError(message: String) {
        errorMessage = message
        loginStep = SpotifyLoginStep.ERROR
    }

    private fun destroyBrowser() {
        loginWebView?.apply {
            stopLoading()
            webChromeClient = null
            webViewClient = WebViewClient()
            removeAllViews()
            destroy()
        }
        loginWebView = null
    }

    override fun onDestroy() {
        destroyBrowser()
        super.onDestroy()
    }

    companion object {
        private const val LOGIN_LOAD_TIMEOUT_MS = 25_000L
        private const val SPOTIFY_LOGIN_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpotifyLoginScreen(
    step: SpotifyLoginStep,
    errorMessage: String?,
    progress: Int,
    pageCommitted: Boolean,
    authorizationUri: Uri?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    browserFactory: (Uri) -> WebView,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Connect Spotify") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (step == SpotifyLoginStep.SIGNING_IN && authorizationUri != null) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                var activeView: WebView? = null
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { browserFactory(authorizationUri).also { activeView = it } },
                )
                if (progress < 100) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0, 100) / 100f },
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    )
                }
                if (!pageCommitted) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(18.dp))
                            Text("Loading secure Spotify sign-in…", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Your playlists will sync after authorization.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                DisposableEffect(Unit) {
                    onDispose {
                        activeView?.stopLoading()
                        activeView = null
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Rounded.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    if (step == SpotifyLoginStep.CONNECTING) "Synchronizing Spotify" else "Spotify sign-in needs attention",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    if (step == SpotifyLoginStep.CONNECTING) {
                        "VYBE is securely loading your profile and playlists."
                    } else {
                        errorMessage.orEmpty()
                    },
                    color = if (step == SpotifyLoginStep.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                if (step == SpotifyLoginStep.CONNECTING) {
                    CircularProgressIndicator()
                } else {
                    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
                }
            }
        }
    }
}
