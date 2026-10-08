package com.example.moduli

import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.util.Calendar
import androidx.compose.material.icons.filled.ArrowDropDown

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuloCantinaScreen() {
    var isCantinaModeEnabled by remember { mutableStateOf(false) }
    
    val lockTypes = listOf("Nessuno", "Scorrimento (Swipe)", "PIN", "Segno (Pattern)", "Impronta Digitale/Biometrico")
    var expanded by remember { mutableStateOf(false) }
    var selectedLockType by remember { mutableStateOf(lockTypes[0]) }
    
    var securityCode by remember { mutableStateOf("") }
    
    var lightSensorHour by remember { mutableStateOf(22) }
    var lightSensorMinute by remember { mutableStateOf(0) }
    
    var cameraHour by remember { mutableStateOf(8) }
    var cameraMinute by remember { mutableStateOf(0) }
    
    var timeoutSeconds by remember { mutableFloatStateOf(60f) }
    
    val context = LocalContext.current

    fun showTimePicker(initialHour: Int, initialMinute: Int, onTimeSelected: (Int, Int) -> Unit) {
        TimePickerDialog(context, { _, hourOfDay, minute ->
            onTimeSelected(hourOfDay, minute)
        }, initialHour, initialMinute, true).show()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Modalità Cantina 🍷",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Text(
            text = "Configura l'accensione automatica dello schermo e lo sblocco per l'uso fisso del dispositivo.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Attiva Modalità Cantina",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Switch(
                    checked = isCantinaModeEnabled,
                    onCheckedChange = { isCantinaModeEnabled = it }
                )
            }
        }
        
        if (isCantinaModeEnabled) {
            Divider()

            // Lock Type Selection
            Text("Tipo di blocco schermo attuale", fontWeight = FontWeight.SemiBold)
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedLockType,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    lockTypes.forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text(selectionOption) },
                            onClick = {
                                selectedLockType = selectionOption
                                expanded = false
                            }
                        )
                    }
                }
            }

            if (selectedLockType == "PIN" || selectedLockType == "Segno (Pattern)") {
                OutlinedTextField(
                    value = securityCode,
                    onValueChange = { securityCode = it },
                    label = { Text("Inserisci il $selectedLockType per lo sblocco automatico") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Il codice verrà utilizzato dal Servizio di Accessibilità per sbloccare il dispositivo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (selectedLockType == "Impronta Digitale/Biometrico") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = "Warning", tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "L'uso dello sblocco biometrico richiede l'intervento manuale. Rimuovi il blocco o usa PIN/Scorrimento per automatizzare.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Text("Fasce Orarie Sensori", fontWeight = FontWeight.SemiBold)

            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Orario Attivazione Sensore di Luce (Notte)", style = MaterialTheme.typography.labelLarge)
                    Text("Si attiva quando rilevata la luce ambientale.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { showTimePicker(lightSensorHour, lightSensorMinute) { h, m -> lightSensorHour = h; lightSensorMinute = m } }) {
                        Text(String.format("Impostato alle: %02d:%02d", lightSensorHour, lightSensorMinute))
                    }
                }
            }

            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Orario Attivazione Fotocamera (Giorno)", style = MaterialTheme.typography.labelLarge)
                    Text("Si attiva rilevando movimento (pixel detection).", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { showTimePicker(cameraHour, cameraMinute) { h, m -> cameraHour = h; cameraMinute = m } }) {
                        Text(String.format("Impostato alle: %02d:%02d", cameraHour, cameraMinute))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            
            Text("Timeout Spegnimento Schermo: ${timeoutSeconds.toInt()} sec", fontWeight = FontWeight.SemiBold)
            Slider(
                value = timeoutSeconds,
                onValueChange = { timeoutSeconds = it },
                valueRange = 30f..300f,
                steps = 27
            )
            Text(
                text = "Tempo di inattività dopo il quale il dispositivo si blocca.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
