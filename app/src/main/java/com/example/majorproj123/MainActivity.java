package com.example.majorproj123;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

public class MainActivity extends AppCompatActivity implements SensorEventListener {   

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private static final float FALL_THRESHOLD = 9.0f;
    private FusedLocationProviderClient fusedLocationClient;
    private boolean accidentAlertActive = false;
    private final Handler alertHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // You can keep your navigation setup too

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Check for accelerometer
        if (accelerometer == null) {
            Toast.makeText(this, "Accelerometer not available on this device.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Ask permissions for location
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        float acceleration = (float) Math.sqrt(x * x + y * y + z * z);

        if (acceleration > FALL_THRESHOLD && !accidentAlertActive) {
            showAccidentAlert();
        }
    }

    private void showAccidentAlert() {
        accidentAlertActive = true;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Accident Detected!");
        builder.setMessage("We detected a fall. Are you okay?");
        builder.setCancelable(false);

        builder.setPositiveButton("I'm Fine", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                accidentAlertActive = false;
                dialog.dismiss();
                Toast.makeText(MainActivity.this, "Glad you're okay!", Toast.LENGTH_SHORT).show();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();

        alertHandler.postDelayed(() -> {
            if (accidentAlertActive) {
                dialog.dismiss();
                handleAccidentDetected();
                accidentAlertActive = false;
            }
        }, 10000); // 10 seconds
    }

    private void handleAccidentDetected() {
        Toast.makeText(this, "Accident confirmed. Getting location...", Toast.LENGTH_LONG).show();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e("Location", "Permission not granted.");
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        double lat = location.getLatitude();
                        double lon = location.getLongitude();
                        Log.d("Location", "Lat: " + lat + " Lon: " + lon);
                        Toast.makeText(this, "Accident at: \nLat: " + lat + "\nLon: " + lon, Toast.LENGTH_LONG).show();
                        // TODO: Send location to server or emergency contact
                    } else {
                        Toast.makeText(this, "Unable to fetch location!", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
                Toast.makeText(this, "Location permission granted!", Toast.LENGTH_SHORT).show();
            } else {
                // Permission denied
                Toast.makeText(this, "Location permission denied.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}