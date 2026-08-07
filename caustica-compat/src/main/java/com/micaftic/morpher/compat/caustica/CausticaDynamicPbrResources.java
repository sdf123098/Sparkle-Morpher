package com.micaftic.morpher.compat.caustica;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.compat.ClientRenderCompatibility;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.client.texture.OuterFileTexture;
import com.micaftic.morpher.core.compat.oculus.ShadersTextureType;
import com.micaftic.morpher.compat.caustica.mixin.CausticaMinecraftAccessor;
import com.micaftic.morpher.compat.caustica.mixin.CausticaPackRepositoryAccessor;
import com.micaftic.morpher.model.ServerModelManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.world.flag.FeatureFlagSet;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/** Exposes runtime model PBR textures to resource-scanning renderers such as Caustica. */
public final class CausticaDynamicPbrResources implements ClientRenderCompatibility {
    private static final String PACK_ID = "sparkle_morpher_dynamic_pbr";
    private static final boolean CAUSTICA_LOADED = FabricLoader.getInstance().isModLoaded("caustica");
    private static final PackLocationInfo LOCATION = new PackLocationInfo(PACK_ID,
            Component.literal("Sparkle Morpher dynamic PBR textures"), PackSource.BUILT_IN,
            Optional.<KnownPack>empty());
    private static final Map<Identifier, byte[]> RESOURCES = new ConcurrentHashMap<>();
    /** Ref-counted live claimants per content-addressed location (multiple model assemblies may share one). */
    private static final Map<Identifier, Integer> ACTIVE_TEXTURES = new ConcurrentHashMap<>();
    private static final Path CACHE_DIRECTORY = ServerModelManager.CACHE.resolve("dynamic_pbr");
    private static final byte[] FINGERPRINT_SCHEMA =
            "sparkle-morpher-pbr-material-v1".getBytes(StandardCharsets.UTF_8);
    private static final ExecutorService CACHE_WRITER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Sparkle-Morpher-PBR-Cache-Writer");
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.setDaemon(true);
        return thread;
    });
    private static final RepositorySource SOURCE = output -> output.accept(createPack());
    private static final CausticaMaterialRefresh CAUSTICA_MATERIAL_REFRESH =
            CAUSTICA_LOADED ? findCausticaMaterialRefresh() : null;
    private static final CausticaEntityPrewarm CAUSTICA_ENTITY_PREWARM =
            CAUSTICA_LOADED ? findCausticaEntityPrewarm() : null;
    private static final long REFRESH_DEBOUNCE_NANOS = 400_000_000L;

    private static volatile boolean materialDirty;
    private static volatile boolean refreshing;
    private static volatile long refreshAfterNanos;
    private static volatile boolean entityPrewarmPending;
    private static volatile boolean entityViewInvalidationPending;
    private static volatile boolean installed;

    public CausticaDynamicPbrResources() {
    }

    @Override
    public boolean isAvailable() {
        return CAUSTICA_LOADED;
    }

    /** Caustica needs glow bones in a dedicated emissive pass to read material semantics. */
    @Override
    public boolean requiresEmissiveBoneSplit() {
        return true;
    }

    /**
     * Loads the persisted resource cache. Pack installation is deferred to the first client tick:
     * {@code initialize()} runs from {@code ClientModInitializer}, before the Minecraft constructor
     * has finished setting up its own resource pack repository, so calling {@code reload()} here
     * would race the engine's own pack setup.
     */
    @Override
    public void initialize() {
        int persisted = loadPersistedResources();
        if (persisted > 0) {
            YesSteveModel.LOGGER.info("[SM] Preloaded {} cached PBR resources for Caustica.", persisted);
        }
    }

    /** Stable across sessions, model load order, and texture-instance recycling. */
    @Override
    public Identifier resolveTextureLocation(OuterFileTexture texture) {
        if (texture.getSuffixTextures().isEmpty()) return null;
        byte[] normal = resourceData(texture, ShadersTextureType.NORMAL);
        byte[] specular = resourceData(texture, ShadersTextureType.SPECULAR);
        return materialLocation(texture.getResourceData(), normal, specular);
    }

    /** Publishes all PBR resources as soon as a model assembly exists; no GPU upload is performed. */
    @Override
    public void onModelAssemblyCreated(ModelAssembly assembly) {
        if (assembly == null) return;
        registerResources(assembly.getTextures());
    }

    @Override
    public void onTextureRegistered(Identifier location, OuterFileTexture texture, boolean replaced) {
        if (texture.getSuffixTextures().isEmpty()) return;
        register(location, texture);
        markTextureActive(location);
        if (replaced) noteTextureInstanceReplaced(location);
    }

    @Override
    public void onTextureUploaded(Identifier location, OuterFileTexture texture) {
        if (texture.getSuffixTextures().isEmpty()) return;
        register(location, texture);
        noteTextureInstanceReplaced(location);
    }

    @Override
    public void onTextureInactive(Identifier location) {
        markTextureInactive(location);
    }

    private static void registerResources(Collection<? extends AbstractTexture> textures) {
        if (textures == null) return;
        for (AbstractTexture texture : textures) {
            if (texture instanceof OuterFileTexture outer && !outer.getSuffixTextures().isEmpty()) {
                Identifier location = materialLocation(outer);
                if (location != null) register(location, outer);
            }
        }
    }

    private static void register(Identifier textureLocation, OuterFileTexture texture) {
        if (textureLocation == null || texture.getSuffixTextures().isEmpty()) return;

        String basePath = textureLocation.getPath();
        boolean changed = putIfChanged(resource(textureLocation, basePath + ".png"), texture.getResourceData());
        for (Map.Entry<ShadersTextureType, ? extends AbstractTexture> entry
                : texture.getSuffixTextures().entrySet()) {
            if (!(entry.getValue() instanceof OuterFileTexture suffixTexture)) continue;
            String suffix = entry.getKey() == ShadersTextureType.NORMAL ? "_n"
                    : entry.getKey() == ShadersTextureType.SPECULAR ? "_s" : null;
            if (suffix != null) {
                changed |= putIfChanged(resource(textureLocation, basePath + suffix + ".png"),
                        suffixTexture.getResourceData());
            }
        }
        if (changed) scheduleMaterialRefresh();
    }

    /** Marks a content-hashed texture as backed by a live TextureManager entry (ref-counted). */
    private static void markTextureActive(Identifier textureLocation) {
        if (textureLocation != null
                && ACTIVE_TEXTURES.merge(textureLocation, 1, Integer::sum) == 1) {
            entityPrewarmPending = true;
        }
    }

    /**
     * Stops refresh prewarming from resolving a TextureManager entry that no longer exists. When
     * the last claimant releases the location, drops its in-memory byte copies; the disk cache
     * stays so a later model reload can republish without re-downloading.
     */
    private static void markTextureInactive(Identifier textureLocation) {
        if (textureLocation == null) return;
        Integer remaining = ACTIVE_TEXTURES.computeIfPresent(textureLocation,
                (ignored, count) -> count <= 1 ? null : count - 1);
        if (remaining == null) releaseResources(textureLocation);
    }

    private static void releaseResources(Identifier textureLocation) {
        String basePath = textureLocation.getPath();
        RESOURCES.remove(resource(textureLocation, basePath + ".png"));
        RESOURCES.remove(resource(textureLocation, basePath + "_n.png"));
        RESOURCES.remove(resource(textureLocation, basePath + "_s.png"));
    }

    /** A shared location now points at a different GPU image view; only Caustica's entity-view cache is stale. */
    private static void noteTextureInstanceReplaced(Identifier textureLocation) {
        if (!ACTIVE_TEXTURES.containsKey(textureLocation)) return;
        entityViewInvalidationPending = true;
        entityPrewarmPending = true;
    }

    @Override
    public void tick() {
        ensureInstalled();
        refreshMaterials(false);
        refreshEntityViews();
    }

    @Override
    public void flush() {
        ensureInstalled();
        refreshMaterials(true);
        refreshEntityViews();
    }

    /** Installs the always-enabled pack on the first client tick, once the engine's pack repo is stable. */
    private static void ensureInstalled() {
        if (installed) return;
        installed = true;
        try {
            install(((CausticaMinecraftAccessor) Minecraft.getInstance())
                    .sparkle_morpher_caustica$getResourcePackRepository());
        } catch (RuntimeException exception) {
            installed = false;
            YesSteveModel.LOGGER.error("[SM] Could not install dynamic PBR resource pack", exception);
        }
    }

    private static void scheduleMaterialRefresh() {
        materialDirty = true;
        refreshAfterNanos = System.nanoTime() + REFRESH_DEBOUNCE_NANOS;
    }

    private static void refreshMaterials(boolean force) {
        if (!materialDirty || refreshing
                || (!force && System.nanoTime() < refreshAfterNanos)) return;
        install(((CausticaMinecraftAccessor) Minecraft.getInstance())
                .sparkle_morpher_caustica$getResourcePackRepository());
        if (CAUSTICA_MATERIAL_REFRESH == null) return;

        refreshing = true;
        long started = System.nanoTime();
        try {
            if (!CAUSTICA_MATERIAL_REFRESH.refresh()) {
                refreshAfterNanos = System.nanoTime() + REFRESH_DEBOUNCE_NANOS;
                return;
            }
            materialDirty = false;
            // bindWorldTextures resets Caustica's entity texture state as part of a real material rebuild.
            entityViewInvalidationPending = false;
            entityPrewarmPending = true;
            YesSteveModel.LOGGER.info("[SM] Refreshed {} dynamic PBR resources for Caustica in {} ms.",
                    RESOURCES.size(), (System.nanoTime() - started) / 1_000_000L);
        } catch (ReflectiveOperationException exception) {
            refreshAfterNanos = System.nanoTime() + REFRESH_DEBOUNCE_NANOS;
            YesSteveModel.LOGGER.error("[SM] Dynamic PBR material refresh failed", exception);
        } finally {
            refreshing = false;
        }
    }

    /**
     * Re-resolves live entity texture views without rebuilding the world material registry. This is
     * the normal path for model activation and TextureManager image recycling, and deliberately does
     * not call RtComposite.bindWorldTextures (which also requests a full RT terrain clear).
     */
    private static void refreshEntityViews() {
        if (!entityPrewarmPending || CAUSTICA_ENTITY_PREWARM == null) return;
        boolean invalidateViews = entityViewInvalidationPending;
        try {
            CAUSTICA_ENTITY_PREWARM.prewarm(ACTIVE_TEXTURES.keySet(), invalidateViews);
            entityViewInvalidationPending = false;
            entityPrewarmPending = false;
        } catch (ReflectiveOperationException exception) {
            YesSteveModel.LOGGER.warn("[SM] Caustica entity texture prewarming failed", exception);
        }
    }

    private static void install(PackRepository repository) {
        CausticaPackRepositoryAccessor accessor = (CausticaPackRepositoryAccessor) repository;
        Set<RepositorySource> sources = accessor.sparkle_morpher_caustica$getSources();
        if (sources.contains(SOURCE)) return;
        LinkedHashSet<RepositorySource> updated = new LinkedHashSet<>(sources);
        updated.add(SOURCE);
        accessor.sparkle_morpher_caustica$setSources(updated);
        repository.reload();
    }

    private static Pack createPack() {
        Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo location) {
                return new Resources(location);
            }

            @Override
            public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
                return new Resources(location);
            }
        };
        Pack.Metadata metadata = new Pack.Metadata(Component.literal("Runtime model PBR textures"),
                PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), List.of());
        return new Pack(LOCATION, supplier, metadata,
                new PackSelectionConfig(true, Pack.Position.TOP, true));
    }

    private static Identifier resource(Identifier base, String path) {
        return Identifier.fromNamespaceAndPath(base.getNamespace(), path);
    }

    private static boolean putIfChanged(Identifier id, byte[] data) {
        byte[] previous = RESOURCES.put(id, data);
        boolean changed = previous == null || !Arrays.equals(previous, data);
        if (changed) CACHE_WRITER.execute(() -> persist(id, data));
        return changed;
    }

    private static Identifier materialLocation(OuterFileTexture texture) {
        if (texture.getSuffixTextures().isEmpty()) return null;
        byte[] normal = resourceData(texture, ShadersTextureType.NORMAL);
        byte[] specular = resourceData(texture, ShadersTextureType.SPECULAR);
        return materialLocation(texture.getResourceData(), normal, specular);
    }

    private static Identifier materialLocation(byte[] albedo, byte[] normal, byte[] specular) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(FINGERPRINT_SCHEMA);
            updateDigest(digest, albedo);
            updateDigest(digest, normal);
            updateDigest(digest, specular);
            return Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID,
                    "textures/pbr/" + HexFormat.of().formatHex(digest.digest()));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static void updateDigest(MessageDigest digest, byte[] data) {
        int length = data == null ? -1 : data.length;
        digest.update((byte) (length >>> 24));
        digest.update((byte) (length >>> 16));
        digest.update((byte) (length >>> 8));
        digest.update((byte) length);
        if (data != null) digest.update(data);
    }

    private static byte[] resourceData(OuterFileTexture texture, ShadersTextureType type) {
        AbstractTexture suffix = texture.getSuffixTextures().get(type);
        return suffix instanceof OuterFileTexture outer ? outer.getResourceData() : null;
    }

    private static int loadPersistedResources() {
        if (!Files.isDirectory(CACHE_DIRECTORY)) return 0;
        int loaded = 0;
        try (Stream<Path> files = Files.walk(CACHE_DIRECTORY)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Path relative = CACHE_DIRECTORY.relativize(file);
                if (relative.getNameCount() < 2) continue;
                String namespace = relative.getName(0).toString();
                String path = relative.subpath(1, relative.getNameCount()).toString().replace('\\', '/');
                RESOURCES.put(Identifier.fromNamespaceAndPath(namespace, path), Files.readAllBytes(file));
                loaded++;
            }
        } catch (Exception exception) {
            YesSteveModel.LOGGER.warn("[SM] Could not load the persistent PBR resource cache", exception);
        }
        return loaded;
    }

    private static void persist(Identifier id, byte[] data) {
        try {
            Path namespaceRoot = CACHE_DIRECTORY.resolve(id.getNamespace()).normalize();
            Path target = namespaceRoot.resolve(id.getPath()).normalize();
            if (!target.startsWith(namespaceRoot)) return;
            Files.createDirectories(target.getParent());
            if (!Files.isRegularFile(target) || !Arrays.equals(Files.readAllBytes(target), data)) {
                Files.write(target, data);
            }
        } catch (IOException exception) {
            YesSteveModel.LOGGER.debug("[SM] Could not persist dynamic PBR resource {}", id, exception);
        }
    }

    private static CausticaMaterialRefresh findCausticaMaterialRefresh() {
        try {
            Class<?> contextClass = Class.forName("dev.comfyfluffy.caustica.rt.RtContext");
            Class<?> compositeClass = Class.forName("dev.comfyfluffy.caustica.rt.RtComposite");
            Method bindWorldTextures = compositeClass.getDeclaredMethod("bindWorldTextures", contextClass);
            bindWorldTextures.setAccessible(true);
            Field worldPipeline = compositeClass.getDeclaredField("worldPipeline");
            worldPipeline.setAccessible(true);
            return new CausticaMaterialRefresh(contextClass.getMethod("currentOrNull"),
                    contextClass.getMethod("waitIdle"), compositeClass.getField("INSTANCE"),
                    worldPipeline, bindWorldTextures);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            YesSteveModel.LOGGER.warn("[SM] Caustica targeted material refresh is unavailable", exception);
            return null;
        }
    }

    private static CausticaEntityPrewarm findCausticaEntityPrewarm() {
        try {
            Class<?> entityTextures = Class.forName("dev.comfyfluffy.caustica.rt.entity.RtEntityTextures");
            Field viewCache = entityTextures.getDeclaredField("viewCache");
            viewCache.setAccessible(true);
            return new CausticaEntityPrewarm(entityTextures.getField("INSTANCE"),
                    entityTextures.getMethod("slotFor", RenderType.class),
                    entityTextures.getMethod("materialIdFor", RenderType.class, boolean.class), viewCache);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            YesSteveModel.LOGGER.debug("[SM] Caustica entity texture prewarming is unavailable", exception);
            return null;
        }
    }

    private record CausticaMaterialRefresh(Method currentContext, Method waitIdle,
                                           Field compositeInstance, Field worldPipeline,
                                           Method bindWorldTextures) {
        private boolean refresh() throws ReflectiveOperationException {
            Object context = currentContext.invoke(null);
            Object composite = compositeInstance.get(null);
            if (context == null || worldPipeline.get(composite) == null) return false;
            waitIdle.invoke(context);
            bindWorldTextures.invoke(composite, context);
            return true;
        }
    }

    private record CausticaEntityPrewarm(Field instanceField, Method slotFor, Method materialIdFor,
                                         Field viewCache) {
        private void prewarm(Set<Identifier> textures, boolean invalidateViews)
                throws ReflectiveOperationException {
            Object instance = instanceField.get(null);
            if (invalidateViews) {
                ((Map<?, ?>) viewCache.get(instance)).clear();
            }
            for (Identifier texture : textures) {
                RenderType cutout = RenderTypes.entityCutout(texture);
                slotFor.invoke(instance, cutout);
                materialIdFor.invoke(instance, cutout, false);
                RenderType translucent = RenderTypes.entityTranslucent(texture);
                slotFor.invoke(instance, translucent);
                materialIdFor.invoke(instance, translucent, true);
            }
        }
    }

    private static final class Resources implements PackResources {
        private final PackLocationInfo location;

        private Resources(PackLocationInfo location) {
            this.location = location;
        }

        @Override
        public IoSupplier<InputStream> getRootResource(String... elements) {
            return null;
        }

        @Override
        public IoSupplier<InputStream> getResource(PackType type, Identifier id) {
            if (type != PackType.CLIENT_RESOURCES) return null;
            byte[] data = RESOURCES.get(id);
            return data == null ? null : () -> new ByteArrayInputStream(data);
        }

        @Override
        public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
            if (type != PackType.CLIENT_RESOURCES) return;
            RESOURCES.forEach((id, data) -> {
                if (id.getNamespace().equals(namespace) && id.getPath().startsWith(path)) {
                    output.accept(id, () -> new ByteArrayInputStream(data));
                }
            });
        }

        @Override
        public Set<String> getNamespaces(PackType type) {
            return type == PackType.CLIENT_RESOURCES ? Set.of(YesSteveModel.MOD_ID) : Set.of();
        }

        @Override
        public <T> T getMetadataSection(MetadataSectionType<T> type) {
            return null;
        }

        @Override
        public PackLocationInfo location() {
            return location;
        }

        @Override
        public void close() {
        }
    }
}
