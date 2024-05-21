package ch.ethz.emvextension.controller;

import static ch.ethz.emvextension.Apdu.HexUtils.bin2hex;

import android.app.Activity;
import android.util.Log;

import com.example.emvextension.R;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPublicKey;
import java.util.concurrent.Semaphore;

import ch.ethz.emvextension.Crypto;
import ch.ethz.emvextension.channel.Channel;
import ch.ethz.emvextension.channel.UartChannel;
import ch.ethz.emvextension.protocol.ApplicationCryptogram;
import ch.ethz.emvextension.protocol.ProtocolExecutor;
import ch.ethz.emvextension.protocol.ReaderStateMachine;
import ch.ethz.emvextension.protocol.Session;
import fr.devnied.bitlib.BytesUtils;

public class ReaderController extends PaymentController {
    private static ReaderController controller = null;
    private static Activity activity;
    public static final String LOG_EVT = "log_event";
    private final String TAG =  ReaderController.class.getName();

    public static ReaderController getInstance(Channel emvChannel, Channel boardChannel, ProtocolExecutor protocol, Activity activity) {
        if (controller == null) {
            controller = new ReaderController(emvChannel, boardChannel, protocol);
            loadSecondaryKey(activity);
        }


        return controller;
    }

    private static void loadSecondaryKey(Activity activity) {
        byte[] cert;
        try (InputStream inputStream = activity.getResources().openRawResource(R.raw.certificate)) {
            cert = Crypto.loadCertificate(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try (InputStream inputStream = activity.getResources().openRawResource(R.raw.ca_pubkey)) {
            RSAPublicKey caPublicKey = Crypto.loadPublicKey(inputStream);
            secondaryPublicKey = Crypto.getPublicKeyFromCertificate(cert, caPublicKey);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private ReaderController(Channel emvChannel, Channel boardChannel, ProtocolExecutor protocol) {
        super(emvChannel, boardChannel, protocol);
        boardChannel.addPropertyChangeListener(this::handleBoardEvent);
    }

    private static RSAPublicKey secondaryPublicKey;
    private Long start_uwb;
    private Long stop_uwb;
    public static float ranging_time;
    private StringBuilder protocolLog = new StringBuilder();

    private void log(byte[] cmd, byte[] res) {
        paymentSession.notifyAllListeners(LOG_EVT,
                "[C-APDU] " + BytesUtils.bytesToStringNoSpace(cmd),
                "[R-APDU] " + BytesUtils.bytesToStringNoSpace(res));
    }

    @Override
    public void initialize(Semaphore parsingSemaphore, ApplicationCryptogram AC, Session session) {
        super.initialize(parsingSemaphore, AC, session);
        protocolLog = new StringBuilder();
    }

    public String getLog() {
        return protocolLog.toString().trim();
    }

    public void start() {
        byte[] res;
        PropertyChangeListener[] listeners = paymentSession.getListeners();
        paymentSession = new Session(new ReaderStateMachine());

        for (PropertyChangeListener l : listeners) {
            paymentSession.addPropertyChangeListener(l);
        }
        paymentSession.setSecondaryKey(secondaryPublicKey);
        protocol.init(paymentSession);
        paymentSession.step();
        byte[] hello = protocol.createReaderHello(paymentSession);
        emvChannel.write(hello);
        paymentSession.step();
        res = emvChannel.read();
        log(hello, res);
        new Thread(() -> {
            protocol.parseCardHello(res, paymentSession);
            paymentSession.step();
            start_uwb = System.nanoTime();
            byte[] key = protocol.programKey(paymentSession);
            Log.i(TAG, "Write ranging key to board (" + bin2hex(key) + ")");
            boardChannel.write(key);
        }).start();
    }

    private void handleBoardEvent(PropertyChangeEvent evt) {
        protocol.parseTimingReport((byte[]) evt.getNewValue(), paymentSession);
        stop_uwb = System.nanoTime();
        ranging_time = ((float) (stop_uwb - start_uwb)) / 1000000;
        paymentSession.step();
        Log.i("ReaderController", "Ranging time" + ranging_time);
    }

    public void authenticate() {
        Log.i("ReaderController", "Authenticate extension");
        paymentSession.step();
        byte[] cmd = protocol.getSignatureCommand();
        emvChannel.write(cmd);
        byte[] res = emvChannel.read();
        log(cmd, res);
        try {
            parsingSemaphore.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        paymentSession.setAC(AC.getAC());
        protocol.verifySignature(res, paymentSession);
        paymentSession.step();
        protocol.finish(paymentSession);
        paymentSession.step();
    }

    public boolean isSuccess() {
        Log.i("ReaderController", "Distance measured:" + paymentSession.getDistance());
        return paymentSession.isSuccess();
    }
}
