package com.example.stethoscope_app;

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
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;


public class PatientListActivity extends AppCompatActivity {
    // Creating blank array of patients
    public ArrayList<MainActivity.Patient> patients_view = MainActivity.Patient.createPatientsList(20);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d("STARTING ACTIVITY: ","PATIENT LIST");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_list);
        // Lookup the recyclerview in activity layout
        RecyclerView rvPatients = (RecyclerView) findViewById(R.id.patient_recycler_view);

        // Retrieve patient list
        patients_view = retrievePatientlist(this.getApplicationContext());

        // Create adapter passing in the sample user data
        PatientAdapter adapter = new PatientAdapter(patients_view);
        int curSize = adapter.getItemCount();
        adapter.notifyItemInserted(curSize);
        rvPatients.setAdapter(adapter);
        rvPatients.setLayoutManager(new LinearLayoutManager(this));
    }
    @Override
    public void onBackPressed(){
        // Save patient list data if we have data inside
        if (!patients_view.isEmpty()){
            savePatientlist(this.getApplicationContext(),patients_view);
        }
        // Return to home page
        Intent home = new Intent(this, MainActivity.class);
        finish();
        startActivity(home);
    }
    public void addPatient(View view) {
        savePatientlist(this.getApplicationContext(),patients_view);
        Intent intent = new Intent(this, AddPatientActivity.class);
        finish();
        startActivity(intent);
    }
    // Function to save ArrayList to shared preferences
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

    public class PatientAdapter extends
            RecyclerView.Adapter<PatientAdapter.ViewHolder> {

        public class ViewHolder extends RecyclerView.ViewHolder {

            public TextView nameTextView;
            public Button messageButton;

            public ViewHolder(View itemView) {

                super(itemView);
                nameTextView = (TextView) itemView.findViewById(R.id.patient_name);
                messageButton = (Button) itemView.findViewById(R.id.check_button);
            }

        }
        // Storing a member variable for the patients
        // Note that this is a list, not an ArrayList
        private List<MainActivity.Patient> mPatients;

        // Pass in patient array into the constructor
        public PatientAdapter(List<MainActivity.Patient> view_listpatients){
            mPatients = view_listpatients;
        }



        // Inflates the item layout and creates the holder
        @Override
        public PatientAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            LayoutInflater inflater = LayoutInflater.from(context);

            // Inflate the custom layout
            View patientView = inflater.inflate(R.layout.item_patient, parent, false);

            // Return a new holder instance
            ViewHolder viewHolder = new ViewHolder(patientView);
            return viewHolder;
        }

        // Set view attributes based on data
        // Involves populating data into the item through holder
        // note our inputs to the method
        @Override
        public void onBindViewHolder(PatientAdapter.ViewHolder viewHolder, final int position) {
            // Get the data model based on position
            final MainActivity.Patient temp_patient = mPatients.get(position);

            // Set item views based on your views and data model
            TextView textView = viewHolder.nameTextView;
            textView.setText(temp_patient.getName());
            Button button = viewHolder.messageButton;
            button.setText("VIEW PATIENT");
            button.setEnabled(true);
            Log.d("PATIENT VALUE:",patients_view.get(position).getName());
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent view_details = new Intent(getApplicationContext(),PatientView.class);
                    view_details.putExtra("person_index",position);
                    startActivity(view_details);
                }

            });

        }
        // determines the number of items
        // Returns the total count of items in the list
        @Override
        public int getItemCount() {
            if (mPatients!=null){
                return mPatients.size();
            }
            return 0;
        }


    }





}


