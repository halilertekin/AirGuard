package de.seemoo.at_tracking_detection.util.ble

import android.app.Service
import android.bluetooth.*
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGattCharacteristic.FORMAT_UINT8
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import timber.log.Timber
import java.util.*

class BluetoothLeService : Service() {
    private var bluetoothAdapter: BluetoothAdapter? = null

    private var bluetoothGatt: BluetoothGatt? = null

    private var connectionState = STATE_DISCONNECTED

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothLeService {
            return this@BluetoothLeService
        }
    }

    fun init(): Boolean {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        return true
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        bluetoothGatt?.let {
            it.close()
            bluetoothGatt = null
        }
        Timber.d("Unbinding BluetoothLeService")
        return super.onUnbind(intent)
    }

    fun connect(deviceAddress: String): Boolean {
        bluetoothAdapter?.let {
            return try {
                val device = it.getRemoteDevice(deviceAddress)
                bluetoothGatt = device.connectGatt(this, false, bluetoothGattCallback)
                true
            } catch (e: IllegalArgumentException) {
                Timber.e("Failed to connect to device!")
                false
            }
        } ?: run {
            Timber.w("Bluetooth adapter is not initialized!")
            return false
        }
    }

    private val bluetoothGattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (status) {
                GATT_SUCCESS -> {
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            connectionState = STATE_CONNECTED
                            Timber.d("Connected to gatt device!")
                            gatt.discoverServices()
                            broadcastUpdate(ACTION_GATT_CONNECTED)
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            connectionState = STATE_DISCONNECTED
                            broadcastUpdate(ACTION_GATT_DISCONNECTED)
                            Timber.d("Disconnected from gatt device!")
                        }
                        else -> {
                            Timber.d("Connection state changed to $connectionState")
                        }
                    }
                }
                19 -> {
                    broadcastUpdate(ACTION_EVENT_COMPLETED)
                }
                else -> {
                    Timber.e("Failed to connect to bluetooth device! Status: $status")
                    broadcastUpdate(ACTION_EVENT_FAILED)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(AIR_TAG_SOUND_SERVICE)
            if (service == null) {
                Timber.e("Airtag sound service not found!")
            } else {
                service.getCharacteristic(AIR_TAG_SOUND_CHARACTERISTIC)
                    .let {
                        it.setValue(175, FORMAT_UINT8, 0)
                        gatt.writeCharacteristic(it)
                        Timber.d("Playing sound...")
                    }
            }
            super.onServicesDiscovered(gatt, status)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic != null) {
                when (characteristic.properties and AIR_TAG_EVENT_CALLBACK) {
                    AIR_TAG_EVENT_CALLBACK -> broadcastUpdate(ACTION_EVENT_COMPLETED)
                }
            }
        }
    }

    private fun broadcastUpdate(action: String) =
        sendBroadcast(Intent(applicationContext, GattUpdateReceiver::class.java).apply {
            setAction(action)
        })

    companion object {
        const val ACTION_GATT_CONNECTED = "de.seemoo.at_tracking_detection.ACTION_GATT_CONNECTED"
        const val ACTION_GATT_DISCONNECTED =
            "de.seemoo.at_tracking_detection.ACTION_GATT_DISCONNECTED"
        const val ACTION_EVENT_COMPLETED = "de.seemoo.at_tracking_detection.EVENT_COMPLETED"
        const val ACTION_EVENT_FAILED = "de.seemoo.at_tracking_detection.EVENT_FAILED"
        private const val STATE_DISCONNECTED = 0
        private const val STATE_CONNECTED = 2
        private val AIR_TAG_SOUND_SERVICE = UUID.fromString("7DFC9000-7D1C-4951-86AA-8D9728F8D66C")
        private val AIR_TAG_SOUND_CHARACTERISTIC =
            UUID.fromString("7DFC9001-7D1C-4951-86AA-8D9728F8D66C")
        private const val AIR_TAG_EVENT_CALLBACK = 0x302
    }
}
