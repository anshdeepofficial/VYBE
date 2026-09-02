package com.theveloper.pixelplay.presentation.links

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.MainActivityIntentContract
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MusicLinkActivity : ComponentActivity() {

    @Inject
    lateinit var musicLinkRouter: MusicLinkRouter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) {
            finish()
            return
        }

        val reelUrl = musicLinkRouter.parseSocialReelUrl(intent)
        if (reelUrl != null) {
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                action = MainActivityIntentContract.ACTION_RECOGNIZE_REEL_LINK
                putExtra(MainActivityIntentContract.EXTRA_REEL_URL, reelUrl)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(mainIntent)
            finish()
            return
        }

        val trackId = musicLinkRouter.parseIntent(intent)
        if (trackId != null) {
            // Forward to MainActivity
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                action = MainActivityIntentContract.ACTION_PLAY_MUSIC_LINK
                putExtra(MainActivityIntentContract.EXTRA_TRACK_ID, trackId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(mainIntent)
        } else {
            Toast.makeText(this, "No supported music link found", Toast.LENGTH_SHORT).show()
        }
        
        finish()
    }
}
