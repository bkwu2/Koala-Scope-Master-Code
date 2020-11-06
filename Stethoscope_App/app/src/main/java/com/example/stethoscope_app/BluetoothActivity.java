package com.example.stethoscope_app;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

// Class imported to make bluetooth adapter available
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
// Classes imported for discovering devices
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;

import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
// Class imported for toast notifications
// Class imported to allow for Toast widget
import android.widget.Toast;
import android.content.Intent;
import android.os.Bundle;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.sql.Time;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

// Used for retrieving time


@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class BluetoothActivity extends AppCompatActivity {
    // ---------- BLUETOOTH RELATED VARIABLES -----------------
    private static final int REQUEST_FINE_LOCATION = 0;
    private static final String DEVICE_NAME = "Stethoscope";
    // Constant that is passed to startActivityForResult() and just needs to be greater than 0
    private final static int REQUEST_ENABLE_BT = 1;  // Used to enable bluetooth
    // Follow this format for UUID configurations
    // UUID for service from Arduino as well as the associated characteristics

    // ~~~~~~~~~~~~ File transfer  ~~~~~~~~~~~~~~~~~
    private static UUID LED_SERVICE = UUID.fromString("145b9e49-e39c-4a98-a28b-7c77cbd9bf2b");
    // Stores buffer data
    private static UUID LED_CHARACTERISTIC = UUID.fromString("4cdb99ab-808c-4b0b-a85e-7a89ad7f1a67");
    // descriptor requires UUID that is specific to 0x2902
    // the rest is the base UUID for BLE devices
    private static UUID LED_CHAR_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB");
    // Acknowledgement flag to let the Arduino know we have received and written the data
    private static UUID ACK = UUID.fromString("6156a359-f7f7-4cb6-841e-a326c6cd4b22");
    // Ack flag signalling whether we have reached the end of a file
    private static UUID EndFile = UUID.fromString("4620f975-160a-48a2-a5ff-fe6ec15a1985");
    // Battery level indicator
    private static UUID BattLvl = UUID.fromString("62976934-ea9d-4bc0-b1fb-ad4ff3e1d5eb");
    // Holds current time stamp on phone when we sync device
    private static UUID Timestamp = UUID.fromString("f11af9cf-2afa-4ce3-bc28-49e2be313a2b");
    // Reads file name from arduino
    private static UUID filelist = UUID.fromString("714cdea5-b8bc-4153-9335-7c8b07e1e85a");

    // Setup for BT scanning
    private BluetoothLeScanner bluetoothLeScanner = BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner();
    private BluetoothGatt mGatt = null;

    // ----------- DATA TRANSFER BUFFERS/VARIABLES/FLAGS ---------------
    ArrayList<byte[]> filenames = new ArrayList<byte[]>(); // List of byte arrays

    int filecount = 0; // used to track how many files we have in our system
    int curfile = 0; // index used to see what file we are up to
    byte[] readbuf; // buffer used to store file data
    byte[] fileflagcheck; // buffer used to store EndFile indicator value
    byte[] ackflagcheck; // buffer used to store ACKflag value
    byte[] flag = {0x01}; // Positive flag
    byte[] flag_res = {0x00}; // Negative flag
    byte[] data; // array to store all data
    byte[] filename; // array to for file transfer flag


    // Flags local to android device that let ACK Flag know which function it is
    // working for
    boolean timeflag = true; // Flag for reading time
    boolean transflag = false; // Flag for starting data transfer
    boolean dataread = false; // Flag indicating whether file name has been read
    boolean dataflag = false; // Flag for when we are actually transferring data
    boolean complete = false; // flag for entire sequence, when we have finished syncing files, set flag

    // Used for knowing which Characteristics pertains to which indices in the characteristic array
    int[] charindex = new int[6];


    // Button references
    Button mOnBtn, mOffBtn,  mConnectBtn, mSyncBtn, mDisconnectBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);

        // Request location permissions for bluetooth
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        != PackageManager.PERMISSION_GRANTED){
            //Check Permissions Now
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},REQUEST_FINE_LOCATION);
        }

        // button definitions
        mOnBtn = findViewById(R.id.onbtn);
        mOffBtn = findViewById(R.id.offbtn);
        mConnectBtn = findViewById(R.id.connectbtn);
        mSyncBtn = findViewById(R.id.syncbtn);
        mDisconnectBtn = findViewById(R.id.disconnectbtn);


        // Creating bluetooth adapter (object that is required and is used for Bluetooth activity)
        // Requires API level 18
        final BluetoothManager btmanager;
        btmanager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothAdapter btadapter = btmanager.getAdapter();

        // Creating a toast notification to show that device doesn't have BT
        // show toast is a self-made function below that builds off of toast widget
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)){
            showToast("Device does not support Bluetooth");
        }

        // OnBtn event
        mOnBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            // Checking whether bluetooth is on, if not then send for request for user to enable
            public void onClick(View v){
                // SDK if statement required for bluetooth enabling functions
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    if (btadapter == null || !btadapter.isEnabled()) {
                        //showToast("Turning on Bluetooth ... ");
                        // Enable BT
                        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                    } else {
                        showToast("Bluetooth is already enabled");
                    }
                }
            }
        });
        mOffBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                // Disable bluetooth
                btadapter.disable();
                mGatt = null;
                // ADD THIS TO DISCONNECT BUTTON
                timeflag = true;
                showToast("Bluetooth disabled");
            }
        });
        // Find device and connect to it, will enable battery level and file transfer
        mConnectBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                // Scan for device
                scanLeDevice(true);
            }
        });
        mDisconnectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Disconnect from device
                if (mGatt!=null){
                    mGatt.disconnect();
                    mGatt = null;
                    // So we can retrieve time again later
                    timeflag = true;
                    showToast("Disconnected");
                }
                else{
                    showToast("Not Connected");
                }

            }
        });

        // Sync files from phone - can only start once connected to device
        mSyncBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mGatt!=null){
                    // Clear filenames list
                    // Read buffer
                    mGatt.readCharacteristic(mGatt.getService(LED_SERVICE).getCharacteristic(filelist));
                }
                else{
                    showToast("Please connect to device first");
                }

            }
        });

    }
    // Call back function initiated from scanning a device
    private ScanCallback leScanCallback =
            new ScanCallback(){
                @Override
                // onScanResult only appears when a BLE advertisement has been found
                public void onScanResult(int callbackType, ScanResult result){
                    super.onScanResult(callbackType, result);
                    Log.i("callbackType", String.valueOf(callbackType)); // Error logging callback type
                    Log.i("result", result.toString()); // Error logging result fo scan

                    // assign variable stethoscope to the device we scanned
                    BluetoothDevice stethoscope = null; // get device details from scan
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        stethoscope = result.getDevice();
                        String name = result.getDevice().getName();
                        Log.i("Device Name","Device name is: " + name);
                        if (DEVICE_NAME.equals(name) && (mGatt == null)){
                            connectToDevice(stethoscope); // call connect function
                            Log.d("Connect","Connect loop entered");
                        }
                    }
                }
                @Override
                public void onScanFailed(int errorCode){
                    Log.e("Scan Failed","Error Code: " + errorCode);
                }
            };

    private Handler handler = new Handler();
    // Function for scanning for devices
    // use startLeScan(UUID[], BluetoothAdapter.LeScanCallback) for specific GATT services and peripherals
    private void scanLeDevice(final boolean enable){
        if (enable){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Scan for 3 seconds
                handler.postDelayed(new Runnable(){
                    @Override
                    public void run() {
                        bluetoothLeScanner.stopScan(leScanCallback);
                    }
                }, 2000);
                bluetoothLeScanner.startScan(leScanCallback);

            }
        }
        else{
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                bluetoothLeScanner.stopScan(leScanCallback);
            }
        }
    }



    public void connectToDevice(BluetoothDevice device){
        //scanLeDevice(false); // stop scanning after first device detection;
        if (mGatt!=null){
            showToast("Device is already connected");
        }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            showToast("Device connected");
            mGatt = device.connectGatt(this, false, gattCallback); // auto connect using "true" is flaky and behaves unusually
            Log.d("connect function", "Connect attempted");
        }

    }

    // Callback function triggered from connecting to the device
    private final BluetoothGattCallback gattCallback =
            new BluetoothGattCallback() {
                // list that stores all characteristics in our service
                List<BluetoothGattCharacteristic> gattCharacteristics = new ArrayList<>();


                // Function to determine what characteristics we have read and what position
                // They sit in the list
                // Takes the characteristic list as an input
                public void setCharacteristics(List<BluetoothGattCharacteristic> gattList) {
                    for (int i = 0; i < gattList.size(); i++){
                        switch (gattList.get(i).getUuid().toString()){
                                // ledCharacteristic
                            case "4cdb99ab-808c-4b0b-a85e-7a89ad7f1a67":
                                Log.e("setCharacteristic", "ledCharacteristic set");
                                charindex[0] = i;
                                break;
                                // ACK flag
                            case "6156a359-f7f7-4cb6-841e-a326c6cd4b22":
                                Log.e("setCharacteristic", "ACK flag set");
                                charindex[1] = i;
                                break;
                                // EndFile flag
                            case "4620f975-160a-48a2-a5ff-fe6ec15a1985":
                                Log.e("setCharacteristic", "EndFile flag set");
                                charindex[2] = i;
                                break;
                                // Battery Level
                            case "62976934-ea9d-4bc0-b1fb-ad4ff3e1d5eb":
                                Log.e("setCharacteristic", "Batt Level set");
                                charindex[3] = i;
                                break;
                                // Time stmap
                            case "f11af9cf-2afa-4ce3-bc28-49e2be313a2b":
                                Log.e("setCharacteristic", "Timestamp set");
                                charindex[4] = i;
                                break;
                            case "714cdea5-b8bc-4153-9335-7c8b07e1e85a":
                                Log.e("setCharacteristic", "Filelist set");
                                charindex[5] = i;
                                break;
                            default:
                                Log.e("setCharacteristic", "Reached default");
                                break;
                        }
                    }

                }

                // Function for reading characteristics
                public void requestCharacteristics(BluetoothGatt gatt, int index){
                    if (index == 0){
                        // read data buffer
                        gatt.readCharacteristic(gattCharacteristics.get(charindex[0]));
                    }
                    if (index == 1){
                        // read ACK flag
                        gatt.readCharacteristic(gattCharacteristics.get(charindex[1]));
                    }
                    if (index == 2) {
                        // read endfile flag
                        gatt.readCharacteristic(gattCharacteristics.get(charindex[2]));
                    }
                    if (index == 3){
                        // read battery level
                        gatt.readCharacteristic(gattCharacteristics.get(charindex[3]));
                    }
                    if (index == 4){
                        // read time stmap
                        gatt.readCharacteristic(gattCharacteristics.get(charindex[4]));
                    }
                    if (index == 5) {
                        // read file list
                        gatt.readCharacteristic(gattCharacteristics.get(charindex[5]));
                    }
                }

                @Override
                // RUNS ONCE WE CONNECT TO THE GATT SERVER
                // gatt is the gatt client that enables further gatt functionality
                // status determines status of connect or disconnect operation
                // newState returns the new connection state
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {

                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        Log.e("gattCallback", "STATE_CONNECTED");
                        // Change the maximum packet size to 247
                        // Will minus 3 bytes for header
                        // Triggers onMtuChanged
                        gatt.requestMtu(247);

                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        Log.e("gattCallback", "STATE_DISCONNECTED");
                        // Reset values once we disconnect
                        timeflag = true;
                        transflag = false;
                        dataread = false;
                        dataflag = false;
                        complete = false;
                        mGatt = null;
                    } else {
                        Log.e("gattCallback", "STATE_OTHER");
                    }

                }

                @Override
                // Discovering new service provided from BLE device
                public void onServicesDiscovered(BluetoothGatt gatt, int stats) {
                    // Conditional statement required for bluetooth functionality
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        if (gattCharacteristics!=null){
                            gattCharacteristics.clear();
                        }

                        // Retrieve primary service
                        BluetoothGattService service = gatt.getService(LED_SERVICE);
                        // Retrieving all characteristics associated with the service
                        gattCharacteristics.addAll(service.getCharacteristics());
                        // 0 means we want LED_Char, 1 means ACK, 2 means EndFile, 3 means battery level, 4 means time stamp
                        // Figure out which characteristics sit where in "gattCharacteristics"
                        setCharacteristics(gattCharacteristics);
                        // Request time stamp bluetooth variable
                        requestCharacteristics(gatt, 4);
                    }
                }

                @Override
                public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        // discover services once we know that we are connected
                        // should trigger onServicesDiscovered callback
                        gatt.discoverServices();
                    }
                    super.onMtuChanged(gatt, mtu, status);
                }

                // callback reporting result of characteristic read operation
                @Override
                public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                    super.onCharacteristicRead(gatt, characteristic, status);
                    // Check if we are reading time stamp
                    // Should enter here first once connected
                    // Read the date and write it to the characteristic
                    if (characteristic.getUuid().equals(Timestamp)){
                        // If our time flag is true, retrieve current time and date on phone
                        // and write to the characteristic
                        if (timeflag){
                            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy-hh-mm-ss");
                            String format = simpleDateFormat.format(new Date());
                            characteristic.setValue(format.getBytes());
                            Log.e("timestamp read","timestamp not read");
                            // go to callback to read ACK flag, where we will write it to 1
                            // to tell the Arduino to read the data
                            gatt.writeCharacteristic(characteristic);
                        }
                        // Output line to say we arduino has received data
                        else{
                            Log.e("timestamp read","timestamp read");
                        }

                    }
                    // If we have reached end of file
                    else if (characteristic.getUuid().equals(filelist)) {
                        filename = characteristic.getValue();
                        // Only enter this if statement if we have only just read the time stamp
                        // do not enter otherwise
                        if(filename[0] == (byte)0x01){
                            // Setting filename[0] to 0x00 should trigger RUN SYNCH CHECK
                            filename[0] = (byte)0x00;
                            transflag = true;
                            characteristic.setValue(filename);
                            // Then write to ACK flag to start the file name transfer
                            // ENTER "RUN SYNCH CHECK"
                            Log.e("read filelist", "set filelist to 0x00 to start file transfer and check for ACK flag");
                            gatt.writeCharacteristic(characteristic);
                        }
                        // This runs immediately after Arduino has overwritten filename
                        else {
                            // read file name from arduino
                            filename = characteristic.getValue();
                            // 0x28 is arbitrary number that wont coincide with a number that may
                            // occur when reading the time
                            // 0x28 will signal the end of file transfer

                            // If we have finished the list, run below
                            if (filename[0] == (byte)0x28) {
                                // If we have reached the end of the list, use ACKflag to signal
                                // start of file transfer
                                transflag = false;
                                Log.e("End Transfer", "Files transferred");
                                dataflag = true;
                                gatt.writeCharacteristic(characteristic);
                            }
                            // Otherwise, get the file name and store it
                            if (transflag){
                                byte[] curfilename = characteristic.getValue();
                                Log.e("Received name", curfilename.toString());
                                // add file to list
                                addfilename(curfilename);
                                filecount++;
                                characteristic.setValue(curfilename);
                                // Set ACK flag again
                                dataread = true;
                            }
                            gatt.writeCharacteristic(characteristic);
                        }
                    }
                    // Check if we received buffer characteristic
                    else if (characteristic.getUuid().equals(LED_CHARACTERISTIC)) {
                        readbuf = characteristic.getValue();
                        data = readbuf;
                        Log.e("readCallback", "Characteristic read: " + readbuf[0] + " " + readbuf[243]);
                        //Register for further updates as notifications
                        gatt.setCharacteristicNotification(characteristic, true);
                        // Descriptor is used to enable actual /indications via the descriptor
                        // Notifications are one-way, indications require acknowledgement
                        // This is dependent on the UUID of the characteristic
                        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(LED_CHAR_DESCRIPTOR);
                        // Acknowledgement by phone is given to Arduino when onCharacteristicChanged is triggered
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(descriptor);
                    }
                    // If we see the ACK characteristic, set it to 1 and write to characteristic
                    else if (characteristic.getUuid().equals(ACK)){
                        // If we have written the time stamp to the characteristic
                        if (timeflag){
                            ackflagcheck = characteristic.getValue();
                            if (ackflagcheck[0] == 0x01){
                                // Then stop writing to the time stamp in the timestamp characteristic
                                // and tell the Arduino we have written via ACK flag reset
                                characteristic.setValue(flag_res);
                                timeflag = false;
                                Log.e("timeflag check", "ACK flag set");
                            }
                            else{
                                characteristic.setValue(flag);
                                Log.e("timeflag check", "ACK flag not set");
                            }
                            // Go back and read timestamp characteristic via write call back
                            gatt.writeCharacteristic(characteristic);
                        }
                        if (dataflag){
                            characteristic.setValue(flag);
                            Log.e("Char check", "ACK sent");
                            gatt.writeCharacteristic(characteristic);

                        }
                        // Condition where we are transferring file names
                        if (transflag){
                            ackflagcheck = characteristic.getValue();

                            // Ack flag at 1 means that the file has not yet been written
                            // as the Arduino has not yet set it to 0x00
                            if (ackflagcheck[0] == (byte)0x01){
                                // Flag ACK again so it reads again
                                characteristic.setValue(flag_res);
                                Log.e("ACK read", "File not written, read again");

                            }
                            else{
                                // ackflag at 0 means the first file has already been written to the characteristic
                                // we can then write flag to 1 to reset
                                characteristic.setValue(flag);
                                Log.e("ACK read", "File written, go store value");
                            }
                            gatt.writeCharacteristic(characteristic);
                        }

                    }
                    // If we see the end of file flag, store data to file using createWAV
                    // and start the next file transfer with file transfer flag
                    else if (characteristic.getUuid().equals(EndFile)){

                        fileflagcheck = characteristic.getValue();
                        Log.e("fileflagval", Arrays.toString(fileflagcheck));
                        if (fileflagcheck[0] == 1){
                            Log.e("curfile", String.valueOf(curfile));
                            Log.e("filecount", String.valueOf(filecount));
                            // create WAV file if we have not yet reached the end of our file list
                            // if (curfile < filecount)
                            if (curfile < filecount){
                                // 19 represents length of values that hold
                                byte[] name = Arrays.copyOf(filenames.get(curfile),23);
                                // convert to readable values
                                String str = new String(name, StandardCharsets.US_ASCII);
                                createWAV(data,str);
                                // reset EndFile to tell the system that we are ready for the next file
                                Log.e("Endfile","File created");
                                Log.e("Filename: ", str);
                                curfile++;
                            }
                            // Check whether we have created all files
                            if (curfile == filecount){
                                complete = true;
                            }

                            // Now we need to reset the flag and read the next file/buffer
                            if (!complete) {
                                dataflag = false;
                                characteristic.setValue(flag_res);
                                gatt.writeCharacteristic(characteristic);
                            }
                        }
                        else{
                            gatt.writeCharacteristic(characteristic);
                        }

                    }

                }

                // This callback should be triggered whenever our characteristic changes
                // This is enabled with notifications
                // i.e. we write a new value
                @Override
                public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                    super.onCharacteristicChanged(gatt, characteristic);
                    if (characteristic.getUuid().equals(LED_CHARACTERISTIC)) {
                        readbuf = characteristic.getValue();
                        data = Arrays.copyOf(data, data.length + readbuf.length);
                        System.arraycopy(readbuf,0, data, data.length - readbuf.length, readbuf.length);
                        Log.e("changeCallback", "Characteristic read: " + readbuf[0] + " " + readbuf[243]);
                        // Once we have retrieved the value, set ACK flag via EndFile characteristic call
                        requestCharacteristics(gatt, 2);
                    }
                }
                // Callback when we write to a value
                @Override
                public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                    super.onCharacteristicWrite(gatt, characteristic, status);
                    // If called from ACK, we want to check for end file flag
                    if (characteristic.getUuid().equals(ACK)){
                        if (dataflag){
                            // check if we are at the end of the file
                            requestCharacteristics(gatt, 2);
                        }
                        // Indicates we are now in filename transfer mode
                        // or data transfer prep mode
                        else if (transflag){
                            ackflagcheck = characteristic.getValue();
                            if (ackflagcheck[0] == 0x01){
                                requestCharacteristics(gatt, 5);
                            }
                            else{
                                // Read ack again
                                requestCharacteristics(gatt, 1);
                            }
                        }
                        else{
                            requestCharacteristics(gatt, 4);
                        }
                    }
                    // If called from end file, we want to set the ACK flag
                    if (characteristic.getUuid().equals(EndFile)){
                        if (dataflag){
                            requestCharacteristics(gatt, 1);
                        }
                        else{
                            // This should run when we have reached the end of file and want to start the next file
                            dataflag = true;
                            requestCharacteristics(gatt, 0);
                        }

                    }
                    // Once we write to file list, we want to write the next file name
                    if (characteristic.getUuid().equals(filelist)){
                        Log.e("on filelistWrite: ","Check ACK status");
                        if (transflag){
                            requestCharacteristics(gatt, 1);
                        }
                        else{
                            // If we finished file transfer, we want to read data
                            requestCharacteristics(gatt, 0);
                        }
                    }
                    if (characteristic.getUuid().equals(Timestamp)){
                        // Read the ack flag
                        Log.e("ACK call", "Read ACK flag");
                        requestCharacteristics(gatt, 1);
                    }
                }

                // Only runs during data read
                @Override
                public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                    super.onDescriptorWrite(gatt, descriptor, status);
                    requestCharacteristics(gatt, 2);
                }
            };

    // Function that creates WAV file, requires byte array input and ID input
    private void createWAV(byte[] data, String recID) {
        // Check if we can write to phone storage
        isExternalStorageWriteable();

        // Check if directory "Recordings" exists
        File recStorageDir = new File(getExternalFilesDir(null), "Recordings");
        // If it doesnt, make it a directory
        if (!recStorageDir.exists()) {
            recStorageDir.mkdirs();
        }
        File newFile = new File(recStorageDir, recID);
        // must surround output streams with try/catch
        try {
            OutputStream os = new FileOutputStream(newFile);
            // Writing data to file
            os.write(data);
            // Remember to close
            os.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // function to add file name to array to save later
    private void addfilename(byte[] curfilename){
        filenames.add(curfilename);
    }

    // Check if a volume containing external storage is available for read and write
    private boolean isExternalStorageWriteable(){
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED;
    }


    @Override
    // Go back to home activity and return the patient stored variables
    public void onBackPressed(){
        Intent home = new Intent(this, MainActivity.class);
        finish();
        startActivity(home);
    }
    // Function to simplify toast message
    private void showToast(String msg){ Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }

}