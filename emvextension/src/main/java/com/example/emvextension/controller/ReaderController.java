
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
    private static ReaderController controller = null;
    public static ReaderController getInstance(Channel emvChannel, Channel boardChannel, ProtocolExecutor protocol){
        if(controller == null){
            controller = new ReaderController(emvChannel, boardChannel, protocol);
        }
        return controller;
    }

    private ReaderController(Channel emvChannel, Channel boardChannel, ProtocolExecutor protocol) {
        super(emvChannel, boardChannel, protocol);
        boardChannel.addPropertyChangeListener(this::handleBoardEvent);
    }

    private Long start;
    private Long stop;
    private StringBuilder protocolLog = new StringBuilder();

    private void log(byte [] cmd, byte[] res){
        protocolLog.append("[C-APDU] ").append(BytesUtils.bytesToStringNoSpace(cmd)).append("\n");
        protocolLog.append("[R-APDU] ").append(BytesUtils.bytesToStringNoSpace(res)).append("\n");
    }

    @Override
    public void initialize(Semaphore s, ApplicationCryptogram AC, Session session){
        super.initialize(s, AC, session);
        protocolLog = new StringBuilder();
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
        protocol.init(paymentSession);
        paymentSession.step();
        byte [] hello = protocol.createReaderHello(paymentSession);
        emvChannel.write(hello);
        paymentSession.step();
        res = emvChannel.read();
        log(hello, res);
        new Thread(()->{
            protocol.parseCardHello(res, paymentSession);
            paymentSession.step();
            start = System.nanoTime();
            byte [] key = protocol.programKey(paymentSession);
            Log.i("ReaderController", "Write key to board: " + bin2hex(key));
            boardChannel.write(key);
        }).start();
    }

    private void handleBoardEvent(PropertyChangeEvent evt){
        Log.i("ReaderController", "Event: "+ evt.getPropertyName());
        protocol.parseTimingReport((byte[]) evt.getNewValue(), paymentSession);
        stop = System.nanoTime();
        paymentSession.step();
        Log.i("ReaderController", "Ranging time" +  ((float)(stop-start))/1000000 );
        try {
            Long start = System.nanoTime();
            s.acquire();
            Long stop = System.nanoTime();
            Log.i("Timer", "[SEM]\t" + "Time: " + ((float)(stop - start)/1000000));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        paymentSession.step();
        paymentSession.setAC(AC.getAC());
        byte [] cmd = protocol.getSignatureCommand();
        emvChannel.write(cmd);
        byte [] res = emvChannel.read();
        log(cmd, res);
        protocol.verifySignature(res, paymentSession);
        paymentSession.step();
        protocol.finish(paymentSession);
        paymentSession.step();
    }

    public boolean isSuccess(){
        Log.i("ReaderController", "Distance measured:" + paymentSession.getDistance());
        return paymentSession.isSuccess();
    }
}
