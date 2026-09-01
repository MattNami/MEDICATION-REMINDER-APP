package com.example.medicationreminderapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "MedicationApp.db";
    private static final int DATABASE_VERSION = 1;

    // Users Table
    public static final String TABLE_USERS = "users";
    public static final String COLUMN_USER_ID = "id";
    public static final String COLUMN_USER_NAME = "full_name";
    public static final String COLUMN_USER_EMAIL = "email";
    public static final String COLUMN_USER_PASSWORD = "password";
    public static final String COLUMN_USER_IMAGE = "profile_image_uri";

    // Medications Table
    public static final String TABLE_MEDICATIONS = "medications";
    public static final String COLUMN_MED_ID = "id";
    public static final String COLUMN_MED_NAME = "name";
    public static final String COLUMN_MED_DOSAGE = "dosage";
    public static final String COLUMN_MED_TIME = "time";
    public static final String COLUMN_MED_HOUR = "hour";
    public static final String COLUMN_MED_MINUTE = "minute";
    public static final String COLUMN_MED_IS_TAKEN = "is_taken";
    public static final String COLUMN_MED_LAST_TAKEN = "last_taken";

    private static final String CREATE_USERS_TABLE = "CREATE TABLE " + TABLE_USERS + "("
            + COLUMN_USER_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
            + COLUMN_USER_NAME + " TEXT,"
            + COLUMN_USER_EMAIL + " TEXT UNIQUE,"
            + COLUMN_USER_PASSWORD + " TEXT,"
            + COLUMN_USER_IMAGE + " TEXT"
            + ")";

    private static final String CREATE_MEDICATIONS_TABLE = "CREATE TABLE " + TABLE_MEDICATIONS + "("
            + COLUMN_MED_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
            + COLUMN_MED_NAME + " TEXT,"
            + COLUMN_MED_DOSAGE + " TEXT,"
            + COLUMN_MED_TIME + " TEXT,"
            + COLUMN_MED_HOUR + " INTEGER,"
            + COLUMN_MED_MINUTE + " INTEGER,"
            + COLUMN_MED_IS_TAKEN + " INTEGER DEFAULT 0,"
            + COLUMN_MED_LAST_TAKEN + " INTEGER DEFAULT 0"
            + ")";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_USERS_TABLE);
        db.execSQL(CREATE_MEDICATIONS_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MEDICATIONS);
        onCreate(db);
    }

    // --- User Methods ---

    public boolean addUser(String name, String email, String password) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_USER_NAME, name);
        values.put(COLUMN_USER_EMAIL, email);
        values.put(COLUMN_USER_PASSWORD, password);
        long id = db.insert(TABLE_USERS, null, values);
        return id != -1;
    }

    public boolean checkUser(String email, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] columns = {COLUMN_USER_ID};
        String selection = COLUMN_USER_EMAIL + " = ?" + " AND " + COLUMN_USER_PASSWORD + " = ?";
        String[] selectionArgs = {email, password};
        Cursor cursor = db.query(TABLE_USERS, columns, selection, selectionArgs, null, null, null);
        int count = cursor.getCount();
        cursor.close();
        return count > 0;
    }

    // --- Medication Methods ---

    public long addMedication(String name, String dosage, String time, int hour, int minute) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_MED_NAME, name);
        values.put(COLUMN_MED_DOSAGE, dosage);
        values.put(COLUMN_MED_TIME, time);
        values.put(COLUMN_MED_HOUR, hour);
        values.put(COLUMN_MED_MINUTE, minute);
        values.put(COLUMN_MED_IS_TAKEN, 0);
        return db.insert(TABLE_MEDICATIONS, null, values);
    }

    public List<Medication> getAllMedications() {
        List<Medication> medicationList = new ArrayList<>();
        String selectQuery = "SELECT * FROM " + TABLE_MEDICATIONS;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        if (cursor.moveToFirst()) {
            do {
                Medication med = new Medication(
                        cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_MED_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MED_NAME)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MED_DOSAGE)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MED_TIME)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_MED_HOUR)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_MED_MINUTE)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_MED_IS_TAKEN)) == 1,
                        cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_MED_LAST_TAKEN))
                );
                medicationList.add(med);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return medicationList;
    }

    public void updateMedicationStatus(int id, boolean isTaken, long lastTaken) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_MED_IS_TAKEN, isTaken ? 1 : 0);
        values.put(COLUMN_MED_LAST_TAKEN, lastTaken);
        db.update(TABLE_MEDICATIONS, values, COLUMN_MED_ID + " = ?", new String[]{String.valueOf(id)});
    }

    public void updateMedicationStatus(String name, boolean isTaken) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_MED_IS_TAKEN, isTaken ? 1 : 0);
        values.put(COLUMN_MED_LAST_TAKEN, System.currentTimeMillis());
        db.update(TABLE_MEDICATIONS, values, COLUMN_MED_NAME + " = ?", new String[]{name});
    }

    public void deleteMedication(String name) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_MEDICATIONS, COLUMN_MED_NAME + " = ?", new String[]{name});
    }

     public Medication getMedicationById(int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_MEDICATIONS, null, COLUMN_MED_ID + " = ?", new String[]{String.valueOf(id)}, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            Medication med = new Medication(
                    cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_MED_ID)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MED_NAME)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MED_DOSAGE)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MED_TIME)),
                    cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_MED_HOUR)),
                    cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_MED_MINUTE)),
                    cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_MED_IS_TAKEN)) == 1,
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_MED_LAST_TAKEN))
            );
            cursor.close();
            return med;
        }
        if (cursor != null) cursor.close();
        return null;
    }

    public boolean updateMedication(Medication med) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_MED_NAME, med.getName());
        values.put(COLUMN_MED_DOSAGE, med.getDosage());
        values.put(COLUMN_MED_TIME, med.getTime());
        values.put(COLUMN_MED_HOUR, med.getHour());
        values.put(COLUMN_MED_MINUTE, med.getMinute());
        int rows = db.update(TABLE_MEDICATIONS, values, COLUMN_MED_ID + " = ?", new String[]{String.valueOf(med.getId())});
        return rows > 0;
    }

    public void deleteMedication(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_MEDICATIONS, COLUMN_MED_ID + " = ?", new String[]{String.valueOf(id)});
    }

    public void updateMedication(String oldName, String name, String dosage, String time, int hour, int minute) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_MED_NAME, name);
        values.put(COLUMN_MED_DOSAGE, dosage);
        values.put(COLUMN_MED_TIME, time);
        values.put(COLUMN_MED_HOUR, hour);
        values.put(COLUMN_MED_MINUTE, minute);
        db.update(TABLE_MEDICATIONS, values, COLUMN_MED_NAME + " = ?", new String[]{oldName});
    }

    public void updateUserImage(String email, String imageUri) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_USER_IMAGE, imageUri);
        db.update(TABLE_USERS, values, COLUMN_USER_EMAIL + " = ?", new String[]{email});
    }

    public Cursor getUserDetails(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_USERS, null, COLUMN_USER_EMAIL + " = ?", new String[]{email}, null, null, null);
    }

    public void updateUser(String oldEmail, String newName, String newEmail) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_USER_NAME, newName);
        values.put(COLUMN_USER_EMAIL, newEmail);
        db.update(TABLE_USERS, values, COLUMN_USER_EMAIL + " = ?", new String[]{oldEmail});
    }
}
