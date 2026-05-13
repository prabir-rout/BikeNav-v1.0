package com.bikenv.app

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import java.util.Locale

class NavigationService : Service() {

    private val apiKey =
        "eyJvcmciOiI1YjNjZTM1OTc4NTExMTAwMDFjZjYyNDgiLCJpZCI6ImI3ZTUxNTExZDY5MDQ3MzliMjJjMDdlMmUwNDFmYjVhIiwiaCI6Im11cm11cjY0In0="

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var destinationText = ""
    private var destinationLat = 0.0
    private var destinationLon = 0.0

    private var currentStepIndex = 0
    private var routeSteps: List<Step> = emptyList()

    private val serviceScope = CoroutineScope(
        Dispatchers.IO + SupervisorJob()
    )

    override fun onCreate() {
        super.onCreate()

        fusedLocationClient =
            LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        destinationText =
            intent?.getStringExtra("destination") ?: ""

        if (destinationText.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        resolveDestination()

        return START_STICKY
    }

    private fun resolveDestination() {

        serviceScope.launch {

            try {
                val geocoder =
                    Geocoder(this@NavigationService, Locale.getDefault())

                val results =
                    geocoder.getFromLocationName(destinationText, 1)

                if (results.isNullOrEmpty()) {
                    Log.e("BikeNav", "Destination not found")
                    return@launch
                }

                destinationLat = results[0].latitude
                destinationLon = results[0].longitude

                startLocationUpdates()

            } catch (e: Exception) {
                Log.e("BikeNav", "Geocoder error", e)
            }
        }
    }

    private fun startLocationUpdates() {

        if (
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val request =
            LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                5000
            ).build()

        fusedLocationClient.requestLocationUpdates(
            request,
            locationCallback,
            mainLooper
        )
    }

    private val locationCallback =
        object : LocationCallback() {

            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return

                if (routeSteps.isEmpty()) {
                    fetchRoute(
                        location.latitude,
                        location.longitude
                    )
                } else {
                    checkProgress(location.latitude, location.longitude)
                }
            }
        }

    private fun fetchRoute(
        lat: Double,
        lon: Double
    ) {

        serviceScope.launch {

            try {
                val response =
                    ApiClient.orsApi.getRoute(
                        apiKey,
                        "$lon,$lat",
                        "$destinationLon,$destinationLat"
                    )

                if (!response.isSuccessful) {
                    Log.e("BikeNav", "API failed")
                    return@launch
                }

                val body = response.body() ?: return@launch

                routeSteps =
                    body.routes[0]
                        .segments[0]
                        .steps

                currentStepIndex = 0

                sendCurrentStep()

            } catch (e: Exception) {
                Log.e("BikeNav", "Route fetch error", e)
            }
        }
    }

    private fun sendCurrentStep() {

        if (currentStepIndex >= routeSteps.size) {
            sendPacket("ARRIVED|Destination reached")
            stopSelf()
            return
        }

        val step = routeSteps[currentStepIndex]

        val distance =
            "${step.distance.toInt()} m"

        val instruction =
            step.instruction

        val direction =
            parseDirection(instruction)

        val packet =
            "$direction|$distance $instruction"

        sendPacket(packet)
    }

    private fun parseDirection(text: String): String {

        val lower = text.lowercase()

        return when {
            "u-turn" in lower -> "UTURN"
            "left" in lower -> "LEFT"
            "right" in lower -> "RIGHT"
            "roundabout" in lower -> "ROUNDABOUT"
            "merge" in lower -> "MERGE"
            "continue" in lower -> "STRAIGHT"
            else -> "NAV"
        }
    }

    private fun checkProgress(
        lat: Double,
        lon: Double
    ) {

        if (currentStepIndex >= routeSteps.size) return

        val step = routeSteps[currentStepIndex]

        if (step.distance < 30) {
            currentStepIndex++
            sendCurrentStep()
        }
    }

    private fun sendPacket(text: String) {

        Log.d("BikeNav", "Sending: $text")

        LocalBroadcastManager.getInstance(this)
            .sendBroadcast(
                Intent("NAV_DATA")
                    .putExtra("text", text)
            )

        LocalBroadcastManager.getInstance(this)
            .sendBroadcast(
                Intent("NAV_INSTRUCTION")
                    .putExtra("text", text)
            )
    }

    override fun onDestroy() {
        super.onDestroy()

        fusedLocationClient.removeLocationUpdates(locationCallback)
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}