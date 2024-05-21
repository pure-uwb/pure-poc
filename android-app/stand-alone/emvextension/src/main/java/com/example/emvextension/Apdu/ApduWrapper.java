package com.example.emvextension.Apdu;

public interface ApduWrapper {
    byte[] encode(byte[] payload);

    byte[] decode(byte[] msg);

    void setAction(int action);

    int getAction();
}
