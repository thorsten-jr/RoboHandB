package de.sinnemann.robohand;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;


public class MainActivity extends AppCompatActivity {

    /** Names of possible target device to connect to. */
    private static final List<String> NAMES = Arrays.asList(new String[]{"BTM-222","HC-05","HC-06","linvor"});

    /** Integer constants for the message handler. */
    public static final int REPLACE_TEXT = 0;
    public static final int APPEND_TEXT = 1;
    public static final int DISABLE_BUTTONS = 2;
    public static final int ENABLE_BUTTONS = 3;

    /** Using the class name to tag log messages. */
    private final String TAG = getClass().getName();

    /** Handles messages from threads that update the view */
    private final UpdateHandler updateHandler = new UpdateHandler(this);

    /** Handle press on the start button */
    public void button1Clicked(View view) {
        Log.d(TAG, "Start button clicked");

        // The Bluetooth communication must be done on a separate thread
        // because it takes a while and would freeze the UI otherwise.
        new Thread() {
            @Override
            public void run() {

                // Disable the start button
                Message.obtain(updateHandler, DISABLE_BUTTONS).sendToTarget();

                BluetoothSocket socket=null;
                InputStream istream=null;
                OutputStream ostream=null;
                try {

                    // Open the bluetooth adapter
                    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                    if (adapter==null) {
                        Message.obtain(updateHandler, APPEND_TEXT, "Missing Bluetooth adapter\n").sendToTarget();
                        return;
                    }

                    // Find the target device
                    BluetoothDevice targetDevice=findTarget(adapter,NAMES);
                    if (targetDevice==null) {
                        Message.obtain(updateHandler, APPEND_TEXT, "No Bluetooth device with name "+NAMES+" found.\n").sendToTarget();
                        Message.obtain(updateHandler, APPEND_TEXT, "Pair a Bluetooth device in the Android settings, then retry.\n").sendToTarget();
                        return;
                    }
                    Message.obtain(updateHandler, APPEND_TEXT, "Using " + targetDevice.getAddress()+"\n").sendToTarget();

                    // Open a socket to communicate with the device.
                    Message.obtain(updateHandler, APPEND_TEXT, "Open connection...\n").sendToTarget();
                    socket = targetDevice.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
                    socket.connect();
                    istream = socket.getInputStream();
                    ostream = socket.getOutputStream();
                    Message.obtain(updateHandler, APPEND_TEXT, "Connection is open\n").sendToTarget();

                    // Send a string
                    String message = "Hello\n";
                    Message.obtain(updateHandler, APPEND_TEXT, "Sending: "+message+"\n").sendToTarget();
                    ostream.write(message.getBytes());
                    ostream.flush();

                    // Wait one second for the response
                    Message.obtain(updateHandler, APPEND_TEXT, "Waiting 1s for the response...\n").sendToTarget();
                    Thread.sleep(1000);

                    // Retrieve the response
                    String response=readIntoString(istream);
                    Message.obtain(updateHandler, APPEND_TEXT, "Received: " + response+"\n").sendToTarget();

                } catch (Exception e) {
                    Message.obtain(updateHandler, APPEND_TEXT, "Error: " + e.getMessage()+"\n").sendToTarget();
                    e.printStackTrace();
                }
                finally {
                    // close streams and the socket
                    Message.obtain(updateHandler, APPEND_TEXT, "Closing connection\n").sendToTarget();
                    if (istream!=null) {
                        try {
                            istream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    if (ostream!=null) {
                        try {
                            ostream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    if (socket!=null) {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    // Enable the start button
                    Message.obtain(updateHandler, ENABLE_BUTTONS).sendToTarget();
                }
            }
        }.start();
    }


    /** Read all available bytes from an input stream and return them as String. */
    private String readIntoString(InputStream in) throws IOException {
        int avail = in.available();
        Log.d(TAG, "Received " + avail + " bytes");
        byte[] buffer = new byte[avail];
        in.read(buffer);
        return new String(buffer, Charset.forName("ISO-8859-1"));
    }

    /** Find the target Bluetooth device by name. */
    private BluetoothDevice findTarget(BluetoothAdapter adapter, Collection<String> allowedNames) {
        // List all paired devices and select a target
        Message.obtain(updateHandler, REPLACE_TEXT, "The following devices are paired\n").sendToTarget();
        Set<BluetoothDevice> pairedDevices = adapter.getBondedDevices();
        Iterator<BluetoothDevice> iterator = pairedDevices.iterator();
        BluetoothDevice targetDevice = null;
        while (iterator.hasNext()) {
            BluetoothDevice device = iterator.next();
            Message.obtain(updateHandler, APPEND_TEXT, device.getAddress() + " = " + device.getName()+"\n").sendToTarget();
            if (allowedNames.contains(device.getName())) {
                targetDevice = device;
            }
        }
        return targetDevice;
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_activity_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_quit:
                System.exit(0);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    /** Handles messages from threads that update the view */
    private static class UpdateHandler extends Handler {

        /** Reference to the related activity must be weak to prevent a memory leak */
        private final WeakReference<Activity> wActivity;

        /** Constructor */
        public UpdateHandler(Activity activity) {
            wActivity = new WeakReference<Activity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            Activity activity = wActivity.get();
            if (activity != null) {
                TextView textField1 = (TextView) activity.findViewById(R.id.textView1);
                TextView textField2 = (TextView) activity.findViewById(R.id.textView2);
                Button button1 = (Button) activity.findViewById(R.id.button1);
                Button button2 = (Button) activity.findViewById(R.id.button2);
                Button button3 = (Button) activity.findViewById(R.id.button3);
                Button button4 = (Button) activity.findViewById(R.id.button4);
                switch (msg.what) {
                    case REPLACE_TEXT:
                        textField2.setText(msg.obj.toString());
                        break;
                    case APPEND_TEXT:
                        textField2.append(msg.obj.toString());
                        break;
                    case DISABLE_BUTTONS:
                        button1.setEnabled(false);
                        break;
                    case ENABLE_BUTTONS:
                        button1.setEnabled(true);
                        break;

                }
                textField1.invalidate();
            }
        }
    }

}
