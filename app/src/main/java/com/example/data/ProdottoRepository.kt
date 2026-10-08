package com.example.data

import com.example.model.Prodotto
import kotlinx.coroutines.flow.Flow

class ProdottoRepository(private val dao: ProdottoDao) {
    val allProdotti: Flow<List<Prodotto>> = dao.getAllProdotti()
    val prodottiSpesa: Flow<List<Prodotto>> = dao.getProdottiListaSpesa()

    suspend fun getProdottoById(id: Long): Prodotto? = dao.getProdottoById(id)

    suspend fun getProdottoByBarcode(barcode: String): Prodotto? = dao.getProdottoByBarcode(barcode)

    suspend fun getProdottoByNameOrCategory(search: String): Prodotto? = dao.getProdottoByNameOrCategory(search)

    suspend fun getAllProdottiList(): List<Prodotto> = dao.getAllProdottiList()

    suspend fun insert(prodotto: Prodotto): Long = dao.insertProdotto(prodotto)

    suspend fun insertAll(prodotti: List<Prodotto>) = dao.insertAll(prodotti)

    suspend fun replaceAllProdotti(prodotti: List<Prodotto>) {
        dao.deleteAll()
        dao.insertAll(prodotti)
    }

    suspend fun update(prodotto: Prodotto) = dao.updateProdotto(prodotto)

    suspend fun delete(prodotto: Prodotto) = dao.deleteProdotto(prodotto)

    suspend fun deleteById(id: Long) = dao.deleteProdottoById(id)

    suspend fun updateQuantita(id: Long, nuovaQuantita: Int) {
        val prod = dao.getProdottoById(id)
        if (prod != null) {
            val quantitaEffettiva = if (nuovaQuantita < 0) 0 else nuovaQuantita
            // Se scende a 0, segna automaticamente in lista spesa
            val inLista = quantitaEffettiva == 0 || prod.inListaSpesa
            dao.updateProdotto(prod.copy(quantita = quantitaEffettiva, inListaSpesa = inLista))
        }
    }

    suspend fun toggleListaSpesa(id: Long, inLista: Boolean) {
        dao.updateInListaSpesa(id, inLista)
    }
}
