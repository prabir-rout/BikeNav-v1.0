package com.bikenv.app

import android.app.*
import android.bluetooth.*
import android.content.*
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.util.UUID

class BleService : Service() {

    private var bluetoothGatt: BluetoothGatt? = null
    private var navCharacteristic: BluetoothGattCharacteristic? = null
    private var isConnected = false

    private val SERVICE_UUID =
        UUID.fromString("12345678-1234-1234-1234-123456789012")

    private val CHAR_UUID =
        UUID.fromString("87654321-4321-4321-4321-210987654321")

    private val ESP32_MAC = "A0:B7:65:49:8A:CE"

    private val CHANNEL_ID = "bikenav_channel"
    private val NOTIF_ID = 1

    // ─────────────────────────────
    private val navReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val text = intent.getStringExtra("text") ?: return

            sendToEsp32(text)

            LocalBroadcastManager.getInstance(context)
                .sendBroadcast(
                    Intent("NAV_INSTRUCTION")
                        .putExtra("text", text)
                )
        }
    }

    // ─────────────────────────────
    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true

                Log.d("BikeNav", "Connected to ESP32")

                // REQUEST BIGGER MTU
                gatt.requestMtu(247)

                broadcast("BLE_CONNECTED")

            } else {
                isConnected = false

                Log.d("BikeNav", "Disconnected from ESP32")

                broadcast("BLE_DISCONNECTED")

                Handler(Looper.getMainLooper()).postDelayed({
                    connectToDevice()
                }, 3000)
            }
        }

        override fun onMtuChanged(
            gatt: BluetoothGatt,
            mtu: Int,
            status: Int
        ) {

            Log.d("BikeNav", "MTU changed: $mtu")

            gatt.discoverServices()
        }

        override fun onServicesDiscovered(
            gatt: BluetoothGatt,
            status: Int
        ) {

            Log.d("BikeNav", "Looking for correct service...")

            val service = gatt.getService(SERVICE_UUID)

            if (service == null) {
                Log.e("BikeNav", "❌ Custom service NOT found")
                updateNotification("Service not found ❌")
                return
            }

            val characteristic =
                service.getCharacteristic(CHAR_UUID)

            if (characteristic == null) {
                Log.e("BikeNav", "❌ Custom characteristic NOT found")
                updateNotification("Characteristic not found ❌")
                return
            }

            navCharacteristic = characteristic

            Log.d("BikeNav", "✅ Correct characteristic connected!")
            updateNotification("Ready to send data")
        }
    }

    // ─────────────────────────────
    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        startForeground(
            NOTIF_ID,
            buildNotification("Connecting to ESP32...")
        )

        LocalBroadcastManager.getInstance(this)
            .registerReceiver(
                navReceiver,
                IntentFilter("NAV_DATA")
            )

        connectToDevice()
    }

    override fun onDestroy() {
        super.onDestroy()

        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(navReceiver)

        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ─────────────────────────────
    private fun connectToDevice() {

        val bm =
            getSystemService(BLUETOOTH_SERVICE)
                    as BluetoothManager

        val device =
            bm.adapter.getRemoteDevice(ESP32_MAC)

        Log.d("BikeNav", "Connecting to ${device.address}")

        bluetoothGatt =
            device.connectGatt(
                this,
                false,
                gattCallback
            )
    }

    // ─────────────────────────────
    fun sendToEsp32(text: String) {

        val char = navCharacteristic ?: run {
            Log.e("BikeNav", "❌ Characteristic NULL")
            return
        }

        if (!isConnected) {
            Log.e("BikeNav", "❌ Not connected")
            return
        }

        char.value = text.toByteArray()

        char.writeType =
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

        val success =
            bluetoothGatt?.writeCharacteristic(char)

        Log.d("BikeNav", "📤 Sent: $text | Success: $success")

        updateNotification("Sent: $text")
    }

    // ─────────────────────────────
    private fun broadcast(action: String) {

        LocalBroadcastManager.getInstance(this)
            .sendBroadcast(Intent(action))

        if (action == "BLE_CONNECTED") {
            updateNotification("ESP32 connected!")
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "BikeNav Service",
            NotificationManager.IMPORTANCE_LOW
        )

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {

        val intent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setContentTitle("BikeNav")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentIntent(intent)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm =
            getSystemService(NotificationManager::class.java)

        nm?.notify(
            NOTIF_ID,
            buildNotification(text)
        )
    }
}