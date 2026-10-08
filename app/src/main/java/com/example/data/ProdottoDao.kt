package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.model.Prodotto
import kotlinx.coroutines.flow.Flow

@Dao
interface ProdottoDao {
    @Query("SELECT * FROM prodotti ORDER BY dataScadenza ASC")
    fun getAllProdotti(): Flow<List<Prodotto>>

    @Query("SELECT * FROM prodotti WHERE inListaSpesa = 1 ORDER BY comprato ASC, nome ASC")
    fun getProdottiListaSpesa(): Flow<List<Prodotto>>

    @Query("SELECT * FROM prodotti WHERE id = :id LIMIT 1")
    suspend fun getProdottoById(id: Long): Prodotto?

    @Query("SELECT * FROM prodotti WHERE barcode LIKE '%' || :barcode || '%' LIMIT 1")
    suspend fun getProdottoByBarcode(barcode: String): Prodotto?

    @Query("SELECT * FROM prodotti WHERE LOWER(nome) LIKE '%' || LOWER(:search) || '%' OR LOWER(categoria) LIKE '%' || LOWER(:search) || '%' LIMIT 1")
    suspend fun getProdottoByNameOrCategory(search: String): Prodotto?

    @Query("SELECT * FROM prodotti")
    suspend fun getAllProdottiList(): List<Prodotto>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProdotto(prodotto: Prodotto): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(prodotti: List<Prodotto>)

    @Query("DELETE FROM prodotti")
    suspend fun deleteAll()

    @Update
    suspend fun updateProdotto(prodotto: Prodotto)

    @Delete
    suspend fun deleteProdotto(prodotto: Prodotto)

    @Query("DELETE FROM prodotti WHERE id = :id")
    suspend fun deleteProdottoById(id: Long)

    @Query("UPDATE prodotti SET quantita = :nuovaQuantita WHERE id = :id")
    suspend fun updateQuantita(id: Long, nuovaQuantita: Int)

    @Query("UPDATE prodotti SET inListaSpesa = :inLista WHERE id = :id")
    suspend fun updateInListaSpesa(id: Long, inLista: Boolean)
}
