package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.core.api.network.state.CloudErrorCode;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Content-addressed client cache with verify-before-publish and atomic replacement. */
public final class CloudAssetCache {

    public CompletableFuture<Path> downloadAndStore(CloudAssetClient client, CloudAssetRef ref, Path cacheRoot) {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(cacheRoot, "cacheRoot");
        return client.download(ref, null, null).thenApply(response -> {
            if (response.statusCode() != 200) {
                throw new CloudHttpException(response.statusCode(), response.statusCode() == 404 ? CloudErrorCode.ASSET_NOT_FOUND : CloudErrorCode.INTERNAL, "Cloud asset download did not return the complete object");
            }
            try {
                return writeVerified(cacheRoot, ref, response.body());
            } catch (IOException e) {
                throw new CloudHttpException(500, CloudErrorCode.INTERNAL, "Cloud asset cache write failed: " + e.getMessage());
            }
        });
    }

    static Path writeVerified(Path cacheRoot, CloudAssetRef ref, byte[] bytes) throws IOException {
        Objects.requireNonNull(cacheRoot, "cacheRoot");
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(bytes, "bytes");
        String actualSha = sha256(bytes);
        if (!actualSha.equals(ref.rawSha256())) {
            throw new CloudHttpException(200, CloudErrorCode.ASSET_HASH_MISMATCH, "Cloud asset SHA-256 does not match its trusted reference");
        }
        Path root = cacheRoot.toAbsolutePath().normalize();
        Files.createDirectories(root);
        Path target = root.resolve(ref.assetId() + "-r" + ref.revision() + "-" + actualSha + ".bin");
        if (Files.isRegularFile(target)) return target;
        Path temp = root.resolve("." + target.getFileName() + ".part-" + UUID.randomUUID());
        try {
            Files.write(temp, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}
