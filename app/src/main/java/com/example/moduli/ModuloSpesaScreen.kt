package com.example.moduli

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RestorePage
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.Prodotto
import com.example.ui.theme.ForestGreenPrimary
import com.example.viewmodel.DispensaViewModel

@Composable
fun ModuloSpesaScreen(
    viewModel: DispensaViewModel,
    modifier: Modifier = Modifier
) {
    val prodottiSpesa by viewModel.prodottiSpesa.collectAsStateWithLifecycle()
    val isKeepIntegrationEnabled by viewModel.isKeepIntegrationEnabled.collectAsStateWithLifecycle()
    val keepNoteTitle by viewModel.keepNoteTitle.collectAsStateWithLifecycle()
    var nuovoArticoloNome by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.ShoppingCart,
                contentDescription = "Spesa",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Lista della Spesa 🛒",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Text(
            text = "Articoli da acquistare. Spuntali quando li compri per contrassegnarli come acquistati.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Manual Add Field
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = nuovoArticoloNome,
                onValueChange = { nuovoArticoloNome = it },
                placeholder = { Text("Aggiungi prodotto alla spesa...") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("add_manual_shopping_input")
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (nuovoArticoloNome.isNotBlank()) {
                        viewModel.insertProdotto(
                            Prodotto(
                                nome = nuovoArticoloNome.trim(),
                                categoria = "Dispensa Secca",
                                quantita = 0,
                                dataScadenza = System.currentTimeMillis() + (30L * 86400000L),
                                inListaSpesa = true,
                                comprato = false
                            )
                        )
                        nuovoArticoloNome = ""
                    }
                },
                enabled = nuovoArticoloNome.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
                modifier = Modifier.testTag("add_manual_shopping_button")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Aggiungi")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isKeepIntegrationEnabled) {
            OutlinedButton(
                onClick = { viewModel.sincronizzaConGoogleKeep() },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("button_quick_keep_sync_spesa")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                    contentDescription = "Controlla Keep",
                    tint = ForestGreenPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Controlla Nota Keep ('$keepNoteTitle')",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ForestGreenPrimary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        val spuntatiCount = prodottiSpesa.count { it.comprato }
        if (spuntatiCount > 0) {
            Button(
                onClick = { viewModel.cancellaProdottiSpuntati() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("button_clear_bought_items")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Cancella spuntati",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Cancella prodotti spuntati ($spuntatiCount)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (prodottiSpesa.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.AddShoppingCart,
                        contentDescription = "Lista Vuota",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "La lista della spesa è vuota! 🎉",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Quando un articolo con scorta minima finisce in dispensa, verrà aggiunto automaticamente qui.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(prodottiSpesa, key = { it.id }) { prodotto ->
                    val isComprato = prodotto.comprato

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isComprato)
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            else
                                MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Checkbox(
                                checked = isComprato,
                                onCheckedChange = { viewModel.toggleCompratoSpesa(prodotto) },
                                colors = CheckboxDefaults.colors(checkedColor = ForestGreenPrimary),
                                modifier = Modifier.testTag("check_shopping_item_${prodotto.id}")
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = prodotto.nome,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        textDecoration = if (isComprato) TextDecoration.LineThrough else TextDecoration.None,
                                        color = if (isComprato) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (prodotto.daKeep) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = ForestGreenPrimary.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "📌 Keep",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ForestGreenPrimary,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "Categoria: ${prodotto.categoria} • ${prodotto.posizione}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Button(
                                onClick = { viewModel.toggleCompratoSpesa(prodotto) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isComprato) ForestGreenPrimary.copy(alpha = 0.2f) else ForestGreenPrimary,
                                    contentColor = if (isComprato) ForestGreenPrimary else Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("restore_item_button_${prodotto.id}")
                            ) {
                                Icon(
                                    imageVector = if (isComprato) Icons.Default.CheckCircle else Icons.Default.CheckCircle,
                                    contentDescription = if (isComprato) "Spuntato" else "Comprato",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isComprato) "Spuntato ✓" else "Comprato",
                                    fontSize = 11.sp,
                                    fontWeight = if (isComprato) FontWeight.Bold else FontWeight.Normal
                                )
                            }

                            IconButton(
                                onClick = { viewModel.rimuoviDaListaSpesa(prodotto) },
                                modifier = Modifier.testTag("delete_shopping_item_${prodotto.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Rimuovi",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
