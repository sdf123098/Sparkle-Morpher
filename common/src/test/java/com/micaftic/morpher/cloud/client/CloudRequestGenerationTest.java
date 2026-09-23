package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CloudRequestGenerationTest {
    @Test
    void staleScopeResultCannotRunItsStateMutation() {
        CloudRequestGeneration generation = new CloudRequestGeneration();
        long request = generation.current();
        CompletableFuture<String> source = new CompletableFuture<>();
        AtomicInteger applied = new AtomicInteger();
        CompletableFuture<String> guarded = generation.guard(request, source, ignored -> applied.incrementAndGet());

        generation.advance();
        source.complete("old scope");

        CompletionException failure = assertThrows(CompletionException.class, guarded::join);
        org.junit.jupiter.api.Assertions.assertInstanceOf(CancellationException.class, failure.getCause());
        assertEquals(0, applied.get());
    }
}
