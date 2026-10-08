package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(tableName = "prodotti")
data class Prodotto(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val barcode: String = "",
    val nome: String = "",
    val categoria: String = "Dispensa Secca", // Dispensa Secca, Frigo, Surgelati, Bevande, Igiene/Casa, Altro
    val quantita: Int = 1,
    val quantitaMinima: Int = 1, // Quantità minima sotto la quale scatta il riquadro rosso e la spesa automatica
    val dataScadenza: Long = System.currentTimeMillis() + 86400000L, // Epoch milliseconds
    val posizione: String = "Dispensa", // Frigo, Dispensa, Freezer
    val note: String = "",
    val inListaSpesa: Boolean = false,
    val daKeep: Boolean = false,
    val comprato: Boolean = false
) {
    fun getGiorniAllaScadenza(): Long {
        val oggiMillis = System.currentTimeMillis()
        val diffMillis = dataScadenza - oggiMillis
        return diffMillis / (1000 * 60 * 60 * 24)
    }

    fun getDataScadenzaFormatted(): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.ITALIAN)
        return sdf.format(Date(dataScadenza))
    }

    fun getStatoScadenza(): StatoScadenza {
        val giorni = getGiorniAllaScadenza()
        return when {
            giorni <= 0 -> StatoScadenza.SCADUTO
            giorni <= 3 -> StatoScadenza.IN_SCADENZA
            else -> StatoScadenza.FRESCO
        }
    }
}

enum class StatoScadenza {
    SCADUTO,     // Rosso: <= 0 giorni (scaduto o scade oggi)
    IN_SCADENZA, // Giallo/Arancio: <= 3 giorni
    FRESCO       // Verde: > 3 giorni
}
