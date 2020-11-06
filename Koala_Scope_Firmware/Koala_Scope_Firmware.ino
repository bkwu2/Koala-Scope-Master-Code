// LIBRARIES
#include <ArduinoBLE.h>
// Serial Peripheral Interface required for communicating with the SD card
#include <SPI.h>
// Extended library for controlling SD card functionality
#include <SdFat.h>
// must  be included to avoid struct stat error
// file modifier to nrf microcontroller also required, see design log
#include <sys/stat.h>
// library for microcontroller
#include <nrf.h>
// library for checking digit vs character in string
#include <ctype.h>
// standard math library
#include <math.h>


/*--------------------- VARIABLES FOR SOUND CAPTURE  ------------------------*/
// PIN/CHANNEL ASSIGNMENTS
// SD Card
#define CS    10
#define MOSI  11
#define MISO  12
#define CLK   13

// Button & LEDs
#define RECBUTTON 3
#define RECLED 2
#define BTBUTTON 5
#define BTLED 4

// Channel assignments for PPI
#define TIMER3_PPI 0
#define TIMER3_RELOAD_CC 5

// SD card function class redefinition
SdFat sd;
SdFile rec;

// Header data
unsigned long recfileSize = 0L;
unsigned long waveChunk = 16;
unsigned int waveType = 1;
unsigned int numChannels = 1;
unsigned long sampleRate = 10000;
unsigned long bytesPerSec = 10000;
unsigned int blockAlign = 1;
unsigned int bitsPerSample = 8;
unsigned long dataSize = 0L;
long recByteCount = 0L;
long recByteSaved = 0L;


// Sample storage buffer
// 1024 * 3 for each sample
byte buf00[4096]; // buffer for storing to text file
byte buf01[4096]; // 2nd buffer for text file storage
byte readbuf[4096]; // For reading data from .txt file and writing to WAV file

// Individual buffers to write to WAV file
byte piezobuf[1024];
byte elec1buf[1024];
byte elec2buf[1024];
byte ecgbuf[1024];

// For writing out header
unsigned long recByteWritten = 0L;
// General purpose byte storage variables
byte byte1, byte2, byte3, byte4;
// Samples stored in buffer counter
unsigned int bufByteCount;
// Buffer select variable
byte bufWrite;

// Recording status/flag - default off
int recording = 0;
// Variables that store the result from the SAADC
// 3 because we have 3 channels
int16_t result[4];

// Initialised values for holding
uint8_t piezoval = 0; uint8_t elec1val = 0; uint8_t elec2val = 0; uint8_t ecgval = 0;

// Command flag from interrupt function to the loop function
int writebuff1 = 0;
int writebuff2 = 0;

// Variable to store time since Arduino was connected to mobile
unsigned long t_start = 0;
int t_elapsed = 0;

// Variable to signal when to read files
bool readfiles = true;


// File definitions
File piezorec;
File electret1rec;
File electret2rec;
File ecgrec;
File data;

/*--------------------- VARIABLES FOR BLUETOOTH  ------------------------*/
// ---------------- data transfer variables --------------------
int bufsize = 244;
int bufsize2 = 1;
byte databuf[244];
byte ackflag[1]; // temp variable for ACK readvalue to store
byte fileflag[1];
byte filename[65]; // used for sending file names to Phone, not to be confused with filename(s)
byte timebuf[20];
int sync = 0;
int syncing = 0; // variable to avoid second BT button start
int starttransfer = 0;

int i = 0; // seekset initialiser
int nxtfile = 0;//internal flag to signal next file
int filesize = 0;
int fileindex = 0; // Seeing which file we are reading
int filecount = 0; // how many files do we have to transfer
// counter for how many wav files we detected
int filenames_transferred = 0; // how many files we have transfered
int wavcount = 0;

// String for storing file names, maximum names of 5
// maximum string size of 65, note this has to match size of 'names'
// in printFiles
char filenames[20][65];
char openfile[100];

// Variable that allows bluetooth loop to run several times to allow phone to register values
int manualstop = 100000;

// Generic file type to open wav files from SD card
File wavfile;

// --------------- time transfer variables -------------------
byte timeinbytes[20]; // Variable to store time stamp
String curtime;

// ------------- UUID definitions for service/characteristics -----------
// OVERALL SERVICE THAT HOLDS THE BELOW CHARACTERISTICS
BLEService fileservice("145b9e49-e39c-4a98-a28b-7c77cbd9bf2b"); // BLE file Service

/* Characteristic for data transfer
   transfers 247 bytes at once given limits of MTU
*/
BLECharacteristic datachar("4cdb99ab-808c-4b0b-a85e-7a89ad7f1a67", BLERead | BLEWrite | BLENotify, 512);

/* Characteristic for ack flag, found using random number generator
   Flag is used to indicate when next buffer can be sent
   Note it is BLEIntCharacteristic, not BLECharacteristic
*/
BLECharacteristic ACK("6156a359-f7f7-4cb6-841e-a326c6cd4b22", BLERead | BLEWrite, 1);

/* Characteristic used for signalling end of file
   Will write to 1 when we have reached the end of the file
*/
BLECharacteristic EndFile("4620f975-160a-48a2-a5ff-fe6ec15a1985", BLERead | BLEWrite, 1);

/* Characteristic used for receiving time stamp
   Will store the date and time upon connecting to phone
*/
BLECharacteristic Timestamp("f11af9cf-2afa-4ce3-bc28-49e2be313a2b", BLERead | BLEWrite, 20);

