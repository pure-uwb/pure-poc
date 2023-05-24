package com.example.emvextension.protocol;

import static at.zweng.emv.keys.EmvKeyReader.calculateRSA;
import static at.zweng.emv.utils.EmvUtils.getUnsignedBytes;
import static at.zweng.emv.utils.EmvUtils.notEmpty;

import android.app.Activity;
import android.util.Log;

import com.github.devnied.emvnfccard.iso7816emv.EmvTags;
import com.github.devnied.emvnfccard.iso7816emv.impl.TagImpl;
import com.github.devnied.emvnfccard.model.EmvCard;
import com.github.devnied.emvnfccard.utils.TlvUtil;

import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.concurrent.Semaphore;

import at.zweng.emv.ca.RootCa;
import at.zweng.emv.ca.RootCaManager;
import at.zweng.emv.keys.CaPublicKey;
import at.zweng.emv.keys.EmvKeyReader;
import at.zweng.emv.keys.EmvPublicKey;
import at.zweng.emv.keys.IssuerIccPublicKey;
import at.zweng.emv.keys.checks.ROCACheck;
import at.zweng.emv.utils.EmvParsingException;
import at.zweng.emv.utils.EmvUtils;
import fr.devnied.bitlib.BytesUtils;


public class EmvParserJob extends Thread {
    private ApplicationCryptogram AC;
    private final byte[] genAcResponse;
    private EmvCard card;
    private Semaphore s;
    private Activity activity;
    private int ca_res_id;
    public EmvParserJob(EmvCard card, Semaphore s, byte[] genAcResponse, ApplicationCryptogram AC, Activity activity, int ca_res_id) {
        this.card = card;
        this.s = s;
        this.activity = activity;
        this.ca_res_id = ca_res_id;
        this.genAcResponse = genAcResponse;
        this.AC = AC;
    }

    @Override
    public void run() {

        byte[] ACBytes = TlvUtil.getValue(genAcResponse, EmvTags.APP_CRYPTOGRAM);
        if(ACBytes == null){
            byte [] sdad = TlvUtil.getValue(genAcResponse, EmvTags.SIGNED_DYNAMIC_APPLICATION_DATA);
            ACBytes = getACFromSdad(sdad);
        }
        if(ACBytes == null){
            throw new RuntimeException("Could not retrieve AC");
        }

        AC.setAC(ACBytes);
        s.release();
    }

    private byte [] getACFromSdad(byte [] sdad){
        try {
            RootCaManager rootCaManager = new RootCaManager(activity, ca_res_id);

            // Parse the card
            final RootCa rootCaForCardScheme = rootCaManager.getCaForRid(card.getAid().substring(0, 10));
            final CaPublicKey caKey = rootCaForCardScheme.getCaPublicKeyWithIndex(card.getCaPublicKeyIndex());
            EmvKeyReader keyReader = new EmvKeyReader();

            if (card.getApplicationLabel() != null) {
                log("Application label: " + card.getApplicationLabel());
            }
            if (notEmpty(card.getIssuerPublicKeyCertificate()) &&
                    notEmpty(card.getIssuerPublicKeyExponent())) {
                final IssuerIccPublicKey issuerKey = keyReader.parseIssuerPublicKey(caKey, card.getIssuerPublicKeyCertificate(),
                        card.getIssuerPublicKeyRemainder(), card.getIssuerPublicKeyExponent());
                if (notEmpty(card.getIccPublicKeyCertificate()) &&
                        notEmpty(card.getIccPublicKeyExponent())) {
                    final EmvPublicKey iccKey = keyReader.parseIccPublicKey(issuerKey, card.getIccPublicKeyCertificate(),
                            card.getIccPublicKeyRemainder(), card.getIccPublicKeyExponent());
                    //TODO: Decrypt SDAD and getAC
                    byte [] sdad_rec = calculateRSA(sdad, iccKey.getPublicExponent(), iccKey.getModulus());
                    Log.i("EmvParserJob", BytesUtils.bytesToStringNoSpace(sdad_rec));
                    return Arrays.copyOfRange(sdad_rec, sdad_rec.length - 21, sdad_rec.length - 1); // Get the hash of the DYNAMICALLY SIGNED DATA
                }
            }
        }catch(Exception e){
            Log.e("EmvParserJob", e.toString());
        }
        return null;
    }
    private void log (String msg){
        Log.i("EmvParserJob", msg);
    }
}
