package com.ejrm.radiocubana.pro.data

import com.ejrm.radiocubana.pro.data.database.dao.StationsDao
import com.ejrm.radiocubana.pro.data.database.entities.StationsEntity
import com.ejrm.radiocubana.pro.data.model.StationsModel
import com.ejrm.radiocubana.pro.data.model.StationsProvider
import com.ejrm.radiocubana.pro.data.model.toEntity
import com.ejrm.radiocubana.pro.data.model.toModel
import javax.inject.Inject

class StationsRepository @Inject constructor(private val stationsProvider: StationsProvider, private val stationsDao: StationsDao) {
    suspend fun getFavoriteStationsFromDatabase(): List<StationsModel> {
        val response: List<StationsEntity> = stationsDao.getFavoriteStations()
        return response.map { it.toModel() }
    }
    suspend fun insertFavoriteStations(stationsModel: StationsModel){
        val stationsEntity: StationsEntity = stationsModel.toEntity()
        stationsDao.insertFavoriteStations(stationsEntity)
    }
    suspend fun deleteFavoriteStations(stationsModel: StationsModel){
        val stationsEntity: StationsEntity = stationsModel.toEntity()
        stationsDao.deleteFavoriteStations(stationsEntity.imagen)
    }
    suspend fun checkIfExistStation(stationsModel: StationsModel): Boolean{
        val response: StationsEntity = stationsDao.checkStation(stationsModel.imagen)
        if (response != null){
            return true
        } else return false
    }

    suspend fun getFavoriteStationsFromProvider(): List<StationsModel> {
        return stationsProvider.getEmisoras()
    }

    suspend fun searchStations(station: String): List<StationsModel>{
        return stationsProvider.getEmisoras()
            .filter { stations -> stations.name.contains(station,true) }
    }
}