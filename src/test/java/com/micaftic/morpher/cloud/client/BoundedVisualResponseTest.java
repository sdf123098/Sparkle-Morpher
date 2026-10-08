package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Flow;
import static org.junit.jupiter.api.Assertions.*;

class BoundedVisualResponseTest {
    static final class Subscription implements Flow.Subscription {
        boolean cancelled;
        public void request(long n) {}
        public void cancel() { cancelled=true; }
    }
    @Test void streamsChunksUntilTheLimitAndCancelsOnAnOversizedChunk() throws Exception {
        var exact=new CloudHttpClient.BoundedJsonSubscriber(4);var subscription=new Subscription();exact.onSubscribe(subscription);
        exact.onNext(List.of(ByteBuffer.wrap(new byte[]{1,2}),ByteBuffer.wrap(new byte[]{3,4})));exact.onComplete();
        assertArrayEquals(new byte[]{1,2,3,4},exact.getBody().toCompletableFuture().get());assertFalse(subscription.cancelled);
        var excessive=new CloudHttpClient.BoundedJsonSubscriber(4);var cancelled=new Subscription();excessive.onSubscribe(cancelled);
        excessive.onNext(List.of(ByteBuffer.wrap(new byte[]{1,2,3})));excessive.onNext(List.of(ByteBuffer.wrap(new byte[]{4,5})));
        assertTrue(cancelled.cancelled);assertTrue(excessive.getBody().toCompletableFuture().isCompletedExceptionally());
    }
}
