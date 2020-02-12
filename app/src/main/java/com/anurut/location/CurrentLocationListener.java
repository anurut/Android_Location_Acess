package com.anurut.location;

import android.app.Activity;
import android.content.Context;
import android.content.IntentSender;
import android.location.Location;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.text.DateFormat;
import java.util.Date;

class CurrentLocationListener {

    private static final int REQUEST_CHECK_SETTINGS = 0x1;
    private static CurrentLocationListener instance = null;
    private static final String TAG = "CurrentLocationListener";
    private LocationCallback locationCallback;
    private SettingsClient settingsClient;
    private LocationSettingsRequest locationSettingsRequest;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationRequest locationRequest;
    private MutableLiveData<Location> currentLocation;
    private MutableLiveData<String> lastUpdateTime;
    private MutableLiveData<Boolean> isRequestingLocationUpdates;
    private Context appContext;


    /**
     * Constructor needs to be private in order to make it a Singleton class
     */
    private CurrentLocationListener(Context appContext, LocationRequest locationRequest) {
        //startLocationUpdates(appContext);
        this.appContext = appContext;
        this.locationRequest = locationRequest;
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(appContext);
        settingsClient = LocationServices.getSettingsClient(appContext);
        setIsRequestingLocationUpdates(false);
        //setLastUpdateTime("");

        createLocationCallback();
        //createLocationRequest();
        buildLocationSettingsRequest();
    }

    /**
     *  Making it Singleton
     */
    static CurrentLocationListener getInstance(Context appContext, LocationRequest locationRequest) {

        if (instance == null) {
            instance = new CurrentLocationListener(appContext, locationRequest);
        }
        return instance;
    }

    /**
     * Creates a callback for receiving location events
     */
    private void createLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                setCurrentLocation(locationResult.getLastLocation());
                setLastUpdateTime(DateFormat.getTimeInstance().format(new Date()));
            }
        };
    }

    /**
     * Uses a {@link com.google.android.gms.location.LocationSettingsRequest.Builder} to build
     * a {@link com.google.android.gms.location.LocationSettingsRequest} that is used for checking
     * if a device has the needed location settings.
     */
    private void buildLocationSettingsRequest() {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(locationRequest);
        locationSettingsRequest = builder.build();
    }

    /**
     * Requests location updates from the FusedLocationApi. Note: we don't call this unless location
     * runtime permission has been granted.
     */
    void startLocationUpdates(final Activity activity) {
        // Begin by checking if the device has the necessary location settings.
        settingsClient.checkLocationSettings(locationSettingsRequest)
                .addOnSuccessListener(activity, new OnSuccessListener<LocationSettingsResponse>() {
                    @Override
                    public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                        Log.i(TAG, "All location settings are satisfied.");
                        // Request location updates without checking request permissions
                        fusedLocationProviderClient.requestLocationUpdates(
                                locationRequest,
                                locationCallback,
                                Looper.getMainLooper());
                    }
                })
                .addOnFailureListener(activity, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        int statusCode = ((ApiException) e).getStatusCode();

                        switch (statusCode) {
                            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                                Log.i(TAG, "Location settings are not satisfied. Attempting to upgrade " +
                                        "location settings ");

                                try {
                                    ResolvableApiException resolvableApiException = (ResolvableApiException) e;
                                    resolvableApiException.startResolutionForResult(activity, REQUEST_CHECK_SETTINGS);
                                } catch (IntentSender.SendIntentException ex) {
                                    Log.i(TAG, "PendingIntent unable to execute request.");
                                    ex.printStackTrace();
                                }
                                break;
                            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                                String errorMessage = "Location settings are inadequate, and cannot be " +
                                        "fixed here. Fix in Settings.";
                                Log.e(TAG, errorMessage);
                                Toast.makeText(activity, errorMessage,
                                        Toast.LENGTH_LONG)
                                        .show();
                                setIsRequestingLocationUpdates(false);
                        }
                    }
                });
    }

    /**
     * Removes location updates from the FusedLocationApi.
     * @param activity
     */
    void stopLocationUpdates(MainActivity activity) {
        if (!isRequestingLocationUpdates.getValue()) {
            Log.d(TAG, "stopLocationUpdates: updates never requested, no-op.");
            return;
        }

        // It is a good practice to remove location requests when the activity is in a paused or
        // stopped state. Doing so helps battery performance and is especially
        // recommended in applications that request frequent location updates.
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
                .addOnCompleteListener(activity, new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        setIsRequestingLocationUpdates(false);
                    }
                });

    }

    // ************************************************ Getters and Setters **********************************************************//
    MutableLiveData<Location> getCurrentLocation() {
        if (currentLocation == null) {
            currentLocation = new MutableLiveData<>();
            //getLastKnownLocation();
        }
        return currentLocation;
    }

    private void setCurrentLocation(Location currentLocation) {
        if (this.currentLocation == null) {
            this.currentLocation = new MutableLiveData<>();
        }
        this.currentLocation.setValue(currentLocation);
    }

    MutableLiveData<String> getLastUpdateTime() {
        if (this.lastUpdateTime == null) {
            this.lastUpdateTime = new MutableLiveData<>();
            this.lastUpdateTime.setValue("Not updated yet");
        }
        return lastUpdateTime;
    }

    private void setLastUpdateTime(String lastUpdateTime) {
        if (this.lastUpdateTime == null)
            this.lastUpdateTime = new MutableLiveData<>();
            this.lastUpdateTime.setValue("");
        this.lastUpdateTime.setValue(lastUpdateTime);
    }

    MutableLiveData<Boolean> getIsRequestingLocationUpdates() {
        if(this.isRequestingLocationUpdates == null) {
            this.isRequestingLocationUpdates = new MutableLiveData<>();
            this.isRequestingLocationUpdates.setValue(false);
        }
        return isRequestingLocationUpdates;
    }

    void setIsRequestingLocationUpdates(Boolean isRequestingLocationUpdates) {
        if (this.isRequestingLocationUpdates == null) {
            this.isRequestingLocationUpdates = new MutableLiveData<>();
            //this.isRequestingLocationUpdates.setValue(false);
        }
        this.isRequestingLocationUpdates.setValue(isRequestingLocationUpdates);
    }
}
