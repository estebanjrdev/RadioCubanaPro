package com.ejrm.radiocubana.pro.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ejrm.radiocubana.pro.data.database.entities.StationsEntity

@Dao
interface StationsDao {
    @Query("Select * FROM stations_favorite_table")
    suspend fun getFavoriteStations(): List<StationsEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun  insertFavoriteStations(stationsEntity: StationsEntity)

    @Query("DELETE FROM stations_favorite_table WHERE imagen = :imagen")
    suspend fun  deleteFavoriteStations(imagen: Int)

    @Query("SELECT * FROM stations_favorite_table WHERE imagen = :imagen")
    suspend fun  checkStation(imagen: Int): StationsEntity
}