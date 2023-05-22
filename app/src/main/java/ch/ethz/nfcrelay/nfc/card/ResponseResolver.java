package ch.ethz.nfcrelay.nfc.card;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;

import ch.ethz.nfcrelay.MainActivity;
import ch.ethz.nfcrelay.nfc.Util;
import ch.ethz.nfcrelay.nfc.card.hce.EMVraceApduService;

public class ResponseResolver extends Thread {
    private final MainActivity activity;
    private final EMVraceApduService hostApduService;

    private final String ip;
    private final int port;
    private final byte[] cmd;
    private final boolean isPPSECmd;

    public
    ResponseResolver(EMVraceApduService hostApduService, String ip, int port,
                            byte[] cmd, boolean isPPSECmd, MainActivity activity) {
        this.hostApduService = hostApduService;
        this.ip = ip;
        this.port = port;
        this.cmd = cmd;
        this.isPPSECmd = isPPSECmd;
        this.activity = activity;
    }

    @Override
    public void run() {
        try {
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

                if (isPPSECmd && responseOK) //launch Wallet Activity
                    activity.startCardEmulator();

                else if (!isPPSECmd && hostApduService != null)
                    hostApduService.sendResponseApdu(resp);
            }

            //close resources
            baos.close();
            in.close();
            out.close();
            socket.close();

        } catch (Exception e) {
            if (isPPSECmd)
                activity.showErrorOrWarning(e, false);
        }
    }
}
