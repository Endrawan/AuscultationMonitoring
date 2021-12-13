package com.endrawan.auscultationmonitoring

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import com.endrawan.auscultationmonitoring.adapters.AudioAdapter
import com.endrawan.auscultationmonitoring.configs.Dummy
import com.endrawan.auscultationmonitoring.databinding.ActivityListBinding
import com.endrawan.auscultationmonitoring.databinding.ActivityMainBinding

class ListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityListBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        binding = ActivityListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prepareToolbar()
        prepareRecyclerView()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            android.R.id.home -> onBackPressed()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun prepareToolbar() {
        setSupportActionBar(binding.toolbar)
        val actionBar = supportActionBar
        actionBar?.title = "Daftar suara"
        actionBar?.setDisplayShowHomeEnabled(true)
        actionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun prepareRecyclerView() {
        binding.recylerView.layoutManager = LinearLayoutManager(this)
        binding.recylerView.adapter = AudioAdapter(Dummy.audioDummies)
    }
}