package ch.ethz.nfcrelay.nfc.card.hce;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.util.Log;

import java.util.Arrays;

import ch.ethz.nfcrelay.CardActivity;
import ch.ethz.nfcrelay.nfc.Util;
import ch.ethz.nfcrelay.nfc.card.ResponseResolver;

public class EMVraceApduService extends HostApduService {

    public static CardActivity cardActivity;
    public static CommandDispatcher dispatcher;
    public static String ip;
    public static int port;

    @Override
    public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
        try{
            Log.i(this.getClass().getName(), "Received command " + Util.bytesToHex(commandApdu));
            if (cardActivity != null)
                cardActivity.onApduCommandReceived(commandApdu);
            dispatcher.dispatch(this, ip, port, commandApdu, false, null);
        }catch (Exception e){
            Log.e(this.getClass().getName(), e.toString());
            Log.e(this.getClass().getName(), Arrays.toString(e.getStackTrace()));
        }
        return Util.EMPTY_APDU_RESPONSE;//tell the HCE to wait until the response has been resolved
    }

    @Override
    public void onDeactivated(int reason) {
        if (cardActivity != null)
            cardActivity.onApduServiceDeactivated(reason);
    }
}
