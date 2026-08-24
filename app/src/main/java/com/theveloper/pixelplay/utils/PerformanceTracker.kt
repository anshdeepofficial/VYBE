package com.theveloper.pixelplay.utils

import android.util.Log

object PerformanceTracker {
    private const val TAG = "PlaybackPerformance"
    private var t0 = 0L
    private var t1 = 0L
    private var t2 = 0L
    private var t3 = 0L
    private var t4 = 0L
    private var t5 = 0L
    private var t6 = 0L
    private var t7 = 0L
    private var t8 = 0L
    private var t9 = 0L
    
    fun start(timestamp: Long = System.currentTimeMillis()) {
        t0 = timestamp
        Log.d(TAG, "T0 - User tap: 0ms")
    }
    
    fun markT1(timestamp: Long = System.currentTimeMillis()) {
        t1 = timestamp
        Log.d(TAG, "T1 - Dispatch to service: ${t1 - t0}ms")
    }
    
    fun markT2(timestamp: Long = System.currentTimeMillis()) {
        t2 = timestamp
        Log.d(TAG, "T2 - Resolver start: ${t2 - t0}ms")
    }
    
    fun markT3(timestamp: Long = System.currentTimeMillis()) {
        t3 = timestamp
        Log.d(TAG, "T3 - Resolver finish: ${t3 - t0}ms")
    }
    
    fun markT4(timestamp: Long = System.currentTimeMillis()) {
        t4 = timestamp
        Log.d(TAG, "T4 - MediaItem ready: ${t4 - t0}ms")
    }
    
    fun markT5(timestamp: Long = System.currentTimeMillis()) {
        t5 = timestamp
        Log.d(TAG, "T5 - MediaItem sent to player: ${t5 - t0}ms")
    }
    
    fun markT6(timestamp: Long = System.currentTimeMillis()) {
        t6 = timestamp
        Log.d(TAG, "T6 - prepare(): ${t6 - t0}ms")
    }
    
    fun markT7(timestamp: Long = System.currentTimeMillis()) {
        t7 = timestamp
        Log.d(TAG, "T7 - buffering: ${t7 - t0}ms")
    }

    fun markT8(timestamp: Long = System.currentTimeMillis()) {
        t8 = timestamp
        Log.d(TAG, "T8 - ready: ${t8 - t0}ms")
    }

    fun markT9(timestamp: Long = System.currentTimeMillis()) {
        t9 = timestamp
        Log.d(TAG, "T9 - playback starts: ${t9 - t0}ms")
        
        Log.d(TAG, "--- FINAL PERFORMANCE REPORT ---")
        Log.d(TAG, "Tap -> dispatch (T0->T1): ${t1 - t0}ms")
        Log.d(TAG, "Resolver duration (T2->T3): ${t3 - t2}ms")
        Log.d(TAG, "MediaItem creation to Player (T3->T5): ${t5 - t3}ms")
        Log.d(TAG, "Player prepare -> READY (T6->T8): ${t8 - t6}ms")
        Log.d(TAG, "Total T0->T9 (Playback Starts): ${t9 - t0}ms")
        Log.d(TAG, "--------------------------------")
    }
}