/* Characteristic used for sending file names
   will pass each file name into the buffer before sending
*/
BLECharacteristic filelist("714cdea5-b8bc-4153-9335-7c8b07e1e85a", BLERead | BLEWrite, 65);



// Required to instantiate SAADC handler function
#ifdef __cplusplus
extern "C" {
#endif
void SAADC_IRQHandler_v(void);
#ifdef __cplusplus
}
#endif


// SETUP FUNCTION
// Required in every arduino sketch
void setup() {
  // Sets the data rate in bits per second (baud) for serial data transmission
  
  Serial.begin(9600);
  while (!Serial) {
  ; // wait for serial port to connect. Needed for native USB port only
  }

  // Set up internal timer
  Setup_timer();
  // Configure ADC
  Setup_ADC();
  /* ------------------------Instantiating PPI----------------------
     PPI creates a direct relationship between two events of the microcontroller
     Timer3_SAADC_PPI creates a link between the timer hitting a set count
     and the ADC triggering for the purposes of generating a specific sampling rate
  */
  TIMER3_SAADC_PPI();


  // Initialise pushbutton pin as input
  pinMode(RECBUTTON, INPUT);
  // Initialise onboard LED
  pinMode(LED_BUILTIN, OUTPUT);
  // Initialise red recording LED
  pinMode(RECLED, OUTPUT);
  // Same for BT hardware
  pinMode(BTBUTTON, INPUT);
  pinMode(BTLED, OUTPUT);


  // Only uncomment for serial print commands
  // All serial print debugging points have been commented out
  
   while(1){
    Serial.println("initialised");
  }


  // Entire block below just checking whether the SD card has been registered
  // Serial.print("Initializing SD card...");
  if (!sd.begin(CS)) { // SD.begin initialises communications between the SD card adapter via the chip select (CS) pin
    Serial.println("initialization failed!");
    // Flash the LED if we failed initialisation.
    while (1) {
      digitalWrite(LED_BUILTIN, HIGH);
      delay(100);
      digitalWrite(LED_BUILTIN, LOW);
      delay(100);
      Serial.println("ERROR");
      delay(100);
      
    }
    return;
  }
  Serial.println("initialization done.");


  // -------- BLE SETUP ---------
  // begin initialization
  if (!BLE.begin()) {
    Serial.println("starting BLE failed!");
    while (1);
  }

  // set advertised local name and service UUID:
  // Arbitrary name, set to LED
  BLE.setLocalName("Stethoscope");
  BLE.setAdvertisedService(fileservice);

  // add the characteristic to the service
  fileservice.addCharacteristic(datachar);
  fileservice.addCharacteristic(ACK);
  fileservice.addCharacteristic(EndFile);
  fileservice.addCharacteristic(Timestamp);
  fileservice.addCharacteristic(filelist);


  // Initialise ACK flag value
  ackflag[0] = (byte)0x01;
  ACK.writeValue(ackflag, bufsize2);
  // Initilise EndFile flag value
  fileflag[0] = (byte)0x00;
  EndFile.writeValue(fileflag, bufsize2);
  timeinbytes[0] = (byte)0x00;
  // Initialising so that file transfer does not start on connection
  // File transfer triggers at filename[0] = byte(0x00)
  filename[0] = byte(0x01);
  filelist.writeValue(filename, 65);
  filenames_transferred = 0;

  // add service
  BLE.addService(fileservice);

}

