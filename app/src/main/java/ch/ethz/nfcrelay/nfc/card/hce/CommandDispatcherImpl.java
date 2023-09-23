 package ch.ethz.nfcrelay.nfc.card.hce;

import static com.example.emvextension.utils.Constants.EVT_CMD;
import static com.github.devnied.emvnfccard.enums.CommandEnum.isExtensionCommand;
import static com.github.devnied.emvnfccard.utils.CommandApdu.getCommandEnum;

import android.util.Log;

import com.example.emvextension.channel.Channel;
import com.example.emvextension.protocol.ProtocolModifier;
import com.github.devnied.emvnfccard.enums.CommandEnum;

import java.util.Arrays;

import at.zweng.emv.utils.EmvParsingException;
import ch.ethz.nfcrelay.MainActivity;
import ch.ethz.nfcrelay.nfc.Util;
import ch.ethz.nfcrelay.nfc.card.ResponseResolver;

public class CommandDispatcherImpl extends Channel implements ch.ethz.nfcrelay.nfc.card.hce.CommandDispatcher{
    byte [] cmd;
    EMVraceApduService hostApduService;
    ProtocolModifier modifier;
    boolean transparentRelay;

    public CommandDispatcherImpl(ProtocolModifier modifier, boolean transparentRelay) {
        this.modifier = modifier;
        this.transparentRelay = transparentRelay;
    }

    @Override
    public void dispatch(EMVraceApduService hostApduService, String ip, int port, byte[] cmd, boolean isPPSECmd, MainActivity activity) {
        try {
            this.cmd = Arrays.copyOf(cmd, cmd.length);
            this.hostApduService = hostApduService;
            new Thread(() -> {
                this.notifyAllListeners(EVT_CMD, null, null);
            }).start();
            if (!isExtensionCommand(cmd)  || transparentRelay){
                Log.i("Dispatcher", "EMV command: " + Util.bytesToHex(cmd));
                ResponseResolver responseResolver = new ResponseResolver(hostApduService, ip, port,
                        cmd, false, null, modifier);

                responseResolver.start();
            }
        } catch (EmvParsingException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public byte[] read() {
        return cmd;
    }

    @Override
    public void write(byte[] payload) {
        hostApduService.sendResponseApdu(payload);
    }
}
