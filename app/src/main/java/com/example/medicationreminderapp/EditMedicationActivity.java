package com.example.medicationreminderapp;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
// Removed unused imports: import org.json.JSONArray; import org.json.JSONObject;

public class EditMedicationActivity extends AppCompatActivity {

    private TextInputEditText etEditMedName, etEditDosage;
    private TextView tvEditSelectedTime;
    private MaterialButton btnUpdateMedication, btnDeleteMedication;
    private int medId; // Changed from position to medId
    private int selectedHour = -1, selectedMinute = -1;

    private DatabaseHelper dbHelper; // Added DatabaseHelper

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_medication);

        dbHelper = new DatabaseHelper(this); // Initialize DatabaseHelper
        etEditMedName = findViewById(R.id.etEditMedName);
        etEditDosage = findViewById(R.id.etEditDosage);
        tvEditSelectedTime = findViewById(R.id.tvEditSelectedTime);
        btnUpdateMedication = findViewById(R.id.btnUpdateMedication);
        btnDeleteMedication = findViewById(R.id.btnDeleteMedication);

        medId = getIntent().getIntExtra("med_id", -1); // Changed from med_position to med_id

        loadMedicationData();

        findViewById(R.id.editTimeCard).setOnClickListener(v -> showTimePicker());

        btnUpdateMedication.setOnClickListener(v -> updateMedication());

        btnDeleteMedication.setOnClickListener(v -> deleteMedication());
    }

    private void loadMedicationData() {
        if (medId == -1) { // Check medId
            Toast.makeText(this, "Error: Medication ID not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Retrieve medication from database using medId
        Medication medication = dbHelper.getMedicationById(medId);
        if (medication != null) {
            etEditMedName.setText(medication.getName());
            etEditDosage.setText(medication.getDosage());
            tvEditSelectedTime.setText(medication.getTime());
            selectedHour = medication.getHour();
            selectedMinute = medication.getMinute();
        } else {
            Toast.makeText(this, "Error: Medication not found in database", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void updateMedication() {
        Log.d("EditMedication", "=== Starting updateMedication() ===");
        
        // Get input values
        String name = etEditMedName.getText().toString().trim();
        String dosage = etEditDosage.getText().toString().trim();
        String timeText = tvEditSelectedTime.getText().toString();
        
        Log.d("EditMedication", "Input - Name: '" + name + "', Dosage: '" + dosage + "', Time: '" + timeText + "'");
        Log.d("EditMedication", "Med ID: " + medId);

        // 1. Validate inputs
        if (name.isEmpty()) {
            etEditMedName.setError("Name is required");
            etEditMedName.requestFocus();
            Log.e("EditMedication", "Validation failed: Name is empty");
            return;
        }

        if (dosage.isEmpty()) {
            etEditDosage.setError("Dosage is required");
            etEditDosage.requestFocus();
            Log.e("EditMedication", "Validation failed: Dosage is empty");
            return;
        }

        // 2. Handle time - Always parse the time from the displayed text to ensure we have the latest value
        if (timeText.isEmpty() || timeText.equals("Select Time")) {
            Log.e("EditMedication", "Validation failed: No time selected");
            Toast.makeText(this, "Please select a time for the medication", Toast.LENGTH_LONG).show();
            return;
        }
        
        try {
            // Check if format is 24-hour (HH:mm) or 12-hour (hh:mm AM/PM)
            String[] timeParts = timeText.split("[: ]");
            Log.d("EditMedication", "Time parts: " + Arrays.toString(timeParts));
            
            if (timeParts.length == 2) {
                // 24-hour format: HH:mm
                selectedHour = Integer.parseInt(timeParts[0]);
                selectedMinute = Integer.parseInt(timeParts[1]);
                Log.d("EditMedication", "Parsed 24h Time - Hour: " + selectedHour + ", Minute: " + selectedMinute);
            } else if (timeParts.length >= 3) {
                // 12-hour format: hh:mm AM/PM
                int hour = Integer.parseInt(timeParts[0]);
                int minute = Integer.parseInt(timeParts[1]);
                String ampm = timeParts[2];
                
                Log.d("EditMedication", "Parsed 12h Time - Hour: " + hour + ", Minute: " + minute + ", AM/PM: " + ampm);
                
                // Convert to 24-hour format
                if (ampm.equalsIgnoreCase("PM") && hour < 12) {
                    hour += 12;
                } else if (ampm.equalsIgnoreCase("AM") && hour == 12) {
                    hour = 0;
                }
                
                selectedHour = hour;
                selectedMinute = minute;
                Log.d("EditMedication", "Converted to 24h Time - Hour: " + selectedHour + ", Minute: " + selectedMinute);
            } else {
                throw new Exception("Invalid time format");
            }
        } catch (Exception e) {
            Log.e("EditMedication", "Error parsing time: " + e.getMessage(), e);
            Toast.makeText(this, "Invalid time format. Please use HH:mm", Toast.LENGTH_LONG).show();
            return;
        }

        // 3. Update medication in database
        try {
            Medication oldMedication = dbHelper.getMedicationById(medId);
            if (oldMedication == null) {
                throw new Exception("Original medication record not found in database.");
            }
            String oldName = oldMedication.getName();

            Medication updatedMedication = new Medication(medId, name, dosage, timeText, selectedHour, selectedMinute, oldMedication.isTaken(), oldMedication.getLastTaken());
            boolean success = dbHelper.updateMedication(updatedMedication);
            
            if (!success) {
                throw new Exception("Failed to update medication data in database");
            }
            
            Log.d("EditMedication", "Medication data updated successfully in DB");
            
            // Update alarm if name changed
            if (!oldName.isEmpty() && !oldName.equals(name)) {
                Log.d("EditMedication", "Medication name changed, canceling old alarm");
                cancelAlarm(oldName);
            }
            
            // Schedule the updated alarm
            Log.d("EditMedication", "Scheduling new alarm for medication");
            scheduleAlarm(name, dosage);
            
            // Show success message and finish
            Log.d("EditMedication", "Update successful, finishing activity");
            runOnUiThread(() -> {
                Toast.makeText(this, " Medication updated successfully!", Toast.LENGTH_LONG).show();
                setResult(RESULT_OK);
                finish();
            });
            
        } catch (Exception e) {
            String errorMsg = "Error updating medication: " + e.getMessage();
            Log.e("EditMedication", errorMsg, e);
            runOnUiThread(() -> {
                Toast.makeText(this, " Failed to update: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
        }
    }

    private void deleteMedication() {
        try {
            Medication med = dbHelper.getMedicationById(medId);
            if (med != null) {
                String medName = med.getName();
                dbHelper.deleteMedication(medId);
                cancelAlarm(medName);
                Toast.makeText(this, "Medication deleted", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            }
        } catch (Exception e) {
            Log.e("EditMedication", "Error deleting medication: " + e.getMessage());
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

        // Using name.hashCode() as a unique ID for this medication's alarm
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, name.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (alarmManager != null) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
        }
    }

    private void cancelAlarm(String name) {
        Intent intent = new Intent(this, ReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, name.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }
    }

    private void showTimePicker() {
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(selectedHour != -1 ? selectedHour : 12)
                .setMinute(selectedMinute != -1 ? selectedMinute : 0)
                .setTitleText("Select Notification Time")
                .build();

        picker.show(getSupportFragmentManager(), "MATERIAL_TIME_PICKER");

        picker.addOnPositiveButtonClickListener(v -> {
            selectedHour = picker.getHour();
            selectedMinute = picker.getMinute();
            String time = String.format("%02d:%02d", selectedHour, selectedMinute);
            tvEditSelectedTime.setText(time);
        });
    }
}
