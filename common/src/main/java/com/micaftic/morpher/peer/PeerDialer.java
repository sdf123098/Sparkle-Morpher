package com.micaftic.morpher.peer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Bounded interleaved A/AAAA candidate race. Returns one pinned TLS winner; never sends AUTH. */
public final class PeerDialer implements AutoCloseable {
    @FunctionalInterface public interface Resolver { InetAddress[] resolve(String host)throws UnknownHostException; }
    public record Connection(PeerTlsTransport transport,SSLSocket socket) implements AutoCloseable {
        @Override public void close(){transport.close();}
    }
    private final Resolver resolver;
    // JVM DNS can be non-interruptible. Keep two owned daemon workers maximum, even across retries.
    private static final ThreadPoolExecutor dns=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(8),
            r->{var t=new Thread(r,"spm-peer-dns");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private final ExecutorService attempts=Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("spm-peer-dial-",0).factory());
    private final Set<PeerTlsTransport> pending=ConcurrentHashMap.newKeySet();
    private volatile boolean closed;
    public PeerDialer(){this(InetAddress::getAllByName);}
    public PeerDialer(Resolver resolver){this.resolver=Objects.requireNonNull(resolver);}
    public synchronized Connection connect(PeerInvitation invite,int totalTimeoutMs)throws IOException {
        if(closed || !invite.validAt(Instant.now()))throw new IOException("Peer dialer closed or invitation expired");
        if(totalTimeoutMs<1 || totalTimeoutMs>30000)throw new IllegalArgumentException("Invalid dial deadline");
        long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(totalTimeoutMs);
        List<Future<List<InetSocketAddress>>> resolutions=new ArrayList<>();List<Future<Connection>> futures=new ArrayList<>();
        var cancelled=new java.util.concurrent.atomic.AtomicBoolean();
        Connection winner=null;
        PeerFailure diagnostic = null;
        try {
            for(var endpoint:invite.endpoints())resolutions.add(dns.submit(()->{
                var addresses=resolver.resolve(endpoint.host());List<InetSocketAddress> v4=new ArrayList<>(),v6=new ArrayList<>(),mixed=new ArrayList<>();
                for(var a:addresses) {var list=a instanceof Inet6Address?v6:v4;if(list.size()<8)list.add(new InetSocketAddress(a,endpoint.port()));}
                for(int i=0;i<Math.max(v4.size(),v6.size());i++){if(i<v6.size())mixed.add(v6.get(i));if(i<v4.size())mixed.add(v4.get(i));}
                return mixed;
            }));
            // Collect completed resolutions rather than waiting for the first unresponsive domain.
            SSLContext tls=PeerPinnedTls.context(invite.pin());
            var results=new ExecutorCompletionService<Connection>(attempts);int submitted=0,finished=0;Set<InetSocketAddress> addresses=new HashSet<>();
            var active=new Semaphore(2);
            while(!closed && System.nanoTime()<deadline) {
                for(var iterator=resolutions.iterator();iterator.hasNext();) {
                    var resolution=iterator.next();if(!resolution.isDone())continue;
                    try {
                        for(var address:resolution.get())if(addresses.size()<32 && addresses.add(address)) {
                            int delay=submitted++*100;
                            futures.add(results.submit(()->{
                                PeerTlsTransport transport=null;
                                try {
                                    Thread.sleep(delay);active.acquire();
                                    try {
                                        if(closed || cancelled.get() || Thread.currentThread().isInterrupted() || System.nanoTime()>=deadline)throw new IOException("Peer dial cancelled");
                                        transport=new PeerTlsTransport(tls);pending.add(transport);
                                        if(closed || cancelled.get())throw new IOException("Peer dial cancelled");
                                        int remaining=(int)Math.max(1,Math.min(5000,TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime())));
                                        var socket=transport.connect(address,remaining);
                                        if(closed || cancelled.get())throw new IOException("Peer dial cancelled");
                                        return new Connection(transport,socket);
                                    } finally {active.release();}
                                } catch(Exception e) {if(transport!=null){pending.remove(transport);transport.close();}throw e;}
                            }));
                        }
                    } catch(ExecutionException error){diagnostic = PeerFailure.classify(PeerFailure.Stage.DNS, error);}
                    iterator.remove();
                }
                var result=results.poll(Math.min(TimeUnit.MILLISECONDS.toNanos(20),Math.max(1,deadline-System.nanoTime())),TimeUnit.NANOSECONDS);
                if(result!=null) {
                    finished++;
                    try {winner=result.get();if(closed)throw new IOException("Peer dial cancelled");pending.remove(winner.transport());return winner;}
                    catch(ExecutionException error){
                        var rejected = PeerFailure.classify(PeerFailure.Stage.CONNECT, error);
                        if(diagnostic == null || diagnostic.stage() != PeerFailure.Stage.TLS || rejected.code() == PeerFrame.ErrorCode.PIN_MISMATCH) diagnostic = rejected;
                    }
                }
                if(finished==submitted && resolutions.isEmpty())break;
            }
            throw diagnostic == null ? new PeerFailure(PeerFailure.Stage.CONNECT, PeerFrame.ErrorCode.ENDPOINT_UNREACHABLE) : diagnostic;
        } catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Peer dial cancelled");}
        catch(java.security.GeneralSecurityException|RejectedExecutionException e){throw new IOException("Peer dial unavailable");}
        finally {
            cancelled.set(true);
            resolutions.forEach(f->f.cancel(true));futures.forEach(f->f.cancel(true));
            for(var transport:pending){transport.close();pending.remove(transport);}
        }
    }
    @Override public void close(){closed=true;for(var t:pending)t.close();pending.clear();dns.purge();attempts.shutdownNow();}
}
