package com.example.majorproj123;

//if (freeFallDetected && impactDetected && rotationDetected && speedDropDetected && !accidentAlertActive) conditioins are met then only accident happneed
// if (acceleration < FREE_FALL_THRESHOLD) detetcs free fall
// if (acceleration > FALL_THRESHOLD) impact detetcted //if (rotation > ROTATION_THRESHOLD) rotation detetced
// if (previousSpeed > 40 && speedDrop > SPEED_DROP_THRESHOLD) spped sudden dropped
// combining accelerometer, gyroscope, and GPS data to identify free fall, impact, rotation, and sudden speed drop, which together indicate a real crash event
// Why all 4 together are important
// If i use only ONE condition → many false alarms.
// Drop phone → free fall + impact but no speed drop → NOT accident
// Hard braking → speed drop but no free fall → NOT accident
// Shake phone → rotation but no impact → NOT accident

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import android.telephony.SmsManager;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    private final Handler alertHandler = new Handler(Looper.getMainLooper());

    // Thresholds
    private static final float FREE_FALL_THRESHOLD = 1.5f;
    private static final float IMPACT_THRESHOLD = 25.0f;
    private static final float ROTATION_THRESHOLD = 5.0f;
    private static final float SPEED_DROP_THRESHOLD = 30.0f;

    private static final long FUSION_WINDOW = 5000;

    private long freeFallTime = 0;
    private long impactTime = 0;
    private long rotationTime = 0;
    private long speedDropTime = 0;

    private boolean accidentAlertActive = false;

    private float previousSpeed = 0f;
    private float currentSpeed = 0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs =
                getSharedPreferences("EmergencyPrefs", MODE_PRIVATE);

        if (!prefs.contains("familyNumber")) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        findViewById(R.id.btnTestSMS).setOnClickListener(v -> {
            testSMSFunctionality();
        });

        findViewById(R.id.btnTestAccident).setOnClickListener(v -> {

            long now = System.currentTimeMillis();

            freeFallTime = now;
            impactTime = now;
            rotationTime = now;
            speedDropTime = now;

            checkAccidentFusion();
        });

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        if (accelerometer == null) {
            Toast.makeText(this, "Accelerometer not available.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        } else {
            startSpeedMonitoring();
        }

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.SEND_SMS}, 2);
        }
    }

    private void testSMSFunctionality() {

        SharedPreferences prefs = getSharedPreferences("EmergencyPrefs", MODE_PRIVATE);
        String phone = prefs.getString("familyNumber", null);

        if (phone == null) {
            Toast.makeText(this, "No emergency contact saved", Toast.LENGTH_SHORT).show();
            return;
        }

        try {

            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phone, null,
                    "Test SMS from Accident Detection App", null, null);

            Toast.makeText(this,
                    "Test SMS sent to " + phone,
                    Toast.LENGTH_SHORT).show();

        } catch (Exception e) {

            Toast.makeText(this,
                    "Failed: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        sensorManager.registerListener(this,
                accelerometer,
                SensorManager.SENSOR_DELAY_GAME);

        if (gyroscope != null) {
            sensorManager.registerListener(this,
                    gyroscope,
                    SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        sensorManager.unregisterListener(this);

        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            float magnitude = (float) Math.sqrt(x * x + y * y + z * z);
            float acceleration = Math.abs(magnitude - SensorManager.GRAVITY_EARTH);

            if (acceleration < FREE_FALL_THRESHOLD) {
                freeFallTime = System.currentTimeMillis();
            }

            if (acceleration > IMPACT_THRESHOLD) {
                impactTime = System.currentTimeMillis();
            }
        }

        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {

            float rx = event.values[0];
            float ry = event.values[1];
            float rz = event.values[2];

            float rotation = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);

            if (rotation > ROTATION_THRESHOLD) {
                rotationTime = System.currentTimeMillis();
            }
        }

        checkAccidentFusion();
    }

    private void checkAccidentFusion() {

        long now = System.currentTimeMillis();

        boolean validFreeFall = now - freeFallTime < FUSION_WINDOW;
        boolean validImpact = now - impactTime < FUSION_WINDOW;
        boolean validRotation = now - rotationTime < FUSION_WINDOW;
        boolean validSpeedDrop = now - speedDropTime < FUSION_WINDOW;

        if (validFreeFall &&
                validImpact &&
                validRotation &&
                validSpeedDrop &&
                !accidentAlertActive) {

            showAccidentAlert();
        }
    }

    private void showAccidentAlert() {

        accidentAlertActive = true;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Accident Detected!");
        builder.setMessage("We detected a crash. Are you okay?");
        builder.setCancelable(false);

        builder.setPositiveButton("I'm Fine", (dialog, which) -> {
            accidentAlertActive = false;
            dialog.dismiss();
        });

        AlertDialog dialog = builder.create();
        dialog.show();

        alertHandler.postDelayed(() -> {
            if (accidentAlertActive) {
                dialog.dismiss();
                handleAccidentDetected();
                accidentAlertActive = false;
            }
        }, 10000);
    }

    private void handleAccidentDetected() {

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED)
            return;

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {

                    if (location == null) {
                        Toast.makeText(this,
                                "Unable to fetch location",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    double lat = location.getLatitude();
                    double lon = location.getLongitude();

                    new Thread(() -> {

                        String address = getAddressFromCoordinates(lat, lon);
                        String hospitalList = findHospitalsInRange(lat, lon);

                        runOnUiThread(() -> {

                            showAccidentResultDialog(lat, lon, address, hospitalList);
                            sendAccidentSMS(lat, lon, address);

                        });

                    }).start();
                });
    }

    private void startSpeedMonitoring() {

        LocationRequest locationRequest =
                LocationRequest.create()
                        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                        .setInterval(2000);

        locationCallback = new LocationCallback() {

            @Override
            public void onLocationResult(LocationResult result) {

                if (result == null) return;

                for (Location location : result.getLocations()) {

                    if (location.hasSpeed() && location.getAccuracy() < 20) {

                        currentSpeed = location.getSpeed() * 3.6f;
                        float speedDrop = previousSpeed - currentSpeed;

                        if (previousSpeed > 40 &&
                                speedDrop > SPEED_DROP_THRESHOLD) {

                            speedDropTime = System.currentTimeMillis();
                        }

                        previousSpeed = currentSpeed;
                    }
                }
            }
        };

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED)
            return;

        fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper());
    }

    private String getAddressFromCoordinates(double lat, double lon) {

        try {

            String urlString =
                    "https://nominatim.openstreetmap.org/reverse?format=json"
                            + "&lat=" + lat
                            + "&lon=" + lon;

            HttpURLConnection connection =
                    (HttpURLConnection) new URL(urlString).openConnection();

            connection.setRequestProperty("User-Agent", "MajorProj123App");

            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(connection.getInputStream()));

            StringBuilder response = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null)
                response.append(line);

            reader.close();

            JSONObject jsonObject =
                    new JSONObject(response.toString());

            return jsonObject.getString("display_name");

        } catch (Exception e) {
            e.printStackTrace();
        }

        return "Address not available";
    }

    private String findHospitalsInRange(double lat, double lon) {

        try {

            String query =
                    "https://overpass-api.de/api/interpreter?data=" +
                            "[out:json];node[\"amenity\"=\"hospital\"](around:5000," +
                            lat + "," + lon + ");out;";

            HttpURLConnection connection =
                    (HttpURLConnection) new URL(query).openConnection();

            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(connection.getInputStream()));

            StringBuilder response = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null)
                response.append(line);

            reader.close();

            JSONObject jsonObject =
                    new JSONObject(response.toString());

            JSONArray elements = jsonObject.getJSONArray("elements");

            if (elements.length() == 0)
                return "No hospitals found within 5 km";

            StringBuilder hospitalList = new StringBuilder();

            for (int i = 0; i < elements.length(); i++) {

                JSONObject hospital = elements.getJSONObject(i);
                JSONObject tags = hospital.getJSONObject("tags");

                String name = tags.optString("name", "Unnamed Hospital");

                hospitalList.append("• ").append(name).append("\n");
            }

            return hospitalList.toString();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return "Error retrieving hospitals";
    }

    private void sendAccidentSMS(double lat, double lon, String address) {

        SharedPreferences prefs =
                getSharedPreferences("EmergencyPrefs", MODE_PRIVATE);

        String phone = prefs.getString("familyNumber", null);

        if (phone == null) return;

        String message =
                "ACCIDENT ALERT!\n\n" +
                        "The user may have been in an accident.\n\n" +
                        "Location:\n" + address + "\n\n" +
                        "https://maps.google.com/?q=" + lat + "," + lon;

        try {

            SmsManager smsManager = SmsManager.getDefault();

            smsManager.sendTextMessage(phone,
                    null,
                    message,
                    null,
                    null);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showAccidentResultDialog(
            double lat,
            double lon,
            String address,
            String hospitalInfo) {

        AlertDialog.Builder builder =
                new AlertDialog.Builder(this);

        builder.setTitle("Accident Confirmed");

        String message =
                "Address:\n" + address + "\n\n"
                        + "Nearby Hospitals:\n" + hospitalInfo;

        builder.setMessage(message);

        builder.setPositiveButton("Open in Maps",
                (dialog, which) -> {

                    String uri =
                            "https://maps.google.com/?q=" + lat + "," + lon;

                    startActivity(new Intent(
                            Intent.ACTION_VIEW,
                            android.net.Uri.parse(uri)));
                });

        builder.setNegativeButton("Close",
                (dialog, which) -> dialog.dismiss());

        builder.show();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults);

        if (requestCode == 1 &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            startSpeedMonitoring();
        }
    }
}