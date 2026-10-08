package com.example.servizi

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.example.model.Prodotto
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

data class GoogleDriveState(
    val selectedAccount: String = "cappe88it@gmail.com",
    val isAutoSyncEnabled: Boolean = true,
    val lastBackupTimestamp: Long = System.currentTimeMillis(),
    val totalItemsBackedUp: Int = 0,
    val driveStorageUsedKb: Double = 14.5
)

class BackupExportService(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("dispensa_drive_backup_prefs", Context.MODE_PRIVATE)

    private val _driveState = MutableStateFlow(
        GoogleDriveState(
            selectedAccount = prefs.getString("google_account", "cappe88it@gmail.com") ?: "cappe88it@gmail.com",
            isAutoSyncEnabled = prefs.getBoolean("auto_sync_drive", true),
            lastBackupTimestamp = prefs.getLong("last_backup_time", System.currentTimeMillis()),
            totalItemsBackedUp = prefs.getInt("total_items_backed_up", 0)
        )
    )
    val driveState: StateFlow<GoogleDriveState> = _driveState

    // In-memory Drive cloud vault keyed by account email
    companion object {
        private val cloudDriveVault = mutableMapOf<String, String>()
    }

    fun setSelectedAccount(email: String) {
        if (email.isNotBlank()) {
            prefs.edit().putString("google_account", email.trim()).apply()
            _driveState.value = _driveState.value.copy(selectedAccount = email.trim())
        }
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_sync_drive", enabled).apply()
        _driveState.value = _driveState.value.copy(isAutoSyncEnabled = enabled)
    }

    fun exportToJson(prodotti: List<Prodotto>): String {
        val jsonArray = JSONArray()
        for (p in prodotti) {
            val obj = JSONObject().apply {
                put("id", p.id)
                put("barcode", p.barcode)
                put("nome", p.nome)
                put("categoria", p.categoria)
                put("quantita", p.quantita)
                put("quantitaMinima", p.quantitaMinima)
                put("dataScadenza", p.dataScadenza)
                put("posizione", p.posizione)
                put("note", p.note)
                put("inListaSpesa", p.inListaSpesa)
                put("comprato", p.comprato)
            }
            jsonArray.put(obj)
        }
        val wrapper = JSONObject().apply {
            put("app", "DispensaSmart")
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            put("prodotti", jsonArray)
        }
        return wrapper.toString(2)
    }

    fun parseJsonToProdotti(jsonString: String): List<Prodotto> {
        val resultList = mutableListOf<Prodotto>()
        val wrapper = JSONObject(jsonString)
        val jsonArray = if (wrapper.has("prodotti")) wrapper.getJSONArray("prodotti") else JSONArray(jsonString)

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val p = Prodotto(
                id = if (obj.has("id")) obj.getLong("id") else 0L,
                barcode = obj.optString("barcode", ""),
                nome = obj.getString("nome"),
                categoria = obj.optString("categoria", "Dispensa Secca"),
                quantita = obj.optInt("quantita", 1),
                quantitaMinima = obj.optInt("quantitaMinima", 1),
                dataScadenza = obj.optLong("dataScadenza", System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000L)),
                posizione = obj.optString("posizione", "Dispensa"),
                note = obj.optString("note", ""),
                inListaSpesa = obj.optBoolean("inListaSpesa", false),
                comprato = obj.optBoolean("comprato", false)
            )
            resultList.add(p)
        }
        return resultList
    }

    fun writeJsonToUri(uri: Uri, jsonContent: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(jsonContent.toByteArray(Charsets.UTF_8))
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun readJsonFromUri(uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun syncBackupToGoogleDriveCloud(prodotti: List<Prodotto>): Boolean {
        return try {
            val db = FirebaseFirestore.getInstance()
            val account = _driveState.value.selectedAccount
            val jsonContent = exportToJson(prodotti)
            
            val docData = hashMapOf(
                "backupJson" to jsonContent,
                "timestamp" to System.currentTimeMillis()
            )
            
            db.collection("backups").document(account).set(docData).await()
            
            val now = System.currentTimeMillis()
            val kbSize = jsonContent.length / 1024.0
            
            prefs.edit()
                .putLong("last_backup_time", now)
                .putInt("total_items_backed_up", prodotti.size)
                .apply()
                
            _driveState.value = _driveState.value.copy(
                lastBackupTimestamp = now,
                totalItemsBackedUp = prodotti.size,
                driveStorageUsedKb = String.format("%.1f", kbSize.coerceAtLeast(0.5)).toDoubleOrNull() ?: 1.0
            )
            true
        } catch (e: Exception) {
            Log.e("BackupService", "Errore backup su Firebase: ${e.message}")
            false
        }
    }

    suspend fun fetchBackupFromGoogleDriveCloud(): List<Prodotto>? {
        return try {
            val db = FirebaseFirestore.getInstance()
            val account = _driveState.value.selectedAccount
            
            val snapshot = db.collection("backups").document(account).get().await()
            val json = snapshot.getString("backupJson") ?: return null
            
            parseJsonToProdotti(json)
        } catch (e: Exception) {
            Log.e("BackupService", "Errore fetch da Firebase: ${e.message}")
            null
        }
    }
}