// LOOP FUNCTION
// also required in each arduino sketch
void loop() {

  // Read what files are in the SD card
  if (readfiles) {
    printFiles();
    Serial.println("Files saved are: ");
    for (int i = 0; i < wavcount; i++) {
      Serial.println(filenames[i]);
    }
    readfiles = false;
  }


  // READ BUTTON PRESS FOR BLUETOOTH
  if (digitalRead(BTBUTTON) == HIGH && syncing == 0) {

    // ---------------- RESET VALUES ----------------------
    manualstop = 100000;
    sync = 0;
    starttransfer = 0;

    i = 0; // seekset initialiser
    fileindex = 0; // Seeing which file we are reading
    // counter for how many wav files we detected
    wavcount = 0;

    ackflag[0] = (byte)0x01;
    ACK.writeValue(ackflag, bufsize2);

    fileflag[0] = (byte)0x00;
    EndFile.writeValue(fileflag, bufsize2);
    timeinbytes[0] = (byte)0x00;

    filename[0] = byte(0x01);
    filelist.writeValue(filename, 65);
    filenames_transferred = 0;

    digitalWrite(BTLED, HIGH);
    // Advertise once
    BLE.advertise();
    //Serial.println("BLE advertising");

    syncing = 1;
  }
  if (syncing == 1) {
    // -------------- BT SEQUENCE --------------------
    // listen for BLE peripherals to connect:
    BLEDevice central = BLE.central();

    // if a central is connected to peripheral:
    if (central) {
      //Serial.print("Connected to central: ");
      // print the central's MAC address:
      //Serial.println(central.address());

      // while the central is still connected to peripheral:
      while (central.connected() && manualstop != 0) {

        // ------------- RETRIEVE TIME STAMP  -------------
        // If  time has been written
        ACK.readValue(ackflag, bufsize2);
        // ack flag instantiated as 0x01
        // check if the phone has reset it to 0 to start this sequence
        // timeinbytes check - (once we have received a time, stop entering this loop)
        if (ackflag[0] == (byte)0x00 && timeinbytes[0] == (byte)0x00) {
          // Get time stamp and date in bytes and store in 10 index array
          Timestamp.readValue(timeinbytes, 20);
          //Serial.println("timestamp read");
          // Check if a time has been received at all

          // CONVERT TO TIME AND DATE
          curtime = ((char*)timeinbytes);
          //Serial.println(curtime);

          // Retrieve clock value at the moment we connect to the phone
          t_start = millis();
        }


        // -------------- RUN SYNCH CHECK -----------------
        // If first index of file list is flagged as 0x00, then start sync function
        filelist.readValue(filename, 65);

        if (filename[0] == byte(0x00) || starttransfer == 1) {
          // -------------- SEND FILE NAMES TO PHONE ----------
          // If the phone has written to the file list

          if (filenames_transferred <= filecount + 1) {
            starttransfer = 1;
            ACK.readValue(ackflag, bufsize2);
            if (ackflag[0] == byte(0x01)) {
              // if (filenames_transferred == filecount)
              if (filenames_transferred == filecount) {
                // Move to data transfer
                // Setting first array index to a number that wont coincide with date values
                // i.e. no date value will ever be 40
                nxtfile = 1;
                filename[0] = byte(0x28);// 40 in decimal
                filelist.writeValue(filename, 65);
                // Exit this loop
                filenames_transferred++;
                //ackflag[0] = (byte)0x00;
                //ACK.writeValue(ackflag, bufsize2);
              }
              //else{
              else if (filenames_transferred < filecount) {
                String temp = filenames[filenames_transferred];
                temp.getBytes(filename, 65);
                //Serial.print("Filename: ");
                //Serial.println(temp);
                filelist.writeValue(filename, 65);
                filenames_transferred++;
                ackflag[0] = (byte)0x00;
                ACK.writeValue(ackflag, bufsize2);
              }
            }
          }

          ACK.readValue(ackflag, bufsize2);
          // -------------- OPEN NEXT FILE ------------------
          if (nxtfile == 1) {
            // check if we have transferred all the files
            // if (fileindex <= filecount)
            if (fileindex <= filecount) {
              // Retrieve and open the next file
              wavfile.open(filenames[fileindex]);
              //Serial.println(filenames[fileindex]);
              //Serial.println("File opened");
              // Find file size
              filesize = wavfile.size();
              // seekset initialiser
              i = 0;
              // Don't enter this loop after first run
              nxtfile = 0;
              // Start data transfer
              sync = 1;
              // Read one buffer
              ackflag[0] = (byte)0x01;
              ACK.writeValue(ackflag, bufsize2);
              // Move to the next file
              fileindex++;
            }
          }

          // --------- BEGIN DATA TRANSFER FOR FILE ----------
          // Check if the central android device has written to the characteristic
          ACK.readValue(ackflag, bufsize2);
          // if it has, reset the flag and change our characteristic buffer to the new values
          if ((ackflag[0] == (byte)0x01) && (sync == 1)) {
            // reset flag
            ackflag[0] = (byte)0x00;
            ACK.writeValue(ackflag, bufsize2);
            updateBuffer(filesize);
          }
        }

        // Let the while loop run for a period of time to let the bluetooth signal be captured
        /*
           This is a workaround for difficult bug that could not be resolved due to time constraints
           The file transfer as of this version only works when the phone has ample time
           to register the final file flag from the Arduino
           The loop is therefore run for a period of time before disconnecting after the final file is written

           An extra blank file will begin transferring at the end of this loop and stop at the first buffer
           This conditional statement will disconnect it manually after set period of time after that first
           buffer is written
        */
        if (fileindex > filecount) {
          manualstop--;
        }

      }
      // --------DISCONNECT AFTER TRANSFERRING ALL FILES ------
      central.disconnect();
      BLE.stopAdvertise();
      //Serial.print(F("Disconnected from central: "));
      //Serial.println(central.address());
      digitalWrite(BTLED, LOW);
      syncing = 0;
    }
  }



  // READ BUTTON PRESS FOR RECORDING
  if (digitalRead(RECBUTTON) == HIGH && recording == 1) {
    StopRec();
    delay(200);
  }
  if (digitalRead(RECBUTTON) == HIGH && recording == 0) {
    delay(200);
    StartRec();
  }

  // WRITE DATA TO TEXT FILE
  if (writebuff1 == 1 && recording == 1) {
    // Write to file
    data.write(buf00, 4096);
    // update byte saved count
    recByteSaved += 4096;
    writebuff1 = 0;
  } // save buf01 to card
  if (writebuff2 == 1 && recording == 1) {
    data.write(buf01, 4096);
    recByteSaved += 4096;
    writebuff2 = 0;
  } // save buf02 to card
}


