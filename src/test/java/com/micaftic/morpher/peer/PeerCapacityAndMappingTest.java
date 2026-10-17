package com.micaftic.morpher.peer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Lightweight logical clients only. Does not assert Minecraft, Sakura service or GPU capacity. */
class PeerCapacityAndMappingTest {
    @TempDir Path temporary;
    @Test void thirtyTwoPinnedTlsMembersKeepControlAndMotionBounded()throws Exception {
        char[] password="test".toCharArray();var key=PeerTlsIdentity.create(password);var ids=new ArrayList<UUID>();for(int i=0;i<33;i++)ids.add(UUID.randomUUID());var expiry=Instant.now().plusSeconds(120);UUID room=UUID.randomUUID();byte[] secret=new byte[32];
        try(var server=new PeerRoomServer(PeerInvitation.single(room,"localhost:12345",key.pin(),expiry,secret),32,key,password,ids)) {
            var address=server.listen(InetAddress.getByName("127.0.0.1"),0);var invite=PeerInvitation.single(room,"127.0.0.1:"+address.getPort(),key.pin(),expiry,secret);var clients=new ArrayList<PeerRoomClient>();
            try{for(int i=0;i<32;i++){var client=PeerRoomClient.join(invite,ids.get(i),3000);clients.add(client);client.publish(new PeerAppearance(1,new PeerModelIdentity(PeerContentRoot.digest(new byte[]{(byte)i}),"ysm-v1"),"default"));assertTrue(client.publishMotion(new PeerMotion(1,1,0,"",0,10000,Map.of(),Map.of(),List.of())));}
                assertEquals(32,clients.getLast().poll().members().size());assertEquals(31,clients.getLast().motions().size());
                var refused=assertThrows(PeerFailure.class,()->PeerRoomClient.join(invite,ids.getLast(),3000));assertEquals(PeerFrame.ErrorCode.CAPACITY,refused.code());
            }finally{for(var client:clients)client.close();}
        }
    }
    @Test void separateModelTcpMappingCarriesPinnedControlAndAssetTraffic()throws Exception {
        int modelPort;try(var free=new ServerSocket(0)){modelPort=free.getLocalPort();}UUID a=UUID.randomUUID(),b=UUID.randomUUID();var ready=new CompletableFuture<PeerInvitation>();
        try(var mapping=new Mapping(modelPort);var host=new PeerRoomRuntime(temporary.resolve("host"),Runnable::run,ignored->{},ignored->{})) {
            host.host(PeerModelEntrances.parse("127.0.0.1:"+modelPort,"127.0.0.1:"+mapping.port()),a,List.of(a,b),ready::complete);var invite=ready.get(8,TimeUnit.SECONDS);
            assertNotEquals(modelPort,invite.endpoints().getFirst().port());assertTrue(PeerEndpointProbe.probe(invite,3000).connectedAuthority().endsWith(":"+mapping.port()));
            try(var guest=PeerRoomClient.join(invite,b,3000);var receiver=new PeerAssetStore(temporary.resolve("guest"))) {
                Path source=Files.createDirectory(temporary.resolve("mapped-source"));Files.writeString(source.resolve("m.bbmodel"),"{\"name\":\"separate TCP mapping\"}");host.share(()->new LocalShareSourceResolver.Selection(source,"m.bbmodel","bbmodel-v1",Map.of("m.bbmodel",PeerAssetManifest.Role.MODEL)),"default");
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);PeerRoomCoordinator.Member owner=null;
                while(System.nanoTime()<deadline){owner=guest.poll().members().stream().filter(member->member.session().minecraftUuid().equals(a)&&member.appearance()!=null).findFirst().orElse(null);if(owner!=null&&host.publishedAppearance()!=null)break;Thread.sleep(20);}
                assertNotNull(owner);var downloaded=guest.download(owner,receiver,()->true);assertTrue(Files.readString(downloaded.entrypoint()).contains("separate TCP mapping"));host.clear();
            }
        }
    }
    private static final class Mapping implements AutoCloseable {
        final ServerSocket listener=new ServerSocket(0,8,InetAddress.getByName("127.0.0.1"));final Set<Socket> sockets=ConcurrentHashMap.newKeySet();final Semaphore permits=new Semaphore(8);
        final ExecutorService workers=Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
        Mapping(int target)throws IOException {workers.submit(()->{try{while(!listener.isClosed()){var incoming=listener.accept();if(!permits.tryAcquire()){incoming.close();continue;}sockets.add(incoming);workers.submit(()->{
            Socket outgoing=null;try{outgoing=new Socket("127.0.0.1",target);sockets.add(outgoing);var destination=outgoing;var reverse=workers.submit(()->{try{destination.getInputStream().transferTo(incoming.getOutputStream());}catch(IOException ignored){}finally{closeSocket(incoming);}});incoming.getInputStream().transferTo(destination.getOutputStream());reverse.cancel(true);
            }catch(IOException ignored){}finally{closeSocket(incoming);if(outgoing!=null)closeSocket(outgoing);permits.release();}
        });}}catch(IOException ignored){}return null;});}
        int port(){return listener.getLocalPort();}
        void closeSocket(Socket socket){sockets.remove(socket);try{socket.close();}catch(IOException ignored){}}
        public void close()throws IOException {listener.close();for(var socket:sockets)closeSocket(socket);workers.shutdownNow();}
    }
}
