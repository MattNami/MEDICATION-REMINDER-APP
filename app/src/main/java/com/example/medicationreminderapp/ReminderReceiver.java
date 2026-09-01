package com.example.medicationreminderapp;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import android.widget.Toast;
import android.content.SharedPreferences;
import java.util.List;

public class ReminderReceiver extends BroadcastReceiver {
    // START STEP: Change ID to force update of notification settings (sound/vibration)
    public static final String CHANNEL_ID = "MEDICATION_REMINDER_CHANNEL_V2";
    // END STEP
    public static final String ACTION_SNOOZE = "com.example.medicationreminderapp.ACTION_SNOOZE";
    public static final String ACTION_TAKEN = "com.example.medicationreminderapp.ACTION_TAKEN";
    private static final int SNOOZE_DELAY_MINUTES = 10; // 10 minutes snooze time
    private static final int NOTIFICATION_ID = 1001;

    @Override
    public void onReceive(Context context, Intent intent) {
        // START STEP: Acquire WakeLock immediately
        android.os.PowerManager pm = (android.os.PowerManager) context.getSystemService(Context.POWER_SERVICE);
        android.os.PowerManager.WakeLock wakeLock = null;
        if (pm != null) {
            wakeLock = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK | 
                                    android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP | 
                                    android.os.PowerManager.ON_AFTER_RELEASE, "MedicationReminder:AlertLock");
            wakeLock.acquire(60 * 1000L /*1 minute*/);
        }
        // END STEP

        String action = intent.getAction();

        // Handle snooze action
        if (ACTION_SNOOZE.equals(action)) {
            NotificationHelper.stopSound(); // STOP SOUND
            handleSnooze(context, intent);
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            return;
        }

        // Handle taken action
        if (ACTION_TAKEN.equals(action)) {
            NotificationHelper.stopSound(); // STOP SOUND
            handleTaken(context, intent);
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            return;
        }

        // Regular reminder
        String medName = intent.getStringExtra("med_name");
        String medDosage = intent.getStringExtra("med_dosage");
        int requestCode = intent.getIntExtra("request_code", 0);

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        
        // Load preferred sound from SharedPreferences
        SharedPreferences prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String selectedSound = prefs.getString("selected_sound", "Default");
        Uri alarmSound;
        
        if ("Bell".equals(selectedSound)) {
            alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        } else if ("Chime".equals(selectedSound) || "Notification".equals(selectedSound)) {
            alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        } else if ("None".equals(selectedSound)) {
            alarmSound = null;
        } else {
            // Default fallback
            alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmSound == null) {
                alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
        }

