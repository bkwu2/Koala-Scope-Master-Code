package com.example.stethoscope_app;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class RecordingView extends AppCompatActivity implements Runnable {

    int position; // find index of recording
    ImageButton mDelRecordingBtn;
    FloatingActionButton mPlayBtn;
    Button mAssignBtn, mSaveNotesBtn;


    // Defining text widgets in display
    public TextView recnameText;
    public TextView recpatnameText;
    public EditText textnotes;
    public TextView seekBarHint;
    public TextView audiototaltime;
    // Defining array list that holds recordings in database
    public ArrayList<MainActivity.Recording> getRecordings = MainActivity.Recording.createRecordingsList(20);
    // Defining spinner that holds drop down list
    public Spinner spinner;

    // Initialising mediaplayer object
    MediaPlayer mp = new MediaPlayer();
    SeekBar progressbar;
    boolean wasplaying = false;
    boolean pause_state = false;
    public Uri curUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recording_view);

        Intent retrieve_details = getIntent(); // get details from main
        // Retrieve position of recording in the database array
        // This was sent from its host activity
        position = (int) retrieve_details.getSerializableExtra("rec_index");

        // Retrieve list of recording names
        getRecordings = retrieveRecordinglist(this.getApplicationContext());
        // Retrieve list of patients
        ArrayList<MainActivity.Patient> getPatients = retrievePatientlist(this.getApplicationContext());

        // Retrieve list of all patients
        ArrayList<String> patientnames = new ArrayList<String>();
        for (int i = 0; i < getPatients.size(); i++){
            patientnames.add(getPatients.get(i).getName());
        }

        // Assigning widgets to variables
        recnameText = (TextView) findViewById(R.id.text_recname);
        textnotes = (EditText) findViewById(R.id.text_notes);
        recpatnameText = (TextView) findViewById(R.id.text_recpatname);
        seekBarHint = (TextView) findViewById(R.id.texttime);
        mAssignBtn = findViewById(R.id.assignbtn);
        mSaveNotesBtn = findViewById(R.id.savenotesbtn);
        mDelRecordingBtn = findViewById(R.id.delrecordingbtn);
        progressbar = findViewById(R.id.seekBar);
        audiototaltime = findViewById(R.id.texttotaltime);

        mPlayBtn = findViewById(R.id.playbtn);



        // Setting widget values
        recnameText.setText(getRecordings.get(position).getName());
        textnotes.setText(getRecordings.get(position).getNotes());
        recpatnameText.setText(getRecordings.get(position).getPatient());
        mPlayBtn.setImageDrawable(ContextCompat.getDrawable(RecordingView.this,android.R.drawable.ic_media_play));

        // DROP DOWN MENU
        // Creating dropdown menu
        spinner = (Spinner) findViewById(R.id.assignpat_spinner);
        // Assign list of patients to the adapter
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getApplicationContext(), android.R.layout.simple_spinner_dropdown_item, patientnames);
        // Set layout
        adapter.setDropDownViewResource((android.R.layout.simple_spinner_dropdown_item));
        spinner.setAdapter(adapter);

        // MEDIA PLAYER
        // Retrieving file
        File recStorageDir = new File(getExternalFilesDir(null), "Recordings");
        File newFile = new File(recStorageDir, getRecordings.get(position).getName());
        if (newFile.exists()){
            newFile.setReadable(true,false);
        }
        // Uniform resource identifier
        // identifies our wav file resource in this case
        curUri = Uri.fromFile(newFile);

        mp = new MediaPlayer();
        try {
            mp.setDataSource(this.getApplicationContext(),curUri);
            mp.prepare();
            long totaltime = mp.getDuration();
            int timeinsecs = (int) Math.ceil(totaltime/1000);
            Log.e("total time","" + timeinsecs);
            if (timeinsecs < 10){ audiototaltime.setText("0:0" + timeinsecs);}
            else{audiototaltime.setText("0:" + timeinsecs);}

        } catch (IOException e) {
            e.printStackTrace();
        }
        mp.reset(); // set back to idle state after we get the mediaplayer duration



        // SEEK BAR callback
        progressbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                seekBarHint.setVisibility(View.VISIBLE);

                // update time stamp
                if(mp!=null && mp.isPlaying()){
                    int pos = mp.getCurrentPosition();
                    int curtime = (int) Math.ceil(pos / 1000);
                    if (curtime < 10) {
                        seekBarHint.setText("0:0" + curtime);
                    } else {
                        seekBarHint.setText("0:" + curtime);
                    }
                }

                int x = (int) Math.ceil(progress/1000f);
                if (x != 0 && mp!=null && !mp.isPlaying()){
                    clearPlayer();
                    RecordingView.this.progressbar.setProgress(0);
                    seekBarHint.setText("0:00");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                seekBarHint.setVisibility(View.VISIBLE);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (mp != null && mp.isPlaying()){
                    // Scrub to time that seekbar stops on
                    mp.seekTo(seekBar.getProgress());
                }
            }
        });


        mPlayBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playFile();

            }
        });

        mAssignBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Save recording to a patient
                savePatienttoRecording();
            }
        });

        mSaveNotesBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Save notes to the recording
                saveNotestoRecording();
            }
        });

        mDelRecordingBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Start pop-up dialog to make sure user wants to actually delete file
                AlertDialog.Builder builder = new AlertDialog.Builder(RecordingView.this);
                builder.setMessage("Delete Recording?").setNegativeButton("No", dialogClickListener).setPositiveButton("Yes",dialogClickListener).show();

            }
        });

    }
    private void tempprep(MediaPlayer mp){
        mp = new MediaPlayer();
        try {
            mp.setDataSource(this.getApplicationContext(),curUri);
            mp.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Callback function for pop-up dialog
    DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    // Yes button clicked
                    // Delete recording from database and file
                    deleteRecording(position, getRecordings.get(position).getName());
                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    // No button clicked
                    // Do nothing
                    break;
            }
        }
    };

    private void playFile(){
        Log.e("playfile", "in playfile");
        try {
            if (mp != null && mp.isPlaying()){
                clearPlayer();
                progressbar.setProgress(0);
                wasplaying = true;
                mPlayBtn.setImageDrawable(ContextCompat.getDrawable(RecordingView.this,android.R.drawable.ic_media_play));
            }
            // If the player is empty, create a new one
            if (!wasplaying){
                if (mp == null){
                    mp = new MediaPlayer();
                }
                mPlayBtn.setImageDrawable(ContextCompat.getDrawable(RecordingView.this,android.R.drawable.ic_media_rew));
                // set data source of media player to current file
                mp.setDataSource(this.getApplicationContext(),curUri);
                // Gets details of sound file, including duration
                mp.prepare();
                // don't let the file run indefinitely
                mp.setLooping(false);
                progressbar.setMax(mp.getDuration());
                mp.start();
                new Thread(this).start();
            }
            wasplaying = false;
        }
        catch (IOException e) {
            e.printStackTrace();
        }


    }
    private void clearPlayer(){
        mp.stop();
        mp.release();
        mp = null;

    }
    @Override
    public void run() {
        int pos = mp.getCurrentPosition();
        int totaltime = mp.getDuration();
        while (mp != null && mp.isPlaying() && pos < totaltime) {
            try {
                // dont run for 1000 ms or 1s
                Thread.sleep(1000);
                // get new position in file
                pos = mp.getCurrentPosition();
            } catch (InterruptedException e) {
                return;
            } catch (Exception e){
                return;
            }
            // update progress bar
            progressbar.setProgress(pos);

        }

    }
    // Function that allows us to display temp message on screen
    private void showToast(String msg){ Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }

    private void deleteRecording(int position, String filename){
        // Remove it from the saved database
        getRecordings.remove(position);
        // Remove file from storage
        File recStorageDir = new File(getExternalFilesDir(null), "Recordings");
        File newFile = new File(recStorageDir, filename);
        if (newFile.exists()) {
            newFile.delete();
        }
        // Save database state
        saveRecordinglist(this.getApplicationContext(),getRecordings);
        // Go back home
        Intent home = new Intent(this, MainActivity.class);
        finish();
        startActivity(home);
    }
    private void saveNotestoRecording(){
        // Save notes to shared preferences
        String tempnotes = textnotes.getText().toString();
        getRecordings.get(position).setNotes(tempnotes);
        saveRecordinglist(this.getApplicationContext(),getRecordings);
        showToast("Notes Saved");
    }
    private void savePatienttoRecording(){
        // Creating a temp variable to hold our redefined recording object
        String assignname = spinner.getSelectedItem().toString().trim();
        getRecordings.get(position).setPatient(assignname);
        // Save to shared preferences
        saveRecordinglist(this.getApplicationContext(),getRecordings);
        // Notify user that patient has been assigned
        showToast("Patient Assigned");

    }
    private void saveRecordinglist(Context context, ArrayList<MainActivity.Recording> arrayList){
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = sharedPrefs.edit();
        Gson gson = new Gson();

        String json = gson.toJson(arrayList);
        editor.putString("RecList",json);
        editor.commit();
    }
    ArrayList<MainActivity.Recording> retrieveRecordinglist(Context context){
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        Gson gson = new Gson();
        String json = sharedPrefs.getString("RecList","");
        Type type = new TypeToken<List<MainActivity.Recording>>(){}.getType();
        ArrayList<MainActivity.Recording> reclist = gson.fromJson(json, type);
        return reclist;
    }

    ArrayList<MainActivity.Patient> retrievePatientlist(Context context){
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        Gson gson = new Gson();
        String json = sharedPrefs.getString("PatientData","");
        Type type = new TypeToken<List<MainActivity.Patient>>(){}.getType();
        ArrayList<MainActivity.Patient> patlist = gson.fromJson(json, type);
        return patlist;
    }

    @Override
    public void onBackPressed() {
        finish();
        saveRecordinglist(this.getApplicationContext(),getRecordings);
        super.onBackPressed();
    }

}