//-------------- STARTING RECORDING -------------------
void StartRec() { // begin recording process

  // Check if we have read the time and date from phone
  // If we have, start recording sequence, otherwise flash LED to show error and exit sequence
  if (curtime != NULL) {
    //Serial.println("Start recording");

    // recByteSaved is used to determine the file size for the WAV header
    writeWavHeader(); // creates header to determine WAV file
    recByteCount = 0; // how many samples taken
    recByteSaved = 0; // used as a reference to see which buffer to save to card

    //Serial.print(recByteCount);
    //Serial.print(" :recByteCount\n");
    //Serial.print(recByteSaved);
    //Serial.print(" :recByteSaved\n");

    // Variables tell us which buffer to write to
    writebuff1 = 0;
    writebuff2 = 0;

    recording = 1; // set recording flag

    digitalWrite(RECLED, HIGH);

    // Start ADC, stay in loop until ADC has completely initialised
    NRF_SAADC->TASKS_START = 1;
    while (NRF_SAADC->EVENTS_STARTED == 0);
    NRF_SAADC->EVENTS_STARTED = 0;
  }
  else {
    //Serial.println("Timestamp value is null");
    // Flash LED for 1 second to show error
    for (int j = 0; j < 5; j++) {
      digitalWrite(RECLED, HIGH);
      delay(100);
      digitalWrite(RECLED, LOW);
      delay(100);
    }
  }
}
//-------------- STOP RECORDING ------------------------
void StopRec() { // stop recording process, update WAV header, close file
  digitalWrite(RECLED, LOW);
  // Stop the SAADC, since it's not used anymore.
  NRF_SAADC->TASKS_STOP = 1;
  while (NRF_SAADC->EVENTS_STOPPED == 0);
  NRF_SAADC->EVENTS_STOPPED = 0;

  recording = 0; // reset recording flag

  writeOutHeader(); // updates WAV header with final filesize/datasize

  //Serial.println("Stop Recording");
  //Serial.println(recByteCount);
  //Serial.println(" :recByteCount");
  //Serial.println(recByteSaved);
  //Serial.println(" :recByteSaved");

}
//------------- WRITING ACTUAL VALUES TO WAV FILE --------------------
/* WAV file requires certain header values to define it as a WAV file
    these are a mixture of words and number values
    that define the properties of the file and are written as bytes
*/
void writeWavHeader() { // write initial original WAV header to file

  recByteSaved = 0;
  String tempstr;
  char temp[30];

  // Find time elapsed (in seconds) since we have taken time and date from phone
  // rough rounding - does not need to be specific
  float t_elapsed = (float)(millis() - t_start) / 1000;

  // create file suffix as character array
  tempstr = ".wav";
  tempstr.toCharArray(temp, tempstr.length() + 1);


  // Retrieved time from phone is in format:
  // dd-mm-yyyy-hh-mm-ss
  // 28-09-2020-07-42-55
  /* Retrieve each segment
     find remainder of hrs mins secs
     convert to string
  */

  String s_secs;
  String s_mins;
  String s_hrs;

  // Get segment in string that refers to seconds
  // add the milliseconds and find remaining seconds
  tempstr = curtime.substring(17);
  int secs = tempstr.toInt();
  // Find remainder of seconds
  secs = (secs + (int)floor(t_elapsed)) % 60;
  if (secs / 10 < 1) {
    s_secs = "0" + String(secs);
  }
  else {
    s_secs = String(secs);
  }

  tempstr = curtime.substring(14, 16);
  int mins = tempstr.toInt();
  mins = (mins + (int)floor(t_elapsed / 60)) % 60;
  s_mins = String(mins);
  if (mins / 10 < 1) {
    s_mins = "0" + String(mins);
  }
  else {
    s_mins = String(mins);
  }

  tempstr = curtime.substring(11.13);
  int hrs = tempstr.toInt();
  hrs = (hrs + (int)floor(t_elapsed / 3600)) % 12;
  if (hrs / 10 < 1) {
    s_hrs = "0" + String(hrs);
  }
  else {
    s_hrs = String(hrs);
  }


  String date = curtime.substring(0, 10);

  // create final string
  String time_date = s_hrs + "-" + s_mins + "-" + s_secs + " " + date;
  //Serial.println(time_date);


  // get time stamp and convert to character array
  char curfilename[30];
  time_date.toCharArray(curfilename, time_date.length() + 1);

  tempstr = "pdata ";
  char piezoname[30];
  tempstr.toCharArray(piezoname, tempstr.length() + 1);
  strcat(piezoname, curfilename);
  strcat(piezoname, temp);

  tempstr = "edata ";
  char elec2name[30];
  tempstr.toCharArray(elec2name, tempstr.length() + 1);
  strcat(elec2name, curfilename);
  strcat(elec2name, temp);

  char elec1name[30];
  strncpy(elec1name, curfilename, 30);
  strcat(elec1name, temp);

  tempstr = "ecgdata ";
  char ecgname[30];
  tempstr.toCharArray(ecgname, tempstr.length() + 1);
  strcat(ecgname, curfilename);
  strcat(ecgname, temp);

  // O_CREAT creates the file if it doesn't already exist
  // O_TRUNC will truncate length to 0 if the file exists and is successfully opened
  // O_RDWR means to open the file for reading and writing

  piezorec.open(piezoname, O_CREAT | O_TRUNC | O_RDWR);
  electret1rec.open(elec1name, O_CREAT | O_TRUNC | O_RDWR);
  electret2rec.open(elec2name, O_CREAT | O_TRUNC | O_RDWR);
  ecgrec.open(ecgname, O_CREAT | O_TRUNC | O_RDWR);

  // Opening and creating text file
  data.open("data.txt", O_CREAT | O_TRUNC | O_RDWR);

  // Write string
  piezorec.write("RIFF"); electret1rec.write("RIFF"); electret2rec.write("RIFF"); ecgrec.write("RIFF");
  // Write value stored in "fileSize"
  byte1 = recfileSize & 0xff;
  byte2 = (recfileSize >> 8) & 0xff;
  byte3 = (recfileSize >> 16) & 0xff;
  byte4 = (recfileSize >> 24) & 0xff;
  piezorec.write(byte1);  piezorec.write(byte2);  piezorec.write(byte3);  piezorec.write(byte4);
  electret1rec.write(byte1);  electret1rec.write(byte2);  electret1rec.write(byte3);  electret1rec.write(byte4);
  electret2rec.write(byte1);  electret2rec.write(byte2);  electret2rec.write(byte3);  electret2rec.write(byte4);
  ecgrec.write(byte1);  ecgrec.write(byte2);  ecgrec.write(byte3);  ecgrec.write(byte4);

  piezorec.write("WAVE"); electret1rec.write("WAVE"); electret2rec.write("WAVE"); ecgrec.write("WAVE");
  piezorec.write("fmt "); electret1rec.write("fmt "); electret2rec.write("fmt "); ecgrec.write("fmt ");

  byte1 = waveChunk & 0xff;
  byte2 = (waveChunk >> 8) & 0xff;
  byte3 = (waveChunk >> 16) & 0xff;
  byte4 = (waveChunk >> 24) & 0xff;
  piezorec.write(byte1);  piezorec.write(byte2);  piezorec.write(byte3);  piezorec.write(byte4);
  electret1rec.write(byte1);  electret1rec.write(byte2);  electret1rec.write(byte3);  electret1rec.write(byte4);
  electret2rec.write(byte1);  electret2rec.write(byte2);  electret2rec.write(byte3);  electret2rec.write(byte4);
  ecgrec.write(byte1);  ecgrec.write(byte2);  ecgrec.write(byte3);  ecgrec.write(byte4);

  byte1 = waveType & 0xff;
  byte2 = (waveType >> 8) & 0xff;
  piezorec.write(byte1);  piezorec.write(byte2);
  electret1rec.write(byte1);  electret1rec.write(byte2);
  electret2rec.write(byte1);  electret2rec.write(byte2);
  ecgrec.write(byte1);  ecgrec.write(byte2);

  byte1 = numChannels & 0xff;
  byte2 = (numChannels >> 8) & 0xff;
  piezorec.write(byte1);  piezorec.write(byte2);
  electret1rec.write(byte1);  electret1rec.write(byte2);
  electret2rec.write(byte1);  electret2rec.write(byte2);
  ecgrec.write(byte1);  ecgrec.write(byte2);

  byte1 = sampleRate & 0xff;
  byte2 = (sampleRate >> 8) & 0xff;
  byte3 = (sampleRate >> 16) & 0xff;
  byte4 = (sampleRate >> 24) & 0xff;
  piezorec.write(byte1);  piezorec.write(byte2);  piezorec.write(byte3);  piezorec.write(byte4);
  electret1rec.write(byte1);  electret1rec.write(byte2);  electret1rec.write(byte3);  electret1rec.write(byte4);
  electret2rec.write(byte1);  electret2rec.write(byte2);  electret2rec.write(byte3);  electret2rec.write(byte4);
  ecgrec.write(byte1);  ecgrec.write(byte2);  ecgrec.write(byte3);  ecgrec.write(byte4);

  byte1 = bytesPerSec & 0xff;
  byte2 = (bytesPerSec >> 8) & 0xff;
  byte3 = (bytesPerSec >> 16) & 0xff;
  byte4 = (bytesPerSec >> 24) & 0xff;
  piezorec.write(byte1);  piezorec.write(byte2);  piezorec.write(byte3);  piezorec.write(byte4);
  electret1rec.write(byte1);  electret1rec.write(byte2);  electret1rec.write(byte3);  electret1rec.write(byte4);
  electret2rec.write(byte1);  electret2rec.write(byte2);  electret2rec.write(byte3);  electret2rec.write(byte4);
  ecgrec.write(byte1);  ecgrec.write(byte2);  ecgrec.write(byte3);  ecgrec.write(byte4);

  byte1 = blockAlign & 0xff;
  byte2 = (blockAlign >> 8) & 0xff;
  piezorec.write(byte1);  piezorec.write(byte2);
  electret1rec.write(byte1);  electret1rec.write(byte2);
  electret2rec.write(byte1);  electret2rec.write(byte2);
  ecgrec.write(byte1);  ecgrec.write(byte2);

  byte1 = bitsPerSample & 0xff;
  byte2 = (bitsPerSample >> 8) & 0xff;
  piezorec.write(byte1);  piezorec.write(byte2);
  electret1rec.write(byte1);  electret1rec.write(byte2);
  electret2rec.write(byte1);  electret2rec.write(byte2);
  ecgrec.write(byte1);  ecgrec.write(byte2);

  piezorec.write("data");
  electret1rec.write("data");
  electret2rec.write("data");
  ecgrec.write("data");


  byte1 = dataSize & 0xff;
  byte2 = (dataSize >> 8) & 0xff;
  byte3 = (dataSize >> 16) & 0xff;
  byte4 = (dataSize >> 24) & 0xff;
  piezorec.write(byte1);  piezorec.write(byte2);  piezorec.write(byte3);  piezorec.write(byte4);
  electret1rec.write(byte1);  electret1rec.write(byte2);  electret1rec.write(byte3);  electret1rec.write(byte4);
  electret2rec.write(byte1);  electret2rec.write(byte2);  electret2rec.write(byte3);  electret2rec.write(byte4);
  ecgrec.write(byte1);  ecgrec.write(byte2);  ecgrec.write(byte3);  ecgrec.write(byte4);
}
//------------- UPDATING WAV HEADER --------------------
void writeOutHeader() { // update WAV header with final filesize/datasize
  // Overwriting file size from byte 5 onwards
  /*  code to transfer data from
      data.txt to each wav file
      data should seek set from data.txt file for each byte and write to
      the wav file for the length of the data file
  */

  // Initialise bytes written
  recByteWritten = 0;
  // Iterate through the text file
  // 4096 is our buffer size containing multiple samples
  for (int i = 0; i < recByteSaved / 4096; i++) {
    // Move to correct location in file
    data.seekSet(i * 4096);
    // Read from text file
    data.read(readbuf, 4096);
    // For the entire buffer, assign the right data from the buffer to each sensor
    for (int j = 0; j <= 4092; j += 4) {
      piezobuf[j / 4] = readbuf[j];
      elec1buf[j / 4] = readbuf[j + 1];
      elec2buf[j / 4] = readbuf[j + 2];
      ecgbuf[j / 4] = readbuf[j + 3];
    }
    // Write each subbuffer to the apt file
    piezorec.write(piezobuf, 1024);
    electret1rec.write(elec1buf, 1024);
    electret2rec.write(elec2buf, 1024);
    ecgrec.write(ecgbuf, 1024);
    recByteWritten += 1024;
  }

  // Finish writing the header
  piezorec.seekSet(4);
  electret1rec.seekSet(4);
  electret2rec.seekSet(4);
  ecgrec.seekSet(4);
  // Write data size and add header size (36 bytes)
  recByteWritten = recByteWritten + 36;
  byte1 = recByteWritten & 0xff;
  byte2 = (recByteWritten >> 8) & 0xff;
  byte3 = (recByteWritten >> 16) & 0xff;
  byte4 = (recByteWritten >> 24) & 0xff;
  piezorec.write(byte1);  piezorec.write(byte2);  piezorec.write(byte3);  piezorec.write(byte4);
  electret1rec.write(byte1);  electret1rec.write(byte2);  electret1rec.write(byte3);  electret1rec.write(byte4);
  electret2rec.write(byte1);  electret2rec.write(byte2);  electret2rec.write(byte3);  electret2rec.write(byte4);
  ecgrec.write(byte1);  ecgrec.write(byte2);  ecgrec.write(byte3);  ecgrec.write(byte4);

  // Overwriting data size from byte 41 onwards
  piezorec.seekSet(40);
  electret1rec.seekSet(40);
  electret2rec.seekSet(40);
  ecgrec.seekSet(40);

  recByteWritten = recByteWritten - 36;
  byte1 = recByteWritten & 0xff;
  byte2 = (recByteWritten >> 8) & 0xff;
  byte3 = (recByteWritten >> 16) & 0xff;
  byte4 = (recByteWritten >> 24) & 0xff;

  piezorec.write(byte1);  piezorec.write(byte2);  piezorec.write(byte3);  piezorec.write(byte4);
  electret1rec.write(byte1);  electret1rec.write(byte2);  electret1rec.write(byte3);  electret1rec.write(byte4);
  electret2rec.write(byte1);  electret2rec.write(byte2);  electret2rec.write(byte3);  electret2rec.write(byte4);
  ecgrec.write(byte1);  ecgrec.write(byte2);  ecgrec.write(byte3);  ecgrec.write(byte4);
  // Close files
  piezorec.close();
  electret1rec.close();
  electret2rec.close();
  ecgrec.close();
  // Closing data file too
  data.close();
  readfiles = true;
}

