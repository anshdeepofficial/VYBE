package com.theveloper.pixelplay.data.recognition

object ChromaprintBridge {
    init { System.loadLibrary("vybe_recognition") }
    external fun fingerprint(samples: ShortArray, sampleRate: Int): String?
}
