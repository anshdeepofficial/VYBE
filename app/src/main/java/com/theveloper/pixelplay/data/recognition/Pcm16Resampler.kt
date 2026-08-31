package com.theveloper.pixelplay.data.recognition

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

object Pcm16Resampler {
    suspend fun to16KhzMono(input: CapturedPcm): ShortArray = withContext(Dispatchers.Default) {
        require(input.samples.isNotEmpty() && input.sampleRate > 0)
        if (input.sampleRate == 16_000) return@withContext input.samples
        val outputSize = (input.samples.size.toLong() * 16_000L / input.sampleRate).toInt()
        require(outputSize > 0)
        ShortArray(outputSize) { index ->
            if (index and 0x3ff == 0) coroutineContext.ensureActive()
            val source = index.toDouble() * input.sampleRate / 16_000.0
            val low = source.toInt().coerceIn(0, input.samples.lastIndex)
            val high = (low + 1).coerceAtMost(input.samples.lastIndex)
            val fraction = source - low
            (input.samples[low] * (1.0 - fraction) + input.samples[high] * fraction)
                .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }
}

