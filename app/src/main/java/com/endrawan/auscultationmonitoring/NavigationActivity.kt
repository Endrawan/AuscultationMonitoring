package com.endrawan.auscultationmonitoring

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import com.endrawan.auscultationmonitoring.databinding.ActivityMainBluetoothBinding
import com.endrawan.auscultationmonitoring.databinding.ActivityNavigationBinding

class NavigationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNavigationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        binding = ActivityNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonBluetooth.setOnClickListener {
            startActivity(Intent(this@NavigationActivity, MainBluetoothActivity::class.java))
        }

        binding.buttonUsbSerial.setOnClickListener {
            startActivity(Intent(this@NavigationActivity, MainActivity::class.java))
        }
    }
}