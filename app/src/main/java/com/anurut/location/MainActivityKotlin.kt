package com.anurut.location

import android.Manifest.permission
import android.content.Intent
import android.content.IntentSender.SendIntentException
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.anurut.location.databinding.ActivityMainBinding
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.material.snackbar.Snackbar
import java.text.DateFormat
import java.util.*

class MainActivityKotlin : AppCompatActivity() {
    companion object {
        private val TAG = MainActivity::class.java.simpleName
        
        // Code used in requesting runtime permissions.
        private const val REQUEST_PERMISSIONS_REQUEST_CODE = 34
        
        // Constant used in the location settings dialog.
        private const val REQUEST_CHECK_SETTINGS = 0x1
        
        // Desired interval for location updates. Inexact.
        // Updates may be more or less frequent.
        private const val UPDATE_INTERVAL_IN_MILLISECONDS: Long = 10000
        
        // The fastest rate for active location updates. Exact.
        // Updates will never be more frequent than this value.
        private const val FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = UPDATE_INTERVAL_IN_MILLISECONDS / 2
        
        // Keys for storing activity state in the Bundle.
        private const val KEY_REQUESTING_LOCATION_UPDATES = "requesting-location-updates"
        private const val KEY_LOCATION = "location"
        private const val KEY_LAST_UPDATE_TIME_STRING = "last-update-time-string"
    }
    // Provides access to the Fused Location Provider API.
    private var mFusedLocationProviderClient: FusedLocationProviderClient? = null
    
    // Provides access to the Location Settings API.
    private var mSettingsClient: SettingsClient? = null
    
    // Stores parameters for requests to the FusedLocationProviderAPI.
    private var mLocationRequest: LocationRequest? = null
    
    // Stores the types of location services the client is interested in using. Used for checking
    // settings to determine if the device has optimal location settings.
    private var mLocationSettingsRequest: LocationSettingsRequest? = null
    
    // Callback for location events.
    private var mLocationCallback: LocationCallback? = null
    
    // Current location fetched form the device.
    private var mCurrentLocation: Location? = null
    
    // ViewBinding
    private lateinit var binding: ActivityMainBinding
    
    // Labels
    private var mLatitudeLabel: String? = null
    private var mLongitudeLabel: String? = null
    private var mLastUpdateTimeLabel: String? = null
    
    /*
     * Tracks the status of the location updates request. Value changes when the user presses the
     * Start Updates and Stop Updates buttons.
     */
    // TODO: Put this variable in viewModel
    private var mRequestingLocationUpdates: Boolean = false
    
    /*
     * Time when the last updated location was set.
     */
    private var mLastUpdateTime: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ViewBinding initialization
        initializeViewBinding()
        
        with(binding) {
            // Set link to root view and update view's elements
            val view: View = root
            setContentView(view)
    
            setSupportActionBar(toolbar)
            
            startUpdatesButton.setOnClickListener {
                onStartUpdatesButtonClicked()
            }
            stopUpdatesButton.setOnClickListener {
                onStopUpdatesButtonClicked()
            }
        }
        
        // Set labels
        setLabels()
        
        if (mRequestingLocationUpdates) {
            mRequestingLocationUpdates = false
        }
        mLastUpdateTime = ""
        
        // Update values using data stored in the bundle
        updateValuesFromBundle(savedInstanceState)
        
        initializeFusedLocationProviderClient()
        initializeSettingsClient()
        
