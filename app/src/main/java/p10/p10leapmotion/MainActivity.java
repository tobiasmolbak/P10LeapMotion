package p10.p10leapmotion;

import android.Manifest;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStates;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import org.apache.commons.collections4.queue.CircularFifoQueue;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Queue;

public class MainActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener,
        CreateNewFileDialog.AddNewFileListener {

    // UI Elements
    public TextView txt_score;
    public TextView txt_message;
    public TextView txt_status;
    public GifImageView gifImageView;

    public static final String BLUETOOTH_PAIRED_DEVICES = "BLUETOOTH_PAIRED_DEVICES";
    public static final String ATTENTIVE = "ATTENTIVE";
    public static final String INATTENTIVE = "INATTENTIVE";
    public static final String GOOD = "GOOD";
    public static final String NEUTRAL = "NEUTRAL";
    public static final String NEGATIVE = "NEGATIVE";

    public static final String TAG = "MainActivity";

    public final static int MESSAGE_STATE_CHANGE = 1337;
    public final static String DEVICE_NAME = "1337 mmkay";
    public final static int MESSAGE_DEVICE_NAME = 1338;
    public final static String TOAST = "1339 mmkay";
    public final static int MESSAGE_TOAST = 1339;
    public final static int MESSAGE_READ = 1340;
    public final static int MESSAGE_WRITE = 1341;
    private static final int REQUEST_ENABLE_BT = 137;

    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    private final static int REQUEST_CHECK_SETTINGS = 9001;
    public static final int REQUEST_PERMISSIONS = 99;

    private TextToSpeech textToSpeech;

    private BluetoothServices mBluetoothServices = null;
    private BluetoothAdapter mBluetoothAdapter = null;
    private String mConnectedDeviceName = null;

    public static final Integer INSTANCES_BEFORE_WARNING = 4;
    public static final double MINIMUM_METER_DRIVEN = 10;
    public static final Integer MINIMUM_SPEED = 5;
    public static final Integer SCORE_SHOW_TIMER = 1000 * 8; // Given in seconds

    private ArrayList<BluetoothDevice> pairedDevices = new ArrayList<BluetoothDevice>();
    private Queue<String> stateQueue = new CircularFifoQueue<>(INSTANCES_BEFORE_WARNING);
    private boolean increasedIntensity = false;

    private ArrayList<String> attentivePredictedStates = new ArrayList<>();
    private ArrayList<String> rightPredictedStates = new ArrayList<>();
    private ArrayList<String> leftPredictedStates = new ArrayList<>();
    private ArrayList<SegmentData> routeData = new ArrayList<>();

    private boolean dataCollecting = false;
    private File file;

    private GoogleApiClient mGoogleApiClient;
    private Location mLastLocation = null;
    private LocationRequest mLocationRequest = null;
    private boolean mRequestingLocationUpdates = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initialiseComponents();

        createLocationRequest();
        checkPermissions();
        if (checkPlayServices()) {
            buildGoogleApiClient();
        }

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG);
        }

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetoothIntent, REQUEST_ENABLE_BT);
        }

        pairedDevices.addAll(mBluetoothAdapter.getBondedDevices());

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(bluetoothReceiver, filter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        } else if (mBluetoothServices == null) {
            mBluetoothServices = new BluetoothServices(this, mHandler);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mGoogleApiClient.isConnected() && !mRequestingLocationUpdates) {
            startLocationUpdates();
        }

        setupTextToSpeech();
        if (mBluetoothServices != null) {
            if (mBluetoothServices.getState() == BluetoothServices.STATE_NONE) {
                mBluetoothServices.start();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
        if (mBluetoothServices != null) {
            mBluetoothServices.stop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(bluetoothReceiver);
        if (mBluetoothServices != null) {
            mBluetoothServices.stop();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu (Menu menu) {

        MenuItem startItem = menu.findItem(R.id.start_collect);
        MenuItem stopItem = menu.findItem(R.id.stop_collect);

        if (dataCollecting) {
            startItem.setEnabled(false);
            stopItem.setEnabled(true);
        } else {
            startItem.setEnabled(true);
            stopItem.setEnabled(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.start_collect:
                CreateNewFileDialog createNewFileDialog = new CreateNewFileDialog();
                createNewFileDialog.show(getSupportFragmentManager(), "AddNewFile");
                return true;
            case R.id.stop_collect:
                stopCollecting();
                txt_status.setText("Not collecting");
                return true;
            case R.id.connect:
                chooseBluetoothDevice();
                return true;
            case R.id.discover:
                ensureDiscoverable();
                return true;
            case R.id.map:
                File path = Environment.getExternalStorageDirectory();
                File root = new File(path, getApplicationContext().getPackageName());

                Intent intent = new Intent(this, MapsActivity.class);
                intent.putExtra("root", root);
                startActivity(intent);
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onLocationChanged(android.location.Location location) {
        Log.e(TAG, "~Location changed: " + location.getLatitude() + "/" + location.getLongitude() + " dist: " + location.distanceTo(mLastLocation));
        if (dataCollecting) {
            if (location.distanceTo(mLastLocation) >= MINIMUM_METER_DRIVEN) {
                Log.e(TAG, "~Data added~");
                Toast.makeText(this, "Data added", Toast.LENGTH_SHORT).show();

                SegmentData tempData = new SegmentData(mLastLocation, location, attentivePredictedStates, rightPredictedStates, leftPredictedStates);
                routeData.add(tempData);

                attentivePredictedStates = new ArrayList<>();
                rightPredictedStates = new ArrayList<>();
                leftPredictedStates = new ArrayList<>();

                mLastLocation = location;
            }

            if (location.getSpeed() <= MINIMUM_SPEED && routeData.size() >= 12) {
                double calculatedScore = 0;
                for (SegmentData segment : routeData) {
                    calculatedScore += segment.getScore();
                }

                calculatedScore = calculatedScore / (double) routeData.size();
                txt_score.setText(String.valueOf(new DecimalFormat("##.#").format(calculatedScore)));

                sendWarning(calculateAttentiveState((float) calculatedScore));
                new ShowScoreTask().execute();

                ArrayList<SegmentData> tempRouteData = routeData;
                writeToFile(tempRouteData);
                routeData.clear();
            }
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.i(TAG, "Connection failed: ConnectionResult.getErrorCode() = " + result.getErrorCode());
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.i(TAG, "Connected to GoogleApiClient");

        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(mLocationRequest);

        PendingResult<LocationSettingsResult> result =
                LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient,
                        builder.build());

        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(@NonNull LocationSettingsResult locationSettingsResult) {
                Status status = locationSettingsResult.getStatus();
                LocationSettingsStates locationSettingsStates = locationSettingsResult.getLocationSettingsStates();

                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        startLocationUpdates();

                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        // Location settings are not satisfied, but this can be fixed
                        // by showing the user a dialog.
                        try {
                            // Show the dialog by calling startResolutionForResult(),
                            // and check the result in onActivityResult().
                            status.startResolutionForResult(
                                    MainActivity.this,
                                    REQUEST_CHECK_SETTINGS);
                        } catch (IntentSender.SendIntentException e) {
                            // Ignore the error.
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        // Location settings are not satisfied. However, we have no way
                        // to fix the settings so we won't show the dialog.

                        break;
                }
            }
        });
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "Connection suspended");
        // Reestablish connection
        mGoogleApiClient.connect();
    }

    @Override
    public void onAcceptNewFile(String _title) {
        createNewDataFile(_title);
        txt_status.setText("Collecting");
        startCollecting();

    }

    private void initialiseComponents() {
        // UI Elements
        gifImageView = (GifImageView) findViewById(R.id.GifImageView);
        txt_score = (TextView) findViewById(R.id.txt_score);
        txt_message = (TextView) findViewById(R.id.txt_message);
        txt_status = (TextView) findViewById(R.id.txt_status);

        txt_score.setVisibility(View.INVISIBLE);
        txt_message.setVisibility(View.INVISIBLE);
        setupTextToSpeech();
    }

    public void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ArrayList<String> permissions = new ArrayList<>();
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH);
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_ADMIN);
            }
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
            if (checkSelfPermission(Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.INTERNET);
            }
            if (checkSelfPermission(Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.ACCESS_NETWORK_STATE);
            }

            if (!permissions.isEmpty()) {
                requestPermissions(permissions.toArray(new String[permissions.size()]), REQUEST_PERMISSIONS);
            }
        }
    }

    private boolean checkPlayServices() {
        Log.i(TAG, "Running: checkPlayServices");
        GoogleApiAvailability googleApi = GoogleApiAvailability.getInstance();
        int resultCode = googleApi.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (googleApi.isUserResolvableError(resultCode)) {
                googleApi.getErrorDialog(this, resultCode,
                        PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                Toast.makeText(getApplicationContext(),
                        "This device is not supported.", Toast.LENGTH_LONG)
                        .show();
                finish();
            }
            return false;
        }
        return true;
    }

    protected synchronized void buildGoogleApiClient() {
        Log.i(TAG, "Running: buildGoogleApiClient");
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }

    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(5000);
        mLocationRequest.setFastestInterval(4000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    protected void startLocationUpdates() {
        mRequestingLocationUpdates = true;
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    protected void stopLocationUpdates() {
        mRequestingLocationUpdates = false;
        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        }
    }

    private void setupTextToSpeech() {
        textToSpeech = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status != TextToSpeech.ERROR) {
                    textToSpeech.setLanguage(Locale.UK);
                }
            }
        });
    }

    private class ShowScoreTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected void onPreExecute() {
            txt_score.setVisibility(View.VISIBLE);
            txt_message.setVisibility(View.VISIBLE);
        }
        @Override
        protected Void doInBackground(Void... i) {
            try {
                Thread.sleep(SCORE_SHOW_TIMER);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void i) {
            txt_score.setVisibility(View.INVISIBLE);
            txt_message.setVisibility(View.INVISIBLE);
        }
    }

    private void startCollecting() {
        dataCollecting = true;
        attentivePredictedStates = new ArrayList<>();
        rightPredictedStates = new ArrayList<>();
        leftPredictedStates = new ArrayList<>();
    }

    private void stopCollecting() {
        dataCollecting = false;
        writeToFile(routeData);
    }

    private ArrayList<SegmentData> readDirectory() {
        ArrayList<SegmentData> tempList = new ArrayList<>();

        File path = Environment.getExternalStorageDirectory();
        File root = new File(path, getApplicationContext().getPackageName());

        //57.0,9.9 58.0,10.9 126478.29 Infinity GOOD 100.0 (ATTENTIVE,ATTENTIVE,ATTENTIVE,ATTENTIVE,ATTENTIVE) (1.0,1.0,2.0,2.0,3.0) (1.0,1.0,1.0,1.0,2.0)
        //58.0,10.9 59.0,11.9 125720.805 Infinity NEUTRAL 60.000004 (ATTENTIVE,ATTENTIVE,ATTENTIVE,INATTENTIVE,INATTENTIVE) (1.0,4.0,3.0,3.0,3.0) (1.0,2.0,2.0,2.0,2.0)

        for (File f : root.listFiles()) {
            System.out.println(f.getName());

            try {
                BufferedReader br = new BufferedReader(new FileReader(f));
                String line = "";

                while ((line = br.readLine()) != null) {
                    String[] temp = line.split(" ");
                    System.out.println(line);

                    // Start location
                    String[] startLoc = temp[0].split(",");
                    Location startLocation = new Location("Temp");
                    startLocation.setLatitude(Double.parseDouble(startLoc[0]));
                    startLocation.setLongitude(Double.parseDouble(startLoc[1]));

                    // End location
                    String[] endLoc = temp[1].split(",");
                    Location endLocation = new Location("Temp");
                    endLocation.setLatitude(Double.parseDouble(endLoc[0]));
                    endLocation.setLongitude(Double.parseDouble(endLoc[1]));

                    // distance and speed
                    float dist = Float.parseFloat(temp[2]);
                    float speed = 0.0f;
                    if (!temp[3].equals("Infinity")) {
                        speed = Float.parseFloat(temp[3]);
                    }

                    // Attentive state and score
                    String attentiveState = temp[4];
                    float score = Float.parseFloat(temp[5]);

                    // Attentive list
                    String[] attentiveness = temp[6].replace("(", "").replace(")", "").split(",");
                    ArrayList<String> attentiveList = new ArrayList<>();
                    for (String s : attentiveness) {
                        attentiveList.add(s);
                    }

                    // right list
                    String[] rightPreds = temp[7].replace("(", "").replace(")", "").split(",");
                    ArrayList<String> rightPredictions = new ArrayList<>();
                    for (String s : rightPreds) {
                        rightPredictions.add(s);
                    }

                    // left list
                    String[] leftPreds = temp[8].replace("(", "").replace(")", "").split(",");
                    ArrayList<String> leftPredictions = new ArrayList<>();
                    for (String s : leftPreds) {
                        leftPredictions.add(s);
                    }

                    SegmentData tempSegment = new SegmentData(startLocation, endLocation, attentiveList, rightPredictions, leftPredictions);
                    tempList.add(tempSegment);
                }
                br.close();
            } catch (IOException e) {
                //You'll need to add proper error handling here
            }
        }

        return tempList;
    }

    private void createNewDataFile(String fileName) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM-hh:mm");
        String currentTime = dateFormat.format(new Date());
        //String fileName = "route-" + currentTime;

        File path = Environment.getExternalStorageDirectory();
        File root = new File(path, getApplicationContext().getPackageName());
        if (!root.exists()) {
            root.mkdirs();
        }
        //Log.e(TAG, "File path: " + path);
        //Log.e(TAG, "File root: " + root);
        file = new File(root, fileName + " " + currentTime + ".txt");
    }

    private void writeToFile(ArrayList<SegmentData> routeList) {
        try {
            FileWriter fw = new FileWriter(file, true);
            BufferedWriter bw = new BufferedWriter(fw);

            for (SegmentData segment : routeList) {
                String dataString =
                        segment.getStartLocation().getLatitude() + "," + segment.getStartLocation().getLongitude() + " " +
                                segment.getEndLocation().getLatitude() + "," + segment.getEndLocation().getLongitude() + " " +
                                segment.getDistance() + " " +
                                segment.getSpeed() + " " +
                                segment.getAttentiveState() + " " +
                                segment.getScore() + " (" +
                                segment.getAttentivePredStatesString() + ") (" +
                                segment.getRightPredStatesString() + ") (" +
                                segment.getLeftPredStatesString() + ")";

                Log.e(TAG, "Data test: " + dataString);
                bw.write(String.format(dataString + "%n"));
            }
            bw.close();
        } catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
    }

    // Start bluetooth section
    // Create a BroadcastReceiver for ACTION_FOUND.
    //TODO: Slet det loart
    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                //String deviceName = device.getName();
                //String deviceHardwareAddress = device.getAddress(); // MAC address
            }
        }
    };

    private void ensureDiscoverable() {
        if (mBluetoothAdapter.getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    /**
     * Establish connection with other device
     *
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    private void connectDevice(int deviceNumber, boolean secure) {
        if (pairedDevices.size() > 0) {
            String deviceAddress = pairedDevices.get(deviceNumber).getAddress();
            BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(deviceAddress);
            mBluetoothServices.connect(device, false);
        }
    }

    // Create dialog to choose bluetooth device - Only paired devices show up!
    public Dialog chooseBluetoothDevice() {
        ArrayList<String> tempList = new ArrayList<>();
        for (BluetoothDevice bluetoothDevice : pairedDevices) {
            tempList.add(bluetoothDevice.getName());
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose Bluetooth device");
        builder.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_single_choice, tempList), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
                connectDevice(i, false);
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
        return builder.create();
    }

    // Send a message to connected bluetooth device
    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mBluetoothServices.getState() != BluetoothServices.STATE_CONNECTED) {
            Toast.makeText(getApplicationContext(), "Not Connected", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mBluetoothServices.write(send);
        }
    }

    private void addToStateList(String readMessage) {
        stateQueue.add(readMessage);
        int sameState = 0;
        int attentiveState = 0;
        //if (locationSpeed >= 20) {
        if (stateQueue.size() >= INSTANCES_BEFORE_WARNING) {
            for (String state : stateQueue) {
                if (state.equals(INATTENTIVE)) {
                    sameState++;
                } else if (state.equals(ATTENTIVE)) {
                    attentiveState++;
                }
            }

            if (attentiveState == INSTANCES_BEFORE_WARNING) {
                increasedIntensity = false;
            }

            if (sameState == INSTANCES_BEFORE_WARNING) {
                warnDriver();
                increasedIntensity = true;
                stateQueue = new CircularFifoQueue<>(INSTANCES_BEFORE_WARNING);
            }
        }
        //}
    }

    public void sendWarning(String attentiveType) {
        String warningMessage = "";

        if (attentiveType.equals(GOOD)) {
            warningMessage = "Good job on that last section";
        } else if (attentiveType.equals(NEUTRAL)) {
            warningMessage = "Remember to hold the steering wheel properly";
        } else if (attentiveType.equals(NEGATIVE)) {
            warningMessage = "Your driving should be improved";
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            textToSpeech.speak(warningMessage, TextToSpeech.QUEUE_FLUSH, null, null);
        } else {
            textToSpeech.speak(warningMessage, TextToSpeech.QUEUE_FLUSH, null);
        }
        txt_message.setText(warningMessage);
    }

    private String calculateAttentiveState(float score) {
        if (score >= 80) {
            return GOOD;
        } else if (score >= 60) {
            return NEUTRAL;
        } else {
            return NEGATIVE;
        }
    }

    private void warnDriver() {
        // Set Image / GIF
        if (increasedIntensity) {
            new ImageViewTask().execute();
            String textMessage = "Get your hand on the wheel!";
            // Play warning sound
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                textToSpeech.speak(textMessage, TextToSpeech.QUEUE_FLUSH, null, null);
            } else {
                textToSpeech.speak(textMessage, TextToSpeech.QUEUE_FLUSH, null);
            }
        } else {
            String textMessage = "Be attentive";
            // Play warning sound
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                textToSpeech.speak(textMessage, TextToSpeech.QUEUE_FLUSH, null, null);
            } else {
                textToSpeech.speak(textMessage, TextToSpeech.QUEUE_FLUSH, null);
            }
        }
    }

    // ASyncTask for update UI  // new ImageViewTask().execute(warning, null, null);
    private class ImageViewTask extends AsyncTask<Integer, Void, Void> {
        protected void onPreExecute() {
            gifImageView.setGifImageResource(R.drawable.gif_hypetrain);
        }

        protected Void doInBackground(Integer... params) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.interrupted();
            }
            return null;
        }

        protected void onPostExecute(Void result) {
            gifImageView.setGifImageResource(R.drawable.empty);
        }
    }

    private void handleBluetoothMessage(String message) {
        // Example message is: "INATTENTIVE 3.0 1.0" First value is predictedRight, second is predictedLeft
        String[] temp = message.split(" ");

        //txt_message.setText(message); //Testing: Shows bluetooth message in txt_message

        // addToStateList(temp[0]); // Starts immediate feedback
        if (dataCollecting) {
            attentivePredictedStates.add(temp[0]);

            if (temp.length == 3) {
                //if (!temp[1].equals("-1.0")) {
                rightPredictedStates.add(temp[1]);
                //}
                //if (!temp[2].equals("-1.0")) {
                leftPredictedStates.add(temp[2]);
                //}
            }
        }
    }

    /**
     * The Handler that gets information back from the BluetoothServices
     */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            //FragmentActivity activity = this;
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothServices.STATE_CONNECTED:
                            System.out.println("Connected_to: " + mConnectedDeviceName);
                            break;
                        case BluetoothServices.STATE_CONNECTING:
                            System.out.println("Connecting");
                            break;
                        case BluetoothServices.STATE_LISTEN:
                        case BluetoothServices.STATE_NONE:
                            System.out.println("Not_connected");
                            break;
                    }
                    break;
                case MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(writeBuf);
                    System.out.println("Me: " + writeMessage);
                    break;
                case MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    System.out.println(mConnectedDeviceName + ":  " + readMessage);
                    handleBluetoothMessage(readMessage);

                    break;
                case MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    if (null != getApplicationContext()) {
                        Toast.makeText(getApplicationContext(), "Connected to "
                                + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    }
                    break;
                case MESSAGE_TOAST:
                    if (null != getApplicationContext()) {
                        Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                                Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
        }
    };
}