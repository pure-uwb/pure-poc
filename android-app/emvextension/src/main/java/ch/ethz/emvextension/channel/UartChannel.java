package ch.ethz.emvextension.channel;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.example.emvextension.BuildConfig;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.List;

import ch.ethz.emvextension.jobs.EmvParserJob;
import fr.devnied.bitlib.BytesUtils;

public class UartChannel extends Channel implements SerialInputOutputManager.Listener {
    private static final int WRITE_WAIT_MILLIS = 100;
    public static final String READ_DATA = "READ_DATA";
    private final Activity activity;
    private Boolean connect;
    private UsbSerialPort port;
    private final int messageSize = 25;
    private int received = 0;
    private final byte[] messageBuf;
    private final String TAG =  UartChannel.class.getName();

    private static final String INTENT_ACTION_GRANT_USB = BuildConfig.LIBRARY_PACKAGE_NAME + ".GRANT_USB";
    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (INTENT_ACTION_GRANT_USB.equals(intent.getAction())) {
                Boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                connect();
            }
        }
    };
    private static UartChannel channel = null;

    public static UartChannel getChannel(Activity activity) {
        if (channel == null) {
            channel = new UartChannel(activity);
        }
        return channel;
    }

    private UartChannel(Activity activity) {
        this.activity = activity;
        activity.registerReceiver(broadcastReceiver, new IntentFilter(INTENT_ACTION_GRANT_USB));
        messageBuf = new byte[messageSize];
        connect();
    }

    private void connect() {
        UsbManager manager = (UsbManager) activity.getBaseContext().getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        if (availableDrivers.isEmpty()) {
            return;
        }

        // Open a connection to the first available driver.
        UsbSerialDriver driver = availableDrivers.get(0);
        if (!manager.hasPermission(driver.getDevice())) {
            Log.i(TAG, "Request permission");
            int flags = PendingIntent.FLAG_MUTABLE;
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(activity, 0, new Intent(INTENT_ACTION_GRANT_USB), flags);
            manager.requestPermission(driver.getDevice(), usbPermissionIntent);
            return;
        }
        UsbDeviceConnection connection = manager.openDevice(driver.getDevice());

        if (connection == null) {
            // add UsbManager.requestPermission(driver.getDevice(), ..) handling here
            Log.e(TAG, "Failed to create a connection to Uart");
            return;
        }

        port = driver.getPorts().get(0); // Most devices have just one port (port 0)
        try {
            port.open(connection);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        SerialInputOutputManager usbIoManager = new SerialInputOutputManager(port, this);
        Log.i(TAG, "Buf len:" + usbIoManager.getReadBufferSize());
        try {
            port.setDTR(true); // for arduino, ...
            port.setRTS(true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
//        usbIoManager.setReadTimeout(1000);
        usbIoManager.start();
    }

    @Override
    public byte[] read() {
        byte[] buf = new byte[30];
        try {
            int read = port.read(buf, 20);
            Log.i(TAG, "Read " + read + " bytes");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return buf;
    }

    @Override
    public void write(byte[] payload) {
        try {
            port.write(payload, 20);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onNewData(byte[] data) {
        Log.i(TAG, "Received data: " + BytesUtils.bytesToString(data) + "Len:" + data.length);
        if (data.length + received >= messageSize) {
            System.arraycopy(data, 0, messageBuf, received, messageSize - received);
            received = 0;
            Log.i(TAG, "Received message: " + BytesUtils.bytesToString(messageBuf) + "Len:" + messageBuf.length);
            notifyAllListeners(READ_DATA, null, messageBuf);
            return;
        }
        System.arraycopy(data, 0, messageBuf, received, data.length);
        received += data.length;

    }

    @Override
    public void onRunError(Exception e) {

    }
}
