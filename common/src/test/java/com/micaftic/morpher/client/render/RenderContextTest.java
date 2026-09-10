package com.micaftic.morpher.client.render;

import com.micaftic.morpher.client.render.RenderContext.RenderScope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderContextTest {

    @Test
    void defaultsToWorldScopeWithNoEntityOrPartialTick() {
        assertEquals(RenderPass.WORLD, RenderContext.currentPass());
        assertNull(RenderContext.currentEntity());
        assertEquals(0.0f, RenderContext.partialTick());
        assertFalse(RenderContext.isGuiPreview());
        assertFalse(RenderContext.isOldHud());
        assertFalse(RenderContext.isFirstPerson());
        assertFalse(RenderContext.isPaperDoll());
    }

    @Test
    void legacyEnterRestoreKeepsPassOnlySemantics() {
        RenderPass previous = RenderContext.enter(RenderPass.GUI_PREVIEW);
        assertEquals(RenderPass.WORLD, previous);
        assertEquals(RenderPass.GUI_PREVIEW, RenderContext.currentPass());
        assertTrue(RenderContext.isGuiPreview());

        RenderContext.restore(RenderPass.WORLD);
        assertEquals(RenderPass.WORLD, RenderContext.currentPass());
        assertFalse(RenderContext.isGuiPreview());
    }

    @Test
    void predicateSemanticsAreExclusivePerPass() {
        RenderContext.enterScope(new RenderScope(RenderPass.GUI_PREVIEW, null, 0.0f));
        assertTrue(RenderContext.isGuiPreview());
        assertFalse(RenderContext.isOldHud());
        assertFalse(RenderContext.isFirstPerson());
        RenderContext.restoreScope(RenderScope.world());

        RenderContext.enterScope(new RenderScope(RenderPass.OLD_HUD, null, 0.0f));
        assertTrue(RenderContext.isGuiPreview());
        assertTrue(RenderContext.isOldHud());
        RenderContext.restoreScope(RenderScope.world());

        RenderContext.enterScope(new RenderScope(RenderPass.FIRST_PERSON, null, 0.0f));
        assertTrue(RenderContext.isFirstPerson());
        assertFalse(RenderContext.isPaperDoll());
        RenderContext.restoreScope(RenderScope.world());

        RenderContext.enterScope(new RenderScope(RenderPass.PAPER_DOLL, null, 0.0f));
        assertTrue(RenderContext.isPaperDoll());
        assertFalse(RenderContext.isFirstPerson());
        assertFalse(RenderContext.isGuiPreview());
        RenderContext.restoreScope(RenderScope.world());
    }

    @Test
    void enterScopeCarriesPartialTickAndRestoresPreviousScope() {
        RenderScope previous = RenderContext.enterScope(RenderPass.FIRST_PERSON, null, 0.5f);
        assertEquals(RenderPass.WORLD, previous.pass());
        assertEquals(RenderPass.FIRST_PERSON, RenderContext.currentPass());
        assertEquals(0.5f, RenderContext.partialTick());

        RenderContext.restoreScope(previous);
        assertEquals(RenderPass.WORLD, RenderContext.currentPass());
        assertEquals(0.0f, RenderContext.partialTick());
    }

    @Test
    void nestedScopesRestoreInLifoOrder() {
        RenderScope world = RenderContext.currentScope();
        RenderScope firstPerson = RenderContext.enterScope(RenderPass.FIRST_PERSON, null, 0.5f);
        RenderScope paperDoll = RenderContext.enterScope(RenderPass.PAPER_DOLL, null, 0.25f);

        assertEquals(RenderPass.PAPER_DOLL, RenderContext.currentPass());
        assertEquals(0.25f, RenderContext.partialTick());

        RenderContext.restoreScope(paperDoll);
        assertEquals(RenderPass.FIRST_PERSON, RenderContext.currentPass());
        assertEquals(0.5f, RenderContext.partialTick());

        RenderContext.restoreScope(firstPerson);
        assertEquals(RenderPass.WORLD, RenderContext.currentPass());
        assertEquals(0.0f, RenderContext.partialTick());
        assertEquals(world, RenderContext.currentScope());
    }

    @Test
    void legacyPassOnlyEnterResetsCarriedEntityAndPartialTick() {
        RenderScope previous = RenderContext.enterScope(RenderPass.FIRST_PERSON, null, 0.5f);
        assertEquals(0.5f, RenderContext.partialTick());

        RenderPass outerPass = RenderContext.enter(RenderPass.GUI_PREVIEW);
        assertEquals(RenderPass.FIRST_PERSON, outerPass);
        assertNull(RenderContext.currentEntity());
        assertEquals(0.0f, RenderContext.partialTick());

        RenderContext.restore(RenderPass.FIRST_PERSON);
        assertEquals(RenderPass.FIRST_PERSON, RenderContext.currentPass());
        assertEquals(0.0f, RenderContext.partialTick());

        RenderContext.restoreScope(previous);
        assertEquals(RenderPass.WORLD, RenderContext.currentPass());
    }

    @Test
    void passIsThreadLocalAndDoesNotLeakAcrossThreads() throws Exception {
        RenderContext.enterScope(RenderPass.FIRST_PERSON, null, 0.5f);
        try {
            RenderPass[] otherThreadPass = new RenderPass[1];
            Thread worker = new Thread(() -> otherThreadPass[0] = RenderContext.currentPass());
            worker.start();
            worker.join();
            assertEquals(RenderPass.WORLD, otherThreadPass[0]);
            assertEquals(RenderPass.FIRST_PERSON, RenderContext.currentPass());
        } finally {
            RenderContext.restoreScope(RenderScope.world());
        }
    }
}
