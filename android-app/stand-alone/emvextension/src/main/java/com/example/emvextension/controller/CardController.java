package com.example.emvextension.controller;

import static com.example.emvextension.Apdu.HexUtils.bin2hex;
import static com.example.emvextension.Apdu.UtilsAPDU.INS_CERT;
import static com.example.emvextension.Apdu.UtilsAPDU.INS_SELECT;
import static com.example.emvextension.Apdu.UtilsAPDU.INS_SIG;
import static com.example.emvextension.Apdu.UtilsAPDU.INS_WRITE;
import static com.example.emvextension.channel.CardNfcChannel.EVT_CMD;

import android.util.Log;

import com.example.emvextension.channel.CardNfcChannel;
import com.example.emvextension.channel.Channel;
import com.example.emvextension.protocol.CardStateMachine;
import com.example.emvextension.protocol.ProtocolExecutor;
import com.example.emvextension.protocol.Session;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class CardController extends PaymentController {
    private final String TAG = "CardController";

    public CardController(CardNfcChannel emvChannel, Channel boardChannel, ProtocolExecutor protocol) {
        super(emvChannel, boardChannel, protocol);
        emvChannel.addPropertyChangeListener(this::handleEmvEvent);
        boardChannel.addPropertyChangeListener(this::handleBoardEvent);
    }

    private long start;
    private long stop;

    private void handleEmvEvent(PropertyChangeEvent evt) {
        if (evt.getPropertyName().equals(EVT_CMD)) {
            Log.i(TAG, "Received command");

            byte[] cmd = emvChannel.read();

            switch (ProtocolExecutor.getCommandType(cmd)) {
                case INS_SELECT:
                    Log.i(TAG, "INS_SELECT");
                    PropertyChangeListener[] listeners = paymentSession.getListeners();
                    paymentSession = new Session(new CardStateMachine(), listeners);
                    emvChannel.write(protocol.respSelectAid(paymentSession));
                    paymentSession.step();
                    break;

                case INS_WRITE:
                    protocol.parseTerminalHello(cmd, paymentSession);
                    paymentSession.step();

                    emvChannel.write(protocol.createCardHello(paymentSession));
                    paymentSession.step();
                    break;
                case INS_CERT:
                    start = System.nanoTime();
                    byte[] key = protocol.programKey(paymentSession);
                    Log.i("CardController", "Write key to board: " + bin2hex(key));
                    try {
                        boardChannel.write(key);
                    } catch (Exception e) {
                        Log.e("Controller", "UART FAIL");
                    }
                    paymentSession.step();
                    emvChannel.write(protocol.sendCert(paymentSession));
                    paymentSession.step();
                    break;
                case INS_SIG:
                    Log.i(TAG, "INS_SIG");
                    emvChannel.write(protocol.sendSignature(paymentSession));
                    paymentSession.step();
                    protocol.finish(paymentSession);
                    break;
                default:
                    throw new RuntimeException("Command not found");
            }
        }
    }

    private void handleBoardEvent(PropertyChangeEvent evt) {
        Log.i("CardController", "Event: " + evt.getPropertyName());
        protocol.parseTimingReport((byte[]) evt.getNewValue(), paymentSession);
        stop = System.nanoTime();
        Log.i("CardController", "Ranging time" + ((float) (stop - start)) / 1000000);
    }
}
