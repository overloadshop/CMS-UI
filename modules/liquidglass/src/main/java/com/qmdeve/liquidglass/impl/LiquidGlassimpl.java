/*
 * MIT License
 *
 * Copyright (c) 2025-2026 Donny Yale
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * ===========================================
 * Project: AndroidLiquidGlassView
 * Created Date: 2025-11-01
 * Author: Donny Yale
 * GitHub: https://github.com/QmDeve/AndroidLiquidGlassView
 * WebSite: https://liquidglass.qmdeve.com
 * ===========================================
 */

package com.qmdeve.liquidglass.impl;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.HardwareRenderer;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.RequiresApi;

import com.qmdeve.liquidglass.R;
import com.qmdeve.liquidglass.Config;

import org.intellij.lang.annotations.Language;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

@RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
public final class LiquidGlassimpl implements Impl {

    private final View host, target;
    private final RenderNode node;
    private final RenderNode sampleNode;
    private RenderEffect cachedBlurEffect;
    private HardwareRenderer sampleRenderer;
    private ImageReader sampleImageReader;
    private int sampleRenderWidth = -1;
    private int sampleRenderHeight = -1;
    private final int[] tp = new int[2];
    private final int[] hp = new int[2];
    private final RuntimeShader liquidShader;
    private final android.graphics.Path underlayClipPath = new android.graphics.Path();
    private float lastCornerRadius, lastEccentricFactor, lastRefractionHeight, lastRefractionAmount,
            lastContrast, lastWhitePoint, lastChromaMultiplier, lastSigma,
            lastChromaticAberration, lastDepthEffect, lastBlurLevel,
            lastTintRed, lastTintGreen, lastTintBlue, lastTintAlpha;
    private float lastEdgeOnly = Float.NaN;
    private float lastFluidWidth = Float.NaN, lastFluidHeight, lastFluidStrain, lastFluidShear;
    private int lastFluidRestingTint;
    private float lastFluidOpticalProgress = Float.NaN;

    private boolean needsUpdate = true;
    private int renderWidth = -1;
    private int renderHeight = -1;
    private long lastBlurUpdateTime = 0;
    private final Config config;

    // ═══ CLEAR Glass Adaptive Protocol — 亮度采样 ═══
    private volatile float contentBrightness = 0.5f;
    private long lastSampleTime = 0;
    private volatile long lastSampleUptimeMs = 0L;
    private volatile String lastSampleMode = "none";
    private long sampleFailCount = 0;
    private static final int SAMPLE_INTERVAL_MS = 16;
    private static final String TAG = "LiquidGlass";
    private static final int SAMPLE_WIDTH = 120;   // 采样位图最大宽度
    private static final int SAMPLE_GRID = 10;     // 采样网格数

    public LiquidGlassimpl(View host, View target, Config config) {
        this.host = host;
        this.target = target;
        this.config = config;
        this.node = new RenderNode("AndroidLiquidGlassView");
        this.sampleNode = new RenderNode("AndroidLiquidGlassSampler");
        this.liquidShader = loadAgsl(target.getResources(), R.raw.liquidglass_effect);

        lastCornerRadius = Float.NaN;
        lastEccentricFactor = Float.NaN;
        lastRefractionHeight = Float.NaN;
        lastRefractionAmount = Float.NaN;
        lastContrast = Float.NaN;
        lastWhitePoint = Float.NaN;
        lastChromaMultiplier = Float.NaN;
        lastSigma = Float.NaN;
        lastBlurLevel = Float.NaN;
        lastChromaticAberration = Float.NaN;
        lastDepthEffect = Float.NaN;
        lastTintRed = Float.NaN;
        lastTintGreen = Float.NaN;
        lastTintBlue = Float.NaN;
        lastTintAlpha = Float.NaN;

    }

    @Override
    public void onSizeChanged(int w, int h) {
        needsUpdate = true;
        onPreDraw();
    }

