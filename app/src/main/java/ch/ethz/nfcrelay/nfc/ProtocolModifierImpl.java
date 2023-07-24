package ch.ethz.nfcrelay.nfc;

import static com.github.devnied.emvnfccard.utils.CommandApdu.getCommandEnum;

import android.app.Activity;
import android.util.Log;

import androidx.annotation.RequiresPermission;

import com.example.emvextension.Apdu.ApduWrapperCard;
import com.example.emvextension.Apdu.ApduWrapperReader;
import com.example.emvextension.channel.Channel;
import com.example.emvextension.channel.UartChannelMock;
import com.example.emvextension.controller.CardController;
import com.example.emvextension.controller.PaymentController;
import com.example.emvextension.controller.ReaderController;
import com.example.emvextension.jobs.ReaderControllerJob;
import com.example.emvextension.protocol.ApplicationCryptogram;
import com.example.emvextension.protocol.CardStateMachine;
import com.example.emvextension.jobs.EmvParserJob;
import com.example.emvextension.protocol.ProtocolExecutor;
import com.example.emvextension.protocol.ProtocolModifier;
import com.example.emvextension.protocol.ReaderStateMachine;
import com.example.emvextension.protocol.Session;
import com.example.emvextension.utils.Timer;
import com.github.devnied.emvnfccard.enums.CommandEnum;
import com.github.devnied.emvnfccard.iso7816emv.EmvTags;
import com.github.devnied.emvnfccard.model.EmvCard;
import com.github.devnied.emvnfccard.parser.EmvParser;
import com.github.devnied.emvnfccard.utils.TlvUtil;
import com.github.devnied.emvnfccard.utils.TrackUtils;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.concurrent.Semaphore;

import at.zweng.emv.utils.EmvParsingException;
import ch.ethz.nfcrelay.MainActivity;
import ch.ethz.nfcrelay.Provider;
import fr.devnied.bitlib.BytesUtils;

public class ProtocolModifierImpl implements ProtocolModifier, PropertyChangeListener {
    private final Semaphore semaphore;
    public boolean executeExtension = true;
    private final EmvParser parser = new EmvParser(true);
    private final byte EXTENSION_PROTOCOL_AIP_MASK = (byte) 0x2;
    private boolean isReader;

    private Channel nfcChannel;

    private Activity activity;

    private ReaderController readerController;
    private CardController cardController = null;
    private boolean isProtocolFinished = false;
    private Long start;

    private final String TAG = ProtocolModifierImpl.class.getName();

    public ProtocolModifierImpl(Activity activity, boolean isReader) {
        this.isReader = isReader;
        this.activity = activity;
        this.semaphore = new Semaphore(0);
    }

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
                byte[] aip = TlvUtil.getValue(res, EmvTags.APPLICATION_INTERCHANGE_PROFILE);

                if (isReader) {
                    executeExtension = ((aip[1] & EXTENSION_PROTOCOL_AIP_MASK) > 0);
                    aip[1] = (byte) (aip[1] & (~EXTENSION_PROTOCOL_AIP_MASK)); // SET EXT to 0
                } else {
                    aip[1] = (byte) (aip[1] | EXTENSION_PROTOCOL_AIP_MASK);    // SET EXT to 1
                }

                if (executeExtension) {
                    Log.i(this.getClass().getName(), "Start of extension");
                    ApplicationCryptogram AC = new ApplicationCryptogram();
                    Semaphore s = new Semaphore(0);
                    // Controller MUST wait on the semaphore s before signing
                    if (isReader) {
                        readerController = ReaderController.getInstance(nfcChannel,
                                Provider.getUartChannel(activity),
                                new ProtocolExecutor(new ApduWrapperReader(), activity));
                        readerController.initialize(s, AC, new Session(new ReaderStateMachine()));
                        readerController.registerSessionListener(new Timer(new ReaderStateMachine()));
                        readerController.registerSessionListener(this);
                        readerController.start();
                    } else {
                        cardController = CardController.getInstance(nfcChannel,
                                Provider.getUartChannel(activity),
                                new ProtocolExecutor(new ApduWrapperCard(), activity));
                        cardController.initialize(s, AC, new Session(new CardStateMachine()));
                        cardController.registerSessionListener(new Timer(new CardStateMachine()));
                    }
                }
                res = TlvUtil.setValue(res, EmvTags.APPLICATION_INTERCHANGE_PROFILE, aip, false);


                break;

            case GEN_AC:
                EmvCard card = parser.getCard();
                card.setType(parser.findCardScheme(card.getAid(), card.getCardNumber()));
                // Derive the AC in parallel
                if (executeExtension){
                    if ( isReader ){
                        new EmvParserJob(parser.getCard(), readerController.getSemaphore(),
                                res, readerController.getAC(), activity,
                                com.github.devnied.emvnfccard.R.raw.cardschemes_public_root_ca_keys).start();
                        try {
                            semaphore.acquire();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        ((MainActivity) activity).appendToLog(readerController.getLog());
                        if (!readerController.isSuccess()) {
                            res = new byte[]{(byte) 0x00};
                            Log.e("ProtocolModifierImpl", "Extension protocol failed");
                        }
                    } else{
                        new EmvParserJob(parser.getCard(), cardController.getSemaphore(),
                                res, cardController.getAC(), activity,
                                com.github.devnied.emvnfccard.R.raw.cardschemes_public_root_ca_keys).start();
                    }
                }
                Long stop = System.nanoTime();
                Log.i("Timer", "Total transaction time: " + ((float)(stop - start)/1000000));
                isProtocolFinished = true;
                break;
            case SELECT:
                /*TODO: Add PDOL list so that the reader includes its capabilites regarding the
                 * Extention protocol
                 * */
                start = System.nanoTime();
                // Track AID
                String aid = BytesUtils.bytesToStringNoSpace(TlvUtil.getValue(res, EmvTags.DEDICATED_FILE_NAME));
                parser.getCard().setAid(aid);

                break;
        }
        return res;
    }

    @Override
    public void setNfcChannel(Channel channel) {
        this.nfcChannel = channel;
    }

    @Override
    public boolean isProtocolFinished() {
        return isProtocolFinished;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getPropertyName().equals("state_finish")) {
            semaphore.release();
        }
    }
}
