package com.tgca.collection.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/** Sons usados pelos dois jogos. */
enum class Snd {
    EAT, DIE,
    MOVE, ROTATE, FALL, SOFT_DROP, HARD_DROP, LOCK, HOLD, SWAP,
    CLEAR1, CLEAR2, CLEAR3, CLEAR4, TSPIN,
    START, GAME_OVER, LEVEL_UP, COMBO, RECORD, UI, WRONG
}

/**
 * Sintetizador de efeitos sonoros (AudioTrack, 44,1 kHz, mono, 16 bits).
 * Reproduz as frequências dos efeitos originais dos jogos sem nenhum arquivo
 * de áudio externo — o aplicativo continua 100% offline e sem permissões.
 */
object Sfx {
    private const val RATE = 44100
    private const val VOLUME = 0.22f
    private const val ATTACK_MS = 4.0
    private const val RELEASE_MS = 12.0

    @Volatile
    var muted: Boolean = false

    private class Tone(
        val f0: Double,
        val f1: Double,
        val ms: Int,
        val wave: Int, // 0 seno, 1 quadrada, 2 triângulo, 3 dente-de-serra
        val gain: Float,
        val delayMs: Int = 0,
    )

    private val track: AudioTrack?
    private val thread = HandlerThread("tgca-sfx").apply { start() }
    private val handler: Handler
    private val lastPlayed = ConcurrentHashMap<Snd, Long>()

