package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ForestGreenPrimary
import com.example.util.GoogleKeepHelper

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeepSyncDialog(
    initialNoteTitle: String,
    onTitleChange: (String) -> Unit,
    onImport: (String, String) -> Unit,
    onClearKeepItems: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var noteTitle by remember { mutableStateOf(initialNoteTitle) }
    var textContent by remember { mutableStateOf("") }
    var showConfirmClear by remember { mutableStateOf(false) }

    val clipText = clipboardManager.getText()?.text ?: ""
    val hasClipboardContent = clipText.isNotBlank()

    val detectedItems = remember(textContent, noteTitle) {
        if (textContent.isNotBlank()) {
            GoogleKeepHelper.cleanAndExtractKeepItems(textContent, noteTitle)
        } else {
            emptyList()
        }
    }

    if (showConfirmClear) {
        AlertDialog(
            onDismissRequest = { showConfirmClear = false },
            title = { Text("Pulisci articoli Keep?") },
            text = { Text("Tutti gli articoli aggiunti da Keep o non desiderati nella spesa verranno rimossi.") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearKeepItems?.invoke()
                        showConfirmClear = false
                        Toast.makeText(context, "Articoli Keep rimossi!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Rimuovi")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmClear = false }) {
                    Text("Annulla")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                    contentDescription = "Keep",
                    tint = ForestGreenPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Sincronizza Google Keep",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Nome della lista / nota Keep da cercare:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = noteTitle,
                    onValueChange = {
                        noteTitle = it
                        onTitleChange(it)
                    },
                    placeholder = { Text("es. Lista della Spesa, Esselunga...") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_keep_note_title")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Pulsanti azione: Cerca in Keep & Incolla da Appunti
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            GoogleKeepHelper.openKeepSearch(context, noteTitle)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_open_google_keep")
                    ) {
                        Icon(imageVector = Icons.Default.Launch, contentDescription = "Cerca in Keep", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cerca in Keep", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (hasClipboardContent) {
                                textContent = clipText
                                Toast.makeText(context, "Testo incollato dagli appunti!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Nessun testo trovato negli appunti", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_paste_clipboard_keep")
                    ) {
                        Icon(imageVector = Icons.Default.ContentPaste, contentDescription = "Incolla", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Incolla Testo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Contenuto della nota Keep (uno per riga o elenco):",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = textContent,
                    onValueChange = { textContent = it },
                    placeholder = { Text("Incolla o scrivi qui gli articoli della nota:\n- Latte\n- Pane\n- Caffè") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .testTag("dialog_keep_text_content")
                )

                // Anteprima prodotti rilevati in tempo reale
                if (detectedItems.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "📋 Articoli reali rilevati (${detectedItems.size}):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreenPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        detectedItems.forEach { item ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ForestGreenPrimary.copy(alpha = 0.12f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = ForestGreenPrimary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = item,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = ForestGreenPrimary
                                    )
                                }
                            }
                        }
                    }
                } else if (textContent.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "⚠️ Nessun articolo valido rilevato nel testo inserito.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Card istruzioni rapide
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "💡 Come sincronizzare la nota esatta:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "• Da Keep: apri la nota '$noteTitle' -> tocca i 3 puntini ⋮ -> Invia -> Invia tramite altra app -> seleziona Dispensa Smart.\n• Oppure tocca 'Cerca in Keep', copia il testo della lista e tocca 'Incolla Testo' qui sopra.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 14.sp
                        )
                    }
                }

                if (onClearKeepItems != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showConfirmClear = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pulisci articoli Keep esistenti", fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (textContent.isNotBlank()) {
                        onImport(textContent, noteTitle)
                        onDismiss()
                    } else {
                        Toast.makeText(context, "Incolla o inserisci prima gli articoli da importare", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = textContent.isNotBlank() && detectedItems.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
                modifier = Modifier.testTag("dialog_btn_confirm_import_keep")
            ) {
                Icon(imageVector = Icons.Default.Assignment, contentDescription = "Importa", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Importa ${if (detectedItems.isNotEmpty()) "(${detectedItems.size})" else ""}")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annulla")
            }
        }
    )
}
