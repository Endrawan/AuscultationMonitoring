package com.endrawan.auscultationmonitoring.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.endrawan.auscultationmonitoring.databinding.ItemLinearAudioBinding
import com.endrawan.auscultationmonitoring.models.Audio

class AudioAdapter(private val audios: List<Audio>) :
    RecyclerView.Adapter<AudioAdapter.ViewHolder>(){

    class ViewHolder(private val binding: ItemLinearAudioBinding): RecyclerView.ViewHolder(binding.root) {
        fun bind(audio: Audio) {
            binding.textViewName.text = audio.name
            binding.textViewAdded.text = audio.date
            binding.textViewLength.text = audio.timeLength
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AudioAdapter.ViewHolder {
        val binding = ItemLinearAudioBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val audio = audios[position]
        holder.bind(audio)
    }

    override fun getItemCount(): Int = audios.size
}