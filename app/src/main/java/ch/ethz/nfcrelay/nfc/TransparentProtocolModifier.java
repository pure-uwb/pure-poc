package ch.ethz.nfcrelay.nfc;

import com.example.emvextension.channel.Channel;
import com.example.emvextension.protocol.ProtocolModifier;

import at.zweng.emv.utils.EmvParsingException;

public class TransparentProtocolModifier implements ProtocolModifier {

    @Override
    public byte[] parse(byte[] cmd, byte[] res) throws EmvParsingException {
        return res;
    }

    @Override
    public void setNfcChannel(Channel channel) {
    }
}
