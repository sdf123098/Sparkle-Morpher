package com.micaftic.morpher.peer;

/** Server age is a duration, never a clock timestamp. Receiver adds the measured round trip conservatively. */
public record PeerMotionReceipt(PeerSession owner,PeerMotion motion,long transitAgeMillis) {
    public PeerMotionReceipt {if(owner==null||motion==null||transitAgeMillis<0||transitAgeMillis>10000)throw new IllegalArgumentException("Invalid motion receipt");}
}
