package ch.ethz.emvextension.Apdu;

import static ch.ethz.emvextension.Apdu.UtilsAPDU.CLA;
import static ch.ethz.emvextension.Apdu.UtilsAPDU.P1;
import static ch.ethz.emvextension.Apdu.UtilsAPDU.P2;

import com.github.devnied.emvnfccard.enums.CommandEnum;

public class ApduWrapperReader implements ApduWrapper {
    int action;

    @Override
    public byte[] encode(CommandEnum command, byte[] payload) {
        return new  CommandAPDU(command.getCla(), command.getIns(), command.getP1(),
                command.getP2(), payload).getBytes();
    }

    @Override
    public byte[] encode(byte[] payload) {
        CommandAPDU apdu = new CommandAPDU(CLA, action, P1, P2, payload);
        return apdu.getBytes();
    }

    @Override
    public byte[] decode(byte[] msg) {
        ResponseAPDU apdu = new ResponseAPDU(msg);
        return apdu.getData();
    }

    @Override
    public void setAction(int action) {
        this.action = action;
    }

    @Override
    public int getAction() {
        return action;
    }
}
