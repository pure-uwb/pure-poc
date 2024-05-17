package ch.ethz.pure.nfc;

import static com.github.devnied.emvnfccard.utils.CommandApdu.getCommandEnum;

import android.app.Activity;

import com.github.devnied.emvnfccard.enums.CommandEnum;
import com.github.devnied.emvnfccard.model.EmvCard;
import com.github.devnied.emvnfccard.parser.EmvParser;
import com.github.devnied.emvnfccard.utils.TrackUtils;

import java.util.Arrays;
import java.util.concurrent.Semaphore;

import at.zweng.emv.utils.EmvParsingException;
import ch.ethz.emvextension.channel.Channel;
import ch.ethz.emvextension.jobs.EmvParserJob;
import ch.ethz.emvextension.protocol.ApplicationCryptogram;
import ch.ethz.emvextension.protocol.ProtocolModifier;

public class TransparentProtocolModifier implements ProtocolModifier {
    private final Activity activity;
    private boolean isProtocolFinished;
    private final EmvParser parser = new EmvParser(true);

    public TransparentProtocolModifier(Activity activity) {
        this.activity = activity;
    }

    @Override
    public byte[] parse(byte[] cmd, byte[] res) throws EmvParsingException {
        CommandEnum command = getCommandEnum(cmd);
        switch (command) {
            case READ_RECORD:
                parser.extractCardHolderName(res);
                parser.extractCaPublicKeyIndex(res);
                parser.extractIssuerPublicKeyTags(res);
                parser.extractIccPublicKeyTags(res);
                parser.extractIccPinEnciphermentPublicKeyTags(res);
                TrackUtils.extractTrack2Data(parser.getCard(), res);
                break;

            case GEN_AC:
                EmvCard card = parser.getCard();
                card.setType(parser.findCardScheme(card.getAid(), card.getCardNumber()));

                // Derive the AC in parallel
                new EmvParserJob(parser.getCard(), new Semaphore(1),
                        res, new ApplicationCryptogram(), activity,
                        com.github.devnied.emvnfccard.R.raw.cardschemes_public_root_ca_keys).start();
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
