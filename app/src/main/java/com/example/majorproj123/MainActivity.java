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
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.Toast;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import android.location.Location;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.*;
import com.google.android.gms.location.LocationRequest;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import android.location.*;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import android.telephony.SmsManager;


public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private TextView speedText;
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

    // store nearest hospital coordinates for navigation
    private double hospitalLatitude = 0;
    private double hospitalLongitude = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseDatabase.getInstance("https://accidentdetectionapp-6a919-default-rtdb.asia-southeast1.firebasedatabase.app")
                .getReference("test")
                .setValue("hello");
        FirebaseDatabase.getInstance("https://accidentdetectionapp-6a919-default-rtdb.asia-southeast1.firebasedatabase.app")
                .getReference("ping")
                .setValue("alive")
                .addOnSuccessListener(a -> Log.d("FB", "Firebase write OK"))
                .addOnFailureListener(e -> Log.e("FB", "Firebase write FAILED: " + e.getMessage()));

        SharedPreferences prefs =
                getSharedPreferences("EmergencyPrefs", MODE_PRIVATE);

        if (!prefs.contains("familyNumber")) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);
        TextView speedText;
//        speedText = findViewById(R.id.speedText);
        speedText = findViewById(R.id.speedText);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {

                float speed = location.getSpeed(); // m/s
                float speedKmh = speed * 3.6f;

                if (speedText != null) {
                    speedText.setText(String.format("%.2f km/h", speed));
                }
            }

            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override public void onProviderEnabled(String provider) {}
            @Override public void onProviderDisabled(String provider) {}
        };
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        } else {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000,
                    1,
                    locationListener
            );
        }


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

        LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_accident, null);

        TextView countdownText = view.findViewById(R.id.countdownText);
        Button btnSafe = view.findViewById(R.id.btnSafe);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .setCancelable(false)
                .create();

        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        final int[] countdown = {10};

        Handler handler = new Handler(Looper.getMainLooper());

        Runnable runnable = new Runnable() {
            @Override
            public void run() {

                if (!accidentAlertActive) return;

                countdownText.setText(String.valueOf(countdown[0]));
                countdownText.animate()
                        .scaleX(1.2f)
                        .scaleY(1.2f)
                        .setDuration(300)
                        .withEndAction(() -> {
                            countdownText.setScaleX(1f);
                            countdownText.setScaleY(1f);
                        });
                countdown[0]--;

                if (countdown[0] >= 0) {
                    handler.postDelayed(this, 1000);
                } else {

                    dialog.dismiss();

                    if (accidentAlertActive) {
                        handleAccidentDetected();
                        accidentAlertActive = false;

                        freeFallTime = 0;
                        impactTime = 0;
                        rotationTime = 0;
                        speedDropTime = 0;
                    }
                }
            }
        };

        handler.post(runnable);

        btnSafe.setOnClickListener(v -> {

            accidentAlertActive = false;
            dialog.dismiss();

            freeFallTime = 0;
            impactTime = 0;
            rotationTime = 0;
            speedDropTime = 0;
        });
    }
