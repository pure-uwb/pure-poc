package com.example.emvextension.channel;

import static android.nfc.NfcAdapter.FLAG_READER_NFC_A;
import static android.nfc.NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK;

import android.app.Activity;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.util.Log;

import java.io.IOException;
import java.util.Objects;


public class ReaderNfcChannel extends Channel implements NfcAdapter.ReaderCallback {
    public static final byte[] SelectAID = new byte[]{
            (byte) 0xF0, (byte) 0x01, (byte) 0x02, (byte) 0x03,
            (byte) 0x04, (byte) 0x05, (byte) 0x06};

    public static final String EVT_NEW_TAG = "NEW_TAG";
    private static final String EVT_RECEIVED_RESP = "RECEIVED_RES";


    private static final String TAG = "NfcChannel";
    private Tag tag;
    private byte[] response;
    private IsoDep isoDep;

    public ReaderNfcChannel(Activity activity) {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(activity.getApplicationContext());
        adapter.enableReaderMode(activity, this, FLAG_READER_NFC_A | FLAG_READER_SKIP_NDEF_CHECK, null);
    }

    @Override
    public byte[] read() {
        if (response == null) {
            throw new NullPointerException("Response is not set. Can be because a read happened before a write");
        }
        return response;
    }

    @Override
    public void write(byte[] payload) {
        isoDep = IsoDep.get(tag);

        response = null;
        if (isoDep != null) {
            try {
                Log.i("ReaderNfcChannel", "Write: " + getHex(payload));
                Long start = System.nanoTime();
                byte[] result = isoDep.transceive(payload);
                Long finish = System.nanoTime();
                Log.i("ReaderNfc", "NFC ping pong time" + ((float) (finish - start)) / 1000000);
                response = result;
                return;
            } catch (IOException ex) {
                try {
                    Log.e(TAG, "IO Exception");
                    Log.e(TAG, Objects.requireNonNull(ex.getMessage()));
                    isoDep.close();
                } catch (Exception ignored) {
                    Log.e(TAG, "Ignored");
                    throw new RuntimeException();
                }
            }
        }
        throw new RuntimeException();
    }

    public void closeChannel() {
        isoDep = IsoDep.get(tag);
        try {
            Log.e(TAG, "IO Exception");
            isoDep.close();
        } catch (Exception ignored) {
            Log.e(TAG, "Ignored");
            throw new RuntimeException();
        }
    }

    public void onTagDiscovered(Tag tag) {
        Log.i(TAG, "############ TAG DISCOVERED ##############");
        this.tag = tag;
        IsoDep isoDep = IsoDep.get(tag);
        try {
            isoDep.connect();
        } catch (IOException e) {
            Log.e(TAG, "Failed connect");
            throw new RuntimeException(e);
        }
        notifyAllListeners(EVT_NEW_TAG, null, null);
    }

    private String getHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = bytes.length - 1; i >= 0; --i) {
            int b = bytes[i] & 0xff;
            if (b < 0x10)
                sb.append('0');
            sb.append(Integer.toHexString(b));
            if (i > 0) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }

}
