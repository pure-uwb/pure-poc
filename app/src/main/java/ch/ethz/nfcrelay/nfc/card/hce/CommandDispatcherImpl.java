package ch.ethz.nfcrelay.nfc.card.hce;

import static com.example.emvextension.utils.Constants.EVT_CMD;
import static com.github.devnied.emvnfccard.enums.CommandEnum.isExtensionCommand;
import static com.github.devnied.emvnfccard.utils.CommandApdu.getCommandEnum;

import com.example.emvextension.channel.Channel;
import com.example.emvextension.protocol.ProtocolModifier;
import com.github.devnied.emvnfccard.enums.CommandEnum;

import at.zweng.emv.utils.EmvParsingException;
import ch.ethz.nfcrelay.MainActivity;
import ch.ethz.nfcrelay.nfc.card.ResponseResolver;

public class CommandDispatcherImpl extends Channel implements ch.ethz.nfcrelay.nfc.card.hce.CommandDispatcher{
    byte [] cmd;
    EMVraceApduService hostApduService;
    ProtocolModifier modifier;

    public CommandDispatcherImpl(ProtocolModifier modifier) {
        this.modifier = modifier;
    }

    @Override
    public void dispatch(EMVraceApduService hostApduService, String ip, int port, byte[] cmd, boolean isPPSECmd, MainActivity activity) {
        try {
            if (isExtensionCommand(cmd)){
                this.cmd = cmd;
                this.hostApduService = hostApduService;
                this.notifyAllListeners(EVT_CMD, null, null);
            }else{
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
