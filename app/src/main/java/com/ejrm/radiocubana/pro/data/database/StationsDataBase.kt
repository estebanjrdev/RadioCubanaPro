package com.ejrm.radiocubana.pro.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ejrm.radiocubana.pro.data.database.dao.StationsDao
import com.ejrm.radiocubana.pro.data.database.entities.StationsEntity

@Database(entities = [StationsEntity::class], version = 2)
abstract class StationsDataBase: RoomDatabase() {
    abstract fun getEmisoraDao(): StationsDao
}