package com.example.stethoscope_app;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class PatientView extends AppCompatActivity {

    private Context context;
    int position; // Used to store patient position in the array
    int posindatabase; // Used to find recording position in database

    public ArrayList<MainActivity.Recording> getPatientRecordings= MainActivity.Recording.createRecordingsList(20);;
    public ArrayList<MainActivity.Recording> getRecordings = MainActivity.Recording.createRecordingsList(20);

    public TextView nameText;
    public TextView ageText;
    public TextView sexText;
    public TextView dateText;
    public EditText textpatnotes;
    ArrayList<MainActivity.Patient> patients_view = MainActivity.Patient.createPatientsList(20);

    ImageButton mdelPatientbtn;
    Button mSaveNotesBtn2;

    // For image button (delete button)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_view);

        mdelPatientbtn = findViewById(R.id.delpatientbtn);
        mSaveNotesBtn2 = findViewById(R.id.savenotesbtn2);

        // Receive call from AddPatient
        Intent retrieve_details = getIntent();

        // Retrieve all recordings
        getRecordings = retrieveRecordinglist(this.getApplicationContext());
        // Retrieve patients list
        patients_view = retrievePatientlist(this.getApplicationContext());
        // Defining output as "Patient" object from MainActivity
        position = (int) retrieve_details.getSerializableExtra("person_index");


        // Retrieve recordings only assigned to patient
        //Log.e("Recording name",getRecordings.get(0).getPatient());
        Log.e("comparison name",patients_view.get(position).getName());
        // Store whichever recordings are assigned to patient to a new list
        // We convert the string to char array to avoid string errors
        String currentpatname = patients_view.get(position).getName();
        for (int i = 0; i < getRecordings.size(); i++){
            String temprecpatname = getRecordings.get(i).getPatient();

            if (temprecpatname != null && temprecpatname.equals(currentpatname)){
                MainActivity.Recording temp = getRecordings.get(i);
                getPatientRecordings.add(temp);
                Log.e("getPatient check","added");
            }

        }

        nameText = (TextView) findViewById(R.id.text_name);
        ageText = (TextView) findViewById(R.id.text_age);
        sexText = (TextView) findViewById(R.id.text_sex);
        dateText = (TextView) findViewById(R.id.text_dateseen);
        textpatnotes = (EditText) findViewById(R.id.text_patnotes);


        nameText.setText(patients_view.get(position).getName());
        ageText.setText(patients_view.get(position).getAges());
        sexText.setText(patients_view.get(position).getSex());
        dateText.setText(patients_view.get(position).getDate());
        textpatnotes.setText(patients_view.get(position).getPatNotes());



        // Delete patient
        mdelPatientbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(PatientView.this);
                builder.setMessage("Delete Patient?").setNegativeButton("No", dialogClickListener).setPositiveButton("Yes",dialogClickListener).show();
            }
        });

        mSaveNotesBtn2.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                saveNotestoPatient();
            }
        });

        // Create list view recycler
        RecyclerView rvRecordings = (RecyclerView) findViewById(R.id.patrecview);
        PatRecAdapter adapter = new PatRecAdapter(getPatientRecordings);
        int curSize = adapter.getItemCount();
        adapter.notifyItemInserted(curSize);
        rvRecordings.setAdapter(adapter);
        rvRecordings.setLayoutManager(new LinearLayoutManager(this));

    }
    DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    // Yes button clicked
                    deletePatient(position);
                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    // No button clicked
                    // Do nothing
                    break;
            }
        }
    };

    private void saveNotestoPatient(){
        // Save notes to shared preferences
        String tempnotes = textpatnotes.getText().toString();
        patients_view.get(position).setNotes(tempnotes);
        savePatientlist(this.getApplicationContext(),patients_view);
        showToast("Notes Saved");
    }

    private void deletePatient(int position){
        // Delete patient from list
        patients_view.remove(position);
        savePatientlist(this.getApplicationContext(),patients_view);
        Intent home = new Intent(this, PatientListActivity.class);
        startActivity(home);
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

    ArrayList<MainActivity.Recording> retrieveRecordinglist(Context context){
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        Gson gson = new Gson();
        String json = sharedPrefs.getString("RecList","");
        Type type = new TypeToken<List<MainActivity.Recording>>(){}.getType();
        ArrayList<MainActivity.Recording> reclist = gson.fromJson(json, type);
        return reclist;
    }

    // List adapter for displaying current recordings
    public class PatRecAdapter extends
            RecyclerView.Adapter<PatRecAdapter.ViewHolder> {

        public class ViewHolder extends RecyclerView.ViewHolder {
            // Holder should contain a member variable
            // for any view that will be set as you render a row
            public TextView patrecnameTextView;
            public Button patassignButton;
            // Constructor
            public ViewHolder(View itemView) {
                // Stores the itemView in a public final member variable that can be used
                // to access the context from any ViewHolder instance
                super(itemView);
                patrecnameTextView = (TextView) itemView.findViewById(R.id.prec_name);
                patassignButton = (Button) itemView.findViewById(R.id.passign_button);
            }
        }

        private List<MainActivity.Recording> mPatRecordings;
        public PatRecAdapter(List<MainActivity.Recording> view_recordings){
            mPatRecordings = view_recordings;
        }


        @Override
        public PatRecAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            LayoutInflater inflater = LayoutInflater.from(context);

            // Retrieve item and attach to recycler view
            // NOTE THAT THIS IS WHERE WE GET THE ITEM LAYOUT
            View recordingView = inflater.inflate(R.layout.item_patientviewrecordings, parent, false);

            // Return a new holder instance
            ViewHolder viewHolder = new ViewHolder(recordingView);
            return viewHolder;
        }

        @Override
        public void onBindViewHolder(PatRecAdapter.ViewHolder viewHolder, final int position) {

            // Retrieve specific recording from list
            Log.e("Position","position: " + position);
            String currecname = getPatientRecordings.get(position).getName();

            Log.e("Current rec name", currecname);
            // Set item views based on your views and data model
            TextView textView = viewHolder.patrecnameTextView;
            // Retrieve recording file name
            textView.setText(currecname);
            Button button = viewHolder.patassignButton;
            button.setText("VIEW");
            button.setEnabled(true);

            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // get position in entire recordings list, not just this patients recordings
                    // Retrieve position in get recordings
                    for (int i = 0; i < getRecordings.size();i++){
                        if (getPatientRecordings.get(position).getName().equals(getRecordings.get(i).getName())){
                            posindatabase = i;
                        }
                    }
                    Intent openrec = new Intent(getApplicationContext(),RecordingView.class);
                    openrec.putExtra("rec_index",posindatabase);
                    finish();
                    startActivity(openrec);
                }
            });
        }

        @Override
        public int getItemCount() {
            return mPatRecordings.size();
        }
    }


    @Override
    public void onBackPressed(){

        Intent home = new Intent(this, PatientListActivity.class);
        finish();
        startActivity(home);
    }

    // Function to create array that only has recordings assigned to the patient
    ArrayList<MainActivity.Recording> retrievePatientRecordings(String patientName){
        // Retrieve recordings list
        ArrayList<MainActivity.Recording> patientRecordings = MainActivity.Recording.createRecordingsList(getRecordings.size());
        Log.e("patient name retrieved",patientName);


        // Return new arraylist
        return patientRecordings;
    }
    private void saveRecordinglist(Context context, ArrayList<MainActivity.Recording> arrayList){
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = sharedPrefs.edit();
        Gson gson = new Gson();

        String json = gson.toJson(arrayList);

        editor.putString("RecList",json);
        editor.commit();
    }
    private void showToast(String msg){ Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }
}

