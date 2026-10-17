package com.micaftic.morpher.peer;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded instance-owned sockets/workers. Control and asset sessions use the same listener. */
public final class PeerTlsTransport implements AutoCloseable {
    @FunctionalInterface public interface Handler { void connected(SSLSocket socket)throws IOException; }
    private final SSLContext context;
    private final Set<Socket> sockets=ConcurrentHashMap.newKeySet();
    private final Set<SSLServerSocket> listeners=ConcurrentHashMap.newKeySet();
    private final Semaphore socketBudget=new Semaphore(PeerLimits.CAPACITY_CEILING*2+PeerLimits.HANDSHAKE_COUNT);
    private final Semaphore handshakes=new Semaphore(PeerLimits.HANDSHAKE_COUNT);
    private final AtomicBoolean closed=new AtomicBoolean();
    private final ExecutorService workers=Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("spm-peer-io-",0).factory());
    public PeerTlsTransport(SSLContext context){this.context=Objects.requireNonNull(context);}
    public synchronized InetSocketAddress listen(InetAddress address,int port,Handler handler)throws IOException {
        if(closed.get())throw new IOException("Peer transport closed");
        if(listeners.size()>=2)throw new IOException("Peer listener limit");
        var listener=(SSLServerSocket)context.getServerSocketFactory().createServerSocket();
        try {
            listener.setEnabledProtocols(new String[]{"TLSv1.3"});listener.setReuseAddress(false);
            listener.bind(new InetSocketAddress(address,port),PeerLimits.HANDSHAKE_COUNT);
            listeners.add(listener);if(closed.get())throw new IOException("Peer transport closed");
            workers.submit(()->accept(listener,handler));return (InetSocketAddress)listener.getLocalSocketAddress();
        } catch(IOException|RuntimeException e){listeners.remove(listener);listener.close();throw e;}
    }
    private void accept(SSLServerSocket listener,Handler handler) {
        try {
            while(!closed.get()) {
                var socket=(SSLSocket)listener.accept();
                if(!socketBudget.tryAcquire()){socket.close();continue;}
                if(!handshakes.tryAcquire()){socketBudget.release();socket.close();continue;}
                sockets.add(socket);
                if(closed.get()){handshakes.release();release(socket);break;}
                try{workers.submit(()->serve(socket,handler));}
                catch(RejectedExecutionException e){handshakes.release();release(socket);}
            }
        } catch(IOException e){/* listener closure or failed accept; all accepted sockets retain their owner */}
        finally{listeners.remove(listener);quietClose(listener);}
    }
    private void serve(SSLSocket socket,Handler handler) {
        boolean permit=true;
        try {
            socket.setSoTimeout(10000);socket.setTcpNoDelay(true);socket.startHandshake();
            handshakes.release();permit=false;
            if(!closed.get())handler.connected(socket);
        } catch(IOException|RuntimeException e){/* protocol failures are scoped to this connection */}
        finally{if(permit)handshakes.release();release(socket);}
    }
    public SSLSocket connect(InetSocketAddress endpoint,int timeoutMs)throws IOException {
        if(timeoutMs<1 || timeoutMs>10000)throw new IllegalArgumentException("Invalid connect deadline");
        if(closed.get() || !socketBudget.tryAcquire())throw new IOException("Peer transport closed or full");
        SSLSocket socket=null;
        try {
            socket=(SSLSocket)context.getSocketFactory().createSocket();sockets.add(socket);
            if(closed.get())throw new IOException("Peer transport closed");
            socket.setEnabledProtocols(new String[]{"TLSv1.3"});socket.setSoTimeout(timeoutMs);socket.setTcpNoDelay(true);
            socket.connect(endpoint,timeoutMs);socket.startHandshake();
            if(closed.get())throw new IOException("Peer transport closed");
            return socket; // AUTH can only be written after this pinned handshake succeeded.
        } catch(IOException|RuntimeException e){if(socket==null)socketBudget.release();else release(socket);throw e;}
    }
    public void release(Socket socket){if(sockets.remove(socket)){quietClose(socket);socketBudget.release();}}
    public int activeConnections(){return sockets.size();}
    public int activeListeners(){return listeners.size();}
    private static void quietClose(Closeable socket){try{socket.close();}catch(IOException ignored){}}
    @Override public void close(){
        if(!closed.compareAndSet(false,true))return;
        for(var listener:listeners)quietClose(listener);listeners.clear();
        for(var socket:sockets)release(socket);
        workers.shutdownNow();
    }
}
