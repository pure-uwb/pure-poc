package ch.ethz.pure.nfc.pos;

import static com.github.devnied.emvnfccard.utils.CommandApdu.getCommandEnum;
import static ch.ethz.pure.nfc.BuildSettings.saveTimings;

import android.content.Context;
import android.nfc.tech.IsoDep;
import android.util.Log;

import com.github.devnied.emvnfccard.enums.CommandEnum;

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

import ch.ethz.emvextension.controller.ReaderController;
import ch.ethz.emvextension.protocol.ProtocolModifier;
import ch.ethz.pure.MainActivity;
import ch.ethz.pure.R;
import ch.ethz.pure.nfc.BuildSettings;
import ch.ethz.pure.nfc.Util;

public class PosEmulator extends Thread {

    private final MainActivity activity;
    private final IsoDep tagComm;
    private final ProtocolModifier modifier;
    private final Semaphore semaphore;
    private Long total_time_start;
    private Long total_time_finish;
    private Long summed;
    private final static List<String> commands = Arrays.asList("SEL1", "SEL2", "GPO", "RR1", "RR2", "RR3", "RR4", "GEN_AC",
            "SEL1_MOD", "SEL2_MOD", "GPO_MOD", "RR1_MOD", "RR2_MOD", "RR3_MOD", "RR4_MOD", "GEN_AC_MOD", "RANGING");
    private final List<String> timings;
    private final List<String> timingsMod;

    private final String TAG = "PosEmulator";
    private final String TIMER_TAG = "Timer";

    public PosEmulator(MainActivity activity, IsoDep tagComm, ProtocolModifier modifier, Semaphore s) {
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
                Log.i(TAG, "Kill thread");
                try {
                    activity.getServerSocket().close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                activity.updateStatus(activity.getString(R.string.waiting_for_card),
                        false);
            }).start();

            //execute transaction
            ServerSocket serverSocket = activity.getServerSocket();
            Log.i(TAG, "Transaction started");
            while (true) {
                //waiting for connection with remote card emulator
                Long start = System.nanoTime();
                Socket socket;
                socket = serverSocket.accept();
                if (total_time_start == null)
                    total_time_start = start;
                //read APDU command from socket
                DataInputStream in = new DataInputStream(socket.getInputStream());
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                baos.write(buffer, 0, in.read(buffer));
                byte[] cmd = baos.toByteArray();
                //refresh GUI with command
                Log.i(TAG, "Sending [C-APDU]\n" + Util.bytesToHex(cmd));
                activity.appendToLog("[C-APDU] " + Util.bytesToHex(cmd));

                if (tagComm == null || !tagComm.isConnected()) {
                    return;
                }

                // Send command and receive response from CARD
                byte[] resp = tagComm.transceive(cmd);
                //refresh GUI with response
                activity.appendToLog("[R-APDU] " + Util.bytesToHex(resp));
                Log.i(TAG, "Received [R-APDU]\n" + Util.bytesToHex(resp));

                // HERE ON gen_ac_command do:
                // 1. extract AC
                // 2. execute extension protocol
                // 3. Send AC to the socket only if extension protocol succeeded
                Long start_modifier = System.nanoTime();
                // Protocol modifier parses all messages to track important values needed to recover
                // the input to the AC.
                resp = modifier.parse(cmd, resp);
                Long end_modifier = System.nanoTime();

                // Write APDU response to the backend
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                out.write(resp);

                //close resources
                baos.close();
                out.close();
                in.close();
                socket.close();

                // Track timings
                Long stop = System.nanoTime();
                Log.i(TIMER_TAG, "CMD:\t" + getCommandEnum(cmd)  +
                                      "\nTime:\t" +((float) (stop - start) / 1000000) +
                                      "\nOf which extension: \t" +
                        ((float) (end_modifier - start_modifier) / 1000000));
                timings.add(String.format("%.2f", (float) (stop - start) / 1000000));
                timingsMod.add(String.format("%.2f", ((float) (end_modifier - start_modifier) / 1000000)));
                summed += (stop - start);
                if (modifier.isProtocolFinished()) {
                    Log.i(TIMER_TAG, "Protocol finished");
                    break;
                }
            }
            total_time_finish = System.nanoTime();
            Log.i(TAG, "[TOT]\t" + "Time: " + ((float) (total_time_finish - total_time_start) / 1000000));
            Log.i(TAG, "[SUM]\t" + "Time: " + ((float) (summed) / 1000000));
            Log.i(TAG, "#############################################################################");
            if (saveTimings) saveTimings();
        } catch (Exception e) {
            activity.showErrorOrWarning(e, true);
        }
    }

    private void saveTimings() {
        Log.i(TAG, "SAVING FILE in " + activity.getFilesDir());
        if (!Arrays.asList(activity.fileList()).contains(BuildSettings.outputFileName)) {
            try (FileOutputStream fos = activity.openFileOutput(BuildSettings.outputFileName, Context.MODE_PRIVATE)) {
                fos.write(String.join(",", commands).getBytes());
                fos.write("\n".getBytes());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        try (FileOutputStream fos = activity.openFileOutput(BuildSettings.outputFileName,
                Context.MODE_APPEND)) {
            fos.write(String.join(",", timings).getBytes());
            fos.write(",".getBytes());
            fos.write(String.join(",", timingsMod).getBytes());
            fos.write(String.format(",%.2f", ReaderController.ranging_time).getBytes());
            fos.write("\n".getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
