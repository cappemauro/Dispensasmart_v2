package com.example.servizi

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.BuildConfig
import com.example.model.Prodotto
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class SyncDevice(
    val deviceId: String,
    val deviceName: String,
    val isCurrentDevice: Boolean = false,
    val lastActiveTimestamp: Long = System.currentTimeMillis()
)

data class SyncLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val deviceName: String,
    val actionText: String
)

data class SyncStatusState(
    val isConnected: Boolean = true,
    val isSyncing: Boolean = false,
    val pantryCode: String = "DISPENSA-FAMIGLIA-4819",
    val activeDevicesCount: Int = 1,
    val lastSyncedTime: Long = System.currentTimeMillis(),
    val devices: List<SyncDevice> = emptyList(),
    val syncLogs: List<SyncLogEntry> = emptyList()
)

class SyncService(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("dispensa_sync_prefs", Context.MODE_PRIVATE)

    val currentDeviceId: String = prefs.getString("device_id", null) ?: UUID.randomUUID().toString().take(8).also {
        prefs.edit().putString("device_id", it).apply()
    }

    private val defaultPantryCode = "DISPENSA-FAMIGLIA-" + (1000..9999).random()

    var pantryCode: String
        get() = prefs.getString("pantry_code", defaultPantryCode) ?: defaultPantryCode
        set(value) {
            prefs.edit().putString("pantry_code", value).apply()
            _syncState.value = _syncState.value.copy(pantryCode = value)
        }

    private val _syncState = MutableStateFlow(
        SyncStatusState(
            pantryCode = pantryCode,
            devices = listOf(
                SyncDevice(currentDeviceId, "Questo Dispositivo", isCurrentDevice = true)
            ),
            syncLogs = listOf(
                SyncLogEntry(deviceName = "Sistema", actionText = "Inizializzata sincronizzazione Firebase")
            )
        )
    )
    val syncState: MutableStateFlow<SyncStatusState> = _syncState

    private var firestore: FirebaseFirestore? = null

    init {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val apiKey = BuildConfig::class.java.getField("FIREBASE_API_KEY").get(null) as? String
                val appId = BuildConfig::class.java.getField("FIREBASE_APP_ID").get(null) as? String
                val projectId = BuildConfig::class.java.getField("FIREBASE_PROJECT_ID").get(null) as? String
                
                if (!apiKey.isNullOrBlank() && !appId.isNullOrBlank() && !projectId.isNullOrBlank()) {
                    val options = FirebaseOptions.Builder()
                        .setApiKey(apiKey)
                        .setApplicationId(appId)
                        .setProjectId(projectId)
                        .build()
                    FirebaseApp.initializeApp(context, options)
                }
            }
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                firestore = FirebaseFirestore.getInstance()
                Log.d("SyncService", "Firebase inizializzato correttamente.")
            } else {
                Log.e("SyncService", "Firebase NON inizializzato. Controlla i Secrets in AI Studio.")
            }
        } catch (e: Exception) {
            Log.e("SyncService", "Errore nell'inizializzazione di Firebase: ${e.message}")
        }
    }

    fun addSyncLog(deviceName: String, action: String) {
        val entry = SyncLogEntry(deviceName = deviceName, actionText = action)
        val currentLogs = _syncState.value.syncLogs.toMutableList()
        currentLogs.add(0, entry)
        if (currentLogs.size > 20) currentLogs.removeAt(currentLogs.lastIndex)
        _syncState.value = _syncState.value.copy(
            syncLogs = currentLogs,
            lastSyncedTime = System.currentTimeMillis()
        )
    }

    suspend fun pushLocalToCloud(pantryCode: String, localProdotti: List<Prodotto>) {
        _syncState.value = _syncState.value.copy(isSyncing = true)
        
        val db = firestore
        if (db != null) {
            try {
                val batch = db.batch()
                val collRef = db.collection("dispense").document(pantryCode).collection("prodotti")
                
                // Aggiorniamo ogni prodotto come documento
                for (prod in localProdotti) {
                    val docRef = collRef.document(prod.id.toString())
                    batch.set(docRef, prod, SetOptions.merge())
                }
                
                batch.commit().await()
                Log.d("SyncService", "Sincronizzazione Cloud completata per $pantryCode")
                _syncState.value = _syncState.value.copy(
                    isSyncing = false,
                    lastSyncedTime = System.currentTimeMillis(),
                    isConnected = true
                )
            } catch (e: Exception) {
                Log.e("SyncService", "Errore nel push: ${e.message}")
                _syncState.value = _syncState.value.copy(isSyncing = false, isConnected = false)
            }
        } else {
            _syncState.value = _syncState.value.copy(isSyncing = false, isConnected = false)
        }
    }

    suspend fun deleteCloudProdotto(pantryCode: String, prodottoId: Long) {
        val db = firestore ?: return
        try {
            db.collection("dispense").document(pantryCode).collection("prodotti")
                .document(prodottoId.toString())
                .delete()
                .await()
            Log.d("SyncService", "Prodotto $prodottoId eliminato dal cloud.")
        } catch (e: Exception) {
            Log.e("SyncService", "Errore eliminazione cloud: ${e.message}")
        }
    }

    suspend fun pushProdotto(pantryCode: String, prodotto: Prodotto) {
        val db = firestore ?: return
        try {
            db.collection("dispense").document(pantryCode).collection("prodotti")
                .document(prodotto.id.toString())
                .set(prodotto, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e("SyncService", "Errore push singolo prodotto: ${e.message}")
        }
    }

    suspend fun fetchCloudProdotti(pantryCode: String): List<Prodotto> {
        val db = firestore ?: return emptyList()
        return try {
            val collRef = db.collection("dispense").document(pantryCode).collection("prodotti")
            val snapshot = collRef.get().await()
            val list = mutableListOf<Prodotto>()
            for (doc in snapshot.documents) {
                val p = doc.toObject(Prodotto::class.java)
                if (p != null) list.add(p)
            }
            list
        } catch (e: Exception) {
            Log.e("SyncService", "Errore nel fetch: ${e.message}")
            emptyList()
        }
    }

    fun updatePantryCode(newCode: String) {
        if (newCode.isNotBlank()) {
            pantryCode = newCode.trim().uppercase()
            addSyncLog("Questo Dispositivo", "Connesso alla Dispensa Condivisa: $pantryCode")
        }
    }
}