        // Create Channel for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(context);
        }

        // Create intent for opening app when notification is tapped
        Intent mainIntent = new Intent(context, MainActivity.class);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, requestCode, mainIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Create snooze intent
        Intent snoozeIntent = new Intent(context, ReminderReceiver.class);
        snoozeIntent.setAction(ACTION_SNOOZE);
        snoozeIntent.putExtra("med_name", medName);
        snoozeIntent.putExtra("med_dosage", medDosage);
        snoozeIntent.putExtra("request_code", requestCode);
        PendingIntent snoozePendingIntent = PendingIntent.getBroadcast(
                context, 
                requestCode + 1, 
                snoozeIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Create taken intent
        Intent takenIntent = new Intent(context, ReminderReceiver.class);
        takenIntent.setAction(ACTION_TAKEN);
        takenIntent.putExtra("med_name", medName);
        takenIntent.putExtra("request_code", requestCode);
        PendingIntent takenPendingIntent = PendingIntent.getBroadcast(
                context, 
                requestCode + 2, 
                takenIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Build Notification with actions
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_pill)
                .setContentTitle("Time for your Medication! ")
                .setContentText("Take " + (medName != null ? medName : "Medication") + " (" + (medDosage != null ? medDosage : "") + ")")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(pendingIntent, true) // High priority!
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_snooze, "Snooze", snoozePendingIntent)
                .addAction(R.drawable.ic_done, "Taken", takenPendingIntent)
                .setAutoCancel(true)
                .setSound(alarmSound)
                .setVibrate(new long[]{0, 1000, 500, 1000, 500, 1000})
                .setOnlyAlertOnce(true)
                .setOngoing(true);

        // Show the notification
        if (notificationManager != null) {
            notificationManager.notify(requestCode, builder.build());
        }

        // Reschedule for the next day
        rescheduleAlarm(context, medName, medDosage, requestCode);

        // Start guaranteed sound
        NotificationHelper.startSound(context, alarmSound);

        // Also trigger vibrator directly for immediate effect
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(
                    new long[]{0, 1000, 500, 1000, 500, 1000}, 
                    new int[]{0, 255, 0, 255, 0, 255}, 
                    -1
                ));
            } else {
                vibrator.vibrate(new long[]{0, 1000, 500, 1000, 500, 1000}, 0);
            }
        }
        
        // Release lock
        if (wakeLock != null && wakeLock.isHeld()) {
             // We can release here because we started the sound player which is running on a different thread
             // and holding open resources, but without a Service a long running wake lock in a Receiver is tricky.
             // We'll rely on the fact that MediaPlayer usually acquires its own wake lock if set, or the screen is now on.
             wakeLock.release();
        }
    }

    private void handleSnooze(Context context, Intent intent) {
        String medName = intent.getStringExtra("med_name");
        String medDosage = intent.getStringExtra("med_dosage");
        int requestCode = intent.getIntExtra("request_code", 0);

        // Cancel the current notification
        NotificationManagerCompat.from(context).cancel(requestCode);

        // Schedule a new alarm for SNOOZE_DELAY_MINUTES from now
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent snoozeIntent = new Intent(context, ReminderReceiver.class);
        snoozeIntent.putExtra("med_name", medName);
        snoozeIntent.putExtra("med_dosage", medDosage);
        snoozeIntent.putExtra("request_code", requestCode);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 
                requestCode, 
                snoozeIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long triggerTime = System.currentTimeMillis() + (SNOOZE_DELAY_MINUTES * 60 * 1000);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        }

        // Show a toast that the reminder was snoozed
        Toast.makeText(context, "Snoozed " + medName + " for " + SNOOZE_DELAY_MINUTES + " minutes", 
                Toast.LENGTH_SHORT).show();
                
        // Notify UI to refresh
        context.sendBroadcast(new Intent("com.example.medicationreminderapp.UPDATE_UI"));
    }

    private void handleTaken(Context context, Intent intent) {
        String medName = intent.getStringExtra("med_name");
        int requestCode = intent.getIntExtra("request_code", 0);

        // Cancel the notification
        NotificationManagerCompat.from(context).cancel(requestCode);

        // Update the medication status in SharedPreferences
        updateMedicationStatus(context, medName, true);

        // Show a confirmation notification
        showTakenConfirmation(context, medName, requestCode);
        
        // Notify UI to refresh
        context.sendBroadcast(new Intent("com.example.medicationreminderapp.UPDATE_UI"));
    }

    private void updateMedicationStatus(Context context, String medName, boolean isTaken) {
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        Log.d("ReminderReceiver", "Updating status for " + medName + " to " + isTaken + " (SQLite)");
        dbHelper.updateMedicationStatus(medName, isTaken);
    }

    private void showTakenConfirmation(Context context, String medName, int requestCode) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_check_green) // Changed icon to verify update
                .setContentTitle("Medication Taken")
                .setContentText(medName + " marked as taken")
                .setPriority(NotificationCompat.PRIORITY_HIGH) // Bump priority
                .setAutoCancel(true);
        
        // Ensure channel exists (redundant safety)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(context);
        }

        NotificationManagerCompat.from(context).notify(requestCode + 1000, builder.build());
    }

    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "Medication Reminders",
                        NotificationManager.IMPORTANCE_HIGH);
                channel.setDescription("Channel for medication alerts");

                // Configure the notification channel
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build();

                // Explicitly use the system alarm sound
                Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (alarmSound == null) {
                    alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                }

                // Important: Set hints to true
                channel.enableLights(true);
                channel.enableVibration(true);
                channel.setShowBadge(true);
                channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
                
                // Set the sound and vibration again
                channel.setSound(alarmSound, audioAttributes);
                channel.setVibrationPattern(new long[]{0, 1000, 500, 1000, 500, 1000});

                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void rescheduleAlarm(Context context, String medName, String medDosage, int requestCode) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("med_name", medName);
        intent.putExtra("med_dosage", medDosage);
        intent.putExtra("request_code", requestCode);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 
                requestCode, 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long triggerTime = System.currentTimeMillis() + AlarmManager.INTERVAL_DAY;
        
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        List<Medication> all = dbHelper.getAllMedications();
        for (Medication med : all) {
            if (med.getName().equals(medName)) {
                int hour = med.getHour();
                int minute = med.getMinute();
                
                java.util.Calendar calendar = java.util.Calendar.getInstance();
                calendar.set(java.util.Calendar.HOUR_OF_DAY, hour);
                calendar.set(java.util.Calendar.MINUTE, minute);
                calendar.set(java.util.Calendar.SECOND, 0);
                
                while (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
                    calendar.add(java.util.Calendar.DAY_OF_YEAR, 1);
                }
                triggerTime = calendar.getTimeInMillis();
                break;
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        }
    }

    // Static helper to manage sound playback globally across receiver instances
    private static class NotificationHelper {
        private static android.media.MediaPlayer mediaPlayer;

        static void startSound(Context context, Uri soundUri) {
            try {
                stopSound(); // Stop any previous sound first
                mediaPlayer = new android.media.MediaPlayer();
                mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
                mediaPlayer.setDataSource(context, soundUri);
                mediaPlayer.setLooping(true); // Loop until user interacts
                mediaPlayer.prepare();
                mediaPlayer.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        static void stopSound() {
            try {
                if (mediaPlayer != null) {
                    if (mediaPlayer.isPlaying()) {
                        mediaPlayer.stop();
                    }
                    mediaPlayer.release();
                    mediaPlayer = null;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
