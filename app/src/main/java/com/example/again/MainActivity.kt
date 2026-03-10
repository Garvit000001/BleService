package com.example.again

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.*
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.again.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val eventList = mutableListOf<String>()
    private lateinit var adapter: EventAdapter
    private lateinit var bleManager: BleAdvertiserManager
    private var lastClickedItem: String? = null

    private val requestEnableBluetooth = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.d("BLE_AD", "Bluetooth enabled by user. Starting advertising.")
            lastClickedItem?.let {
                processAndAdvertise(it)
                lastClickedItem = null 
            }
        } else {
            Log.e("BLE_AD", "Bluetooth not enabled by user.")
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val data = intent?.getStringExtra("data") ?: return
            Log.d("MAIN_ACTIVITY", "Received broadcast for app: $data")
            
            eventList.add(0, data)
            adapter.notifyItemInserted(0)
            binding.recyclerView.scrollToPosition(0)

            if (eventList.size > 200) {
                val lastPosition = eventList.size - 1
                eventList.removeAt(lastPosition)
                adapter.notifyItemRemoved(lastPosition)
            }

            // Automatically start advertising when a new app is detected from broadcast
            processAndAdvertise(data)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bleManager = BleAdvertiserManager(this)
        requestPermissions()

        adapter = EventAdapter(eventList) { clickedItem ->
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter?.isEnabled == true) {
                processAndAdvertise(clickedItem)
            } else {
                lastClickedItem = clickedItem 
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                requestEnableBluetooth.launch(enableBtIntent)
            }
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.btnStart.setOnClickListener {
            if (!isAccessibilityServiceEnabled()) {
                showAccessibilityServiceDialog()
            } else {
                try {
                    val serviceIntent = Intent(this, ForegroundService::class.java)
                    ContextCompat.startForegroundService(this, serviceIntent)
                    Toast.makeText(this, "Service Started", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("MAIN_ACTIVITY", "Error starting ForegroundService: ${e.message}")
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, IntentFilter("ACTION_USER_EVENT"), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, IntentFilter("ACTION_USER_EVENT"))
        }
    }

    private fun processAndAdvertise(appName: String) {
        Log.d("MAIN_ACTIVITY", "processAndAdvertise: $appName")
        runOnUiThread {
            Toast.makeText(this, "Advertising: $appName", Toast.LENGTH_SHORT).show()
        }
        bleManager.startAdvertising(appName)
    }

    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissions(permissionsToRequest.toTypedArray(), 1001)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "${packageName}/${MyAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        if (enabledServices != null) {
            colonSplitter.setString(enabledServices)
            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.equals(service, ignoreCase = true)) {
                    return true
                }
            }
        }
        return false
    }

    private fun showAccessibilityServiceDialog() {
        AlertDialog.Builder(this)
            .setTitle("Enable Accessibility Service")
            .setMessage("To track user actions, you need to enable the accessibility service for this app in the device settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {}
        // If we want it to keep advertising in background, don't stop here 
        // unless you move the manager to the ForegroundService.
        // For now, I'll keep it as is.
    }
}
