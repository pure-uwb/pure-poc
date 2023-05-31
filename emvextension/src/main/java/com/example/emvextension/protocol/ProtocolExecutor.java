package com.example.emvextension.protocol;

import static com.example.emvextension.Apdu.HexUtils.bin2hex;
import static com.example.emvextension.Apdu.UtilsAPDU.INS_SELECT;
import static com.example.emvextension.Apdu.UtilsAPDU.INS_SIG;
import static com.example.emvextension.Apdu.UtilsAPDU.INS_WRITE;
import static com.example.emvextension.protocol.StateMachineUtils.stateToString;

import android.content.Context;
import android.util.Log;

import com.example.emvextension.Apdu.ApduWrapper;
import com.example.emvextension.Apdu.CommandAPDU;
import com.example.emvextension.Crypto;
import com.example.emvextension.HKDF.Hkdf;
import com.example.emvextension.R;
import com.github.devnied.emvnfccard.enums.CommandEnum;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Security;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import fr.devnied.bitlib.BytesUtils;

public class ProtocolExecutor {
    protected static final int TAG_LEN = 32;
    private static final byte[] GET_SIGANTURE = new byte[]{(byte) 0x80, (byte) 0xAE};
    protected final String TAG = "device.ProtocolExecutor";

    private static byte[] SelectAID = new byte[]{(byte) 0xF0, (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05, (byte) 0x06};
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
        KeyPair key = session.getLocalKey();
        if (key == null) throw new NullPointerException();
        Log.i(TAG, "Encoded format: " + key.getPublic().getFormat());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            outputStream.write(key.getPublic().getEncoded());
            outputStream.write(computeMac(session.getTagKey(), session.getTranscript()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        byte[] response = outputStream.toByteArray();
        Log.i(TAG, "CreateHello:\n" + getHex(response));
        session.step();
        return apduWrapper.encode(response);
    }

    public byte[] createReaderHello(Session session) {
        KeyPair key = session.getLocalKey();
        if (key == null) throw new NullPointerException();
        Log.i(TAG, "Encoded format: " + key.getPublic().getFormat());
        byte[] response = key.getPublic().getEncoded();
        Log.i(TAG, "CreateHello:\n" + getHex(response));
        session.step();
        return apduWrapper.encode(CommandEnum.EXT_CL_HELLO, response);
    }


    public void parseCardHello(byte[] helloMessage, Session session) {
        helloMessage = apduWrapper.decode(helloMessage);
        byte[] dhBytes = Arrays.copyOfRange(helloMessage, 0, helloMessage.length - TAG_LEN);
        byte[] tagBytes = Arrays.copyOfRange(helloMessage,
                helloMessage.length - TAG_LEN, helloMessage.length);
        Log.i(TAG, "Hello received:" + getHex(helloMessage));

        // Parse dh value
        PublicKey remoteKey;
        KeyFactory factory = null;
        try {
            factory = KeyFactory.getInstance("EC");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(dhBytes);

        try {
            remoteKey = factory.generatePublic(publicKeySpec);
        } catch (InvalidKeySpecException e) {
            Log.e(TAG, "Invalid key");
            throw new RuntimeException(e);
        }
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

        session.step();
    }

    public void parseTerminalHello(byte[] helloMessage, Session session) {
        helloMessage = apduWrapper.decode(helloMessage);
        Log.i(TAG, "Hello received:" + getHex(helloMessage));
        PublicKey remoteKey;
        KeyFactory factory = null;
        try {
            factory = KeyFactory.getInstance("EC");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(helloMessage);

        try {
            remoteKey = factory.generatePublic(publicKeySpec);
        } catch (InvalidKeySpecException e) {
            Log.e(TAG, "Invalid key");
            throw new RuntimeException(e);
        }
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

        session.step();
    }

    public byte[] selectAID(Session session) {
        session.step();
        return apduWrapper.encode(CommandEnum.EXT_SELECT_AID ,SelectAID);
    }

    public byte[] respSelectAid(Session session) {
        session.step();
        return apduWrapper.encode(new byte[]{(byte) 0x00, (byte) 0x00});
    }

    public byte[] programKey(Session session) {
        if (session.getState() != StateMachine.State.RANGE){
            throw new RuntimeException("Range state accessed in " + stateToString(StateMachine.State.RANGE));
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
            paymentSession.step();
            return;
        }
        if (paymentSession.getState() != StateMachine.State.RANGE) {
            Log.e(TAG, "This function should not be called in state: " + stateToString(paymentSession.getState()));
            return;
        }
        Log.i(TAG, "State parsingTiming:" + stateToString(paymentSession.getState()));
        int pollTxOffset = 0;
        int respRxOffset = 4;
        int pollRxOffset = 8;
        int respTxOffset = 12;
        int distOffset = 16;

        long pollRx;
        long pollTx;
        long respRx;
        long respTx;
        float distance;

        Log.i(TAG, bin2hex(timingsBytes));
        Log.i(TAG, Arrays.toString(timingsBytes));
        try {
            pollRx = byteArrayToUInt32(timingsBytes, pollRxOffset);
            pollTx = byteArrayToUInt32(timingsBytes, pollTxOffset);
            respRx = byteArrayToUInt32(timingsBytes, respRxOffset);
            respTx = byteArrayToUInt32(timingsBytes, respTxOffset);
            paymentSession.setTimings(pollRx, pollTx, respRx, respTx);
            distance = Float.parseFloat(new String(Arrays.copyOfRange(timingsBytes, distOffset, distOffset + 5), StandardCharsets.UTF_8));
            paymentSession.setDistance(distance);
            Log.i(TAG, "Distance: " + paymentSession.getDistance());
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        } finally {
            paymentSession.step();
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
            signature.write(Crypto.sign(privateKey, session.getTranscript()));
            Log.i(TAG, "Signing:\n" + BytesUtils.bytesToString(session.getTranscript()) + "\n");
            Log.i(TAG, "Card key modulus: " + privateKey.getModulus());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try (InputStream inputStream = ctx.getResources().openRawResource(R.raw.certificate)) {
            byte [] cert = Crypto.loadCertificate(inputStream);
            signature.write(cert);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Log.i(TAG,"Sending signature (sign || cert): " + BytesUtils.bytesToString(signature.toByteArray()));
        Log.i(TAG, "Signature length:" + signature.size());
        session.step();
        return apduWrapper.encode(signature.toByteArray());
    }

    public boolean verifySignature(byte[] signatureMessage, Session session) {
        signatureMessage = apduWrapper.decode(signatureMessage);
        byte[] signature = Arrays.copyOfRange(signatureMessage, 0, 128);
        byte[] cert = Arrays.copyOfRange(signatureMessage, 128, signatureMessage.length);
        Log.i(TAG, "Received signature: " + BytesUtils.bytesToString(signature));
        Log.i(TAG, "Received cert: " + BytesUtils.bytesToString(cert));
        try (InputStream inputStream = ctx.getResources().openRawResource(R.raw.ca_pubkey)) {
            RSAPublicKey caPublicKey = Crypto.loadPublicKey(inputStream);
            if(!Crypto.validateCertificate(cert, caPublicKey)){
                throw new RuntimeException("Invalid certificate");
                //TODO include output in the UI
            }
            RSAPublicKey cardKey = Crypto.getPublicKeyFromCertificate(cert, caPublicKey);
            Log.i(TAG, "Signing:\n" + BytesUtils.bytesToString(session.getRemoteTranscript()) + "\n");
            Log.i(TAG, "Card key modulus: " + cardKey.getModulus());
            if(! Crypto.verify(cardKey, signature, session.getRemoteTranscript())){
                throw  new RuntimeException("Invalid signature");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        session.setSignVerif(true);
        session.step();
        return true;
    }

    public void init(Session paymentSession) {
        paymentSession.step();
    }
}


