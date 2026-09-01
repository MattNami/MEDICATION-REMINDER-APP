package com.example.medicationreminderapp;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import java.util.Calendar;
import org.json.JSONArray;
import org.json.JSONObject;

public class AddMedicationActivity extends AppCompatActivity {

    private TextInputEditText etMedName, etDosage;
    private TextView tvSelectedTime;
    private MaterialButton btnAddMedication;
    private int selectedHour = -1, selectedMinute = -1;
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_medication);

        dbHelper = new DatabaseHelper(this);
        etMedName = findViewById(R.id.etMedName);
        etDosage = findViewById(R.id.etDosage);
        tvSelectedTime = findViewById(R.id.tvSelectedTime);
        btnAddMedication = findViewById(R.id.btnAddMedication);

        findViewById(R.id.timeCard).setOnClickListener(v -> showTimePicker());

        btnAddMedication.setOnClickListener(v -> {
            String name = etMedName.getText().toString().trim();
            String dosage = etDosage.getText().toString().trim();
            String time = tvSelectedTime.getText().toString();

            if (name.isEmpty()) {
                etMedName.setError("Name is required");
                return;
            }

            if (selectedHour == -1) {
                Toast.makeText(this, "Please select a time", Toast.LENGTH_SHORT).show();
                return;
            }

            saveAndScheduleMedication(name, dosage, time);
            finish();
        });
    }

    private void saveAndScheduleMedication(String name, String dosage, String time) {
        try {
            long medId = dbHelper.addMedication(name, dosage, time, selectedHour, selectedMinute);
            
            if (medId != -1) {
                // Set flag that user has data so dummy data won't reappear (stored in prefs for simple flag)
                getSharedPreferences("user_prefs", MODE_PRIVATE).edit()
                    .putBoolean("has_medication_data", true)
                    .apply();

                scheduleAlarm(name, dosage);
                Toast.makeText(this, "Reminder set for " + name, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Failed to save medication", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void scheduleAlarm(String name, String dosage) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, selectedHour);
        calendar.set(Calendar.MINUTE, selectedMinute);
        calendar.set(Calendar.SECOND, 0);

        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, ReminderReceiver.class);
        intent.putExtra("med_name", name);
        intent.putExtra("med_dosage", dosage);

        // Using name.hashCode() for unique alarm ID
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, name.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (alarmManager != null) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
        }
    }

    private void showTimePicker() {
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(12)
                .setMinute(0)
                .setTitleText("Select Notification Time")
                .build();

        picker.show(getSupportFragmentManager(), "MATERIAL_TIME_PICKER");

        picker.addOnPositiveButtonClickListener(v -> {
            selectedHour = picker.getHour();
            selectedMinute = picker.getMinute();
            String time = String.format("%02d:%02d", selectedHour, selectedMinute);
            tvSelectedTime.setText(time);
        });
    }
}