package ch.ethz.pure.nfc;

import static com.github.devnied.emvnfccard.utils.CommandApdu.getCommandEnum;

import ch.ethz.emvextension.channel.Channel;
import ch.ethz.emvextension.protocol.ProtocolModifier;
import com.github.devnied.emvnfccard.enums.CommandEnum;

import java.util.Arrays;

import at.zweng.emv.utils.EmvParsingException;

public class TransparentProtocolModifier implements ProtocolModifier {

    private boolean isProtocolFinished;

    @Override
    public byte[] parse(byte[] cmd, byte[] res) throws EmvParsingException {
        byte[] cmdEnum = Arrays.copyOf(cmd, cmd.length);
        if (cmd[0] == (byte) 0x80 & cmd[1] == (byte) 0xAE) {
            // NOTE: In GEN AC, P1 is variable. Enum looks for P1 = 0x00 to recognise GEN AC.
            cmdEnum[2] = (byte) 0x00;
        }
        if (cmd[0] == (byte) 0x00 & cmd[1] == (byte) 0xB2) {
            cmdEnum[2] = (byte) 0x00;
            cmdEnum[3] = (byte) 0x00;
        }
        CommandEnum command = getCommandEnum(cmdEnum);
        switch (command) {
            case GEN_AC:
//                isProtocolFinished = true;
                break;
        }
        return res;
    }

    @Override
    public void setNfcChannel(Channel channel) {
    }

    @Override
    public boolean isProtocolFinished() {
        return isProtocolFinished;
    }
}