    init {
        handler = Handler(thread.looper)
        val minBuf = AudioTrack.getMinBufferSize(
            RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(max(minBuf, RATE))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (t: Throwable) {
            null
        }
    }

    private val throttleMs: Map<Snd, Long> = mapOf(
        Snd.MOVE to 35L, Snd.FALL to 80L, Snd.SOFT_DROP to 45L, Snd.ROTATE to 50L,
        Snd.HOLD to 100L, Snd.SWAP to 100L, Snd.WRONG to 400L,
    )

    fun play(snd: Snd) {
        if (muted) return
        val th = throttleMs[snd] ?: 0L
        if (th > 0L) {
            val now = SystemClock.elapsedRealtime()
            val last = lastPlayed[snd] ?: 0L
            if (now - last < th) return
            lastPlayed[snd] = now
        }
        val tones = score(snd)
        handler.post { write(tones) }
    }

    private fun score(snd: Snd): List<Tone> = when (snd) {
        Snd.EAT -> listOf(Tone(520.0, 520.0, 90, 0, 1.0f))
        Snd.DIE -> listOf(Tone(180.0, 140.0, 260, 0, 1.4f))
        Snd.MOVE -> listOf(Tone(180.0, 240.0, 28, 1, 0.7f))
        Snd.ROTATE -> listOf(Tone(420.0, 740.0, 65, 1, 0.9f))
        Snd.FALL -> listOf(Tone(100.0, 80.0, 25, 2, 0.55f))
        Snd.SOFT_DROP -> listOf(Tone(140.0, 90.0, 35, 2, 1.35f))
        Snd.HARD_DROP -> listOf(
            Tone(480.0, 60.0, 130, 3, 0.8f),
            Tone(85.0, 42.0, 110, 2, 2.5f),
        )
        Snd.LOCK -> listOf(Tone(110.0, 55.0, 75, 2, 2.0f))
        Snd.HOLD -> listOf(Tone(420.0, 660.0, 60, 1, 0.8f))
        Snd.SWAP -> listOf(Tone(660.0, 420.0, 60, 1, 0.8f))
        Snd.CLEAR1 -> listOf(
            Tone(523.0, 523.0, 55, 1, 0.8f),
            Tone(659.0, 659.0, 55, 1, 0.8f, 58),
        )
        Snd.CLEAR2 -> listOf(
            Tone(523.0, 523.0, 55, 1, 0.85f),
            Tone(659.0, 659.0, 55, 1, 0.85f, 58),
            Tone(784.0, 784.0, 70, 1, 0.9f, 116),
        )
        Snd.CLEAR3 -> listOf(
            Tone(523.0, 523.0, 50, 1, 0.9f),
            Tone(659.0, 659.0, 50, 1, 0.9f, 52),
            Tone(784.0, 784.0, 50, 1, 0.95f, 104),
            Tone(1047.0, 1047.0, 85, 1, 1.0f, 156),
        )
        Snd.CLEAR4 -> listOf(
            Tone(523.0, 523.0, 50, 1, 0.9f),
            Tone(659.0, 659.0, 50, 1, 0.9f, 52),
            Tone(784.0, 784.0, 50, 1, 0.95f, 104),
            Tone(1047.0, 1047.0, 110, 1, 1.1f, 156),
        )
        Snd.TSPIN -> listOf(
            Tone(587.0, 587.0, 50, 1, 0.9f),
            Tone(880.0, 880.0, 60, 1, 1.0f, 55),
        )
        Snd.START -> listOf(
            Tone(262.0, 262.0, 80, 2, 1.0f),
            Tone(330.0, 330.0, 80, 2, 1.0f, 85),
            Tone(523.0, 523.0, 110, 2, 1.1f, 170),
        )
        Snd.GAME_OVER -> listOf(
            Tone(392.0, 392.0, 110, 2, 1.0f),
            Tone(330.0, 330.0, 110, 2, 1.0f, 120),
            Tone(262.0, 262.0, 110, 2, 1.0f, 240),
            Tone(196.0, 196.0, 190, 2, 1.2f, 360),
        )
        Snd.LEVEL_UP -> listOf(
            Tone(523.0, 523.0, 70, 2, 1.0f),
            Tone(659.0, 659.0, 70, 2, 1.0f, 75),
            Tone(784.0, 784.0, 70, 2, 1.05f, 150),
            Tone(1047.0, 1047.0, 100, 2, 1.1f, 225),
        )
        Snd.COMBO -> listOf(
            Tone(659.0, 659.0, 60, 2, 1.0f),
            Tone(784.0, 784.0, 60, 2, 1.05f, 65),
            Tone(1047.0, 1047.0, 90, 2, 1.1f, 130),
        )
        Snd.RECORD -> listOf(
            Tone(784.0, 784.0, 70, 2, 1.0f),
            Tone(1047.0, 1047.0, 70, 2, 1.05f, 75),
            Tone(1568.0, 1568.0, 120, 2, 1.15f, 150),
        )
        Snd.UI -> listOf(Tone(740.0, 620.0, 35, 2, 0.9f))
        Snd.WRONG -> listOf(Tone(140.0, 140.0, 120, 3, 0.9f))
    }

    private fun waveValue(wave: Int, phase: Double): Double = when (wave) {
        1 -> if (phase < 0.5) 1.0 else -1.0
        2 -> 4.0 * abs(phase - 0.5) - 1.0
        3 -> 2.0 * (phase - phase.toInt()) - 1.0
        else -> sin(2.0 * Math.PI * phase)
    }

    private fun write(tones: List<Tone>) {
        val t = track ?: return
        if (tones.isEmpty()) return
        val totalMs = tones.maxOf { it.delayMs + it.ms }
        val n = (totalMs * RATE / 1000.0).toInt()
        val buf = ShortArray(n)
        for (tone in tones) {
            val start = (tone.delayMs * RATE / 1000.0).toInt()
            val len = (tone.ms * RATE / 1000.0).toInt()
            val attack = (ATTACK_MS * RATE / 1000.0).toInt()
            val release = (RELEASE_MS * RATE / 1000.0).toInt()
            for (i in 0 until len) {
                val idx = start + i
                if (idx >= n) break
                val p = i.toDouble() / len.toDouble()
                val freq = tone.f0 + (tone.f1 - tone.f0) * p
                val phase = (freq * i / RATE) % 1.0
                var env = 1.0
                if (attack > 0 && i < attack) env = i.toDouble() / attack
                val tailStart = len - release
                if (release > 0 && i > tailStart) env *= (len - i).toDouble() / release
                val v = waveValue(tone.wave, phase) * env * tone.gain * VOLUME
                buf[idx] = (buf[idx] + (v * Short.MAX_VALUE * 0.9)).toInt().toShort()
            }
        }
        try {
            if (t.playState != AudioTrack.PLAYSTATE_PLAYING) t.play()
            t.write(buf, 0, n)
        } catch (_: Throwable) {
        }
    }
}
