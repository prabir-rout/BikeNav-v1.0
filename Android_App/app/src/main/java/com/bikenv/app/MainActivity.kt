package com.bikenv.app

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlin.math.*

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private lateinit var etDestination: EditText
    private lateinit var tvBleStatus: TextView
    private lateinit var tvGpsStatus: TextView
    private lateinit var tvEta: TextView
    private lateinit var tvLastInstruction: TextView
    private lateinit var tvOledDetail: TextView
    private lateinit var btnStartNav: Button

    private val client = OkHttpClient()

    private var currentLocation: LatLng? = null
    private var destinationLocation: LatLng? = null

    private val routePolylines = mutableListOf<Polyline>()
    private val routeJsonRoutes = mutableListOf<JSONObject>()

    private var selectedRouteIndex = 0
    private var navigationActive = false
    private var currentStepIndex = 0

    private var currentSteps: JSONArray? = null
    private var currentRoutePath: List<LatLng> = emptyList()
    private var lastRerouteTime = 0L

    companion object {
        private const val AUTOCOMPLETE_REQUEST_CODE = 1001
        private const val GOOGLE_API_KEY = "YOUR_API_KEY" //put your Maps API key here

        private const val ARRIVAL_THRESHOLD_METERS = 25.0
        private const val STEP_REACHED_THRESHOLD_METERS = 30.0
        private const val OFF_ROUTE_THRESHOLD_METERS = 100.0
    }

    private val bleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "BLE_CONNECTED" -> {
                    tvBleStatus.text = "ESP32: Connected"
                    tvBleStatus.setTextColor(Color.GREEN)
                }

                "BLE_DISCONNECTED" -> {
                    tvBleStatus.text = "ESP32: Disconnected"
                    tvBleStatus.setTextColor(Color.RED)
                }

                "NAV_INSTRUCTION" -> {
                    val text = intent.getStringExtra("text") ?: return
                    updateOledPreview(text)
                }
            }
        }
    }

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val granted =
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true

            if (granted) {
                enableLocation()
            } else {
                Toast.makeText(
                    this,
                    "Location permission required",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etDestination = findViewById(R.id.etDestination)
        tvBleStatus = findViewById(R.id.tvBleStatus)
        tvGpsStatus = findViewById(R.id.tvGpsStatus)
        tvEta = findViewById(R.id.tvEta)
        tvLastInstruction = findViewById(R.id.tvLastInstruction)
        tvOledDetail = findViewById(R.id.tvOledDetail)
        btnStartNav = findViewById(R.id.btnStartNav)

        fusedLocationClient =
            LocationServices.getFusedLocationProviderClient(this)

        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, GOOGLE_API_KEY)
        }

        val mapFragment =
            supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment

        mapFragment.getMapAsync(this)

        requestPermissions()

        etDestination.setOnClickListener {
            openAutocomplete()
        }

        btnStartNav.setOnClickListener {
            if (!navigationActive) {
                startNavigation()
            } else {
                stopNavigation()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, BleService::class.java))
        } else {
            startService(Intent(this, BleService::class.java))
        }

        setupLocationTracking()
    }

    override fun onResume() {
        super.onResume()

        val filter = IntentFilter().apply {
            addAction("BLE_CONNECTED")
            addAction("BLE_DISCONNECTED")
            addAction("NAV_INSTRUCTION")
        }

        LocalBroadcastManager.getInstance(this)
            .registerReceiver(bleReceiver, filter)

        if (navigationActive) {
            startLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()

        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(bleReceiver)

        stopLocationUpdates()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        googleMap.uiSettings.isZoomControlsEnabled = true
        googleMap.uiSettings.isMyLocationButtonEnabled = true

        if (
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            enableLocation()
        }

        googleMap.setOnMapClickListener { latLng ->
            destinationLocation = latLng

            googleMap.clear()

            googleMap.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("Destination")
            )

            currentLocation?.let {
                fetchRoutes(it, latLng)
            }
        }

        googleMap.setOnPolylineClickListener { polyline ->
            selectedRouteIndex = polyline.tag as Int
            highlightSelectedRoute()
            prepareSelectedRoute()
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }

        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun enableLocation() {

        if (
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        googleMap.isMyLocationEnabled = true

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                location?.let {
                    currentLocation =
                        LatLng(it.latitude, it.longitude)

                    googleMap.moveCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            currentLocation!!,
                            15f
                        )
                    )

                    tvGpsStatus.text = "Navigation: GPS Ready"
                }
            }
    }

    private fun openAutocomplete() {
        val fields = listOf(
            Place.Field.NAME,
            Place.Field.LAT_LNG
        )

        val intent =
            Autocomplete.IntentBuilder(
                AutocompleteActivityMode.OVERLAY,
                fields
            ).build(this)

        startActivityForResult(intent, AUTOCOMPLETE_REQUEST_CODE)
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (
            requestCode == AUTOCOMPLETE_REQUEST_CODE &&
            resultCode == RESULT_OK &&
            data != null
        ) {
            val place = Autocomplete.getPlaceFromIntent(data)

            etDestination.setText(place.name)

            destinationLocation = place.latLng

            destinationLocation?.let { dest ->
                googleMap.clear()

                googleMap.addMarker(
                    MarkerOptions()
                        .position(dest)
                        .title(place.name)
                )

                currentLocation?.let {
                    fetchRoutes(it, dest)
                }
            }
        }
    }

    private fun fetchRoutes(origin: LatLng, destination: LatLng) {

        val url =
            "https://maps.googleapis.com/maps/api/directions/json?" +
                    "origin=${origin.latitude},${origin.longitude}" +
                    "&destination=${destination.latitude},${destination.longitude}" +
                    "&alternatives=true" +
                    "&mode=driving" +
                    "&key=$GOOGLE_API_KEY"

        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request)
            .enqueue(object : Callback {

                override fun onFailure(call: Call, e: IOException) {
                    Log.e("BikeNav", "Directions API failed", e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val body = response.body?.string() ?: return
                    val json = JSONObject(body)

                    runOnUiThread {
                        drawRoutes(json)
                    }
                }
            })
    }

    private fun drawRoutes(json: JSONObject) {

        routePolylines.clear()
        routeJsonRoutes.clear()
        googleMap.clear()

        destinationLocation?.let {
            googleMap.addMarker(
                MarkerOptions()
                    .position(it)
                    .title("Destination")
            )
        }

        val routes = json.getJSONArray("routes")

        if (routes.length() == 0) {
            Toast.makeText(
                this,
                "No routes found",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        for (i in 0 until routes.length()) {

            val route = routes.getJSONObject(i)
            routeJsonRoutes.add(route)

            val encoded =
                route.getJSONObject("overview_polyline")
                    .getString("points")

            val decoded = decodePolyline(encoded)

            val polyline =
                googleMap.addPolyline(
                    PolylineOptions()
                        .addAll(decoded)
                        .width(14f)
                        .color(
                            if (i == 0)
                                Color.BLUE
                            else
                                Color.GRAY
                        )
                        .clickable(true)
                )

            polyline.tag = i
            routePolylines.add(polyline)
        }

        selectedRouteIndex = 0
        highlightSelectedRoute()
        prepareSelectedRoute()

        val firstLeg =
            routes.getJSONObject(0)
                .getJSONArray("legs")
                .getJSONObject(0)

        val eta =
            firstLeg.getJSONObject("duration")
                .getString("text")

        tvEta.text = "ETA: $eta"

        val bounds = LatLngBounds.builder()
        currentLocation?.let { bounds.include(it) }
        destinationLocation?.let { bounds.include(it) }

        googleMap.animateCamera(
            CameraUpdateFactory.newLatLngBounds(
                bounds.build(),
                120
            )
        )
    }

    private fun highlightSelectedRoute() {
        for (i in routePolylines.indices) {
            routePolylines[i].color =
                if (i == selectedRouteIndex)
                    Color.BLUE
                else
                    Color.GRAY
        }
    }

    private fun prepareSelectedRoute() {
        val route = routeJsonRoutes[selectedRouteIndex]

        val leg =
            route.getJSONArray("legs")
                .getJSONObject(0)

        currentSteps = leg.getJSONArray("steps")

        val encoded =
            route.getJSONObject("overview_polyline")
                .getString("points")

        currentRoutePath = decodePolyline(encoded)

        currentStepIndex = 0
    }

    private fun setupLocationTracking() {

        locationRequest =
            LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                3000L
            )
                .setMinUpdateIntervalMillis(2000L)
                .build()

        locationCallback =
            object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation ?: return

                    currentLocation =
                        LatLng(loc.latitude, loc.longitude)

                    if (navigationActive) {
                        processNavigationUpdate()
                    }
                }
            }
    }

    private fun startLocationUpdates() {
        if (
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            mainLooper
        )
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun startNavigation() {

        if (routeJsonRoutes.isEmpty()) {
            Toast.makeText(
                this,
                "Select route first",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        navigationActive = true

        btnStartNav.text = "STOP NAVIGATION"
        btnStartNav.setBackgroundColor(Color.RED)

        tvGpsStatus.text = "Navigation: Active"

        startLocationUpdates()
        sendCurrentStepInstruction()
    }

    private fun stopNavigation() {
        navigationActive = false

        stopLocationUpdates()

        btnStartNav.text = "START NAVIGATION"
        btnStartNav.setBackgroundColor(
            Color.parseColor("#1DB954")
        )

        tvGpsStatus.text = "Navigation: Inactive"

        tvLastInstruction.text = "NO NAVIGATION"
        tvOledDetail.text = "Select destination to begin"
    }

    private fun processNavigationUpdate() {

        val current = currentLocation ?: return
        val destination = destinationLocation ?: return
        val steps = currentSteps ?: return

        val distanceToDestination =
            distanceMeters(current, destination)

        if (distanceToDestination <= ARRIVAL_THRESHOLD_METERS) {
            arrived()
            return
        }

        val offRouteDistance =
            distanceToRoute(current, currentRoutePath)

        if (offRouteDistance > OFF_ROUTE_THRESHOLD_METERS) {

            val now = System.currentTimeMillis()

            if (now - lastRerouteTime > 25000) {
                lastRerouteTime = now
                reroute()
            }

            return
        }

        if (currentStepIndex >= steps.length()) return

        val step = steps.getJSONObject(currentStepIndex)

        val endLoc =
            step.getJSONObject("end_location")

        val stepEnd =
            LatLng(
                endLoc.getDouble("lat"),
                endLoc.getDouble("lng")
            )

        val distToStep =
            distanceMeters(current, stepEnd)

        if (distToStep <= STEP_REACHED_THRESHOLD_METERS) {
            currentStepIndex++

            if (currentStepIndex >= steps.length()) {
                arrived()
                return
            }
        }

        sendCurrentStepInstruction()
    }

    private fun sendCurrentStepInstruction() {

        val steps = currentSteps ?: return

        if (currentStepIndex >= steps.length()) return

        val step = steps.getJSONObject(currentStepIndex)

        val endLoc =
            step.getJSONObject("end_location")

        val stepEnd =
            LatLng(
                endLoc.getDouble("lat"),
                endLoc.getDouble("lng")
            )

        val current = currentLocation ?: return

        val liveDistance =
            distanceMeters(current, stepEnd)

        val distance =
            if (liveDistance >= 1000)
                String.format("%.1f km", liveDistance / 1000)
            else
                "${liveDistance.toInt()} m"

        val html =
            step.getString("html_instructions")

        val cleanInstruction =
            android.text.Html.fromHtml(
                html,
                android.text.Html.FROM_HTML_MODE_LEGACY
            ).toString()

        val maneuver =
            if (step.has("maneuver"))
                step.getString("maneuver")
            else
                "NAV"

        val direction = when {

            maneuver.contains("turn-slight-left", true) -> "SL LEFT"
            maneuver.contains("turn-sharp-left", true) -> "SH LEFT"
            maneuver.contains("left", true) -> "LEFT"

            maneuver.contains("turn-slight-right", true) -> "SL RIGHT"
            maneuver.contains("turn-sharp-right", true) -> "SH RIGHT"
            maneuver.contains("right", true) -> "RIGHT"

            maneuver.contains("uturn", true) ||
                    maneuver.contains("u-turn", true) -> "UTURN"

            maneuver.contains("merge", true) -> "MERGE"

            maneuver.contains("fork-left", true) -> "K LEFT"
            maneuver.contains("fork-right", true) -> "K RIGHT"
            maneuver.contains("fork", true) -> "KEEP"

            maneuver.contains("ramp-left", true) -> "X LEFT"
            maneuver.contains("ramp-right", true) -> "X RIGHT"

            maneuver.contains("roundabout-left", true) -> "R-ABOUT"
            maneuver.contains("roundabout-right", true) -> "R-ABOUT"
            maneuver.contains("roundabout", true) -> "R-ABOUT"

            maneuver.contains("ferry", true) -> "FERRY"

            maneuver.contains("straight", true) -> "STRAIGHT"

            cleanInstruction.contains("head", true) -> "STRAIGHT"
            cleanInstruction.contains("continue", true) -> "STRAIGHT"
            cleanInstruction.contains("keep left", true) -> "K LEFT"
            cleanInstruction.contains("keep right", true) -> "K RIGHT"

            else -> "STRAIGHT"
        }

        val packet =
            "$direction|$distance $cleanInstruction"

        LocalBroadcastManager.getInstance(this)
            .sendBroadcast(
                Intent("NAV_DATA")
                    .putExtra("text", packet)
            )

        updateOledPreview(packet)
    }

    private fun reroute() {

        tvGpsStatus.text = "Navigation: Rerouting..."

        currentLocation?.let { origin ->
            destinationLocation?.let { dest ->
                fetchRoutes(origin, dest)
            }
        }
    }

    private fun arrived() {

        navigationActive = false
        stopLocationUpdates()

        val packet = "ARRIVED|Destination reached"

        LocalBroadcastManager.getInstance(this)
            .sendBroadcast(
                Intent("NAV_DATA")
                    .putExtra("text", packet)
            )

        updateOledPreview(packet)

        btnStartNav.text = "START NAVIGATION"
        btnStartNav.setBackgroundColor(
            Color.parseColor("#1DB954")
        )

        tvGpsStatus.text = "Navigation: Complete"
    }

    private fun updateOledPreview(packet: String) {
        val parts =
            packet.split(
                delimiters = arrayOf("|"),
                limit = 2
            )

        if (parts.size >= 2) {
            tvLastInstruction.text = parts[0]
            tvOledDetail.text = parts[1]
        }
    }

    private fun distanceMeters(a: LatLng, b: LatLng): Double {
        val results = FloatArray(1)

        android.location.Location.distanceBetween(
            a.latitude,
            a.longitude,
            b.latitude,
            b.longitude,
            results
        )

        return results[0].toDouble()
    }

    private fun distanceToRoute(
        point: LatLng,
        route: List<LatLng>
    ): Double {

        if (route.isEmpty()) return Double.MAX_VALUE

        var min = Double.MAX_VALUE

        for (p in route) {
            val d = distanceMeters(point, p)
            if (d < min) min = d
        }

        return min
    }

    private fun decodePolyline(encoded: String): List<LatLng> {

        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {

            var b: Int
            var shift = 0
            var result = 0

            do {
                b = encoded[index++].code - 63
                result = result or (
                        (b and 0x1f) shl shift
                        )
                shift += 5
            } while (b >= 0x20)

            val dlat =
                if ((result and 1) != 0)
                    (result shr 1).inv()
                else
                    result shr 1

            lat += dlat

            shift = 0
            result = 0

            do {
                b = encoded[index++].code - 63
                result = result or (
                        (b and 0x1f) shl shift
                        )
                shift += 5
            } while (b >= 0x20)

            val dlng =
                if ((result and 1) != 0)
                    (result shr 1).inv()
                else
                    result shr 1

            lng += dlng

            poly.add(
                LatLng(
                    lat / 1E5,
                    lng / 1E5
                )
            )
        }

        return poly
    }
}
