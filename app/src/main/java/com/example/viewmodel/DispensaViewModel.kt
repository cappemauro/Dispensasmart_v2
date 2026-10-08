package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.ProdottoRepository
import com.example.model.Prodotto
import com.example.model.StatoScadenza
import com.example.servizi.AiProductResult
import com.example.servizi.AiService
import com.example.servizi.BackupExportService
import com.example.servizi.GoogleDriveState
import com.example.servizi.SyncService
import com.example.servizi.SyncStatusState
import com.example.util.BarcodeSoundFeedback
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SessionItemCarico(
    val tempId: String = java.util.UUID.randomUUID().toString(),
    val barcode: String,
    val prodottoEsistenteId: Long? = null,
    var nome: String,
    var categoria: String,
    var quantita: Int = 1,
    var dataScadenza: Long,
    var posizione: String,
    var note: String = "",
    val isAiRecognized: Boolean = false,
    val barcodeLinkedToExisting: Boolean = false
)

data class SessionItemScarico(
    val tempId: String = java.util.UUID.randomUUID().toString(),
    val barcode: String,
    val prodottoId: Long,
    val nome: String,
    val categoria: String,
    var quantitaDaScaricare: Int = 1,
    val quantitaDisponibile: Int
)

class DispensaViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ProdottoRepository
    private val aiService = AiService()
    val syncService = SyncService(application)
    val backupService = BackupExportService(application)

    val syncState: StateFlow<SyncStatusState> = syncService.syncState
    val driveState: StateFlow<GoogleDriveState> = backupService.driveState

    private val prefs = application.getSharedPreferences("dispensa_settings_prefs", Context.MODE_PRIVATE)
    val isMinQuantityAlertEnabled = MutableStateFlow(prefs.getBoolean("min_qty_alert_enabled", true))
    val isExpiryManagementEnabled = MutableStateFlow(prefs.getBoolean("expiry_management_enabled", true))
    val isKeepIntegrationEnabled = MutableStateFlow(prefs.getBoolean("keep_integration_enabled", true))
    val keepNoteTitle = MutableStateFlow(prefs.getString("keep_note_title", "Lista della Spesa") ?: "Lista della Spesa")
    val keepLastSyncMessage = MutableStateFlow<String?>(null)

    val isHeaderCompact = MutableStateFlow(prefs.getBoolean("dispensa_header_compact", false))

    fun toggleHeaderCompact() {
        val newVal = !isHeaderCompact.value
        prefs.edit().putBoolean("dispensa_header_compact", newVal).apply()
        isHeaderCompact.value = newVal
    }

    fun setHeaderCompact(compact: Boolean) {
        prefs.edit().putBoolean("dispensa_header_compact", compact).apply()
        isHeaderCompact.value = compact
    }

    fun setMinQuantityAlertEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("min_qty_alert_enabled", enabled).apply()
        isMinQuantityAlertEnabled.value = enabled
    }

    fun setExpiryManagementEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("expiry_management_enabled", enabled).apply()
        isExpiryManagementEnabled.value = enabled
    }

    fun setKeepIntegrationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("keep_integration_enabled", enabled).apply()
        isKeepIntegrationEnabled.value = enabled
    }

    val selectedSpesaFilter = MutableStateFlow("Tutti") // "Tutti", "Dispensa", "Keep"

    fun setSelectedSpesaFilter(filter: String) {
        selectedSpesaFilter.value = filter
    }

    fun setKeepNoteTitle(title: String) {
        val clean = title.trim()
        prefs.edit().putString("keep_note_title", clean).apply()
        keepNoteTitle.value = clean
        keepLastSyncMessage.value = "Titolo lista Keep impostato: '$clean'"
    }

    fun rimuoviTuttiProdottiDaKeep() {
        viewModelScope.launch {
            val all = repository.getAllProdottiList()
            var count = 0
            for (p in all) {
                if (p.daKeep) {
                    if (p.quantita <= 0) {
                        markProductAsDeleted(p.id)
                        repository.delete(p)
                        syncService.deleteCloudProdotto(syncService.pantryCode, p.id)
                    } else {
                        val updated = p.copy(inListaSpesa = false, daKeep = false)
                        repository.update(updated)
                        syncService.pushProdotto(syncService.pantryCode, updated)
                    }
                    count++
                }
            }
            keepLastSyncMessage.value = "🗑️ Rimossi $count articoli Keep dalla lista della spesa."
            batchFeedbackMessage.value = "Lista spesa Keep svuotata ($count articoli)."
            triggerAutoDriveBackup()
        }
    }

    fun sincronizzaConGoogleKeep(
        customTextContent: String? = null,
        targetTitle: String? = null,
        onResult: ((Int) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val title = (targetTitle?.trim()?.ifBlank { null } ?: keepNoteTitle.value).ifBlank { "Lista della Spesa" }
            if (customTextContent.isNullOrBlank()) {
                keepLastSyncMessage.value = "⚠️ Nessun testo inserito. Copia la tua nota da Keep o usa 'Apri Keep' per recuperarla."
                onResult?.invoke(0)
                return@launch
            }

            val items = com.example.util.GoogleKeepHelper.cleanAndExtractKeepItems(customTextContent, title)
            if (items.isEmpty()) {
                keepLastSyncMessage.value = "⚠️ Nessun articolo valido trovato nel testo della nota '$title'."
                onResult?.invoke(0)
                return@launch
            }

            var countAdded = 0
            val currentProdotti = repository.getAllProdottiList()

            for (itemText in items) {
                val existing = currentProdotti.find { it.nome.equals(itemText, ignoreCase = true) }
                if (existing != null) {
                    if (!existing.inListaSpesa || !existing.daKeep) {
                        val updated = existing.copy(
                            inListaSpesa = true,
                            daKeep = true,
                            note = if (existing.note.isBlank()) "Keep: $title" else existing.note,
                            comprato = false
                        )
                        repository.update(updated)
                        syncService.pushProdotto(syncService.pantryCode, updated)
                        countAdded++
                    }
                } else {
                    val (categoria, _) = aiService.inferisciCategoriaDaNomeEBarcode(itemText, "")
                    val newProd = Prodotto(
                        nome = itemText,
                        categoria = categoria,
                        quantita = 0,
                        quantitaMinima = 1,
                        dataScadenza = System.currentTimeMillis() + (30L * 86400000L),
                        inListaSpesa = true,
                        daKeep = true,
                        note = "Keep: $title",
                        comprato = false
                    )
                    val newId = repository.insert(newProd)
                    syncService.pushProdotto(syncService.pantryCode, newProd.copy(id = newId))
                    countAdded++
                }
            }

            if (countAdded > 0) {
                keepLastSyncMessage.value = "✅ Importati con successo $countAdded prodotti per la lista '$title'!"
                batchFeedbackMessage.value = "🛒 Aggiunti $countAdded prodotti da Keep ('$title') alla spesa!"
            } else {
                keepLastSyncMessage.value = "ℹ️ Gli articoli della nota '$title' erano già presenti nella spesa."
            }
            triggerAutoDriveBackup()
            onResult?.invoke(countAdded)
        }
    }

    fun importaProdottiDaKeepTesto(rawText: String, subject: String? = null) {
        viewModelScope.launch {
            val title = if (!subject.isNullOrBlank()) subject.trim() else keepNoteTitle.value.ifBlank { "Lista della Spesa" }
            if (!subject.isNullOrBlank()) {
                setKeepNoteTitle(subject.trim())
            }
            sincronizzaConGoogleKeep(rawText, title)
        }
    }

    val selectedCategory = MutableStateFlow("Tutti")
    val selectedTab = MutableStateFlow(0) // 0 = Tutta la Dispensa, 1 = In Scadenza (<= 3 giorni)
    val searchQuery = MutableStateFlow("")

    val selectedProdottoForEdit = MutableStateFlow<Prodotto?>(null)

    val isAnalyzingBarcode = MutableStateFlow(false)
    val isCatalogingOffline = MutableStateFlow(false)
    val barcodeScanMessage = MutableStateFlow<String?>(null)

    // Batch Multi-Scan Session Queues
    val caricoSessionQueue = MutableStateFlow<List<SessionItemCarico>>(emptyList())
    val scaricoSessionQueue = MutableStateFlow<List<SessionItemScarico>>(emptyList())
    val batchFeedbackMessage = MutableStateFlow<String?>(null)

    private val persistedDeletedIds: MutableSet<String> = (prefs.getStringSet("persisted_deleted_ids", emptySet()) ?: emptySet()).toMutableSet()
    private val deletedProductIds = mutableSetOf<Long>()

    private fun markProductAsDeleted(id: Long) {
        deletedProductIds.add(id)
        synchronized(persistedDeletedIds) {
            persistedDeletedIds.add(id.toString())
            prefs.edit().putStringSet("persisted_deleted_ids", persistedDeletedIds).apply()
        }
    }

    init {
        val database = AppDatabase.getDatabase(application)
        repository = ProdottoRepository(database.prodottoDao())

        // Carica gli ID cancellati memorizzati per evitare che il Cloud li re-inserisca
        synchronized(persistedDeletedIds) {
            for (idStr in persistedDeletedIds) {
                idStr.toLongOrNull()?.let { deletedProductIds.add(it) }
            }
        }

        // Rimozione una-tantum dei campioni mock pre-caricati per iniziare con la dispensa pulita
        val hasCleanedSamples = prefs.getBoolean("has_cleaned_default_samples_v2", false)
        if (!hasCleanedSamples) {
            viewModelScope.launch {
                val defaultSampleBarcodes = setOf(
                    "8008800123456",
                    "8001234567890",
                    "8000500000000",
                    "8000425000000",
                    "8000300000000"
                )
                val defaultSampleNames = setOf(
                    "Latte Fresco Intero",
                    "Spaghetti Barilla n.5",
                    "Passata di Pomodoro Mutti",
                    "Mozzarella Fresca",
                    "Piselli Novelli Findus"
                )
                val current = repository.getAllProdottiList()
                for (p in current) {
                    if (p.barcode in defaultSampleBarcodes || p.nome in defaultSampleNames) {
                        markProductAsDeleted(p.id)
                        repository.delete(p)
                        syncService.deleteCloudProdotto(syncService.pantryCode, p.id)
                    }
                }
                prefs.edit().putBoolean("has_cleaned_default_samples_v2", true).apply()
            }
        }

        // Rimozione una-tantum dei prodotti fittizi aggiunti erroneamente dal mock di Keep
        val hasCleanedKeepMock = prefs.getBoolean("has_cleaned_keep_mock_v4", false)
        if (!hasCleanedKeepMock) {
            viewModelScope.launch {
                val mockKeepNames = setOf(
                    "Latte Intero", "Pane Fresco", "Caffè in Polvere", "Insalata", "Marmellata",
                    "Latte", "Pane", "Caffè"
                )
                val current = repository.getAllProdottiList()
                for (p in current) {
                    if (p.daKeep && p.quantita == 0 && (p.nome in mockKeepNames || p.nome.isBlank())) {
                        markProductAsDeleted(p.id)
                        repository.delete(p)
                        syncService.deleteCloudProdotto(syncService.pantryCode, p.id)
                    }
                }
                prefs.edit().putBoolean("has_cleaned_keep_mock_v4", true).apply()
            }
        }

        // Launch background real-time synchronization engine loop
        startRealTimeSyncEngine()
    }

    fun isNetworkAvailable(): Boolean {
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm != null) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        return false
    }

    private fun startRealTimeSyncEngine() {
        viewModelScope.launch {
            var lastAutoCatalogTime = 0L
            while (true) {
                delay(4000) // Poll every 4 seconds for real-time cloud sync across devices
                try {
                    val now = System.currentTimeMillis()
                    if (isNetworkAvailable() && (now - lastAutoCatalogTime > 30000L)) {
                        val pendingList = repository.getAllProdottiList().filter { 
                            it.categoria.equals("Da Catalogare", ignoreCase = true) 
                        }
                        if (pendingList.isNotEmpty()) {
                            lastAutoCatalogTime = now
                            ricatalogaProdottiOffline()
                        }
                    }

                    val localList = repository.getAllProdottiList()
                    syncService.pushLocalToCloud(syncService.pantryCode, localList)

                    val cloudItems = syncService.fetchCloudProdotti(syncService.pantryCode)
                    if (cloudItems.isNotEmpty()) {
                        val localMap = localList.associateBy { it.id }
                        for (cItem in cloudItems) {
                            if (deletedProductIds.contains(cItem.id) || persistedDeletedIds.contains(cItem.id.toString())) {
                                syncService.deleteCloudProdotto(syncService.pantryCode, cItem.id)
                                continue
                            }
                            val local = localMap[cItem.id]
                            if (local == null) {
                                repository.insert(cItem)
                            } else if (local != cItem) {
                                repository.update(cItem)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun ricatalogaProdottiOffline(onComplete: ((Int) -> Unit)? = null) {
        if (isCatalogingOffline.value) return
        viewModelScope.launch {
            isCatalogingOffline.value = true
            try {
                val pendingList = repository.getAllProdottiList().filter { 
                    it.categoria.equals("Da Catalogare", ignoreCase = true) 
                }
                if (pendingList.isEmpty()) {
                    batchFeedbackMessage.value = "Nessun prodotto in attesa di catalogazione."
                    onComplete?.invoke(0)
                    return@launch
                }

                var countOnline = 0
                var countFallback = 0

                for (prod in pendingList) {
                    val queryText = prod.barcode.ifBlank { prod.nome }
                    val aiRes = aiService.analizzaBarcodeONome(queryText, forceNetwork = true)
                    
                    if (!aiRes.isOfflinePending && !aiRes.categoria.equals("Da Catalogare", ignoreCase = true)) {
                        val newNome = if (prod.nome.startsWith("Prodotto ", ignoreCase = true) || prod.nome.isBlank()) {
                            aiRes.nome
                        } else {
                            prod.nome
                        }
                        val newNote = if (prod.note.contains("offline", ignoreCase = true) || prod.note.isBlank()) {
                            aiRes.note
                        } else {
                            "${prod.note} • ${aiRes.note}"
                        }
                        val newScadenza = if (prod.dataScadenza <= System.currentTimeMillis() + 86400000L) {
                            System.currentTimeMillis() + (aiRes.giorniScadenzaStimati.toLong() * 86400000L)
                        } else {
                            prod.dataScadenza
                        }
                        
                        val updated = prod.copy(
                            nome = newNome,
                            categoria = aiRes.categoria,
                            posizione = aiRes.posizione,
                            dataScadenza = newScadenza,
                            note = newNote
                        )
                        repository.update(updated)
                        countOnline++
                    } else {
                        // Fallback intelligente immediato: assegna la categoria corretta e toglie lo stato "Da Catalogare"
                        val (cat, pos) = aiService.inferisciCategoriaDaNomeEBarcode(prod.nome, prod.barcode)
                        val updated = prod.copy(
                            categoria = cat,
                            posizione = pos,
                            note = if (prod.note.isBlank()) "Catalogato in dispensa" else prod.note
                        )
                        repository.update(updated)
                        countFallback++
                    }
                }
                
                val total = countOnline + countFallback
                if (countOnline > 0 && countFallback > 0) {
                    batchFeedbackMessage.value = "✨ $countOnline prodotti riconosciuti online, $countFallback catalogati in dispensa!"
                } else if (countOnline > 0) {
                    batchFeedbackMessage.value = "✨ Catalogati con successo $countOnline prodott${if (countOnline == 1) "o" else "i"} con Open Food Facts!"
                } else if (countFallback > 0) {
                    batchFeedbackMessage.value = "✅ $countFallback prodott${if (countFallback == 1) "o" else "i"} catalogat${if (countFallback == 1) "o" else "i"} in dispensa!"
                }
                triggerAutoDriveBackup()
                onComplete?.invoke(total)
            } catch (e: Exception) {
                batchFeedbackMessage.value = "⚠️ Errore catalogazione: ${e.message}"
                onComplete?.invoke(0)
            } finally {
                isCatalogingOffline.value = false
            }
        }
    }

    fun updatePantryCode(newCode: String) {
        syncService.updatePantryCode(newCode)
        viewModelScope.launch {
            syncService.pushLocalToCloud(syncService.pantryCode, repository.getAllProdottiList())
        }
    }

    fun sincronizzaOra() {
        viewModelScope.launch {
            val localList = repository.getAllProdottiList()
            syncService.pushLocalToCloud(syncService.pantryCode, localList)
            val cloudItems = syncService.fetchCloudProdotti(syncService.pantryCode)
            for (cItem in cloudItems) {
                repository.insert(cItem)
            }
            syncService.addSyncLog("Questo Dispositivo", "Sincronizzazione manuale in tempo reale completata")
        }
    }

    val allProdotti: StateFlow<List<Prodotto>> = repository.allProdotti
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val daCatalogareCount: StateFlow<Int> = allProdotti
        .combine(searchQuery) { list, _ ->
            list.count { it.categoria.equals("Da Catalogare", ignoreCase = true) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val prodottiSpesa: StateFlow<List<Prodotto>> = combine(
        repository.prodottiSpesa,
        isKeepIntegrationEnabled,
        selectedSpesaFilter,
        keepNoteTitle
    ) { list, isKeepEnabled, filter, currentNoteTitle ->
        when (filter) {
            "Dispensa" -> list.filter { !it.daKeep }
            "Keep" -> {
                if (isKeepEnabled) {
                    list.filter { it.daKeep }
                } else {
                    emptyList()
                }
            }
            else -> { // "Tutti"
                if (isKeepEnabled) {
                    list
                } else {
                    list.filter { !it.daKeep }
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredProdotti: StateFlow<List<Prodotto>> = combine(
        allProdotti,
        selectedCategory,
        selectedTab,
        searchQuery
    ) { prodotti, categoria, tab, query ->
        prodotti.filter { prod ->
            val matchAvailable = prod.quantita > 0
            val matchCategory = categoria == "Tutti" || prod.categoria.equals(categoria, ignoreCase = true)
            val matchTab = if (tab == 1) {
                prod.getStatoScadenza() == StatoScadenza.SCADUTO || prod.getStatoScadenza() == StatoScadenza.IN_SCADENZA
            } else true
            val matchQuery = query.isBlank() || prod.nome.contains(query, ignoreCase = true) || prod.barcode.contains(query)
            
            matchAvailable && matchCategory && matchTab && matchQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun triggerAutoDriveBackup() {
        if (driveState.value.isAutoSyncEnabled) {
            viewModelScope.launch {
                val currentList = repository.getAllProdottiList()
                backupService.syncBackupToGoogleDriveCloud(currentList)
            }
        }
    }

    fun insertProdotto(prodotto: Prodotto) {
        viewModelScope.launch {
            val deveAggiungereSpesa = isMinQuantityAlertEnabled.value &&
                    prodotto.quantitaMinima > 0 &&
                    prodotto.quantita <= prodotto.quantitaMinima
            val pFinal = if (deveAggiungereSpesa) prodotto.copy(inListaSpesa = true) else prodotto
            repository.insert(pFinal)
            triggerAutoDriveBackup()
        }
    }

    fun updateProdotto(prodotto: Prodotto) {
        viewModelScope.launch {
            val deveAggiungereSpesa = isMinQuantityAlertEnabled.value &&
                    prodotto.quantitaMinima > 0 &&
                    prodotto.quantita <= prodotto.quantitaMinima
            val pFinal = if (deveAggiungereSpesa) prodotto.copy(inListaSpesa = true) else prodotto
            repository.update(pFinal)
            triggerAutoDriveBackup()
        }
    }

    fun deleteProdotto(prodotto: Prodotto) {
        viewModelScope.launch {
            markProductAsDeleted(prodotto.id)
            repository.delete(prodotto)
            syncService.deleteCloudProdotto(syncService.pantryCode, prodotto.id)
            triggerAutoDriveBackup()
        }
    }

    fun deleteProdottoById(id: Long) {
        viewModelScope.launch {
            markProductAsDeleted(id)
            repository.deleteById(id)
            syncService.deleteCloudProdotto(syncService.pantryCode, id)
            triggerAutoDriveBackup()
        }
    }

    fun incrementaQuantita(prodotto: Prodotto) {
        viewModelScope.launch {
            val nuovaQuantita = prodotto.quantita + 1
            val deveAggiungereSpesa = isMinQuantityAlertEnabled.value &&
                    prodotto.quantitaMinima > 0 &&
                    nuovaQuantita <= prodotto.quantitaMinima
            val pFinal = prodotto.copy(
                quantita = nuovaQuantita,
                inListaSpesa = if (deveAggiungereSpesa) true else prodotto.inListaSpesa
            )
            repository.update(pFinal)
            triggerAutoDriveBackup()
        }
    }

    fun decrementaQuantita(prodotto: Prodotto) {
        viewModelScope.launch {
            val nuovaQuantita = (prodotto.quantita - 1).coerceAtLeast(0)
            val deveAggiungereSpesa = isMinQuantityAlertEnabled.value &&
                    prodotto.quantitaMinima > 0 &&
                    nuovaQuantita <= prodotto.quantitaMinima
            val pFinal = prodotto.copy(
                quantita = nuovaQuantita,
                inListaSpesa = if (deveAggiungereSpesa) true else prodotto.inListaSpesa
            )
            repository.update(pFinal)
            triggerAutoDriveBackup()
        }
    }

    // --- GOOGLE DRIVE & LOCAL JSON EXPORT/IMPORT METHODS ---
    fun setGoogleAccount(email: String) {
        backupService.setSelectedAccount(email)
    }

    fun setAutoSyncDriveEnabled(enabled: Boolean) {
        backupService.setAutoSyncEnabled(enabled)
    }

    fun exportLocalJsonUri(uri: android.net.Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val currentList = repository.getAllProdottiList()
            val json = backupService.exportToJson(currentList)
            val success = backupService.writeJsonToUri(uri, json)
            onResult(success)
        }
    }

    fun importLocalJsonUri(uri: android.net.Uri, isReplaceMode: Boolean, onResult: (Boolean, Int) -> Unit) {
        viewModelScope.launch {
            val json = backupService.readJsonFromUri(uri)
            if (json.isNullOrBlank()) {
                onResult(false, 0)
                return@launch
            }
            try {
                val parsedList = backupService.parseJsonToProdotti(json)
                if (isReplaceMode) {
                    repository.replaceAllProdotti(parsedList)
                } else {
                    repository.insertAll(parsedList)
                }
                triggerAutoDriveBackup()
                onResult(true, parsedList.size)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false, 0)
            }
        }
    }

    fun backupNowToGoogleDrive(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val currentList = repository.getAllProdottiList()
            val success = backupService.syncBackupToGoogleDriveCloud(currentList)
            onResult(success)
        }
    }

    fun restoreFromGoogleDriveCloud(isReplaceMode: Boolean, onResult: (Boolean, Int) -> Unit) {
        viewModelScope.launch {
            val cloudList = backupService.fetchBackupFromGoogleDriveCloud()
            if (cloudList == null || cloudList.isEmpty()) {
                onResult(false, 0)
                return@launch
            }
            if (isReplaceMode) {
                repository.replaceAllProdotti(cloudList)
            } else {
                repository.insertAll(cloudList)
            }
            onResult(true, cloudList.size)
        }
    }

    fun aggiungiAllaListaSpesa(prodotto: Prodotto) {
        viewModelScope.launch {
            repository.update(prodotto.copy(inListaSpesa = true, comprato = false))
            triggerAutoDriveBackup()
        }
    }

    fun toggleCompratoSpesa(prodotto: Prodotto) {
        viewModelScope.launch {
            val nuovoStato = !prodotto.comprato
            repository.update(prodotto.copy(comprato = nuovoStato))
            triggerAutoDriveBackup()
        }
    }

    fun rimuoviDaListaSpesa(prodotto: Prodotto) {
        viewModelScope.launch {
            if (prodotto.quantita <= 0) {
                // Se la quantità in dispensa è 0 (prodotto consumato o inserito a mano solo per la spesa),
                // lo eliminiamo completamente dal database così non ricompare in dispensa né in spesa
                markProductAsDeleted(prodotto.id)
                repository.delete(prodotto)
                syncService.deleteCloudProdotto(syncService.pantryCode, prodotto.id)
            } else {
                // Se invece in dispensa ci sono ancora unità fisiche (> 0), togliamo solo il flag della spesa
                val updated = prodotto.copy(inListaSpesa = false, daKeep = false, comprato = false)
                repository.update(updated)
                syncService.pushProdotto(syncService.pantryCode, updated)
            }
            triggerAutoDriveBackup()
        }
    }

    fun cancellaProdottiSpuntati() {
        viewModelScope.launch {
            val attualiSpesa = prodottiSpesa.value.filter { it.comprato }
            for (p in attualiSpesa) {
                if (p.quantita <= 0) {
                    markProductAsDeleted(p.id)
                    repository.delete(p)
                    syncService.deleteCloudProdotto(syncService.pantryCode, p.id)
                } else {
                    val updated = p.copy(inListaSpesa = false, daKeep = false, comprato = false)
                    repository.update(updated)
                    syncService.pushProdotto(syncService.pantryCode, updated)
                }
            }
            triggerAutoDriveBackup()
        }
    }

    fun ripristinaDaListaSpesa(prodotto: Prodotto) {
        toggleCompratoSpesa(prodotto)
    }

    fun analizzaInputIA(barcodeONome: String, onComplete: (AiProductResult) -> Unit) {
        viewModelScope.launch {
            isAnalyzingBarcode.value = true
            val res = aiService.analizzaBarcodeONome(barcodeONome)
            isAnalyzingBarcode.value = false
            onComplete(res)
        }
    }

    // Smart Barcode AI Matching
    suspend fun trovaOAssociaProdotto(barcode: String): Pair<Prodotto?, AiProductResult?> {
        val trimmed = barcode.trim()
        if (trimmed.isBlank()) return Pair(null, null)

        // 1. Direct barcode match in DB
        val dbMatch = repository.getProdottoByBarcode(trimmed)
        if (dbMatch != null) {
            return Pair(dbMatch, null)
        }

        // 2. AI Analysis for barcode/type
        val aiResult = aiService.analizzaBarcodeONome(trimmed)

        // 3. Search DB by AI Product Name keywords or Category
        val allProdottiList = repository.getAllProdottiList()
        val firstKeyword = aiResult.nome.split(" ").firstOrNull { it.length > 3 } ?: aiResult.nome
        
        val matchedExisting = allProdottiList.firstOrNull { prod ->
            prod.nome.contains(firstKeyword, ignoreCase = true) ||
            prod.barcode.contains(trimmed) ||
            (prod.categoria.equals(aiResult.categoria, ignoreCase = true) && prod.nome.contains(firstKeyword, ignoreCase = true))
        }

        if (matchedExisting != null) {
            // Append new barcode to existing product if missing
            if (!matchedExisting.barcode.contains(trimmed)) {
                val updatedBarcodes = if (matchedExisting.barcode.isBlank()) trimmed else "${matchedExisting.barcode}, $trimmed"
                val updated = matchedExisting.copy(barcode = updatedBarcodes)
                repository.update(updated)
                return Pair(updated, aiResult)
            }
            return Pair(matchedExisting, aiResult)
        }

        return Pair(null, aiResult)
    }

    // --- CARICO BATCH SESSION ---
    fun scansionaPerCaricoBatch(barcode: String) {
        viewModelScope.launch {
            val trimmed = barcode.trim()
            if (trimmed.isBlank()) return@launch

            BarcodeSoundFeedback.playScanBeep(getApplication())

            // Check if already in current carico session queue
            val existingIndex = caricoSessionQueue.value.indexOfFirst { it.barcode == trimmed }
            if (existingIndex >= 0) {
                val updatedList = caricoSessionQueue.value.toMutableList()
                val item = updatedList[existingIndex]
                updatedList[existingIndex] = item.copy(quantita = item.quantita + 1)
                caricoSessionQueue.value = updatedList
                batchFeedbackMessage.value = "➕ Incrementata quantità per '${item.nome}' (+1)"
                return@launch
            }

            isAnalyzingBarcode.value = true
            val (prodEsistente, aiRes) = trovaOAssociaProdotto(trimmed)
            isAnalyzingBarcode.value = false

            val defaultScadenza = System.currentTimeMillis() + ((aiRes?.giorniScadenzaStimati ?: 30).toLong() * 86400000L)

            val newItem = SessionItemCarico(
                barcode = trimmed,
                prodottoEsistenteId = prodEsistente?.id,
                nome = prodEsistente?.nome ?: aiRes?.nome ?: "Prodotto $trimmed",
                categoria = prodEsistente?.categoria ?: aiRes?.categoria ?: "Dispensa Secca",
                quantita = 1,
                dataScadenza = prodEsistente?.dataScadenza ?: defaultScadenza,
                posizione = prodEsistente?.posizione ?: aiRes?.posizione ?: "Dispensa",
                note = prodEsistente?.note ?: aiRes?.note ?: "",
                isAiRecognized = aiRes != null,
                barcodeLinkedToExisting = prodEsistente != null
            )

            caricoSessionQueue.value = caricoSessionQueue.value + newItem
            batchFeedbackMessage.value = if (prodEsistente != null)
                "✨ Barcode associato a '${prodEsistente.nome}' in dispensa!"
            else
                "➕ Aggiunto '${newItem.nome}' alla sessione di carico"
        }
    }

    fun incrementaCaricoItem(tempId: String) {
        caricoSessionQueue.value = caricoSessionQueue.value.map {
            if (it.tempId == tempId) it.copy(quantita = it.quantita + 1) else it
        }
    }

    fun decrementaCaricoItem(tempId: String) {
        caricoSessionQueue.value = caricoSessionQueue.value.mapNotNull {
            if (it.tempId == tempId) {
                if (it.quantita > 1) it.copy(quantita = it.quantita - 1) else null
            } else it
        }
    }

    fun rimuoviCaricoItem(tempId: String) {
        caricoSessionQueue.value = caricoSessionQueue.value.filterNot { it.tempId == tempId }
    }

    fun confermaOperazioniCarico(onComplete: () -> Unit) {
        viewModelScope.launch {
            val currentQueue = caricoSessionQueue.value
            for (item in currentQueue) {
                if (item.prodottoEsistenteId != null) {
                    val prodDb = repository.getProdottoById(item.prodottoEsistenteId)
                    if (prodDb != null) {
                        repository.update(prodDb.copy(quantita = prodDb.quantita + item.quantita))
                    } else {
                        repository.insert(
                            Prodotto(
                                barcode = item.barcode,
                                nome = item.nome,
                                categoria = item.categoria,
                                quantita = item.quantita,
                                dataScadenza = item.dataScadenza,
                                posizione = item.posizione,
                                note = item.note
                            )
                        )
                    }
                } else {
                    repository.insert(
                        Prodotto(
                            barcode = item.barcode,
                            nome = item.nome,
                            categoria = item.categoria,
                            quantita = item.quantita,
                            dataScadenza = item.dataScadenza,
                            posizione = item.posizione,
                            note = item.note
                        )
                    )
                }
            }
            caricoSessionQueue.value = emptyList()
            batchFeedbackMessage.value = "✅ Carico di ${currentQueue.sumOf { it.quantita }} prodotti confermato!"
            onComplete()
        }
    }

    fun annullaOperazioniCarico() {
        caricoSessionQueue.value = emptyList()
        batchFeedbackMessage.value = "❌ Sessione di carico annullata."
    }

    // --- SCARICO BATCH SESSION ---
    fun scansionaPerScaricoBatch(barcode: String) {
        viewModelScope.launch {
            val trimmed = barcode.trim()
            if (trimmed.isBlank()) return@launch

            BarcodeSoundFeedback.playScanBeep(getApplication())

            // Check if already in scarico session queue
            val existingIndex = scaricoSessionQueue.value.indexOfFirst { it.barcode == trimmed }
            if (existingIndex >= 0) {
                val updatedList = scaricoSessionQueue.value.toMutableList()
                val item = updatedList[existingIndex]
                if (item.quantitaDaScaricare < item.quantitaDisponibile) {
                    updatedList[existingIndex] = item.copy(quantitaDaScaricare = item.quantitaDaScaricare + 1)
                    scaricoSessionQueue.value = updatedList
                    batchFeedbackMessage.value = "➖ Incrementato scarico per '${item.nome}' (+1)"
                } else {
                    batchFeedbackMessage.value = "⚠️ Quantità max disponibile (${item.quantitaDisponibile}) per '${item.nome}'"
                }
                return@launch
            }

            isAnalyzingBarcode.value = true
            val (prodEsistente, aiRes) = trovaOAssociaProdotto(trimmed)
            isAnalyzingBarcode.value = false

            if (prodEsistente != null && prodEsistente.quantita > 0) {
                val newItem = SessionItemScarico(
                    barcode = trimmed,
                    prodottoId = prodEsistente.id,
                    nome = prodEsistente.nome,
                    categoria = prodEsistente.categoria,
                    quantitaDaScaricare = 1,
                    quantitaDisponibile = prodEsistente.quantita
                )
                scaricoSessionQueue.value = scaricoSessionQueue.value + newItem
                batchFeedbackMessage.value = "➖ Aggiunto '${newItem.nome}' allo scarico"
            } else {
                batchFeedbackMessage.value = "⚠️ Prodotto $trimmed non trovato o esaurito in dispensa!"
            }
        }
    }

    fun aggiungiProdottoADirettScarico(prodotto: Prodotto) {
        val existingIndex = scaricoSessionQueue.value.indexOfFirst { it.prodottoId == prodotto.id }
        if (existingIndex >= 0) {
            val updatedList = scaricoSessionQueue.value.toMutableList()
            val item = updatedList[existingIndex]
            if (item.quantitaDaScaricare < item.quantitaDisponibile) {
                updatedList[existingIndex] = item.copy(quantitaDaScaricare = item.quantitaDaScaricare + 1)
                scaricoSessionQueue.value = updatedList
            }
        } else {
            val newItem = SessionItemScarico(
                barcode = prodotto.barcode,
                prodottoId = prodotto.id,
                nome = prodotto.nome,
                categoria = prodotto.categoria,
                quantitaDaScaricare = 1,
                quantitaDisponibile = prodotto.quantita
            )
            scaricoSessionQueue.value = scaricoSessionQueue.value + newItem
        }
    }

    fun incrementaScaricoItem(tempId: String) {
        scaricoSessionQueue.value = scaricoSessionQueue.value.map {
            if (it.tempId == tempId && it.quantitaDaScaricare < it.quantitaDisponibile) {
                it.copy(quantitaDaScaricare = it.quantitaDaScaricare + 1)
            } else it
        }
    }

    fun decrementaScaricoItem(tempId: String) {
        scaricoSessionQueue.value = scaricoSessionQueue.value.mapNotNull {
            if (it.tempId == tempId) {
                if (it.quantitaDaScaricare > 1) it.copy(quantitaDaScaricare = it.quantitaDaScaricare - 1) else null
            } else it
        }
    }

    fun rimuoviScaricoItem(tempId: String) {
        scaricoSessionQueue.value = scaricoSessionQueue.value.filterNot { it.tempId == tempId }
    }

    fun confermaOperazioniScarico(onComplete: () -> Unit) {
        viewModelScope.launch {
            val currentQueue = scaricoSessionQueue.value
            for (item in currentQueue) {
                val prodDb = repository.getProdottoById(item.prodottoId)
                if (prodDb != null) {
                    val nq = maxOf(0, prodDb.quantita - item.quantitaDaScaricare)
                    val deveAggiungereSpesa = isMinQuantityAlertEnabled.value &&
                            prodDb.quantitaMinima > 0 &&
                            nq <= prodDb.quantitaMinima
                    val pFinal = prodDb.copy(
                        quantita = nq,
                        inListaSpesa = if (deveAggiungereSpesa) true else prodDb.inListaSpesa
                    )
                    repository.update(pFinal)
                }
            }
            triggerAutoDriveBackup()
            scaricoSessionQueue.value = emptyList()
            batchFeedbackMessage.value = "✅ Scarico di ${currentQueue.sumOf { it.quantitaDaScaricare }} prodotti confermato!"
            onComplete()
        }
    }

    fun annullaOperazioniScarico() {
        scaricoSessionQueue.value = emptyList()
        batchFeedbackMessage.value = "❌ Sessione di scarico annullata."
    }

    fun scaricaProdottoViaBarcode(barcode: String, onFeedback: (String) -> Unit) {
        viewModelScope.launch {
            val prod = repository.getProdottoByBarcode(barcode)
            if (prod != null) {
                val nq = maxOf(0, prod.quantita - 1)
                val deveAggiungereSpesa = isMinQuantityAlertEnabled.value &&
                        prod.quantitaMinima > 0 &&
                        nq <= prod.quantitaMinima
                val pFinal = prod.copy(
                    quantita = nq,
                    inListaSpesa = if (deveAggiungereSpesa) true else prod.inListaSpesa
                )
                repository.update(pFinal)
                triggerAutoDriveBackup()
                if (nq == 0) {
                    if (deveAggiungereSpesa) {
                        onFeedback("🎉 '${prod.nome}' è terminato! Scorta minima impostata (${prod.quantitaMinima}): aggiunto alla lista della spesa.")
                    } else {
                        onFeedback("🎉 '${prod.nome}' è terminato ed è stato nascosto dalla dispensa.")
                    }
                } else if (deveAggiungereSpesa) {
                    onFeedback("⚠️ '${prod.nome}' sotto la scorta minima ($nq ≤ ${prod.quantitaMinima}). Aggiunto in spesa!")
                } else {
                    onFeedback("Consumata 1 unità di '${prod.nome}'. Rimanenti: $nq")
                }
            } else {
                onFeedback("⚠️ Prodotto con barcode $barcode non trovato in dispensa.")
            }
        }
    }
}
