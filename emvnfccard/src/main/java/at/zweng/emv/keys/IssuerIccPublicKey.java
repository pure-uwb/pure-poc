package at.zweng.emv.keys;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.Date;

/**
 * @author Johannes Zweng on 24.10.17.
 */
public class IssuerIccPublicKey extends EmvPublicKey {
    public IssuerIccPublicKey(BigInteger publicExponent, BigInteger modulus, byte[] emvCertificate, Date expirationDate) {
        super(publicExponent, modulus, emvCertificate, expirationDate);
    }

    @Override
    public String getAlgorithm() {
        return ALGORITHM_RSA;
    }

    @Override
    public String getFormat() {
        return FORMAT_ISSUER_PUBKEY;
    }

    public RSAPublicKey getBaseKey(){
        KeyFactory keyFactory;
        RSAPublicKeySpec keySpec = new RSAPublicKeySpec(this.getModulus(), this.getPublicExponent());
        try {
            keyFactory = KeyFactory.getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        try {
            return (RSAPublicKey) keyFactory.generatePublic(keySpec);
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }
}
