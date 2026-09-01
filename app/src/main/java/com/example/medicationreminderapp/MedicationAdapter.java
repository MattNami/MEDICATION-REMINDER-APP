package com.example.medicationreminderapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;

public class MedicationAdapter extends RecyclerView.Adapter<MedicationAdapter.MedicationViewHolder> {

    private List<Medication> medicationList;
    private DatabaseHelper dbHelper;

    public MedicationAdapter(List<Medication> medicationList) {
        this.medicationList = medicationList;
    }

    @NonNull
    @Override
    public MedicationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (dbHelper == null) {
            dbHelper = new DatabaseHelper(parent.getContext());
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_medication, parent, false);
        return new MedicationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MedicationViewHolder holder, int position) {
        try {
            Medication medication = medicationList.get(position);
            holder.name.setText(medication.getName());
            holder.dosage.setText(medication.getDosage());
            holder.time.setText(medication.getTime());
            
            holder.checkBox.setOnCheckedChangeListener(null);
            holder.checkBox.setChecked(medication.isTaken());

            holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                medication.setTaken(isChecked);
                saveMedicationState(buttonView.getContext(), medication.getId(), isChecked);
            });

            holder.itemView.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(v.getContext(), EditMedicationActivity.class);
                    // Pass ID instead of position for DB operations
                    intent.putExtra("med_id", medication.getId());
                    v.getContext().startActivity(intent);
                } catch (Exception e) {
                    Log.e("MedicationAdapter", "Error starting EditMedicationActivity: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            Log.e("MedicationAdapter", "Error in onBindViewHolder: " + e.getMessage());
        }
    }

    private void saveMedicationState(Context context, int medId, boolean isTaken) {
        long lastTaken = isTaken ? System.currentTimeMillis() : 0;
        dbHelper.updateMedicationStatus(medId, isTaken, lastTaken);
        
        // Notify UI to update summary counts
        context.sendBroadcast(new Intent("com.example.medicationreminderapp.UPDATE_UI"));
    }

    @Override
    public int getItemCount() {
        return medicationList != null ? medicationList.size() : 0;
    }

    static class MedicationViewHolder extends RecyclerView.ViewHolder {
        TextView name, dosage, time;
        CheckBox checkBox;

        public MedicationViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.medName);
            dosage = itemView.findViewById(R.id.medDosage);
            time = itemView.findViewById(R.id.medTime);
            checkBox = itemView.findViewById(R.id.takeCheckbox);
        }
    }
}