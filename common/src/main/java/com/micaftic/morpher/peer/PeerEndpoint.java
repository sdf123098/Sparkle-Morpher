package com.micaftic.morpher.peer;

import java.net.IDN;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;

/** An explicit model TCP endpoint, independent of the Minecraft server address. */
public record PeerEndpoint(String host, int port) {
    public PeerEndpoint {
        if (host==null || host.isBlank() || host.length()>253 || port<1 || port>65535
                || host.chars().anyMatch(c->Character.isWhitespace(c) || Character.isISOControl(c))
                || host.contains("/") || host.contains("\\") || host.contains("@") || host.contains("["))
            throw new IllegalArgumentException("Invalid model endpoint");
        if(host.contains(":")) {
            String address=host.split("%",-1)[0];
            if(!address.matches("[0-9a-fA-F:.]+")) throw new IllegalArgumentException("Invalid IPv6 literal");
            try {
                if(!(InetAddress.getByName(address) instanceof Inet6Address)) throw new IllegalArgumentException("Invalid IPv6 literal");
            } catch(UnknownHostException e) { throw new IllegalArgumentException("Invalid IPv6 literal"); }
            if(host.contains("%") && !host.matches("[^%]+%[a-zA-Z0-9_.-]{1,32}"))
                throw new IllegalArgumentException("Invalid local IPv6 scope");
            int percent=host.indexOf('%');
            host=percent<0?host.toLowerCase(Locale.ROOT):host.substring(0,percent).toLowerCase(Locale.ROOT)+host.substring(percent);
        } else {
            host=IDN.toASCII(host,IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
            if(host.endsWith(".") || host.isEmpty()) throw new IllegalArgumentException("Invalid model host");
        }
    }
    public static PeerEndpoint parse(String authority) {
        if(authority==null) throw new IllegalArgumentException("Missing model endpoint");
        String host,port;
        if(authority.startsWith("[")) {
            int end=authority.indexOf(']');
            if(end<2 || end+1>=authority.length() || authority.charAt(end+1)!=':')
                throw new IllegalArgumentException("IPv6 endpoint needs [address]:port");
            host=authority.substring(1,end); port=authority.substring(end+2);
            if(!host.contains(":")) throw new IllegalArgumentException("Brackets require IPv6");
        } else {
            int colon=authority.indexOf(':');
            if(colon<1 || colon!=authority.lastIndexOf(':')) throw new IllegalArgumentException("Model endpoint needs host:port; bracket IPv6");
            host=authority.substring(0,colon); port=authority.substring(colon+1);
        }
        if(!port.matches("[0-9]{1,5}")) throw new IllegalArgumentException("Invalid model port");
        return new PeerEndpoint(host,Integer.parseInt(port));
    }
    public boolean hasLocalScope() { return host.contains("%"); }
    public String authority() { return (host.contains(":")?"["+host+"]":host)+":"+port; }
}
