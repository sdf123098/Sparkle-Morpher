package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.model.ServerModelManager;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class ModelPanelFileActions {
    private static final Pattern CATEGORY_PATTERN = Pattern.compile("[a-z0-9_./-]+");

    private ModelPanelFileActions() {
    }

    public static boolean canWriteServerModelDirectory() {
        return true;
    }

    public static Component deleteModels(Collection<String> modelIds) {
        if (!canWriteServerModelDirectory()) {
            return Component.translatable("gui.sparkle_morpher.model_select.error.read_only");
        }
        List<String> removed = new ArrayList<>();
        for (String modelId : modelIds) {
            Optional<Path> source = findManageableSource(modelId);
            if (source.isEmpty()) {
                continue;
            }
            try {
                deletePath(source.get());
                removed.add(modelId);
            } catch (IOException e) {
                return Component.translatable("gui.sparkle_morpher.model_select.error.file", e.getMessage());
            }
        }
        if (!removed.isEmpty()) {
            ClientModelManager.removeLocalModels(removed);
        }
        ClientModelManager.reloadLocalModels(null);
        return Component.translatable("gui.sparkle_morpher.model_select.deleted", removed.size(), modelIds.size());
    }

    public static Component moveModels(Collection<String> modelIds, String category) {
        if (!canWriteServerModelDirectory()) {
            return Component.translatable("gui.sparkle_morpher.model_select.error.read_only");
        }
        String safeCategory = normalizeCategory(category);
        if (safeCategory.isBlank()) {
            return Component.translatable("gui.sparkle_morpher.model_select.error.category");
        }
        Path targetDir = ServerModelManager.CUSTOM.resolve(safeCategory).normalize();
        if (!isInside(ServerModelManager.CUSTOM, targetDir)) {
            return Component.translatable("gui.sparkle_morpher.model_select.error.category");
        }
        List<String> moved = new ArrayList<>();
        try {
            Files.createDirectories(targetDir);
            for (String modelId : modelIds) {
                Optional<Path> source = findManageableSource(modelId);
                if (source.isEmpty()) {
                    continue;
                }
                Path sourcePath = source.get();
                Path target = uniqueTarget(targetDir.resolve(sourcePath.getFileName()).normalize());
                if (isInside(ServerModelManager.CUSTOM, target)) {
                    Files.move(sourcePath, target, StandardCopyOption.REPLACE_EXISTING);
                    moved.add(modelId);
                }
            }
        } catch (IOException e) {
            return Component.translatable("gui.sparkle_morpher.model_select.error.file", e.getMessage());
        }
        if (!moved.isEmpty()) {
            ClientModelManager.removeLocalModels(moved);
        }
        ClientModelManager.reloadLocalModels(null);
        return Component.translatable("gui.sparkle_morpher.model_select.moved", moved.size(), safeCategory);
    }

    public static Component createCategory(String category) {
        if (!canWriteServerModelDirectory()) {
            return Component.translatable("gui.sparkle_morpher.model_select.error.read_only");
        }
        String safeCategory = normalizeCategory(category);
        if (safeCategory.isBlank()) {
            return Component.translatable("gui.sparkle_morpher.model_select.error.category");
        }
        Path dir = ServerModelManager.CUSTOM.resolve(safeCategory).normalize();
        if (!isInside(ServerModelManager.CUSTOM, dir)) {
            return Component.translatable("gui.sparkle_morpher.model_select.error.category");
        }
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            return Component.translatable("gui.sparkle_morpher.model_select.error.file", e.getMessage());
        }
        ClientModelManager.reloadLocalModels(null);
        return Component.translatable("gui.sparkle_morpher.model_select.category_created", safeCategory);
    }

    public static Component renameCategory(String oldCategory, String newCategory) {
        if (!canWriteServerModelDirectory()) {
            return Component.translatable("gui.sparkle_morpher.model_select.error.read_only");
        }
        String oldSafe = normalizeCategory(oldCategory);
        String newSafe = normalizeCategory(newCategory);
        if (oldSafe.isBlank() || newSafe.isBlank() || oldSafe.equals(newSafe)) {
            return Component.translatable("gui.sparkle_morpher.model_select.error.category");
        }
        List<String> affectedModelIds = findLoadedModelsInCategory(oldSafe);
        int moved = 0;
        try {
            for (Path root : List.of(ServerModelManager.CUSTOM, ServerModelManager.AUTH)) {
                Path oldDir = root.resolve(oldSafe).normalize();
                Path newDir = root.resolve(newSafe).normalize();
                if (!isInside(root, oldDir) || !isInside(root, newDir) || !Files.exists(oldDir)) {
                    continue;
                }
                Files.createDirectories(newDir.getParent());
                mergeCategoryDirectory(root, oldDir, newDir);
                moved++;
            }
        } catch (IOException e) {
            return Component.translatable("gui.sparkle_morpher.model_select.error.file", e.getMessage());
        }
        if (!affectedModelIds.isEmpty()) {
            ClientModelManager.removeLocalModels(affectedModelIds);
        }
        ClientModelManager.reloadLocalModels(null);
        return Component.translatable("gui.sparkle_morpher.model_select.category_renamed", oldSafe, newSafe, moved);
    }

    public static Component deleteCategory(String category, boolean deleteModels) {
        if (!canWriteServerModelDirectory()) {
            return Component.translatable("gui.sparkle_morpher.model_select.error.read_only");
        }
        String safeCategory = normalizeCategory(category);
        if (safeCategory.isBlank()) {
            return Component.translatable("gui.sparkle_morpher.model_select.error.category");
        }
        List<String> affectedModelIds = findLoadedModelsInCategory(safeCategory);
        int deleted = 0;
        try {
            for (Path root : List.of(ServerModelManager.CUSTOM, ServerModelManager.AUTH)) {
                Path dir = root.resolve(safeCategory).normalize();
                if (!isInside(root, dir) || !Files.exists(dir)) {
                    continue;
                }
                if (deleteModels) {
                    deletePath(dir);
                } else {
                    moveCategoryContentsToParent(root, dir);
                }
                deleted++;
            }
        } catch (IOException e) {
            return Component.translatable("gui.sparkle_morpher.model_select.error.file", e.getMessage());
        }
        if (!affectedModelIds.isEmpty()) {
            ClientModelManager.removeLocalModels(affectedModelIds);
        }
        ClientModelManager.reloadLocalModels(null);
        return Component.translatable(deleteModels ? "gui.sparkle_morpher.model_select.category_deleted" : "gui.sparkle_morpher.model_select.category_deleted_keep_models", safeCategory, deleted);
    }

    public static List<String> listCategories() {
        List<String> categories = new ArrayList<>();
        collectCategories(ServerModelManager.CUSTOM, categories);
        collectCategories(ServerModelManager.AUTH, categories);
        return categories.stream().distinct().sorted().toList();
    }

    public static String normalizeCategory(String category) {
        if (category == null) {
            return "";
        }
        String normalized = category.trim().replace('\\', '/').toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_./-]+", "_")
                .replaceAll("/+", "/")
                .replaceAll("^/+", "")
                .replaceAll("/+$", "");
        if (normalized.isBlank() || !CATEGORY_PATTERN.matcher(normalized).matches()) {
            return "";
        }
        return normalized;
    }

    private static Optional<Path> findManageableSource(String modelId) {
        for (Path root : List.of(ServerModelManager.CUSTOM, ServerModelManager.AUTH)) {
            Optional<Path> found = findSource(root, modelId);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private static Optional<Path> findSource(Path root, String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return Optional.empty();
        }
        for (String suffix : List.of(".ysm", ".zip", ".bbmodel", "")) {
            Path candidate = root.resolve(modelId + suffix).normalize();
            if (isInside(root, candidate) && Files.exists(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static void collectCategories(Path root, List<String> categories) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(path -> !path.equals(root) && Files.isDirectory(path))
                    .map(path -> root.relativize(path).toString().replace('\\', '/'))
                    .filter(path -> !path.isBlank())
                    .forEach(categories::add);
        } catch (IOException ignored) {
        }
    }

    private static void deletePath(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (Stream<Path> stream = Files.walk(path)) {
                List<Path> paths = stream.sorted(Comparator.reverseOrder()).toList();
                for (Path child : paths) {
                    Files.deleteIfExists(child);
                }
            }
        } else {
            Files.deleteIfExists(path);
        }
    }

    private static void moveCategoryContentsToParent(Path root, Path dir) throws IOException {
        Path parent = dir.getParent();
        if (parent == null || !isInside(root, parent)) {
            parent = root;
        }
        moveDirectoryContents(root, dir, parent);
        Files.deleteIfExists(dir);
    }

    private static void mergeCategoryDirectory(Path root, Path oldDir, Path newDir) throws IOException {
        if (!Files.exists(newDir)) {
            Files.move(oldDir, newDir);
            return;
        }
        if (!Files.isDirectory(newDir)) {
            Files.move(oldDir, uniqueTarget(newDir), StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        moveDirectoryContents(root, oldDir, newDir);
        Files.deleteIfExists(oldDir);
    }

    private static void moveDirectoryContents(Path root, Path dir, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path child : stream.toList()) {
                Path target = uniqueTarget(targetDir.resolve(child.getFileName()).normalize());
                if (isInside(root, target)) {
                    Files.move(child, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static List<String> findLoadedModelsInCategory(String category) {
        String prefix = category.endsWith("/") ? category : category + "/";
        return ClientModelManager.getModelAssemblyMap().keySet().stream()
                .filter(modelId -> modelId.startsWith(prefix))
                .toList();
    }

    private static Path uniqueTarget(Path target) {
        if (!Files.exists(target)) {
            return target;
        }
        String fileName = target.getFileName().toString();
        String stem = fileName;
        String extension = "";
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            stem = fileName.substring(0, dot);
            extension = fileName.substring(dot);
        }
        for (int i = 1; i < 1000; i++) {
            Path candidate = target.resolveSibling(stem + "_" + i + extension);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        return target;
    }

    private static boolean isInside(Path root, Path path) {
        Path absoluteRoot = root.toAbsolutePath().normalize();
        Path absolutePath = path.toAbsolutePath().normalize();
        return absolutePath.startsWith(absoluteRoot);
    }

}
