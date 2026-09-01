package com.theveloper.pixelplay.data.recognition

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max

/**
 * Shazam DejaVu signature generator adapted from Echo Music (GPL-3.0).
 * Source: https://github.com/EchoMusicApp/Echo-Music
 */
internal object ShazamSignatureGenerator {
    private const val SAMPLE_RATE = 16_000
    private const val FFT_SIZE = 2048
    private const val FFT_OUTPUT_SIZE = FFT_SIZE / 2 + 1
    private const val MAX_PEAKS = 255
    private const val MAX_TIME_SECONDS = 12.0
    private const val RING_BUF_SIZE = 256
    private val hanning = DoubleArray(FFT_SIZE) { i ->
        0.5 * (1.0 - cos(2.0 * PI * (i + 1).toDouble() / 2049.0))
    }

    fun fromPcm16(samples: ShortArray): String {
        require(samples.isNotEmpty()) { "Audio sample is empty" }
        return State().process(samples)
    }

    private class State {
        private val samplesRing = IntArray(FFT_SIZE)
        private var samplesPos = 0
        private val fftOutputs = Array(RING_BUF_SIZE) { DoubleArray(FFT_OUTPUT_SIZE) }
        private var fftPos = 0
        private val spreadFfts = Array(RING_BUF_SIZE) { DoubleArray(FFT_OUTPUT_SIZE) }
        private var spreadPos = 0
        private var spreadNumWritten = 0
        private var numSamples = 0
        private val bandPeaks = Array(4) { mutableListOf<FrequencyPeak>() }
        private var totalPeaks = 0

        fun process(pcm: ShortArray): String {
            var offset = 0
            while (offset + 128 <= pcm.size) {
                if (numSamples.toDouble() / SAMPLE_RATE >= MAX_TIME_SECONDS && totalPeaks >= MAX_PEAKS) break
                numSamples += 128
                for (k in offset until offset + 128) {
                    samplesRing[samplesPos] = pcm[k].toInt()
                    samplesPos = (samplesPos + 1) % FFT_SIZE
                }
                doFft()
                doPeakSpreading()
                if (spreadNumWritten >= 47) doPeakRecognition()
                offset += 128
            }
            check(totalPeaks > 0) { "No usable audio signature peaks" }
            return encodeSignature()
        }

        private fun doFft() {
            val windowed = DoubleArray(FFT_SIZE) { i ->
                samplesRing[(samplesPos + i) % FFT_SIZE].toDouble() * hanning[i]
            }
            computeRfft(windowed).copyInto(fftOutputs[fftPos])
            fftPos = (fftPos + 1) % RING_BUF_SIZE
        }

        private fun doPeakSpreading() {
            val spread = fftOutputs[(fftPos - 1 + RING_BUF_SIZE) % RING_BUF_SIZE].copyOf()
            for (pos in 0 until FFT_OUTPUT_SIZE - 2) {
                spread[pos] = maxOf(spread[pos], spread[pos + 1], spread[pos + 2])
            }
            for (pos in 0 until FFT_OUTPUT_SIZE) {
                var maxValue = spread[pos]
                for (offset in intArrayOf(-1, -3, -6)) {
                    val idx = (spreadPos + offset + RING_BUF_SIZE) % RING_BUF_SIZE
                    maxValue = maxOf(maxValue, spreadFfts[idx][pos])
                    spreadFfts[idx][pos] = maxValue
                }
            }
            spread.copyInto(spreadFfts[spreadPos])
            spreadPos = (spreadPos + 1) % RING_BUF_SIZE
            spreadNumWritten++
        }

        private fun doPeakRecognition() {
            val fft = fftOutputs[(fftPos - 46 + RING_BUF_SIZE * 2) % RING_BUF_SIZE]
            val spread = spreadFfts[(spreadPos - 49 + RING_BUF_SIZE * 2) % RING_BUF_SIZE]
            val otherOffsets = intArrayOf(-53, -45, 165, 172, 179, 186, 193, 200, 214, 221, 228, 235, 242, 249)
            for (bin in 10 until FFT_OUTPUT_SIZE - 8) {
                val value = fft[bin]
                if (value < 1.0 / 64.0 || value < spread[bin]) continue
                var neighborMax = 0.0
                for (offset in intArrayOf(-10, -7, -4, -3, 1, 2, 5, 8)) neighborMax = maxOf(neighborMax, spread[bin + offset])
                if (value <= neighborMax) continue
                for (offset in otherOffsets) {
                    val idx = (spreadPos + offset + RING_BUF_SIZE) % RING_BUF_SIZE
                    neighborMax = maxOf(neighborMax, spreadFfts[idx][bin - 1])
                }
                if (value <= neighborMax) continue
                val peak = ln(max(1.0 / 64.0, value)) * 1477.3 + 6144
                val before = ln(max(1.0 / 64.0, fft[bin - 1])) * 1477.3 + 6144
                val after = ln(max(1.0 / 64.0, fft[bin + 1])) * 1477.3 + 6144
                val variation = peak * 2 - before - after
                if (variation == 0.0) continue
                val correctedBin = bin * 64.0 + (after - before) * 32 / variation
                val frequency = correctedBin * (16000.0 / 2.0 / 1024.0 / 64.0)
                val band = when {
                    frequency < 250 -> continue
                    frequency < 520 -> 0
                    frequency < 1450 -> 1
                    frequency < 3500 -> 2
                    frequency <= 5500 -> 3
                    else -> continue
                }
                bandPeaks[band] += FrequencyPeak(spreadNumWritten - 46, peak.toInt(), correctedBin.toInt())
                totalPeaks++
            }
        }

