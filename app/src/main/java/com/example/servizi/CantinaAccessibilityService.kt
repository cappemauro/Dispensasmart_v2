package com.example.servizi

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.util.Log

/**
 * Servizio di Accessibilità per sbloccare il dispositivo simulando Swipe o input PIN/Pattern.
 * DEVE essere abilitato dall'utente nelle Impostazioni -> Accessibilità di Android.
 */
class CantinaAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("CantinaAccessibility", "Servizio connesso. Pronto per simulare gli sblocchi.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Logica per intercettare la Lock Screen e inserire il PIN / Pattern.
        // Esempio: se rileviamo la schermata di sblocco (es. com.android.systemui)
        if (event?.packageName == "com.android.systemui") {
            Log.d("CantinaAccessibility", "Rilevata schermata di sistema (possibile lockscreen)")
            // Qui andrebbe inserita la logica di UIAutomator / Node interaction
            // per cercare la tastiera del PIN o fare lo swipe.
        }
    }

    override fun onInterrupt() {
        Log.d("CantinaAccessibility", "Servizio interrotto")
    }

    /**
     * Esempio di swipe programmatico (per sblocco Scorrimento o Pattern)
     */
    fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        
        val stroke = GestureDescription.StrokeDescription(path, 0, 300)
        val gestureBuilder = GestureDescription.Builder().addStroke(stroke)
        
        dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.d("CantinaAccessibility", "Swipe completato con successo")
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.d("CantinaAccessibility", "Swipe annullato")
            }
        }, null)
    }
}
