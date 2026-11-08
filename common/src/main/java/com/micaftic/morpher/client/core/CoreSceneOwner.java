package com.micaftic.morpher.client.core;

import dev.spm.core.api.CancellationToken;
import dev.spm.core.api.DecodePolicy;
import dev.spm.core.api.FormatHint;
import dev.spm.core.api.ImportRequest;
import dev.spm.core.api.ImportResult;
import dev.spm.core.api.ModelDocument;
import dev.spm.core.api.ModelSource;
import dev.spm.core.api.ResourceBudget;
import dev.spm.core.parser.SpmModelParser;
import dev.spm.core.scene.EffectCommand;
import dev.spm.core.scene.EffectCursor;
import dev.spm.core.scene.MolangBindings;
import dev.spm.core.scene.MolangException;
import dev.spm.core.scene.MolangFunctionLibrary;
import dev.spm.core.scene.MolangHooks;
import dev.spm.core.scene.MolangValue;
import dev.spm.core.scene.PeerScriptPolicy;
import dev.spm.core.scene.PoseAnimationLibrary;
import dev.spm.core.scene.PoseAnimationPlayer;
import dev.spm.core.scene.PoseFrame;
import dev.spm.core.scene.SceneInputs;
import dev.spm.core.scene.ScriptBudget;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Independent Host owner. Tick/frame drivers advance it; render consumers only read snapshot(). */
public final class CoreSceneOwner implements AutoCloseable {
    public enum Lane { WORLD, PREVIEW }
    private final Lane lane;
    private final ParentResourceResolver resolver;
    private final ImportResult imported;
    private final ModelDocument document;
    private final String clip;
    private final PoseAnimationPlayer player;
    private final CoreSceneEpoch epoch = new CoreSceneEpoch();
    private final CancellationToken cancellation = new CancellationToken();
    private final PeerScriptPolicy policy = new PeerScriptPolicy();
    private final CoreActiveClock clock = new CoreActiveClock();
    private final EffectCursor effects = new EffectCursor();
    private final CoreBoneEffects boneEffects = new CoreBoneEffects();
    private boolean closed;
    private PoseFrame published;
    private long sampledSlot = -1;
    private SceneInputs sampledInputs;
    private final Map<MolangBindings.Call, MolangValue> sampledCalls = new HashMap<>();

    public static CoreSceneOwner load(Path path, Lane lane) throws IOException {
        ParentResourceResolver resolver = new ParentResourceResolver(path);
        ImportResult imported = null;
        try {
            var budget = new ResourceBudget(512L << 20);
            var request = new ImportRequest(new ModelSource(path.toAbsolutePath().normalize().toString(), resolver.entry(), resolver),
                    FormatHint.AUTO, DecodePolicy.localModel(), budget, new CancellationToken());
            imported = new SpmModelParser().importModel(request);
            String variant = System.getProperty("spm.core.scene.variant", "");
            var selected = imported.models().stream().filter(model -> variant.isEmpty() || model.key().equals(variant))
                    .min(java.util.Comparator.comparing(ImportResult.Model::key))
                    .orElseThrow(() -> new IOException("No selected Core model variant: " + variant));
            var document = selected.asset().document();
            ScriptBudget compileBudget = new ScriptBudget(2_000_000, 128, new CancellationToken());
            var library = PoseAnimationLibrary.fromModel(document, compileBudget,
                    dev.spm.core.scene.AnimationDecodeLimits.localYsm());
            var functions = MolangFunctionLibrary.load(document, imported.resources(), compileBudget, false);
            var initialize = new java.util.ArrayList<dev.spm.core.scene.MolangProgram>();
            var update = new java.util.ArrayList<dev.spm.core.scene.MolangProgram>();
            for (var hook : new java.util.TreeMap<>(functions.hooks()).entrySet()) {
                switch (hook.getKey()) {
                    case "player_init", "entity_init" -> initialize.addAll(hook.getValue());
                    case "player_update", "entity_update" -> update.addAll(hook.getValue());
                    case "sync", "defer" -> { }
                    default -> throw new IOException("Unrouted Core Host lifecycle hook: " + hook.getKey());
                }
            }
            var hooks = new MolangHooks(initialize, update, List.of(), List.of(),
                    functions.hooks().getOrDefault("defer", List.of()), functions.hooks().getOrDefault("sync", List.of()));
            String selectedClip = System.getProperty("spm.core.scene.clip", "");
            String clip = selectedClip.isEmpty() ? library.names().stream().sorted().findFirst()
                    .orElseThrow(() -> new IOException("Core model has no named executable animation")) : selectedClip;
            var player = new PoseAnimationPlayer(library, document.bindPose(), 12, 0, 0, functions.programs(), hooks);
            player.play(clip);
            return new CoreSceneOwner(lane, resolver, imported, document, clip, player);
        } catch (IOException | RuntimeException | Error failure) {
            if (imported != null) imported.close();
            resolver.close();
            throw failure;
        }
    }

