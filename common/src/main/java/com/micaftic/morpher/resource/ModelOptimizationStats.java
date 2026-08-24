package com.micaftic.morpher.resource;

public final class ModelOptimizationStats {
    public int quadsBefore;
    public int quadsAfter;
    public int prunedInvisibleFaces;
    public int prunedInternalFaces;
    public int internalFaceCandidatePairs;
    public int translucentFaces;
    public int zeroThicknessFaces;
    public int negativeSizedFaces;
    public int bones;
    public int cubes;
    public int textures;
    public int opaqueFaces;
    public int cutoutFaces;
    public int glowFaces;
    public int partMaskAllQuads;
    public int partMaskLeftArmQuads;
    public int partMaskRightArmQuads;
    public long estimatedBakedBytes;
    public long estimatedGpuMeshBytes;
    public long importBakeMillis;

    public String toLogString() {
        return "quadsBefore=" + quadsBefore
                + " quadsAfter=" + quadsAfter
                + " prunedInvisibleFaces=" + prunedInvisibleFaces
                + " prunedInternalFaces=" + prunedInternalFaces
                + " internalFaceCandidatePairs=" + internalFaceCandidatePairs
                + " translucentFaces=" + translucentFaces
                + " zeroThicknessFaces=" + zeroThicknessFaces
                + " negativeSizedFaces=" + negativeSizedFaces
                + " bones=" + bones
                + " cubes=" + cubes
                + " textures=" + textures
                + " opaqueFaces=" + opaqueFaces
                + " cutoutFaces=" + cutoutFaces
                + " glowFaces=" + glowFaces
                + " partMaskAllQuads=" + partMaskAllQuads
                + " partMaskLeftArmQuads=" + partMaskLeftArmQuads
                + " partMaskRightArmQuads=" + partMaskRightArmQuads
                + " estimatedBakedBytes=" + estimatedBakedBytes
                + " estimatedGpuMeshBytes=" + estimatedGpuMeshBytes
                + " importBakeMillis=" + importBakeMillis;
    }
}
