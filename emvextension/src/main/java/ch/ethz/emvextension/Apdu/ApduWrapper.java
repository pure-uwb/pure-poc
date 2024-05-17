package ch.ethz.emvextension.Apdu;

import com.github.devnied.emvnfccard.enums.CommandEnum;

public interface ApduWrapper {
    byte[] encode(CommandEnum command, byte[] payload);

    byte[] encode(byte[] payload);

    byte[] decode(byte[] msg);

    void setAction(int action);

    int getAction();
}
