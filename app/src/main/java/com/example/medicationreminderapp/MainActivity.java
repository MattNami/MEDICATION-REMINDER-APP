package com.example.medicationreminderapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.imageview.ShapeableImageView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private RecyclerView medicationRecyclerView;
    private MedicationAdapter adapter;
    private List<Medication> medicationList;
    private TextView greetingText, dateText, tvTotalCount, tvTakenCount, tvMissedCount;
    private ShapeableImageView profileShortcut;

    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_main);

            dbHelper = new DatabaseHelper(this);
            
            greetingText = findViewById(R.id.greetingText);
            dateText = findViewById(R.id.dateText);
            tvTotalCount = findViewById(R.id.tvTotalCount);
            tvTakenCount = findViewById(R.id.tvTakenCount);  // REQUIRED - Do not delete!
            tvMissedCount = findViewById(R.id.tvMissedCount);
            profileShortcut = findViewById(R.id.profileShortcut);
            FloatingActionButton addMedFab = findViewById(R.id.addMedicationFab);
            medicationRecyclerView = findViewById(R.id.medicationRecyclerView);

            updateHeader();

            medicationRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            
            loadMedications();

            profileShortcut.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e("MainActivity", "Error opening ProfileActivity: " + e.getMessage());
                }
            });

            addMedFab.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(MainActivity.this, AddMedicationActivity.class);
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e("MainActivity", "Error opening AddMedicationActivity: " + e.getMessage());
                }
            });

            checkPermissions();
            
            // Register local receiver for updates (e.g. Snooze/Taken from notification)
            android.content.IntentFilter filter = new android.content.IntentFilter("com.example.medicationreminderapp.UPDATE_UI");
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(updateReceiver, filter);
            }

        } catch (Exception e) {
            Log.e("MainActivity", "Error in onCreate: " + e.getMessage());
        }
    }
    
    private final android.content.BroadcastReceiver updateReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, android.content.Intent intent) {
            Log.d("MainActivity", "Received UPDATE_UI broadcast, reloading medications");
            loadMedications();
        }
    };
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(updateReceiver);
        } catch (Exception e) {
            // ignore if not registered
        }
    }

    private void checkPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
        
        // Check for Exact Alarm permission (Android 12+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(android.content.Context.ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                startActivity(intent);
            }
        }
    }

    private void loadMedications() {
        Log.d("MainActivity", "=== loadMedications called (SQLite Version) ===");
        
        medicationList = dbHelper.getAllMedications();

        // If no data exists, create and save dummy data to DB
        if (medicationList.isEmpty()) {
            createAndSaveDummyData();
            medicationList = dbHelper.getAllMedications();
        }

        // Handle daily reset logic here if needed (could also be done in DB helper)
        // But for now, let's keep it consistent with previous logic
        java.util.Calendar today = java.util.Calendar.getInstance();
        int todayDay = today.get(java.util.Calendar.DAY_OF_YEAR);
        int todayYear = today.get(java.util.Calendar.YEAR);
        
        Log.d("MainActivity", "Today: day=" + todayDay + ", year=" + todayYear);

        boolean needsUpdateInDb = false;
        for (Medication med : medicationList) {
            boolean isTaken = med.isTaken();
            long lastTakenMillis = med.getLastTaken();

            if (isTaken && lastTakenMillis > 0) {
                java.util.Calendar lastTakenCal = java.util.Calendar.getInstance();
                lastTakenCal.setTimeInMillis(lastTakenMillis);
                int lastDay = lastTakenCal.get(java.util.Calendar.DAY_OF_YEAR);
                int lastYear = lastTakenCal.get(java.util.Calendar.YEAR);

                if (lastDay != todayDay || lastYear != todayYear) {
                    med.setTaken(false); // Update in memory
                    med.setLastTaken(0); // Reset timestamp
                    dbHelper.updateMedicationStatus(med.getId(), false, 0); // Update in DB
                    needsUpdateInDb = true;
                    Log.d("MainActivity", "  -> RESET: Taken on different day for " + med.getName());
                } else {
                    Log.d("MainActivity", "  -> VALID: Taken today, keeping status for " + med.getName());
                }
            } else if (isTaken && lastTakenMillis == 0) {
                // Legacy or toggle check without timestamp, reset
                med.setTaken(false);
                dbHelper.updateMedicationStatus(med.getId(), false, 0);
                needsUpdateInDb = true;
                Log.d("MainActivity", "  -> RESET: No timestamp found for " + med.getName());
            }
        }
        // No need to explicitly save the list back if individual updates are done in the loop

        adapter = new MedicationAdapter(medicationList);
        medicationRecyclerView.setAdapter(adapter);
        
        updateSummary();
    }

    private void createAndSaveDummyData() {
        dbHelper.addMedication("Amoxicillin", "500mg - 1 Pill", "08:00", 8, 0);
        dbHelper.addMedication("Lisinopril", "10mg - 1 Pill", "09:30", 9, 30);
    }

    private void updateSummary() {
        int total = medicationList.size();
        int taken = 0;
        int missed = 0;
        
        Log.d("MainActivity", "=== updateSummary called, total medications: " + total);
        
        // Get current hour and minute for comparison
        java.util.Calendar now = java.util.Calendar.getInstance();
        int currentHour = now.get(java.util.Calendar.HOUR_OF_DAY);
        int currentMinute = now.get(java.util.Calendar.MINUTE);
        int currentTimeValue = currentHour * 60 + currentMinute;

        for (Medication med : medicationList) {
            boolean isTaken = med.isTaken();
            Log.d("MainActivity", "Med: " + med.getName() + ", isTaken=" + isTaken + ", time=" + med.getTime());
            
            if (isTaken) {
                // If taken, count as taken - don't check if missed!
                taken++;
                Log.d("MainActivity", "  -> Counted as TAKEN");
            } else {
                // Only check for missed if NOT taken
                try {
                     String t = med.getTime();
                     int medH = 0; 
                     int medM = 0;
                     
                     // Handle "HH:mm" or "hh:mm AM/PM"
                     String[] parts = t.split("[: ]");
                     if (parts.length == 2) {
                         // 24-hour style
                         medH = Integer.parseInt(parts[0]);
                         medM = Integer.parseInt(parts[1]);
                     } else if (parts.length >= 3) {
                         // 12-hour style
                         int h = Integer.parseInt(parts[0]);
                         int m = Integer.parseInt(parts[1]);
                         String ampm = parts[2];
                         if (ampm.equalsIgnoreCase("PM") && h < 12) h += 12;
                         if (ampm.equalsIgnoreCase("AM") && h == 12) h = 0;
                         medH = h;
                         medM = m;
                     }
                     
                     int medTimeValue = medH * 60 + medM;
                     
                     // Use <= to include the current minute (immediate visual feedback)
                     if (medTimeValue <= currentTimeValue) {
                         missed++;
                         Log.d("MainActivity", "  -> Counted as MISSED (time passed, not taken)");
                     } else {
                         Log.d("MainActivity", "  -> PENDING (future time, not taken)");
                     }
                } catch (Exception e) {
                    // unexpected format, ignore
                    Log.e("MainActivity", "  -> Error parsing time: " + e.getMessage());
                }
            }
        }
        
        Log.d("MainActivity", "Summary: Total=" + total + ", Taken=" + taken + ", Missed=" + missed);
        
        tvTotalCount.setText(String.valueOf(total));
        tvTakenCount.setText(String.valueOf(taken));
        tvMissedCount.setText(String.valueOf(missed));
    }

    private void updateHeader() {
        try {
            SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
            String fullName = prefs.getString("full_name", "User");
            greetingText.setText("Hello, " + fullName + "!");

            String currentDate = new SimpleDateFormat("EEEE, MMM dd", Locale.getDefault()).format(new Date());
            dateText.setText(currentDate);

            String imageUriString = prefs.getString("profile_image_uri", null);
            if (imageUriString != null && !imageUriString.isEmpty()) {
                try {
                    profileShortcut.setImageURI(Uri.parse(imageUriString));
                } catch (Exception e) {
                    profileShortcut.setImageResource(R.drawable.ic_person_white);
                }
            } else {
                profileShortcut.setImageResource(R.drawable.ic_person_white);
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error in updateHeader: " + e.getMessage());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateHeader();
        loadMedications();
    }
}