//----------- SETTING UP IN-BUILT TIMER FOR USE ----------------
void Setup_timer() {


  NRF_TIMER3->BITMODE = TIMER_BITMODE_BITMODE_16Bit << TIMER_BITMODE_BITMODE_Pos;
  // TIMER base frequency is given as 16 MHz divided by 2^(prescaler value)
  // Prescale value of 4 actually means 2^4, therefore meaning 16
  // BELOW RATES ARE EXCLUDING SAMPLING TIME OF SINGLE SAMPLE
  // PRESCALE - 2 - frequency of 4MHz
  // (With timer count of 91 yields sample rate of 43.96 kHz for example)

  // For the purposes of accuracy, a prescaler value of 0 will be set, meaning our timer will be running at 16MHz
  NRF_TIMER3->PRESCALER = 0;

  // compare value, generates EVENTS_COMPARE[0]
  // 16MHz means 62.5ns ticks

  // Each ADC sample time was set at 3us
  // Sample time occurs during timer count so does not need to be accounted for


  // Value of 1600 gives 10,000Hz sample rate
  NRF_TIMER3->CC[TIMER3_RELOAD_CC] = 1600;

  // Clear the timer when COMPARE[0] event is triggered
  NRF_TIMER3->SHORTS = TIMER_SHORTS_COMPARE0_CLEAR_Enabled << TIMER3_RELOAD_CC;
  // Set priority and enable interrupts
  NVIC_SetPriority(TIMER3_IRQn, 6);
  NVIC_EnableIRQ(TIMER3_IRQn);
  // Start timer
  NRF_TIMER3->TASKS_START = 1;

}
//-------------- SETTING UP ADC FOR USE ------------------------
void Setup_ADC() {

  //----------- CONFIGURING ANALOGUE PIN 1 ---------------
  // Enable interrupts
  NRF_SAADC->INTENSET = SAADC_INTENSET_END_Enabled << SAADC_INTENSET_END_Pos;
  // Set interrupt handler
  // Interrupt handler stores sample values to buffer
  NVIC_EnableIRQ(SAADC_IRQn);

  NRF_SAADC->CH[0].CONFIG = (SAADC_CH_CONFIG_GAIN_Gain1_6    << SAADC_CH_CONFIG_GAIN_Pos) |
                            (SAADC_CH_CONFIG_MODE_SE         << SAADC_CH_CONFIG_MODE_Pos) |
                            (SAADC_CH_CONFIG_REFSEL_Internal << SAADC_CH_CONFIG_REFSEL_Pos) |
                            (SAADC_CH_CONFIG_RESN_Bypass     << SAADC_CH_CONFIG_RESN_Pos) |
                            (SAADC_CH_CONFIG_RESP_Bypass     << SAADC_CH_CONFIG_RESP_Pos) |
                            (SAADC_CH_CONFIG_TACQ_5us        << SAADC_CH_CONFIG_TACQ_Pos);

  NRF_SAADC->CH[1].CONFIG = (SAADC_CH_CONFIG_GAIN_Gain1_6    << SAADC_CH_CONFIG_GAIN_Pos) |
                            (SAADC_CH_CONFIG_MODE_SE         << SAADC_CH_CONFIG_MODE_Pos) |
                            (SAADC_CH_CONFIG_REFSEL_Internal << SAADC_CH_CONFIG_REFSEL_Pos) |
                            (SAADC_CH_CONFIG_RESN_Bypass     << SAADC_CH_CONFIG_RESN_Pos) |
                            (SAADC_CH_CONFIG_RESP_Bypass     << SAADC_CH_CONFIG_RESP_Pos) |
                            (SAADC_CH_CONFIG_TACQ_5us        << SAADC_CH_CONFIG_TACQ_Pos);

  NRF_SAADC->CH[2].CONFIG = (SAADC_CH_CONFIG_GAIN_Gain1_6    << SAADC_CH_CONFIG_GAIN_Pos) |
                            (SAADC_CH_CONFIG_MODE_SE         << SAADC_CH_CONFIG_MODE_Pos) |
                            (SAADC_CH_CONFIG_REFSEL_Internal << SAADC_CH_CONFIG_REFSEL_Pos) |
                            (SAADC_CH_CONFIG_RESN_Bypass     << SAADC_CH_CONFIG_RESN_Pos) |
                            (SAADC_CH_CONFIG_RESP_Bypass     << SAADC_CH_CONFIG_RESP_Pos) |
                            (SAADC_CH_CONFIG_TACQ_5us        << SAADC_CH_CONFIG_TACQ_Pos);

  NRF_SAADC->CH[3].CONFIG = (SAADC_CH_CONFIG_GAIN_Gain1_6    << SAADC_CH_CONFIG_GAIN_Pos) |
                            (SAADC_CH_CONFIG_MODE_SE         << SAADC_CH_CONFIG_MODE_Pos) |
                            (SAADC_CH_CONFIG_REFSEL_Internal << SAADC_CH_CONFIG_REFSEL_Pos) |
                            (SAADC_CH_CONFIG_RESN_Bypass     << SAADC_CH_CONFIG_RESN_Pos) |
                            (SAADC_CH_CONFIG_RESP_Bypass     << SAADC_CH_CONFIG_RESP_Pos) |
                            (SAADC_CH_CONFIG_TACQ_5us        << SAADC_CH_CONFIG_TACQ_Pos);

  // Pin assignments can be found in the NRF52840 online information booklet
  // Pin A0
  NRF_SAADC->CH[0].PSELP = SAADC_CH_PSELP_PSELP_AnalogInput2 << SAADC_CH_PSELP_PSELP_Pos;
  NRF_SAADC->CH[0].PSELN = SAADC_CH_PSELN_PSELN_NC << SAADC_CH_PSELN_PSELN_Pos;

  // Pin A1
  NRF_SAADC->CH[1].PSELP = SAADC_CH_PSELP_PSELP_AnalogInput3 << SAADC_CH_PSELP_PSELP_Pos;
  NRF_SAADC->CH[1].PSELN = SAADC_CH_PSELN_PSELN_NC << SAADC_CH_PSELN_PSELN_Pos;

  // Pin A2
  NRF_SAADC->CH[2].PSELP = SAADC_CH_PSELP_PSELP_AnalogInput6 << SAADC_CH_PSELP_PSELP_Pos;
  NRF_SAADC->CH[2].PSELN = SAADC_CH_PSELN_PSELN_NC << SAADC_CH_PSELN_PSELN_Pos;

  // Pin A3
  NRF_SAADC->CH[3].PSELP = SAADC_CH_PSELP_PSELP_AnalogInput1 << SAADC_CH_PSELP_PSELP_Pos;
  NRF_SAADC->CH[3].PSELN = SAADC_CH_PSELN_PSELN_NC << SAADC_CH_PSELN_PSELN_Pos;

  // 8 bit samples
  // NRF52840 can sample up to 12 bits
  NRF_SAADC->RESOLUTION = SAADC_RESOLUTION_VAL_8bit << SAADC_RESOLUTION_VAL_Pos;

  // Maybe need to change to accomodate more results
  // Number of sampels * number of channels
  // 1 sample & 3 channels
  NRF_SAADC->RESULT.MAXCNT = 4;

  // Result.ptr stores value, which we will assign the base value address to the start of our result array
  NRF_SAADC->RESULT.PTR = (uint32_t)&result[0];
  // Set sampling to manual samplinog
  NRF_SAADC->SAMPLERATE = SAADC_SAMPLERATE_MODE_Task << SAADC_SAMPLERATE_MODE_Pos;
  // Enable ADC
  NRF_SAADC->ENABLE = SAADC_ENABLE_ENABLE_Enabled << SAADC_ENABLE_ENABLE_Pos;



}
// PPI Function
void TIMER3_SAADC_PPI(void) {
  // Connect timer event to sample ADC event
  NRF_PPI->CH[TIMER3_PPI].EEP = (uint32_t)&NRF_TIMER3->EVENTS_COMPARE[TIMER3_RELOAD_CC];
  NRF_PPI->CH[TIMER3_PPI].TEP = (uint32_t)&NRF_SAADC->TASKS_SAMPLE;
  NRF_PPI->CHENSET = 1 << TIMER3_PPI; // Enables channel PPI (Programmable peripheral interconnect)
}

