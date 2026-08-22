package com.theveloper.pixelplay.presentation.ytmusic.auth

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.data.network.ytmusic.YouTubeAccountManager
import com.theveloper.pixelplay.data.network.ytmusic.YouTubeSyncState
import com.theveloper.pixelplay.ui.theme.PixelPlayTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private enum class LoginStep { READY, SIGNING_IN, SYNCING, ERROR }

/**
 * Signs in to the YouTube Music website and captures its authenticated music session. All account,
 * library and playlist requests then go to music.youtube.com/youtubei; the regular YouTube Data API
 * is deliberately not used.
 */
@AndroidEntryPoint
class YouTubeLoginActivity : ComponentActivity() {

    @Inject lateinit var youtubeAccountManager: YouTubeAccountManager

    private var loginStep by mutableStateOf(LoginStep.READY)
    private var errorMessage by mutableStateOf<String?>(null)
    private var webView: WebView? = null
    private var submittedSession = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra(EXTRA_AUTO_START_SIGN_IN, false)) {
            loginStep = LoginStep.SIGNING_IN
        }
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val browser = webView
                if (loginStep == LoginStep.SIGNING_IN && browser?.canGoBack() == true) {
                    browser.goBack()
                } else {
                    finish()
                }
            }
        })
        setContent {
            PixelPlayTheme {
                val syncState by youtubeAccountManager.syncStateFlow.collectAsStateWithLifecycle()
                LaunchedEffect(syncState) {
                    when (syncState) {
                        YouTubeSyncState.SYNCED -> {
                            Toast.makeText(
                                this@YouTubeLoginActivity,
                                "YouTube Music connected and synchronized.",
                                Toast.LENGTH_SHORT,
                            ).show()
                            finish()
                        }
                        YouTubeSyncState.ERROR -> if (loginStep == LoginStep.SYNCING) {
                            submittedSession = false
                            showError("The YouTube Music session could not be synchronized. Please sign in again.")
                        }
                        else -> Unit
                    }
                }
                YouTubeMusicLoginScreen(
                    step = loginStep,
                    errorMessage = errorMessage,
                    onBack = onBackPressedDispatcher::onBackPressed,
                    onSignIn = {
                        errorMessage = null
                        loginStep = LoginStep.SIGNING_IN
                    },
                    browserFactory = ::createLoginWebView,
                )
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createLoginWebView(): WebView = WebView(this).apply {
        webView = this
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            builtInZoomControls = true
            displayZoomControls = false
            userAgentString = MUSIC_LOGIN_USER_AGENT
        }
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(this, true)
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                errorMessage = null
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                val host = runCatching { Uri.parse(url).host }.getOrNull()
                if (host != MUSIC_HOST || submittedSession) return
                val cookie = CookieManager.getInstance().getCookie(MUSIC_ORIGIN).orEmpty()
                if (cookie.hasYouTubeMusicSession()) {
                    CookieManager.getInstance().flush()
                    submittedSession = true
                    loginStep = LoginStep.SYNCING
                    youtubeAccountManager.loginWithAuth(cookie)
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) {
                    showError("YouTube Music sign-in could not be loaded. Check your connection and try again.")
                }
            }
        }
        loadUrl(MUSIC_LOGIN_URL)
    }

    private fun String.hasYouTubeMusicSession(): Boolean = split(';').any { part ->
        val name = part.substringBefore('=').trim()
        name == "SAPISID" || name == "__Secure-3PAPISID"
    }

    private fun showError(message: String) {
        errorMessage = message
        loginStep = LoginStep.ERROR
    }

    companion object {
        const val EXTRA_AUTO_START_SIGN_IN = "youtube_music_auto_start_sign_in"
        const val MUSIC_HOST = "music.youtube.com"
        const val MUSIC_ORIGIN = "https://music.youtube.com"
        const val MUSIC_LOGIN_URL =
            "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com%2F"
        const val MUSIC_LOGIN_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YouTubeMusicLoginScreen(
    step: LoginStep,
    errorMessage: String?,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    browserFactory: () -> WebView,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Connect YouTube Music") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (step == LoginStep.SIGNING_IN) {
            var activeView: WebView? = null
            AndroidView(
                modifier = Modifier.fillMaxSize().padding(padding),
                factory = {
                    browserFactory().also { activeView = it }
                },
            )
            DisposableEffect(Unit) {
                onDispose {
                    activeView?.let { view ->
                        view.stopLoading()
                        view.removeAllViews()
                        view.destroy()
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
                    imageVector = Icons.Rounded.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(0.24f),
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = if (step == LoginStep.SYNCING) {
                        "Fetching your YouTube Music library"
                    } else {
                        "Sign in to YouTube Music"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "VYBE uses your YouTube Music session only for your music profile, likes, history, and playlists. Search and playback remain available without signing in.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (!errorMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(24.dp))
                if (step == LoginStep.SYNCING) {
                    CircularProgressIndicator()
                } else {
                    Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
                        Text(if (step == LoginStep.ERROR) "Try again" else "Continue to sign in")
                    }
                }
            }
        }
    }
}
