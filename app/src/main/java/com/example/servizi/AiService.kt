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
import java.util.concurrent.TimeUnit

data class AiProductResult(
    val nome: String,
    val categoria: String, // Dispensa Secca, Frigo, Surgelati, Bevande, Igiene/Casa, Da Catalogare, Altro
    val posizione: String, // Frigo, Dispensa, Freezer
    val giorniScadenzaStimati: Int, // Number of days from today
    val note: String = "",
    val isOfflinePending: Boolean = false
)

class AiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // Local dictionary fallback for common barcodes & sample items
    private val barcodeDictionary = mapOf(
        "8001234567890" to AiProductResult("Pasta Spaghetti Barilla 500g", "Dispensa Secca", "Dispensa", 730, "Mantenere in luogo fresco e asciutto"),
        "8008800123456" to AiProductResult("Latte Parzialmente Scremato Granarolo 1L", "Frigo", "Frigo", 10, "Conservare in frigorifero dopo l'apertura"),
        "8000500000000" to AiProductResult("Passata di Pomodoro Mutti 700g", "Dispensa Secca", "Dispensa", 365, "Una volta aperto conservare in frigo per 3-4 giorni"),
        "8001120000000" to AiProductResult("Yogurt Naturale Milla 2x125g", "Frigo", "Frigo", 14, "Conservare a max +4°C"),
        "8000425000000" to AiProductResult("Mozzarella Santa Lucia 3x125g", "Frigo", "Frigo", 12, "Scadenza indicata sulla confezione"),
        "8000300000000" to AiProductResult("Piselli Novelli Surgelati Findus 450g", "Surgelati", "Freezer", 180, "Mantenere nel congelatore a -18°C"),
        "8001040000000" to AiProductResult("Acqua Naturale San Benedetto 1.5L", "Bevande", "Dispensa", 365, "Al riparo dalla luce e dal calore"),
        "8000123000000" to AiProductResult("Detersivo Piatti Svelto 1L", "Igiene/Casa", "Dispensa", 1095, "Tenere lontano dalla portata dei bambini")
    )

    suspend fun analizzaBarcodeONome(barcodeONome: String, forceNetwork: Boolean = false): AiProductResult = withContext(Dispatchers.IO) {
        val trimmed = barcodeONome.trim()
        
        // 1. Check direct local dictionary if barcode matches
        barcodeDictionary[trimmed]?.let {
            return@withContext it
        }

        // 2. Try Gemini AI API call
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }

        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val promptText = """
                    Analizza il seguente prodotto alimentare o codice a barre italiano: "$trimmed".
                    Restituisci ESATTAMENTE un oggetto JSON valido con i seguenti campi senza markdown:
                    {
                      "nome": "Nome leggibile del prodotto in italiano",
                      "categoria": "Una tra: Dispensa Secca, Frigo, Surgelati, Bevande, Igiene/Casa, Altro",
                      "posizione": "Una tra: Frigo, Dispensa, Freezer",
                      "giorniScadenzaStimati": numero intero di giorni di validità dalla data odierna (es. 10 per latte fresco, 30 per formaggio, 365 per scatole/pasta),
                      "note": "Breve nota su conservazione"
                    }
                """.trimIndent()

                val jsonRequest = JSONObject().apply {
                    put("contents", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", org.json.JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", promptText)
                                })
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("responseMimeType", "application/json")
                    })
                }

                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
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

                        val resultJson = JSONObject(text)
                        return@withContext AiProductResult(
                            nome = resultJson.optString("nome", "Prodotto $trimmed"),
                            categoria = normalizzaCategoria(resultJson.optString("categoria", "Dispensa Secca")),
                            posizione = normalizzaPosizione(resultJson.optString("posizione", "Dispensa")),
                            giorniScadenzaStimati = resultJson.optInt("giorniScadenzaStimati", 30),
                            note = resultJson.optString("note", "Auto-analizzato con IA"),
                            isOfflinePending = false
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("AiService", "Errore chiamata Gemini (Offline/No Network): ${e.message}")
            }
        }

        // 3. Fallback when network is unavailable or Gemini fails
        stimaOfflineFallback(trimmed)
    }

    private fun normalizzaCategoria(cat: String): String {
        return when {
            cat.contains("Frigo", ignoreCase = true) || cat.contains("Latte", ignoreCase = true) || cat.contains("Formaggi", ignoreCase = true) -> "Frigo"
            cat.contains("Surgelat", ignoreCase = true) || cat.contains("Freezer", ignoreCase = true) -> "Surgelati"
            cat.contains("Bevand", ignoreCase = true) || cat.contains("Acqua", ignoreCase = true) || cat.contains("Succh", ignoreCase = true) -> "Bevande"
            cat.contains("Igiene", ignoreCase = true) || cat.contains("Casa", ignoreCase = true) || cat.contains("Pulizi", ignoreCase = true) -> "Igiene/Casa"
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
        val nomeLabel = if (text.matches(Regex("\\d+"))) "Prodotto $text" else text.replaceFirstChar { it.uppercase() }
        return AiProductResult(
            nome = nomeLabel,
            categoria = "Da Catalogare",
            posizione = "Dispensa",
            giorniScadenzaStimati = 30,
            note = "Scansionato offline - In attesa di catalogazione IA",
            isOfflinePending = true
        )
    }
}
