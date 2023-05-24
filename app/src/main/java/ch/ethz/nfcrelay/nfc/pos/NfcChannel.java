package ch.ethz.nfcrelay.nfc.pos;

import android.nfc.tech.IsoDep;

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
            response = tag.transceive(payload);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
