package ch.ethz.nfcrelay.nfc.pos;

import android.nfc.tech.IsoDep;
import android.util.Log;

import com.example.emvextension.channel.Channel;

import java.io.IOException;

public class NfcChannel extends Channel {

    private IsoDep tag;
    private byte [] response;

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
            Log.i("Timer", "Time: " + ((float)(stop - start)/1000000) +"\t Cmd_len:" + payload.length +  "\tResp_len: "+ response.length);



        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
