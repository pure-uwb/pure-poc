package ch.ethz.emvextension;

import static at.zweng.emv.utils.EmvUtils.getUnsignedBytes;

import android.util.Log;

import ch.ethz.emvextension.Apdu.HexUtils;

import org.apache.commons.io.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import at.zweng.emv.keys.EmvKeyReader;
import at.zweng.emv.keys.IssuerIccPublicKey;
import at.zweng.emv.utils.EmvParsingException;
import at.zweng.emv.utils.EmvUtils;
import fr.devnied.bitlib.BytesUtils;

/*
 * This class is a hybrid between EMV crypto and simple RSA signatures.
 *
 * For the certificate, the EMV certificates are used
 * For the signature which would correspond to the SDAD signature,
 *  we use, at least for the stand alone prototype a simple RSA signature.
 * */
public class Crypto {
    private static final EmvKeyReader keyReader = new EmvKeyReader();


    public static RSAPrivateKey loadPrivateKey(InputStream inputStream) {
        KeyFactory keyFactory;
        try {
            keyFactory = KeyFactory.getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        PKCS8EncodedKeySpec keySpec = null;
        try {
            keySpec = new PKCS8EncodedKeySpec(IOUtils.toByteArray(inputStream));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            return (RSAPrivateKey) keyFactory.generatePrivate(keySpec);
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }

    public static RSAPublicKey loadPublicKey(InputStream inputStream) {
        KeyFactory keyFactory;
        try {
            keyFactory = KeyFactory.getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        X509EncodedKeySpec keySpec = null;
        try {
            keySpec = new X509EncodedKeySpec(IOUtils.toByteArray(inputStream));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            return (RSAPublicKey) keyFactory.generatePublic(keySpec);
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte [] loadCertificate(InputStream inputStream){
        try {
            byte[] certHex = IOUtils.toByteArray(inputStream);
            return BytesUtils.fromString(new String(certHex, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean validateCertificate(byte[] cert, RSAPublicKey caPublicKey) {
        try {
            return keyReader.validateIccPublicKey(
                    new IssuerIccPublicKey(caPublicKey.getPublicExponent(),
                            caPublicKey.getModulus(), null, null),
                    cert, null, caPublicKey.getPublicExponent().toByteArray());
        } catch (EmvParsingException e) {
            throw new RuntimeException(e);
        }
    }

    public static RSAPublicKey getPublicKeyFromCertificate(byte[] cert, RSAPublicKey caPublicKey) {
        try {
            return keyReader.parseIccPublicKey(
                    new IssuerIccPublicKey(BigInteger.valueOf(3), caPublicKey.getModulus(), null, null),
                    cert, null, EmvUtils.getUnsignedBytes(caPublicKey.getPublicExponent())).getBaseKey();
        } catch (EmvParsingException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean verify(RSAPublicKey pk, byte[] signature, byte[] message) {
        //Let's check the signature
        Signature publicSignature = null;

        try {
            publicSignature = Signature.getInstance("SHA256withRSA");
            publicSignature.initVerify(pk);
            publicSignature.update(message);
            return publicSignature.verify(signature);
        } catch (InvalidKeyException | SignatureException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] sign(RSAPrivateKey sk, byte[] message) {
        try {
            Signature privateSignature = Signature.getInstance("SHA256withRSA");
            Signature.getInstance("SHA256withRSA");
            privateSignature.initSign(sk);
            privateSignature.update(message);
            return privateSignature.sign();
        } catch (InvalidKeyException | SignatureException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Manually perform RSA operation: data ^ exponent mod modulus
     * "Recovery function" in the EMV Book 2
     *
     * @param data     data bytes to operate on
     * @param exponent exponent
     * @param modulus  modulus
     * @return data ^ exponent mod modulus
     */
    private static byte[] calculateRSA(byte[] data, BigInteger exponent, BigInteger modulus) throws EmvParsingException {
        // bigInts here are unsigned:
        BigInteger dataBigInt = new BigInteger(1, data);

        return getUnsignedBytes(dataBigInt.modPow(exponent, modulus));
    }

    public static byte[] encodePublicKey(ECPublicKey pk){
        byte [] x_byte =  pk.getW().getAffineX().toByteArray();
        byte [] y_byte =  pk.getW().getAffineY().toByteArray();
        Log.i("DEBUG", "Pk: " + HexUtils.bin2hex(x_byte) + "Len: " + x_byte.length);
        Log.i("DEBUG", "Pk: " + HexUtils.bin2hex(y_byte) + "Len: " + y_byte.length);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            baos.write( (byte)x_byte.length);
            baos.write(x_byte);
            baos.write( (byte)y_byte.length);
            baos.write(y_byte);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        byte [] encoded =  baos.toByteArray();//key.getPublic().getEncoded();
        return encoded;
    }

    public static PublicKey decodePublicKey(byte [] pk){
        int x_len = pk[0];
        byte[] x_byte = Arrays.copyOfRange(pk, 1, x_len + 1);
        int y_len = pk[x_len + 1];
        byte[] y_byte = Arrays.copyOfRange(pk, 2 + x_len, 2 + x_len + y_len);

        Log.i("DEBUG",  "x_len:" + x_len);
        Log.i("DEBUG",  "x_len:" + y_len);

        Log.i("DEBUG", "x: " + HexUtils.bin2hex(x_byte));
        Log.i("DEBUG", "y: " + HexUtils.bin2hex(y_byte));

        ECPoint pubPoint = new ECPoint(new BigInteger(x_byte),new BigInteger(y_byte));
        AlgorithmParameters parameters = null;
        try {
            parameters = AlgorithmParameters.getInstance("EC");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        try {
            parameters.init(new ECGenParameterSpec("secp256r1"));
        } catch (InvalidParameterSpecException e) {
            throw new RuntimeException(e);
        }
        ECParameterSpec ecParameters = null;
        try {
            ecParameters = parameters.getParameterSpec(ECParameterSpec.class);
        } catch (InvalidParameterSpecException e) {
            throw new RuntimeException(e);
        }
        ECPublicKeySpec pubSpec = new ECPublicKeySpec(pubPoint, ecParameters);

        KeyFactory keyFactory = null; // Tried ECDSA as well
        try {
            keyFactory = KeyFactory.getInstance("EC");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        ECPublicKey ecPublicKey = null; // <-- exception here
        try {
            ecPublicKey = (ECPublicKey) keyFactory.generatePublic(pubSpec);
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }

        System.out.println(ecPublicKey);
        return ecPublicKey;
    }

}
