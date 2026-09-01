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
 * 1.2.4（§14.2）：动画求值边界 —— 基础动作求值核心不得引用 network / upload 类型。
 *
 * <p>纯求值类（快照采集、状态映射、动画选择）只允许读取 entity 并返回 PlayState，
 * 不得直接发包或触发上传副作用；网络同步（{@code network.sync.PlayerStateSynchronizer}、
 * {@code ysm.sync}）与上传协调只存在于各自显式调用点，后续版本（1.2.5 RenderContext）
 * 再统一收敛渲染副作用。
 *
 * <p>策略与 {@link ArchitectureBoundaryTest} 一致：扫描编译产物字节码常量池，
 * 白名单模式（违规即失败），防止新增引用重新污染求值边界。
 */
class ActionEvaluationBoundaryTest {

    private static final Pattern NETWORK_REF = Pattern.compile("com/micaftic/morpher/network/[A-Za-z0-9_$]+(?:/[A-Za-z0-9_$]+)*");
    private static final Pattern UPLOAD_REF = Pattern.compile("com/micaftic/morpher/client/upload/[A-Za-z0-9_$]+(?:/[A-Za-z0-9_$]+)*");

    /** 纯求值核心类（§14.1/§14.2 边界）。 */
    private static final String[] EVALUATION_CLASSES = {
            "com/micaftic/morpher/client/animation/PlayerActionState",
            "com/micaftic/morpher/client/animation/PlayerActionSnapshot",
            "com/micaftic/morpher/client/animation/ControllerActionResolver",
            "com/micaftic/morpher/client/animation/AnimationRegister",
            "com/micaftic/morpher/client/animation/AnimationManager",
            "com/micaftic/morpher/client/animation/AnimationState",
            "com/micaftic/morpher/client/animation/IAnimationPredicate",
            "com/micaftic/morpher/client/animation/Priority"
    };

    @Test
    void actionEvaluationCoreHasNoNetworkOrUploadReferences() throws Exception {
        List<Violation> violations = new ArrayList<>();
        for (ClassFile cf : scanMainClasses()) {
            if (!isEvaluationClass(cf.className)) {
                continue;
            }
            for (String ref : cf.references(NETWORK_REF)) {
                violations.add(new Violation(cf.className, display(ref),
                        "求值核心不得引用 network 类型（§14.2 副作用分离）"));
            }
            for (String ref : cf.references(UPLOAD_REF)) {
                violations.add(new Violation(cf.className, display(ref),
                        "求值核心不得引用 upload 类型（§14.2 副作用分离）"));
            }
        }
        assertTrue(violations.isEmpty(), describe(violations));
    }

    private static boolean isEvaluationClass(String className) {
        for (String clazz : EVALUATION_CLASSES) {
            if (className.equals(clazz)) {
                return true;
            }
        }
        return false;
    }

    private static String display(String reference) {
        return reference.replace('/', '.');
    }

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

    private static Path locateMainClassesRoot() throws Exception {
        URL marker = ActionEvaluationBoundaryTest.class.getResource("/com/micaftic/morpher/core/render/SmGraphicsCapabilities.class");
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
            if (entry.isBlank()) {
                continue;
            }
            Path dir = Paths.get(entry);
            if (Files.isDirectory(dir) && Files.isRegularFile(
                    dir.resolve("com/micaftic/morpher/core/render/SmGraphicsCapabilities.class"))) {
                return dir;
            }
        }
        throw new IllegalStateException("Cannot locate main classes root for action evaluation boundary scan");
    }

    private static String describe(List<Violation> violations) {
        StringBuilder sb = new StringBuilder("动作求值边界违规（§14.2）共 " + violations.size() + " 处:\n");
        for (Violation v : violations) {
            sb.append("  ").append(v.className).append(" -> ").append(v.reference)
                    .append("  [").append(v.rule).append("]\n");
        }
        return sb.toString();
    }
}
