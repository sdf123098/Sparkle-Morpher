package com.micaftic.morpher.core.network;

import java.io.*;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Process-local client request admission. No account, truststore, proxy or backend changes. */
public final class NetworkAccess {
    public record State(NetworkPreset preset, boolean privacy, long epoch) {}
    public static final class Disabled extends IllegalStateException {
        public Disabled() { super("Cloud networking is disabled by the current network preset or privacy mode"); }
    }
    private static volatile State state = new State(NetworkPreset.AUTO, false, 0);
    private static final Map<CompletableFuture<?>, Runnable> inFlight = new HashMap<>();
    private NetworkAccess() {}
    public static State state() { return state; }
    public static boolean allowsCloud() { var s=state; return s.preset.allowsCloud(s.privacy); }
    public static boolean allowsPeer() { var s=state; return s.preset.allowsPeer(s.privacy); }
    public static boolean current(long epoch) { return state.epoch == epoch && allowsCloud(); }
    public static boolean update(NetworkPreset preset, boolean privacy) {
        List<Runnable> cancelled;
        synchronized(NetworkAccess.class) {
            var old=state; if(old.preset==preset && old.privacy==privacy) return false;
            state=new State(Objects.requireNonNull(preset),privacy,old.epoch+1);
            cancelled=List.copyOf(inFlight.values()); inFlight.clear();
        }
        cancelled.forEach(Runnable::run); return true;
    }
    public static <T> CompletableFuture<T> cloud(Supplier<CompletableFuture<T>> operation) {
        final State admitted; final CompletableFuture<T> upstream;
        var result=new CompletableFuture<T>();
        synchronized(NetworkAccess.class) {
            if(!allowsCloud()) return CompletableFuture.failedFuture(new Disabled());
            admitted=state;
            try { upstream=operation.get(); } catch(RuntimeException error) { return CompletableFuture.failedFuture(error); }
            inFlight.put(result,()->{ result.completeExceptionally(new Disabled()); upstream.cancel(true); });
        }
        upstream.whenComplete((value,error)->{
            boolean accepted;
            synchronized(NetworkAccess.class) {
                inFlight.remove(result);
                accepted=current(admitted.epoch);
            }
            // Completion can run arbitrary callbacks; never hold the admission lock across them.
            if(!accepted) { closeBody(value); result.completeExceptionally(new Disabled()); }
            else if(error!=null) result.completeExceptionally(error);
            else result.complete(value);
        });
        result.whenComplete((value,error)->{ if(result.isCancelled()) upstream.cancel(true); });
        return result;
    }
    private static void closeBody(Object value) {
        if(value instanceof java.net.http.WebSocket socket) socket.abort();
        if(value instanceof HttpResponse<?> response && response.body() instanceof InputStream stream) try { stream.close(); } catch(IOException ignored) {}
    }
}
