package com.example.moduli

import androidx.compose.material3.MenuAnchorType

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.Prodotto
import com.example.ui.theme.ExpiryRed
import com.example.ui.theme.ForestGreenPrimary
import com.example.viewmodel.DispensaViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuloModificaScreen(
    viewModel: DispensaViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prodottoToEdit by viewModel.selectedProdottoForEdit.collectAsStateWithLifecycle()

    if (prodottoToEdit == null) {
        onBack()
        return
    }

    val prod = prodottoToEdit!!

    var nome by remember { mutableStateOf(prod.nome) }
    var barcode by remember { mutableStateOf(prod.barcode) }
    var categoria by remember { mutableStateOf(prod.categoria) }
    var posizione by remember { mutableStateOf(prod.posizione) }
    var quantita by remember { mutableIntStateOf(prod.quantita) }
    var quantitaMinima by remember { mutableIntStateOf(prod.quantitaMinima) }
    var dataScadenza by remember { mutableLongStateOf(prod.dataScadenza) }
    var note by remember { mutableStateOf(prod.note) }

    var showDeleteDialog by remember { mutableStateOf(false) }

    val categorieOptions = listOf("Dispensa Secca", "Frigo", "Surgelati", "Bevande", "Igiene/Casa", "Altro")
    val posizioniOptions = listOf("Dispensa", "Frigo", "Freezer")

    var isCategoryExpanded by remember { mutableStateOf(false) }
    var isPositionExpanded by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.ITALIAN) }

    val calendar = Calendar.getInstance()
    calendar.timeInMillis = dataScadenza
    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val cal = Calendar.getInstance()
                cal.set(year, month, dayOfMonth)
                dataScadenza = cal.timeInMillis
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Modifica Prodotto ✏️",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Nome
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Nome Prodotto") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_input_nome")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Barcode
                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    label = { Text("Codice a Barre (Barcode)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Categoria Menu
                ExposedDropdownMenuBox(
                    expanded = isCategoryExpanded,
                    onExpandedChange = { isCategoryExpanded = !isCategoryExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = categoria,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Categoria") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCategoryExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = isCategoryExpanded,
                        onDismissRequest = { isCategoryExpanded = false }
                    ) {
                        categorieOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    categoria = option
                                    isCategoryExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Posizione Menu
                ExposedDropdownMenuBox(
                    expanded = isPositionExpanded,
                    onExpandedChange = { isPositionExpanded = !isPositionExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = posizione,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Luogo di Conservazione") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isPositionExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = isPositionExpanded,
                        onDismissRequest = { isPositionExpanded = false }
                    ) {
                        posizioniOptions.forEach { pos ->
                            DropdownMenuItem(
                                text = { Text(pos) },
                                onClick = {
                                    posizione = pos
                                    isPositionExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Quantità & Quantità Minima Steppers
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Quantità", style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            IconButton(onClick = { if (quantita > 0) quantita-- }, modifier = Modifier.size(36.dp)) {
                                Icon(imageVector = Icons.Default.Remove, contentDescription = "Meno")
                            }
                            Text(
                                text = "$quantita",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            IconButton(onClick = { quantita++ }, modifier = Modifier.size(36.dp)) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = "Più")
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Scorta Minima ⚠️", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            IconButton(onClick = { if (quantitaMinima > 0) quantitaMinima-- }, modifier = Modifier.size(36.dp)) {
                                Icon(imageVector = Icons.Default.Remove, contentDescription = "Meno Min")
                            }
                            Text(
                                text = "$quantitaMinima",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            IconButton(onClick = { quantitaMinima++ }, modifier = Modifier.size(36.dp)) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = "Più Min")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // DatePicker Data Scadenza
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Data Scadenza", style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { datePickerDialog.show() },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.CalendarToday, contentDescription = "Calendario", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = dateFormat.format(Date(dataScadenza)), fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Note
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (es. Aperto il 12/03)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ExpiryRed),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("delete_product_button")
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Elimina")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Elimina")
                    }

                    Button(
                        onClick = {
                            viewModel.updateProdotto(
                                prod.copy(
                                    nome = nome.trim(),
                                    barcode = barcode,
                                    categoria = categoria,
                                    posizione = posizione,
                                    quantita = quantita,
                                    quantitaMinima = quantitaMinima,
                                    dataScadenza = dataScadenza,
                                    note = note,
                                    inListaSpesa = quantita == 0
                                )
                            )
                            onBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.5f)
                            .height(48.dp)
                            .testTag("save_edit_button")
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = "Salva")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Salva Modifiche")
                    }
                }
            }
        }
    }

    // Confirm Delete Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Conferma Eliminazione") },
            text = { Text("Sei sicuro di voler eliminare permanentemente '${prod.nome}' dalla dispensa?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteProdotto(prod)
                        showDeleteDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpiryRed)
                ) {
                    Text("Elimina")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Annulla")
                }
            }
        )
    }
}
