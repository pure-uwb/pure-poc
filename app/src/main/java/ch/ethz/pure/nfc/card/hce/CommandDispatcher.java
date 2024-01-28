package ch.ethz.pure.nfc.card.hce;

import ch.ethz.pure.MainActivity;

public interface CommandDispatcher {
    void dispatch(EMVraceApduService hostApduService, String ip, int port,
                  byte[] cmd, boolean isPPSECmd, MainActivity activity);
}