    private CoreSceneOwner(Lane lane, ParentResourceResolver resolver, ImportResult imported, ModelDocument document,
                           String clip, PoseAnimationPlayer player) {
        this.lane = lane; this.resolver = resolver; this.imported = imported; this.document = document;
        this.clip = clip; this.player = player;
    }

    /** Resolver runs in the Host, between failed transactional attempts; Core sees data only. */
    public synchronized PoseFrame advance(long nowNanos, boolean paused, SceneInputs inputs,
                                          Function<MolangBindings.Call, MolangValue> sampleCall) {
        return advance(nowNanos, paused, () -> inputs, (call, frozen) -> sampleCall.apply(call));
    }

    /** Inputs are sampled only for a new logical slot; retries reuse that same immutable snapshot. */
    public synchronized PoseFrame advance(long nowNanos, boolean paused,
                                          java.util.function.Supplier<SceneInputs> sampleInputs,
                                          java.util.function.BiFunction<MolangBindings.Call, SceneInputs, MolangValue> sampleCall) {
        requireOpen();
        var time = clock.advance(nowNanos, paused);
        if (published != null && published.frameId() == time.slot()) return published;
        if (sampledSlot != time.slot()) {
            var candidateInputs = java.util.Objects.requireNonNull(sampleInputs.get());
            var candidateCalls = Map.copyOf(candidateInputs.bindings().calls());
            sampledInputs = candidateInputs;
            sampledCalls.clear();
            sampledCalls.putAll(candidateCalls);
            sampledSlot = time.slot();
        }
        ScriptBudget meter = policy.frame(time.slot(), cancellation);
        for (int request = 0; request <= 4096; request++) {
            try {
                var frozen = new SceneInputs(sampledInputs.queries(), new MolangBindings(sampledInputs.bindings().values(), sampledCalls),
                        lane == Lane.WORLD, sampledInputs.syncMode());
                var evaluated = player.evaluate(time.slot(), time.activeNanos(), frozen, meter);
                dev.spm.core.api.BonePose pose;
                try {
                    pose = boneEffects.apply(document.skeleton(), evaluated.pose(), evaluated.effects());
                } catch (RuntimeException failure) {
                    // Core has committed this frame. Retire the entire owner rather than expose an
                    // earlier snapshot or retry the already-committed player after Host adaptation fails.
                    close();
                    throw failure;
                }
                published = new PoseFrame(evaluated.instanceId(), evaluated.generation(), evaluated.frameId(), evaluated.step(),
                        evaluated.timeSeconds(), evaluated.interpolation(), evaluated.droppedSteps(), evaluated.animation(),
                        evaluated.animationTimeSeconds(), evaluated.finished(), pose, evaluated.variables(), evaluated.effects());
                return published;
            } catch (MolangException missing) {
                var binding = missing.bindingRequest().orElseThrow(() -> missing);
                if (sampledCalls.containsKey(binding)) throw new IllegalStateException("Core rejected an already sampled Host binding", missing);
                sampledCalls.put(binding, java.util.Objects.requireNonNull(sampleCall.apply(binding, sampledInputs)));
            }
        }
        throw new IllegalStateException("Core Host binding request limit exceeded");
    }

    /** Reservation is checked against the actual player generation and the separate Host epoch. */
    public synchronized List<EffectCommand> claim(CoreSceneEpoch.Ticket ticket) {
        if (closed || !epoch.current(ticket) || published == null) return List.of();
        return effects.claim(published, player.generation());
    }
    public synchronized boolean current(CoreSceneEpoch.Ticket ticket, EffectCommand command) {
        return !closed && epoch.current(ticket) && published != null && command.instanceId() == published.instanceId()
                && command.generation() == player.generation();
    }
    public synchronized CoreSceneEpoch.Ticket ticket() { return epoch.ticket(); }
    public synchronized PoseFrame snapshot() { return published; }
    public synchronized ModelDocument document() { requireOpen(); return document; }
    public Lane lane() { return lane; }
    public String clip() { return clip; }
    @Override public synchronized void close() {
        if (closed) return;
        epoch.retire(); // Invalidate every queued commit before retiring the assets.
        cancellation.cancel();
        player.reset();
        closed = true;
        published = null;
        try { imported.close(); } finally { resolver.close(); }
    }
    private void requireOpen() { if (closed) throw new IllegalStateException("Core Host owner is retired"); }
}
