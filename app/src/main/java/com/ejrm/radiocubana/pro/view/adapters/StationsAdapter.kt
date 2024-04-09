package com.ejrm.radiocubana.pro.view.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ejrm.radiocubana.pro.R
import com.ejrm.radiocubana.pro.data.model.StationsModel
import com.ejrm.radiocubana.pro.databinding.EmisoraCardBinding

class StationsAdapter(private val onClickListener: StationsAdapter.StationsAdapterListener) :
    RecyclerView.Adapter<StationsAdapter.ViewHolder>() {
    private var stationsList: List<StationsModel>? = null
    interface StationsAdapterListener{
        fun onEmisoraSelected(stations: StationsModel)
    }
    fun setStationsList(stationsList: List<StationsModel>) {
        this.stationsList = stationsList
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StationsAdapter.ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        return ViewHolder(layoutInflater.inflate(R.layout.emisora_card, parent, false))
    }

    override fun onBindViewHolder(holder: StationsAdapter.ViewHolder, position: Int) {
        holder.bind(stationsList!![position], onClickListener)
    }

    fun updateStations(stationsList: List<StationsModel>){
        this.stationsList = stationsList
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = stationsList?.size ?: 0

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val emisoraCardBinding = EmisoraCardBinding.bind(view)
        fun bind(emisor: StationsModel, onClickPayListener: StationsAdapter.StationsAdapterListener) {
            emisoraCardBinding.itemTitle.text = emisor.name
            emisoraCardBinding.itemDescrip.text = emisor.description
            emisoraCardBinding.itemLogo.setImageResource(emisor.imagen)
            itemView.setOnClickListener { onClickPayListener.onEmisoraSelected(emisor) }
        }
    }
}