package com.example.medicationreminderapp;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class EditProfileActivity extends AppCompatActivity {

    private EditText fullNameEditText;
    private EditText emailEditText;
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        dbHelper = new DatabaseHelper(this);
        fullNameEditText = findViewById(R.id.fullNameEditText);
        emailEditText = findViewById(R.id.emailEditText);

        // Load existing user data from prefs and DB
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String currentEmail = prefs.getString("email", "");
        
        android.database.Cursor cursor = dbHelper.getUserDetails(currentEmail);
        if (cursor != null && cursor.moveToFirst()) {
            String fullName = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_USER_NAME));
            fullNameEditText.setText(fullName);
            emailEditText.setText(currentEmail);
            cursor.close();
        }

        Button saveChangesButton = findViewById(R.id.saveChangesButton);
        saveChangesButton.setOnClickListener(v -> {
            String newName = fullNameEditText.getText().toString();
            String newEmail = emailEditText.getText().toString();

            // Save updated user data to DB
            dbHelper.updateUser(currentEmail, newName, newEmail);

            // Update session SharedPreferences
            SharedPreferences.Editor editor = getSharedPreferences("user_prefs", MODE_PRIVATE).edit();
            editor.putString("email", newEmail);
            editor.apply();

            Toast.makeText(this, "Changes saved", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
