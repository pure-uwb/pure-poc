package com.example.emvextension.Apdu;

public class ApduWrapperCard implements ApduWrapper {
    private int action;

    @Override
    public byte[] encode(byte[] payload) {
        ResponseAPDU responseAPDU = new ResponseAPDU(0x00, 0x00, payload);
        return responseAPDU.getBytes();
    }

    @Override
    public byte[] decode(byte[] msg) {
        CommandAPDU apdu = new CommandAPDU(msg);
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
