package com.example.servizi

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Ricevitore per i permessi di Amministratore Dispositivo (Device Admin).
 * Serve per poter chiamare DevicePolicyManager.lockNow() e spegnere forzatamente lo schermo.
 */
class CantinaDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "Permessi di Amministratore Dispositivo Abilitati", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "Permessi di Amministratore Dispositivo Disabilitati", Toast.LENGTH_SHORT).show()
    }
}
