package com.endrawan.auscultationmonitoring

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.endrawan.auscultationmonitoring.databinding.ActivityBluetoothBinding
import java.io.IOException
import java.util.*
import java.util.regex.Pattern

class BluetoothActivity : AppCompatActivity() {

    private val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

    private lateinit var binding: ActivityBluetoothBinding
    private val REQUEST_ENABLE_BT = 100
    private val TAG = "BluetoothActivity"

    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var granted = true
            permissions.entries.forEach {
                Log.d(TAG, "${it.key} = ${it.value}")
                granted = granted and it.value
            }

            if (granted) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                requestBluetooth.launch(enableBtIntent)
            } else {
                Toast.makeText(
                    this@BluetoothActivity,
                    "Permission not granted!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val requestBluetooth =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                Log.d(TAG, "Bluetooth activated")
            } else {
                Toast.makeText(
                    this@BluetoothActivity,
                    "You need to turned on your bluetooth to use this app!",
                    Toast.LENGTH_SHORT
                ).show()
                Log.d(TAG, "Bluetooth not activated")
            }
        }

    @SuppressLint("MissingPermission")
    private val companionDeviceLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val deviceToPair: BluetoothDevice? =
                    result.data?.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
//                deviceToPair?.let { device ->
//                    device.createBond()
//                    // Continue to interact with the paired device.
//                }
                Toast.makeText(this@BluetoothActivity, "Device: ${deviceToPair?.name}", Toast.LENGTH_SHORT).show()
                deviceToPair?.createBond()
                connectToESP32Bluetooth(deviceToPair!!)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBluetoothBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        binding.buttonBluetoothPermission.setOnClickListener {
            val bluetoothAdapter: BluetoothAdapter? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val bluetoothManager: BluetoothManager =
                        getSystemService(BluetoothManager::class.java)
                    bluetoothManager.adapter
                } else {
                    // TODO handler bluetooth legacy
                    null
                }

            if (bluetoothAdapter == null) {
                Toast.makeText(
                    this@BluetoothActivity,
                    "Your device doesn't support Bluetooth",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (!bluetoothAdapter.isEnabled) {
                handleEnableBluetooth()
            } else {
                Toast.makeText(
                    this@BluetoothActivity,
                    "Bluetooth already enabled!",
                    Toast.LENGTH_SHORT
                ).show()
            }

        }

        binding.buttonBluetoothConnect.setOnClickListener {
            connectToBluetoothDevice()
        }
    }

    private fun handleEnableBluetooth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestMultiplePermissions.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            )
        } else {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            requestBluetooth.launch(enableBtIntent)
        }
    }

    private fun connectToBluetoothDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val deviceFilter: BluetoothDeviceFilter = BluetoothDeviceFilter.Builder()
                .setNamePattern(Pattern.compile("ESP32"))
                .build()

            val pairingRequest: AssociationRequest = AssociationRequest.Builder()
                .addDeviceFilter(deviceFilter)
                .build()

            val deviceManager = getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager

            deviceManager.associate(pairingRequest,
                object : CompanionDeviceManager.Callback() {
                    // Called when a device is found. Launch the IntentSender so the user
                    // can select the device they want to pair with.
                    override fun onDeviceFound(chooserLauncher: IntentSender) {
                        val intentSenderRequest = IntentSenderRequest.Builder(chooserLauncher).build()
                        companionDeviceLauncher.launch(intentSenderRequest)
                    }

                    override fun onFailure(error: CharSequence?) {
                        Toast.makeText(this@BluetoothActivity, "Error: $error", Toast.LENGTH_SHORT).show()
                    }
                }, null)


        } else {
            // TODO Add handler to bluetooth legacy
        }
    }

    @SuppressLint("MissingPermission")
    private inner class ConnectThread(device: BluetoothDevice) : Thread() {

        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            device.createRfcommSocketToServiceRecord(MY_UUID)
        }

        override fun run() {
            // Cancel discovery because it otherwise slows down the connection.
            val bluetoothManager: BluetoothManager? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    getSystemService(BluetoothManager::class.java)
                } else {
                    TODO("VERSION.SDK_INT < M")
                    null
                }
            bluetoothManager?.adapter?.cancelDiscovery()

            mmSocket?.let { socket ->
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                socket.connect()

                // The connection attempt succeeded. Perform work associated with
                // the connection in a separate thread.
                toast("Connection succeed")
//                manageMyConnectedSocket(socket)
            }
        }

        // Closes the client socket and causes the thread to finish.
        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the client socket", e)
            }
        }
    }

    private fun connectToESP32Bluetooth(device: BluetoothDevice) {
        val handler = Handler(Looper.getMainLooper())
        handler.post(ConnectThread(device))
    }


    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}