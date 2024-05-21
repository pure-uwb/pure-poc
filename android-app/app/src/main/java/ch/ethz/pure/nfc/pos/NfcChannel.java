package ch.ethz.pure.nfc.pos;

import android.nfc.tech.IsoDep;
import android.util.Log;

import java.io.IOException;

import ch.ethz.emvextension.channel.Channel;

public class NfcChannel extends Channel {

    private final IsoDep tag;
    private byte[] response;

    public NfcChannel(IsoDep tag) {
        this.tag = tag;
    }

    @Override
    public byte[] read() {
        return response;
    }

    @Override
    public void write(byte[] payload) {
        try {
            response = tag.transceive(payload);
            Log.i("NfcChannel", "NFC tx: " + payload.length + "bytes\n" +
                                         "NFC rx: " + response.length + "bytes\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