//    private void handleAccidentDetected() {
//
//        if (ActivityCompat.checkSelfPermission(this,
//                Manifest.permission.ACCESS_FINE_LOCATION)
//                != PackageManager.PERMISSION_GRANTED) {
//            return;
//        }
//
//        fusedLocationClient.getLastLocation()
//                .addOnSuccessListener(this, location -> {
//
//                    double lat = 0;
//                    double lon = 0;
//
//                    if (location != null) {
//                        lat = location.getLatitude();
//                        lon = location.getLongitude();
//                    } else {
//                        Toast.makeText(this,
//                                "Location unavailable. Sending SMS without exact location.",
//                                Toast.LENGTH_SHORT).show();
//                    }
//
//                    double finalLat = lat;
//                    double finalLon = lon;
//
//                    new Thread(() -> {
//
//                        String address = "Location not available";
//                        JSONArray hospitals = null;
//
//                        if (finalLat != 0 && finalLon != 0) {
//
//                            address = getAddressFromCoordinates(finalLat, finalLon);
//                            hospitals = findNearbyHospitals(finalLat, finalLon);
//                        }
//
//                        String finalAddress = address;
//                        JSONArray finalHospitals = hospitals;
//
//                        runOnUiThread(() -> {
//
//                            showAccidentResultDialog(
//                                    finalLat,
//                                    finalLon,
//                                    finalAddress,
//                                    finalHospitals
//                            );
//                            saveCrashLog(
//                                    finalLat,
//                                    finalLon,
//                                    finalAddress
//                            );
//
//
//                            sendAccidentSMS(
//                                    finalLat,
//                                    finalLon,
//                                    finalAddress
//                            );
//
//                        });
//
//                    }).start();
//                })
//                .addOnFailureListener(e -> {
//
//                    Toast.makeText(this,
//                            "Location fetch failed. Sending SMS.",
//                            Toast.LENGTH_SHORT).show();
//
//                    sendAccidentSMS(0, 0, "Location unavailable");
//                });
////        callEmergencyContact();
//    }
//private void handleAccidentDetected() {
//
//    if (ActivityCompat.checkSelfPermission(this,
//            Manifest.permission.ACCESS_FINE_LOCATION)
//            != PackageManager.PERMISSION_GRANTED) {
//        return;
//    }
//
//    fusedLocationClient.getLastLocation()
//            .addOnSuccessListener(this, location -> {
//
//                double lat = 0;
//                double lon = 0;
//
//                if (location != null) {
//                    lat = location.getLatitude();
//                    lon = location.getLongitude();
//                } else {
//                    Toast.makeText(this,
//                            "Location unavailable. Sending SMS without exact location.",
//                            Toast.LENGTH_SHORT).show();
//                }
//
//                double finalLat = lat;
//                double finalLon = lon;
//
//                new Thread(() -> {
//
//                    String address = "Location not available";
//                    JSONArray hospitals = null;
//
//                    if (finalLat != 0 && finalLon != 0) {
//
//                        address = getAddressFromCoordinates(finalLat, finalLon);
//                        hospitals = findNearbyHospitals(finalLat, finalLon);
//                    }
//
//                    String finalAddress = address;
//                    JSONArray finalHospitals = hospitals;
//
//                    runOnUiThread(() -> {
//
//                        showAccidentResultDialog(
//                                finalLat,
//                                finalLon,
//                                finalAddress,
//                                finalHospitals
//                        );
//
//                        // SAVE BLACK BOX DATA
//                        saveCrashLog(
//                                finalLat,
//                                finalLon,
//                                finalAddress
//                        );
//                        //fire base ka
//                        DatabaseReference ref = FirebaseDatabase
//                                .getInstance()
//                                .getReference("crashes");
//
//                        HashMap<String, Object> crashData = new HashMap<>();
//
//                        crashData.put("speed_before", previousSpeed);
//                        crashData.put("speed_after", currentSpeed);
//                        crashData.put("latitude", finalLat);
//                        crashData.put("longitude", finalLon);
//                        crashData.put("address", finalAddress);
//
//                        ref.push().setValue(crashData);
//
//                        // SEND SMS ALERT
//                        sendAccidentSMS(
//                                finalLat,
//                                finalLon,
//                                finalAddress
//                        );
//
//                    });
//
//                }).start();
//            })
//            .addOnFailureListener(e -> {
//
//                Toast.makeText(this,
//                        "Location fetch failed. Sending SMS.",
//                        Toast.LENGTH_SHORT).show();
//
//                // SAVE BLACK BOX EVEN IF LOCATION FAILS
//                saveCrashLog(0, 0, "Location unavailable");
//
//                sendAccidentSMS(0, 0, "Location unavailable");
//            });
//}

