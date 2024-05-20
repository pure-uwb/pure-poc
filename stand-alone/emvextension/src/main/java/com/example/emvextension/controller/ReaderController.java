package com.example.emvextension.controller;

import static com.example.emvextension.Apdu.HexUtils.bin2hex;
import static com.example.emvextension.channel.ReaderNfcChannel.EVT_NEW_TAG;

import android.util.Log;

import com.example.emvextension.BuildSettings;
import com.example.emvextension.channel.Channel;
import com.example.emvextension.channel.ReaderNfcChannel;
import com.example.emvextension.protocol.ProtocolExecutor;
import com.example.emvextension.protocol.ReaderStateMachine;
import com.example.emvextension.protocol.Session;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.concurrent.Semaphore;

public class ReaderController extends PaymentController {
    Semaphore uwbSemaphore;

    public ReaderController(ReaderNfcChannel emvChannel, Channel boardChannel, ProtocolExecutor protocol) {
        super(emvChannel, boardChannel, protocol);
        emvChannel.addPropertyChangeListener(this::handleEmvEvent);
        boardChannel.addPropertyChangeListener(this::handleBoardEvent);
        uwbSemaphore = new Semaphore(0);
    }

    private Long start;
    private Long stop;

    private void handleEmvEvent(PropertyChangeEvent evt) {
        for (int i = 0; i < BuildSettings.number_of_tests; i++) {
            if (evt.getPropertyName().equals(EVT_NEW_TAG)) {
                PropertyChangeListener[] listeners = paymentSession.getListeners();
                paymentSession = new Session(new ReaderStateMachine(), listeners);
                Log.i("TAG", "New tag detected");
                byte[] selectAID = protocol.selectAID(paymentSession);
                emvChannel.write(selectAID);
                paymentSession.step();

                byte[] hello = protocol.createReaderHello(paymentSession);
                emvChannel.write(hello);
                paymentSession.step();
                byte[] cardHello = emvChannel.read();
                paymentSession.step();
                new Thread(() -> {
                    protocol.parseCardHello(cardHello, paymentSession);
                    byte[] key = protocol.programKey(paymentSession);
                    Log.i("ReaderController", "Write key to board: " + bin2hex(key));
                    start = System.nanoTime();
                    boardChannel.write(key);
                    paymentSession.step();
                }).start();
                emvChannel.write(protocol.getCertCommand(paymentSession));
                protocol.parseCert(emvChannel.read(), paymentSession);
                paymentSession.step();

                try {
                    uwbSemaphore.acquire();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                emvChannel.write(protocol.getSignatureCommand());
                protocol.verifySignature(emvChannel.read(), paymentSession);
                paymentSession.step();
                protocol.finish(paymentSession);
            }
        }
    }

    private void handleBoardEvent(PropertyChangeEvent evt) {

        stop = System.nanoTime();
        Log.i("ReaderController", "Ranging time" + ((float) (stop - start)) / 1000000);
        protocol.parseTimingReport((byte[]) evt.getNewValue(), paymentSession);
        paymentSession.notifyAllListeners("RANGE", -1, ((float) (stop - start)) / 1000000);
        uwbSemaphore.release();
    }
}
