package com.micaftic.morpher.peer;

/** Per authenticated control connection; a noisy member cannot consume an unbounded command stream. */
public final class PeerControlBudget {
    private long last;private double tokens=256;
    public PeerControlBudget(long now){last=now;}
    public boolean admit(int payloadBytes,long now){tokens=Math.min(256,tokens+Math.max(0,now-last)/1_000_000_000d*128);last=now;int cost=Math.max(1,(payloadBytes+8191)/8192);if(tokens<cost)return false;tokens-=cost;return true;}
}