        private fun encodeSignature(): String {
            val contents = ByteArrayOutputStream()
            for (band in 0..3) {
                val peaks = bandPeaks[band]
                if (peaks.isEmpty()) continue
                val payload = ByteArrayOutputStream()
                var previousPass = 0
                for (peak in peaks) {
                    val difference = peak.pass - previousPass
                    if (difference >= 255) {
                        payload.write(0xff)
                        write32(payload, peak.pass)
                        previousPass = peak.pass
                    }
                    payload.write(peak.pass - previousPass)
                    write16(payload, peak.magnitude)
                    write16(payload, peak.frequencyBin)
                    previousPass = peak.pass
                }
                val bytes = payload.toByteArray()
                write32(contents, 0x60030040 + band)
                write32(contents, bytes.size)
                contents.write(bytes)
                repeat((4 - bytes.size % 4) % 4) { contents.write(0) }
            }
            val body = contents.toByteArray()
            val header = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(0xcafe2580.toInt()); putInt(0); putInt(body.size + 8); putInt(0x94119c00.toInt())
                putInt(0); putInt(0); putInt(0); putInt(3 shl 27); putInt(0); putInt(0)
                putInt((numSamples + SAMPLE_RATE * 0.24).toInt()); putInt((15 shl 19) + 0x40000)
            }.array()
            val output = ByteArrayOutputStream(56 + body.size).apply {
                write(header); write32(this, 0x40000000); write32(this, body.size + 8); write(body)
            }.toByteArray()
            val crc = CRC32().apply { update(output, 8, output.size - 8) }.value.toInt()
            output[4] = crc.toByte(); output[5] = (crc ushr 8).toByte()
            output[6] = (crc ushr 16).toByte(); output[7] = (crc ushr 24).toByte()
            return "data:audio/vnd.shazam.sig;base64,${Base64.encodeToString(output, Base64.NO_WRAP)}"
        }
    }

    private data class FrequencyPeak(val pass: Int, val magnitude: Int, val frequencyBin: Int)
    private fun write32(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xff); out.write(value ushr 8 and 0xff); out.write(value ushr 16 and 0xff); out.write(value ushr 24 and 0xff)
    }
    private fun write16(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xff); out.write(value ushr 8 and 0xff)
    }

    private fun computeRfft(windowed: DoubleArray): DoubleArray {
        val n = windowed.size
        val re = windowed.copyOf()
        val im = DoubleArray(n)
        var j = 0
        for (i in 1 until n) {
            var bit = n ushr 1
            while (j and bit != 0) { j = j xor bit; bit = bit ushr 1 }
            j = j xor bit
            if (i < j) {
                var temp = re[i]; re[i] = re[j]; re[j] = temp
                temp = im[i]; im[i] = im[j]; im[j] = temp
            }
        }
        var length = 2
        while (length <= n) {
            val half = length ushr 1
            val angle = -PI / half
            val baseRe = cos(angle)
            val baseIm = kotlin.math.sin(angle)
            var i = 0
            while (i < n) {
                var wRe = 1.0; var wIm = 0.0
                for (k in 0 until half) {
                    val u = i + k; val v = u + half
                    val oddRe = re[v] * wRe - im[v] * wIm
                    val oddIm = re[v] * wIm + im[v] * wRe
                    val evenRe = re[u]; val evenIm = im[u]
                    re[u] = evenRe + oddRe; im[u] = evenIm + oddIm
                    re[v] = evenRe - oddRe; im[v] = evenIm - oddIm
                    val nextRe = wRe * baseRe - wIm * baseIm
                    wIm = wRe * baseIm + wIm * baseRe; wRe = nextRe
                }
                i += length
            }
            length = length shl 1
        }
        return DoubleArray(FFT_OUTPUT_SIZE) { index ->
            max(1e-10, (re[index] * re[index] + im[index] * im[index]) / (1 shl 17))
        }
    }
}
