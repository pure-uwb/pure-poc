package com.example.emvextension.protocol;

public interface ProtocolModifier {
    byte [] parse(byte [] cmd, byte[] res);
}
