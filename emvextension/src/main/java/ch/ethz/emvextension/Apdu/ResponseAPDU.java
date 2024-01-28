 package ch.ethz.emvextension.Apdu;

import android.util.Log;

import java.util.Arrays;

public class ResponseAPDU {
    final byte[] apdu;

    public ResponseAPDU(byte[] bytes) {
        if (bytes.length < 2) {
            throw new IllegalArgumentException("APDU must be at least 2 bytes!");
        }
        this.apdu = bytes.clone();
    }

    public int getSW1() {
        return apdu[apdu.length - 2] & 0xff;
    }

    public int getSW2() {
        return apdu[apdu.length - 1] & 0xff;
    }

    public int getSW() {
        return (getSW1() << 8) | getSW2();
    }

    public byte[] getData() {
        return Arrays.copyOf(apdu, apdu.length - 2);
    }

    public byte[] getBytes() {
        return apdu.clone();
    }

    public byte[] getSWBytes() {
        return Arrays.copyOfRange(apdu, apdu.length - 2, apdu.length);
    }

    public ResponseAPDU(int sw1, int sw2, byte[] data) {
        apdu = new byte[data.length + 2];
        int dataOffset = 2;
        apdu[apdu.length- 2] = (byte) sw1;
        apdu[apdu.length -1] = (byte) sw2;
        Log.i("APDU", "Data:"  + getHex(data));
        System.arraycopy(data, 0, apdu, 0, data.length);
        Log.i("APDU", "apdu:"  + getHex(apdu));
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
