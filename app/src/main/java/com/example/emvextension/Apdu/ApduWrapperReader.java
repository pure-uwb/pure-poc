package com.example.emvextension.Apdu;

import static com.example.emvextension.Apdu.UtilsAPDU.CLA;
import static com.example.emvextension.Apdu.UtilsAPDU.P1;
import static com.example.emvextension.Apdu.UtilsAPDU.P2;

public class ApduWrapperReader implements ApduWrapper {
    int action;
    @Override
    public byte[] encode(byte[] payload) {
        CommandAPDU apdu = new CommandAPDU(CLA, action, P1, P2, payload);
        return apdu.getBytes();
    }

    @Override
    public byte[] decode(byte[] msg) {
        ResponseAPDU apdu = new ResponseAPDU(msg);
        return apdu.getData();
    }

    @Override
    public void setAction(int action) {
        this.action = action;
    }

    @Override
    public int getAction() {
        return action;
    }
}
