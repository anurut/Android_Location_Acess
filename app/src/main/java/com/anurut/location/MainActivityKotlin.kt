package com.anurut.location

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.anurut.location.databinding.ActivityMainBinding
import com.google.android.gms.location.*


class MainActivityKotlin : AppCompatActivity() {
    companion object {
        private val TAG = MainActivityKotlin::class.java.simpleName
    
        // Constants
        private const val REQUEST_LOCATION_PERMISSION = 111
        private const val TRACKING_LOCATION_KEY = "tracking_location"

        private const val UPDATE_INTERVAL_IN_MILLISECONDS: Long = 5000
        
        private const val FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = UPDATE_INTERVAL_IN_MILLISECONDS / 2
    }
    // ViewBinding
    private lateinit var binding: ActivityMainBinding
    
    // Location dependencies
    private var fusedLocationProviderClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var locationRequest: LocationRequest? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ViewBinding initialization
        initializeViewBinding()
        
        with(binding) {
            // Set link to root view and update view's elements
            val view: View = root
            setContentView(view)
    
            setSupportActionBar(toolbar)
            
            getCurrentLocationButton.setOnClickListener {
                onGetCurrentLocationButtonClicked()
            }
        }
        
        // Initialize location dependencies
        initializeFusedLocationProviderClient()
        initializeLocationRequest()
        initializeLocationCallback()
    }
    
    // Initialization
    private fun initializeViewBinding() {
        binding = ActivityMainBinding.inflate(layoutInflater)
    }
    private fun initializeFusedLocationProviderClient() {
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
    }
    private fun initializeLocationRequest() {
        locationRequest = LocationRequest().apply {
            interval = UPDATE_INTERVAL_IN_MILLISECONDS
            fastestInterval = FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    }
    private fun initializeLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                stopLocationUpdates()
                updateLocationUI(locationResult.lastLocation)
            }
        }
    }
    private fun stopLocationUpdates() {
        fusedLocationProviderClient
            ?.removeLocationUpdates(locationCallback)
        
        Log.d(TAG, "Location updates were stopped")
    }
    private fun updateLocationUI(currentLocation: Location) {
        // TODO: Добавить проверку на случай, если локация будет
        //  равна нулю, на этот случай добавить snackBar с единственной кнопкой -
        //  OK - по нажатию на которую будет ещё раз производиться подбор локации
        with(binding) {
            latitudeText.text = currentLocation.latitude.toString()
            longitudeText.text = currentLocation.longitude.toString()
        }
    }

    // Get user location
    private fun onGetCurrentLocationButtonClicked() {
        getUserLocation()
    }
    private fun getUserLocation() {
        if (ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(ACCESS_FINE_LOCATION),
                REQUEST_LOCATION_PERMISSION)
        } else {
            if (locationRequest != null && locationCallback != null) {
                fusedLocationProviderClient?.requestLocationUpdates(
                    locationRequest, locationCallback, null
                )
            }
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,
                                            grantResults: IntArray) {
        when (requestCode) {
            REQUEST_LOCATION_PERMISSION ->
                // If the permission is granted, get the location, otherwise,
                // show a Toast
                if (grantResults.isNotEmpty() && grantResults[0]
                    == PackageManager.PERMISSION_GRANTED) {
                    getUserLocation()
                } else {
                    Toast.makeText(this,
                        "Пользователь отклонил разрешения",
                        Toast.LENGTH_SHORT).show()
                }
        }
    }
}