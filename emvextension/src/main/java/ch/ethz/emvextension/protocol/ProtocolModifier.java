package ch.ethz.emvextension.protocol;

import at.zweng.emv.utils.EmvParsingException;
import ch.ethz.emvextension.channel.Channel;

public interface ProtocolModifier {
    byte[] parse(byte[] cmd, byte[] res) throws EmvParsingException;

    void setNfcChannel(Channel channel);

    boolean isProtocolFinished();
}
