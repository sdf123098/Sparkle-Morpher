package com.micaftic.morpher.client.gui.resource.station;

import com.micaftic.morpher.client.gui.resource.ModelRepoClient;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/** Interruptible socket scope shared by the loader-specific HTTP implementations. */
public final class InterruptibleResourceRequest {
    private InterruptibleResourceRequest() {}

    public static void checkCancelled(ModelRepoClient.ProgressListener listener) {
        if (Thread.currentThread().isInterrupted() || listener != null && listener.isCancelled())
            throw new CancellationException("cancelled");
    }

    public static <T> T call(ModelRepoClient.ProgressListener listener, Callable<T> operation) throws IOException {
        checkCancelled(listener);
        if (listener == null || Thread.currentThread().isVirtual()) {
            try { return operation.call(); }
            catch (IOException | RuntimeException error) { throw error; }
            catch (Exception error) { throw new IOException(error); }
        }
        // JDK 21 virtual-thread socket reads close the socket when interrupted.
        // The caller waits for the worker to exit, retaining serial queue semantics.
        FutureTask<T> request = new FutureTask<>(operation);
        Thread reader = Thread.ofVirtual().name("SM-Resource-HTTP").unstarted(request);
        Runnable unregister = listener.onCancel(reader::interrupt);
        try {
            checkCancelled(listener);
            reader.start();
            return request.get();
        } catch (InterruptedException interrupted) {
            reader.interrupt();
            Thread.currentThread().interrupt();
            throw new CancellationException("cancelled");
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof IOException io) throw io;
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IOException(cause);
        } finally { unregister.run(); }
    }
}
