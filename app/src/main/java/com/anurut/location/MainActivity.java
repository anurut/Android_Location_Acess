package com.anurut.location;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

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
import com.google.android.material.snackbar.Snackbar;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    //    Code used in requesting runtime permissions.
    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 34;

    //    Constant used in the location settinds dialog.
    private static final int REQUSET_CHECK_SETTINGS = 0x1;

    //    Desired interval for location updates. Inexact. Updates may be more or less frequent.
    private static final long UPDATE_INTERVAL_IN_MILLISECONDS = 10000;

    //    The fastest rate for active loation updates. Exact. Updates will never be more frequent than this value.
    private static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS =
            UPDATE_INTERVAL_IN_MILLISECONDS / 2;

    //    Keys for storing activity state in the Bundle.
    private static final String KEY_REQUESTING_LOCATION_UPDATES = "requesting-location-updates";
    private static final String KEY_LOCATION = "location";
    private static final String KEY_LAST_UPDATE_TIME_STRING = "last-update-time-string";

    //    Provides access to the Fused Location Provider API.
    private FusedLocationProviderClient mFusedLocationProviderClient;

    //    Provides access to the Location Settings API.
    private SettingsClient mSettingsClient;

    //    Stores parameters for requests to the FusedLocationProviderAPI.
    private LocationRequest mLocationRequest;

    //    Stores the types of location services the client is interested in using. Used for checking
    //    settings to determine if the device has optimal location settings.
    private LocationSettingsRequest mLocationSetiingsRequest;

    //    Callback for location events.
    private LocationCallback mLocationCallback;

    //    Current location fetched form the device.
    private Location mCurrentLocation;

    // UI widgets
    private Button mStartUpdatesButton;
    private Button mStopUpdatesButton;
    private TextView mLastUpdateTimeTextView;
    private TextView mLatitudeTextView;
    private TextView mLongitudeTextView;

    // Labels
    private String mLatitudeLabel;
    private String mLongitudeLabel;
    private String mLastUdateTimeLabel;

    /*
     * Tracks the status of the location updates request. Value changes when the user presses the
     * Start Updates and Stop Updates buttons.
     */
    // TODO: Put this variable in viewModel
    private Boolean mRequestingLocationUpdates;

    /*
     * Time when the last updated location was set.
     */
    private String mLastUdateTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Locate the UI widgets
        bindViews();

        // Set labels
        setLabels();

        mRequestingLocationUpdates = false;
        mLastUdateTime = "";

        // Update values using data stored in the bundle
        updateValuesFromBundle(savedInstanceState);

        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        mSettingsClient = LocationServices.getSettingsClient(this);

        // Kick off the process of building the LocationCallback, LocationRequest, and
        // LocationSettingsRequest objects.
        createLocationCallback();
        createLocationRequest();
        buildLocationSettingsRequest();
    }

    /**
     * Sets up the location request. Android has two location request settings:
     * {@code ACCESS_COARSE_LOCATION} and {@code ACCESS_FINE_LOCATION}. These settings control
     * the accuracy of the current location. This sample uses ACCESS_FINE_LOCATION, as defined in
     * the AndroidManifest.xml.
     * <p/>
     * When the ACCESS_FINE_LOCATION setting is specified, combined with a fast update interval
     * (5 seconds), the Fused Location Provider API returns location updates that are accurate to
     * within a few feet.
     * <p/>
     * These settings are appropriate for mapping applications that show real-time location updates
     */
    private void createLocationRequest() {
        mLocationRequest = new LocationRequest();

        // Sets the desired interval for active location updates. This interval is
        // inexact. You may not receive updates at all if no location sources are available, or
        // you may receive them slower than requested. You may also receive updates faster than
        // requested if other applications are requesting location at a faster interval.
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);

        // Sets the fastest rate for active location updates. This interval is exact, and your
        // application will never receive updates faster than this value.
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    /**
     * Creates a callback for receiving location events
     */
    private void createLocationCallback() {
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                mCurrentLocation = locationResult.getLastLocation();
                mLastUdateTime = DateFormat.getTimeInstance().format(new Date());
                updateLocationUI();
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
        builder.addLocationRequest(mLocationRequest);
        mLocationSetiingsRequest = builder.build();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        //super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUSET_CHECK_SETTINGS) {
            switch (resultCode) {
                case Activity.RESULT_OK:
                    Log.i(TAG, "User agreed to make required location settings changes.");
                    // Nothing to do. startLocationUpdates() gets called in onResume again.
                    break;
                case Activity.RESULT_CANCELED:
                    Log.i(TAG, "User chose not to make required location settings changes.");
                    mRequestingLocationUpdates = false;
                    updateUI();
                    break;
            }
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    /**
     * Updates fields based on data stored in the bundle
     *
     * @param savedInstanceState The activity state saved in the bundle
     */
    private void updateValuesFromBundle(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            // Update the value of mRequestingLocationUpdates from the Bundle, and make sure that
            // the Start Updates and Stop Updates buttons are correctly enabled or disabled.
            if (savedInstanceState.keySet().contains(KEY_REQUESTING_LOCATION_UPDATES)) {
                mRequestingLocationUpdates = savedInstanceState.getBoolean(
                        KEY_REQUESTING_LOCATION_UPDATES
                );
            }

            // Update the value of mCurrentLocation from the Bundle and update the UI to show the
            // correct latitude and longitude.
            if (savedInstanceState.keySet().contains(KEY_LOCATION)) {
                // Since KEY_LOCATION was found in the Bundle, we can be sure that mCurrentLocation
                // is not null.
                // TODO: Put this variable in viewModel
                mCurrentLocation = savedInstanceState.getParcelable(KEY_LOCATION);
            }

            // Update the value of mLastUpdateTime from the Bundle and update the UI.
            if (savedInstanceState.keySet().contains(KEY_LAST_UPDATE_TIME_STRING)) {
                // TODO: Put this variable in viewModel
                mLastUdateTime = savedInstanceState.getString(KEY_LAST_UPDATE_TIME_STRING);
            }
            updateUI();
        }
    }

    private void updateUI() {
        setButtonsEnabledState();
        updateLocationUI();
    }


    private void setButtonsEnabledState() {
        // TODO: Set an observer here
        if (mRequestingLocationUpdates) {
            mStartUpdatesButton.setEnabled(false);
            mStopUpdatesButton.setEnabled(true);
        } else {
            mStartUpdatesButton.setEnabled(true);
            mStopUpdatesButton.setEnabled(false);
        }
    }


    private void updateLocationUI() {
        // TODO: Set observer here
        if (mCurrentLocation != null) {
            mLatitudeTextView.setText(String.format(Locale.ENGLISH, "%s: %f", mLatitudeLabel,
                    mCurrentLocation.getLatitude()));
            mLongitudeTextView.setText(String.format(Locale.ENGLISH, "%s: %f", mLongitudeLabel,
                    mCurrentLocation.getLongitude()));
            mLastUpdateTimeTextView.setText(String.format(Locale.ENGLISH, "%s: %s",
                    mLastUdateTimeLabel, mLastUdateTime));
        }
    }


    private void bindViews() {
        mStartUpdatesButton = findViewById(R.id.start_updates_button);
        mStopUpdatesButton = findViewById(R.id.stop_updates_button);
        mLatitudeTextView = findViewById(R.id.latitude_text);
        mLongitudeTextView = findViewById(R.id.longitude_text);
        mLastUpdateTimeTextView = findViewById(R.id.last_update_time_text);
    }

    private void setLabels() {
        mLatitudeLabel = getResources().getString(R.string.latitude_label);
        mLongitudeLabel = getResources().getString(R.string.longitude_label);
        mLastUdateTimeLabel = getResources().getString(R.string.last_update_time_label);
    }


    /**
     * Handles the Start Updates button and requests start of location updates. Does nothing if
     * updates have already been requested.
     */
    public void startUpdatesButtonHandler(View view) {
        if (!mRequestingLocationUpdates) {
            mRequestingLocationUpdates = true;
            setButtonsEnabledState();
            startLocationUpdates();
        }
    }

    /**
     * Handles the Stop Updates button, and requests removal of location updates.
     */
    public void stopUpdatesButtonHandler(View view) {
        stopLocationUpdates();
    }

    /**
     * Removes location updates from the FusedLocationApi.
     */
    private void stopLocationUpdates() {
        if (!mRequestingLocationUpdates) {
            Log.d(TAG, "stopLocationUpdates: updates never requested, no-op.");
            return;
        }

        // It is a good practice to remove location requests when the activity is in a paused or
        // stopped state. Doing so helps battery performance and is especially
        // recommended in applications that request frequent location updates.
        mFusedLocationProviderClient.removeLocationUpdates(mLocationCallback)
                .addOnCompleteListener(this, new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        mRequestingLocationUpdates = false;
                        setButtonsEnabledState();
                    }
                });

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mRequestingLocationUpdates && checkPermissions())
            startLocationUpdates();
        else if (!checkPermissions())
            requestLocationPermissions();

        updateUI();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Remove location updates to save battery.
        stopLocationUpdates();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {

        outState.putBoolean(KEY_REQUESTING_LOCATION_UPDATES, mRequestingLocationUpdates);
        outState.putParcelable(KEY_LOCATION, mCurrentLocation);
        outState.putString(KEY_LAST_UPDATE_TIME_STRING, mLastUdateTime);

        super.onSaveInstanceState(outState);
    }

    /**
     * Return the current state of the permissions needed.
     */
    private boolean checkPermissions() {
        return ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermissions() {
        boolean shouldProvideRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.ACCESS_FINE_LOCATION);

        // Provide an additional rationale to the user. This would happen if the user denied the
        // request previously, but didn't check the "Don't ask again" checkbox.
        if (shouldProvideRationale) {
            Log.i(TAG, "Displaying permission rationale to provide additional context.");
            showSnackbar(R.string.permission_rationale,
                    android.R.string.ok, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            //Request Permission
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                    REQUEST_PERMISSIONS_REQUEST_CODE);
                        }
                    });
        } else {
            Log.i(TAG, "Requesting Permission");

            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_PERMISSIONS_REQUEST_CODE);
        }
    }

    private void showSnackbar(final int mainTextStringId, final int actionStringId, View.OnClickListener onClickListener) {
        Snackbar.make(findViewById(android.R.id.content),
                getString(mainTextStringId),
                Snackbar.LENGTH_INDEFINITE)
                .setAction(getString(actionStringId), onClickListener).show();
    }

    /**
     * Requests location updates from the FusedLocationApi. Note: we don't call this unless location
     * runtime permission has been granted.
     */
    private void startLocationUpdates() {
        // Begin by checking if the device has the necessary location settings.
        mSettingsClient.checkLocationSettings(mLocationSetiingsRequest)
                .addOnSuccessListener(this, new OnSuccessListener<LocationSettingsResponse>() {
                    @Override
                    public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                        Log.i(TAG, "All location settings are satisfied.");
                        // Request location updates without checking request permissions
                        mFusedLocationProviderClient.requestLocationUpdates(
                                mLocationRequest,
                                mLocationCallback,
                                Looper.getMainLooper());
                        updateUI();
                    }
                })
                .addOnFailureListener(this, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        int statusCode = ((ApiException) e).getStatusCode();

                        switch (statusCode) {
                            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                                Log.i(TAG, "Location settings are not satisfied. Attempting to upgrade " +
                                        "location settings ");

                                try {
                                    ResolvableApiException resolvableApiException = (ResolvableApiException) e;
                                    resolvableApiException.startResolutionForResult(MainActivity.this, REQUSET_CHECK_SETTINGS);
                                } catch (IntentSender.SendIntentException ex) {
                                    Log.i(TAG, "PendingIntent unable to execute request.");
                                    ex.printStackTrace();
                                }
                                break;
                            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                                String errorMessage = "Location settings are inadequate, and cannot be " +
                                        "fixed here. Fix in Settings.";
                                Log.e(TAG, errorMessage);
                                Toast.makeText(MainActivity.this, errorMessage,
                                        Toast.LENGTH_LONG)
                                        .show();
                                mRequestingLocationUpdates = false;
                        }
                        updateUI();
                    }
                });
    }

    /**
     * Callback received when a permissions request has been completed.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        //super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        Log.i(TAG, "onRequestPermissionsResult");
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length <= 0) {
                // If user interaction was interrupted, the permission request is cancelled and you
                // receive empty arrays.
                Log.i(TAG, "User interaction was cancelled");
            } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (mRequestingLocationUpdates) {
                    Log.i(TAG, "Permission granted, updates requested, starting updates");
                    startLocationUpdates();
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
                showSnackbar(R.string.permission_denied_explanation,
                        R.string.settings, new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                // Build intent that displays the App settings screen.
                                Intent intent = new Intent();
                                intent.setAction(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                Uri uri = Uri.fromParts("package", BuildConfig.APPLICATION_ID,
                                        null);
                                intent.setData(uri);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                            }
                        });
            }
        }
    }
}
