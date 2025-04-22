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
    private Sensor accelerometer, gyroscope;
    private FusedLocationProviderClient fusedLocationClient;
    private boolean accidentAlertActive = false;
    private final Handler alertHandler = new Handler();

    private static final float ACCEL_DELTA_THRESHOLD = 5.0f;  // sudden change
    private static final float GYRO_DELTA_THRESHOLD = 3.0f;   // sudden spin

    private float[] lastAccel = new float[3];
    private float[] lastGyro = new float[3];
    private long lastSensorTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        if (accelerometer == null || gyroscope == null) {
            Toast.makeText(this, "Required sensors not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSensorTime < 100) return; // limit updates to 10Hz
        lastSensorTime = currentTime;

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float dx = Math.abs(event.values[0] - lastAccel[0]);
            float dy = Math.abs(event.values[1] - lastAccel[1]);
            float dz = Math.abs(event.values[2] - lastAccel[2]);

            lastAccel = event.values.clone();

            if ((dx > ACCEL_DELTA_THRESHOLD || dy > ACCEL_DELTA_THRESHOLD || dz > ACCEL_DELTA_THRESHOLD)
                    && !accidentAlertActive) {
                Log.d("Motion", "Sudden acceleration change: dx=" + dx + " dy=" + dy + " dz=" + dz);
                Toast.makeText(this, "Sudden motion detected!", Toast.LENGTH_SHORT).show();
                showAccidentAlert();
            }

        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            float drx = Math.abs(event.values[0] - lastGyro[0]);
            float dry = Math.abs(event.values[1] - lastGyro[1]);
            float drz = Math.abs(event.values[2] - lastGyro[2]);

            lastGyro = event.values.clone();

            if ((drx > GYRO_DELTA_THRESHOLD || dry > GYRO_DELTA_THRESHOLD || drz > GYRO_DELTA_THRESHOLD)
                    && !accidentAlertActive) {
                Log.d("Motion", "Sudden rotation: drx=" + drx + " dry=" + dry + " drz=" + drz);
                Toast.makeText(this, "Sudden rotation detected!", Toast.LENGTH_SHORT).show();
                showAccidentAlert();
            }
        }
    }

    private void showAccidentAlert() {
        if (isFinishing() || isDestroyed()) {
            Log.w("Dialog", "Activity is finishing or destroyed. Skipping dialog.");
            return;
        }

        accidentAlertActive = true;

        runOnUiThread(() -> {
            try {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Accident Detected!");
                builder.setMessage("We detected a sudden motion. Are you okay?");
                builder.setCancelable(false);

                builder.setPositiveButton("I'm Fine", (dialog, which) -> {
                    accidentAlertActive = false;
                    dialog.dismiss();
                    Toast.makeText(MainActivity.this, "Glad you're okay!", Toast.LENGTH_SHORT).show();
                });

                AlertDialog dialog = builder.create();
                dialog.show();

                alertHandler.postDelayed(() -> {
                    if (accidentAlertActive && !isFinishing() && !isDestroyed()) {
                        dialog.dismiss();
                        handleAccidentDetected();
                        accidentAlertActive = false;
                    }
                }, 10000); // 10 seconds

            } catch (Exception e) {
                Log.e("DialogError", "Error showing dialog: " + e.getMessage());
            }
        });
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
                        // You can send this data somewhere here
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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Location permission granted!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Location permission denied.", Toast.LENGTH_SHORT).show();
        }
    }
}
