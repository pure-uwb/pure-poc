
package com.example.emvextension.controller;

import static com.example.emvextension.Apdu.HexUtils.bin2hex;

import android.util.Log;

import com.example.emvextension.channel.Channel;
import com.example.emvextension.protocol.ApplicationCryptogram;
import com.example.emvextension.protocol.ProtocolExecutor;
import com.example.emvextension.protocol.ReaderStateMachine;
import com.example.emvextension.protocol.Session;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.concurrent.Semaphore;

import fr.devnied.bitlib.BytesUtils;

public class ReaderController extends PaymentController {

    public ReaderController(Channel emvChannel, Channel boardChannel, ProtocolExecutor protocol, Semaphore s, ApplicationCryptogram AC) {
        super(emvChannel, boardChannel, protocol, s, AC);
        boardChannel.addPropertyChangeListener(this::handleBoardEvent);
    }

    private Long start;
    private Long stop;
    private StringBuilder protocolLog = new StringBuilder();

    private void log(byte [] cmd, byte[] res){
        protocolLog.append("[C-APDU] ").append(BytesUtils.bytesToStringNoSpace(cmd)).append("\n");
        protocolLog.append("[R-APDU] ").append(BytesUtils.bytesToStringNoSpace(res)).append("\n");
    }

    public String getLog(){
        return protocolLog.toString().trim();
    }
    public void start(){
            byte [] res;
            PropertyChangeListener[] listeners = paymentSession.getListeners();
            paymentSession = new Session(new ReaderStateMachine());
            for (PropertyChangeListener l :listeners) {
                paymentSession.addPropertyChangeListener(l);
            }
            Log.i("TAG", "New tag detected");

            byte [] hello = protocol.createReaderHello(paymentSession);
            emvChannel.write(hello);
            try {
                s.acquire();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            paymentSession.setAC(AC.getAC());
            res = emvChannel.read();
            protocol.parseCardHello(res, paymentSession);
            log(hello, res);
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
        byte [] cmd = protocol.getSignatureCommand();
        emvChannel.write(cmd);
        byte [] res = emvChannel.read();
        log(cmd, res);
        protocol.verifySignature(res, paymentSession);
        protocol.finish(paymentSession);
    }

    public boolean isSuccess(){
        Log.i("ReaderController", "Distance measured:" + paymentSession.getDistance());
        return paymentSession.isSuccess();
    }
}
