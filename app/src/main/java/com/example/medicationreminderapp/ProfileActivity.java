package com.example.medicationreminderapp;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.widget.ImageView;
import android.widget.TextView;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.switchmaterial.SwitchMaterial;

public class ProfileActivity extends AppCompatActivity {

    private ImageView profileImage;
    private TextView soundValue;
    private TextView languageValue;
    private ActivityResultLauncher<Intent> pickImageLauncher;
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        dbHelper = new DatabaseHelper(this);
        try {
            profileImage = findViewById(R.id.profileImage);
            profileImage.setImageResource(R.drawable.ic_person_white);

            pickImageLauncher = registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        try {
                            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                                Uri imageUri = result.getData().getData();
                                if (imageUri != null) {
                                    profileImage.setImageURI(imageUri);
                                    
                                    String email = getSharedPreferences("user_prefs", MODE_PRIVATE).getString("email", "");
                                    dbHelper.updateUserImage(email, imageUri.toString());
                                }
                            }
                        } catch (Exception e) {
                            Log.e("ProfileActivity", "Error handling image selection", e);
                        }
                    });

            soundValue = findViewById(R.id.soundValue);
            languageValue = findViewById(R.id.languageValue);

            findViewById(R.id.soundContainer).setOnClickListener(v -> showSoundSelectionDialog());
            findViewById(R.id.languageContainer).setOnClickListener(v -> showLanguageSelectionDialog());

            loadUserData();
        } catch (Exception e) {
            Log.e("ProfileActivity", "Error initializing profile", e);
        }

        profileImage.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            pickImageLauncher.launch(intent);
        });

        findViewById(R.id.editProfileButton).setOnClickListener(v ->
                startActivity(new Intent(ProfileActivity.this, EditProfileActivity.class)));

        findViewById(R.id.logoutButton).setOnClickListener(v -> {
            getSharedPreferences("user_prefs", MODE_PRIVATE).edit().clear().apply();
            Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        setupVibrationSwitch();
    }

    private void loadUserData() {
        try {
            SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
            String email = prefs.getString("email", "");
            
            android.database.Cursor cursor = dbHelper.getUserDetails(email);
            if (cursor != null && cursor.moveToFirst()) {
                String fullName = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_USER_NAME));
                String imgUri = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_USER_IMAGE));
                
                ((TextView) findViewById(R.id.userNameText)).setText(fullName);
                ((TextView) findViewById(R.id.emailValue)).setText(email);

                if (imgUri != null && !imgUri.isEmpty()) {
                    profileImage.setImageURI(Uri.parse(imgUri));
                }
                cursor.close();
            }

            soundValue.setText(prefs.getString("selected_sound", "Default"));
            languageValue.setText(prefs.getString("selected_language", "English"));
        } catch (Exception e) {
            Log.e("ProfileActivity", "Error loading user data", e);
        }
    }

    private void setupVibrationSwitch() {
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        SwitchMaterial vibrateSwitch = findViewById(R.id.vibrateSwitch);
        vibrateSwitch.setChecked(prefs.getBoolean("vibrate_pref", true));
        vibrateSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (vibrator != null && vibrator.hasVibrator()) {
                    vibrator.vibrate(500);
                }
            }
            getSharedPreferences("user_prefs", MODE_PRIVATE).edit().putBoolean("vibrate_pref", isChecked).apply();
        });
    }

    private void showSoundSelectionDialog() {
        final String[] sounds = {"Default", "Chime", "Bell", "None"};
        new AlertDialog.Builder(this)
                .setTitle("Select a sound")
                .setItems(sounds, (dialog, which) -> {
                    String selectedSound = sounds[which];
                    soundValue.setText(selectedSound);
                    getSharedPreferences("user_prefs", MODE_PRIVATE).edit().putString("selected_sound", selectedSound).apply();
                    playSound(which);
                })
                .show();
    }

    private void playSound(int soundIndex) {
        Uri soundUri;
        switch (soundIndex) {
            case 0: // Default
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                break;
            case 1: // Chime
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                break;
            case 2: // Bell
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                break;
            default:
                soundUri = null;
        }

        if (soundUri != null) {
            RingtoneManager.getRingtone(getApplicationContext(), soundUri).play();
        }
    }

    private void showLanguageSelectionDialog() {
        final String[] languages = {"English", "Spanish", "French", "German", "Somali"};
        final String[] languageCodes = {"en", "es", "fr", "de", "so"};

        new AlertDialog.Builder(this)
                .setTitle("Select a language")
                .setItems(languages, (dialog, which) -> {
                    String selectedLanguage = languages[which];
                    String selectedLanguageCode = languageCodes[which];
                    languageValue.setText(selectedLanguage);
                    SharedPreferences.Editor editor = getSharedPreferences("user_prefs", MODE_PRIVATE).edit();
                    editor.putString("selected_language", selectedLanguage);
                    editor.putString("selected_language_code", selectedLanguageCode);
                    editor.apply();

                    recreate();
                    Toast.makeText(this, "Language changed to " + selectedLanguage, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            loadUserData();
        } catch (Exception e) {
            Log.e("ProfileActivity", "Error in onResume", e);
        }
    }
}
