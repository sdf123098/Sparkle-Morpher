package com.micaftic.morpher.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R1.2.2 §10.4 依赖方向边界测试（第一版）。
 *
 * <p>扫描编译产物（main classes）字节码常量池引用，强制两类依赖方向：
 * <ol>
 *   <li><b>Raw GL 边界（RULE-GFX-2）</b>：{@code org.lwjgl.opengl.GL*} 只允许出现在
 *       GL 边界包内（按分支实际边界）：
 *       <ul>
 *         <li>26.x：{@code core.gpu}（Raw GL 渲染）+ {@code client.event}
 *             （{@code ClientSetupEvent} 启动时 GL/Vulkan 能力探测）；</li>
 *         <li>26.1.2 额外：{@code client.renderer.modernhud}（现代 HUD 的 FBO
 *             回读诊断，玩家确认后移除，届时收紧）；</li>
 *         <li>1.21.1：{@code core.gpu} + {@code client.renderer} + {@code client.event}。</li>
 *       </ul></li>
 *   <li><b>平台 API 边界</b>（仅多模块 fabric common 启用；NeoForge 单体分支整个
 *       源码就是 loader 代码，此规则不适用）：{@code net.fabricmc.fabric.*} /
 *       {@code net.neoforged.neoforge.*} / {@code net.minecraftforge.*} 只允许出现在
 *       平台适配边界（{@code core.architectury}、{@code client.event} 事件绑定层、
 *       {@code config} 配置边界），以及配置库类型白名单（ModConfigSpec /
 *       ForgeConfigSpec）与 loader 入口类白名单（YesSteveModel / ConfigRegistration）。</li>
 * </ol>
 *
 * <p>策略：第一版用「边界包 + 白名单」而非全量清零——现有历史引用允许保留，
 * 但<b>新增</b>的违规引用会使测试失败，从而把架构规则立起来（§4.7.2）。
 * 后续版本逐步收紧（§10.5 / R13）。
 */
class ArchitectureBoundaryTest {

    private static final Pattern GL_REF = Pattern.compile("org/lwjgl/opengl/GL[A-Za-z0-9_$]*");
    private static final Pattern FABRIC_REF = Pattern.compile("net/fabricmc/fabric/[A-Za-z0-9_$]+(?:/[A-Za-z0-9_$]+)*");
    private static final Pattern NEO_REF = Pattern.compile("net/neoforged/neoforge/[A-Za-z0-9_$]+(?:/[A-Za-z0-9_$]+)*");
    private static final Pattern FORGE_REF = Pattern.compile("net/minecraftforge/[A-Za-z0-9_$]+(?:/[A-Za-z0-9_$]+)*");

    /** 配置库类型：可在任意包引用（forge-config-api-port 提供）。 */
    private static final String NEO_MOD_CONFIG_SPEC = "net/neoforged/neoforge/common/ModConfigSpec";
    private static final String FORGE_CONFIG_SPEC = "net/minecraftforge/common/ForgeConfigSpec";

    /** loader 入口类：允许 forge fml 生命周期引用。 */
    private static final String ENTRY_YES_STEVE_MODEL = "com/micaftic/morpher/YesSteveModel";
    private static final String ENTRY_CONFIG_REGISTRATION = "com/micaftic/morpher/core/api/config/ConfigRegistration";

    // ---- 分支适配（§10.4：按分支自身依赖边界执行）----
    /** NeoForge 单体分支（neo1.21.1 / neo26.x）：整个源码即 loader 代码，跳过平台 API 检查。 */
    private static final boolean CHECK_PLATFORM_API = true;

    /** GL 渲染/探测边界包（RULE-GFX-2，按分支实际分布）。 */
    private static final String[] GL_BOUNDARY_PACKAGES = { "com/micaftic/morpher/core/gpu", "com/micaftic/morpher/client/renderer", "com/micaftic/morpher/client/event" };

    /** 平台适配边界包（仅 CHECK_PLATFORM_API=true 时生效）。 */
    private static final String[] PLATFORM_ADAPTER_PACKAGES = { "com/micaftic/morpher/core/architectury", "com/micaftic/morpher/core/api/config/fabric", "com/micaftic/morpher/config", "com/micaftic/morpher/client/event" };

    @Test
    void rawOpenGlOnlyInsideGlBoundary() throws Exception {
        List<Violation> violations = new ArrayList<>();
        for (ClassFile cf : scanMainClasses()) {
            if (isGlBoundary(cf.className)) {
                continue;
            }
            for (String ref : cf.references(GL_REF)) {
                violations.add(new Violation(cf.className, "org.lwjgl.opengl." + ref,
                        "Raw GL 只允许在 GL 边界包内（RULE-GFX-2）"));
            }
        }
        assertTrue(violations.isEmpty(), describe("Raw GL 边界", violations));
    }

