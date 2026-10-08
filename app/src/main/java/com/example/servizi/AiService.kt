package com.example.servizi

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class AiProductResult(
    val nome: String,
    val categoria: String, // Dispensa Secca, Frigo, Surgelati, Bevande, Igiene/Casa, Altro
    val posizione: String, // Frigo, Dispensa, Freezer
    val giorniScadenzaStimati: Int, // Giorni stimati da oggi
    val note: String = "",
    val isOfflinePending: Boolean = false
)

class AiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    // Ricco dizionario locale per prodotti italiani comuni e test istantaneo senza rete
    private val barcodeDictionary = mapOf(
        // Pasta e Cereali
        "8001234567890" to AiProductResult("Spaghetti Barilla n.5 500g", "Dispensa Secca", "Dispensa", 730, "Mantenere in luogo fresco e asciutto"),
        "8076809572224" to AiProductResult("Penne Rigate Barilla 500g", "Dispensa Secca", "Dispensa", 730, "Mantenere in luogo fresco e asciutto"),
        "8076809572231" to AiProductResult("Fusilli Barilla 500g", "Dispensa Secca", "Dispensa", 730, "Mantenere in luogo fresco e asciutto"),
        "8001250120128" to AiProductResult("Spaghetti De Cecco n.12 500g", "Dispensa Secca", "Dispensa", 730, "Pasta di semola di grano duro"),
        "8076809513753" to AiProductResult("Pesto alla Genovese Barilla 190g", "Dispensa Secca", "Dispensa", 365, "In frigo dopo l'apertura"),
        
        // Pomodoro e Conserve
        "8000500000000" to AiProductResult("Passata di Pomodoro Mutti 700g", "Dispensa Secca", "Dispensa", 365, "Una volta aperta conservare in frigo"),
        "8000500001000" to AiProductResult("Polpa di Pomodoro Mutti 2x400g", "Dispensa Secca", "Dispensa", 500, "In frigo dopo l'apertura"),
        "8000500002000" to AiProductResult("Pelati Veraci Cirio 400g", "Dispensa Secca", "Dispensa", 500, "Pomodori pelati italiani"),
        "8000600000000" to AiProductResult("Tonno Rio Mare all'Olio di Oliva 3x80g", "Dispensa Secca", "Dispensa", 1095, "Tonno pinne gialle"),
        "8000600001000" to AiProductResult("Tonno Nostromo Naturale 3x65g", "Dispensa Secca", "Dispensa", 1095, "Al naturale, ricco di proteine"),
        "8000700000000" to AiProductResult("Olio Extra Vergine di Oliva Monini 1L", "Dispensa Secca", "Dispensa", 540, "Conservare al riparo dalla luce"),

        // Freschi e Latticini (Frigo)
        "8008800123456" to AiProductResult("Latte Parzialmente Scremato Granarolo 1L", "Frigo", "Frigo", 10, "Conservare in frigorifero dopo l'apertura"),
        "8001150000000" to AiProductResult("Latte Fresco Intero Parmalat 1L", "Frigo", "Frigo", 8, "Conservare a max +4°C"),
        "8001150001000" to AiProductResult("Latte Zymil Senza Lattosio 1L", "Frigo", "Frigo", 90, "UHT ad alta digeribilità"),
        "8001120000000" to AiProductResult("Yogurt Naturale Intero 2x125g", "Frigo", "Frigo", 14, "Conservare a max +4°C"),
        "8000425000000" to AiProductResult("Mozzarella Santa Lucia Galbani 3x125g", "Frigo", "Frigo", 12, "Conservare in frigorifero a max +4°C"),
        "8001550000000" to AiProductResult("Burro Parmareggio 250g", "Frigo", "Frigo", 60, "Burro fresco da panna di latte"),
        "8000200000000" to AiProductResult("Parmigiano Reggiano DOP 200g", "Frigo", "Frigo", 90, "Stagionatura 24 mesi"),

        // Colazione e Dolci
        "8000500310427" to AiProductResult("Nutella Biscuits Ferrero 304g", "Dispensa Secca", "Dispensa", 240, "Luogo fresco e asciutto"),
        "8000500037560" to AiProductResult("Nutella Ferrero 750g", "Dispensa Secca", "Dispensa", 365, "Crema spalmabile alle nocciole"),
        "8076809513715" to AiProductResult("Macine Mulino Bianco 800g", "Dispensa Secca", "Dispensa", 240, "Biscotti con panna fresca"),
        "8076809513722" to AiProductResult("Tarallucci Mulino Bianco 800g", "Dispensa Secca", "Dispensa", 240, "Biscotti con uova fresche"),
        "8000950000000" to AiProductResult("Gocciole Chocolate Pavesi 500g", "Dispensa Secca", "Dispensa", 240, "Biscotti con gocce di cioccolato"),
        "8076809513739" to AiProductResult("Pan Bauletto Bianco Mulino Bianco 400g", "Dispensa Secca", "Dispensa", 45, "Pane morbido a fette"),
        "8076809513746" to AiProductResult("Fette Biscottate Dorate Mulino Bianco 315g", "Dispensa Secca", "Dispensa", 180, "Fette biscottate classiche"),

        // Surgelati e Ghiaccio (Freezer)
        "8000300000000" to AiProductResult("Piselli Novelli Surgelati Findus 450g", "Surgelati", "Freezer", 180, "Mantenere nel congelatore a -18°C"),
        "8000300001000" to AiProductResult("Bastoncini di Merluzzo Findus x10", "Surgelati", "Freezer", 240, "100% filetti di merluzzo"),
        "8000300002000" to AiProductResult("Spinaci Foglia Più Orogel 900g", "Surgelati", "Freezer", 240, "Spinaci in cubetti"),
        "8000300003000" to AiProductResult("Pizza Ristorante Salame Cameo", "Surgelati", "Freezer", 180, "Cuocere in forno a 220°C"),
        "8000300004000" to AiProductResult("Gelato Viennetta Vaniglia Algida", "Surgelati", "Freezer", 180, "Dessert gelato"),

        // Bevande e Caffè
        "8001040000000" to AiProductResult("Acqua Naturale San Benedetto 1.5L", "Bevande", "Dispensa", 365, "Al riparo dalla luce e dal calore"),
        "8001040001000" to AiProductResult("Acqua Naturale Sant'Anna 1.5L", "Bevande", "Dispensa", 365, "Sorgente Rebruant"),
        "8001040002000" to AiProductResult("Acqua Minerale Levissima 1.5L", "Bevande", "Dispensa", 365, "Acqua pura di montagna"),
        "8001040003000" to AiProductResult("Acqua Effervescente Naturale Ferrarelle 1.5L", "Bevande", "Dispensa", 365, "Effervescenza naturale"),
        "5449000000996" to AiProductResult("Coca-Cola Original Taste 330ml", "Bevande", "Dispensa", 240, "Servire ghiacciata"),
        "5449000131805" to AiProductResult("Coca-Cola Zero Zuccheri 330ml", "Bevande", "Dispensa", 240, "Senza calorie"),
        "8008440000000" to AiProductResult("Birra Peroni 66cl", "Bevande", "Dispensa", 365, "Lager italiana 4.7% vol"),
        "8008440001000" to AiProductResult("Birra Moretti Ricetta Originale 66cl", "Bevande", "Dispensa", 365, "Lager dorata"),
        "8000070000000" to AiProductResult("Caffè Qualità Rossa Lavazza 250g", "Bevande", "Dispensa", 365, "Miscela di caffè macinato"),
        "8000070001000" to AiProductResult("Caffè Crema e Gusto Lavazza 250g", "Bevande", "Dispensa", 365, "Gusto intenso e rotondo"),

        // Igiene e Casa
        "8000123000000" to AiProductResult("Detersivo Piatti Svelto al Limone 1L", "Igiene/Casa", "Dispensa", 1095, "Tenere lontano dai bambini"),
        "8001090000000" to AiProductResult("Detersivo Lavatrice Dash Pods x24", "Igiene/Casa", "Dispensa", 730, "Capsule lavatrice tutto in 1"),
        "8001090001000" to AiProductResult("Candeggina Classica Ace 1L", "Igiene/Casa", "Dispensa", 730, "Protezione e igiene"),
        "8015194511019" to AiProductResult("Sgrassatore Universale Chanteclair Marsiglia 600ml", "Igiene/Casa", "Dispensa", 1095, "Superpotente su ogni sporco"),
        "8004260000000" to AiProductResult("Carta Igienica Scottex L'Originale 4 rotoli", "Igiene/Casa", "Dispensa", 1825, "Carta igienica morbida")
    )

    suspend fun analizzaBarcodeONome(barcodeONome: String, forceNetwork: Boolean = false): AiProductResult = withContext(Dispatchers.IO) {
        val trimmed = barcodeONome.trim()
        if (trimmed.isBlank()) {
            return@withContext AiProductResult("Prodotto", "Dispensa Secca", "Dispensa", 30)
        }

        val isBarcodeDigits = trimmed.matches(Regex("^\\d{8,14}$"))

        // 1. Controlla il dizionario locale predefinito
        barcodeDictionary[trimmed]?.let {
            return@withContext it
        }

        // 2. Se è un codice a barre numerico, interroga OPEN FOOD FACTS (endpoint prioritario italiano)
        if (isBarcodeDigits) {
            val offResult = cercaSuOpenFoodFacts(trimmed)
            if (offResult != null) {
                return@withContext offResult
            }
        }

        // 3. Se è una stringa testuale o barcode non trovato su OFF, prova la ricerca testuale su Open Food Facts
        if (!isBarcodeDigits) {
            val offSearch = cercaPerNomeSuOpenFoodFacts(trimmed)
            if (offSearch != null) {
                return@withContext offSearch
            }
        }

        // 4. Prova chiamata Gemini API (se configurata dall'utente)
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Exception) {
            ""
        }

        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            val geminiResult = analizzaConGemini(trimmed, apiKey)
            if (geminiResult != null) {
                return@withContext geminiResult
            }
        }

        // 5. Euristica intelligente su parole chiave italiane (per nomi digitati)
        if (!isBarcodeDigits) {
            val euristica = analizzaConParoleChiave(trimmed)
            if (euristica != null) {
                return@withContext euristica
            }
        }

        // 6. Se è richiesta catalogazione forzata (tasto "Cataloga Ora"):
        // Non lasciamo il prodotto bloccato in "Da Catalogare" a vuoto!
        // Assegniamo una catalogazione sensata in Dispensa Secca o in base ai caratteri del nome
        if (forceNetwork) {
            val isItalianBarcode = isBarcodeDigits && trimmed.startsWith("80")
            val nomeAssegnato = if (isItalianBarcode) "Prodotto Italiano ($trimmed)" else if (isBarcodeDigits) "Prodotto $trimmed" else trimmed
            val (cat, pos, giorni) = inferisciCategoriaEPosizione(nomeAssegnato, "")
            return@withContext AiProductResult(
                nome = nomeAssegnato,
                categoria = cat,
                posizione = pos,
                giorniScadenzaStimati = giorni,
                note = "Catalogato automaticamente in dispensa",
                isOfflinePending = false
            )
        }

        // 7. Fallback predefinito se non identificato (in attesa di connessione)
        stimaOfflineFallback(trimmed)
    }

    private fun cercaSuOpenFoodFacts(barcode: String): AiProductResult? {
        val cleanBarcode = barcode.trim()
        val barcodesToTry = if (cleanBarcode.length == 12) listOf(cleanBarcode, "0$cleanBarcode") else listOf(cleanBarcode)

        for (code in barcodesToTry) {
            // URL con precedenza all'istanza italiana v2 con campi ridotti (ultra veloce)
            val urls = listOf(
                "https://it.openfoodfacts.org/api/v2/product/$code?fields=product_name,product_name_it,generic_name_it,generic_name,brands,quantity,categories",
                "https://world.openfoodfacts.org/api/v2/product/$code?fields=product_name,product_name_it,generic_name_it,generic_name,brands,quantity,categories",
                "https://world.openfoodfacts.org/api/v0/product/$code.json"
            )

            for (url in urls) {
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "DispensaSmart-Android/1.0 (contact: info@dispensasmart.app)")
                        .get()
                        .build()

                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val json = JSONObject(body)
                        val status = json.optInt("status", 0)
                        if (status == 1) {
                            val product = json.optJSONObject("product") ?: continue

                            val nameIt = product.optString("product_name_it").trim()
                            val nameGenIt = product.optString("generic_name_it").trim()
                            val nameGeneric = product.optString("product_name").trim()
                            val genericName = product.optString("generic_name").trim()
                            val brand = product.optString("brands").trim()
                            val quantity = product.optString("quantity").trim()
                            val categories = product.optString("categories").trim()

                            var nomeFinale = when {
                                nameIt.isNotBlank() -> nameIt
                                nameGenIt.isNotBlank() -> nameGenIt
                                nameGeneric.isNotBlank() -> nameGeneric
                                genericName.isNotBlank() -> genericName
                                brand.isNotBlank() -> "Prodotto $brand"
                                else -> "Prodotto $code"
                            }

                            // Aggiungi brand al nome se non presente
                            if (brand.isNotBlank() && !nomeFinale.contains(brand, ignoreCase = true)) {
                                nomeFinale = "$brand $nomeFinale"
                            }
                            if (quantity.isNotBlank() && !nomeFinale.contains(quantity, ignoreCase = true)) {
                                nomeFinale = "$nomeFinale $quantity"
                            }

                            val (categoria, posizione, giorni) = inferisciCategoriaEPosizione(nomeFinale, categories)

                            return AiProductResult(
                                nome = nomeFinale,
                                categoria = categoria,
                                posizione = posizione,
                                giorniScadenzaStimati = giorni,
                                note = "Riconosciuto da Open Food Facts",
                                isOfflinePending = false
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.w("AiService", "Tentativo Open Food Facts su $url: ${e.message}")
                }
            }
        }
        return null
    }

    private fun cercaPerNomeSuOpenFoodFacts(nome: String): AiProductResult? {
        try {
            val encoded = URLEncoder.encode(nome, "UTF-8")
            val url = "https://it.openfoodfacts.org/cgi/search.pl?search_terms=$encoded&search_simple=1&action=process&json=1&page_size=1"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "DispensaSmart-Android/1.0 (contact: info@dispensasmart.app)")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val products = json.optJSONArray("products")
                if (products != null && products.length() > 0) {
                    val prod = products.getJSONObject(0)
                    val prodName = prod.optString("product_name_it").ifBlank { prod.optString("product_name") }
                    val categories = prod.optString("categories")
                    if (prodName.isNotBlank()) {
                        val (categoria, posizione, giorni) = inferisciCategoriaEPosizione(prodName, categories)
                        return AiProductResult(
                            nome = prodName,
                            categoria = categoria,
                            posizione = posizione,
                            giorniScadenzaStimati = giorni,
                            note = "Riconosciuto online",
                            isOfflinePending = false
                        )
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun analizzaConGemini(trimmed: String, apiKey: String): AiProductResult? {
        try {
            val promptText = """
                Analizza il seguente prodotto alimentare o codice a barre: "$trimmed".
                Rispondi ESCLUSIVAMENTE con un JSON valido con i campi:
                {
                  "nome": "Nome del prodotto in italiano",
                  "categoria": "Una tra: Dispensa Secca, Frigo, Surgelati, Bevande, Igiene/Casa, Altro",
                  "posizione": "Una tra: Frigo, Dispensa, Freezer",
                  "giorniScadenzaStimati": numero intero di giorni stimati di conservazione,
                  "note": "Breve indicazione conservazione"
                }
            """.trimIndent()

            val jsonRequest = JSONObject().apply {
                put("contents", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", org.json.JSONArray().apply {
                            put(JSONObject().apply { put("text", promptText) })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                })
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
                .post(jsonRequest.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                val rootJson = JSONObject(responseBody)
                val candidates = rootJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val text = candidates.getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")

                    val res = JSONObject(text)
                    return AiProductResult(
                        nome = res.optString("nome", trimmed),
                        categoria = normalizzaCategoria(res.optString("categoria", "Dispensa Secca")),
                        posizione = normalizzaPosizione(res.optString("posizione", "Dispensa")),
                        giorniScadenzaStimati = res.optInt("giorniScadenzaStimati", 30),
                        note = res.optString("note", "Analizzato con IA Gemini"),
                        isOfflinePending = false
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("AiService", "Gemini non disponibile o errore: ${e.message}")
        }
        return null
    }

    fun inferisciCategoriaDaNomeEBarcode(nome: String, barcode: String): Pair<String, String> {
        val (cat, pos, _) = inferisciCategoriaEPosizione(nome, "")
        return Pair(cat, pos)
    }

    private fun inferisciCategoriaEPosizione(nome: String, categories: String): Triple<String, String, Int> {
        val full = "$nome $categories".lowercase()
        return when {
            // Frigo / Latticini / Freschi
            full.contains("frigo") || full.contains("latte") || full.contains("yogurt") ||
            full.contains("mozzarella") || full.contains("formaggi") || full.contains("cheese") ||
            full.contains("dairy") || full.contains("burro") || full.contains("panna") ||
            full.contains("ricotta") || full.contains("parmigiano") || full.contains("prosciutto") ||
            full.contains("salumi") || full.contains("uova") || full.contains("carne fresca") ||
            full.contains("affettat") || full.contains("pasta fresca") || full.contains("stracchino") -> {
                Triple("Frigo", "Frigo", 14)
            }
            // Surgelati / Freezer
            full.contains("surgelat") || full.contains("congelat") || full.contains("frozen") ||
            full.contains("freezer") || full.contains("gelat") || full.contains("ice cream") ||
            full.contains("bastoncini") || full.contains("piselli novelli") || full.contains("cubello") ||
            full.contains("pizza surgelata") || full.contains("ghiacciol") -> {
                Triple("Surgelati", "Freezer", 180)
            }
            // Bevande
            full.contains("bevand") || full.contains("beverage") || full.contains("acqua") ||
            full.contains("water") || full.contains("vino") || full.contains("wine") ||
            full.contains("birra") || full.contains("beer") || full.contains("succo") ||
            full.contains("juice") || full.contains("tè") || full.contains("the") ||
            full.contains("caffè") || full.contains("coffee") || full.contains("cola") ||
            full.contains("aranciata") || full.contains("bibita") || full.contains("aperitivo") -> {
                Triple("Bevande", "Dispensa", 365)
            }
            // Igiene e Casa
            full.contains("igiene") || full.contains("detersiv") || full.contains("detergent") ||
            full.contains("sapone") || full.contains("soap") || full.contains("shampoo") ||
            full.contains("bagnoschiuma") || full.contains("candeggina") || full.contains("pulizi") ||
            full.contains("casa") || full.contains("dentifricio") || full.contains("carta igienica") ||
            full.contains("spugna") || full.contains("sgrassatore") || full.contains("ammorbidente") -> {
                Triple("Igiene/Casa", "Dispensa", 730)
            }
            // Dispensa Secca
            else -> {
                Triple("Dispensa Secca", "Dispensa", 365)
            }
        }
    }

    private fun analizzaConParoleChiave(testo: String): AiProductResult? {
        val (cat, pos, giorni) = inferisciCategoriaEPosizione(testo, "")
        if (cat != "Dispensa Secca" || testo.contains("pasta", ignoreCase = true) ||
            testo.contains("riso", ignoreCase = true) || testo.contains("biscott", ignoreCase = true) ||
            testo.contains("farina", ignoreCase = true) || testo.contains("pomodoro", ignoreCase = true)) {
            val capitalized = testo.replaceFirstChar { it.uppercase() }
            return AiProductResult(
                nome = capitalized,
                categoria = cat,
                posizione = pos,
                giorniScadenzaStimati = giorni,
                note = "Catalogato automaticamente",
                isOfflinePending = false
            )
        }
        return null
    }

    private fun normalizzaCategoria(cat: String): String {
        return when {
            cat.contains("Frigo", ignoreCase = true) -> "Frigo"
            cat.contains("Surgelat", ignoreCase = true) -> "Surgelati"
            cat.contains("Bevand", ignoreCase = true) -> "Bevande"
            cat.contains("Igiene", ignoreCase = true) || cat.contains("Casa", ignoreCase = true) -> "Igiene/Casa"
            else -> "Dispensa Secca"
        }
    }

    private fun normalizzaPosizione(pos: String): String {
        return when {
            pos.contains("Frigo", ignoreCase = true) -> "Frigo"
            pos.contains("Freezer", ignoreCase = true) || pos.contains("Surgelat", ignoreCase = true) -> "Freezer"
            else -> "Dispensa"
        }
    }

    private fun stimaOfflineFallback(text: String): AiProductResult {
        val isBarcode = text.matches(Regex("^\\d+$"))
        val nomeLabel = if (isBarcode) "Prodotto $text" else text.replaceFirstChar { it.uppercase() }
        val (cat, pos, giorni) = inferisciCategoriaEPosizione(text, "")
        return AiProductResult(
            nome = nomeLabel,
            categoria = if (isBarcode) "Da Catalogare" else cat,
            posizione = pos,
            giorniScadenzaStimati = giorni,
            note = if (isBarcode) "Barcode scansionato (in attesa di catalogazione)" else "Inserimento manuale",
            isOfflinePending = isBarcode
        )
    }
}
