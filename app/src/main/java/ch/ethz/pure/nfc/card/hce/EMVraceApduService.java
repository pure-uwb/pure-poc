package ch.ethz.pure.nfc.card.hce;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.util.Log;

import java.util.Arrays;

import ch.ethz.pure.CardActivity;
import ch.ethz.pure.nfc.ProtocolModifierImpl;
import ch.ethz.pure.nfc.Util;

public class EMVraceApduService extends HostApduService {

    public static CardActivity cardActivity;
    public static CommandDispatcher dispatcher;
    public static String ip;
    public static int port;
    private final String TAG = EMVraceApduService.class.getName();


    @Override
    public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
        try {
            if (cardActivity != null)
                cardActivity.onApduCommandReceived(commandApdu);
            dispatcher.dispatch(this, ip, port, commandApdu, false, null);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            Log.e(TAG, Arrays.toString(e.getStackTrace()));
        }
        return Util.EMPTY_APDU_RESPONSE;//tell the HCE to wait until the response has been resolved
    }

    @Override
    public void onDeactivated(int reason) {
        try {
            if (cardActivity != null)
                cardActivity.onApduServiceDeactivated(reason);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }
}
