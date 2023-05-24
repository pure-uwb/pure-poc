package ch.ethz.nfcrelay.nfc;

import static com.github.devnied.emvnfccard.utils.CommandApdu.getCommandEnum;

import android.app.Activity;

import com.example.emvextension.Apdu.ApduWrapperCard;
import com.example.emvextension.Apdu.ApduWrapperReader;
import com.example.emvextension.channel.Channel;
import com.example.emvextension.channel.UartChannelMock;
import com.example.emvextension.controller.CardController;
import com.example.emvextension.controller.ReaderController;
import com.example.emvextension.protocol.ApplicationCryptogram;
import com.example.emvextension.protocol.CardStateMachine;
import com.example.emvextension.protocol.EmvParserJob;
import com.example.emvextension.protocol.ProtocolExecutor;
import com.example.emvextension.protocol.ProtocolModifier;
import com.example.emvextension.protocol.ReaderStateMachine;
import com.example.emvextension.utils.Timer;
import com.github.devnied.emvnfccard.enums.CommandEnum;
import com.github.devnied.emvnfccard.iso7816emv.EmvTags;
import com.github.devnied.emvnfccard.model.EmvCard;
import com.github.devnied.emvnfccard.parser.EmvParser;
import com.github.devnied.emvnfccard.utils.TlvUtil;
import com.github.devnied.emvnfccard.utils.TrackUtils;

import java.util.Arrays;
import java.util.concurrent.Semaphore;

import at.zweng.emv.utils.EmvParsingException;
import fr.devnied.bitlib.BytesUtils;

public class ProtocolModifierImpl implements ProtocolModifier {
    public boolean executeExtension = true;
    private final EmvParser parser = new EmvParser(true);
    private final byte EXTENSION_PROTOCOL_AIP_MASK = (byte)0x2;
    private boolean isReader;

    private Channel nfcChannel;

    private Activity activity;
    private final String TAG = ProtocolModifierImpl.class.getName();

    public ProtocolModifierImpl(Activity activity,  boolean isReader) {
        this.isReader = isReader;
        this.activity = activity;
    }

    @Override
    public byte[] parse(byte[] cmd, byte[] res) throws EmvParsingException {
        byte[] cmdEnum = Arrays.copyOf(cmd, cmd.length);
        if (cmd[0] == (byte) 0x80 & cmd[1] == (byte) 0xAE) {
            // NOTE: In GEN AC, P1 is variable. Enum looks for P1 = 0x00 to recognise GEN AC.
            cmdEnum[2] = (byte) 0x00;
        }
        if (cmd[0] == (byte) 0x00 & cmd[1] == (byte) 0xB2){
            cmdEnum[2] = (byte) 0x00;
            cmdEnum[3] = (byte) 0x00;
        }
        CommandEnum command = getCommandEnum(cmdEnum);
        switch (command) {
            case READ_RECORD:
                parser.extractCardHolderName(res);
                parser.extractCaPublicKeyIndex(res);
                parser.extractIssuerPublicKeyTags(res);
                parser.extractIccPublicKeyTags(res);
                parser.extractIccPinEnciphermentPublicKeyTags(res);
                TrackUtils.extractTrack2Data(parser.getCard(), res);
                // Retrieve pks
                break;
            case GPO:
                /* TODO: Here the Terminal should set the PDOL so that the card knows if it supports
                   Extension protocol*/

                // Check AIP and modify it in res
                // If EXTENSION_PROTOCOL is set we are the reader and we have to unset it before
                // forwarding it to the backend.
                // If EXTENSION_PROTOCOL is NOT set, then we are the card and we have to set it to
                // communicate to the reader that we are capable of executing ranging.
                byte [] aip = TlvUtil.getValue(res, EmvTags.APPLICATION_INTERCHANGE_PROFILE);

                if(isReader){
                    executeExtension = ((aip[1] & EXTENSION_PROTOCOL_AIP_MASK) > 0);
                    aip[1] = (byte)(aip[1] & (~EXTENSION_PROTOCOL_AIP_MASK)); // SET EXT to 0
                }else{
                    aip[1] = (byte)(aip[1] | EXTENSION_PROTOCOL_AIP_MASK);    // SET EXT to 1
                }
                res = TlvUtil.setValue(res, EmvTags.APPLICATION_INTERCHANGE_PROFILE, aip, false);
                break;

            case GEN_AC:
                if (executeExtension){
                    EmvCard card = parser.getCard();
                    card.setType(parser.findCardScheme(card.getAid(), card.getCardNumber()));
                    // Derive the AC in parallel
                    ApplicationCryptogram AC = new ApplicationCryptogram();
                    Semaphore s = new Semaphore(0);

                    new EmvParserJob(parser.getCard(), s, res, AC, activity, com.github.devnied.emvnfccard.R.raw.cardschemes_public_root_ca_keys).start();
                    // Controller MUST wait on the semaphore before signing
                    if (isReader){
                        ReaderController controller = new ReaderController(nfcChannel,
                                new UartChannelMock(),
                                new ProtocolExecutor(new ApduWrapperReader(), activity),
                                s, AC);
                        controller.registerSessionListener(new Timer(new ReaderStateMachine()));
                        controller.start();
                    }else{
                        //TODO add semaphore for the signature
                        CardController controller = new CardController(nfcChannel,
                                new UartChannelMock(),
                                new ProtocolExecutor(new ApduWrapperCard(), activity),
                                s,
                                AC);
                        controller.registerSessionListener(new Timer(new CardStateMachine()));
                    }
                    // If extension has to be executed, start extension execution
                    break;
                }

            case SELECT:
                /*TODO: Add PDOL list so that the reader includes its capabilites regarding the
                 * Extention protocol
                 * */
                // Track AID
                String aid =  BytesUtils.bytesToStringNoSpace(TlvUtil.getValue(res, EmvTags.DEDICATED_FILE_NAME));
                parser.getCard().setAid(aid);
                break;
        }
        return res;
    }

    @Override
    public void setNfcChannel(Channel channel) {
        this.nfcChannel = channel;
    }
}
