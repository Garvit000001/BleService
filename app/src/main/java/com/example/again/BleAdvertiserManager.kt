package com.example.again

import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.util.Log
import java.nio.charset.Charset
import java.util.*

class BleAdvertiserManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private val advertiser: BluetoothLeAdvertiser? = adapter?.bluetoothLeAdvertiser
    private var gattServer: BluetoothGattServer? = null

    // Using custom UUIDs is best practice to avoid confusion with standard services.
    private val serviceUuid: UUID = UUID.fromString("a8a8180f-7f77-4b4f-8086-9a2f5f72a1da") // Custom User Event Service
    private val characteristicUuid: UUID = UUID.fromString("a8a82a19-7f77-4b4f-8086-9a2f5f72a1da") // Custom User Event Characteristic

    // Storing both the short action for the advertisement and the full data for the GATT characteristic
    @Volatile private var lastAction: String? = null
    @Volatile private var lastEventData: ByteArray? = null

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d("BLE_AD", "Advertising started successfully.")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e("BLE_AD", "Advertising failed: $errorCode")
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            val state = if (newState == BluetoothProfile.STATE_CONNECTED) "Connected" else "Disconnected"
            Log.d("GATT_SERVER", "Device ${device?.address} $state")
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            super.onServiceAdded(status, service)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("GATT_SERVER", "Service added. Starting advertising.")
                val settings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(true)
                    .build()

                // --- Build Advertisement Data ---
                val manufacturerData = lastAction?.toByteArray(Charset.defaultCharset()) ?: ByteArray(0)

                val data = AdvertiseData.Builder()
                    .addServiceUuid(android.os.ParcelUuid(serviceUuid)) // Still advertise the service for connection
                    .addManufacturerData(0xABCD, manufacturerData) // Add the action (e.g., "CLICK") for visibility
                    .setIncludeDeviceName(false)
                    .build()

                advertiser?.startAdvertising(settings, data, advertiseCallback)
            } else {
                Log.e("GATT_SERVER", "Failed to add service: $status")
            }
        }

        override fun onCharacteristicReadRequest(device: BluetoothDevice?, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic?) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
            if (characteristic?.uuid == characteristicUuid) {
                Log.d("GATT_SERVER", "Read request for custom characteristic. Offset: $offset")
                val value = lastEventData ?: ByteArray(0)
                val dataToSend = if (offset < value.size) {
                    value.copyOfRange(offset, value.size)
                } else {
                    ByteArray(0)
                }
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, dataToSend)
            }
        }
    }

    fun startAdvertising(rawData: String) {
        if (adapter == null || advertiser == null || !adapter.isEnabled) {
            Log.e("BLE_AD", "BLE is not available or not enabled.")
            return
        }

        stopAdvertising()

        // Store the full data for the GATT characteristic read
        lastEventData = rawData.toByteArray(Charset.defaultCharset())
        // Store just the action for the advertisement packet
        lastAction = rawData.split(" | ").firstOrNull()

        Log.d("GATT_SERVER", "Opening GATT server...")
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)

        val service = BluetoothGattService(serviceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            characteristicUuid,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        service.addCharacteristic(characteristic)

        gattServer?.addService(service)
    }

    fun stopAdvertising() {
        advertiser?.stopAdvertising(advertiseCallback)
        gattServer?.close()
        gattServer = null
        Log.d("BLE_AD", "Advertising and GATT server stopped.")
    }
}