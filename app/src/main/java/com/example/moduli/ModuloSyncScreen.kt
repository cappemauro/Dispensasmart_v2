package com.example.moduli

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.ui.platform.LocalClipboardManager
import com.example.util.GoogleKeepHelper
import com.example.ui.theme.ExpiryRed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.ForestGreenPrimary
import com.example.viewmodel.DispensaViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ModuloSyncScreen(
    viewModel: DispensaViewModel,
    onNavigateToCantina: () -> Unit = {}
) {
    val context = LocalContext.current
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val driveState by viewModel.driveState.collectAsStateWithLifecycle()

    var activeTab by remember { mutableIntStateOf(0) } // 0 = Backup & Google Drive, 1 = Multi-Dispositivo Live

    var inputCode by remember { mutableStateOf("") }
    var isEditingCode by remember { mutableStateOf(false) }

    var customAccountInput by remember { mutableStateOf("") }
    var showAccountDialog by remember { mutableStateOf(false) }

    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showRestoreOptionsDialog by remember { mutableStateOf(false) }
    var showDriveRestoreConfirmDialog by remember { mutableStateOf(false) }

    val timeFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.ITALIAN) }

    // JSON Export Launcher
    val createJsonDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportLocalJsonUri(uri) { success ->
                if (success) {
                    Toast.makeText(context, "✅ Database esportato con successo in JSON!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "❌ Errore durante l'esportazione del file JSON", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // JSON Import Launcher
    val openJsonDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            showRestoreOptionsDialog = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("modulo_sync_screen")
    ) {
        // Header
        Text(
            text = "Impostazioni ⚙️",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Gestisci le preferenze, il backup in locale, la sincronizzazione e la condivisione.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Navigation Tabs
        ScrollableTabRow(
            selectedTabIndex = activeTab,
            edgePadding = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                text = { Text("Backup & Drive ☁️", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                modifier = Modifier.testTag("tab_drive_backup")
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = { Text("Multi-Device 📱", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                modifier = Modifier.testTag("tab_multi_device")
            )
            Tab(
                selected = activeTab == 2,
                onClick = { activeTab = 2 },
                text = { Text("Cantina 🍷", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                modifier = Modifier.testTag("tab_cantina")
            )
            Tab(
                selected = activeTab == 3,
                onClick = { activeTab = 3 },
                text = { Text("IA Gemini 🤖", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                modifier = Modifier.testTag("tab_gemini_ai")
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (activeTab == 0) {
            // --- TAB 0: LOCAL JSON EXPORT/IMPORT & GOOGLE DRIVE AUTO-SYNC ---

            // Offline First Room Banner
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.OfflinePin,
                        contentDescription = "Offline OK",
                        tint = ForestGreenPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Funzionamento Offline Garantito ⚡",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Tutti i dati sono memorizzati nel database Room locale sul tuo dispositivo. Se chiudi l'app o vai offline non perderai mai nulla.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // CARD IMPOSTAZIONI SCORTA MINIMA
            val isMinQtyAlertEnabled by viewModel.isMinQuantityAlertEnabled.collectAsStateWithLifecycle()
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Scorta Minima",
                                tint = ExpiryRed,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Avviso Scorta Minima & Auto Spesa",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Evidenzia in rosso i prodotti sotto la quantità minima e inseriscili automaticamente nella lista della spesa",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isMinQtyAlertEnabled,
                            onCheckedChange = { viewModel.setMinQuantityAlertEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ForestGreenPrimary
                            ),
                            modifier = Modifier.testTag("switch_min_qty_alert")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // CARD IMPOSTAZIONI GESTIONE SCADENZE
            val isExpiryManagementEnabled by viewModel.isExpiryManagementEnabled.collectAsStateWithLifecycle()
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = "Gestione Scadenze",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Gestione Scadenze Prodotti",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Monitora e mostra avvisi di scadenza e filtro 'In Scadenza' per i prodotti",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isExpiryManagementEnabled,
                            onCheckedChange = { viewModel.setExpiryManagementEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ForestGreenPrimary
                            ),
                            modifier = Modifier.testTag("switch_expiry_management")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // CARD IMPOSTAZIONI GOOGLE KEEP (LISTA SPESA)
            val isKeepIntegrationEnabled by viewModel.isKeepIntegrationEnabled.collectAsStateWithLifecycle()
            val keepNoteTitle by viewModel.keepNoteTitle.collectAsStateWithLifecycle()
            val keepLastSyncMessage by viewModel.keepLastSyncMessage.collectAsStateWithLifecycle()
            val context = LocalContext.current
            val clipboardManager = LocalClipboardManager.current
            var inputNoteTitle by remember(keepNoteTitle) { mutableStateOf(keepNoteTitle) }
            var showImportDialog by remember { mutableStateOf(false) }
            var importTextContent by remember { mutableStateOf("") }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                                contentDescription = "Google Keep",
                                tint = ForestGreenPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Integrazione Google Keep",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Controlla la nota Keep ed importa automaticamente i prodotti in lista spesa",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isKeepIntegrationEnabled,
                            onCheckedChange = { viewModel.setKeepIntegrationEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ForestGreenPrimary
                            ),
                            modifier = Modifier.testTag("switch_keep_integration")
                        )
                    }

                    if (isKeepIntegrationEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Nome esatto della nota/lista Keep:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = inputNoteTitle,
                                onValueChange = {
                                    inputNoteTitle = it
                                    viewModel.setKeepNoteTitle(it)
                                },
                                placeholder = { Text("es. Lista della Spesa") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("input_keep_note_title")
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    GoogleKeepHelper.openKeepSearch(context, inputNoteTitle)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("button_open_keep")
                            ) {
                                Icon(imageVector = Icons.Default.Launch, contentDescription = "Apri in Keep", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Apri in Keep", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    val clip = clipboardManager.getText()?.text ?: ""
                                    if (clip.isNotBlank()) {
                                        importTextContent = clip
                                        showImportDialog = true
                                        Toast.makeText(context, "Testo incollato! Verifica l'anteprima.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Nessun testo trovato negli appunti", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("button_paste_keep_clipboard")
                            ) {
                                Icon(imageVector = Icons.Default.ContentPaste, contentDescription = "Incolla Appunti", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Incolla Testo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    val clip = clipboardManager.getText()?.text ?: ""
                                    if (clip.isNotBlank() && importTextContent.isBlank()) {
                                        importTextContent = clip
                                    }
                                    showImportDialog = true
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("button_import_text_keep")
                            ) {
                                Icon(imageVector = Icons.Default.FileUpload, contentDescription = "Importa Testo", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Scrivi Testo", fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = {
                                viewModel.rimuoviTuttiProdottiDaKeep()
                                Toast.makeText(context, "Articoli Keep rimossi dalla lista della spesa", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("button_clear_keep_items")
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pulisci articoli Keep dalla spesa", fontSize = 11.sp)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "💡 Come sincronizzare i prodotti reali da Google Keep:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "1. Tocca 'Apri in Keep': l'app cerca la nota '$inputNoteTitle' e copia il nome negli appunti.\n2. Nella nota, tocca ⋮ -> Invia -> Invia tramite altra app -> seleziona Dispensa Smart (importa subito con il titolo corretto!).\n3. In alternativa: copia il testo della lista e tocca 'Incolla Testo' con anteprima.",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 14.sp
                                )
                            }
                        }

                        if (!keepLastSyncMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = keepLastSyncMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = ForestGreenPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "💡 Quando questa funzione è disabilitata, i prodotti presi da Keep sono nascosti nella lista spesa.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (showImportDialog) {
                val previewItems = remember(importTextContent, inputNoteTitle) {
                    if (importTextContent.isNotBlank()) {
                        com.example.util.GoogleKeepHelper.cleanAndExtractKeepItems(importTextContent, inputNoteTitle)
                    } else {
                        emptyList()
                    }
                }

                AlertDialog(
                    onDismissRequest = { showImportDialog = false },
                    title = { Text("Importa articoli da '$inputNoteTitle'") },
                    text = {
                        Column {
                            Text(
                                text = "Inserisci o incolla qui i prodotti dalla tua nota Google Keep:",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = importTextContent,
                                onValueChange = { importTextContent = it },
                                placeholder = { Text("- Latte\n- Pane\n- Caffè") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .testTag("input_keep_text_content")
                            )

                            if (previewItems.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "📋 Articoli rilevati (${previewItems.size}): ${previewItems.joinToString(", ")}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ForestGreenPrimary
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.sincronizzaConGoogleKeep(importTextContent, inputNoteTitle)
                                importTextContent = ""
                                showImportDialog = false
                            },
                            enabled = previewItems.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
                        ) {
                            Text("Importa (${previewItems.size})")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showImportDialog = false }) {
                            Text("Annulla")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // LOCAL JSON BACKUP & RESTORE CARD
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Storage, contentDescription = "JSON Local", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Salvataggio Locale in JSON 💾",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Esporta l'intero database in un file .json sul tuo smartphone per conservarlo o trasferirlo altrove senza dipendere dal cloud.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                createJsonDocumentLauncher.launch("dispensa_smart_backup.json")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("export_json_button")
                        ) {
                            Icon(imageVector = Icons.Default.FileUpload, contentDescription = "Esporta JSON", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Esporta File JSON", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                openJsonDocumentLauncher.launch(arrayOf("application/json", "*/*"))
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("import_json_button")
                        ) {
                            Icon(imageVector = Icons.Default.FileDownload, contentDescription = "Ripristina JSON", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ripristina da JSON", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // GOOGLE DRIVE SYNC CARD
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Backup, contentDescription = "Google Drive", tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Google Drive Auto-Sync ☁️",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        TextButton(onClick = { showAccountDialog = true }) {
                            Text("Cambia account", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Account Info Row
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Account Google",
                                tint = ForestGreenPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = driveState.selectedAccount,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Ultimo backup: ${timeFormat.format(Date(driveState.lastBackupTimestamp))} (${driveState.totalItemsBackedUp} prodotti)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Auto sync switch
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sincronizzazione Automatica",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Salva il backup su Google Drive ad ogni aggiunta o modifica",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = driveState.isAutoSyncEnabled,
                            onCheckedChange = { viewModel.setAutoSyncDriveEnabled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = ForestGreenPrimary)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                viewModel.backupNowToGoogleDrive { success ->
                                    if (success) {
                                        Toast.makeText(context, "☁️ Backup su Google Drive completato!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Icon(imageVector = Icons.Default.CloudUpload, contentDescription = "Backup Ora", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Backup Ora Drive", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { showDriveRestoreConfirmDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Icon(imageVector = Icons.Default.CloudDownload, contentDescription = "Ripristina Drive", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ripristina da Drive", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else if (activeTab == 1) {
            // --- TAB 1: MULTI-DEVICE REAL-TIME SYNC ---

            // Live Status Banner
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (syncState.isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (syncState.isConnected) Color(0xFF2E7D32) else Color.Red)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (syncState.isSyncing) "Sincronizzazione in corso..." else "🟢 Live Sync Attivo",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Ultimo sync: ${timeFormat.format(Date(syncState.lastSyncedTime))}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            viewModel.sincronizzaOra()
                            Toast.makeText(context, "Sincronizzazione avviata!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        if (syncState.isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Aggiorna ora")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Pantry Share Code Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Codice Dispensa Condivisa",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Row {
                            IconButton(
                                onClick = {
                                    if (!isEditingCode) {
                                        inputCode = syncState.pantryCode
                                    }
                                    isEditingCode = !isEditingCode
                                },
                                modifier = Modifier.testTag("button_edit_code_icon")
                            ) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Modifica codice", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Codice Dispensa", syncState.pantryCode)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "📋 Codice copiato negli appunti!", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copia codice")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = syncState.pantryCode,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 2.sp,
                            color = ForestGreenPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (!isEditingCode) {
                                    inputCode = syncState.pantryCode
                                }
                                isEditingCode = !isEditingCode
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("button_toggle_edit_code")
                        ) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Modifica codice", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = if (isEditingCode) "Chiudi" else "Modifica / Personalizza", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                val sendIntent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    putExtra(android.content.Intent.EXTRA_TEXT, "Unisciti alla mia dispensa condivisa su DispensaSmart con il codice: ${syncState.pantryCode}")
                                    type = "text/plain"
                                }
                                context.startActivity(android.content.Intent.createChooser(sendIntent, "Condividi Codice Dispensa"))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("button_share_code")
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = "Condividi", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Condividi", fontSize = 12.sp)
                        }
                    }

                    AnimatedVisibility(visible = isEditingCode) {
                        Column(
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Personalizza Codice Famiglia ✏️",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Inserisci un codice personalizzato (es. 'FAMIGLIA-ROSSI') o il codice fornito da un tuo familiare. Tutti i dispositivi con lo stesso codice saranno sincronizzati insieme.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = inputCode,
                                onValueChange = { inputCode = it.uppercase() },
                                label = { Text("Codice Dispensa Personalizzato") },
                                placeholder = { Text("es. FAMIGLIA-ROSSI-2026") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_custom_pantry_code")
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        inputCode = "DISPENSA-FAMIGLIA-" + (1000..9999).random()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Genera Casuale", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("🎲 Casuale", fontSize = 11.sp)
                                }

                                Button(
                                    onClick = {
                                        val trimmed = inputCode.trim()
                                        if (trimmed.isNotBlank()) {
                                            viewModel.updatePantryCode(trimmed)
                                            isEditingCode = false
                                            Toast.makeText(context, "✅ Codice dispensa impostato: $trimmed", Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    enabled = inputCode.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1.5f)
                                        .testTag("button_save_pantry_code")
                                ) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = "Salva", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("✅ Imposta Codice", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Connected Devices Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Devices, contentDescription = "Dispositivi", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Dispositivi Connessi (${syncState.devices.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    syncState.devices.forEach { device ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (device.isCurrentDevice) ForestGreenPrimary else Color.Gray)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = device.deviceName + if (device.isCurrentDevice) " (Questo)" else "",
                                    fontWeight = if (device.isCurrentDevice) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                            }

                            Text(
                                text = "Attivo ora",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else if (activeTab == 2) {
            // --- TAB 2: CANTINA MODE ---
            ModuloCantinaScreen()
        } else if (activeTab == 3) {
            // --- TAB 3: INTELLIGENZA ARTIFICIALE GEMINI 3.5 FLASH & RICERCA WEB LIVE ---
            val isGeminiConfigured = viewModel.isGeminiApiKeyConfigured
            var testBarcode by remember { mutableStateOf("") }
            var testResult by remember { mutableStateOf<com.example.servizi.AiProductResult?>(null) }
            var isTesting by remember { mutableStateOf(false) }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isGeminiConfigured) Color(0xFFE8F5E9) else Color(0xFFFFF8E1)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Gemini",
                            tint = if (isGeminiConfigured) ForestGreenPrimary else Color(0xFFF57F17),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Gemini 3.5 Flash con Ricerca Web",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isGeminiConfigured) "Stato: ATTIVO E PRONTO ALL'USO 🟢" else "Stato: IN ATTESA DI API KEY 🟡",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isGeminiConfigured) ForestGreenPrimary else Color(0xFFE65100)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "A differenza dei database esterni parziali, Gemini 3.5 Flash con Google Search Grounding effettua una ricerca su internet in tempo reale sul codice a barre esatto per identificare marca, formato, categoria e conservazione ottimale per qualsiasi prodotto.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Guida configurazione passo-passo
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Istruzioni per l'attivazione passo-passo 🔑",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. Ottieni una chiave gratuita su https://aistudio.google.com/apikey\n" +
                               "2. Nel menu di AI Studio Build, apri la scheda 'Secrets' (icona della chiave).\n" +
                               "3. Aggiungi il segreto con nome GEMINI_API_KEY\n" +
                               "4. Incolla il valore della tua chiave API.\n" +
                               "5. Fatto! L'app riconoscerà automaticamente qualsiasi barcode effettuando la ricerca live sul web.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Strumento di test interattivo
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Testa la ricerca su internet di un Barcode 🔍",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = testBarcode,
                        onValueChange = { testBarcode = it },
                        label = { Text("Codice a barre (es. 8001234567890)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (testBarcode.isNotBlank()) {
                                isTesting = true
                                testResult = null
                                viewModel.analizzaInputIA(testBarcode) { res ->
                                    testResult = res
                                    isTesting = false
                                }
                            }
                        },
                        enabled = !isTesting && testBarcode.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ricerca in corso...")
                        } else {
                            Text("Cerca con Gemini Web ✨")
                        }
                    }

                    if (testResult != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(text = "Risultato trovato:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(text = "Nome: ${testResult!!.nome}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(text = "Categoria: ${testResult!!.categoria} • Posizione: ${testResult!!.posizione}", fontSize = 12.sp)
                                Text(text = "Scadenza stimata: ${testResult!!.giorniScadenzaStimati} giorni", fontSize = 12.sp)
                                if (testResult!!.note.isNotBlank()) {
                                    Text(text = "Note: ${testResult!!.note}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // SELECT GOOGLE ACCOUNT DIALOG
    if (showAccountDialog) {
        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            title = { Text("Seleziona Account Google Drive") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Scegli l'account Google con cui sincronizzare il backup della dispensa:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val sampleAccounts = listOf("cappe88it@gmail.com", "famiglia.cappe@gmail.com", "lavoro.cappe@gmail.com")
                    sampleAccounts.forEach { acc ->
                        Card(
                            onClick = {
                                viewModel.setGoogleAccount(acc)
                                showAccountDialog = false
                                Toast.makeText(context, "Account impostato: $acc", Toast.LENGTH_SHORT).show()
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = if (driveState.selectedAccount == acc) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Icon(imageVector = Icons.Default.AccountCircle, contentDescription = null, tint = ForestGreenPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = acc, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = customAccountInput,
                        onValueChange = { customAccountInput = it },
                        label = { Text("Aggiungi altro account Google") },
                        placeholder = { Text("es. tuo.nome@gmail.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customAccountInput.isNotBlank()) {
                            viewModel.setGoogleAccount(customAccountInput)
                            customAccountInput = ""
                            showAccountDialog = false
                            Toast.makeText(context, "Account Google aggiornato!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
                ) {
                    Text("Salva Account")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccountDialog = false }) {
                    Text("Annulla")
                }
            }
        )
    }

    // RESTORE OPTIONS DIALOG FOR LOCAL JSON
    if (showRestoreOptionsDialog && pendingRestoreUri != null) {
        AlertDialog(
            onDismissRequest = {
                showRestoreOptionsDialog = false
                pendingRestoreUri = null
            },
            title = { Text("Ripristina Database da JSON 📥") },
            text = {
                Text(
                    text = "Come desideri procedere con l'importazione del backup?\n\n• UNISCI: Aggiunge i prodotti senza cancellare quelli attuali.\n• SOSTITUISCI: Sostituisce completamente la dispensa attuale con quella del file.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = pendingRestoreUri
                        if (uri != null) {
                            viewModel.importLocalJsonUri(uri, isReplaceMode = true) { success, count ->
                                if (success) {
                                    Toast.makeText(context, "✅ Sostituiti con successo $count prodotti!", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "❌ File JSON non valido o corrotti", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        showRestoreOptionsDialog = false
                        pendingRestoreUri = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Sostituisci DB")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        val uri = pendingRestoreUri
                        if (uri != null) {
                            viewModel.importLocalJsonUri(uri, isReplaceMode = false) { success, count ->
                                if (success) {
                                    Toast.makeText(context, "✅ Importati con successo $count prodotti!", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "❌ File JSON non valido", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        showRestoreOptionsDialog = false
                        pendingRestoreUri = null
                    }
                ) {
                    Text("Unisci Dati")
                }
            }
        )
    }

    // RESTORE CONFIRM DIALOG FOR GOOGLE DRIVE
    if (showDriveRestoreConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDriveRestoreConfirmDialog = false },
            title = { Text("Ripristina da Google Drive ☁️") },
            text = {
                Text(
                    text = "Ripristinare il backup dal cloud Drive per l'account ${driveState.selectedAccount}?",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.restoreFromGoogleDriveCloud(isReplaceMode = false) { success, count ->
                            if (success) {
                                Toast.makeText(context, "✅ Ripristinati $count prodotti da Google Drive!", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "⚠️ Nessun backup valido trovato su Drive per questo account.", Toast.LENGTH_SHORT).show()
                            }
                        }
                        showDriveRestoreConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
                ) {
                    Text("Ripristina Ora")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDriveRestoreConfirmDialog = false }) {
                    Text("Annulla")
                }
            }
        )
    }
}

