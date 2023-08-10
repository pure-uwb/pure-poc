package ch.ethz.nfcrelay.nfc.pos;

import android.content.Context;
import android.nfc.tech.IsoDep;
import android.util.Log;

import com.example.emvextension.protocol.ProtocolModifier;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Semaphore;

import ch.ethz.nfcrelay.nfc.BuildSettings;
import ch.ethz.nfcrelay.MainActivity;
import ch.ethz.nfcrelay.R;
import ch.ethz.nfcrelay.nfc.Util;

public class RelayPosEmulator extends Thread {

    private final MainActivity activity;
    private final IsoDep tagComm;
    private final ProtocolModifier modifier;
    private final Semaphore semaphore;
    private Long total_time_start;
    private Long total_time_finish;
    private Long summed;
    private final static List<String> commands = Arrays.asList("SEL1", "SEL2", "GPO", "RR1", "RR2", "RR3", "RR4", "GEN_AC",
                                                                "SEL1_MOD", "SEL2_MOD", "GPO_MOD", "RR1_MOD", "RR2_MOD", "RR3_MOD", "RR4_MOD", "GEN_AC_MOD");
    private List<String> timings;
    private List<String> timingsMod;

    public RelayPosEmulator(MainActivity activity, IsoDep tagComm, ProtocolModifier modifier, Semaphore s) {
        this.activity = activity;
        this.tagComm = tagComm;
        this.modifier = modifier;
        this.semaphore = s;

        total_time_start = null;
        total_time_finish = null;
        summed = 0L;
        /* NOTE on Timings
        * Timings collects the times needed for each ping pong.
        * The GPO ping-pong contains also the DH .
        * The GEN AC ping-pong contains also the additional signature.
        * Timings mod collects the time needed to execute the modification.
        * The timings_mod for GPO and GEN_AC account for the DH and the additional signature
        * */

        timings = new LinkedList<>();
        timingsMod = new LinkedList<>();
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
                Long start = System.nanoTime();
                Socket socket;
                Log.i(this.getName(), "Before accept");
                socket = serverSocket.accept();
                Log.i(this.getName(), "After accept");
                if (total_time_start == null)
                    total_time_start = start;
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
                Long start_modifier = System.nanoTime();
                resp = modifier.parse(cmd, resp);
                Long end_modifier = System.nanoTime();

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

                Long stop = System.nanoTime();
                Log.i("Timer", "[STD]\t" + "Time: " + ((float)(stop - start)/1000000) +"\tModifier: "+ ((float)(end_modifier - start_modifier)/1000000) );
                timings.add(String.format("%.2f", (float)(stop - start)/1000000));
                timingsMod.add(String.format("%.2f", ((float)(end_modifier - start_modifier)/1000000)));
                summed += (stop-start);
                if(modifier.isProtocolFinished()){
                    Log.i("RelayPosEmulator", "Protocol finished");
                    break;
                }
            }
            total_time_finish = System.nanoTime();
            Log.i("Timer", "[TOT]\t" + "Time: " + ((float)(total_time_finish - total_time_start)/1000000));
            Log.i("Timer", "[SUM]\t" + "Time: " + ((float)(summed)/1000000));
            Log.i("Timer", "#############################################################################");
            saveTimings();
        } catch (Exception e) {
            activity.showErrorOrWarning(e, true);
        }
    }

    private void saveTimings(){
        Log.i("Timings", "SAVING FILE in " + activity.getFilesDir());
        if (!Arrays.asList(activity.fileList()).contains(BuildSettings.outputFileName)){
            try (FileOutputStream fos = activity.openFileOutput(BuildSettings.outputFileName, Context.MODE_PRIVATE)) {
                fos.write(String.join(",", commands).getBytes());
                fos.write("\n".getBytes());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        try(FileOutputStream fos = activity.openFileOutput(BuildSettings.outputFileName,
                Context.MODE_APPEND )){
            fos.write(String.join(",", timings).getBytes());
            fos.write(",".getBytes());
            fos.write(String.join(",", timingsMod).getBytes());
            fos.write("\n".getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
