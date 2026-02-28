package com.example.majorproj123;

//if (freeFallDetected && impactDetected && rotationDetected && speedDropDetected && !accidentAlertActive)  conditioins are met then only accident happneed
//if (acceleration < FREE_FALL_THRESHOLD) detetcs free fall
//if (acceleration > FALL_THRESHOLD) impact detetcted
//if (rotation > ROTATION_THRESHOLD) rotation detetced
//if (previousSpeed > 40 && speedDrop > SPEED_DROP_THRESHOLD) spped sudden dropped
//combining accelerometer, gyroscope, and GPS data to identify free fall, impact, rotation, and sudden speed drop, which together indicate a real crash event

//Why all 4 together are important
//        If i use only ONE condition → many false alarms.
//        Drop phone → free fall + impact but no speed drop → NOT accident
//        Hard braking → speed drop but no free fall → NOT accident
//        Shake phone → rotation but no impact → NOT accident

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
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONArray;
import org.json.JSONObject;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.*;

public class MainActivity extends AppCompatActivity implements SensorEventListener {


    private SensorManager sensorManager;
    private Sensor accelerometer;
    private static final float FALL_THRESHOLD = 10.0f; //it should be 25  for real life...but made 10 for testing
    private FusedLocationProviderClient fusedLocationClient;
    private boolean accidentAlertActive = false;
    private final Handler alertHandler = new Handler();


    private Sensor gyroscope;

    private static final float FREE_FALL_THRESHOLD = 2.0f;
    private static final float ROTATION_THRESHOLD = 5.0f;
    private static final float SPEED_DROP_THRESHOLD = 30.0f;

    private boolean freeFallDetected = false;
    private boolean impactDetected = false;
    private boolean rotationDetected = false;
    private boolean speedDropDetected = false;

