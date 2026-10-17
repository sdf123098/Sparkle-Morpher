package com.micaftic.morpher.peer;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PeerRoomNetworkTest {
    @Test void threeTcpTlsMembersObserveAllSixDirectedCurrentSelectionsWithoutCloud()throws Exception {
        char[] password="test".toCharArray();var key=PeerTlsIdentity.create(password);byte[] secret=new byte[32];new java.security.SecureRandom().nextBytes(secret);
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();UUID room=UUID.randomUUID();Instant expires=Instant.now().plusSeconds(60);
        var base=PeerInvitation.single(room,"localhost:12345",key.pin(),expires,secret);
        try(var server=new PeerRoomServer(base,3,key,password,List.of(a,b,c))) {
            var endpoint=server.listen(InetAddress.getByName("127.0.0.1"),0);
            var invitation=PeerInvitation.single(room,"127.0.0.1:"+endpoint.getPort(),key.pin(),expires,secret);
            try(var ca=PeerRoomClient.join(invitation,a,3000);var cb=PeerRoomClient.join(invitation,b,3000);var cc=PeerRoomClient.join(invitation,c,3000)) {
                var clients=List.of(ca,cb,cc);for(int i=0;i<3;i++)clients.get(i).publish(new PeerAppearance(1,new PeerModelIdentity(PeerContentRoot.digest(new byte[]{(byte)i}),"ysm-v1"),"texture"+i));
                int directions=0;
                for(var observer:clients)for(var member:observer.poll().members())if(!member.session().equals(observer.session())) {
                    directions++;assertNotNull(member.appearance());assertTrue(member.appearance().textureVariant().startsWith("texture"));
                }
                assertEquals(6,directions);cb.publish(new PeerAppearance(2,null,null));
                assertTrue(ca.poll().members().stream().filter(m->m.session().minecraftUuid().equals(b)).findFirst().orElseThrow().appearance().cleared());
                assertThrows(IOException.class,()->ca.publish(new PeerAppearance(1,null,null)));
            }
        }
    }
    @Test void ipv4AndIpv6ListenersShareOneRoomAndUnauthorizedUuidIsRefused()throws Exception {
        char[] password="test".toCharArray();var key=PeerTlsIdentity.create(password);UUID room=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID();var expiry=Instant.now().plusSeconds(60);byte[] secret=new byte[32];
        try(var host=new PeerRoomServer(PeerInvitation.single(room,"localhost:12345",key.pin(),expiry,secret),3,key,password,List.of(a,b))) {
            var v4=host.listen(InetAddress.getByName("127.0.0.1"),0);var v6=host.listen(InetAddress.getByName("::1"),0);
            var i4=PeerInvitation.single(room,"127.0.0.1:"+v4.getPort(),key.pin(),expiry,secret);var i6=PeerInvitation.single(room,"[::1]:"+v6.getPort(),key.pin(),expiry,secret);
            try(var ca=PeerRoomClient.join(i4,a,3000);var cb=PeerRoomClient.join(i6,b,3000)) {
                assertEquals(ca.session().epoch(),cb.session().epoch());assertEquals(2,ca.poll().members().size());
                assertThrows(IOException.class,()->PeerRoomClient.join(i4,UUID.randomUUID(),3000));
            }
        }
    }
}
