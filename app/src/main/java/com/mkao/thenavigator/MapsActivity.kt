package com.mkao.thenavigator

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.mkao.thenavigator.databinding.ActivityMapsBinding

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding

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
        // check whether the location permissions have been granted each time  app launches
        if (!LocationHelperPermissions.hasPermissionManager(this))
            LocationHelperPermissions.requestPermissions(this)
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

    private fun prepareMap() {
        TODO("Not yet implemented")
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Add a marker in Sydney and move the camera
        val sydney = LatLng(-34.0, 151.0)
        mMap.addMarker(MarkerOptions().position(sydney).title("Marker in Sydney"))
        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney))
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
}