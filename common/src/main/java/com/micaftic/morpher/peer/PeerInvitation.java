package com.micaftic.morpher.peer;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.time.Instant;
import java.util.*;

/** Bounded local invitation codec. The secret never appears in toString or exceptions. */
public final class PeerInvitation {
    private static final int MAGIC=0x53504931;
    private final UUID roomId;
    private final List<PeerEndpoint> endpoints;
    private final PeerContentRoot pin;
    private final Instant expiresAt;
    private final byte[] secret;
    public PeerInvitation(UUID roomId,List<PeerEndpoint> endpoints,PeerContentRoot pin,Instant expiresAt,byte[] secret) {
        this.roomId=Objects.requireNonNull(roomId); this.pin=Objects.requireNonNull(pin); this.expiresAt=Objects.requireNonNull(expiresAt);
        if(endpoints==null || endpoints.isEmpty() || endpoints.size()>PeerLimits.ENDPOINT_COUNT || new HashSet<>(endpoints).size()!=endpoints.size())
            throw new IllegalArgumentException("Invalid model endpoint candidates");
        this.endpoints=List.copyOf(endpoints);
        if(secret==null || secret.length!=32) throw new IllegalArgumentException("Invitation needs 256-bit secret");
        this.secret=secret.clone();
    }
    public UUID roomId(){return roomId;}
    public List<PeerEndpoint> endpoints(){return endpoints;}
    public PeerContentRoot pin(){return pin;}
    public Instant expiresAt(){return expiresAt;}
    public byte[] secret(){return secret.clone();}
    public boolean validAt(Instant now){return Objects.requireNonNull(now).isBefore(expiresAt);}
    /** Legacy single modelEndpoint maps to exactly one candidate without guessing additional addresses. */
    public static PeerInvitation single(UUID room,String modelEndpoint,PeerContentRoot pin,Instant expiry,byte[] secret) {
        return new PeerInvitation(room,List.of(PeerEndpoint.parse(modelEndpoint)),pin,expiry,secret);
    }
    public String encode() {
        try {
            var buffer=new ByteArrayOutputStream(); var out=new DataOutputStream(buffer);
            out.writeInt(MAGIC); out.writeByte(PeerLimits.PROTOCOL_MAJOR); out.writeByte(PeerLimits.PROTOCOL_MINOR);
            out.writeLong(roomId.getMostSignificantBits()); out.writeLong(roomId.getLeastSignificantBits());
            out.writeLong(expiresAt.getEpochSecond()); out.write(pin.bytes()); out.write(secret); out.writeByte(endpoints.size());
            for(var endpoint:endpoints) {
                // Sender interface indices have no meaning on a different machine.
                if(endpoint.hasLocalScope()) throw new IllegalArgumentException("IPv6 scope must be entered locally");
                byte[] text=endpoint.authority().getBytes(StandardCharsets.UTF_8); out.writeShort(text.length); out.write(text);
            }
            out.flush(); return "spm-peer:"+Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.toByteArray());
        } catch(IOException e){throw new IllegalStateException(e);}
    }
    public static PeerInvitation decode(String text) {
        try {
            if(text==null || text.length()>PeerLimits.INVITE_BYTES || !text.startsWith("spm-peer:"))
                throw new IllegalArgumentException();
            byte[] bytes=Base64.getUrlDecoder().decode(text.substring(9));
            var in=new DataInputStream(new ByteArrayInputStream(bytes));
            if(in.readInt()!=MAGIC || in.readUnsignedByte()!=PeerLimits.PROTOCOL_MAJOR || in.readUnsignedByte()!=PeerLimits.PROTOCOL_MINOR)
                throw new IllegalArgumentException();
            UUID room=new UUID(in.readLong(),in.readLong()); Instant expiry=Instant.ofEpochSecond(in.readLong());
            byte[] pin=new byte[32],secret=new byte[32]; in.readFully(pin); in.readFully(secret);
            int count=in.readUnsignedByte(); if(count<1 || count>PeerLimits.ENDPOINT_COUNT) throw new IllegalArgumentException();
            List<PeerEndpoint> endpoints=new ArrayList<>();
            for(int i=0;i<count;i++) {
                int length=in.readUnsignedShort(); if(length<1 || length>300 || length>in.available()) throw new IllegalArgumentException();
                byte[] raw=new byte[length]; in.readFully(raw);
                String authority=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();
                var endpoint=PeerEndpoint.parse(authority); if(endpoint.hasLocalScope()) throw new IllegalArgumentException();
                endpoints.add(endpoint);
            }
            if(in.available()!=0) throw new IllegalArgumentException();
            return new PeerInvitation(room,endpoints,new PeerContentRoot(HexFormat.of().formatHex(pin)),expiry,secret);
        } catch(IOException|RuntimeException e){throw new IllegalArgumentException("Invalid Peer invitation");}
    }
    @Override public String toString(){return "PeerInvitation[room="+roomId+",candidates="+endpoints.size()+",secret=redacted]";}
}