// --------------- INTERRUPT HANDLER ---------------
/*
  // Triggered when ADC samples a value
  // It must be noted that CPU heavy events including Serial print etc cannot
  // be run by the interrupt handler and will cause errors
*/

void SAADC_IRQHandler_v(void) {

  // result variable stores the sampled value
  // with 3 channels
  ecgval = (uint8_t)result[0];
  elec2val = (uint8_t)result[2];
  elec1val = (uint8_t)result[1];
  piezoval = (uint8_t)result[3];

  NRF_SAADC->EVENTS_END = 0;
  NRF_SAADC->TASKS_START = 1;

  // note that we increment once because we are taking 8 bit samples
  // IF THE CURRENT BUFFER BYTE COUNT HAS REACHED 4096 AND WE ARE CURRENTLY ON BUFFER 0, SWAP TO BUFFER 1
  recByteCount += 4; // increment sample counter
  bufByteCount += 4; // increment byte count

  if (bufByteCount == 4096 && bufWrite == 0) {
    bufByteCount = 0;
    bufWrite = 1;
    writebuff1 = 1;
  }
  // DO THE OPPOSITE HERE
  else if (bufByteCount == 4096 && bufWrite == 1) {
    bufByteCount = 0;
    bufWrite = 0;
    writebuff2 = 1;
  }


  if (bufWrite == 0) {
    buf00[bufByteCount] = piezoval;
    buf00[bufByteCount + 1] = elec1val;
    buf00[bufByteCount + 2] = elec2val;
    buf00[bufByteCount + 3] = ecgval;
  }
  if (bufWrite == 1) {
    buf01[bufByteCount] = piezoval;
    buf01[bufByteCount + 1] = elec1val;
    buf01[bufByteCount + 2] = elec2val;
    buf01[bufByteCount + 3] = ecgval;
  }


}