    @Override
    public void onPreDraw() {
        int width = host.getWidth();
        int height = host.getHeight();
        if (width <= 0 || height <= 0 || target.getWidth() <= 0 || target.getHeight() <= 0) {
            needsUpdate = true;
            return;
        }
        if (renderWidth != width || renderHeight != height) {
            renderWidth = width;
            renderHeight = height;
            node.setPosition(0, 0, width, height);
            needsUpdate = true;
        }
        record();

        float cornerRadius = config.CORNER_RADIUS_PX;
        float eccentricFactor = config.ECCENTRIC_FACTOR;
        float refractionHeight = config.REFRACTION_HEIGHT;
        float refractionAmount = config.REFRACTION_OFFSET;
        float contrast = config.CONTRAST;
        float whitePoint = config.WHITE_POINT;
        float chromaMultiplier = config.CHROMA_MULTIPLIER;
        float blurLevel = config.BLUR_RADIUS;
        float chromaticAberration = config.DISPERSION;
        float depthEffect = config.DEPTH_EFFECT;
        float tintRed = config.TINT_COLOR_RED;
        float tintGreen = config.TINT_COLOR_GREEN;
        float tintBlue = config.TINT_COLOR_BLUE;
        float tintAlpha = config.TINT_ALPHA;

        boolean paramsChanged =
                lastCornerRadius != cornerRadius ||
                        lastEccentricFactor != eccentricFactor ||
                        lastRefractionHeight != refractionHeight ||
                        lastRefractionAmount != refractionAmount ||
                        lastContrast != contrast ||
                        lastWhitePoint != whitePoint ||
                        lastChromaMultiplier != chromaMultiplier ||
                        lastBlurLevel != blurLevel ||
                        lastChromaticAberration != chromaticAberration ||
                        lastDepthEffect != depthEffect ||
                        lastTintRed != tintRed ||
                        lastTintGreen != tintGreen ||
                        lastTintBlue != tintBlue ||
                        lastTintAlpha != tintAlpha ||
                        lastEdgeOnly != config.EDGE_ONLY ||
                        lastFluidWidth != config.FLUID_WIDTH ||
                        lastFluidHeight != config.FLUID_HEIGHT ||
                        lastFluidStrain != config.FLUID_STRAIN ||
                        lastFluidShear != config.FLUID_SHEAR ||
                        lastFluidRestingTint != config.FLUID_RESTING_TINT ||
                        lastFluidOpticalProgress != config.FLUID_OPTICAL_PROGRESS ||
                        needsUpdate;

        if (paramsChanged) {
            lastCornerRadius = cornerRadius;
            lastEccentricFactor = eccentricFactor;
            lastRefractionHeight = refractionHeight;
            lastRefractionAmount = refractionAmount;
            lastContrast = contrast;
            lastWhitePoint = whitePoint;
            lastChromaMultiplier = chromaMultiplier;
            lastBlurLevel = blurLevel;
            lastChromaticAberration = chromaticAberration;
            lastDepthEffect = depthEffect;
            lastTintRed = tintRed;
            lastTintGreen = tintGreen;
            lastTintBlue = tintBlue;
            lastTintAlpha = tintAlpha;
            lastEdgeOnly = config.EDGE_ONLY;
            lastFluidWidth = config.FLUID_WIDTH;
            lastFluidHeight = config.FLUID_HEIGHT;
            lastFluidStrain = config.FLUID_STRAIN;
            lastFluidShear = config.FLUID_SHEAR;
            lastFluidRestingTint = config.FLUID_RESTING_TINT;
            lastFluidOpticalProgress = config.FLUID_OPTICAL_PROGRESS;
            needsUpdate = false;
            applyRenderEffect();
        }
    }

    private boolean recording = false;

    private void record() {
        if (recording) return;
        int w = target.getWidth(), h = target.getHeight();
        if (w == 0 || h == 0) return;

        recording = true;
        Canvas rec = node.beginRecording(host.getWidth(), host.getHeight());
        try {
            drawSources(rec);
        } catch (Exception e) {
            // View 状态异常时 draw 可能失败，静默处理避免崩溃
        } finally {
            node.endRecording();
            recording = false;
        }

        // 每 SAMPLE_INTERVAL_MS 通过软件 Canvas 采样内容亮度
        long now = System.currentTimeMillis();
        if (config.SAMPLE_BRIGHTNESS && now - lastSampleTime >= SAMPLE_INTERVAL_MS) {
            lastSampleTime = now;
            if (!sampleHardwareReadback()) {
                sampleSoftwareDraw();
            }
        }
    }

