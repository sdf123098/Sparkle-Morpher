package com.micaftic.morpher.core.model.lifecycle;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalImportCompletionTest {

    @Test
    void completionIsScheduledOnceOnTheClientExecutor() {
        Queue<Runnable> clientQueue = new ArrayDeque<>();
        List<Component> delivered = new ArrayList<>();
        LocalImportCompletion completion = new LocalImportCompletion(delivered::add,
                clientQueue::add, failure -> { throw failure; });

        assertTrue(completion.complete(Component.literal("finished")));
        assertFalse(completion.complete(Component.literal("duplicate")));
        assertEquals(List.of(), delivered);

        clientQueue.remove().run();

        assertEquals(1, delivered.size());
        assertEquals("finished", delivered.getFirst().getString());
    }

    @Test
    void callbackFailureIsReportedWithoutReopeningSettlement() {
        Executor direct = Runnable::run;
        AtomicInteger reported = new AtomicInteger();
        LocalImportCompletion completion = new LocalImportCompletion(result -> {
            throw new IllegalStateException(result.getString());
        }, direct, failure -> {
            assertEquals("discarded", failure.getMessage());
            reported.incrementAndGet();
        });

        assertTrue(completion.complete(Component.literal("discarded")));
        assertFalse(completion.complete(null));
        assertEquals(1, reported.get());
    }

    @Test
    void executorRejectionIsReportedWithoutReopeningSettlement() {
        AtomicInteger reported = new AtomicInteger();
        LocalImportCompletion completion = new LocalImportCompletion(result -> { }, command -> {
            throw new IllegalStateException("client executor stopped");
        }, failure -> {
            assertEquals("client executor stopped", failure.getMessage());
            reported.incrementAndGet();
        });

        assertTrue(completion.complete(Component.literal("finished")));
        assertFalse(completion.complete(Component.literal("retry")));
        assertEquals(1, reported.get());
    }
}
