package ch.ethz.emvextension.utils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class Certificates {
    static X509Certificate getIssuerCertificate(){
        CertificateFactory cf = null;
        X509Certificate cert;
        try {
            cf = CertificateFactory.getInstance("X.509");
        } catch (CertificateException e) {
            throw new RuntimeException(e);
        }
        String initialString = "-----BEGIN CERTIFICATE-----\n" +
                "MIIC7DCCAlWgAwIBAgIUOmmjOsQPCJe7Qjus68ZJr7VTWKswDQYJKoZIhvcNAQEL\n" +
                "BQAwgYYxCzAJBgNVBAYTAlpIMQ8wDQYDVQQIDAZadXJpY2gxDzANBgNVBAcMBlp1\n" +
                "cmljaDEWMBQGA1UECgwNTWFzdGVyRXhhbXBsZTEQMA4GA1UECwwHc2VjdGlvbjEW\n" +
                "MBQGA1UEAwwNTWFzdGVyRXhhbXBsZTETMBEGCSqGSIb3DQEJARYEYXNkZjAgFw0y\n" +
                "MzA1MTYxMjExNDBaGA8yMDUwMTAwMTEyMTE0MFowgYYxCzAJBgNVBAYTAlpIMQ8w\n" +
                "DQYDVQQIDAZadXJpY2gxDzANBgNVBAcMBlp1cmljaDEWMBQGA1UECgwNTWFzdGVy\n" +
                "RXhhbXBsZTEQMA4GA1UECwwHc2VjdGlvbjEWMBQGA1UEAwwNTWFzdGVyRXhhbXBs\n" +
                "ZTETMBEGCSqGSIb3DQEJARYEYXNkZjCBnzANBgkqhkiG9w0BAQEFAAOBjQAwgYkC\n" +
                "gYEAvs2dJjbruIqFbXZGTiTLA4AnXXq8Q5vCWbg4Qsg/o6YKxVJDGHnVoR5+j7E0\n" +
                "IDaiySe0nyZxA8KSUNZOXfym8e2d1ga05Aqbguk8EZrL50wbxFL4MU9rlAHopqn4\n" +
                "jUrlG6ZPM5sSvmkm/L4ifAPtnyxPwpoZZHI5OcR/lKC/d8UCAwEAAaNTMFEwHQYD\n" +
                "VR0OBBYEFJ5P7jrVkL63tMp9pN5xyc03P6QqMB8GA1UdIwQYMBaAFJ5P7jrVkL63\n" +
                "tMp9pN5xyc03P6QqMA8GA1UdEwEB/wQFMAMBAf8wDQYJKoZIhvcNAQELBQADgYEA\n" +
                "jc8fxDWAGr/0BTNSisTrlwv/5zccjCRpngiGQ+0MZpOm2ddJUeRvzLZfSsoMglPY\n" +
                "XiZAPlXF56pcePSPFRIwrIrIj3gNWT/sSJjn3OmZs2iRhtTqzBLnV0zHokXpuhbK\n" +
                "vFN3KMFHVEAm22ufGV8N4uMlyA2O4tOZeNPcx0xHAzY=\n" +
                "-----END CERTIFICATE-----\n";
        InputStream targetStream = new ByteArrayInputStream(initialString.getBytes());
        try{
            cert = (X509Certificate)cf.generateCertificate(targetStream);
        } catch (CertificateException e) {
            throw new RuntimeException(e);
        }
        return cert;
    }

    static X509Certificate getCardCertificate(){
        CertificateFactory cf = null;
        X509Certificate cert;
        try {
            cf = CertificateFactory.getInstance("X.509");
        } catch (CertificateException e) {
            throw new RuntimeException(e);
        }
        String initialString = "-----BEGIN CERTIFICATE-----\n" +
                "MIICbTCCAdYCAhI0MA0GCSqGSIb3DQEBBAUAMIGGMQswCQYDVQQGEwJaSDEPMA0G\n" +
                "A1UECAwGWnVyaWNoMQ8wDQYDVQQHDAZadXJpY2gxFjAUBgNVBAoMDU1hc3RlckV4\n" +
                "YW1wbGUxEDAOBgNVBAsMB3NlY3Rpb24xFjAUBgNVBAMMDU1hc3RlckV4YW1wbGUx\n" +
                "EzARBgkqhkiG9w0BCQEWBGFzZGYwHhcNMjMwNTE2MTIxNDI4WhcNMjQwNTE1MTIx\n" +
                "NDI4WjB2MQswCQYDVQQGEwJDSDEPMA0GA1UECAwGWnVyaWNoMQ8wDQYDVQQHDAZa\n" +
                "dXJpY2gxDjAMBgNVBAoMBWNhcmRzMQ4wDAYDVQQLDAVjYXJkczEOMAwGA1UEAwwF\n" +
                "Y2FyZDExFTATBgkqhkiG9w0BCQEWBnF3ZXJ0eTCBnzANBgkqhkiG9w0BAQEFAAOB\n" +
                "jQAwgYkCgYEArLUKR7jVVBofW6V2NEVVxC0St4bcwmKyCpamHLk5EbhkpSal9g1t\n" +
                "2GpQE6u7q5XUvD+f7M90qsXh6fx11yf7EekBs/WZv209jDWxcYEKEu3YRq/44Aij\n" +
                "jegIipDATEO6/2YqASkqVok34VUKAa4gkXPiYxT6kYus2cXXc91GyhECAwEAATAN\n" +
                "BgkqhkiG9w0BAQQFAAOBgQCE7wMIDIBKvxhLciL+M6cTLXh4l5PbTgonDdlzy8rF\n" +
                "uI3WXDlfWSRuNC+/tk/iUwZYKBRmXS8d7Nwkg00Jdpl9hGhBe7isPDm5o/+uen5s\n" +
                "n0j1ECcHxYuBEe6HoIDN5vK5uHj+gn0bNRvzwxBiGV28O61zPSt9/QJRgwQE+Jtu\n" +
                "gw==\n" +
                "-----END CERTIFICATE-----\n";
        InputStream targetStream = new ByteArrayInputStream(initialString.getBytes());
        try{
            cert = (X509Certificate)cf.generateCertificate(targetStream);
        } catch (CertificateException e) {
            throw new RuntimeException(e);
        }
        return cert;
    }
}
