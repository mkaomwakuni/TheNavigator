package com.mkao.thenavigator

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.mkao.thenavigator.databinding.ActivityMapsBinding
import java.io.IOException
import kotlin.random.Random

class MapsActivity : AppCompatActivity(), OnMapReadyCallback,SensorEventListener {

    private lateinit var mMap: GoogleMap
    //finds the user locality and prepares the map view
    private lateinit var recentLastLocation :Location
    private lateinit var fusedLocation: FusedLocationProviderClient
    private lateinit var binding: ActivityMapsBinding
    private val geofencelist = arrayListOf<Geofence>()
    private var DestinLocation: LatLng? =null
    private var destinationMarker:Marker? = null
    private lateinit var geofenceclient: GeofencingClient
    private var navigationstarted = false
    private  var receivingdUpdates = false
    private lateinit var locationcallback:LocationCallback
    private  lateinit var locationRequest: LocationRequest
    private val accelerometerReading = FloatArray(3)
    private  val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var isRotating = false
    private lateinit var sensorManager: SensorManager

    private val timer = object :CountDownTimer(4200000,1000){
        override fun onTick(untilfinishedmilisec: Long) {
            binding.timer.text = getString(R.string.timer,untilfinishedmilisec/1000)

        }

        override fun onFinish() {
            endNavigation()
            binding.timer.text = getString(R.string.times_up)
        }
    }
    private val broadcastReceiver = object:BroadcastReceiver(){
        override fun onReceive(p0: Context?, p1: Intent?) {
            endNavigation()
            Toast.makeText(this@MapsActivity,getString(R.string.treasure_found),Toast.LENGTH_SHORT).show()
        }

    }

    private fun endNavigation() {
        geofenceclient.removeGeofences(createGeofencePendingIntent()).run {
            addOnSuccessListener {
                geofencelist.clear()
            }
            addOnFailureListener{}
        }
        if (destinationMarker==null)
            destinationMarker = placeMakerOnMap(DestinLocation!!)
        binding.treasureHuntButton.text = getString(R.string.start_Place_Navigation)
        binding.hintButton.visibility = View.INVISIBLE
        navigationstarted = false
        //timer discontd.
        timer.cancel()
        binding.timer.text = getString(R.string.navigation_ended)
        
        
    }

    private fun placeMakerOnMap(destinLocation: LatLng): Marker? {

        val markerOpt = MarkerOptions().position(destinLocation)
            .title(getCordinates(destinLocation))
        return  mMap.addMarker(markerOpt)
    }

    private fun getCordinates(destinLocationcord: LatLng): String? {

        var cordText = getString(R.string.no_address)
        try {
            val addresses = Geocoder(this).getFromLocation(destinLocationcord.latitude,destinLocationcord.longitude,1)
            if (!addresses.isNullOrEmpty()){
                cordText = addresses[0].getAddressLine(0)?: cordText
            }
        }catch (e:IOException){
            cordText = getString(R.string.address_error)
        }
        return cordText
    }

    companion object{
        const val MINIMAL_RECO_RADIUS = 6F
        const val  GEOFENCE_KEY = "Destination"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getSupportActionBar()?.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM)
        getSupportActionBar()?.setCustomView(R.layout.cussstombar)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocation = LocationServices.getFusedLocationProviderClient(this)
        // check whether the location permissions have been granted each time  app launches
        if (!LocationHelperPermissions.hasPermissionManager(this))
            LocationHelperPermissions.requestPermissions(this)
        //initialise geoclient
        geofenceclient = LocationServices.getGeofencingClient(this)
        //broadcasts registration
        registerReceiver(broadcastReceiver, IntentFilter("PLACE ARRIVED"))

