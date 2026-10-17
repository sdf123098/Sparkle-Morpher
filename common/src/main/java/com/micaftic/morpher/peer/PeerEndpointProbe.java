package com.micaftic.morpher.peer;

import java.io.IOException;
import java.net.InetSocketAddress;

/** Tests only the invited model endpoint's pinned TLS. Does not send room credentials or probe the game port. */
public final class PeerEndpointProbe {
    public record Result(String connectedAuthority,long elapsedMillis){}
    private PeerEndpointProbe(){}
    public static Result probe(PeerInvitation invite,int timeoutMillis)throws IOException {
        long began=System.nanoTime();try(var dialer=new PeerDialer();var connection=dialer.connect(invite,timeoutMillis)) {
            var address=(InetSocketAddress)connection.socket().getRemoteSocketAddress();return new Result(new PeerEndpoint(address.getAddress().getHostAddress(),address.getPort()).authority(),Math.max(0,(System.nanoTime()-began)/1_000_000L));
        }
    }
}
