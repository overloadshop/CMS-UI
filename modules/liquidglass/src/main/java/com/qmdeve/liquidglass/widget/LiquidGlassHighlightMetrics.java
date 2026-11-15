package com.qmdeve.liquidglass.widget;

/** Shared physical highlight widths for native glass, fusion and fallback surfaces. */
public final class LiquidGlassHighlightMetrics {
    private LiquidGlassHighlightMetrics() {}

    public static float sanitizeScale(float scale) {
        return Float.isFinite(scale) ? Math.max(0.1f, Math.min(1f, scale)) : 1f;
    }

    public static float sanitizeMaxWidth(float maxDp) {
        return Float.isNaN(maxDp) ? Float.POSITIVE_INFINITY : Math.max(0f, maxDp);
    }

    public static float widthPx(float width, float height, float density,
                                float scale, float maxDp, float transformScale) {
        if (!Float.isFinite(width) || !Float.isFinite(height)
                || !Float.isFinite(density) || density <= 0f) return 0f;
        float shortSide = Math.max(0f, Math.min(width, height));
        float shortDp = shortSide / density;
        // Smoothly saturate rather than growing proportionally with the surface.
        float adaptiveDp = 0.62f + 0.18f * (shortDp / (shortDp + 40f));
        float screenScale = Float.isFinite(transformScale)
                ? Math.max(1f, transformScale) : 1f;
        return Math.min(shortSide * 0.125f,
                Math.min(adaptiveDp * sanitizeScale(scale), sanitizeMaxWidth(maxDp))
                        * density / screenScale);
    }

    /** Largest affine stretch: rotations leave width unchanged; shear cannot widen it. */
    public static float maximumStretch(float a, float b, float c, float d) {
        double sum = (double) a*a + (double) b*b + (double) c*c + (double) d*d;
        double determinant = (double) a*d - (double) b*c;
        double stretch = Math.sqrt((sum + Math.sqrt(Math.max(0.0,
                sum*sum - 4.0*determinant*determinant))) * 0.5);
        return Double.isFinite(stretch) ? (float) Math.max(1.0, stretch) : 1f;
    }
}
