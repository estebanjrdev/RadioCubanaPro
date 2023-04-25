package com.ejrm.radiocubana.pro.di

import android.content.Context
import androidx.room.Room
import com.ejrm.radiocubana.pro.data.database.StationsDataBase
import com.ejrm.radiocubana.pro.util.Constants.STATIONS_DATABASE
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RoomModule {
    @Singleton
    @Provides
    fun provideRoom(@ApplicationContext context: Context) =
        Room.databaseBuilder(context, StationsDataBase::class.java,STATIONS_DATABASE).build()

    @Singleton
    @Provides
    fun provideEmisoraDao(dataBase: StationsDataBase) = dataBase.getEmisoraDao()
}