private void handleAccidentDetected() {

    if (ActivityCompat.checkSelfPermission(this,
            Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
        return;
    }

    fusedLocationClient.getLastLocation()
            .addOnSuccessListener(this, location -> {

                double lat = 0;
                double lon = 0;

                if (location != null) {
                    lat = location.getLatitude();
                    lon = location.getLongitude();
                } else {
                    Toast.makeText(this,
                            "Location unavailable. Sending SMS without exact location.",
                            Toast.LENGTH_SHORT).show();
                }

                double finalLat = lat;
                double finalLon = lon;

                new Thread(() -> {

                    String address = "Location not available";
                    JSONArray hospitals = null;

                    if (finalLat != 0 && finalLon != 0) {
                        address = getAddressFromCoordinates(finalLat, finalLon);
                        hospitals = findNearbyHospitals(finalLat, finalLon);
                    }

                    String finalAddress = address;
                    JSONArray finalHospitals = hospitals;

                    runOnUiThread(() -> {

                        // Show hospital dialog
                        showAccidentResultDialog(
                                finalLat,
                                finalLon,
                                finalAddress,
                                finalHospitals
                        );

                        // Save black box locally
                        saveCrashLog(finalLat, finalLon, finalAddress);

                        // Upload crash to Firebase
                        DatabaseReference ref = FirebaseDatabase
                                .getInstance("https://accidentdetectionapp-6a919-default-rtdb.asia-southeast1.firebasedatabase.app")
                                .getReference("crashes");

                        HashMap<String, Object> crashData = new HashMap<>();

                        crashData.put("speed_before", previousSpeed);
                        crashData.put("speed_after", currentSpeed);
                        crashData.put("latitude", finalLat);
                        crashData.put("longitude", finalLon);
                        crashData.put("address", finalAddress);
                        crashData.put("timestamp", System.currentTimeMillis());

                        ref.push().setValue(crashData)
                                .addOnSuccessListener(aVoid ->
                                        Log.d("FIREBASE_UPLOAD", "Crash uploaded successfully"))
                                .addOnFailureListener(e ->
                                        Log.e("FIREBASE_UPLOAD", "Upload failed", e));

                        // Send SMS alert
                        sendAccidentSMS(finalLat, finalLon, finalAddress);

                    });

                }).start();
            })
            .addOnFailureListener(e -> {

                Toast.makeText(this,
                        "Location fetch failed. Sending SMS.",
                        Toast.LENGTH_SHORT).show();

                saveCrashLog(0, 0, "Location unavailable");

                sendAccidentSMS(0, 0, "Location unavailable");
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

    private JSONArray findNearbyHospitals(double lat, double lon) {

        try {

            String query =
                    "https://overpass-api.de/api/interpreter?data=" +
                            "[out:json];node[\"amenity\"=\"hospital\"](around:50000," +
                            lat + "," + lon + ");out;";

            HttpURLConnection connection =
                    (HttpURLConnection) new URL(query).openConnection();

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

            return jsonObject.getJSONArray("elements");

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private String buildHospitalList(JSONArray hospitals) {

        if (hospitals == null || hospitals.length() == 0)
            return "No hospitals found nearby";

        StringBuilder list = new StringBuilder();

        int limit = Math.min(5, hospitals.length());

        for (int i = 0; i < limit; i++) {

            try {

                JSONObject hospital = hospitals.getJSONObject(i);
                JSONObject tags = hospital.getJSONObject("tags");

                String name = tags.optString("name", "Unnamed Hospital");

                // try multiple phone tags
                String phone = tags.optString("phone",
                        tags.optString("contact:phone", "Not available"));

                double lat = hospital.getDouble("lat");
                double lon = hospital.getDouble("lon");

                if (i == 0) {
                    hospitalLatitude = lat;
                    hospitalLongitude = lon;
                }

                list.append(i + 1)
                        .append(". ")
                        .append(name)
                        .append("\nPhone: ")
                        .append(phone)
                        .append("\n\n");

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return list.toString();
    }
    /*
     * Extracts hospital name and coordinates
     * and formats them for display
     */
    private String getHospitalInfo(JSONObject hospital) {

        try {

            JSONObject tags = hospital.getJSONObject("tags");

            String name = tags.optString("name", "Unnamed Hospital");

            double lat = hospital.getDouble("lat");
            double lon = hospital.getDouble("lon");

            // Store hospital coordinates globally for navigation
            hospitalLatitude = lat;
            hospitalLongitude = lon;

            return name;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return "Hospital info unavailable";
    }

//    private void sendAccidentSMS(double lat, double lon, String address) {
//
//        SharedPreferences prefs =
//                getSharedPreferences("EmergencyPrefs", MODE_PRIVATE);
//
//        String phone = prefs.getString("familyNumber", null);
//
//        if (phone == null) return;
//
//        try {
//
//            SmsManager smsManager = SmsManager.getDefault();
//
//            // First SMS: Immediate alert
//            String alertMessage =
//                    "ACCIDENT ALERT!\n\n" +
//                            "A possible accident has been detected.\n" +
//                            "Location details will follow shortly.";
//
//            smsManager.sendTextMessage(
//                    phone,
//                    null,
//                    alertMessage,
//                    null,
//                    null
//            );
//
//            // Second SMS: Detailed information
//            String message =
//                    "Accident Details\n\n" +
//                            "Location:\n" + address + "\n\n" +
//                            "Google Maps:\n" +
//                            "https://maps.google.com/?q=" + lat + "," + lon;
//
//            smsManager.sendTextMessage(
//                    phone,
//                    null,
//                    message,
//                    null,
//                    null
//            );
//
//            Log.d("ACCIDENT_SMS", "Accident alert and location SMS sent");
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

    private void sendAccidentSMS(double lat, double lon, String address) {

        SharedPreferences prefs =
                getSharedPreferences("EmergencyPrefs", MODE_PRIVATE);

        String phone = prefs.getString("familyNumber", null);

        if (phone == null) return;

        try {

            SmsManager smsManager =
                    SmsManager.getSmsManagerForSubscriptionId(
                            SmsManager.getDefaultSmsSubscriptionId());

            float speedDrop = previousSpeed - currentSpeed;

            // SMS 1
            String alertMessage =
                    "ACCIDENT ALERT!\n\n" +
                            "A possible accident has been detected.";

            smsManager.sendTextMessage(phone, null, alertMessage, null, null);

            // SMS 2 (BLACK BOX)
            String message =
                    "Crash Report\n\n" +
                            "Speed Before Crash: " + previousSpeed + " km/h\n" +
                            "Speed After Crash: " + currentSpeed + " km/h\n" +
                            "Speed Drop: " + speedDrop + " km/h\n\n" +
                            "Location:\n" + address + "\n\n" +
                            "Google Maps:\n" +
                            "https://maps.google.com/?q=" + lat + "," + lon;

            ArrayList<String> parts = smsManager.divideMessage(message);

            smsManager.sendMultipartTextMessage(phone, null, parts, null, null);

            Log.d("ACCIDENT_SMS", "Accident SMS + blackbox sent");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

//    private void saveCrashLog(double lat, double lon, String address) {
//
//        try {
//
//            SimpleDateFormat dateFormat =
//                    new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault());
//
//            String time = dateFormat.format(new Date());
//
//            float speedDrop = previousSpeed - currentSpeed;
//
//            String log =
//                    "\n------------------------------\n" +
//                            "ACCIDENT BLACK BOX REPORT\n" +
//                            "Time: " + time + "\n\n" +
//
//                            "Speed Before Crash: " + previousSpeed + " km/h\n" +
//                            "Speed After Crash: " + currentSpeed + " km/h\n" +
//                            "Speed Drop: " + speedDrop + " km/h\n\n" +
//
//                            "Free Fall Detected: YES\n" +
//                            "Impact Detected: YES\n" +
//                            "Rotation Detected: YES\n\n" +
//
//                            "Latitude: " + lat + "\n" +
//                            "Longitude: " + lon + "\n" +
//                            "Address: " + address + "\n" +
//
//                            "Google Maps:\n" +
//                            "https://maps.google.com/?q=" + lat + "," + lon + "\n" +
//                            "------------------------------\n";
//
//            FileOutputStream fos =
//                    openFileOutput("accident_logs.txt", Context.MODE_APPEND);
//
//            fos.write(log.getBytes());
//            fos.close();
//
//            Log.d("BLACKBOX", "Crash log saved");
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
private void saveCrashLog(double lat, double lon, String address) {

    try {

        SimpleDateFormat dateFormat =
                new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault());

        String time = dateFormat.format(new Date());

        float speedDrop = previousSpeed - currentSpeed;

        String log =
                "\n------------------------------\n" +
                        "ACCIDENT BLACK BOX REPORT\n" +
                        "Time: " + time + "\n\n" +

                        "Speed Before Crash: " + previousSpeed + " km/h\n" +
                        "Speed After Crash: " + currentSpeed + " km/h\n" +
                        "Speed Drop: " + speedDrop + " km/h\n\n" +

                        "Free Fall Detected: YES\n" +
                        "Impact Detected: YES\n" +
                        "Rotation Detected: YES\n\n" +

                        "Latitude: " + lat + "\n" +
                        "Longitude: " + lon + "\n" +
                        "Address: " + address + "\n" +

                        "Google Maps:\n" +
                        "https://maps.google.com/?q=" + lat + "," + lon + "\n" +
                        "------------------------------\n";

        FileOutputStream fos =
                openFileOutput("accident_logs.txt", Context.MODE_APPEND);

        fos.write(log.getBytes());
        fos.close();

        Log.d("BLACKBOX", "Crash log saved");

    } catch (Exception e) {
        e.printStackTrace();
    }
}
    private void callEmergencyContact() {

        SharedPreferences prefs =
                getSharedPreferences("EmergencyPrefs", MODE_PRIVATE);

        String phone = prefs.getString("familyNumber", null);

        if (phone == null) {
            Toast.makeText(this,
                    "No emergency contact saved",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        try {

            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(android.net.Uri.parse("tel:" + phone));

            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.CALL_PHONE)
                    != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CALL_PHONE}, 3);
                return;
            }

            startActivity(callIntent);

        } catch (Exception e) {

            Toast.makeText(this,
                    "Call failed: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void callHospital(String phone) {

        if (phone == null || phone.equals("Not available")) {

            Toast.makeText(this,
                    "Phone number unavailable",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Intent callIntent = new Intent(Intent.ACTION_CALL);
        callIntent.setData(Uri.parse("tel:" + phone));

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CALL_PHONE}, 6);
            return;
        }

        startActivity(callIntent);
    }
    /*
     * This method displays a dialog when an accident is confirmed.
     *
     * It shows:
     * 1. The accident address
     * 2. Nearby hospitals
     *
     * It also provides 3 options:
     * - Call emergency ambulance (108)
     * - Navigate to hospital using Google Maps
     * - Close the dialog
     */
    private void showAccidentResultDialog(double lat, double lon, String address, JSONArray hospitals) {

        LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_hospitals, null);

        TextView addressText = view.findViewById(R.id.addressText);
        LinearLayout hospitalContainer = view.findViewById(R.id.hospitalContainer);

        //  Set address
        addressText.setText("Address:\n" + address);

        //  Loop hospitals
        try {
            int limit = Math.min(5, hospitals.length());

            for (int i = 0; i < limit; i++) {

                JSONObject hospital = hospitals.getJSONObject(i);
                JSONObject tags = hospital.getJSONObject("tags");

                String name = tags.optString("name", "Unnamed Hospital");

                // Phone (with fallback)
                String phone = tags.optString("phone",
                        tags.optString("contact:phone", ""));

                if (phone == null || phone.trim().isEmpty()) {
                    String[] demoNumbers = {
                            "+919740708393"
                    };
                    phone = demoNumbers[i % demoNumbers.length];
                }

                final String finalPhone = phone;

                double hLat = hospital.getDouble("lat");
                double hLon = hospital.getDouble("lon");

                // Inflate hospital row
                View item = inflater.inflate(R.layout.hospital_item, null);

                TextView hospitalName = item.findViewById(R.id.hospitalName);
                TextView hospitalDistance = item.findViewById(R.id.hospitalDistance);
                Button callBtn = item.findViewById(R.id.callBtn);
                Button navBtn = item.findViewById(R.id.navBtn);

                hospitalName.setText(name);

                // Distance calculation
                float[] results = new float[1];
                Location.distanceBetween(lat, lon, hLat, hLon, results);
                float distanceKm = results[0] / 1000f;

                hospitalDistance.setText(String.format("%.2f km away", distanceKm));

                // CALL BUTTON
                callBtn.setOnClickListener(v -> {

                    String cleanPhone = finalPhone.replaceAll("[^0-9+]", "");

                    Intent callIntent = new Intent(Intent.ACTION_CALL);
                    callIntent.setData(Uri.parse("tel:" + cleanPhone));

                    if (ActivityCompat.checkSelfPermission(this,
                            Manifest.permission.CALL_PHONE)
                            != PackageManager.PERMISSION_GRANTED) {

                        ActivityCompat.requestPermissions(this,
                                new String[]{Manifest.permission.CALL_PHONE}, 10);
                        return;
                    }

                    startActivity(callIntent);
                });

                //  NAVIGATE BUTTON
                navBtn.setOnClickListener(v -> {

                    String uri = "google.navigation:q=" + hLat + "," + hLon;
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                    intent.setPackage("com.google.android.apps.maps");

                    startActivity(intent);
                });

                hospitalContainer.addView(item);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        // Create dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(view);

        AlertDialog dialog = builder.create();

        // Buttons from XML
        Button btnClose = view.findViewById(R.id.btnClose);
        Button btnEmergency = view.findViewById(R.id.btnEmergency);

        btnClose.setOnClickListener(v -> dialog.dismiss());

        btnEmergency.setOnClickListener(v -> {

            Intent intent = new Intent(Intent.ACTION_CALL);
            intent.setData(Uri.parse("tel:108"));

            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.CALL_PHONE)
                    != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CALL_PHONE}, 11);
                return;
            }

            startActivity(intent);
        });

        // SHOW LAST
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
//        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().setLayout(
                (int)(getResources().getDisplayMetrics().widthPixels * 0.95),
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
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
        if (requestCode == 3 &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            callEmergencyContact();
        }
        if (requestCode == 10 &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            Toast.makeText(this,
                    "Permission granted. Tap call again.",
                    Toast.LENGTH_SHORT).show();
        }
    }

}