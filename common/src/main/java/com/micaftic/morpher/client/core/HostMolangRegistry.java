package com.micaftic.morpher.client.core;

import com.micaftic.morpher.YesSteveModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Stateless compiler/cache helper for {@link HostMolangScript} expressions parsed during
 * model import, GUI expression generation, and extra {@code functions/*.molang} loading.
 */
public final class HostMolangRegistry {

    private static final Pattern LINE_COMMENT = Pattern.compile("//.*$", Pattern.MULTILINE);
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern ROAMING_ASSIGNMENT = Pattern.compile("(?:^|[;\\s])(?:v|variable)\\.roaming\\.[a-zA-Z0-9_]+\\s*=");

    private HostMolangRegistry() {
    }

    public static HostMolangScript parseExpression(String source) {
        return HostMolangScript.compile(source == null ? "" : source);
    }

    public static HostMolangScript parseExpression(String source, boolean stripComments) {
        if (source == null || source.isBlank()) {
            return HostMolangScript.constant(0.0);
        }
        String prepared = stripComments ? stripComments(source) : source;
        return HostMolangScript.compile(prepared);
    }

    public static List<HostMolangScript> parseList(List<String> lines, boolean mergeMultilineExpr) {
        List<HostMolangScript> out = new ArrayList<>();
        if (lines == null || lines.isEmpty()) {
            return out;
        }
        if (mergeMultilineExpr) {
            out.add(parseExpression(String.join("\n", lines), true));
        } else {
            for (String line : lines) {
                out.add(parseExpression(line, false));
            }
        }
        return out;
    }

    public static String stripComments(String source) {
        if (source == null || source.isEmpty()) {
            return "";
        }
        String noBlock = BLOCK_COMMENT.matcher(source).replaceAll(" ");
        return LINE_COMMENT.matcher(noBlock).replaceAll("").trim();
    }

    public static void logParseWarning(String owner, String source, HostMolangScript script) {
        if (!script.parsedSuccessfully()) {
            YesSteveModel.LOGGER.warn("[SM-MOLANG] Failed to compile Molang expression for {}: '{}' -> {}",
                    owner,
                    source.length() > 96 ? source.substring(0, 96) + "..." : source,
                    script.failure() != null ? script.failure() : "unknown");
        }
    }

    public static boolean isRoamingVariableAssignment(String source) {
        if (source == null || source.isEmpty()) {
            return false;
        }
        return ROAMING_ASSIGNMENT.matcher(source).find();
    }

    public static String normalizeKey(String key) {
        return key == null ? "" : key.toLowerCase(Locale.ROOT);
    }
}
