package com.example.emvextension.channel;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.emvextension.MyHostApduService;

public class CardNfcChannel extends Channel {
    private final String TAG = "CardNfcChannel";
    public static final String EVT_CMD = "EVT_CMD";


    private Messenger mAPDUMessenger;
    private Activity activity;

    private byte [] command;

    public CardNfcChannel(Activity activity) {
        this.activity = activity;
        Intent apduIntent = new Intent(activity, MyHostApduService.class);
        boolean ret = activity.bindService(apduIntent, mAPDUConnection, Context.BIND_AUTO_CREATE);
        Log.i("MainActivity", "Bind successful\t"+ ret );
    }
    @Override
    public byte[] read() {
        return command;
    }

    private final ServiceConnection mAPDUConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // The HostApduService has a final override on the onBind() service method that returns
            // an IMessageHandler interface that we can grab and use to send messages back to the
            // terminal - would be better to get a handle to the running instance of the service so
            // that we could make use of the HostApduService#sendResponseApdu public method
            mAPDUMessenger = new Messenger(service);
            registerAPDUMessengerIntentFilters();
            // ^ This method sets up my handlers for local broadcast messages my BroadcastReceiver processes.
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i("ServiceConnection", "Service disconnected");
        }

        @Override
        public void onBindingDied(ComponentName name) {
            ServiceConnection.super.onBindingDied(name);
        }

        @Override
        public void onNullBinding(ComponentName name) {
            ServiceConnection.super.onNullBinding(name);
        }
    };

    private void registerAPDUMessengerIntentFilters() {

        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(activity);

        IntentFilter intentFilter = new IntentFilter(MyHostApduService.ACTION_COMMAND);
        lbm.registerReceiver(apduMessageBroadcastReceiver, intentFilter);
    }

    BroadcastReceiver apduMessageBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "onReceive");
            if (intent.getAction().equals(MyHostApduService.ACTION_COMMAND)) {
                command = intent.getByteArrayExtra(MyHostApduService.EXTRA_COMMAND);
                notifyAllListeners(EVT_CMD, null, null);

                if (command == null){
                    throw new RuntimeException("Command was null");
                }
            }


        }
    };

    public void write(byte[] responseApdu) {
        Log.i(TAG, "SendResponseApdu");
        Message responseMsg = Message.obtain(null, MyHostApduService.MSG_RESPONSE_APDU);
        // ^ Note here that because MSG_RESPONSE_APDU is the message type
        //   defined in the abstract HostApduService class, I had to override
        //   the definition in my subclass to expose it for use from MyActivity.
        //   Same with the KEY_DATA constant value below.
        Bundle dataBundle = new Bundle();
        dataBundle.putByteArray(MyHostApduService.KEY_DATA, responseApdu);
        responseMsg.setData(dataBundle);
        try {
            mAPDUMessenger.send(responseMsg);
        } catch (RemoteException e) {
            // Do something with the failed message
        }
    }
}
