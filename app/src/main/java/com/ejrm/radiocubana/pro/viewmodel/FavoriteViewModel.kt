package com.ejrm.radiocubana.pro.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejrm.radiocubana.pro.data.StationsRepository
import com.ejrm.radiocubana.pro.data.model.StationsModel
import com.ejrm.radiocubana.pro.util.ToastHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoriteViewModel @Inject constructor(private val toastHelper: ToastHelper, private val stationsRepository: StationsRepository) : ViewModel()  {
    var liveDataList: MutableLiveData<List<StationsModel>>
    var liveDataStation: MutableLiveData<Boolean>
    init {
        liveDataList = MutableLiveData()
        liveDataStation = MutableLiveData()
        stationsFavorite()
    }

    fun getLiveDataObserver(): MutableLiveData<List<StationsModel>> {
        return liveDataList
    }

    @JvmName("getLiveDataStation1")
    fun getLiveDataStation(): MutableLiveData<Boolean> {
        return liveDataStation
    }

    fun stationsFavorite(){
        viewModelScope.launch {
            val stations = stationsRepository.getFavoriteStationsFromDatabase()
            liveDataList.postValue(stations)
        }
    }

    fun addFavorite(stationsModel: StationsModel){
        viewModelScope.launch {
            stationsRepository.insertFavoriteStations(stationsModel)
            toastHelper.sendToast("Emisora agregada a favoritos")
        }
    }

    fun deleteFavorite(stationsModel: StationsModel){
        viewModelScope.launch {
            stationsRepository.deleteFavoriteStations(stationsModel)
            toastHelper.sendToast("Emisora eliminada de favoritos")
        }
    }

    fun checkStation(stationsModel: StationsModel){
        viewModelScope.launch {
            val stations = stationsRepository.checkIfExistStation(stationsModel)
            liveDataStation.postValue(stations)
        }
    }
}