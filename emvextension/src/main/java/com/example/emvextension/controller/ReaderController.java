
package com.example.emvextension.controller;

import static com.example.emvextension.Apdu.HexUtils.bin2hex;
import static com.example.emvextension.channel.ReaderNfcChannel.EVT_NEW_TAG;

import android.util.Log;

import com.example.emvextension.channel.Channel;
import com.example.emvextension.channel.ReaderNfcChannel;
import com.example.emvextension.protocol.ApplicationCryptogram;
import com.example.emvextension.protocol.ProtocolExecutor;
import com.example.emvextension.protocol.ReaderStateMachine;
import com.example.emvextension.protocol.Session;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.concurrent.Semaphore;

public class ReaderController extends PaymentController{

    public ReaderController(Channel emvChannel, Channel boardChannel, ProtocolExecutor protocol, Semaphore s, ApplicationCryptogram AC) {
        super(emvChannel, boardChannel, protocol, s, AC);
        boardChannel.addPropertyChangeListener(this::handleBoardEvent);
    }

    private Long start;
    private Long stop;
    public void start(){
            PropertyChangeListener[] listeners = paymentSession.getListeners();
            paymentSession = new Session(new ReaderStateMachine());
            for (PropertyChangeListener l :listeners) {
                paymentSession.addPropertyChangeListener(l);
            }
            Log.i("TAG", "New tag detected");
            byte [] selectAID = protocol.selectAID(paymentSession);
            emvChannel.write(selectAID);

            byte [] hello = protocol.createReaderHello(paymentSession);
            emvChannel.write(hello);
            try {
                s.acquire();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            paymentSession.setAC(AC.getAC());
            protocol.parseCardHello(emvChannel.read(), paymentSession);

            // TODO: execute this in a separate thread
            start = System.nanoTime();
            byte [] key = protocol.programKey(paymentSession);
            Log.i("ReaderController", "Write key to board: " + bin2hex(key));
            boardChannel.write(key);
    }

    private void handleBoardEvent(PropertyChangeEvent evt){
        Log.i("ReaderController", "Event: "+ evt.getPropertyName());
        protocol.parseTimingReport((byte[]) evt.getNewValue(), paymentSession);
        stop = System.nanoTime();
        Log.i("ReaderController", "Ranging time" +  ((float)(stop-start))/1000000 );
        emvChannel.write(protocol.getSignatureCommand());
        protocol.verifySignature(emvChannel.read(), paymentSession);
        protocol.finish(paymentSession);
    }
}
