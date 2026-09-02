package com.theveloper.pixelplay.presentation.spotify.auth

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabsIntent
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.theveloper.pixelplay.data.spotify.SpotifyAccountRepository
import com.theveloper.pixelplay.ui.theme.PixelPlayTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

private enum class SpotifyLoginStep { READY, WAITING_FOR_SPOTIFY, SYNCHRONIZING, ERROR }

/**
 * Spotify OAuth must not run in an embedded WebView. Spotify's supported browser flow avoids
 * Samsung/Nothing WebView black screens while the verified callback returns directly to VYBE.
 */
@AndroidEntryPoint
class SpotifyLoginActivity : ComponentActivity() {
    @Inject lateinit var repository: SpotifyAccountRepository

    private var step by mutableStateOf(SpotifyLoginStep.READY)
    private var message by mutableStateOf<String?>(null)
    private var authorizationUri: Uri? = null
    private var completingAuthorization = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PixelPlayTheme {
                SpotifyLoginScreen(
                    step = step,
                    message = message,
                    onBack = ::finish,
                    onContinue = ::launchSpotifyAuthorization,
                )
            }
        }
        val callback = intent.data?.takeIf(::isSpotifyCallback)
        if (callback != null) completeAuthorization(callback) else prepareAuthorization(launchNow = true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.takeIf(::isSpotifyCallback)?.let(::completeAuthorization)
    }

    private fun prepareAuthorization(launchNow: Boolean) {
        completingAuthorization = false
        message = null
        step = SpotifyLoginStep.READY
        authorizationUri = runCatching(repository::createAuthorizationUri)
            .onFailure { showError(it.message ?: "Spotify sign-in could not be started.") }
            .getOrNull()
        if (launchNow && authorizationUri != null) launchSpotifyAuthorization()
    }

    private fun launchSpotifyAuthorization() {
        val uri = authorizationUri ?: run {
            prepareAuthorization(launchNow = false)
            authorizationUri
        } ?: return
        step = SpotifyLoginStep.WAITING_FOR_SPOTIFY
        message = "Complete the secure sign-in in Spotify. You will return to VYBE automatically."
        try {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setUrlBarHidingEnabled(false)
                .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                .build()
                .launchUrl(this, uri)
        } catch (_: ActivityNotFoundException) {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                .onFailure { showError("No secure browser is available for Spotify sign-in.") }
        }
    }

    private fun isSpotifyCallback(uri: Uri): Boolean {
        if (uri.scheme.equals("vybe", true) && uri.host.equals("spotify-callback", true)) return true
        val expected = Uri.parse(SpotifyAccountRepository.REDIRECT_URI)
        return uri.scheme.equals(expected.scheme, true) &&
            uri.host.equals(expected.host, true) && uri.path == expected.path
    }

    private fun completeAuthorization(uri: Uri) {
        if (completingAuthorization) return
        completingAuthorization = true
        step = SpotifyLoginStep.SYNCHRONIZING
        message = "Loading your public, private, collaborative and followed playlists..."
        lifecycleScope.launch {
            repository.completeAuthorization(uri)
                .onSuccess {
                    runCatching { repository.getCurrentUserPlaylists() }
                        .onSuccess { playlists ->
                            Toast.makeText(
                                this@SpotifyLoginActivity,
                                "Spotify connected - ${playlists.size} playlists found",
                                Toast.LENGTH_LONG,
                            ).show()
                            setResult(RESULT_OK)
                            finish()
                        }
                        .onFailure { showError(it.message ?: "Spotify connected, but playlists could not be loaded.") }
                }
                .onFailure { showError(it.message ?: "Spotify could not complete sign-in.") }
        }
    }

    private fun showError(value: String) {
        completingAuthorization = false
        step = SpotifyLoginStep.ERROR
        message = value
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpotifyLoginScreen(
    step: SpotifyLoginStep,
    message: String?,
    onBack: () -> Unit,
    onContinue: () -> Unit,
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
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Rounded.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(18.dp))
            Text(
                when (step) {
                    SpotifyLoginStep.READY -> "Connect your Spotify account"
                    SpotifyLoginStep.WAITING_FOR_SPOTIFY -> "Waiting for Spotify"
                    SpotifyLoginStep.SYNCHRONIZING -> "Synchronizing your library"
                    SpotifyLoginStep.ERROR -> "Spotify sign-in needs attention"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                message ?: "Sign in securely to import all playlists available to your account.",
                color = if (step == SpotifyLoginStep.ERROR) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            if (step == SpotifyLoginStep.SYNCHRONIZING) {
                CircularProgressIndicator()
            } else {
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                    Text(if (step == SpotifyLoginStep.ERROR) "Try again" else "Continue to Spotify")
                }
            }
        }
    }
}
