package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlin.concurrent.thread
import kotlin.math.sin

/**
 * Gestore feedback sonoro e aptico per la scansione barcode.
 * Genera il classico beep ad alta frequenza (2400 Hz) tipico degli scanner laser da supermercato
 * tramite AudioTrack PCM nativo, con ToneGenerator e RingtoneManager come ridondanza.
 */
object BarcodeSoundFeedback {
    private var toneGenerator: ToneGenerator? = null
    private var cachedPcmBeep: ByteArray? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        } catch (_: Exception) {
            try {
                toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            } catch (_: Exception) {}
        }

        try {
            cachedPcmBeep = generatePcmBeep(durationMs = 120, frequencyHz = 2400)
        } catch (e: Exception) {
            Log.w("BarcodeSoundFeedback", "Impossibile precalcolare PCM beep: ${e.message}")
        }
    }

    /**
     * Genera un'onda sinusoidale pura a 16-bit PCM con rampa di attacco e decadimento
     * per evitare click sonori all'inizio e alla fine.
     */
    private fun generatePcmBeep(durationMs: Int, frequencyHz: Int, sampleRate: Int = 44100): ByteArray {
        val numSamples = (durationMs * sampleRate) / 1000
        val sample = ByteArray(numSamples * 2)
        val rampSamples = (0.015 * sampleRate).toInt() // 15ms fade-in/fade-out

        for (i in 0 until numSamples) {
            val angle = 2.0 * Math.PI * i / (sampleRate / frequencyHz)
            var amplitude = 0.95
            if (i < rampSamples) {
                amplitude *= (i.toDouble() / rampSamples)
            } else if (i > numSamples - rampSamples) {
                amplitude *= ((numSamples - i).toDouble() / rampSamples)
            }
            val sampleVal = (sin(angle) * amplitude * 32767).toInt().toShort()
            sample[2 * i] = (sampleVal.toInt() and 0xFF).toByte()
            sample[2 * i + 1] = ((sampleVal.toInt() shr 8) and 0xFF).toByte()
        }
        return sample
    }

    /**
     * Riproduce il beep sintetizzato tramite AudioTrack in un thread asincrono dedicato.
     */
    private fun playPcmBeep() {
        thread(name = "barcode_beep_thread", isDaemon = true) {
            try {
                val pcm = cachedPcmBeep ?: generatePcmBeep(120, 2400).also { cachedPcmBeep = it }
                val sampleRate = 44100
                val minBufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = maxOf(pcm.size, minBufferSize)

                val audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(bufferSize)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize,
                        AudioTrack.MODE_STATIC
                    )
                }

                audioTrack.write(pcm, 0, pcm.size)
                audioTrack.play()
                Thread.sleep(130)
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                Log.w("BarcodeSoundFeedback", "Errore riproduzione AudioTrack beep: ${e.message}")
            }
        }
    }

    /**
     * Riproduce il classico "BEEP" da scanner laser e attiva la vibrazione aptica.
     */
    fun playScanBeep(context: Context? = null) {
        // 1. Suono acustico PCM reale diretto da 2400Hz (funziona sia su emulatore che su dispositivo)
        playPcmBeep()

        // 2. Beep di sistema ToneGenerator (TONE_CDMA_PIP scanner POS tipico da cassa)
        try {
            val tg = toneGenerator ?: ToneGenerator(AudioManager.STREAM_MUSIC, 100).also { toneGenerator = it }
            tg.startTone(ToneGenerator.TONE_CDMA_PIP, 120)
        } catch (_: Exception) {
            try {
                ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100).startTone(ToneGenerator.TONE_PROP_ACK, 120)
            } catch (_: Exception) {}
        }

        // 3. Fallback Ringtone
        if (context != null) {
            try {
                val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(context.applicationContext ?: context, notificationUri)
                ringtone?.play()
            } catch (_: Exception) {}
        }

        // 4. Feedback tattile vibrazione
        if (context != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vibratorManager?.defaultVibrator?.vibrate(
                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(70)
                    }
                }
            } catch (e: Exception) {
                Log.w("BarcodeSoundFeedback", "Vibrazione non disponibile: ${e.message}")
            }
        }
    }
}