        binding.treasureHuntButton.setOnClickListener {
            when{
                !this::recentLastLocation.isInitialized -> Toast.makeText(this,getString(R.string.location_error),Toast.LENGTH_SHORT).show()
                navigationstarted -> endNavigation()
                else -> {
                    DestinationLocation()
                    binding.treasureHuntButton.text = getString(R.string.end_the_treasure_hunt)
                    binding.hintButton.visibility = View.VISIBLE
                    navigationstarted = true

                }
            }
        }
        //run hints
        binding.hintButton.setOnClickListener {
            hintme()
        }
        //init sensor manager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { accelerometer ->
            sensorManager.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL,
                SensorManager.SENSOR_DELAY_UI
            )
        }

        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also { magneticField ->
            sensorManager.registerListener(
                this,
                magneticField,
                SensorManager.SENSOR_DELAY_NORMAL,
                SensorManager.SENSOR_DELAY_UI
            )
        }
    }

        //user’s response to the permissions request
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (!LocationHelperPermissions.hasPermissionManager(this)){
            LocationHelperPermissions.requestPermissions(this)
        }else
            prepareMap()
    }

    @SuppressLint("MissingPermission")
    private fun prepareMap() {
      if (LocationHelperPermissions.hasPermissionManager(this)){
          mMap.isMyLocationEnabled = true
          //Locate last known locality
          fusedLocation.lastLocation.addOnSuccessListener { location:Location? ->
              location?.apply {
                  recentLastLocation = location
                  val currentLatitude = LatLng(location.latitude,location.longitude)
                  mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatitude, 20.0f))
              }
          }
      }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomControlsEnabled = true
        prepareMap()
    }

    object LocationHelperPermissions {
        private const val  BACKGROUND_LOCATION_PERMISSION = Manifest.permission.ACCESS_BACKGROUND_LOCATION
        private const val  COARSE_LOCATION_PERMISSION = Manifest.permission.ACCESS_COARSE_LOCATION
        private const val FINE_LOCATION_PERMISSION = Manifest.permission.ACCESS_FINE_LOCATION
        fun hasPermissionManager (activity: Activity): Boolean {
            return ContextCompat.checkSelfPermission(activity, FINE_LOCATION_PERMISSION) ==PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(activity, BACKGROUND_LOCATION_PERMISSION)==PackageManager.PERMISSION_GRANTED
        }
        fun requestPermissions(activity: Activity){
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity,
                    FINE_LOCATION_PERMISSION)){
                AlertDialog.Builder(activity).apply {
                    setMessage(activity.getString(R.string.permission_required))
                    setPositiveButton(activity.getString(R.string.ok)){
                        _,_ ->
                        ActivityCompat.requestPermissions(activity, arrayOf(FINE_LOCATION_PERMISSION,
                            COARSE_LOCATION_PERMISSION, BACKGROUND_LOCATION_PERMISSION),0)
                    }
                    show()
                }
            }else{
                ActivityCompat.requestPermissions(activity, arrayOf(FINE_LOCATION_PERMISSION,
                    COARSE_LOCATION_PERMISSION, BACKGROUND_LOCATION_PERMISSION),0)
            }
        }
    }

    @SuppressLint("SuspiciousIndentation")
    private fun removeDestinationMaker() {
    if (destinationMarker!=null)
        destinationMarker?.remove()
        destinationMarker = null
    }

    private fun DestinationLocation (){
        val desireList = listOf(true, false)
        var desireplace = desireList.random()
        val destinationLat = if (desireplace) recentLastLocation.latitude + Random.nextFloat()
        else
            recentLastLocation.latitude - Random.nextFloat()
        desireplace = desireList.random()
        val destinationLong = if (desireplace) recentLastLocation.longitude + Random.nextFloat()
        else
            recentLastLocation.longitude - Random.nextFloat()
        DestinLocation = LatLng(destinationLat, destinationLong)

        removeDestinationMaker()
        geofencelist.add(Geofence.Builder()
            .setRequestId(GEOFENCE_KEY)
            .setCircularRegion(
                destinationLat,destinationLong, MINIMAL_RECO_RADIUS)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .build()
        )
        try {
            geofenceclient.addGeofences(createGeofencingRequest(),createGeofencePendingIntent())
                .addOnSuccessListener(this) {
                Toast.makeText(this, getString(R.string.start_Place_Navigation),
                Toast.LENGTH_SHORT).show()

                    timer.start()
                    hintme()

                    //AREA OF VISIBILITY
                    val figure = CircleOptions()
                        .strokeColor(Color.RED)
                        .fillColor(Color.TRANSPARENT)
                        .center(DestinLocation!!)
                        .radius(MINIMAL_RECO_RADIUS.toDouble())
                    mMap.addCircle(figure)
                    }
                .addOnFailureListener(this) { e -> Toast.makeText(this,
                    getString(R.string.treasure_error, e.message), Toast.LENGTH_SHORT).show()
                }
        } catch (ignore: SecurityException) {}


    }

    private fun createGeofencePendingIntent(): PendingIntent {
       val intent = Intent(this,GeofenceBroadcastReceiver::class.java)
        return  PendingIntent.getBroadcast(this,0,intent,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun createGeofencingRequest(): GeofencingRequest {
        return GeofencingRequest.Builder().apply {
            setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            addGeofences(geofencelist)
        }.build()
    }
    //hint for directions
    private fun hintme () {
        if (DestinLocation!=null && this::recentLastLocation.isInitialized){
            val DirLat = if (DestinLocation!!.latitude > recentLastLocation.latitude) getString(R.string.north)
            else getString(R.string.south)
            val DirLong = if (DestinLocation!!.longitude > recentLastLocation.longitude)
                getString(R.string.east) else getString(R.string.west)
            Toast.makeText(this,getString(R.string.direction,DirLat,DirLong),Toast.LENGTH_SHORT).show()
        }
    }
    private fun createLocationRequest() {
        locationRequest = LocationRequest.create().apply {
            interval = 10000
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        val locationSettingsRequest = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .build()

        val client = LocationServices.getSettingsClient(this)
        client.checkLocationSettings(locationSettingsRequest).apply {
            addOnSuccessListener {
                receivingdUpdates = true
                startLocationUpdates()
            }
            addOnFailureListener {
                if (it is ResolvableApiException) {
                    registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
                        if (result.resultCode == RESULT_OK) {
                            receivingdUpdates = true
                            startLocationUpdates()
                        }
                    }.launch(IntentSenderRequest.Builder(it.resolution).build())
                }
            }
        }
    }

    private fun startLocationUpdates() {
        try {
            locationcallback = object : LocationCallback(){
                override fun onLocationResult(p0: LocationResult) {
                    super.onLocationResult(p0)
                    recentLastLocation = p0.lastLocation!!
                }
            }
            fusedLocation.requestLocationUpdates(locationRequest,locationcallback, Looper.getMainLooper())
        }catch (ignore:SecurityException){

        }
    }

    override fun onPause() {
        super.onPause()
        if (this::locationcallback.isInitialized)
            fusedLocation.removeLocationUpdates(locationcallback)
    }


    //unregister  broadcasts
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(broadcastReceiver)
    }

    override fun onSensorChanged(p0: SensorEvent?) {
     if (p0==null)
         return
        when(p0.sensor.type){
            Sensor.TYPE_ACCELEROMETER -> System.arraycopy(p0.values,0,accelerometerReading
            ,0,accelerometerReading.size)
            Sensor.TYPE_MAGNETIC_FIELD -> System.arraycopy(p0.values,0,magnetometerReading,0,magnetometerReading.size)
        }
        if (!isRotating) updateOrientatiobAngel()
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {

    }
    private fun updateOrientatiobAngel() {
        SensorManager.getRotationMatrix(rotationMatrix,null,accelerometerReading,magnetometerReading)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)
        val degrees = (Math.toDegrees(orientationAngles[0].toDouble()))
        val newRotation = degrees.toFloat() * -1
        val rotationChange = newRotation - binding.compass.rotation
        binding.compass.animate().apply {
            isRotating = true
            rotationBy(rotationChange)
            duration = 500
            setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isRotating = false }
            })
        }.start()
    }
}