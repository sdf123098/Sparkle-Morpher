package com.micaftic.morpher.client.gui.resource.station;

import com.micaftic.morpher.resource.bundle.TextureDecoder;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.awt.image.BufferedImage;
import java.util.*;

/** A bounded, offscreen rest-pose thumbnail. No game renderer, animation, scripts or model registration. */
public final class StaticModelThumbnail {
    private static final int WIDTH = 256, HEIGHT = 384, MAX_FACES = 30_000;
    private static final long MAX_PIXEL_VISITS = 24_000_000;
    private record Vertex(float x, float y, float z, float u, float v) {}

    private StaticModelThumbnail() {}

    public static byte[] render(RawYsmModel raw) {
        if (raw == null || raw.mainEntity == null || raw.mainEntity.mainModel == null) return null;
        var geometry = raw.mainEntity.mainModel;
        if (geometry.bones.size() > 4096 || raw.mainEntity.textures.isEmpty()) return null;
        var texture = raw.mainEntity.textures.get(raw.properties.defaultTexture);
        if (texture == null) texture = raw.mainEntity.textures.values().iterator().next();
        if (texture.width <= 0 || texture.height <= 0 || (long) texture.width * texture.height > 4_194_304) return null;
        BufferedImage skin = EmbeddedModelPreview.decodeImage(texture.data, texture.imageFormat, texture.width, texture.height);
        if (skin == null) return null;
        try {
            Map<String, RawYsmModel.RawBone> bones = new HashMap<>();
            geometry.bones.forEach(bone -> bones.put(bone.name, bone));
            Map<String, Matrix4f> transforms = new HashMap<>();
            List<Vertex[]> faces = new ArrayList<>();
            float minX = Float.POSITIVE_INFINITY, minY = minX, maxX = -minX, maxY = -minX;
            Matrix4f view = new Matrix4f().rotateX(.06f).rotateY(.22f);
            for (var bone : geometry.bones) {
                Matrix4f transform = new Matrix4f(view).mul(transform(bone, bones, transforms, new HashSet<>()));
                for (var cube : bone.cubes) for (var face : cube.faces) {
                    if (faces.size() >= MAX_FACES) return null;
                    Vertex[] vertices = new Vertex[4];
                    for (int i = 0; i < 4; i++) {
                        float[] p = face.positions[i];
                        var v = transform.transformPosition(new Vector3f(p[0], p[1], p[2]));
                        if (!Float.isFinite(v.x) || !Float.isFinite(v.y) || !Float.isFinite(v.z)
                                || !Float.isFinite(face.u[i]) || !Float.isFinite(face.v[i])) return null;
                        vertices[i] = new Vertex(-v.x, -v.y, -v.z, face.u[i], face.v[i]);
                        minX = Math.min(minX, -v.x); maxX = Math.max(maxX, -v.x);
                        minY = Math.min(minY, -v.y); maxY = Math.max(maxY, -v.y);
                    }
                    faces.add(vertices);
                }
            }
            if (faces.isEmpty() || maxX - minX < 1e-6f || maxY - minY < 1e-6f) return null;
            float scale = Math.min((WIDTH - 24) / (maxX - minX), (HEIGHT - 24) / (maxY - minY));
            float centerX = (minX + maxX) / 2, centerY = (minY + maxY) / 2;
            int[] pixels = new int[WIDTH * HEIGHT];
            float[] depths = new float[pixels.length]; Arrays.fill(depths, Float.NEGATIVE_INFINITY);
            long visits = 0;
            for (Vertex[] face : faces) {
                Vertex[] screen = new Vertex[4];
                for (int i = 0; i < 4; i++) {
                    var v = face[i]; screen[i] = new Vertex((v.x - centerX) * scale + WIDTH / 2f,
                            (v.y - centerY) * scale + HEIGHT / 2f, v.z, v.u, v.v);
                }
                visits += triangle(screen[0], screen[1], screen[2], skin, pixels, depths);
                visits += triangle(screen[0], screen[2], screen[3], skin, pixels, depths);
                if (visits > MAX_PIXEL_VISITS) return null;
            }
            if (Arrays.stream(pixels).noneMatch(pixel -> (pixel >>> 24) >= 32)) return null;
            BufferedImage result = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
            result.setRGB(0, 0, WIDTH, HEIGHT, pixels, 0, WIDTH);
            return TextureDecoder.encodeToPng(result, null);
        } finally { skin.flush(); }
    }

    private static Matrix4f transform(RawYsmModel.RawBone bone, Map<String, RawYsmModel.RawBone> bones,
                                      Map<String, Matrix4f> cached, Set<String> visiting) {
        Matrix4f known = cached.get(bone.name);
        if (known != null) return known;
        if (visiting.size() >= 64 || !visiting.add(bone.name)) throw new IllegalArgumentException("Cyclic preview bones");
        var parent = bones.get(bone.parentName);
        Matrix4f matrix = parent == null ? new Matrix4f() : new Matrix4f(transform(parent, bones, cached, visiting));
        float[] pivot = bone.pivot, rotation = bone.rotation;
        matrix.translate(pivot[0] / 16, pivot[1] / 16, pivot[2] / 16)
                .rotateZ(rotation[2]).rotateY(rotation[1]).rotateX(rotation[0])
                .translate(-pivot[0] / 16, -pivot[1] / 16, -pivot[2] / 16);
        visiting.remove(bone.name); cached.put(bone.name, matrix);
        return matrix;
    }

    private static long triangle(Vertex a, Vertex b, Vertex c, BufferedImage skin, int[] pixels, float[] depths) {
        float area = edge(a, b, c.x, c.y);
        if (Math.abs(area) < 1e-6f) return 0;
        int left = Math.max(0, (int) Math.floor(Math.min(a.x, Math.min(b.x, c.x))));
        int right = Math.min(WIDTH - 1, (int) Math.ceil(Math.max(a.x, Math.max(b.x, c.x))));
        int top = Math.max(0, (int) Math.floor(Math.min(a.y, Math.min(b.y, c.y))));
        int bottom = Math.min(HEIGHT - 1, (int) Math.ceil(Math.max(a.y, Math.max(b.y, c.y))));
        for (int y = top; y <= bottom; y++) for (int x = left; x <= right; x++) {
            float wa = edge(b, c, x + .5f, y + .5f) / area;
            float wb = edge(c, a, x + .5f, y + .5f) / area, wc = 1 - wa - wb;
            if (wa < -1e-5f || wb < -1e-5f || wc < -1e-5f) continue;
            int index = y * WIDTH + x;
            float depth = wa * a.z + wb * b.z + wc * c.z;
            if (depth < depths[index]) continue;
            float u = wa * a.u + wb * b.u + wc * c.u, v = wa * a.v + wb * b.v + wc * c.v;
            int color = skin.getRGB(Math.max(0, Math.min(skin.getWidth() - 1, (int) (u * skin.getWidth()))),
                    Math.max(0, Math.min(skin.getHeight() - 1, (int) (v * skin.getHeight()))));
            if ((color >>> 24) < 32) continue;
            pixels[index] = color; depths[index] = depth;
        }
        return Math.max(0L, (long) (right - left + 1) * (bottom - top + 1));
    }

    private static float edge(Vertex a, Vertex b, float x, float y) {
        return (b.x - a.x) * (y - a.y) - (b.y - a.y) * (x - a.x);
    }
}
