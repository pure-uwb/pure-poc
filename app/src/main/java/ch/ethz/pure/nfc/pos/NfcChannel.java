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
            Long start = System.nanoTime();
            response = tag.transceive(payload);
            Long stop = System.nanoTime();
            Log.i("Timer", "[EXT]\tTime: " + ((float) (stop - start) / 1000000) + "\t Cmd_len:" + payload.length + "\tResp_len: " + response.length);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
