package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCartCheckout
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Prodotto
import com.example.model.StatoScadenza
import com.example.ui.theme.ExpiryGreen
import com.example.ui.theme.ExpiryGreenContainer
import com.example.ui.theme.ExpiryGreenText
import com.example.ui.theme.ExpiryOrange
import com.example.ui.theme.ExpiryOrangeContainer
import com.example.ui.theme.ExpiryOrangeText
import com.example.ui.theme.ExpiryRed
import com.example.ui.theme.ExpiryRedContainer
import com.example.ui.theme.ExpiryRedText
import com.example.ui.theme.ForestGreenPrimary

@Composable
fun ProductCard(
    prodotto: Prodotto,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onEdit: () -> Unit,
    onAggiungiSpesa: () -> Unit,
    isMinQuantityAlertEnabled: Boolean = true,
    isExpiryManagementEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val isFinito = prodotto.quantita <= 0
    val isSottoScorta = isMinQuantityAlertEnabled && prodotto.quantitaMinima > 0 && prodotto.quantita <= prodotto.quantitaMinima && !isFinito
    val statoScadenza = prodotto.getStatoScadenza()

    val cardBgColor = when {
        isFinito -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        isSottoScorta -> ExpiryRedContainer.copy(alpha = 0.25f)
        else -> MaterialTheme.colorScheme.surface
    }

    val cardBorderModifier = if (isSottoScorta) {
        Modifier.border(2.dp, ExpiryRed, RoundedCornerShape(16.dp))
    } else {
        Modifier
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(cardBorderModifier)
            .testTag("product_card_${prodotto.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isFinito) 1.dp else 3.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            // Top Row: Category Icon, Name, Category chip & Edit button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(getCategoriaBgColor(prodotto.categoria)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = getCategoriaIcon(prodotto.categoria),
                            contentDescription = prodotto.categoria,
                            tint = getCategoriaIconColor(prodotto.categoria),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = prodotto.nome,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (isFinito) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = prodotto.categoria,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = " • ${prodotto.posizione}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("edit_button_${prodotto.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Modifica Prodotto",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Expiry Badge Row & Quantity Steppers
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Expiry Badge
                ExpiryBadge(prodotto = prodotto, isEnabled = isExpiryManagementEnabled)

                // Quantity Controls
                if (!isFinito) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        IconButton(
                            onClick = onDecrement,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("decrement_button_${prodotto.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Diminuisci quantità",
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Text(
                            text = "Qtà: ${prodotto.quantita}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        IconButton(
                            onClick = onIncrement,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("increment_button_${prodotto.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Aumenta quantità",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Sotto Scorta Minima Banner
            if (isSottoScorta) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ExpiryRedContainer)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Sotto scorta minima",
                            tint = ExpiryRedText,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "⚠️ Sotto Scorta (Qtà: ${prodotto.quantita} ≤ Min: ${prodotto.quantitaMinima})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = ExpiryRedText
                        )
                    }
                    if (prodotto.inListaSpesa) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ForestGreenPrimary.copy(alpha = 0.15f),
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Text(
                                text = "🛒 In Spesa",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForestGreenPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // If Finished (Qtà = 0), show prominent badge + single tap "Aggiungi alla Lista Spesa"
            if (isFinito) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Prodotto Finito",
                            tint = ExpiryRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Esaurito!",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = ExpiryRed
                        )
                    }

                    Button(
                        onClick = onAggiungiSpesa,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ForestGreenPrimary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.testTag("add_shopping_list_${prodotto.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCartCheckout,
                            contentDescription = "Aggiungi a Spesa",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Aggiungi a Lista Spesa", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ExpiryBadge(prodotto: Prodotto, isEnabled: Boolean = true) {
    if (!isEnabled) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.clip(RoundedCornerShape(12.dp))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Scadenza: ${prodotto.getDataScadenzaFormatted()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val stato = prodotto.getStatoScadenza()
    val giorni = prodotto.getGiorniAllaScadenza()

    val (bgColor, textColor, labelText) = when (stato) {
        StatoScadenza.SCADUTO -> {
            val text = if (giorni == 0L) "Scade OGGI!" else "Scaduto da ${-giorni}g (${prodotto.getDataScadenzaFormatted()})"
            Triple(ExpiryRedContainer, ExpiryRedText, text)
        }
        StatoScadenza.IN_SCADENZA -> {
            Triple(ExpiryOrangeContainer, ExpiryOrangeText, "In scadenza tra ${giorni}g (${prodotto.getDataScadenzaFormatted()})")
        }
        StatoScadenza.FRESCO -> {
            Triple(ExpiryGreenContainer, ExpiryGreenText, "Scadenza: ${prodotto.getDataScadenzaFormatted()}")
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(textColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = labelText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

fun getCategoriaIcon(categoria: String): ImageVector {
    return when {
        categoria.contains("Da Catalogare", ignoreCase = true) -> Icons.Default.AutoAwesome
        categoria.contains("Frigo", ignoreCase = true) -> Icons.Default.Kitchen
        categoria.contains("Surgelat", ignoreCase = true) -> Icons.Default.AcUnit
        categoria.contains("Bevand", ignoreCase = true) -> Icons.Default.LocalBar
        categoria.contains("Igiene", ignoreCase = true) || categoria.contains("Casa", ignoreCase = true) -> Icons.Default.CleaningServices
        else -> Icons.Default.ShoppingBag
    }
}

fun getCategoriaBgColor(categoria: String): Color {
    return when {
        categoria.contains("Da Catalogare", ignoreCase = true) -> Color(0xFFFFF8E1)
        categoria.contains("Frigo", ignoreCase = true) -> Color(0xFFE1F5FE)
        categoria.contains("Surgelat", ignoreCase = true) -> Color(0xFFE0F7FA)
        categoria.contains("Bevand", ignoreCase = true) -> Color(0xFFFFF3E0)
        categoria.contains("Igiene", ignoreCase = true) -> Color(0xFFF3E5F5)
        else -> Color(0xFFE8F5E9)
    }
}

fun getCategoriaIconColor(categoria: String): Color {
    return when {
        categoria.contains("Da Catalogare", ignoreCase = true) -> Color(0xFFFFA000)
        categoria.contains("Frigo", ignoreCase = true) -> Color(0xFF0288D1)
        categoria.contains("Surgelat", ignoreCase = true) -> Color(0xFF00838F)
        categoria.contains("Bevand", ignoreCase = true) -> Color(0xFFEF6C00)
        categoria.contains("Igiene", ignoreCase = true) -> Color(0xFF7B1FA2)
        else -> Color(0xFF2E7D32)
    }
}
