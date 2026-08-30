package com.micaftic.morpher.resource.gltf;

import java.util.List;
import java.util.Locale;

/**
 * Runtime animation policy for an independent glTF model.
 *
 * <p>This class deliberately has no Minecraft or loader dependency. Entity renderers
 * provide motion state and an absolute glTF clock; the controller resolves a stable
 * animation name, restarts it when the state changes, and returns a safe local time
 * for {@link GltfSceneEvaluator}.</p>
 */
public final class GltfAnimationController {
    public enum State {
        IDLE, WALK, RUN, JUMP, FALL, ATTACK, USE, DEATH, CUSTOM
    }

    private final GltfModel model;
    private State state = State.IDLE;
    private int animationIndex = -1;
    private float startClockSeconds;
    private float speed = 1.0f;
    private boolean loop = true;
    private boolean playing = true;

    public GltfAnimationController(GltfModel model) {
        if (model == null) {
            throw new IllegalArgumentException("glTF model must not be null");
        }
        this.model = model;
        this.animationIndex = resolveState(State.IDLE);
        if (this.animationIndex < 0) {
            this.animationIndex = firstPlayableAnimation();
        }
    }

    public GltfModel model() {
        return model;
    }

    public State state() {
        return state;
    }

    public int animationIndex() {
        return animationIndex;
    }

    public String animationName() {
        return animationIndex >= 0 && animationIndex < model.animations().size()
                ? model.animations().get(animationIndex).name()
                : null;
    }

    public float speed() {
        return speed;
    }

    public boolean loop() {
        return loop;
    }

    public boolean isPlaying() {
        return playing && animationIndex >= 0;
    }

    public void setSpeed(float speed) {
        this.speed = Float.isFinite(speed) ? Math.max(0.0f, speed) : 0.0f;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    public void stop() {
        this.playing = false;
    }

    public void resume() {
        this.playing = true;
    }

    /** Selects a named animation and restarts it at the supplied absolute clock. */
    public boolean play(String name, float clockSeconds) {
        int resolved = findAnimation(name);
        if (resolved < 0) {
            return false;
        }
        this.state = State.CUSTOM;
        switchAnimation(resolved, clockSeconds);
        return true;
    }

    /** Selects an animation by index and restarts it at the supplied absolute clock. */
    public boolean play(int index, float clockSeconds) {
        if (index < 0 || index >= model.animations().size()) {
            return false;
        }
        this.state = State.CUSTOM;
        switchAnimation(index, clockSeconds);
        return true;
    }

    /**
     * Selects a conventional third-person state using motion information.
     * Animation names are matched case-insensitively by common aliases.
     */
    public void selectForMotion(float horizontalSpeed, boolean onGround, boolean crouching,
                                boolean dying, float clockSeconds) {
        selectForMotion(horizontalSpeed, onGround, crouching, dying, false, false, clockSeconds);
    }

    /** Selects a third-person state, including transient hand actions. */
    public void selectForMotion(float horizontalSpeed, boolean onGround, boolean crouching,
                                boolean dying, boolean attacking, boolean usingItem,
                                float clockSeconds) {
        State next;
        if (dying) {
            next = State.DEATH;
        } else if (attacking) {
            next = State.ATTACK;
        } else if (usingItem) {
            next = State.USE;
        } else if (!onGround) {
            next = State.FALL;
        } else if (horizontalSpeed > 0.5f) {
            next = State.RUN;
        } else if (horizontalSpeed > 0.01f || crouching) {
            next = State.WALK;
        } else {
            next = State.IDLE;
        }
        selectState(next, clockSeconds);
    }

    public void selectState(State next, float clockSeconds) {
        if (next == null) {
            next = State.IDLE;
        }
        if (this.state == next && this.animationIndex >= 0) {
            return;
        }
        this.state = next;
        int resolved = resolveState(next);
        if (resolved >= 0) {
            switchAnimation(resolved, clockSeconds);
        } else if (!isPlayableAnimation(this.animationIndex)) {
            int fallback = firstPlayableAnimation();
            if (fallback >= 0) {
                switchAnimation(fallback, clockSeconds);
            }
        }
    }

    /** Returns the current animation time relative to its last state transition. */
    public float animationTimeSeconds(float clockSeconds) {
        if (!isPlaying() || !Float.isFinite(clockSeconds) || !Float.isFinite(startClockSeconds)) {
            return 0.0f;
        }
        float elapsed = (clockSeconds - startClockSeconds) * speed;
        if (!Float.isFinite(elapsed)) {
            return 0.0f;
        }
        elapsed = Math.max(0.0f, elapsed);
        float duration = model.animations().get(animationIndex).duration();
        if (!(duration > 0.0f) || !Float.isFinite(duration)) {
            return elapsed;
        }
        if (loop) {
            elapsed %= duration;
        } else {
            elapsed = Math.min(duration, elapsed);
        }
        return elapsed;
    }

    public GltfSceneEvaluator.Pose evaluate(GltfSceneEvaluator evaluator, int sceneIndex, float clockSeconds) {
        if (evaluator == null) {
            throw new IllegalArgumentException("glTF scene evaluator must not be null");
        }
        return evaluator.evaluate(sceneIndex, animationIndex,
                animationTimeSeconds(clockSeconds), loop);
    }

    private void switchAnimation(int resolved, float clockSeconds) {
        this.animationIndex = resolved;
        this.startClockSeconds = Float.isFinite(clockSeconds) ? clockSeconds : 0.0f;
        this.playing = true;
    }

    private int resolveState(State state) {
        List<String> aliases = switch (state) {
            case IDLE -> List.of("idle", "stand", "default");
            // Many exported models only contain one locomotion clip. Prefer a
            // walk clip when present, but use run as the movement fallback so
            // a low-speed player does not silently remain on idle.
            case WALK -> List.of("walk", "walking", "move", "movement", "run", "running", "sprint");
            case RUN -> List.of("run", "running", "sprint", "walk", "walking", "move", "movement");
            case JUMP -> List.of("jump", "jump_start");
            case FALL -> List.of("fall", "falling");
            case ATTACK -> List.of("attack", "swing", "swing_hand", "attack_empty", "dianchuo", "click", "poke", "stab");
            case USE -> List.of("use", "use_mainhand", "use_offhand", "interact", "dianchuo", "click", "poke");
            case DEATH -> List.of("death", "die");
            case CUSTOM -> List.of();
        };
        for (String alias : aliases) {
            int found = findAnimation(alias);
            if (found >= 0) {
                return found;
            }
        }
        return -1;
    }

    private int findAnimation(String name) {
        if (name == null || name.isBlank()) {
            return -1;
        }
        String wanted = normalize(name);
        for (int i = 0; i < model.animations().size(); i++) {
            String candidate = normalize(model.animations().get(i).name());
            if (candidate.equals(wanted) || candidate.contains(wanted)) {
                return i;
            }
        }
        return -1;
    }

    private int firstPlayableAnimation() {
        for (int i = 0; i < model.animations().size(); i++) {
            if (isPlayableAnimation(i)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isPlayableAnimation(int index) {
        return index >= 0 && index < model.animations().size()
                && !model.animations().get(index).channels().isEmpty();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
