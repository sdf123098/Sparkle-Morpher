package com.micaftic.morpher.client.model;

/** Read-only UI and runtime capabilities derived from one model assembly. */
public record ModelPresentationCapabilities(
        boolean runtimeResident,
        boolean metadataAvailable,
        boolean textureLabelsAvailable,
        boolean cardPreviewAvailable,
        boolean detailPreviewAvailable,
        boolean playerActionControlsAvailable,
        boolean gpuTrimAvailable,
        boolean nativeTrimAvailable) {
}
