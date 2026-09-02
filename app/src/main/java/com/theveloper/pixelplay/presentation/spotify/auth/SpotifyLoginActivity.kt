package com.theveloper.pixelplay.presentation.spotify.auth

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.theveloper.pixelplay.data.spotify.SpotifyAccountRepository
import com.theveloper.pixelplay.ui.theme.PixelPlayTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

private enum class SpotifyLoginMode { WEBVIEW, COOKIE_INPUT, SYNCHRONIZING, ERROR }

@AndroidEntryPoint
class SpotifyLoginActivity : ComponentActivity() {
    @Inject lateinit var repository: SpotifyAccountRepository

    private var mode by mutableStateOf(SpotifyLoginMode.WEBVIEW)
    private var errorMessage by mutableStateOf<String?>(null)
    private var isSubmitting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PixelPlayTheme {
                SpotifyLoginScreen(
                    mode = mode,
                    errorMessage = errorMessage,
                    onBack = ::finish,
                    onCookieCaptured = ::completeLoginWithCookie,
                    onManualCookieSubmit = ::completeLoginWithCookie,
                    onRetry = {
                        errorMessage = null
                        mode = SpotifyLoginMode.WEBVIEW
                    },
                )
            }
        }
    }

    private fun completeLoginWithCookie(cookie: String) {
        if (isSubmitting) return
        val clean = cookie.trim().removePrefix("sp_dc=").trim()
        if (clean.isBlank()) {
            errorMessage = "Please enter a valid sp_dc cookie"
            mode = SpotifyLoginMode.ERROR
            return
        }
        isSubmitting = true
        mode = SpotifyLoginMode.SYNCHRONIZING
        errorMessage = null

        lifecycleScope.launch {
            repository.loginWithSpDc(clean)
                .onSuccess {
                    runCatching { repository.getCurrentUserPlaylists() }
                        .onSuccess { playlists ->
                            Toast.makeText(
                                this@SpotifyLoginActivity,
                                "Spotify connected! ${playlists.size} playlists synced",
                                Toast.LENGTH_LONG,
                            ).show()
                            setResult(RESULT_OK)
                            finish()
                        }
                        .onFailure {
                            Toast.makeText(
                                this@SpotifyLoginActivity,
                                "Spotify connected successfully!",
                                Toast.LENGTH_SHORT,
                            ).show()
                            setResult(RESULT_OK)
                            finish()
                        }
                }
                .onFailure { error ->
                    isSubmitting = false
                    errorMessage = error.message ?: "Spotify sign-in failed. Please verify your cookie or try again."
                    mode = SpotifyLoginMode.ERROR
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpotifyLoginScreen(
    mode: SpotifyLoginMode,
    errorMessage: String?,
    onBack: () -> Unit,
    onCookieCaptured: (String) -> Unit,
    onManualCookieSubmit: (String) -> Unit,
    onRetry: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var manualCookie by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Connect Spotify", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (mode) {
                SpotifyLoginMode.SYNCHRONIZING -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(20.dp))
                        Text(
                            "Connecting Spotify...",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Syncing your profile, playlists, and music library without restrictions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                SpotifyLoginMode.ERROR -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Rounded.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Connection Failed",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            errorMessage ?: "Could not complete Spotify sign-in.",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Try Again")
                        }
                    }
                }

                SpotifyLoginMode.WEBVIEW, SpotifyLoginMode.COOKIE_INPUT -> {
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Web Sign-In") },
                            icon = { Icon(Icons.Rounded.Language, contentDescription = null) },
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Cookie (sp_dc)") },
                            icon = { Icon(Icons.Rounded.Key, contentDescription = null) },
                        )
                    }

                    if (selectedTab == 0) {
                        SpotifyAuthWebView(
                            onCookieCaptured = onCookieCaptured,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        "How to get your sp_dc cookie:",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "1. Log in to open.spotify.com in your browser.\n" +
                                        "2. Open Developer Tools (F12) -> Application -> Cookies -> spotify.com.\n" +
                                        "3. Copy the 'sp_dc' cookie value and paste it below.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = manualCookie,
                                onValueChange = { manualCookie = it },
                                label = { Text("Spotify sp_dc Cookie") },
                                trailingIcon = {
                                    TextButton(onClick = {
                                        clipboardManager.getText()?.text?.let { text ->
                                            if (text.isNotBlank()) manualCookie = text.trim()
                                        }
                                    }) {
                                        Text("Paste")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )

                            Button(
                                onClick = { onManualCookieSubmit(manualCookie) },
                                enabled = manualCookie.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Connect with sp_dc")
                            }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SpotifyAuthWebView(
    onCookieCaptured: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isLoading by remember { mutableStateOf(true) }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    fun inspectCookies() {
                        val cookieString = cookieManager.getCookie("https://open.spotify.com")
                            ?: cookieManager.getCookie("https://accounts.spotify.com")
                            ?: cookieManager.getCookie(".spotify.com")
                            ?: return

                        val match = Regex("""(?:^|;\s*)sp_dc=([^;]+)""").find(cookieString)
                        val spDc = match?.groupValues?.getOrNull(1)
                        if (!spDc.isNullOrBlank()) {
                            onCookieCaptured(spDc)
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            isLoading = true
                            inspectCookies()
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false
                            inspectCookies()
                        }

                        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                            super.doUpdateVisitedHistory(view, url, isReload)
                            inspectCookies()
                        }
                    }

                    loadUrl("https://accounts.spotify.com/en/login")
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}
