package ch.ethz.nfcrelay.nfc.card.hce;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;

import ch.ethz.nfcrelay.CardActivity;
import ch.ethz.nfcrelay.nfc.Util;
import ch.ethz.nfcrelay.nfc.card.ResponseResolver;

public class EMVraceApduService extends HostApduService {

    public static CardActivity cardActivity;
    public static String ip;
    public static int port;

    @Override
    public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
        if (cardActivity != null)
            cardActivity.onApduCommandReceived(commandApdu);

        ResponseResolver responseResolver = new ResponseResolver(this, ip, port,
                commandApdu, false, null);
        responseResolver.start();
        return Util.EMPTY_APDU_RESPONSE;//tell the HCE to wait until the response has been resolved
    }

    @Override
    public void onDeactivated(int reason) {
        if (cardActivity != null)
            cardActivity.onApduServiceDeactivated(reason);
    }
}
