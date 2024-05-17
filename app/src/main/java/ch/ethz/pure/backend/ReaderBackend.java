package ch.ethz.pure.backend;

import static ch.ethz.pure.nfc.Util.bytesToHex;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.util.concurrent.Semaphore;

public class ReaderBackend extends Thread {

    String ip;
    int port;
    EmvTrace emvTrace;
    private final Semaphore s;
    private final String TAG = this.getClass().toString();

    public ReaderBackend(String ip, int port, EmvTrace emvTrace, Semaphore s) {
        this.ip = ip;
        this.port = port;
        this.emvTrace = emvTrace;
        this.s = s;
    }

    @Override
    public void run() {
        try {
            while (emvTrace.commandsHasNext()) {

                if (Thread.interrupted()) {
                    // We've been interrupted: no more crunching.
                    return;
                }
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
            }
        } catch (Exception e) {
            Log.e(TAG, "Error: " + e);
        }
    }
}