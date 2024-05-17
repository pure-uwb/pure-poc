package ch.ethz.emvextension.controller;

import static com.github.devnied.emvnfccard.utils.CommandApdu.getCommandEnum;
import static ch.ethz.emvextension.Apdu.HexUtils.bin2hex;
import static ch.ethz.emvextension.utils.Constants.EVT_CMD;

import android.util.Log;

import com.github.devnied.emvnfccard.enums.CommandEnum;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.concurrent.Semaphore;

import at.zweng.emv.utils.EmvParsingException;
import ch.ethz.emvextension.channel.Channel;
import ch.ethz.emvextension.protocol.CardStateMachine;
import ch.ethz.emvextension.protocol.ProtocolExecutor;
import ch.ethz.emvextension.protocol.Session;

public class CardController extends PaymentController {
    private final String TAG = "CardController";

    private static CardController controller = null;
    private static final boolean initialized = false;
    private Semaphore uwbSemaphore;
    private Boolean writeKey;

    public static CardController getInstance(Channel emvChannel, Channel boardChannel, ProtocolExecutor protocol) {
        if (controller == null) {
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

    private void handleEmvEvent(PropertyChangeEvent evt) {
        if (evt.getPropertyName().equals(EVT_CMD)) {
            byte[] cmd = emvChannel.read();
            CommandEnum command;
            try {
                command = getCommandEnum(cmd);
            } catch (EmvParsingException e) {
                throw new RuntimeException(e);
            }
            if (command == null) {
                Log.e(TAG, "Command not recognized");
                return;
            }
            switch (command) {
                case READ_RECORD:
                    if (writeKey) {
                        // On the first RR write key to board.
                        writeKey = false;
                        start = System.nanoTime();
                        byte[] key = protocol.programKey(paymentSession);
                        Log.i(TAG, "Write ranging key to board (" + bin2hex(key) + ")");
                        try {
                            boardChannel.write(key);
                        } catch (Exception e) {
                            Log.e(TAG, "UART FAIL" + e);
                        }
                    }
                    break;
                case EXT_CL_HELLO:
                    writeKey = true; // Ranging done, allow new key to be written to the board.
                    PropertyChangeListener[] listeners = paymentSession.getListeners();
                    paymentSession = new Session(new CardStateMachine());
                    for (PropertyChangeListener l : listeners) {
                        paymentSession.addPropertyChangeListener(l);
                    }
                    protocol.init(paymentSession);
                    paymentSession.step();
                    protocol.parseTerminalHello(cmd, paymentSession);
                    paymentSession.step();
                    emvChannel.write(protocol.createCardHello(paymentSession));
                    paymentSession.step();
                    uwbSemaphore = new Semaphore(0);
                    break;
                case EXT_SIGN:
                    try {
                        parsingSemaphore.acquire();
                        uwbSemaphore.acquire();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    paymentSession.setAC(AC.getAC());
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

    private void handleBoardEvent(PropertyChangeEvent evt) {
        Log.i("CardController", "Event: " + evt.getPropertyName());
        protocol.parseTimingReport((byte[]) evt.getNewValue(), paymentSession);
        paymentSession.step();
        stop = System.nanoTime();
        uwbSemaphore.release();
        Log.i("CardController", "Ranging time" + ((float) (stop - start)) / 1000000);
    }
}
