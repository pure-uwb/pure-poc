package com.example.emvextension;

public interface ProtocolModifier {
    byte [] parse(byte [] cmd, byte[] res);
}