    /** Optical capture and brightness fallback use exactly the same screen-aligned composition. */
    private void drawSources(Canvas canvas) {
        int save = canvas.save();
        target.getLocationOnScreen(tp);
        host.getLocationOnScreen(hp);
        android.graphics.PointF screenPosition = config.SAMPLING_SCREEN_POSITION;
        float hostX = screenPosition != null ? screenPosition.x : hp[0];
        float hostY = screenPosition != null ? screenPosition.y : hp[1];
        canvas.translate(tp[0] - hostX, tp[1] - hostY);
        target.draw(canvas);
        canvas.restoreToCount(save);
        View underlayView = config.SAMPLING_UNDERLAY;
        if (underlayView != null && underlayView.isAttachedToWindow() && underlayView.isShown()) {
            save = canvas.save();
            underlayView.getLocationOnScreen(tp);
            canvas.translate(tp[0] - hostX, tp[1] - hostY);
            // View.draw does not impose its own bounds. Clip the sampled lower capsule,
            // otherwise its background and optical children can overwrite the page outside it.
            underlayClipPath.reset();
            float radius = underlayView.getHeight() * 0.5f;
            underlayClipPath.addRoundRect(0f, 0f, underlayView.getWidth(), underlayView.getHeight(),
                    radius, radius, android.graphics.Path.Direction.CW);
            canvas.clipPath(underlayClipPath);
            underlayView.draw(canvas);
            canvas.restoreToCount(save);
        }
        View foreground = config.SAMPLING_FOREGROUND;
        // Capture the labels at full strength: once the lens lifts above them this
        // copy is the only one that survives, so fading it here would remove the
        // icons from inside the glass while the originals stay covered.
        float foregroundAmount = 1f;
        if (foreground != null && foregroundAmount > 0f && foreground.isAttachedToWindow()
                && foreground.isShown()) {
            save = canvas.save();
            foreground.getLocationOnScreen(tp);
            canvas.translate(tp[0] - hostX, tp[1] - hostY);
            canvas.clipRect(0f, 0f, foreground.getWidth(), foreground.getHeight());
            int layer = canvas.saveLayerAlpha(0f, 0f, foreground.getWidth(), foreground.getHeight(),
                    Math.round(foregroundAmount * 255f));
            foreground.draw(canvas);
            canvas.restoreToCount(layer);
            canvas.restoreToCount(save);
        }
    }

    private boolean ensureSampleResources(int width, int height) {
        if (width <= 0 || height <= 0) return false;
        if (sampleRenderer != null && sampleImageReader != null
                && sampleRenderWidth == width && sampleRenderHeight == height) {
            return true;
        }
        releaseSampleResources();
        try {
            sampleImageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
            sampleRenderer = new HardwareRenderer();
            sampleRenderer.setSurface(sampleImageReader.getSurface());
            sampleRenderer.setContentRoot(sampleNode);
            sampleRenderWidth = width;
            sampleRenderHeight = height;
            sampleNode.setPosition(0, 0, width, height);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "ensureSampleResources failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            releaseSampleResources();
            return false;
        }
    }

    private void releaseSampleResources() {
        if (sampleRenderer != null) {
            sampleRenderer.destroy();
            sampleRenderer = null;
        }
        if (sampleImageReader != null) {
            sampleImageReader.close();
            sampleImageReader = null;
        }
        sampleRenderWidth = -1;
        sampleRenderHeight = -1;
    }