    private float previousSpeed = 0f;
    private float currentSpeed = 0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
        // this is tester codeee
        new Handler().postDelayed(() -> {
            freeFallDetected = true;
            impactDetected = true;
            rotationDetected = true;
            speedDropDetected = true;

            checkAccidentFusion();
        }, 3000);
    }

    @Override
    protected void onResume() {
        super.onResume();

        sensorManager.registerListener(this,
                accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL);

        if (gyroscope != null) {

            sensorManager.registerListener(this,
                    gyroscope,
                    SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    // Ssensor detetction
    @Override
    public void onSensorChanged(SensorEvent event) {

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            float acceleration =
                    (float) Math.sqrt(x*x + y*y + z*z);

            // FREE FALL DETECTION
            if (acceleration < FREE_FALL_THRESHOLD) {

                freeFallDetected = true;

                Log.d("Accident", "Free fall detected");

            }

            // impct detetcion
            if (acceleration > FALL_THRESHOLD) {

                impactDetected = true;

                Log.d("Accident", "Impact detected");

            }
        }

        // gyroscope detetctt
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {

            float rx = event.values[0];
            float ry = event.values[1];
            float rz = event.values[2];

            float rotation =
                    (float) Math.sqrt(rx*rx + ry*ry + rz*rz);

            if (rotation > ROTATION_THRESHOLD) {

                rotationDetected = true;

                Log.d("Accident", "Rotation detected");

            }
        }

        checkAccidentFusion();
    }

    // SENSOR FUSION ALGORITHM
    private void checkAccidentFusion() {

        if (freeFallDetected &&
                impactDetected &&
                rotationDetected &&
                speedDropDetected &&
                !accidentAlertActive) {

            Log.d("Accident", "Accident confirmed by fusion");

            showAccidentAlert();

            resetFlags();
        }
    }

    private void resetFlags() {

        freeFallDetected = false;
        impactDetected = false;
        rotationDetected = false;
        speedDropDetected = false;

    }

    //  OG ALERT FUNCTIO
    private void showAccidentAlert() {

        accidentAlertActive = true;

        AlertDialog.Builder builder =
                new AlertDialog.Builder(this);

        builder.setTitle("Accident Detected!");
        builder.setMessage("We detected a fall. Are you okay?");
        builder.setCancelable(false);

        builder.setPositiveButton("I'm Fine",
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        accidentAlertActive = false;
                        dialog.dismiss();

                        Toast.makeText(MainActivity.this,
                                "Glad you're okay!",
                                Toast.LENGTH_SHORT).show();
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

        }, 10000);
    }
    /**
     * Displays accident result including address and nearest hospital.
     */
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
                        + "Nearest Hospital:\n"
                        + hospitalInfo;

        builder.setMessage(message);

        builder.setPositiveButton("Open in Maps",
                (dialog, which) -> {

                    String uri =
                            "https://maps.google.com/?q="
                                    + lat + "," + lon;

                    startActivity(new android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(uri)));
                });

        builder.setNegativeButton("Close",
                (dialog, which) -> dialog.dismiss());

        builder.show();
    }

    // loc thing
    /**
     * Called when accident is confirmed after sensor fusion.
     * Retrieves GPS location, converts coordinates to address,
     * finds nearest hospital, and displays result.
     */
    private void handleAccidentDetected() {

        Toast.makeText(this,
                "Accident confirmed. Getting location...",
                Toast.LENGTH_LONG).show();

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED)
            return;

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {

                    if (location != null) {

                        double lat = location.getLatitude();
                        double lon = location.getLongitude();

                        Log.d("Location",
                                "Lat: " + lat + " Lon: " + lon);

                        // Run both API calls in background thread
                        new Thread(() -> {

                            // Step 1: Reverse Geocoding (Coordinates → Address)
                            String address =
                                    getAddressFromCoordinates(lat, lon);

                            // Step 2: Find Nearest Hospital
                            String hospitalList = findHospitalsInRange(lat, lon);

                            // Step 3: Update UI safely
                            runOnUiThread(() -> {

                                showAccidentResultDialog(
                                        lat,
                                        lon,
                                        address,
                                        hospitalList
                                );
                            });

                        }).start();

                    } else {
                        Toast.makeText(this,
                                "Unable to fetch location",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }
    // speed mon
    private void startSpeedMonitoring() {

        LocationRequest locationRequest =
                LocationRequest.create()
                        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                        .setInterval(2000);

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED)
            return;

        fusedLocationClient.requestLocationUpdates(
                locationRequest,
                new LocationCallback() {

                    @Override
                    public void onLocationResult(LocationResult result) {

                        if (result == null) return;

                        for (Location location :
                                result.getLocations()) {

                            currentSpeed =
                                    location.getSpeed() * 3.6f;

                            float speedDrop =
                                    previousSpeed - currentSpeed;

                            if (previousSpeed > 40 &&
                                    speedDrop > SPEED_DROP_THRESHOLD) {

                                speedDropDetected = true;

                                Log.d("Accident",
                                        "Speed drop detected");
                            }

                            previousSpeed = currentSpeed;

                            Log.d("Speed",
                                    "Speed: " + currentSpeed);
                        }
                    }
                },
                Looper.getMainLooper());
    }

    /**
     * Converts latitude and longitude into a human-readable address
     * using OpenStreetMap Nominatim reverse geocoding API.
     */
    private String getAddressFromCoordinates(double lat, double lon) {

        try {

            String urlString =
                    "https://nominatim.openstreetmap.org/reverse?format=json"
                            + "&lat=" + lat
                            + "&lon=" + lon
                            + "&zoom=18"
                            + "&addressdetails=1";

            URL url = new URL(urlString);

            HttpURLConnection connection =
                    (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("GET");

            // Required header for Nominatim API
            connection.setRequestProperty(
                    "User-Agent",
                    "MajorProj123App");

            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    connection.getInputStream()));

            StringBuilder response =
                    new StringBuilder();

            String line;

            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            reader.close();

            JSONObject jsonObject =
                    new JSONObject(response.toString());

            return jsonObject.getString("display_name");

        } catch (Exception e) {
            e.printStackTrace();
        }

        return "Address not available";
    }


    /**
     * Returns a formatted list of all hospitals
     * within 5km of accident location.
     */
    private String findHospitalsInRange(double accidentLat,
                                        double accidentLon) {

        StringBuilder hospitalList =
                new StringBuilder();

        try {

            String query =
                    "https://overpass-api.de/api/interpreter?data=" +
                            "[out:json];node[\"amenity\"=\"hospital\"](around:50000," +
                            accidentLat + "," + accidentLon + ");out;";

            URL url = new URL(query);

            HttpURLConnection connection =
                    (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("GET");

            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    connection.getInputStream()));

            StringBuilder response =
                    new StringBuilder();

            String line;

            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            reader.close();

            JSONObject jsonObject =
                    new JSONObject(response.toString());

            JSONArray elements =
                    jsonObject.getJSONArray("elements");

            if (elements.length() == 0) {
                return "No hospitals found within 5 km";
            }

            // Loop through ALL hospitals
            for (int i = 0; i < elements.length(); i++) {

                JSONObject hospital =
                        elements.getJSONObject(i);

                double hospitalLat =
                        hospital.getDouble("lat");

                double hospitalLon =
                        hospital.getDouble("lon");

                float[] results = new float[1];

                // Calculate distance
                Location.distanceBetween(
                        accidentLat,
                        accidentLon,
                        hospitalLat,
                        hospitalLon,
                        results
                );

                double distanceKm =
                        results[0] / 1000.0;

                JSONObject tags =
                        hospital.getJSONObject("tags");

                String name =
                        tags.optString("name",
                                "Unnamed Hospital");

                hospitalList.append("• ")
                        .append(name)
                        .append(" (")
                        .append(String.format("%.2f", distanceKm))
                        .append(" km)\n");
            }

            return hospitalList.toString();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return "Error retrieving hospitals";
    }
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    // perm
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
                grantResults[0]
                        == PackageManager.PERMISSION_GRANTED) {
            startSpeedMonitoring();
        }
    }
}