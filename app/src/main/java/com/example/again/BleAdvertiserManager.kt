package com.example.again

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.util.Log
import java.util.*
import kotlin.text.Charsets

class BleAdvertiserManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private val advertiser: BluetoothLeAdvertiser? = adapter?.bluetoothLeAdvertiser
    private var gattServer: BluetoothGattServer? = null
    private var appNameCharacteristic: BluetoothGattCharacteristic? = null
    
    private var currentAdvertisingSet: AdvertisingSet? = null
    private var lastAppName: String? = null
    private val connectedDevices = mutableSetOf<BluetoothDevice>()

    // Custom Service and Characteristic UUIDs
    private val SERVICE_UUID = UUID.fromString("0000FFF0-0000-1000-8000-00805f9b34fb")
    private val CHAR_UUID = UUID.fromString("0000FFF1-0000-1000-8000-00805f9b34fb")
    private val CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d("BLE_AD", "Legacy advertising started successfully.")
        }
        override fun onStartFailure(errorCode: Int) {
            Log.e("BLE_AD", "Legacy advertising failed: $errorCode")
        }
    }

    private val advertisingSetCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        object : AdvertisingSetCallback() {
            override fun onAdvertisingSetStarted(advertisingSet: AdvertisingSet?, txPower: Int, status: Int) {
                if (status == AdvertisingSetCallback.ADVERTISE_SUCCESS) {
                    Log.d("BLE_AD", "Advertising set started successfully.")
                    currentAdvertisingSet = advertisingSet
                } else {
                    Log.e("BLE_AD", "Advertising set failed to start: $status")
                }
            }

            override fun onAdvertisingDataSet(advertisingSet: AdvertisingSet?, status: Int) {
                Log.d("BLE_AD", "Advertising data updated. Status: $status")
            }
        }
    } else null

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("BLE_GATT", "Device connected: ${device?.address}")
                device?.let { connectedDevices.add(it) }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("BLE_GATT", "Device disconnected: ${device?.address}")
                device?.let { connectedDevices.remove(it) }
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            if (characteristic == null) return
            Log.d("BLE_GATT", "Read request for: ${characteristic.uuid} at offset $offset")
            
            if (characteristic.uuid == CHAR_UUID) {
                // Ensure we have a value to send
                val fullValue = appNameCharacteristic?.value ?: "EMPTY".toByteArray()
                
                // Chunk the response for the requested offset
                val responseValue = if (offset < fullValue.size) {
                    fullValue.copyOfRange(offset, fullValue.size)
                } else {
                    byteArrayOf()
                }
                
                val sent = gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, responseValue) ?: false
                Log.d("BLE_GATT", "Sent Read Response: $sent, size: ${responseValue.size}")
            } else {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
            }
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor?
        ) {
            Log.d("BLE_GATT", "Descriptor Read request: ${descriptor?.uuid}")
            if (descriptor?.uuid == CCC_DESCRIPTOR_UUID) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, byteArrayOf(0, 0))
            } else {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, byteArrayOf())
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            descriptor: BluetoothGattDescriptor?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }
    }

    private fun setupGattServer() {
        if (gattServer != null) return
        try {
            gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
            val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            
            appNameCharacteristic = BluetoothGattCharacteristic(
                CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
            
            // Add Client Characteristic Configuration Descriptor (required for Notify)
            val cccDescriptor = BluetoothGattDescriptor(
                CCC_DESCRIPTOR_UUID,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
            )
            appNameCharacteristic?.addDescriptor(cccDescriptor)
            
            service.addCharacteristic(appNameCharacteristic)
            gattServer?.addService(service)
            Log.d("BLE_GATT", "GATT Server setup complete with Custom Service and CCCD.")
        } catch (e: Exception) {
            Log.e("BLE_GATT", "Failed to setup GATT Server: ${e.message}")
        }
    }

    private fun createPayload(appName: String): ByteArray {
        val upper = appName.uppercase()
        val bytes = upper.toByteArray(Charsets.UTF_8)
        val trimmed = if (bytes.size > 18) bytes.copyOf(18) else bytes
        val payload = ByteArray(trimmed.size + 1)
        payload[0] = 0x76.toByte() 
        System.arraycopy(trimmed, 0, payload, 1, trimmed.size)
        return payload
    }

    fun startAdvertising(appName: String) {
        if (adapter == null || advertiser == null || !adapter.isEnabled) {
            Log.e("BLE_AD", "Bluetooth not ready.")
            return
        }
        
        setupGattServer()
        val payload = createPayload(appName)
        
        // Always update the characteristic value
        appNameCharacteristic?.value = payload
        
        // Notify connected devices
        for (device in connectedDevices) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gattServer?.notifyCharacteristicChanged(device, appNameCharacteristic!!, false, payload)
                } else {
                    @Suppress("DEPRECATION")
                    gattServer?.notifyCharacteristicChanged(device, appNameCharacteristic!!, false)
                }
            } catch (e: Exception) {
                Log.e("BLE_GATT", "Error notifying: ${e.message}")
            }
        }

        if (appName == lastAppName && (currentAdvertisingSet != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O)) {
            Log.d("BLE_AD", "App name same, skipping advertisement update.")
            return
        }
        
        lastAppName = appName
        
        val data = AdvertiseData.Builder()
            .addManufacturerData(0x0076, payload)
            .setIncludeDeviceName(false)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && advertisingSetCallback != null) {
            val parameters = AdvertisingSetParameters.Builder()
                .setLegacyMode(true)
                .setConnectable(true)
                .setScannable(true)
                .setInterval(AdvertisingSetParameters.INTERVAL_LOW)
                .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)
                .build()

            if (currentAdvertisingSet == null) {
                advertiser.startAdvertisingSet(parameters, data, null, null, null, advertisingSetCallback)
            } else {
                currentAdvertisingSet?.setAdvertisingData(data)
            }
        } else {
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .build()

            try { advertiser.stopAdvertising(advertiseCallback) } catch (e: Exception) {}
            advertiser.startAdvertising(settings, data, advertiseCallback)
        }
    }

    fun stopAdvertising() {
        lastAppName = null
        connectedDevices.clear()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && currentAdvertisingSet != null && advertisingSetCallback != null) {
                advertiser?.stopAdvertisingSet(advertisingSetCallback)
                currentAdvertisingSet = null
            } else {
                advertiser?.stopAdvertising(advertiseCallback)
            }
            gattServer?.close()
            gattServer = null
            appNameCharacteristic = null
        } catch (e: Exception) {
            Log.e("BLE_AD", "Error stopping: ${e.message}")
        }
    }
}