        // Kick off the process of building the LocationCallback, LocationRequest, and
        // LocationSettingsRequest objects.
        initializeLocationCallback()
        initializeLocationRequest()
        initializeLocationSettingsRequest()
    }
    
    // Initialization
    private fun initializeViewBinding() {
        binding = ActivityMainBinding.inflate(layoutInflater)
    }
    private fun setLabels() {
        mLatitudeLabel = getString(R.string.latitude_label)
        mLongitudeLabel = getString(R.string.longitude_label)
        mLastUpdateTimeLabel = getString(R.string.last_update_time_label)
    }
    /**
     * Updates fields based on data stored in the bundle
     *
     * @param savedInstanceState The activity state saved in the bundle
     */
    private fun updateValuesFromBundle(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            // Update the value of mRequestingLocationUpdates from the Bundle, and make sure that
            // the Start Updates and Stop Updates buttons are correctly enabled or disabled.
            if (savedInstanceState.keySet().contains(KEY_REQUESTING_LOCATION_UPDATES)) {
                mRequestingLocationUpdates = savedInstanceState.getBoolean(
                    KEY_REQUESTING_LOCATION_UPDATES
                )
            }
            
            // Update the value of mCurrentLocation from the Bundle and update the UI to show the
            // correct latitude and longitude.
            if (savedInstanceState.keySet().contains(KEY_LOCATION)) {
                // Since KEY_LOCATION was found in the Bundle, we can be sure that mCurrentLocation
                // is not null.
                // TODO: Put this variable in viewModel
                mCurrentLocation = savedInstanceState.getParcelable(KEY_LOCATION)
            }
            
            // Update the value of mLastUpdateTime from the Bundle and update the UI.
            if (savedInstanceState.keySet().contains(KEY_LAST_UPDATE_TIME_STRING)) {
                // TODO: Put this variable in viewModel
                mLastUpdateTime = savedInstanceState.getString(KEY_LAST_UPDATE_TIME_STRING)
            }
            updateUI()
        }
    }
    private fun initializeFusedLocationProviderClient() {
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
    }
    private fun initializeSettingsClient() {
        mSettingsClient = LocationServices.getSettingsClient(this)
    }
    /**
     * Creates a callback for receiving location events
     */
    private fun initializeLocationCallback() {
        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                mCurrentLocation = locationResult.lastLocation
                mLastUpdateTime = DateFormat.getTimeInstance().format(Date())
                updateLocationUI()
            }
        }
    }
    /**
     * Sets up the location request. Android has two location request settings:
     * `ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION`. These settings control
     * the accuracy of the current location. This sample uses ACCESS_FINE_LOCATION, as defined in
     * the AndroidManifest.xml.
     *
     *
     * When the ACCESS_FINE_LOCATION setting is specified, combined with a fast update interval
     * (5 seconds), the Fused Location Provider API returns location updates that are accurate to
     * within a few feet.
     *
     *
     * These settings are appropriate for mapping applications that show real-time location updates
     */
    private fun initializeLocationRequest() {
        mLocationRequest = LocationRequest().apply {
            // Sets the desired interval for active location updates. This interval is
            // inexact. You may not receive updates at all if no location sources are available, or
            // you may receive them slower than requested. You may also receive updates faster than
            // requested if other applications are requesting location at a faster interval.
            interval = UPDATE_INTERVAL_IN_MILLISECONDS
    
            // Sets the fastest rate for active location updates. This interval is exact, and your
            // application will never receive updates faster than this value.
            fastestInterval = FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    
            // Uncomment if the user wants limited amount of updates
            // mLocationRequest.setNumUpdates(3);
        }
    }
    
    /**
     * Uses a [com.google.android.gms.location.LocationSettingsRequest.Builder] to build
     * a [com.google.android.gms.location.LocationSettingsRequest] that is used for checking
     * if a device has the needed location settings.
     */
    private fun initializeLocationSettingsRequest() {
        val builder = LocationSettingsRequest.Builder()
        mLocationRequest?.let { builder.addLocationRequest(it) }
        mLocationSettingsRequest = builder.build()
    }
    override fun onResume() {
        super.onResume()
        if (mRequestingLocationUpdates && checkPermissions()) startLocationUpdates()
        else if (!checkPermissions()) requestLocationPermissions()
        updateUI()
    }
    /**
     * Return the current state of the permissions needed.
     */
    private fun checkPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(this,
            permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    private fun requestLocationPermissions() {
        val shouldProvideRationale = ActivityCompat.shouldShowRequestPermissionRationale(
            this, permission.ACCESS_FINE_LOCATION)
        
        // Provide an additional rationale to the user. This would happen if the user denied the
        // request previously, but didn't check the "Don't ask again" checkbox.
        if (shouldProvideRationale) {
            Log.i(TAG, "Displaying permission rationale to provide additional context.")
            showSnackBar(R.string.permission_rationale, android.R.string.ok) {
                // Request Permission
                ActivityCompat.requestPermissions(this, arrayOf(permission.ACCESS_FINE_LOCATION),
                    REQUEST_PERMISSIONS_REQUEST_CODE)
            }
        } else {
            Log.i(TAG, "Requesting Permission")
            ActivityCompat.requestPermissions(this, arrayOf(permission.ACCESS_FINE_LOCATION),
                REQUEST_PERMISSIONS_REQUEST_CODE)
        }
    }
    /**
     * Callback received when a permissions request has been completed.
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        //super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.i(TAG, "onRequestPermissionsResult")
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.isEmpty()) {
                // If user interaction was interrupted, the permission request is cancelled and you
                // receive empty arrays.
                Log.i(TAG, "User interaction was cancelled")
            } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (mRequestingLocationUpdates) {
                    Log.i(TAG, "Permission granted, updates requested, starting updates")
                    startLocationUpdates()
                }
            } else {
                // Permission denied.
                
                // Notify the user via a SnackBar that they have rejected a core permission for the
                // app, which makes the Activity useless. In a real app, core permissions would
                // typically be best requested during a welcome-screen flow.
                
                // Additionally, it is important to remember that a permission might have been
                // rejected without asking the user for permission (device policy or "Never ask
                // again" prompts). Therefore, a user interface affordance is typically implemented
                // when permissions are denied. Otherwise, your app could appear unresponsive to
                // touches or interactions which have required permissions.
                showSnackBar(R.string.permission_denied_explanation, R.string.settings) {
                    // Build intent that displays the App settings screen.
                    val intent = Intent()
                    intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    val uri = Uri.fromParts("package", BuildConfig.APPLICATION_ID,
                        null)
                    intent.data = uri
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                }
            }
        }
    }
    override fun onPause() {
        super.onPause()
        // Remove location updates to save battery.
        stopLocationUpdates()
    }
    // Listeners
    /**
     * Handles the Start Updates button and requests start of location updates. Does nothing if
     * updates have already been requested.
     */
    private fun onStartUpdatesButtonClicked() {
        if (!mRequestingLocationUpdates) {
            mRequestingLocationUpdates = true
            setButtonsEnabledState()
            startLocationUpdates()
        }
    }
    /**
     * Handles the Stop Updates button, and requests removal of location updates.
     */
    private fun onStopUpdatesButtonClicked() {
        stopLocationUpdates()
    }
    
    // General methods
    override fun onSaveInstanceState(outState: Bundle) {
        with(outState) {
            putBoolean(KEY_REQUESTING_LOCATION_UPDATES, mRequestingLocationUpdates)
            putParcelable(KEY_LOCATION, mCurrentLocation)
            putString(KEY_LAST_UPDATE_TIME_STRING, mLastUpdateTime)
        }
        super.onSaveInstanceState(outState)
    }
    
    private fun updateUI() {
        setButtonsEnabledState()
        updateLocationUI()
    }
    
    private fun setButtonsEnabledState() {
        // TODO: Set an observer here
        with(binding) {
            if (mRequestingLocationUpdates) {
                startUpdatesButton.isEnabled = false
                stopUpdatesButton.isEnabled = true
            } else {
                startUpdatesButton.isEnabled = true
                stopUpdatesButton.isEnabled = false
            }
        }
    }
    
    private fun updateLocationUI() {
        // TODO: Set observer here
        with(binding) {
            if (mCurrentLocation != null) {
                latitudeText.text = String.format(Locale.ENGLISH, "%s: %f", mLatitudeLabel,
                    mCurrentLocation?.latitude)
                longitudeText.text = String.format(Locale.ENGLISH, "%s: %f", mLongitudeLabel,
                    mCurrentLocation?.longitude)
                lastUpdateTimeText.text = String.format(Locale.ENGLISH, "%s: %s",
                    mLastUpdateTimeLabel, mLastUpdateTime)
            }
        }
    }
    
    /**
     * Requests location updates from the FusedLocationApi. Note: we don't call this unless location
     * runtime permission has been granted.
     */
    private fun startLocationUpdates() {
        // Begin by checking if the device has the necessary location settings.
        mSettingsClient
            ?.checkLocationSettings(mLocationSettingsRequest)
            ?.addOnSuccessListener(this) {
                Log.i(TAG, "All location settings are satisfied.")
                if (ActivityCompat.checkSelfPermission(this, permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(this, permission.ACCESS_COARSE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                    // Request location updates after checking request permissions
                    mFusedLocationProviderClient?.requestLocationUpdates(
                        mLocationRequest,
                        mLocationCallback,
                        Looper.getMainLooper())
                    updateUI()
                }
            }
            ?.addOnFailureListener(this) { exception: Exception ->
                when ((exception as ApiException).statusCode) {
                    LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> {
                        Log.i(TAG, "Location settings are not satisfied. Attempting to upgrade " +
                            "location settings ")
                        try {
                            val resolvableApiException = exception as ResolvableApiException
                            resolvableApiException.startResolutionForResult(this, REQUEST_CHECK_SETTINGS)
                        } catch (ex: SendIntentException) {
                            Log.i(TAG, "PendingIntent unable to execute request.")
                            ex.printStackTrace()
                        }
                    }
                    LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {
                        val errorMessage = "Location settings are inadequate, and cannot be " +
                            "fixed here. Fix in Settings."
                        Log.e(TAG, errorMessage)
                        Toast.makeText(this, errorMessage,
                            Toast.LENGTH_LONG)
                            .show()
                        mRequestingLocationUpdates = false
                    }
                }
                updateUI()
            }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        //super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CHECK_SETTINGS) {
            when (resultCode) {
                RESULT_OK -> Log.i(TAG, "User agreed to make required location settings changes.")
                RESULT_CANCELED -> {
                    Log.i(TAG, "User chose not to make required location settings changes.")
                    mRequestingLocationUpdates = false
                    updateUI()
                }
            }
            super.onActivityResult(requestCode, resultCode, data)
        }
    }
    
    private fun showSnackBar(mainTextStringId: Int, actionStringId: Int, onClickListener: View.OnClickListener) {
        Snackbar.make(binding.root, getString(mainTextStringId),
            Snackbar.LENGTH_INDEFINITE)
            .setAction(getString(actionStringId),
            onClickListener).show()
    }
    /**
     * Removes location updates from the FusedLocationApi.
     */
    private fun stopLocationUpdates() {
        if (!mRequestingLocationUpdates) {
            Log.d(TAG, "stopLocationUpdates: updates never requested, no-op.")
            return
        }
        
        // It is a good practice to remove location requests when the activity is in a paused or
        // stopped state. Doing so helps battery performance and is especially
        // recommended in applications that request frequent location updates.
        mFusedLocationProviderClient
            ?.removeLocationUpdates(mLocationCallback)
            ?.addOnCompleteListener(this) {
                mRequestingLocationUpdates = false
                setButtonsEnabledState()
            }
    }
}