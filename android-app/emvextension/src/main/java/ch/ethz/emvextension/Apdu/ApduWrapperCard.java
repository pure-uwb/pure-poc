package ch.ethz.emvextension.Apdu;

import com.github.devnied.emvnfccard.enums.CommandEnum;

public class ApduWrapperCard implements ApduWrapper {
    private int action;

    @Override
    public byte[] encode(byte[] payload) {
        ResponseAPDU responseAPDU = new ResponseAPDU(0x00, 0x00, payload);
        return responseAPDU.getBytes();
    }

    @Override
    public byte[] decode(byte[] msg) {
        CommandAPDU apdu = new CommandAPDU(msg);
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

    @Override
    public byte[] encode(CommandEnum command, byte[] payload) {
        return new CommandAPDU(command.getCla(), command.getIns(), command.getP1(),
                command.getP2(), payload).getBytes();
    }


}
