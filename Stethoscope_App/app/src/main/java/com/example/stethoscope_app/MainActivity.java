package com.example.stethoscope_app;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    //  Image button definitions for patient list and bluetooth button
    ImageButton mPatListBtn, mBTBtn;

    // Create blank list to hold database list
    public ArrayList<MainActivity.Recording> recordings_list = new ArrayList<MainActivity.Recording>();
    // Create blank list to hold actual file list
    public ArrayList<MainActivity.Recording> getRecordings = new ArrayList<MainActivity.Recording>();

    public boolean addfile = true; // flag indicating whether or not to sync file

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getRecordings = retrieveRecordinglist(this.getApplicationContext());
        // Sync files in storage with our current recorded database
        File recStorageDir = new File(getExternalFilesDir(null), "Recordings");
        if (!recStorageDir.exists()) {
            recStorageDir.mkdirs();
        }
        // Go through each file and create a recording object for it
        for (File f : recStorageDir.listFiles()){
            if (f.isFile()){
                // Create new recording with file name
                MainActivity.Recording temp = new MainActivity.Recording(f.getName().trim());
                Log.e("Saved file name: ", temp.getName());
                // Add recording object to the recordings list
                recordings_list.add(temp);
            }
        }
        // Compare with our actual file list right now and only sync files that are available
        // For the current saved file

        for (int i = 0; i < recordings_list.size();i++){
            // Does it exist in our current database
            for (int j = 0; j < getRecordings.size();j++){
                // If our retrieved file matches at least one already registered recording, do nothing
                if (recordings_list.get(i).getName().equals(getRecordings.get(j).getName())){
                    // Don't add file
                    addfile = false;
                }
            }
            Log.e("Current iteration: ","" + i);
            // If we couldnt find the saved file in our data base, add it to the database
            // database is saved in shared preferences as "Reclist"
            if (addfile){
                getRecordings.add(recordings_list.get(i));
                Log.e("File added:", "Y");
            }
            // Reset flag for next iteration
            addfile = true;
        }

        // Save this list of retrieved recordings to shared preferences so we can use it later
        saveRecordinglist(this.getApplicationContext(),getRecordings);

        // RECYCLER VIEW - Displays list of files onboard phone storage to the display

        // Bind recycler view variable to actual widget on xml file
        RecyclerView rvRecordings = (RecyclerView) findViewById(R.id.recordView);
        // Bind array holding our files to the adapter
        RecAdapter adapter = new RecAdapter(getRecordings);
        // Counter variable and function to enable recycler view to show correct no. of items
        int curSize = adapter.getItemCount();
        adapter.notifyItemInserted(curSize);
        // Linking adapter to recycler view
        rvRecordings.setAdapter(adapter);
        // Set layout
        rvRecordings.setLayoutManager(new LinearLayoutManager(this));



        // Instantiate buttons
        mPatListBtn = findViewById(R.id.patlistbtn);
        mBTBtn = findViewById(R.id.BTbtn);
        // Open "PatientListActivity"
        mPatListBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                patientList(v);
            }
        });
        // Open "BluetoothActivity"
        mBTBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bluetooth(v);
            }
        });

    }
    // Exit app if we hit back button
    @Override
    public void onBackPressed() {
        finish();
        System.exit(1);
        super.onBackPressed();
    }

    // Function that takes us to the patient list
    public void patientList(View view) {
        Intent see_list = new Intent(this, PatientListActivity.class);
        finish();
        startActivity(see_list);
    }

    // Function that takes us to the bluetooth page
    public void bluetooth(View view){

        Intent bluetooth_open = new Intent(this, BluetoothActivity.class);
        finish();
        startActivity(bluetooth_open);
    }
    // Function to store array to 'shared preferences', cached database that retains information
    // after the app has closed
    private void saveRecordinglist(Context context, ArrayList<MainActivity.Recording> arrayList){
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = sharedPrefs.edit();
        Gson gson = new Gson();
        // shared preferences only takes strings so gson function is used to convert array list to
        // a string variable
        String json = gson.toJson(arrayList);
        // "RecList" is the ID that links to our database that stores recordings
        editor.putString("RecList",json);
        editor.commit();
    }

    // Function to retrieve recordings array from shared preferences
    ArrayList<MainActivity.Recording> retrieveRecordinglist(Context context){
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        Gson gson = new Gson();
        String json = sharedPrefs.getString("RecList","");
        Type type = new TypeToken<List<Recording>>(){}.getType();
        ArrayList<MainActivity.Recording> reclist = gson.fromJson(json, type);
        return reclist;
    }



    // Defining patient class to identify patients
    public static class Patient implements Serializable {
        private String name;
        private String age;
        private String sex;
        private String date;
        private String patnotes;
        // Function to create Patient instance
        public Patient(String new_name, String new_age, String new_sex, String new_date, String new_notes) {
            name = new_name;
            age = new_age;
            sex = new_sex;
            date = new_date;
            patnotes = new_notes;
        }
        // Creating functions to retrieve information
        public String getName() {
            return name;
        }

        public String getAges() {
            return age;
        }

        public String getSex() {
            return sex;
        }

        public String getDate() {
            return date;
        }

        public String getPatNotes() { return patnotes;}

        public void setNotes(String typenotes){
            patnotes = typenotes;
        }

        // This method creates a resizeable array of type "Patient" called createPatientsList
        public static ArrayList<MainActivity.Patient> createPatientsList(int numPatients) {
            // defining new array
            ArrayList<MainActivity.Patient> patientslist = new ArrayList<MainActivity.Patient>();
            // adding our new patient and returning our patients array list
            return patientslist;
        }
    }

    // Class for describing recordings
    public static class Recording implements Serializable {
        // Actual file name
        private String filename;
        // Name recording is assigned to
        private String patient;
        // For storing clinician's notes
        private String notes;

        // Function to create recording instance
        public Recording(String new_name) {
            filename = new_name;
        }
        // Creating functions to retrieve information
        public String getName() { return filename; }

        public String getPatient() {
            return patient;
        }

        public String getNotes() {
            return notes;
        }

        // Internal functions to set information to recording
        public void setPatient(String patientname){
            patient = patientname;
        }

        public void setNotes(String typenotes){
            notes = typenotes;
        }

        // Function to generate blank list of recordings
        public static ArrayList<MainActivity.Recording> createRecordingsList(int numRecordings) {
            // defining new array
            ArrayList<MainActivity.Recording> recslist = new ArrayList<MainActivity.Recording>();
            // adding our new patient and returning our patients array list
            return recslist;
        }
    }


    // List adapter for displaying current recordings
    public class RecAdapter extends
            RecyclerView.Adapter<RecAdapter.ViewHolder> {

        // Defining object that displays item
        public class ViewHolder extends RecyclerView.ViewHolder {
            // Holder should contain a member variable
            // for any view that will be set as you render a row
            public TextView recnameTextView;
            public Button assignButton;
            // Constructor
            public ViewHolder(View itemView) {
                // Stores the itemView in a public final member variable that can be used
                // to access the context from any ViewHolder instance
                super(itemView);
                recnameTextView = (TextView) itemView.findViewById(R.id.rec_name);
                assignButton = (Button) itemView.findViewById(R.id.assign_button);
            }
        }

        // Creating function to redefine our array input to mRecordings
        private List<MainActivity.Recording> mRecordings;
        public RecAdapter(List<MainActivity.Recording> view_recordings){
            mRecordings = view_recordings;
        }

        @NonNull
        @Override
        public MainActivity.RecAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            LayoutInflater inflater = LayoutInflater.from(context);
            // Retrieve item and attach to recycler view
            View recordingView = inflater.inflate(R.layout.item_recording, parent, false);
            // Return a new holder instance
            MainActivity.RecAdapter.ViewHolder viewHolder = new MainActivity.RecAdapter.ViewHolder(recordingView);
            return viewHolder;
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, final int position) {

            // Retrieve specific recording from list
            String currecname = getRecordings.get(position).getName();
            // Set item views based on your views and data model
            TextView textView = holder.recnameTextView;
            // Retrieve recording file name
            textView.setText(currecname);
            Button button = holder.assignButton;
            button.setText("VIEW");
            button.setEnabled(true);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent openrec = new Intent(getApplicationContext(),RecordingView.class);
                    // Send position of recording in list to the new activity being opened for reference
                    openrec.putExtra("rec_index",position);
                    startActivity(openrec);
                }
            });
        }
        // Required function within Recycler View Holder
        @Override
        public int getItemCount() {
            return mRecordings.size();
        }
    }

}
