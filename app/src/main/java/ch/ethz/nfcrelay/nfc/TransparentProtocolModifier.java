package ch.ethz.nfcrelay.nfc;

import static com.github.devnied.emvnfccard.utils.CommandApdu.getCommandEnum;

import android.util.Log;

import com.example.emvextension.Apdu.ApduWrapperCard;
import com.example.emvextension.Apdu.ApduWrapperReader;
import com.example.emvextension.channel.Channel;
import com.example.emvextension.controller.CardController;
import com.example.emvextension.controller.ReaderController;
import com.example.emvextension.jobs.EmvParserJob;
import com.example.emvextension.jobs.ReaderControllerJob;
import com.example.emvextension.protocol.ApplicationCryptogram;
import com.example.emvextension.protocol.CardStateMachine;
import com.example.emvextension.protocol.ProtocolExecutor;
import com.example.emvextension.protocol.ProtocolModifier;
import com.example.emvextension.protocol.ReaderStateMachine;
import com.example.emvextension.utils.Timer;
import com.github.devnied.emvnfccard.enums.CommandEnum;
import com.github.devnied.emvnfccard.iso7816emv.EmvTags;
import com.github.devnied.emvnfccard.model.EmvCard;
import com.github.devnied.emvnfccard.utils.TlvUtil;
import com.github.devnied.emvnfccard.utils.TrackUtils;

import java.util.Arrays;
import java.util.concurrent.Semaphore;

import at.zweng.emv.utils.EmvParsingException;
import ch.ethz.nfcrelay.MainActivity;
import ch.ethz.nfcrelay.Provider;

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
                isProtocolFinished = true;
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
