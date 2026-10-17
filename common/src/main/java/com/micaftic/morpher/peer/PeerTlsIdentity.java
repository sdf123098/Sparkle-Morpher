package com.micaftic.morpher.peer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Per-room P-256 TLS key. Uses public JCA APIs only; never modifies the global SSL context. */
public final class PeerTlsIdentity {
    private final KeyStore keys;
    private final X509Certificate certificate;
    private PeerTlsIdentity(KeyStore keys,X509Certificate certificate){this.keys=keys;this.certificate=certificate;}
    public static PeerTlsIdentity create(char[] password)throws GeneralSecurityException,IOException {
        var random=new SecureRandom();var generator=KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"),random);var pair=generator.generateKeyPair();
        // DER wrapper for the generated public JCA key/signature, not a custom crypto implementation.
        byte[] algorithm=seq(oid(0x2A,0x86,0x48,0xCE,0x3D,0x04,0x03,0x02)); // ecdsa-with-SHA256
        byte[] name=seq(set(seq(oid(0x55,0x04,0x03),der(0x0C,"SPM Peer Room".getBytes(StandardCharsets.UTF_8)))));
        Instant now=Instant.now();byte[] validity=seq(time(now.minusSeconds(300)),time(now.plusSeconds(30L*86400)));
        byte[] tbs=seq(der(0xA0,integer(BigInteger.valueOf(2))),integer(new BigInteger(128,random).add(BigInteger.ONE)),
                algorithm,name,validity,name,pair.getPublic().getEncoded());
        var signer=Signature.getInstance("SHA256withECDSA");signer.initSign(pair.getPrivate(),random);signer.update(tbs);
        byte[] signature=signer.sign();byte[] bits=new byte[signature.length+1];System.arraycopy(signature,0,bits,1,signature.length);
        byte[] encoded=seq(tbs,algorithm,der(0x03,bits));
        var cert=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(encoded));
        cert.verify(pair.getPublic());cert.checkValidity();
        var store=KeyStore.getInstance("PKCS12");store.load(null,password);
        store.setKeyEntry("room",pair.getPrivate(),password,new java.security.cert.Certificate[]{cert});
        return new PeerTlsIdentity(store,cert);
    }
    public static PeerTlsIdentity load(InputStream input,char[] password)throws GeneralSecurityException,IOException {
        var store=KeyStore.getInstance("PKCS12");store.load(input,password);
        var cert=(X509Certificate)store.getCertificate("room");
        if(cert==null || !store.isKeyEntry("room"))throw new KeyStoreException("Missing Peer TLS identity");
        cert.checkValidity();return new PeerTlsIdentity(store,cert);
    }
    public void save(OutputStream output,char[] password)throws GeneralSecurityException,IOException{keys.store(output,password);}
    public PeerContentRoot pin(){return PeerContentRoot.digest(certificate.getPublicKey().getEncoded());}
    public SSLContext serverContext(char[] password)throws GeneralSecurityException {
        var manager=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());manager.init(keys,password);
        var context=SSLContext.getInstance("TLSv1.3");context.init(manager.getKeyManagers(),null,new SecureRandom());
        context.getServerSessionContext().setSessionCacheSize(64);context.getServerSessionContext().setSessionTimeout(60);
        return context;
    }
    private static byte[] time(Instant value){return der(0x18,DateTimeFormatter.ofPattern("uuuuMMddHHmmss'Z'").withZone(ZoneOffset.UTC).format(value).getBytes(StandardCharsets.US_ASCII));}
    private static byte[] integer(BigInteger value){return der(0x02,value.toByteArray());}
    private static byte[] oid(int...values){byte[] b=new byte[values.length];for(int i=0;i<b.length;i++)b[i]=(byte)values[i];return der(0x06,b);}
    private static byte[] seq(byte[]...values){return der(0x30,join(values));}
    private static byte[] set(byte[]...values){return der(0x31,join(values));}
    private static byte[] join(byte[][] values){var out=new ByteArrayOutputStream();for(var v:values)out.writeBytes(v);return out.toByteArray();}
    private static byte[] der(int tag,byte[] value){
        var out=new ByteArrayOutputStream();out.write(tag);
        if(value.length<128)out.write(value.length);
        else {int count=(32-Integer.numberOfLeadingZeros(value.length)+7)/8;out.write(0x80|count);for(int i=count-1;i>=0;i--)out.write(value.length>>>(i*8));}
        out.writeBytes(value);return out.toByteArray();
    }
    @Override public String toString(){return "PeerTlsIdentity[privateKey=redacted]";}
}
