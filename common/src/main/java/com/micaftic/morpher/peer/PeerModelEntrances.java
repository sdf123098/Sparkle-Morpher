package com.micaftic.morpher.peer;

import java.io.IOException;
import java.net.*;
import java.util.*;

/** Explicit model listeners and advertised candidates. No Minecraft server port is consulted. */
public record PeerModelEntrances(List<PeerEndpoint> listeners,List<PeerEndpoint> advertised) {
    public PeerModelEntrances {
        listeners=List.copyOf(listeners);advertised=List.copyOf(advertised);
        if(listeners.isEmpty()||listeners.size()>2||new HashSet<>(listeners).size()!=listeners.size())throw new IllegalArgumentException("One or two model listeners required");
        if(advertised.isEmpty()||advertised.size()>PeerLimits.ENDPOINT_COUNT||new HashSet<>(advertised).size()!=advertised.size())throw new IllegalArgumentException("Invalid model entrance candidates");
        for(var endpoint:listeners)if(!Set.of("0.0.0.0","::","127.0.0.1","::1").contains(endpoint.host()))throw new IllegalArgumentException("Model listener must be a local wildcard or loopback");
        for(var endpoint:advertised)if(endpoint.hasLocalScope()||endpoint.host().equals("0.0.0.0")||endpoint.host().equals("::"))throw new IllegalArgumentException("Model entrance must be reachable without a sender-local scope");
    }
    public static PeerModelEntrances parse(String listeners,String advertised){return new PeerModelEntrances(endpoints(listeners),endpoints(advertised));}
    private static List<PeerEndpoint> endpoints(String text){if(text==null||text.length()>2400)throw new IllegalArgumentException("Invalid model entrances");return Arrays.stream(text.split(",",-1)).map(String::trim).map(PeerEndpoint::parse).toList();}
    public static PeerInvitation localRoute(PeerInvitation invite,String override) {
        if(override==null||override.isBlank())return invite;
        var candidates=endpoints(override);for(var endpoint:candidates)if(endpoint.host().equals("0.0.0.0")||endpoint.host().equals("::"))throw new IllegalArgumentException("Local route needs a remote model address");
        return new PeerInvitation(invite.roomId(),candidates,invite.pin(),invite.expiresAt(),invite.secret());
    }
    public static List<PeerEndpoint> listen(PeerRoomServer server,List<PeerEndpoint> listeners,PeerContentRoot pin)throws IOException {
        List<PeerEndpoint> local=new ArrayList<>();
        for(var endpoint:listeners){var bound=server.listen(InetAddress.getByName(endpoint.host()),endpoint.port());String loop=endpoint.host().contains(":")?"::1":"127.0.0.1";var candidate=new PeerEndpoint(loop,bound.getPort());
            verify(candidate,pin);local.add(candidate);
            if(loop.equals("::1"))try{var v4=new PeerEndpoint("127.0.0.1",bound.getPort());verify(v4,pin);if(!local.contains(v4))local.add(v4);}catch(IOException v6Only){/* Only measured coverage is reported. */}
        }
        return List.copyOf(local);
    }
    public static List<PeerEndpoint> automatic(PeerRoomServer server,PeerContentRoot pin)throws IOException {
        List<PeerEndpoint> local=new ArrayList<>();
        try{var bound=server.listen(InetAddress.getByName("::"),0);var v6=new PeerEndpoint("::1",bound.getPort());verify(v6,pin);local.add(v6);
            try{var v4=new PeerEndpoint("127.0.0.1",bound.getPort());verify(v4,pin);local.add(v4);}catch(IOException v6Only){var v4=server.listen(InetAddress.getByName("0.0.0.0"),0);var candidate=new PeerEndpoint("127.0.0.1",v4.getPort());verify(candidate,pin);local.add(candidate);}
        }catch(IOException unavailable){if(!local.isEmpty())throw unavailable;var v4=server.listen(InetAddress.getByName("0.0.0.0"),0);var candidate=new PeerEndpoint("127.0.0.1",v4.getPort());verify(candidate,pin);local.add(candidate);}
        return List.copyOf(local);
    }
    private static void verify(PeerEndpoint endpoint,PeerContentRoot pin)throws IOException {
        try(var transport=new PeerTlsTransport(PeerPinnedTls.context(pin))){var socket=transport.connect(new InetSocketAddress(InetAddress.getByName(endpoint.host()),endpoint.port()),1000);transport.release(socket);}
        catch(java.security.GeneralSecurityException unavailable){throw new IOException("Model listener TLS unavailable",unavailable);}
    }
    /** Automatic offers follow the game's LAN/VPN route and select its actually measured address family. */
    public static PeerEndpoint automaticRoute(InetAddress gameRoute,List<PeerEndpoint> coverage) {
        byte[] address=gameRoute.getAddress();boolean ula=gameRoute instanceof Inet6Address&&(address[0]&0xfe)==0xfc;
        boolean carrier=address.length==4&&(address[0]&255)==100&&(address[1]&255)>=64&&(address[1]&255)<=127;
        if(!gameRoute.isLoopbackAddress()&&!gameRoute.isSiteLocalAddress()&&!gameRoute.isLinkLocalAddress()&&!ula&&!carrier)throw new IllegalArgumentException("Public game routes require an explicit model mapping");
        boolean v6=gameRoute instanceof Inet6Address;var endpoint=coverage.stream().filter(value->value.host().contains(":")==v6).findFirst().orElseThrow(()->new IllegalArgumentException("Model listener does not cover the game route family"));
        return new PeerEndpoint(gameRoute.getHostAddress(),endpoint.port());
    }
}
