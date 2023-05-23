package ch.ethz.nfcrelay.nfc.card.hce;

import ch.ethz.nfcrelay.MainActivity;

public interface CommandDispatcher {
    void dispatch(EMVraceApduService hostApduService, String ip, int port,
                  byte[] cmd, boolean isPPSECmd, MainActivity activity);
}
