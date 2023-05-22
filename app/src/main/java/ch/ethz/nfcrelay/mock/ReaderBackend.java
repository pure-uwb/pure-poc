package ch.ethz.nfcrelay.mock;

import static ch.ethz.nfcrelay.nfc.Util.bytesToHex;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import ch.ethz.nfcrelay.nfc.Util;

public class ReaderBackend extends Thread {

    String ip;
    int port;
    EmvTrace emvTrace;
    private final String TAG = this.getClass().toString();

    public ReaderBackend(String ip, int port, EmvTrace emvTrace) {
        this.ip = ip;
        this.port = port;
        this.emvTrace = emvTrace;
    }

    @Override
    public void run() {
        try {
            while (emvTrace.commandsHasNext()) {
                Log.i(TAG, "New command");
                Socket socket = new Socket(ip, port);
                //waiting for connection with remote card emulator
                byte[] cmd = emvTrace.getCommand();
                //write APDU command to socket
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                out.write(cmd);

                //read APDU response
                DataInputStream in = new DataInputStream(socket.getInputStream());
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int length = in.read(buffer);

                //process APDU response accordingly


                //close resources
                baos.close();
                in.close();
                out.close();
                socket.close();
                Log.i(TAG, "Sent CMD: " + bytesToHex(cmd));

            }
        } catch (Exception e) {
            Log.e(TAG, "Error: " + e);
        }
    }
}