package com.example.emvextension.controller;

import static com.example.emvextension.Apdu.HexUtils.bin2hex;
import static com.example.emvextension.Apdu.UtilsAPDU.INS_WRITE;

import static com.example.emvextension.utils.Constants.EVT_CMD;
import static com.github.devnied.emvnfccard.utils.CommandApdu.getCommandEnum;

import android.util.Log;

import com.example.emvextension.channel.Channel;
import com.example.emvextension.protocol.ApplicationCryptogram;
import com.example.emvextension.protocol.CardStateMachine;
import com.example.emvextension.protocol.ProtocolExecutor;
import com.example.emvextension.protocol.Session;
import com.github.devnied.emvnfccard.enums.CommandEnum;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.concurrent.Semaphore;

import at.zweng.emv.utils.EmvParsingException;

public class CardController extends PaymentController{
    private final String TAG = "CardController";

    private static CardController controller = null;
    private static boolean initialized = false;
    private Semaphore uwbSemaphore;
    private Boolean writeKey;

    public static CardController getInstance(Channel emvChannel, Channel boardChannel, ProtocolExecutor protocol){
        if(controller == null){
            controller = new CardController(emvChannel, boardChannel, protocol);
        }
        return controller;
    }

    private CardController(Channel emvChannel, Channel boardChannel, ProtocolExecutor protocol) {
        super(emvChannel, boardChannel, protocol);
        emvChannel.addPropertyChangeListener(this::handleEmvEvent);
        boardChannel.addPropertyChangeListener(this::handleBoardEvent);
        writeKey = false;
    }

    private long start;
    private long stop;

    private void handleEmvEvent(PropertyChangeEvent evt){
        if(evt.getPropertyName().equals(EVT_CMD)) {
            Log.i(TAG, "Received command");
            byte[] cmd = emvChannel.read();
            CommandEnum command;
            try {
                command = getCommandEnum(cmd);
            } catch (EmvParsingException e) {
                throw new RuntimeException(e);
            }
            if(command == null){
                Log.e(TAG, "Command not recognized");
                return;
            }
            switch (command) {
                case READ_RECORD:
                    if(writeKey){
                        // On the first RR write key to board.
                        writeKey = false;
                        start = System.nanoTime();
                        byte [] key = protocol.programKey(paymentSession);
                        Log.i("CardController", "Write key to board: " + bin2hex(key));
                        //key = new byte[]{(byte)'A', (byte)'A', (byte)'A', (byte)'A', (byte)'A', (byte)'A', (byte)'A', (byte)'A',
                        //                (byte)'A', (byte)'A', (byte)'A', (byte)'A', (byte)'A', (byte)'A', (byte)'A', (byte)'A'};
                        try{
                            boardChannel.write(key);
                        }catch (Exception e){
                            Log.e("Controller", "UART FAIL" + e);
                        }
                    }
                    break;
                case EXT_CL_HELLO:
                    writeKey = true; // Ranging done, allow new key to be written to the board.
                    PropertyChangeListener[] listeners = paymentSession.getListeners();
                    paymentSession = new Session(new CardStateMachine());
                    for (PropertyChangeListener l :listeners) {
                        paymentSession.addPropertyChangeListener(l);
                    }
                    protocol.init(paymentSession);
                    paymentSession.step();
                    protocol.parseTerminalHello(cmd, paymentSession);
                    paymentSession.step();
                    emvChannel.write(protocol.createCardHello(paymentSession));
                    paymentSession.step();
                    Log.i("CardController", "permits: " + s.availablePermits());
                    Log.i("CardController", "Semaphore hashcode: " + s.toString());
                    uwbSemaphore = new Semaphore(0);
                    break;
                case EXT_SIGN:
                    try {
                        s.acquire();
                        uwbSemaphore.acquire();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    paymentSession.setAC(AC.getAC());
                    Log.i(TAG, "INS_SIG");
                    emvChannel.write(protocol.sendSignature(paymentSession));
                    paymentSession.step();
                    protocol.finish(paymentSession);
                    paymentSession.step();
                    break;
                default:
                    Log.w(TAG, "Command not found" + command);
            }
        }
    }

    private void handleBoardEvent(PropertyChangeEvent evt){
        Log.i("CardController", "Event: "+ evt.getPropertyName());
        protocol.parseTimingReport((byte[])evt.getNewValue(), paymentSession);
        paymentSession.step();
        stop = System.nanoTime();
        uwbSemaphore.release();
        Log.i("CardController", "Ranging time" +  ((float)(stop-start))/1000000 );
    }
}
