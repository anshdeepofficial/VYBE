package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.recognition.SongRecognitionLauncher

@Composable
fun SongRecognitionScreen(paddingValues: PaddingValues) {
    val context = LocalContext.current
    var launched by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(paddingValues).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.Mic, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(18.dp))
        Text("Recognize a song", style = MaterialTheme.typography.headlineMedium)
        Text(if (launched) "Listening through your device recognition service" else "Tap Listen when you are ready")
        Spacer(Modifier.height(18.dp))
        Button(onClick = { launched = SongRecognitionLauncher.launch(context) }) { Text("Listen") }
    }
}
