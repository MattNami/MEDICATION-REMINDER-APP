package com.example.medicationreminderapp;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Calendar;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            rescheduleAllAlarms(context);
        }
    }

    private void rescheduleAllAlarms(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String medListJson = prefs.getString("medication_list", "[]");

        try {
            JSONArray jsonArray = new JSONArray(medListJson);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject med = jsonArray.getJSONObject(i);
                String name = med.getString("name");
                String dosage = med.getString("dosage");
                
                // Keep safe, if keys don't exist, skip or use defaults if logically possible
                if (!med.has("hour") || !med.has("minute")) continue;

                int hour = med.getInt("hour");
                int minute = med.getInt("minute");

                scheduleAlarm(context, name, dosage, hour, minute);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void scheduleAlarm(Context context, String name, String dosage, int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);

        // If time has passed today, schedule for tomorrow
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("med_name", name);
        intent.putExtra("med_dosage", dosage);
        // We do not have request_code in JSON, so we re-generate it using hashcode as done in AddMedicationActivity
        intent.putExtra("request_code", name.hashCode());

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 
                name.hashCode(), 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (alarmManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
            }
        }
    }
}
