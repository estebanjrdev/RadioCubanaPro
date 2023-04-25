package com.ejrm.radiocubana.pro.data.model

import com.ejrm.radiocubana.pro.data.database.entities.StationsEntity

data class StationsModel(
    val link: String,
    val name: String,
    val description: String,
    val imagen: Int
)
fun StationsEntity.toModel() = StationsModel(link, name,description,imagen)
fun StationsModel.toEntity() = StationsEntity(link, name,description,imagen)