// Function to update the buffer that stores file data to be sent to the phone
void updateBuffer(int filesize) {

  // integer i dictates what byte we are at in the file
  wavfile.seekSet(i);

  // if data length - i is smaller than bufsize
  // only read remaining length of data
  if ((filesize - i) < bufsize) {
    // create new buffer size of remaining bytes
    int newbufsize = filesize - i;
    // read in last bytes
    wavfile.read(databuf, newbufsize);
    // write last bytes
    datachar.writeValue(databuf, bufsize);
    // Signal to phone that we reached the end of the file
    fileflag[0] = (byte)0x01;
    EndFile.writeValue(fileflag, bufsize2);
    //Serial.println("End of file flag set");
    // close file on last byte saved
    wavfile.close();
    //Serial.println("File closed");

    // Move to next file by triggering OPEN NEXT FILE flag
    nxtfile = 1;
  }

  else {
    // Read current set of bytes
    wavfile.read(databuf, bufsize);
    // Write bytes to characteristic that is being advertised
    datachar.writeValue(databuf, bufsize);
    //Serial.println("Buffer updated");
    i += bufsize;
  }
}

// Function to search for current files on the SD card and transfer to filenames
void printFiles() {
  // Open directory, do something if not successful
  if (!rec.open("/", O_RDONLY)) { //Serial.println("Failed to open directory");
  }
  // If we open the directory successfully
  else {

    bool done = false;
    filecount = 0;
    wavcount = 0;

    // Iterate through all files
    // reset directory location
    sd.vwd()->rewind();
    while (!done) {

      SdFile f;
      char name[65];// Array to store file name
      f.openNext(sd.vwd(), O_RDONLY);// Open next file
      if (f.isOpen()) {
        f.getName(name, 64); // retrieve the name of the file and store into char array
        // Check if name is a digit
        if (isdigit(name[0])) {
          for (int j = 0; name[j] != '\0'; j++) {
            filenames[wavcount][j] = name[j]; // store electret file to filenames array
          }
          wavcount++; // move to next storage index for filenames
          filecount = wavcount; // iterate filecount
        }

        // close the file after we're done
        f.close();
      }
      // exit loop when we're done
      else {

        done = true;
      }
    }
    // close directory
    rec.close();

  }
}
