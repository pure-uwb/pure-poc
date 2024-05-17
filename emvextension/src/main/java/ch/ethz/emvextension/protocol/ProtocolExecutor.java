package ch.ethz.emvextension.protocol;

import static com.github.devnied.emvnfccard.iso7816emv.EmvTags.EXT_DH;
import static com.github.devnied.emvnfccard.iso7816emv.EmvTags.EXT_MAC;
import static com.github.devnied.emvnfccard.iso7816emv.EmvTags.EXT_SIGNATURE;
import static ch.ethz.emvextension.Apdu.HexUtils.bin2hex;
import static ch.ethz.emvextension.protocol.StateMachineUtils.stateToString;

import android.content.Context;
import android.util.Log;

import com.example.emvextension.R;
import com.github.devnied.emvnfccard.enums.CommandEnum;
import com.github.devnied.emvnfccard.iso7816emv.TLV;
import com.github.devnied.emvnfccard.utils.TlvUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Security;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;

import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import ch.ethz.emvextension.Apdu.ApduWrapper;
import ch.ethz.emvextension.Apdu.CommandAPDU;
import ch.ethz.emvextension.Crypto;
import ch.ethz.emvextension.HKDF.Hkdf;
import fr.devnied.bitlib.BytesUtils;

public class ProtocolExecutor {
    protected static final int TAG_LEN = 32;
    private static final byte[] GET_SIGANTURE = new byte[]{(byte) 0x80, (byte) 0xAE};
    protected final String TAG = "device.ProtocolExecutor";

