package com.micaftic.morpher.peer;

import javax.net.ssl.*;
import java.net.Socket;
import java.security.*;
import java.security.cert.*;
import java.security.interfaces.ECPublicKey;

/** Invitation-authenticated room pin. No trust-all fallback, JVM defaults or Cloud trust changes. */
public final class PeerPinnedTls {
    private PeerPinnedTls(){}
    public static SSLContext context(PeerContentRoot expected)throws GeneralSecurityException {
        var context=SSLContext.getInstance("TLSv1.3");
        context.init(null,new TrustManager[]{new PinTrust(expected)},new SecureRandom());
        context.getClientSessionContext().setSessionCacheSize(32);context.getClientSessionContext().setSessionTimeout(60);
        return context;
    }
    private static final class PinTrust extends X509ExtendedTrustManager {
        private final PeerContentRoot pin;
        PinTrust(PeerContentRoot pin){this.pin=java.util.Objects.requireNonNull(pin);}
        private void verify(X509Certificate[] chain)throws CertificateException {
            if(chain==null || chain.length!=1)throw new CertificateException("Invalid room certificate chain");
            var cert=chain[0];cert.checkValidity();
            if(!(cert.getPublicKey() instanceof ECPublicKey key) || key.getParams().getCurve().getField().getFieldSize()!=256
                    || !cert.getSigAlgOID().equals("1.2.840.10045.4.3.2"))throw new CertificateException("Unsupported room certificate key");
            if(!MessageDigest.isEqual(pin.bytes(),PeerContentRoot.digest(cert.getPublicKey().getEncoded()).bytes()))
                throw new CertificateException("Peer room pin mismatch");
            try{cert.verify(cert.getPublicKey());}catch(GeneralSecurityException e){throw new CertificateException("Invalid room certificate signature",e);}
        }
        @Override public void checkServerTrusted(X509Certificate[] c,String a)throws CertificateException{verify(c);}
        @Override public void checkServerTrusted(X509Certificate[] c,String a,Socket s)throws CertificateException{verify(c);}
        @Override public void checkServerTrusted(X509Certificate[] c,String a,SSLEngine e)throws CertificateException{verify(c);}
        @Override public void checkClientTrusted(X509Certificate[] c,String a)throws CertificateException{throw new CertificateException("Client certificate authentication unsupported");}
        @Override public void checkClientTrusted(X509Certificate[] c,String a,Socket s)throws CertificateException{checkClientTrusted(c,a);}
        @Override public void checkClientTrusted(X509Certificate[] c,String a,SSLEngine e)throws CertificateException{checkClientTrusted(c,a);}
        @Override public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
    }
}