    private boolean sampleHardwareReadback() {
        int tw = host.getWidth(), th = host.getHeight();
        if (tw <= 0 || th <= 0) return false;
        float scale = (float) SAMPLE_WIDTH / tw;
        int sw = SAMPLE_WIDTH;
        int sh = Math.max(1, Math.round(th * scale));
        if (!ensureSampleResources(sw, sh)) return false;

        try {
            Canvas sampleCanvas = sampleNode.beginRecording(sw, sh);
            sampleCanvas.scale(scale, scale);
            sampleCanvas.drawRenderNode(node);
            sampleNode.endRecording();

            sampleRenderer.createRenderRequest()
                    .setVsyncTime(System.nanoTime())
                    .syncAndDraw();

            Image image = sampleImageReader.acquireLatestImage();
            if (image == null) return false;
            try {
                return sampleImageBrightness(image, scale, sw, sh);
            } finally {
                image.close();
            }
        } catch (Exception e) {
            Log.w(TAG, "sampleHardwareReadback failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            return false;
        }
    }

    private boolean sampleImageBrightness(Image image, float scale, int sw, int sh) {
        Image.Plane[] planes = image.getPlanes();
        if (planes == null || planes.length == 0) return false;
        Image.Plane plane = planes[0];
        java.nio.ByteBuffer buffer = plane.getBuffer();
        int rowStride = plane.getRowStride();
        int pixelStride = plane.getPixelStride();
        if (buffer == null || pixelStride <= 0) return false;

        int glassW = host.getWidth();
        int glassH = host.getHeight();

        // `record()` 已经把 target 内容平移到玻璃左上角对齐，
        // 这里再按页面绝对坐标二次裁切会把采样区域偏移到错误位置。
        int sampleSx = 0;
        int sampleSy = 0;
        int sampleSw = Math.min(sw, Math.round(glassW * scale));
        int sampleSh = Math.min(sh, Math.round(glassH * scale));
        if (sampleSw <= 0 || sampleSh <= 0) {
            sampleSw = sw;
            sampleSh = sh;
        }

        int sampleEx = Math.min(sw, sampleSx + sampleSw);
        int sampleEy = Math.min(sh, sampleSy + sampleSh);
        int stepX = Math.max(1, (sampleEx - sampleSx) / SAMPLE_GRID);
        int stepY = Math.max(1, (sampleEy - sampleSy) / SAMPLE_GRID);

        long totalLum = 0;
        int count = 0;
        for (int y = sampleSy; y < sampleEy; y += stepY) {
            for (int x = sampleSx; x < sampleEx; x += stepX) {
                int offset = y * rowStride + x * pixelStride;
                if (offset < 0 || offset + 2 >= buffer.limit()) continue;
                int r = buffer.get(offset) & 0xFF;
                int g = buffer.get(offset + 1) & 0xFF;
                int b = buffer.get(offset + 2) & 0xFF;
                totalLum += (r * 299L + g * 587L + b * 114L) / 1000L;
                count++;
            }
        }

        if (count > 0) {
            float newBrightness = Math.max(0f, Math.min(1f, (float) totalLum / (count * 255f)));
            contentBrightness = newBrightness;
            lastSampleMode = "hardware";
            lastSampleUptimeMs = System.currentTimeMillis();
            if (sampleFailCount > 0) sampleFailCount = 0;
            return true;
        }
        return false;
    }

    /**
     * 通过 Bitmap + Canvas + target.draw() 纯软件方式采样内容亮度。
     *
     * 不依赖 GPU 管线同步（如 createSnapshot 可能返回空或抛出异常），
     * 使用 Android 最基础的 View.draw(Canvas) 渲染到软件位图后逐像素采样。
     * 采样区域取玻璃所在区域的底部范围，而非整页中间。
     */
    private void sampleSoftwareDraw() {
        int tw = host.getWidth(), th = host.getHeight();
        if (tw <= 0 || th <= 0) return;

        try {
            // 降采样位图：限制宽度为 SAMPLE_WIDTH，保持宽高比
            float scale = (float) SAMPLE_WIDTH / tw;
            int sw = SAMPLE_WIDTH;
            int sh = Math.max(1, Math.round(th * scale));

            Bitmap bitmap = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            // 缩放到采样尺寸后绘制，target.draw() 会正确处理 View 层次
            canvas.scale(scale, scale);
            drawSources(canvas);

            // `record()` 已经把底下内容对齐到玻璃本地坐标，所以这里直接取左上角的玻璃区域。
            int glassW = host.getWidth();
            int glassH = host.getHeight();
            int sampleSx = 0;
            int sampleSy = 0;
            int sampleSw = Math.min(sw, Math.round(glassW * scale));
            int sampleSh = Math.min(sh, Math.round(glassH * scale));
            if (sampleSw <= 0 || sampleSh <= 0) {
                sampleSw = sw;
                sampleSh = sh;
            }

            int sampleEx = Math.min(sw, sampleSx + sampleSw);
            int sampleEy = Math.min(sh, sampleSy + sampleSh);

            // 以 SAMPLE_GRID×SAMPLE_GRID 网格采样
            int stepX = Math.max(1, (sampleEx - sampleSx) / SAMPLE_GRID);
            int stepY = Math.max(1, (sampleEy - sampleSy) / SAMPLE_GRID);

            long totalLum = 0;
            int count = 0;
            for (int y = sampleSy; y < sampleEy; y += stepY) {
                for (int x = sampleSx; x < sampleEx; x += stepX) {
                    int pixel = bitmap.getPixel(x, y);
                    totalLum += (Color.red(pixel) * 299L
                            + Color.green(pixel) * 587L
                            + Color.blue(pixel) * 114L) / 1000L;
                    count++;
                }
            }

            bitmap.recycle();

            if (count > 0) {
                float newBrightness = Math.max(0f, Math.min(1f,
                        (float) totalLum / (count * 255f)));
                contentBrightness = newBrightness;
                lastSampleMode = "software";
                lastSampleUptimeMs = System.currentTimeMillis();
                if (sampleFailCount > 0) sampleFailCount = 0;
            }
        } catch (Exception e) {
            sampleFailCount++;
            if (sampleFailCount == 1 || sampleFailCount % 20 == 0) {
                Log.w(TAG, "sampleSoftwareDraw failed x" + sampleFailCount
                        + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
            }
        }
    }

    /** 获取最近一次采样的亮度值 (0.0 ~ 1.0) */
    public float getContentBrightness() {
        return contentBrightness;
    }

    @Override
    public String getLastSampleMode() {
        return lastSampleMode;
    }

    @Override
    public long getLastSampleUptimeMs() {
        return lastSampleUptimeMs;
    }

    @Override
    public void draw(Canvas canvas) {
        if (!canvas.isHardwareAccelerated()) return;
        canvas.drawRenderNode(node);
    }

    private void applyRenderEffect() {
        int width = host.getWidth();
        int height = host.getHeight();
        if (width == 0 || height == 0) return;

        float cornerRadiusPx = config.CORNER_RADIUS_PX;
        float refractionHeight = config.REFRACTION_HEIGHT;
        float refractionAmount = config.REFRACTION_OFFSET;
        float contrast = config.CONTRAST;
        float whitePoint = config.WHITE_POINT;
        float chromaMultiplier = config.CHROMA_MULTIPLIER;
        float blurLevel = Math.max(0f, config.BLUR_RADIUS);
        float chromaticAberration = config.DISPERSION;
        float depthEffect = config.DEPTH_EFFECT;
        float tintRed = config.TINT_COLOR_RED;
        float tintGreen = config.TINT_COLOR_GREEN;
        float tintBlue = config.TINT_COLOR_BLUE;
        float tintAlpha = config.TINT_ALPHA;
        float[] size = new float[]{width, height};
        float[] offset = new float[]{0f, 0f};
        float[] cornerRadii = new float[]{
                cornerRadiusPx, cornerRadiusPx, cornerRadiusPx, cornerRadiusPx
        };

        RenderEffect contentEffect = null;
        if (blurLevel > 0.01f) {
            long now = System.currentTimeMillis();
            if (cachedBlurEffect == null || Math.abs(blurLevel - lastSigma) > 0.3f || now - lastBlurUpdateTime > 120) {
                try {
                    contentEffect = RenderEffect.createBlurEffect(blurLevel, blurLevel, Shader.TileMode.CLAMP);
                    cachedBlurEffect = contentEffect;
                    lastSigma = blurLevel;
                    lastBlurUpdateTime = now;
                } catch (Exception e) {
                    contentEffect = cachedBlurEffect;
                }
            } else {
                contentEffect = cachedBlurEffect;
            }
        }

        liquidShader.setFloatUniform("size", size);
        liquidShader.setFloatUniform("offset", offset);
        liquidShader.setFloatUniform("cornerRadii", cornerRadii);
        liquidShader.setFloatUniform("refractionHeight", refractionHeight);
        liquidShader.setFloatUniform("refractionAmount", refractionAmount);
        liquidShader.setFloatUniform("depthEffect", depthEffect);
        liquidShader.setFloatUniform("chromaticAberration", chromaticAberration);
        liquidShader.setFloatUniform("contrast", contrast);
        liquidShader.setFloatUniform("whitePoint", whitePoint);
        liquidShader.setFloatUniform("chromaMultiplier", chromaMultiplier);
        liquidShader.setFloatUniform("tintColor", new float[]{tintRed, tintGreen, tintBlue});
        liquidShader.setFloatUniform("tintAlpha", tintAlpha);
        liquidShader.setFloatUniform("edgeOnly", config.EDGE_ONLY);
        liquidShader.setFloatUniform("fluidBody", config.FLUID_WIDTH, config.FLUID_HEIGHT);
        liquidShader.setFloatUniform("fluidModes", config.FLUID_STRAIN, config.FLUID_SHEAR);
        int restingTint = config.FLUID_RESTING_TINT;
        liquidShader.setFloatUniform("fluidRestingTint",
                Color.red(restingTint) / 255f, Color.green(restingTint) / 255f,
                Color.blue(restingTint) / 255f, Color.alpha(restingTint) / 255f);
        liquidShader.setFloatUniform("fluidOpticalProgress", config.FLUID_OPTICAL_PROGRESS);

        RenderEffect shaderEffect = RenderEffect.createRuntimeShaderEffect(liquidShader, "content");
        RenderEffect finalEffect = (contentEffect != null)
                ? RenderEffect.createChainEffect(shaderEffect, contentEffect)
                : shaderEffect;

        node.setRenderEffect(finalEffect);
    }

    private RuntimeShader loadAgsl(Resources resources, int resourceId) {
        @Language("AGSL")
        String shaderCode = loadRaw(resources, resourceId);
        return new RuntimeShader(shaderCode);
    }

    private String loadRaw(Resources resources, int resourceId) {
        try (InputStream inputStream = resources.openRawResource(resourceId);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (IOException e) {
            throw new RuntimeException("Error loading shader: " + resourceId, e);
        }
    }

    @Override
    public void dispose() {
        releaseSampleResources();
    }
}
