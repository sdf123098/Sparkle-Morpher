package com.micaftic.morpher.geckolib3.core.event;

import com.micaftic.morpher.audio.AudioPlayerManager;
import com.micaftic.morpher.audio.YSMTickableSoundInstance;
import com.micaftic.morpher.geckolib3.core.AnimatableEntity;
import com.micaftic.morpher.geckolib3.core.keyframe.event.EventKeyFrame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SoundTimelineCompatibilityTest {
    private static class RecordingAudio extends AudioPlayerManager {
        final List<String> played = new ArrayList<>();
        int stops;

        @Override
        public boolean playSound(AnimatableEntity<?> entity, int soundId, String name,
                                 boolean forceReplace, Consumer<YSMTickableSoundInstance> callback) {
            played.add(name);
            return true;
        }

        @Override
        public void stopAll() {
            stops++;
        }
    }

    @Test
    void crossingTheEndPlaysTailSoundsWithoutStoppingThePreviousCycle() {
        var audio = new RecordingAudio();
        var executor = new SoundKeyFrameExecutor(List.of(
                new EventKeyFrame<>(0, "head"), new EventKeyFrame<>(19.6, "tail")), audio);
        executor.playSound(null, 19, true);
        executor.finishCycle(null, 20f, true);
        assertEquals(List.of("head", "tail"), audio.played);
        assertEquals(0, audio.stops);
        executor.playSound(null, 1, true);
        assertEquals(List.of("head", "tail", "head"), audio.played);
        executor.reset();
        assertEquals(1, audio.stops);
    }

    @Test
    void anEventExactlyAtTheEndIsNotPlayedTwice() {
        var audio = new RecordingAudio();
        var executor = new SoundKeyFrameExecutor(List.of(new EventKeyFrame<>(20, "tail")), audio);
        executor.playSound(null, 20, true);
        executor.finishCycle(null, 20, true);
        assertEquals(List.of("tail"), audio.played);
    }

    @Test
    void inactiveAndHistoricalWindowsDoNotReplaySuppressedAudio() {
        var audio = new RecordingAudio();
        var executor = new SoundKeyFrameExecutor(List.of(
                new EventKeyFrame<>(0, "head"), new EventKeyFrame<>(19.6, "tail")), audio);
        executor.finishCycle(null, 20, false);
        assertEquals(List.of(), audio.played);
        executor.skipBefore(10);
        executor.finishCycle(null, 20, true);
        assertEquals(List.of("tail"), audio.played);
        assertEquals(0, audio.stops);
    }
}
