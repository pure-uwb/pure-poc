package ch.ethz.pure.backend;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import fr.devnied.bitlib.BytesUtils;


public class CardBackend extends Thread {
    ServerSocket serverSocket;
    EmvTrace emvTrace;
    private final String TAG = this.getClass().toString();
    private String SELECT_1 = "00A404000E325041592E5359532E444446303100";

    private static CardBackend INSTANCE;
    private String info = "Initial info class";

    public static CardBackend getInstance(int port, EmvTrace emvTrace) {
        if(INSTANCE == null) {
            INSTANCE = new CardBackend(port, emvTrace);
        }

        return INSTANCE;
    }

    private CardBackend(int port, EmvTrace emvTrace) {
        try {
            serverSocket = new ServerSocket(port);
            this.emvTrace = emvTrace;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        try {
            while (true) {
                Socket socket = serverSocket.accept();
                //read APDU command from socket
                DataInputStream in = new DataInputStream(socket.getInputStream());
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                baos.write(buffer, 0, in.read(buffer));
                byte[] cmd = baos.toByteArray();
                if (BytesUtils.bytesToStringNoSpace(cmd).equals(SELECT_1)){
                    emvTrace.resetResponses();
                    Log.i(TAG,"Received first select, reset responces");
                }

                byte[] resp = emvTrace.getResponse();
                if (!emvTrace.responsesHasNext()){
                    emvTrace.resetResponses();
                }
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                out.write(resp);

                //close resources
                baos.close();
                out.close();
                in.close();
                socket.close();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error: " + e);
        }
    }
}
