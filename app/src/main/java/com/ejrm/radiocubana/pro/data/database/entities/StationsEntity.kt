package com.ejrm.radiocubana.pro.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stations_favorite_table")
data class StationsEntity(
    @ColumnInfo(name = "link")val link: String,
    @ColumnInfo(name = "name")val name: String,
    @ColumnInfo(name = "description")val description: String,
    @PrimaryKey
    @ColumnInfo(name = "imagen")val imagen: Int
)