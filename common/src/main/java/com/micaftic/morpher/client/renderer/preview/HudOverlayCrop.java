package com.micaftic.morpher.client.renderer.preview;

/** Places a cropped FBO without moving the classic HUD's one-by-two logical viewport. */
public final class HudOverlayCrop {
    public record Origin(float x, float y) { }

    private HudOverlayCrop() { }

    public static Origin compositeOrigin(double x, double y, float scale,
                                         float modelMaxX, float modelMaxY, int padding) {
        // The crop renderer places the model origin at padding + modelMax * scale.
        // The configured HUD rectangle places that same origin at its bottom centre.
        return new Origin((float) (x + (0.5f - modelMaxX) * scale - padding),
                (float) (y + (2.0f - modelMaxY) * scale - padding));
    }
}
