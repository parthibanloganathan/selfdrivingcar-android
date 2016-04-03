package com.selfdriving.columbia.selfdriving;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;

import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;

import java.net.URISyntaxException;
import java.util.Locale;

public class Main extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {

    // GPS
    private GoogleApiClient mGoogleApiClient;
    private Location mLastLocation;
    private Location mCurrentLocation;
    private LocationRequest mLocationRequest;

    private Geocoder geocoder;

    // Compass
    private int mAzimuth = 0;

    // Sockets
    private Socket mSocket;


    protected void onStart() {
        mGoogleApiClient.connect();
        super.onStart();
    }

    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    private void sendMsg(String type, String message) {
        if (TextUtils.isEmpty(message)) {
            return;
        }
        mSocket.emit(type, message);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        System.out.println("Initialized");

        // Sockets
        try {
            mSocket = IO.socket("http://moonshot.ngrok.com/");
            mSocket.connect();
        } catch (URISyntaxException e) {}

        sendMsg("accelerometer", "hello world");

        // GPS
        /*
        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                System.out.println(location.getLatitude() + " " + location.getLongitude());
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {}

            public void onProviderEnabled(String provider) {}

            public void onProviderDisabled(String provider) {}
        };

        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        */

        geocoder = new Geocoder(getApplicationContext(), Locale.getDefault());

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }

        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(3000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(mLocationRequest);

        // Compass
        SensorManager mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Sensor mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        mSensorManager.registerListener(mSensorCompassEventListener, mSensor, SensorManager.SENSOR_DELAY_FASTEST);

        // Accelerometer
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        mSensorManager.registerListener(mSensorAccelerationEventListener , mSensor, SensorManager.SENSOR_DELAY_FASTEST);
    }

    // Compass
    private SensorEventListener mSensorCompassEventListener = new SensorEventListener() {
        float[] orientation = new float[3];
        float[] rMat = new float[9];

        public void onAccuracyChanged(Sensor sensor, int accuracy) {}

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                SensorManager.getRotationMatrixFromVector(rMat, event.values);
                mAzimuth = (int) (Math.toDegrees(SensorManager.getOrientation(rMat, orientation)[0]) + 360) % 360;
                sendMsg("direction", mAzimuth+"");
            }
        }
    };

    // Acceleration
    private SensorEventListener mSensorAccelerationEventListener = new SensorEventListener() {
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                sendMsg("acceleration", event.values[0] + " " + event.values[1] + " " + event.values[2]);
            }
        }
    };

    // GPS
    @Override
    public void onConnected(Bundle connectionHint) {
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                mGoogleApiClient);
        if (mLastLocation != null) {
            sendMsg("location", mLastLocation.getLatitude() + " " + mLastLocation.getLongitude() + "-");
        }

        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {}

    @Override
    public void onLocationChanged(Location location) {
        mCurrentLocation = location;
        double lat = mCurrentLocation.getLatitude();
        double lon = mCurrentLocation.getLongitude();
        double acc = mCurrentLocation.getAccuracy();
        sendMsg("location", lat + " " + lon + " " + acc);

        /*
        List<Address> addresses  = null;
        try {
            addresses = geocoder.getFromLocation(lat,lon,1);
            String city = addresses.get(0).getLocality();
            String state = addresses.get(0).getAdminArea();
            System.out.println(city + " " + state);
        } catch (IOException e) {
            e.printStackTrace();
        }
         */
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSocket.disconnect();
    }
}