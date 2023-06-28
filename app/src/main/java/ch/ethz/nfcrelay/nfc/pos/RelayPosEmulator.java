package ch.ethz.nfcrelay.nfc.pos;

import android.nfc.tech.IsoDep;
import android.util.Log;

import com.example.emvextension.protocol.ProtocolModifier;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.Semaphore;

import ch.ethz.nfcrelay.MainActivity;
import ch.ethz.nfcrelay.R;
import ch.ethz.nfcrelay.nfc.Util;

public class RelayPosEmulator extends Thread {

    private final MainActivity activity;
    private final IsoDep tagComm;
    private final ProtocolModifier modifier;
    private final Semaphore semaphore;

    public RelayPosEmulator(MainActivity activity, IsoDep tagComm, ProtocolModifier modifier, Semaphore s) {
        this.activity = activity;
        this.tagComm = tagComm;
        this.modifier = modifier;
        this.semaphore = s;
        try {
            if (tagComm != null && !tagComm.isConnected())
                tagComm.connect();

        } catch (IOException e) {
            activity.showErrorOrWarning(e, true);
        }
    }

    @Override
    public void run() {
        try {
            //start connection checker
            new Thread(() -> {
                while (tagComm != null && tagComm.isConnected()) {
                    try {
                        //every second check if a card is connected
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        //activity.showErrorOrWarning(e, true);
                    }
                }
                activity.updateStatus(activity.getString(R.string.waiting_for_card),
                        false);
            }).start();

            //execute transaction
            ServerSocket serverSocket = activity.getServerSocket();
            Log.i(this.getName(), "Wait for merchant to start a transaction on thread " + Thread.currentThread());
//            try{
//                semaphore.acquire();
//            }catch(InterruptedException e){
//                Log.w(this.getName(), "Tag was lost, thread killed");
//                return;
//            }
            Log.i(this.getName(), "Transaction started");
            while (true) {
                //waiting for connection with remote card emulator
                Socket socket;
                Log.i(this.getName(), "Before accept");
                socket = serverSocket.accept();
                Log.i(this.getName(), "After accept");

                //read APDU command from socket
                DataInputStream in = new DataInputStream(socket.getInputStream());
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                baos.write(buffer, 0, in.read(buffer));
                byte[] cmd = baos.toByteArray();
                //refresh GUI with command
                Log.i(this.getName(), "[C-APDU] " + Util.bytesToHex(cmd));
                activity.appendToLog("[C-APDU] " + Util.bytesToHex(cmd));

                //send APDU command to the card and receive APDU response
                //byte[] resp = Util.hexToBytes("6F3A840E325041592E5359532E 444463031A528BF0C2561234F07A0000000041010500A4D6173746572436172648701019F0A0800010502000000009000");//tagComm.transceive(cmd);
                /*
                byte [] gen_ac_command = new byte[]{(byte)0x80, (byte)0xAE};
                Tested 4 phones relay with 200 ms sleep and works
                    if(Arrays.equals(gen_ac_command, Arrays.copyOfRange(cmd, 0, 2))){
                        Log.i("RelayPosEmulator", "GEN_AC_COMMAND, sleep...");
                        Thread.sleep(200);
                    }
                 */
                if(tagComm == null || !tagComm.isConnected()){
                    return;
                }
                byte[] resp = tagComm.transceive(cmd);

                //refresh GUI with response

                // HERE ON gen_ac_command do:
                // 1. extract AC
                // 2. execute extension protocol
                // 3. Send AC to the socket only if extension protocol succeeded
                resp = modifier.parse(cmd, resp);

                activity.appendToLog("[R-APDU] " + Util.bytesToHex(resp));
                Log.i(this.getName(), "[R-APDU] " + Util.bytesToHex(resp));

                //write APDU response into the mSocket
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                out.write(resp);

                //close resources
                baos.close();
                out.close();
                in.close();
                socket.close();
                if(modifier.isProtocolFinished()){
                    Log.i("RelayPosEmulator", "Protocol finished");
                    break;
                }
            }
            tagComm.close();
        } catch (Exception e) {
            activity.showErrorOrWarning(e, true);
        }
    }
}
