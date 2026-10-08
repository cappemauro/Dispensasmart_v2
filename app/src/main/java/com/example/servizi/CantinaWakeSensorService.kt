package com.example.servizi

import android.app.KeyguardManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log

/**
 * Servizio in background che ascolta il sensore di luminosità o gestisce la fotocamera (tramite
 * CameraX/MLKit in futuro) per risvegliare il dispositivo in "Modalità Cantina".
 */
class CantinaWakeSensorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private lateinit var powerManager: PowerManager
    private lateinit var keyguardManager: KeyguardManager

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        // Registra il sensore di luminosità
        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d("CantinaWakeSensor", "Sensore di luminosità registrato")
        } ?: run {
            Log.e("CantinaWakeSensor", "Sensore di luminosità non disponibile")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Qui andrebbe la logica per distinguere l'orario e avviare il sensore giusto
        // (Sensore di luce di Notte vs. Fotocamera di Giorno)
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
            val lux = event.values[0]
            Log.d("CantinaWakeSensor", "Livello luce: $lux lux")
            
            // Se la luce supera una certa soglia (es. qualcuno ha acceso la luce in cantina)
            if (lux > 10.0f) {
                wakeUpScreen()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Non necessario
    }

    private fun wakeUpScreen() {
        // Logica per accendere lo schermo
        if (!powerManager.isInteractive) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "DispensaSmart::CantinaWakeLock"
            )
            wakeLock?.acquire(3000) // Accendi per 3 secondi per dare il tempo di sbloccare
            Log.d("CantinaWakeSensor", "Schermo acceso tramite WakeLock")
            
            // Per lo sblocco, se non c'è PIN/Pattern (Nessuno o Swipe)
            // Se c'è il PIN, l'AccessibilityService dovrà intervenire
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
