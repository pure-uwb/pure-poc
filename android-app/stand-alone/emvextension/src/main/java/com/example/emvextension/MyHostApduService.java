package com.example.emvextension;

import android.content.Context;
import android.content.Intent;
import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class MyHostApduService extends HostApduService {
    public final String TAG = "MyHostApduService";

    public static final String ACTION_COMMAND = "ACTION_COMMAND";
    public static final String EXTRA_COMMAND = "EXTRA_COMMAND";

    // Abstract super class constant overrides
    public static final String KEY_DATA = "data";
    public static final int MSG_RESPONSE_APDU = 1;

    @Override
    public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
        Log.i(TAG, "processCommandAPDU");
        Context context = getApplicationContext();
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(context);
        //if (Arrays.equals(MyHostApduService.PPSE_APDU_SELECT_BYTES, commandApdu)) {
        Intent intent = new Intent(ACTION_COMMAND);
        intent.putExtra(EXTRA_COMMAND, commandApdu);

        lbm.sendBroadcast(intent);

        return null;
        // ^ Note the need to return null so that the other end waits for the
        //   activity to send the response via the Messenger handle
    }

    @Override
    public void onDeactivated(int reason) {
        Log.i(TAG, "Deactivated");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Service created");
    }
}


