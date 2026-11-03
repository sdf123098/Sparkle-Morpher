package com.micaftic.morpher.core.compat.gun.tacz;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class TacAnimationResolverTest {
    @Test void aimsBeforeSprintingAndUsesTheCrawlPoseWhenProne() {
        assertEquals("tac:hold:", TacAnimationResolver.holdPrefix(false, false, false, false));
        assertEquals("tac:aim:", TacAnimationResolver.holdPrefix(false, false, true, true));
        assertEquals("tac:run:", TacAnimationResolver.holdPrefix(false, true, false, true));
        assertEquals("tac:climb:", TacAnimationResolver.holdPrefix(true, true, true, false));
        assertEquals("tac:climbing:", TacAnimationResolver.holdPrefix(true, false, false, false));
    }

    @Test void reloadAndMeleeTakePriorityOverShooting() {
        assertEquals("tac:reload:", TacAnimationResolver.actionPrefix(false, false, true, true, true, 10));
        assertEquals("tac:melee:", TacAnimationResolver.actionPrefix(false, false, true, false, true, 10));
        assertEquals("tac:aim:fire:", TacAnimationResolver.actionPrefix(false, false, true, false, false, 10));
        assertEquals("tac:hold:fire:", TacAnimationResolver.actionPrefix(false, false, false, false, false, 10));
        assertEquals("tac:climbing:fire:", TacAnimationResolver.actionPrefix(true, false, false, false, false, 10));
        assertNull(TacAnimationResolver.actionPrefix(false, false, false, false, false, 0));
    }

    @Test void gunSpecificAnimationsWinAndMissingAnimationsDoNotReplaceTheBasePose() {
        var animations = Set.of("tac:hold$tacz:ak47", "tac:hold:rifle", "tac:hold:pistol", "tac:aim:rpg");
        assertEquals("tac:hold$tacz:ak47", TacAnimationResolver.select("tac:hold:", "rifle", "tac:hold$tacz:ak47", animations::contains));
        assertEquals("tac:hold:pistol", TacAnimationResolver.select("tac:hold:", "pistol", "", animations::contains));
        assertEquals("tac:hold:rifle", TacAnimationResolver.select("tac:hold:", "sniper", "", animations::contains));
        assertEquals("tac:aim:rpg", TacAnimationResolver.select("tac:aim:", "rpg", "", animations::contains));
        assertNull(TacAnimationResolver.select("tac:reload:", "rifle", "", animations::contains));
    }
}
