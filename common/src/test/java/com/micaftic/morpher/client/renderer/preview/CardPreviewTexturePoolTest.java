package com.micaftic.morpher.client.renderer.preview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CardPreviewTexturePoolTest {
    private static final class Texture implements AutoCloseable {
        boolean closed;
        @Override public void close() { closed = true; }
    }

    @Test void allTwentySevenMixedCardsRetainTheirOwnImageAcrossFrames() {
        var pool = new CardPreviewTexturePool<Texture>(96, 8_388_608L);
        Texture[] textures = new Texture[27];
        pool.beginFrame();
        for (int i = 0; i < textures.length; i++) {
            textures[i] = pool.acquire("card" + i, 200L * 300, Texture::new);
        }
        for (int frame = 0; frame < 120; frame++) {
            pool.beginFrame();
            for (int i = 0; i < textures.length; i++) {
                assertSame(textures[i], pool.get("card" + i));
                assertFalse(textures[i].closed);
            }
        }
        pool.clear();
        for (Texture texture : textures) assertTrue(texture.closed);
    }

    @Test void evictionNeverClosesATextureAlreadySubmittedInCurrentFrame() {
        var pool = new CardPreviewTexturePool<Texture>(2, 20);
        pool.beginFrame();
        Texture first = pool.acquire("first", 10, Texture::new);
        Texture second = pool.acquire("second", 10, Texture::new);
        assertNull(pool.acquire("third", 10, Texture::new));
        assertFalse(first.closed);
        assertFalse(second.closed);
        pool.beginFrame();
        assertSame(second, pool.get("second"));
        assertNotNull(pool.acquire("third", 10, Texture::new));
        assertTrue(first.closed);
        assertFalse(second.closed);
    }

    @Test void pixelBudgetBoundsAllocationIndependentlyOfEntryCount() {
        var pool = new CardPreviewTexturePool<Texture>(96, 20);
        pool.beginFrame();
        assertNull(pool.acquire("oversize", 21, Texture::new));
        assertNotNull(pool.acquire("one", 15, Texture::new));
        assertNull(pool.acquire("two", 6, Texture::new));
        pool.beginFrame();
        assertNotNull(pool.acquire("two", 6, Texture::new));
        assertNull(pool.get("one"));
    }
}
