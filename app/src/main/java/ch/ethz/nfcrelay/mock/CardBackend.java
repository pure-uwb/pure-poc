package ch.ethz.nfcrelay.mock;

import static com.github.devnied.emvnfccard.utils.CommandApdu.getCommandEnum;
import android.util.Log;
import com.github.devnied.emvnfccard.enums.CommandEnum;

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
                //waiting for connection with remote card emulator
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
                /*
                Tested 4 phones relay with 200 ms sleep and works
                    if(Arrays.equals(gen_ac_command, Arrays.copyOfRange(cmd, 0, 2))){
                        Log.i("RelayPosEmulator", "GEN_AC_COMMAND, sleep...");
                        Thread.sleep(200);
                    }
                 */

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
