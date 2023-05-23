package ch.ethz.nfcrelay.nfc.card;

import static com.example.emvextension.utils.Constants.EVT_CMD;

import com.example.emvextension.channel.Channel;
import com.example.emvextension.protocol.ProtocolModifier;

import ch.ethz.nfcrelay.MainActivity;
import ch.ethz.nfcrelay.nfc.card.hce.EMVraceApduService;

public class CommandDispatcher extends Channel implements ch.ethz.nfcrelay.nfc.card.hce.CommandDispatcher{
    byte [] cmd;
    EMVraceApduService hostApduService;
    ProtocolModifier modifier;

    public CommandDispatcher(ProtocolModifier modifier) {
        this.modifier = modifier;
    }

    @Override
    public void dispatch(EMVraceApduService hostApduService, String ip, int port, byte[] cmd, boolean isPPSECmd, MainActivity activity) {
        if (isExtension(cmd)){
            this.cmd = cmd;
            this.hostApduService = hostApduService;
            this.notifyAllListeners(EVT_CMD, null, null);
        }
        ResponseResolver responseResolver = new ResponseResolver(hostApduService, ip, port,
                cmd, false, null, modifier);

        responseResolver.start();
    }

    private boolean isExtension(byte [] cmd) {
        //TODO: add TAG that identifies extension
        return false;
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
