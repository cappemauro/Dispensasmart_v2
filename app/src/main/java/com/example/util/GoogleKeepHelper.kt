package com.example.util

import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object GoogleKeepHelper {
    /**
     * Apre Google Keep (o il browser se l'app non è installata)
     * e copia il nome della nota negli appunti per consentire la ricerca immediata.
     */
    fun openKeepSearch(context: Context, noteTitle: String) {
        val cleanTitle = noteTitle.trim().ifBlank { "Lista della Spesa" }
        
        // 1. Copia sempre il titolo negli appunti per comodità dell'utente
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("Keep Note Title", cleanTitle)
            clipboard?.setPrimaryClip(clip)
        } catch (_: Exception) {}

        // 2. Prova ad avviare l'app Google Keep nativa
        val packageManager = context.packageManager
        val keepPackage = "com.google.android.keep"
        
        // A) Tentativo tramite ACTION_SEARCH indirizzato a Keep
        val searchIntent = Intent(Intent.ACTION_SEARCH).apply {
            setPackage(keepPackage)
            putExtra(SearchManager.QUERY, cleanTitle)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        var launched = false
        try {
            if (searchIntent.resolveActivity(packageManager) != null) {
                context.startActivity(searchIntent)
                launched = true
            }
        } catch (_: Exception) {}

        // B) Se ACTION_SEARCH non è gestito, apri direttamente l'app Google Keep
        if (!launched) {
            try {
                val launchIntent = packageManager.getLaunchIntentForPackage(keepPackage)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    launched = true
                }
            } catch (_: Exception) {}
        }

        // C) Se Google Keep non è installato sul dispositivo, apri la versione Web
        if (!launched) {
            try {
                val webUri = Uri.parse("https://keep.google.com/#search/text=" + Uri.encode(cleanTitle))
                val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                launched = true
            } catch (e: Exception) {
                Toast.makeText(context, "Impossibile aprire Google Keep: ${e.message}", Toast.LENGTH_SHORT).show()
                return
            }
        }

        Toast.makeText(
            context,
            "Cercando '$cleanTitle'... Nome copiato negli appunti! Incollalo nella ricerca di Keep o condividi la nota qui.",
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * Pulisce ed estrae rigorosamente gli articoli reali da un testo di Google Keep.
     * Rimuove:
     * - Spunte, checkbox, elenchi puntati o numerati (es. "[ ]", "[x]", "☑", "•", "1.", etc.)
     * - Intestazioni tipiche di Keep (es. "Elementi completati", "Checked items", "Note", link http, ecc.)
     * - Il titolo stesso della nota (per non inserirlo erroneamente come prodotto)
     * - Righe vuote o righe con solo punteggiatura / data
     */
    fun cleanAndExtractKeepItems(rawText: String, noteTitle: String): List<String> {
        val titleClean = noteTitle.trim().lowercase()
        val ignoredHeaders = setOf(
            "elementi completati", "elementi selezionati", "elementi spuntati",
            "checked items", "completed items", "note", "promemoria", "elenco",
            "lista", "lista spesa", "lista della spesa", "articoli", "spesa"
        )

        val result = mutableListOf<String>()

        val rawLines = rawText.lines()
        for (line in rawLines) {
            var trimmed = line.trim()
            if (trimmed.isBlank()) continue

            // Ignora link Web o note di sistema
            if (trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) ||
                trimmed.startsWith("www.", ignoreCase = true)
            ) {
                continue
            }

            // Rimuove spunte checkbox di Keep, numeri, trattini, asterischi, pallini
            // Es: "[ ] Latte", "[x] Pane", "☑ Uova", "- Burro", "1. Pasta"
            trimmed = trimmed.replace(Regex("^(\\[[ xX]?\\]|[☑☐✓✔\\*\\-\\•\\+]+|\\d+[\\.\\)]\\s*)\\s*"), "").trim()

            // Rimuove eventuali parentesi quadre residue
            trimmed = trimmed.replace(Regex("^[\\[\\]\\(\\)]+"), "").trim()

            if (trimmed.isBlank() || trimmed.length < 2) continue

            val lower = trimmed.lowercase()

            // Ignora se la riga è identica al titolo della nota
            if (lower == titleClean || lower == "nota: $titleClean" || lower == "lista: $titleClean") {
                continue
            }

            // Ignora intestazioni tipiche di Google Keep
            if (ignoredHeaders.contains(lower) || lower.startsWith("condiviso con") || lower.startsWith("shared with")) {
                continue
            }

            // Se la riga contiene più elementi separati da virgola o punto e virgola
            val subItems = if ((trimmed.contains(",") || trimmed.contains(";")) && !trimmed.contains("\n")) {
                trimmed.split(Regex("[,;]")).map { it.trim() }.filter { it.length >= 2 }
            } else {
                listOf(trimmed)
            }

            for (item in subItems) {
                val cleanItem = item.replace(Regex("^[\\[\\]xX\\*\\-\\•\\s☑☐\\d\\.\\)]+"), "").trim()
                if (cleanItem.isNotBlank() &&
                    cleanItem.length >= 2 &&
                    cleanItem.lowercase() != titleClean &&
                    !ignoredHeaders.contains(cleanItem.lowercase())
                ) {
                    // Capitalizza la prima lettera per una visualizzazione elegante
                    val capitalized = cleanItem.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    result.add(capitalized)
                }
            }
        }

        return result.distinct()
    }
}