    private static final byte[] SelectAID = new byte[]{(byte) 0xF0, (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05, (byte) 0x06};
    protected final Context ctx;
    protected final ApduWrapper apduWrapper;

    public ProtocolExecutor(ApduWrapper apdu, Context context) {
        this.apduWrapper = apdu;
        this.ctx = context;
    }

    private byte[] computeMac(byte[] key, byte[] transcript) {
        Log.i(TAG, "Tag key: \b" + getHex(key));
        Log.i(TAG, "Tag data: \b" + getHex(transcript));

        SecretKeySpec secretKeySpec = new SecretKeySpec(key, "HmacSHA256");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKeySpec);
            return (mac.doFinal(transcript));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] createCardHello(Session session) {
        Long start = System.nanoTime();
        KeyPair key = session.getLocalKey();
        if (key == null) throw new NullPointerException();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            ECPublicKey pk = (ECPublicKey) key.getPublic();
            byte[] encoded = Crypto.encodePublicKey(pk);
            Log.i(TAG, "Encoded format: " + key.getPublic().getFormat() + "len: " + encoded.length);
            outputStream.write(new TLV(EXT_DH, encoded.length, encoded).getTlvBytes());
            byte[] macTag = computeMac(session.getTagKey(), session.getTranscript());
            outputStream.write(new TLV(EXT_MAC, macTag.length, macTag).getTlvBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        byte[] response = outputStream.toByteArray();
        Log.i(TAG, "CreateHello:\n" + getHex(response));
        Long stop = System.nanoTime();
        Log.i("Timer", "CreateCardHello: " + ((float) (stop - start) / 1000000));
        return apduWrapper.encode(response);
    }

    public byte[] createReaderHello(Session session) {
        KeyPair key = session.getLocalKey();
        if (key == null) throw new NullPointerException();
        ECPublicKey pk = (ECPublicKey) key.getPublic();
        byte[] encoded = Crypto.encodePublicKey(pk);
        Log.i(TAG, "Encoded format: " + key.getPublic().getFormat());
        byte[] response = encoded;//key.getPublic().getEncoded();
        response = new TLV(EXT_DH, response.length, response).getTlvBytes();
        Log.i(TAG, "CreateHello:\n" + getHex(response));
        return apduWrapper.encode(CommandEnum.EXT_CL_HELLO, response);
    }


    public void parseCardHello(byte[] helloMessage, Session session) {
        Long start = System.nanoTime();

        helloMessage = apduWrapper.decode(helloMessage);
        byte[] dhBytes = TlvUtil.getValue(helloMessage, EXT_DH);
        byte[] tagBytes = TlvUtil.getValue(helloMessage, EXT_MAC);
        Log.i(TAG, "Hello received:" + getHex(helloMessage));

        // Parse dh value
        PublicKey remoteKey;
        remoteKey = Crypto.decodePublicKey(dhBytes);
        session.setRemoteKey(remoteKey);
        KeyAgreement ecdhU = null;
        try {
            ecdhU = KeyAgreement.getInstance("ECDH");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        try {
            ecdhU.init(session.getLocalKey().getPrivate());
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        }
        try {
            ecdhU.doPhase(remoteKey, true);
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        }

        Log.i("Providers", Arrays.toString(Security.getProviders()));
        Hkdf hkdf = Hkdf.usingDefaults();
        SecretKey key = hkdf.extract(null, ecdhU.generateSecret());
        byte[] secret = hkdf.expand(key, "RANGING".getBytes(StandardCharsets.UTF_8), 16);
        byte[] tagKey = hkdf.expand(key, "TAG_CARD_TO_READER".getBytes(StandardCharsets.UTF_8), 16);

        session.setSecret(secret);
        session.setTagKey(tagKey);

        // Verify MAC
        // MAC is used to have explicit key confirmation
        byte[] tag = computeMac(tagKey, session.getRemoteTranscript());
        if (!Arrays.equals(tag, tagBytes)) {
            throw new RuntimeException("TAG DO NOT MATCH");
        }
        Long stop = System.nanoTime();
        Log.i("Timer", "ParseCardHello: " + ((float) (stop - start) / 1000000));

    }

    public void parseTerminalHello(byte[] helloMessage, Session session) {
        Long start = System.nanoTime();

        helloMessage = apduWrapper.decode(helloMessage);
        byte[] dhBytes = TlvUtil.getValue(helloMessage, EXT_DH);

        PublicKey remoteKey = Crypto.decodePublicKey(dhBytes); // <-- exception here
        session.setRemoteKey(remoteKey);
        KeyAgreement ecdhU = null;
        try {
            ecdhU = KeyAgreement.getInstance("ECDH");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        try {
            ecdhU.init(session.getLocalKey().getPrivate());
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        }
        try {
            ecdhU.doPhase(remoteKey, true);
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        }

        Log.i("Providers", Arrays.toString(Security.getProviders()));
        Hkdf hkdf = Hkdf.usingDefaults();
        SecretKey key = hkdf.extract(null, ecdhU.generateSecret());
        byte[] secret = hkdf.expand(key, "RANGING".getBytes(StandardCharsets.UTF_8), 16);
        byte[] tagKey = hkdf.expand(key, "TAG_CARD_TO_READER".getBytes(StandardCharsets.UTF_8), 16);

        session.setSecret(secret);
        session.setTagKey(tagKey);
        Long stop = System.nanoTime();
        Log.i("Timer", "ParseTerminalHello: " + ((float) (stop - start) / 1000000));

    }

    public byte[] selectAID(Session session) {
        return apduWrapper.encode(CommandEnum.EXT_SELECT_AID, SelectAID);
    }

    public byte[] respSelectAid(Session session) {
        return apduWrapper.encode(new byte[]{(byte) 0x00, (byte) 0x00});
    }

    public byte[] programKey(Session session) {
        if (session.getState().compareTo(StateMachine.State.RANGE) < 0) {
            throw new RuntimeException("Range state accessed in " + stateToString(session.getState()));
        }
        return session.getSecret();
    }

    private String getHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = bytes.length - 1; i >= 0; --i) {
            int b = bytes[i] & 0xff;
            if (b < 0x10) sb.append('0');
            sb.append(Integer.toHexString(b));
            if (i > 0) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }

    public static int getCommandType(byte[] cmd) {
        CommandAPDU apdu = new CommandAPDU(cmd);
        return apdu.getINS();
    }

    public void parseTimingReport(byte[] timingsBytes, Session paymentSession) {
        if (timingsBytes == null) {
            Log.i(TAG, "Skip card record (useless)");
            return;
        }
        if (paymentSession.getState() != StateMachine.State.RANGE) {
            Log.e(TAG, "This function should not be called in state: " + stateToString(paymentSession.getState()));
            return;
        }
        int pollTxOffset = 0;
        int respRxOffset = 4;
        int pollRxOffset = 8;
        int respTxOffset = 12;
        int finalTxOffset = 16;
        int distOffset = 20;

        long pollRx;
        long pollTx;
        long respRx;
        long respTx;
        long finalTx;
        float distance;

        Log.i(TAG, bin2hex(timingsBytes));
        Log.i(TAG, Arrays.toString(timingsBytes));
        try {
            pollRx = byteArrayToUInt32(timingsBytes, pollRxOffset);
            pollTx = byteArrayToUInt32(timingsBytes, pollTxOffset);
            respRx = byteArrayToUInt32(timingsBytes, respRxOffset);
            respTx = byteArrayToUInt32(timingsBytes, respTxOffset);
            finalTx = byteArrayToUInt32(timingsBytes, finalTxOffset);
            paymentSession.setTimings(pollRx, pollTx, respRx, respTx, finalTx);
            distance = Float.parseFloat(new String(Arrays.copyOfRange(timingsBytes, distOffset, distOffset + 5), StandardCharsets.UTF_8));
            paymentSession.setDistance(distance);
            Log.i(TAG, "Distance: " + paymentSession.getDistance());
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }

    }

    private static long byteArrayToUInt32(byte[] data, int offset) {
        if (data == null || data.length < offset + 4) {
            return 0;
        }

        return ((long) (data[offset] & 0xFF) << 24) + ((data[offset + 1] & 0xFF) << 16) + ((data[offset + 2] & 0xFF) << 8) + (data[offset + 3] & 0xFF);
    }

    public void finish(Session paymentSession) {
        paymentSession.finish();
    }

    public byte[] getSignatureCommand() {
        return apduWrapper.encode(CommandEnum.EXT_SIGN, GET_SIGANTURE);
    }

    public byte[] sendSignature(Session session) {
        ByteArrayOutputStream signature = new ByteArrayOutputStream();
        try (InputStream inputStream = ctx.getResources().openRawResource(R.raw.pkcs8_card_key)) {
            RSAPrivateKey privateKey = Crypto.loadPrivateKey(inputStream);
            byte[] valueBytes = Crypto.sign(privateKey, session.getTranscript());
            signature.write(new TLV(EXT_SIGNATURE, valueBytes.length, valueBytes).getTlvBytes());
            Log.i(TAG, "Signing:\n" + BytesUtils.bytesToString(session.getTranscript()) + "\n");
            Log.i(TAG, "Card key modulus: " + privateKey.getModulus());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Log.i(TAG, "Sending signature (sign): " + BytesUtils.bytesToString(signature.toByteArray()));
        Log.i(TAG, "Signature length:" + signature.size());
        return apduWrapper.encode(signature.toByteArray());
    }

    public boolean verifySignature(byte[] signatureMessage, Session session) {
        signatureMessage = apduWrapper.decode(signatureMessage);
        Log.i(TAG, BytesUtils.bytesToString(signatureMessage));
        byte[] signature = TlvUtil.getValue(signatureMessage, EXT_SIGNATURE);
        /*
         * NOTE: At this point CARD and READER have already exchanged certificates in the EMV transaction.
         * Therefore the reader knows the public key of the card. Since we do not have the private key of
         * the card, we use an autogenerated key pair and here we assume that the reader knows it.
         * */
        RSAPublicKey cardKey = session.getSecondaryKey();
        Log.i(TAG, "Signing:\n" + BytesUtils.bytesToString(session.getRemoteTranscript()) + "\n");
        Log.i(TAG, "Card key modulus: " + cardKey.getModulus());
        if (!Crypto.verify(cardKey, signature, session.getRemoteTranscript())) {
            throw new RuntimeException("Invalid signature");
        }
        session.setSignVerif(true);
        return true;
    }

    public void init(Session paymentSession) {
    }
}


