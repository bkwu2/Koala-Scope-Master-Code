package com.example.stethoscope_app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class AddPatientActivity extends PatientListActivity {
    // Creating blank list of patients
    ArrayList<MainActivity.Patient> newPatients = MainActivity.Patient.createPatientsList(20);
    // Instantiating add button
    Button mButtonsave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d("STARTING ACTIVITY: ","ADD PATIENT");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_patient);

        mButtonsave = findViewById(R.id.buttonSave);

        // Retrieve current patient list from data base
        final ArrayList<MainActivity.Patient> temp = retrievePatientlist(this.getApplicationContext());

        // Save patient and return to patient list
        mButtonsave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveDetails(temp);

            }
        });
    }
    private void savePatientlist(Context context, ArrayList<MainActivity.Patient> arrayList){
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = sharedPrefs.edit();
        Gson gson = new Gson();

        String json = gson.toJson(arrayList);

        editor.putString("PatientData",json);
        editor.commit();
    }

    ArrayList<MainActivity.Patient> retrievePatientlist(Context context){
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        Gson gson = new Gson();
        String json = sharedPrefs.getString("PatientData","");
        Type type = new TypeToken<List<MainActivity.Patient>>(){}.getType();
        ArrayList<MainActivity.Patient> patlist = gson.fromJson(json, type);
        return patlist;
    }

    // Linked to save button above
    public void saveDetails(ArrayList<MainActivity.Patient> patlist){
        // Retrieve details from text boxes and store to string
        EditText editTextName = (EditText) findViewById(R.id.editTextName);
        EditText editTextAge = (EditText) findViewById(R.id.editTextAge);
        EditText editTextSex = (EditText) findViewById(R.id.editTextSex);
        EditText editTextDate = (EditText) findViewById(R.id.editTextDate);
        // Converting input text into strings
        String new_name = editTextName.getText().toString();
        String new_age = editTextAge.getText().toString();
        String new_sex = editTextSex.getText().toString();
        String new_date = editTextDate.getText().toString();
        String new_notes = "";

        // Add new patient to array list
        newPatients.add(0, new MainActivity.Patient(new_name,new_age,new_sex,new_date,new_notes));
        patlist.addAll(newPatients);

        // Save details to shared preferences
        savePatientlist(this.getApplicationContext(),patlist);

        // Create new intent
        Intent patlist_return = new Intent(this, PatientListActivity.class);

        // Return to the patient list activity
        startActivity(patlist_return);
    }

    @Override
    public void onBackPressed() {
        // Return on patient list without saving anything
        Intent patlist_return = new Intent(this, PatientListActivity.class);
        startActivity(patlist_return);
        super.onBackPressed();
    }
}
