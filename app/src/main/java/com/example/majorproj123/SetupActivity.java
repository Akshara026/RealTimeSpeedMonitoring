package com.example.majorproj123;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import androidx.appcompat.app.AppCompatActivity;

public class SetupActivity extends AppCompatActivity {

    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        sharedPreferences = getSharedPreferences("EmergencyPrefs", MODE_PRIVATE);

        EditText etName = findViewById(R.id.etName);
        EditText etPhone = findViewById(R.id.etPhone);
        Button btnSave = findViewById(R.id.btnSave);

        btnSave.setOnClickListener(v -> {

            String name = etName.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();

            if (name.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this,
                        "Please enter valid details",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            sharedPreferences.edit()
                    .putString("familyName", name)
                    .putString("familyNumber", phone)
                    .apply();

            Toast.makeText(this,
                    "Emergency contact saved",
                    Toast.LENGTH_SHORT).show();

            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }
}