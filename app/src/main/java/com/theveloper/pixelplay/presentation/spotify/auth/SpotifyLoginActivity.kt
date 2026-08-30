package com.theveloper.pixelplay.presentation.spotify.auth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.theveloper.pixelplay.data.spotify.SpotifyAccountRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SpotifyLoginActivity : ComponentActivity() {
    @Inject lateinit var repository: SpotifyAccountRepository

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
            .onSuccess { startActivity(Intent(Intent.ACTION_VIEW, it)) }
            .onFailure {
                Toast.makeText(this, it.message, Toast.LENGTH_LONG).show()
                finish()
            }
    }
}
