package ch.ethz.pure.nfc.card;

import static ch.ethz.pure.nfc.Util.bytesToHex;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;

import ch.ethz.emvextension.protocol.ProtocolModifier;
import ch.ethz.pure.MainActivity;
import ch.ethz.pure.nfc.ProtocolModifierImpl;
import ch.ethz.pure.nfc.Util;
import ch.ethz.pure.nfc.card.hce.EMVraceApduService;

public class ResponseResolver extends Thread {
    private final MainActivity activity;
    private final EMVraceApduService hostApduService;

    private final String ip;
    private final int port;
    private final byte[] cmd;
    private final boolean isPPSECmd;
    private final ProtocolModifier modifier;

    private final String TAG = ResponseResolver.class.getName();

    public ResponseResolver(EMVraceApduService hostApduService, String ip, int port,
                            byte[] cmd, boolean isPPSECmd, MainActivity activity, ProtocolModifier modifier) {
        this.hostApduService = hostApduService;
        this.ip = ip;
        this.port = port;
        this.cmd = cmd;
        this.isPPSECmd = isPPSECmd;
        this.activity = activity;
        this.modifier = modifier;
    }

    @Override
    public void run() {
        // Once a command cmd is received:
        // 1. connect to the backend at ip:port
        // 2. Get the response
        // 3. Send the response back in the NFC channel
        try {
            Log.i(TAG, "CMD:" + bytesToHex(cmd));
            Log.i(TAG, "Connect to backend: " + ip + ":" + port);
            //create socket
            Socket socket = new Socket(ip, port);

            //write APDU command to socket
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.write(cmd);
            //read APDU response
            DataInputStream in = new DataInputStream(socket.getInputStream());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length = in.read(buffer);

            //process APDU response accordingly
            if (length >= 0 && length < 1024) {
                baos.write(buffer, 0, length);
                byte[] resp = baos.toByteArray();
                boolean responseOK = Util.responseOK(resp);

                if (isPPSECmd && responseOK) { //launch Wallet Activity
                    Log.i(TAG, "Start card activity");
                    activity.startCardEmulator();
                } else if (!isPPSECmd && hostApduService != null) {
                    // HERE ON gen_ac_command do:
                    // 1. extract AC
                    // 2. execute extension protocol
                    // 3. Send AC to the socket only if extension protocol succeeded
                    resp = modifier.parse(cmd, resp);
                    hostApduService.sendResponseApdu(resp);
                    Log.i(TAG, "RESP: " + bytesToHex(resp));
                }
            }

            //close resources
            baos.close();
            in.close();
            out.close();
            socket.close();

        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }
}
