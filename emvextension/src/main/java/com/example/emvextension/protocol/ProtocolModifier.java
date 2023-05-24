package com.example.emvextension.protocol;

import com.example.emvextension.channel.Channel;

import at.zweng.emv.utils.EmvParsingException;

public interface ProtocolModifier {
    byte [] parse(byte [] cmd, byte[] res) throws EmvParsingException;
    void setNfcChannel(Channel channel);
}