    @Test
    void platformApiOnlyInsideAdapterAndConfigBoundaries() throws Exception {
        if (!CHECK_PLATFORM_API) {
            return; // NeoForge 单体分支：无独立 common 模块，平台 API 检查不适用
        }
        List<Violation> violations = new ArrayList<>();
        for (ClassFile cf : scanMainClasses()) {
            if (isPlatformAdapterBoundary(cf.className)) {
                continue;
            }
            boolean entryClass = cf.className.equals(ENTRY_YES_STEVE_MODEL)
                    || cf.className.equals(ENTRY_CONFIG_REGISTRATION);
            for (String ref : cf.references(FABRIC_REF)) {
                violations.add(new Violation(cf.className, displayReference(ref),
                        "fabric-api 只允许在平台适配边界（core.architectury/client.event/config）"));
            }
            for (String ref : cf.references(NEO_REF)) {
                if (ref.equals(NEO_MOD_CONFIG_SPEC) || ref.startsWith(NEO_MOD_CONFIG_SPEC + "$")) {
                    continue; // 配置库类型白名单
                }
                violations.add(new Violation(cf.className, displayReference(ref),
                        "NeoForge API 只允许在平台适配边界或 ModConfigSpec 白名单"));
            }
            for (String ref : cf.references(FORGE_REF)) {
                if (ref.equals(FORGE_CONFIG_SPEC) || ref.startsWith(FORGE_CONFIG_SPEC + "$")) {
                    continue; // 配置库类型白名单
                }
                if (entryClass && ref.startsWith("net/minecraftforge/fml/")) {
                    continue; // loader 入口白名单
                }
                violations.add(new Violation(cf.className, displayReference(ref),
                        "Forge API 只允许在平台适配边界、ForgeConfigSpec 白名单或 loader 入口"));
            }
        }
        assertTrue(violations.isEmpty(), describe("平台 API 边界", violations));
    }

    private static String displayReference(String reference) {
        return reference.replace('/', '.');
    }

    private static boolean isGlBoundary(String className) {
        for (String pkg : GL_BOUNDARY_PACKAGES) {
            if (className.startsWith(pkg + "/")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPlatformAdapterBoundary(String className) {
        for (String pkg : PLATFORM_ADAPTER_PACKAGES) {
            if (className.startsWith(pkg + "/")) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- helpers

    private record ClassFile(String className, String bytes) {
        List<String> references(Pattern pattern) {
            List<String> refs = new ArrayList<>();
            Matcher m = pattern.matcher(bytes);
            while (m.find()) {
                refs.add(m.group());
            }
            return refs;
        }
    }

    private record Violation(String className, String reference, String rule) {
    }

    private static List<ClassFile> scanMainClasses() throws Exception {
        Path classesRoot = locateMainClassesRoot();
        List<ClassFile> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(classesRoot)) {
            walk.filter(p -> p.toString().endsWith(".class"))
                    .filter(p -> p.toString().replace('\\', '/').contains("com/micaftic/morpher"))
                    .forEach(p -> {
                        try {
                            String rel = classesRoot.relativize(p).toString().replace('\\', '/');
                            String className = rel.substring(0, rel.length() - ".class".length());
                            String bytes = Files.readString(p, StandardCharsets.ISO_8859_1);
                            files.add(new ClassFile(className, bytes));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
        return files;
    }

    /**
     * 定位 main classes 根：从 classpath 中找包含 core/render/SmGraphicsCapabilities.class
     * 的目录（测试 classpath 含 main 输出）。
     */
    private static Path locateMainClassesRoot() throws Exception {
        URL marker = ArchitectureBoundaryTest.class.getResource("/com/micaftic/morpher/core/render/SmGraphicsCapabilities.class");
        if (marker != null && "file".equals(marker.getProtocol())) {
            Path cls = Paths.get(marker.toURI());
            Path root = cls.getParent();
            while (root != null && !Files.isDirectory(root.resolve("com/micaftic/morpher"))) {
                root = root.getParent();
            }
            return root;
        }
        String cp = System.getProperty("java.class.path", "");
        for (String entry : cp.split(Pattern.quote(java.io.File.pathSeparator))) {
            if (entry.isBlank()) continue;
            Path dir = Paths.get(entry);
            if (Files.isDirectory(dir) && Files.isRegularFile(
                    dir.resolve("com/micaftic/morpher/core/render/SmGraphicsCapabilities.class"))) {
                return dir;
            }
        }
        throw new IllegalStateException("Cannot locate main classes root for architecture boundary scan");
    }

    private static String describe(String rule, List<Violation> violations) {
        StringBuilder sb = new StringBuilder("依赖方向边界违规（" + rule + "）共 " + violations.size() + " 处:\n");
        for (Violation v : violations) {
            sb.append("  ").append(v.className).append(" -> ").append(v.reference)
                    .append("  [").append(v.rule).append("]\n");
        }
        return sb.toString();
    }
}
