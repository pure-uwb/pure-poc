package ch.ethz.nfcrelay.nfc;

import java.math.BigInteger;
import java.util.Arrays;

public class Util {
    public static final byte[] PPSE_APDU_SELECT = hexToBytes("00A404000E325041592E5359532E444446303100");
    public static final byte[] EMPTY_APDU_RESPONSE = new byte[0];
    private final static char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    public static boolean responseOK(byte[] recv) {
        //check SW1SW2 = '0x9000'
        return recv != null && recv.length >= 2
                && recv[recv.length - 2] == (byte) 0x90 && recv[recv.length - 1] == (byte) 0x00;
    }

    //taken from https://stackoverflow.com/a/140861
    public static byte[] hexToBytes(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    //taken from https://stackoverflow.com/a/9855338
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        int c = 0;
        for (byte b : bytes) {
            int v = b & 0xFF;
            hexChars[c++] = HEX_ARRAY[v >>> 4];
            hexChars[c++] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    //crypto methods from here on use code from https://github.com/johnzweng/android-emv-key-test
    private static byte[] getUnsignedBytes(BigInteger bigint) {
        if (bigint.compareTo(BigInteger.ZERO) < 0) {
            return null;
        }
        final byte[] signedBytes = bigint.toByteArray();
        if (signedBytes[0] == 0x00)
            return Arrays.copyOfRange(signedBytes, 1, signedBytes.length);

        return signedBytes;
    }

    public static boolean isGPO(byte[] cmd) {
        return cmd != null && cmd.length >= 2 && cmd[0] == (byte) 0x80 && cmd[1] == (byte) 0xA8;
    }
}
