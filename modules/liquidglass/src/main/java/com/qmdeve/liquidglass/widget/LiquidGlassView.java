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

package com.qmdeve.liquidglass.widget;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.qmdeve.liquidglass.LiquidGlass;
import com.qmdeve.liquidglass.Config;
import com.qmdeve.liquidglass.util.LiquidTracker;
import com.qmdeve.liquidglass.util.Utils;

public class LiquidGlassView extends ViewGroup {

    public interface OnBrightnessChangedListener {
        void onBrightnessChanged(float brightness);
    }

    public interface OnTransformChangedListener {
        void onTransformChanged(float scaleX, float scaleY, float translationX, float translationY);
    }

    private LiquidGlass glass;
    private ViewGroup customSource;
    private final Context context;
    private float cornerRadius = Utils.dp2px(getResources(), 40), refractionHeight = Utils.dp2px(getResources(), 20), refractionOffset = -Utils.dp2px(getResources(), 70), tintAlpha = 0.0f, tintColorRed = 1.0f, tintColorGreen = 1.0f, tintColorBlue = 1.0f, blurRadius = 0.01f, dispersion = 0.5f, downX, downY, startTx, startTy;
    private float requestedCornerRadius = cornerRadius;
    private boolean draggableEnabled = false;
    private boolean elasticEnabled = false;
    private boolean touchEffectEnabled = false;
    private boolean inputEnabled = true;
    private boolean edgeOnly = false;
    private boolean clearLens = false;
    private float fluidWidth, fluidHeight, fluidStrain, fluidShear;
    private int fluidRestingTint;
    private float fluidOpticalProgress = 1f;
    private ViewGroup samplingUnderlay;
    private ViewGroup samplingForeground;
    private android.graphics.PointF samplingScreenPosition;
    private boolean interactiveContentEnabled = true;
    private float touchEffectIntensity = 1f;
    private float touchEffectRadiusDp = 100f;
    private final float[] sharedTouchGlow = new float[4];
    public interface TouchObserver {
        boolean onTouch(float windowX, float windowY, int action);
    }
    private TouchObserver touchObserver;
    private boolean observerOwnsGesture;
    private boolean touchFeedbackTransferred;
    private boolean externalTouchFeedbackEnabled;
    private boolean externalTouchPressed;
    private long menuLongPressDelay = 180;
    private float menuLongPressSlopDp = 3f;
    private float menuDownRawX, menuDownRawY;
    private final Runnable menuLongPress = () -> {
        if (isLongClickable() && !observerOwnsGesture) {
            cancelLongPress();
            performLongClick();
        }
    };
    private float highlightMaxWidthDp = Float.POSITIVE_INFINITY;
    private float highlightWidthScale = 1f;
    private final int[] touchScreenLocation = new int[2];
    private final int[] touchWindowLocation = new int[2];

    public void setTouchObserver(TouchObserver observer) { touchObserver = observer; }
    public void setMenuLongPressThreshold(long delay, float slopDp) {
        menuLongPressDelay = delay;
        menuLongPressSlopDp = slopDp;
    }

    public void setExternalTouchFeedbackEnabled(boolean enabled) {
        externalTouchFeedbackEnabled = enabled;
    }

    private boolean externalTouchDeformationEnabled = true;
    private final Matrix externalTouchTransform = new Matrix();
    private final Matrix externalTouchInverse = new Matrix();
    private final float[] externalTouchPoint = new float[2];

    public void setExternalTouchDeformationEnabled(boolean enabled) {
        externalTouchDeformationEnabled = enabled;
    }

    public void updateExternalTouchFeedback(float windowX, float windowY, boolean pressed) {
        if (!externalTouchFeedbackEnabled) return;
        if (pressed && (getWidth() <= 0 || getHeight() <= 0)) return;
        if (pressed) {
            getLocationOnScreen(touchScreenLocation);
            getLocationInWindow(touchWindowLocation);
            float localX = windowX - touchWindowLocation[0];
            float localY = windowY - touchWindowLocation[1];
            float rawX = localX + touchScreenLocation[0];
            float rawY = localY + touchScreenLocation[1];
            if (!externalTouchDeformationEnabled) {
                // 宿主统一缩放/平移时，用完整逆变换还原触点，不能仅减左上角。
                externalTouchTransform.reset();
                transformMatrixToGlobal(externalTouchTransform);
                if (externalTouchTransform.invert(externalTouchInverse)) {
                    externalTouchPoint[0] = 0f;
                    externalTouchPoint[1] = 0f;
                    externalTouchTransform.mapPoints(externalTouchPoint);
                    externalTouchPoint[0] += localX;
                    externalTouchPoint[1] += localY;
                    externalTouchInverse.mapPoints(externalTouchPoint);
                    localX = externalTouchPoint[0];
                    localY = externalTouchPoint[1];
                }
            }
            if (!externalTouchPressed) beginTouchFeedback(localX, localY, rawX, rawY);
            else moveTouchFeedback(localX, localY, rawX, rawY);
        } else if (externalTouchPressed) endTouchFeedback();
        externalTouchPressed = pressed;
    }

    private void beginTouchFeedback(float x, float y, float rawX, float rawY) {
        touchStartRawX = rawX;
        touchStartRawY = rawY;
        if (!externalTouchFeedbackEnabled || externalTouchDeformationEnabled) {
            liquidTracker.animateGentleScale(pressedScale(getWidth()), pressedScale(getHeight()));
        }
        removeCallbacks(stopGlowTrackingRunnable);
        Choreographer.getInstance().removeFrameCallback(glowFrameCallback);
        glowX = smoothGlowX = x;
        glowY = smoothGlowY = y;
        glowTrackingActive = true;
        Choreographer.getInstance().postFrameCallback(glowFrameCallback);
        if (glowAnimator.isRunning()) glowAnimator.cancel();
        glowAnimator.setFloatValues(glowAlpha, 1f);
        glowAnimator.start();
    }

    private void moveTouchFeedback(float x, float y, float rawX, float rawY) {
        glowX = x;
        glowY = y;
        if (!externalTouchFeedbackEnabled || externalTouchDeformationEnabled) {
            applyTouchDeformation(rawX, rawY);
        }
        invalidate();
    }

    private void endTouchFeedback() {
        if (!externalTouchFeedbackEnabled || externalTouchDeformationEnabled) {
            liquidTracker.animateGentleScale(1f, 1f);
            liquidTracker.animateTranslation(0f, 0f);
        }
        if (glowAnimator.isRunning()) glowAnimator.cancel();
        glowAnimator.setFloatValues(glowAlpha, 0f);
        glowAnimator.start();
        removeCallbacks(stopGlowTrackingRunnable);
        postDelayed(stopGlowTrackingRunnable, glowAnimator.getDuration());
    }
    public void setHighlightMaxWidth(float widthDp) {
        float resolved = LiquidGlassHighlightMetrics.sanitizeMaxWidth(widthDp);
        if (highlightMaxWidthDp == resolved) return;
        highlightMaxWidthDp = resolved;
        invalidate();
        invalidateMetaballOverlay();
    }

    public void setHighlightWidthScale(float scale) {
        float resolved = LiquidGlassHighlightMetrics.sanitizeScale(scale);
        if (highlightWidthScale == resolved) return;
        highlightWidthScale = resolved;
        invalidate();
        invalidateMetaballOverlay();
    }

    public float getHighlightWidthScale() {
        return highlightWidthScale;
    }

    float getHighlightMaxWidth() {
        return highlightMaxWidthDp;
    }

    private final Matrix highlightTransform = new Matrix();
    private final float[] highlightTransformValues = new float[9];

    private float resolvedHighlightWidth(float density) {
        highlightTransform.reset();
        transformMatrixToGlobal(highlightTransform);
        highlightTransform.getValues(highlightTransformValues);
        float stretch = LiquidGlassHighlightMetrics.maximumStretch(
                highlightTransformValues[Matrix.MSCALE_X], highlightTransformValues[Matrix.MSKEW_X],
                highlightTransformValues[Matrix.MSKEW_Y], highlightTransformValues[Matrix.MSCALE_Y]);
        return LiquidGlassHighlightMetrics.widthPx(getWidth(), getHeight(), density,
                highlightWidthScale, highlightMaxWidthDp, stretch);
    }

    /** The menu inherits the pointer while its trigger returns along its spring. */
    public void transferTouchFeedback() {
        touchFeedbackTransferred = true;
        if (!externalTouchFeedbackEnabled) endTouchFeedback();
    }
    public void setTouchEffectRadius(float radiusDp) {
        float next = Math.max(1f, radiusDp);
        if (touchEffectRadiusDp == next) return;
        touchEffectRadiusDp = next;
        invalidate();
        LiquidGlassSharedGlow.changed(this);
    }
    private Config config;
    private LiquidTracker liquidTracker;
    private float baseRefractionHeight;
    private float baseRefractionOffset;
    private float baseBlurRadius;
    private float baseDispersion;

    // Glow effect variables
    private Paint glowPaint;
    private Paint fresnelPaint;
    private Paint highlightPaint;
    private boolean adaptiveTintEnabled = true;
    private boolean adaptiveTintTrackingActive = false;
    private float lastAdaptiveBrightness = -1f;
    private float adaptiveTargetBrightness = 0.5f;
    private float adaptiveFilteredBrightness = 0.5f;
    private boolean adaptiveAppearanceInitialized = false;
    private long lastAdaptiveSampleUptimeMs = -1L;
    private String lastAdaptiveSampleMode = "none";
    private OnBrightnessChangedListener onBrightnessChangedListener;
    private OnTransformChangedListener onTransformChangedListener;
    private String metaballGroupId;
    private boolean metaballAnchorRight;
    private boolean metaballProximityOnly;
    private float metaballMergeGapPx = Utils.dp2px(getResources(), 18);
    private float metaballProgress = 1f;
    private int metaballColor = Color.WHITE;
    private LiquidGlassFusionOverlay metaballOverlay;
    private boolean metaballFusionActive;
    private float gravityStrength = 1f;
    private float metaballEmergence = 1f;
    private float metaballMotionBlur;
    private Choreographer.FrameCallback adaptiveTintFrameCallback;
    private float glowX, glowY;             // 实际触摸位置（target）
    private float smoothGlowX, smoothGlowY; // 平滑插值后的位置（渲染用）
    private float glowAlpha = 0f;           // 0~1，驱动辉光淡入淡出
    private ValueAnimator glowAnimator;     // 辉光动画器
    private boolean glowTrackingActive = false; // Choreographer 追踪标志，替代 glowAlpha 判断
    private float touchStartRawX, touchStartRawY;  // 触摸起始点（屏幕坐标）
    private Choreographer.FrameCallback glowFrameCallback; // 连续帧回调，驱动平滑追踪
    private final Runnable stopGlowTrackingRunnable = () -> glowTrackingActive = false;

    private final Path clipPath = new Path();
    private final Path ringPath = new Path();
    private final Path innerClipPath = new Path();
    private final RectF clipRect = new RectF();
    private final RectF innerClipRect = new RectF();

    public LiquidGlassView(Context context) {
        super(context);
        this.context = context;
        init();
    }

    public LiquidGlassView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        init();
    }

    public LiquidGlassView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
        init();
    }

    private void init() {
        setClipToPadding(false);
        setClipChildren(false);
        liquidTracker = new LiquidTracker(this);
        liquidTracker.setOnTransformChangedListener((scaleX, scaleY, translationX, translationY) -> {
            if (onTransformChangedListener != null) {
                onTransformChangedListener.onTransformChanged(
                        scaleX, scaleY, translationX, translationY
                );
            }
            invalidateMetaballOverlay();
            if (glowAlpha > 0.001f) LiquidGlassSharedGlow.changed(this);
        });
        baseRefractionHeight = refractionHeight;
        baseRefractionOffset = refractionOffset;
        baseBlurRadius = blurRadius;
        baseDispersion = dispersion;

        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setStyle(Paint.Style.FILL);

        fresnelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fresnelPaint.setStyle(Paint.Style.STROKE);
        fresnelPaint.setStrokeCap(Paint.Cap.ROUND);

        highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        highlightPaint.setStyle(Paint.Style.FILL);

        // 辉光淡入淡出动画器（仅控制 alpha，位置追踪由 Choreographer 帧回调统一负责）
        glowAnimator = ValueAnimator.ofFloat(0f, 1f);
        glowAnimator.setDuration(200);
        glowAnimator.addUpdateListener(animation -> {
            glowAlpha = (float) animation.getAnimatedValue();
            invalidate();
            invalidateMetaballOverlay();
            LiquidGlassSharedGlow.changed(this);
        });

        // 连续帧回调：统一负责平滑追踪位置（蔓延/拖尾效果）
        // 因子 0.14 营造更柔和的拖尾感，比快速跟上更接近真实光晕的惯性
        // ★ 改用 glowTrackingActive 布尔标志替代 glowAlpha 判断，
        //   避免 ACTION_DOWN 时序中 glowAlpha 仍为 0 导致回调自杀
        glowFrameCallback = frameTimeNanos -> {
            if (!glowTrackingActive) return;
            smoothGlowX += (glowX - smoothGlowX) * 0.14f;
            smoothGlowY += (glowY - smoothGlowY) * 0.14f;
            invalidate();
            invalidateMetaballOverlay();
            LiquidGlassSharedGlow.changed(this);
            Choreographer.getInstance().postFrameCallback(glowFrameCallback);
        };

        adaptiveTintFrameCallback = frameTimeNanos -> {
            if (!adaptiveTintTrackingActive || !adaptiveTintEnabled || !isAttachedToWindow()) return;
            if (glass != null) {
                glass.updateParameters();
            }
            float brightness = getContentBrightness();
            long sampleUptimeMs = getLastSampleUptimeMs();
            String sampleMode = getLastSampleMode();
            if (lastAdaptiveBrightness < 0f) {
                adaptiveTargetBrightness = brightness;
                adaptiveFilteredBrightness = brightness;
                lastAdaptiveBrightness = brightness;
            } else {
                float deadband = 0.010f;
                float targetDelta = brightness - adaptiveTargetBrightness;
                float targetFollowFactor = Math.abs(targetDelta) > deadband ? 0.26f : 0.06f;
                adaptiveTargetBrightness += targetDelta * targetFollowFactor;
                if (Math.abs(targetDelta) < 0.0012f) {
                    adaptiveTargetBrightness = brightness;
                }

                float filteredDelta = adaptiveTargetBrightness - adaptiveFilteredBrightness;
                adaptiveFilteredBrightness += filteredDelta * 0.18f;
                if (Math.abs(filteredDelta) < 0.0008f) {
                    adaptiveFilteredBrightness = adaptiveTargetBrightness;
                }
            }
            boolean brightnessChanged = Math.abs(adaptiveFilteredBrightness - lastAdaptiveBrightness) > 0.0008f;
            boolean sampleChanged = sampleUptimeMs != lastAdaptiveSampleUptimeMs
                    || (sampleMode != null && !sampleMode.equals(lastAdaptiveSampleMode));
            boolean appearanceChanged = stepAdaptiveTint(adaptiveFilteredBrightness, false);

            if (brightnessChanged) {
                lastAdaptiveBrightness = adaptiveFilteredBrightness;
                invalidate();
            }
            if (sampleChanged) {
                lastAdaptiveSampleUptimeMs = sampleUptimeMs;
                lastAdaptiveSampleMode = sampleMode;
            }
            if (appearanceChanged) {
                invalidate();
            }
            if ((brightnessChanged || sampleChanged || appearanceChanged) && onBrightnessChangedListener != null) {
                onBrightnessChangedListener.onBrightnessChanged(adaptiveFilteredBrightness);
            }
            Choreographer.getInstance().postFrameCallback(adaptiveTintFrameCallback);
        };
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        // The window overlay draws both the optical union and unwarped content.
        // Keep this View measured, visible and interactive; suppress only drawing.
        if (metaballFusionActive) return;
        // The optical shader owns the deformed silhouette and its surface normals.
        if (fluidWidth > 0f && fluidHeight > 0f && opticalEnabled) {
            super.dispatchDraw(canvas);
            return;
        }

        clipRect.set(0, 0, getWidth(), getHeight());
        clipPath.reset();
        clipPath.addRoundRect(
                clipRect,
                cornerRadius,
                cornerRadius,
                Path.Direction.CW
        );

        int save = canvas.save();
        canvas.clipPath(clipPath);

        super.dispatchDraw(canvas);

        canvas.restoreToCount(save);

        // 菲涅尔边缘光
        if (!surfaceHighlightsEnabled) return;
        float density = getResources().getDisplayMetrics().density;
        float w = getWidth();
        float h = getHeight();
        float shortSide = Math.min(w, h);
        float contentBrightness = adaptiveAppearanceInitialized ? adaptiveFilteredBrightness : getContentBrightness();
        resolveTouchGlow(sharedTouchGlow);
        float touchIntensity = sharedTouchGlow[2];
        float pressBoost = touchIntensity * 0.35f;
        float brightContentFactor = Math.max(0f, Math.min(1f, (contentBrightness - 0.56f) / 0.32f));
        float lightModeVisibilityBoost = Math.max(0f, Math.min(1f, (contentBrightness - 0.68f) / 0.20f));
        float edgeLightAlpha = contentBrightness > 0.5f
                ? 0.16f + (contentBrightness - 0.5f) * 0.14f
                : 0.20f + (0.5f - contentBrightness) * 0.12f;
        edgeLightAlpha = Math.min(0.56f, edgeLightAlpha * (1f + touchIntensity * 0.22f)
                + brightContentFactor * 0.05f
                + lightModeVisibilityBoost * 0.15f);
        float rimAlpha = contentBrightness > 0.5f
                ? 0.075f + (contentBrightness - 0.5f) * 0.050f
                : 0.095f + (0.5f - contentBrightness) * 0.040f;
        rimAlpha = Math.min(0.34f, rimAlpha * (1f + pressBoost * 0.18f)
                + brightContentFactor * 0.045f
                + lightModeVisibilityBoost * 0.14f);
        float contrastEdgeAlpha = (0.030f + brightContentFactor * 0.11f + lightModeVisibilityBoost * 0.040f)
                * (1f + touchIntensity * 0.12f);
        float contrastRimAlpha = (0.018f + brightContentFactor * 0.085f + lightModeVisibilityBoost * 0.016f)
                * (1f + pressBoost * 0.10f);
        float whiteCoreAlpha = Math.min(0.62f, 0.060f + lightModeVisibilityBoost * 0.34f + touchIntensity * 0.055f);
        float ringWidth = resolvedHighlightWidth(density);
        if (ringWidth <= 0f) return;
        float outerCr = Math.min(Math.max(cornerRadius, 0f), shortSide * 0.5f);
        float rimWidth = ringWidth * 0.72f;
        float inset = rimWidth * 0.5f;
        float cr = Math.max(outerCr - inset, 0f);
        float innerInset = ringWidth;
        float innerCr = Math.max(outerCr - ringWidth, 0f);
        innerClipRect.set(innerInset, innerInset, w - innerInset, h - innerInset);
        innerClipPath.reset();
        innerClipPath.addRoundRect(innerClipRect, innerCr, innerCr, Path.Direction.CW);
        ringPath.reset();
        ringPath.addRoundRect(clipRect, outerCr, outerCr, Path.Direction.CW);
        ringPath.op(innerClipPath, Path.Op.DIFFERENCE);

        // Vertical illumination: both upper and lower edges, fading toward
        // the sides. Keep the direction consistent for every glass size.
        float topLeftEndX = 0f;
        float topLeftEndY = h * 0.48f;
        float bottomRightStartX = w;
        float bottomRightStartY = h * 0.52f;
        float topLeftRimEndX = w - inset;
        float topLeftRimEndY = inset;
        float bottomRightRimStartX = inset;
        float bottomRightRimStartY = h - inset;
        float directionalShiftX = 0f;
        float directionalShiftY = 0f;

        canvas.save();
        canvas.clipPath(clipPath);
        canvas.clipPath(ringPath);

        highlightPaint.setStyle(Paint.Style.FILL);
        highlightPaint.setShader(new LinearGradient(
                0f,
                -ringWidth * 0.3f + directionalShiftY,
                topLeftEndX + directionalShiftX,
                topLeftEndY + directionalShiftY,
                new int[]{
                        Color.argb((int) (255 * edgeLightAlpha), 255, 255, 255),
                        Color.argb((int) (180 * edgeLightAlpha), 255, 255, 255),
                        Color.argb((int) (60 * edgeLightAlpha), 255, 255, 255),
                        Color.TRANSPARENT
                },
                new float[]{0f, 0.18f, 0.42f, 1f},
                Shader.TileMode.CLAMP
        ));
        orientSharedHighlight(highlightPaint);
        canvas.drawRect(clipRect, highlightPaint);

        if (contrastEdgeAlpha > 0.01f) {
            highlightPaint.setShader(new LinearGradient(
                    0f,
                    -ringWidth * 0.2f + directionalShiftY,
                    topLeftEndX + directionalShiftX,
                    topLeftEndY + directionalShiftY,
                    new int[]{
                            Color.argb((int) (160 * contrastEdgeAlpha), 0, 0, 0),
                            Color.argb((int) (96 * contrastEdgeAlpha), 0, 0, 0),
                            Color.argb((int) (36 * contrastEdgeAlpha), 0, 0, 0),
                            Color.TRANSPARENT
                    },
                    new float[]{0f, 0.20f, 0.48f, 1f},
                    Shader.TileMode.CLAMP
            ));
            orientSharedHighlight(highlightPaint);
            canvas.drawRect(clipRect, highlightPaint);
        }

        highlightPaint.setShader(new LinearGradient(
                bottomRightStartX + directionalShiftX,
                bottomRightStartY + directionalShiftY,
                w,
                h + ringWidth * 0.3f + directionalShiftY,
                new int[]{
                        Color.TRANSPARENT,
                        Color.argb((int) (55 * edgeLightAlpha), 255, 255, 255),
                        Color.argb((int) (160 * edgeLightAlpha), 255, 255, 255),
                        Color.argb((int) (230 * edgeLightAlpha), 255, 255, 255)
                },
                new float[]{0f, 0.54f, 0.82f, 1f},
                Shader.TileMode.CLAMP
        ));
        orientSharedHighlight(highlightPaint);
        canvas.drawRect(clipRect, highlightPaint);

        if (contrastEdgeAlpha > 0.01f) {
            highlightPaint.setShader(new LinearGradient(
                    bottomRightStartX + directionalShiftX,
                    bottomRightStartY + directionalShiftY,
                    w,
                    h + ringWidth * 0.2f + directionalShiftY,
                    new int[]{
                            Color.TRANSPARENT,
                            Color.argb((int) (34 * contrastEdgeAlpha), 0, 0, 0),
                            Color.argb((int) (92 * contrastEdgeAlpha), 0, 0, 0),
                            Color.argb((int) (150 * contrastEdgeAlpha), 0, 0, 0)
                    },
                    new float[]{0f, 0.52f, 0.80f, 1f},
                    Shader.TileMode.CLAMP
            ));
            orientSharedHighlight(highlightPaint);
            canvas.drawRect(clipRect, highlightPaint);
        }
        highlightPaint.setShader(null);

        Path topLeftRimPath = new Path();
        RectF topLeftCornerRect = new RectF(inset, inset, inset + 2f * cr, inset + 2f * cr);
        topLeftRimPath.moveTo(inset, inset + cr);
        topLeftRimPath.arcTo(topLeftCornerRect, 180f, 90f, false);
        topLeftRimPath.lineTo(w - inset - cr, inset);
        topLeftRimPath.arcTo(new RectF(w - inset - 2f * cr, inset,
                w - inset, inset + 2f * cr), 270f, 90f, false);

        Path bottomRightRimPath = new Path();
        RectF bottomRightCornerRect = new RectF(w - inset - 2f * cr, h - inset - 2f * cr, w - inset, h - inset);
        bottomRightRimPath.moveTo(w - inset, h - inset - cr);
        bottomRightRimPath.arcTo(bottomRightCornerRect, 0f, 90f, false);
        bottomRightRimPath.lineTo(inset + cr, h - inset);
        bottomRightRimPath.arcTo(new RectF(inset, h - inset - 2f * cr,
                inset + 2f * cr, h - inset), 90f, 90f, false);

        fresnelPaint.setStyle(Paint.Style.STROKE);
        fresnelPaint.setStrokeWidth(rimWidth);
        fresnelPaint.setStrokeJoin(Paint.Join.ROUND);
        fresnelPaint.setStrokeCap(Paint.Cap.ROUND);
        if (contrastRimAlpha > 0.008f) {
            fresnelPaint.setShader(new LinearGradient(
                    topLeftRimEndX + directionalShiftX,
                    inset + directionalShiftY,
                    inset + directionalShiftX,
                    topLeftRimEndY + directionalShiftY,
                    new int[]{
                            Color.TRANSPARENT,
                            Color.argb((int) (72 * contrastRimAlpha), 0, 0, 0),
                            Color.argb((int) (135 * contrastRimAlpha), 0, 0, 0),
                            Color.argb((int) (72 * contrastRimAlpha), 0, 0, 0),
                            Color.TRANSPARENT
                    },
                    new float[]{0f, 0.16f, 0.50f, 0.84f, 1f},
                    Shader.TileMode.CLAMP
            ));
            orientSharedHighlight(fresnelPaint);
            canvas.drawPath(topLeftRimPath, fresnelPaint);
            fresnelPaint.setShader(new LinearGradient(
                    w - inset + directionalShiftX,
                    bottomRightRimStartY + directionalShiftY,
                    bottomRightRimStartX + directionalShiftX,
                    h - inset + directionalShiftY,
                    new int[]{
                            Color.TRANSPARENT,
                            Color.argb((int) (72 * contrastRimAlpha), 0, 0, 0),
                            Color.argb((int) (135 * contrastRimAlpha), 0, 0, 0),
                            Color.argb((int) (72 * contrastRimAlpha), 0, 0, 0),
                            Color.TRANSPARENT
                    },
                    new float[]{0f, 0.16f, 0.50f, 0.84f, 1f},
                    Shader.TileMode.CLAMP
            ));
            orientSharedHighlight(fresnelPaint);
            canvas.drawPath(bottomRightRimPath, fresnelPaint);
        }
        fresnelPaint.setShader(new LinearGradient(
                topLeftRimEndX + directionalShiftX,
                inset + directionalShiftY,
                inset + directionalShiftX,
                topLeftRimEndY + directionalShiftY,
                new int[]{
                        Color.TRANSPARENT,
                        Color.argb((int) (92 * rimAlpha), 255, 255, 255),
                        Color.argb((int) (158 * rimAlpha), 255, 255, 255),
                        Color.argb((int) (92 * rimAlpha), 255, 255, 255),
                        Color.TRANSPARENT
                },
                new float[]{0f, 0.18f, 0.50f, 0.82f, 1f},
                Shader.TileMode.CLAMP
        ));
        applySharedLightToRim();
        canvas.drawPath(topLeftRimPath, fresnelPaint);
        fresnelPaint.setShader(new LinearGradient(
                w - inset + directionalShiftX,
                bottomRightRimStartY + directionalShiftY,
                bottomRightRimStartX + directionalShiftX,
                h - inset + directionalShiftY,
                new int[]{
                        Color.TRANSPARENT,
                        Color.argb((int) (92 * rimAlpha), 255, 255, 255),
                        Color.argb((int) (158 * rimAlpha), 255, 255, 255),
                        Color.argb((int) (92 * rimAlpha), 255, 255, 255),
                        Color.TRANSPARENT
                },
                new float[]{0f, 0.18f, 0.50f, 0.82f, 1f},
                Shader.TileMode.CLAMP
        ));
        applySharedLightToRim();
        canvas.drawPath(bottomRightRimPath, fresnelPaint);

        if (whiteCoreAlpha > 0.01f) {
            fresnelPaint.setStrokeWidth(ringWidth * 0.38f);
            fresnelPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
            fresnelPaint.setShader(new LinearGradient(
                    topLeftRimEndX + directionalShiftX,
                    inset + directionalShiftY,
                    inset + directionalShiftX,
                    topLeftRimEndY + directionalShiftY,
                    new int[]{
                            Color.TRANSPARENT,
                            Color.argb((int) (132 * whiteCoreAlpha), 255, 255, 255),
                            Color.argb((int) (248 * whiteCoreAlpha), 255, 255, 255),
                            Color.argb((int) (255 * whiteCoreAlpha), 255, 255, 255),
                            Color.argb((int) (248 * whiteCoreAlpha), 255, 255, 255),
                            Color.argb((int) (132 * whiteCoreAlpha), 255, 255, 255),
                            Color.TRANSPARENT
                    },
                    new float[]{0f, 0.10f, 0.26f, 0.50f, 0.74f, 0.90f, 1f},
                    Shader.TileMode.CLAMP
            ));
            applySharedLightToRim();
            canvas.drawPath(topLeftRimPath, fresnelPaint);
            fresnelPaint.setShader(new LinearGradient(
                    w - inset + directionalShiftX,
                    bottomRightRimStartY + directionalShiftY,
                    bottomRightRimStartX + directionalShiftX,
                    h - inset + directionalShiftY,
                    new int[]{
                            Color.TRANSPARENT,
                            Color.argb((int) (132 * whiteCoreAlpha), 255, 255, 255),
                            Color.argb((int) (248 * whiteCoreAlpha), 255, 255, 255),
                            Color.argb((int) (255 * whiteCoreAlpha), 255, 255, 255),
                            Color.argb((int) (248 * whiteCoreAlpha), 255, 255, 255),
                            Color.argb((int) (132 * whiteCoreAlpha), 255, 255, 255),
                            Color.TRANSPARENT
                    },
                    new float[]{0f, 0.10f, 0.26f, 0.50f, 0.74f, 0.90f, 1f},
                    Shader.TileMode.CLAMP
            ));
            applySharedLightToRim();
            canvas.drawPath(bottomRightRimPath, fresnelPaint);
            fresnelPaint.setXfermode(null);
        }

        fresnelPaint.setStrokeWidth(rimWidth);
        fresnelPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        fresnelPaint.setShader(new LinearGradient(
                topLeftRimEndX + directionalShiftX,
                inset + directionalShiftY,
                inset + directionalShiftX,
                topLeftRimEndY + directionalShiftY,
                new int[]{
                        Color.TRANSPARENT,
                        Color.argb((int) (18 * rimAlpha), 255, 255, 255),
                        Color.argb((int) (92 * rimAlpha), 255, 255, 255),
                        Color.argb((int) (188 * rimAlpha), 255, 255, 255),
                        Color.argb((int) (255 * rimAlpha), 255, 255, 255),
                        Color.argb((int) (188 * rimAlpha), 255, 255, 255),
                        Color.argb((int) (92 * rimAlpha), 255, 255, 255),
                        Color.argb((int) (18 * rimAlpha), 255, 255, 255),
                        Color.TRANSPARENT
                },
                new float[]{0f, 0.06f, 0.16f, 0.32f, 0.50f, 0.68f, 0.84f, 0.94f, 1f},
                Shader.TileMode.CLAMP
        ));
        applySharedLightToRim();
        canvas.drawPath(topLeftRimPath, fresnelPaint);

        fresnelPaint.setShader(new LinearGradient(
                w - inset + directionalShiftX,
                bottomRightRimStartY + directionalShiftY,
                bottomRightRimStartX + directionalShiftX,
                h - inset + directionalShiftY,
                new int[]{
                        Color.TRANSPARENT,
                        Color.argb((int) (18 * rimAlpha), 255, 255, 255),
                        Color.argb((int) (92 * rimAlpha), 255, 255, 255),
                        Color.argb((int) (188 * rimAlpha), 255, 255, 255),
                        Color.argb((int) (255 * rimAlpha), 255, 255, 255),
                        Color.argb((int) (188 * rimAlpha), 255, 255, 255),
                        Color.argb((int) (92 * rimAlpha), 255, 255, 255),
                        Color.argb((int) (18 * rimAlpha), 255, 255, 255),
                        Color.TRANSPARENT
                },
                new float[]{0f, 0.06f, 0.16f, 0.32f, 0.50f, 0.68f, 0.84f, 0.94f, 1f},
                Shader.TileMode.CLAMP
        ));
        applySharedLightToRim();
        canvas.drawPath(bottomRightRimPath, fresnelPaint);
        fresnelPaint.setXfermode(null);
        fresnelPaint.setShader(null);
        fresnelPaint.setStyle(Paint.Style.STROKE);

        // 共享触点光同时照亮轮廓，径向衰减保留控件原有的减弱强度。
        // glowAlpha 沿现有按压动画连续变化，松手时亮边同步渐退。
        if (touchIntensity > 0.001f) {
            highlightPaint.setShader(new RadialGradient(
                    sharedTouchGlow[0], sharedTouchGlow[1],
                    Math.max(1f, sharedTouchGlow[3]),
                    new int[]{
                            Color.argb((int) (255f * 0.46f * touchIntensity), 255, 255, 255),
                            Color.argb((int) (255f * 0.23f * touchIntensity), 255, 255, 255),
                            Color.argb((int) (255f * 0.055f * touchIntensity), 255, 255, 255),
                            Color.TRANSPARENT
                    },
                    new float[]{0f, 0.35f, 0.72f, 1f}, Shader.TileMode.CLAMP
            ));
            canvas.drawRect(clipRect, highlightPaint);
            highlightPaint.setShader(null);
        }
        canvas.restore();

        if (touchEffectEnabled && sharedTouchGlow[2] > 0.001f) {
            canvas.save();
            canvas.clipPath(clipPath);

            float gx = sharedTouchGlow[0], gy = sharedTouchGlow[1];
            float a = sharedTouchGlow[2];

            // baseRadius = 固定物理尺寸 100dp，像真实灯光有固定的发光半径。
            // 小组件 → 被 clipPath 裁剪，核心爆亮区域依然可见
            // 大组件 → 自然展示一个手指触摸大小的光晕，不会过大
            float baseRadius = sharedTouchGlow[3];

            // ── HDR 单层 Bloom ──
            // 单层 7-stop RadialGradient + ADD 混合，无层间硬边界。
            // 曲线设计：中心 0→0.08 急速衰减（HDR blown-out 核心），
            // 0.08→0.65 平滑过渡（光晕主体），0.65→1.0 渐隐（柔光尾迹）
            glowPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
            glowPaint.setShader(new RadialGradient(gx, gy, baseRadius,
                    new int[]{
                            Color.argb((int)(255 * a), 255, 255, 255),
                            Color.argb((int)(210 * a), 255, 255, 255),
                            Color.argb((int)(150 * a), 255, 255, 255),
                            Color.argb((int)(85 * a), 255, 255, 255),
                            Color.argb((int)(40 * a), 255, 255, 255),
                            Color.argb((int)(15 * a), 255, 255, 255),
                            Color.TRANSPARENT
                    },
                    new float[]{0f, 0.08f, 0.20f, 0.40f, 0.65f, 0.85f, 1f},
                    Shader.TileMode.CLAMP));
            canvas.drawRect(clipRect, glowPaint);
            glowPaint.setXfermode(null);

            canvas.restore();
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        // Establish the optical center before the first scaled emergence frame.
        setPivotX(getWidth() * 0.5f);
        setPivotY(getHeight() * 0.5f);
        // Layout all child views
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                // Layout children to fill the entire view
                child.layout(
                        getPaddingLeft(),
                        getPaddingTop(),
                        getPaddingLeft() + child.getMeasuredWidth(),
                        getPaddingTop() + child.getMeasuredHeight()
                );
            }
        }
        invalidateMetaballOverlay();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        if (widthMode == MeasureSpec.AT_MOST) {
            ViewGroup.LayoutParams lp = getLayoutParams();
            // 如果 layoutParams.width 已被显式设置为正数（动画驱动），直接用它，
            // 避免 Compose 内容切换时子 View 的期望尺寸覆盖动画值导致闪现
            if (lp != null && lp.width > 0) {
                int width = Math.min(lp.width, widthSize);
                setMeasuredDimension(width, heightSize);
                for (int i = 0; i < getChildCount(); i++) {
                    View child = getChildAt(i);
                    if (child.getVisibility() != GONE) {
                        child.measure(
                                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                                MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY)
                        );
                    }
                }
                return;
            }

            // 第一遍：用 UNSPECIFIED 让子 View 自由报告期望尺寸
            int maxChildWidth = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() != GONE) {
                    child.measure(
                            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                            MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.AT_MOST)
                    );
                    maxChildWidth = Math.max(maxChildWidth,
                            child.getMeasuredWidth() + getPaddingLeft() + getPaddingRight());
                }
            }
            int width = Math.min(maxChildWidth, widthSize);
            setMeasuredDimension(width, heightSize);

            // 第二遍：用最终尺寸重新量子 View，确保遮罩层正确适配
            int finalW = getMeasuredWidth();
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() != GONE) {
                    child.measure(
                            MeasureSpec.makeMeasureSpec(finalW, MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY)
                    );
                }
            }
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() != GONE) {
                    measureChild(child, widthMeasureSpec, heightMeasureSpec);
                }
            }
        }
    }

    /**
     * Bind sampling source
     *
     * @param source ViewGroup
     */
    public void bind(ViewGroup source) {
        // A source containing this receiver creates a cyclic RenderNode graph.
        // Reject the binding before it reaches HWUI (native recursion cannot be caught).
        View ancestor = this;
        while (ancestor != null) {
            if (ancestor == source) return;
            android.view.ViewParent parent = ancestor.getParent();
            ancestor = parent instanceof View ? (View) parent : null;
        }
        this.customSource = source;
        if (glass != null && source != null) {
            glass.init(source);
        }
        refreshMetaballOverlay();
        invalidateMetaballOverlay();
    }

    private boolean opticalEnabled = true;
    private boolean surfaceHighlightsEnabled = true;

    /** Change the renderer while retaining the same input and spring session. */
    public void setOpticalEnabled(boolean enabled) {
        if (opticalEnabled == enabled) return;
        opticalEnabled = enabled;
        if (enabled) ensureGlass();
        else removeGlass();
        refreshMetaballOverlay();
        invalidate();
    }

    public void setSurfaceHighlightsEnabled(boolean enabled) {
        if (surfaceHighlightsEnabled == enabled) return;
        surfaceHighlightsEnabled = enabled;
        invalidate();
    }

    /**
     * Set the corner radius px
     *
     * @param px float
     */
    public void setCornerRadius(float px) {
        requestedCornerRadius = Math.max(0, px);
        float maxPx = getHeight() > 0 ? getHeight() / 2f : Utils.dp2px(getResources(), 99);
        float resolved = Math.min(requestedCornerRadius, maxPx);
        if (this.cornerRadius == resolved) return;
        this.cornerRadius = resolved;
        invalidate();
        updateConfig();
    }

    /**
     * Set the refraction height px
     *
     * @param px float
     */
    public void setRefractionHeight(float px) {
        float minPx = Utils.dp2px(getResources(), (edgeOnly || clearLens) ? 1 : 12);
        float maxPx = Utils.dp2px(getResources(), 50);
        this.refractionHeight = Math.max(minPx, Math.min(maxPx, px));
        this.baseRefractionHeight = this.refractionHeight;
        updateConfig();
    }

    /**
     * Set the refraction offset px
     * Positive value will be converted to negative
     *
     * @param px float
     */
    public void setRefractionOffset(float px) {
        float minPx = Utils.dp2px(getResources(), (edgeOnly || clearLens) ? 1 : 20);
        float maxPx = Utils.dp2px(getResources(), 120);
        px = Math.max(minPx, Math.min(maxPx, px));
        this.refractionOffset = -px;
        this.baseRefractionOffset = this.refractionOffset;
        updateConfig();
    }

    /**
     * Set the tint color (R)
     *
     * @param red float (0f-1f)
     */
    public void setTintColorRed(float red) {
        if (tintColorRed == red) return;
        this.tintColorRed = red;
        updateConfig();
    }

    /**
     * Set the tint color (G)
     *
     * @param green float (0f-1f)
     */
    public void setTintColorGreen(float green) {
        if (tintColorGreen == green) return;
        this.tintColorGreen = green;
        updateConfig();
    }

    /**
     * Set the tint color (B)
     *
     * @param blue float (0f-1f)
     */
    public void setTintColorBlue(float blue) {
        if (tintColorBlue == blue) return;
        this.tintColorBlue = blue;
        updateConfig();
    }

    /**
     * Set the tint Alpha
     *
     * @param alpha float (0f-1f)
     */
    public void setTintAlpha(float alpha) {
        if (tintAlpha == alpha) return;
        this.tintAlpha = alpha;
        updateConfig();
    }

    /**
     * Set dispersion
     *
     * @param dispersion float (0f-1f)
     */
    public void setDispersion(float dispersion) {
        this.dispersion = Math.max(0f, Math.min(1f, dispersion));
        this.baseDispersion = this.dispersion;
        updateConfig();
    }

    /**
     * Set the blur radius
     *
     * @param radius float
     */
    public void setBlurRadius(float radius) {
        this.blurRadius = Math.max(0.01f, Math.min(50, radius));
        this.baseBlurRadius = this.blurRadius;
        updateConfig();
    }

    /** Transparent center, with optical refraction confined to the perimeter. */
    public void setEdgeOnly(boolean enabled) {
        if (edgeOnly == enabled) return;
        edgeOnly = enabled;
        updateConfig();
    }

    public boolean isEdgeOnly() { return edgeOnly; }

    /** Full sampled glass surface with narrow perimeter refraction. */
    public void setClearLens(boolean enabled) { clearLens = enabled; }

    public boolean isClearLens() { return clearLens; }

    public void setSamplingScreenPosition(float x, float y, boolean enabled) {
        enabled = enabled && Float.isFinite(x) && Float.isFinite(y);
        if (enabled && samplingScreenPosition != null
                && samplingScreenPosition.x == x && samplingScreenPosition.y == y) return;
        if (!enabled && samplingScreenPosition == null) return;
        samplingScreenPosition = enabled ? new android.graphics.PointF(x, y) : null;
        if (config != null) config.SAMPLING_SCREEN_POSITION = samplingScreenPosition;
        invalidate();
    }

    public void setFluidSurface(int restingTint, float opticalProgress) {
        float progress = Float.isFinite(opticalProgress)
                ? Math.max(0f, Math.min(1f, opticalProgress)) : 1f;
        if (fluidRestingTint == restingTint && fluidOpticalProgress == progress) return;
        fluidRestingTint = restingTint;
        fluidOpticalProgress = progress;
        if (config != null) {
            config.FLUID_RESTING_TINT = restingTint;
            config.FLUID_OPTICAL_PROGRESS = progress;
        }
        invalidate();
    }

    public void setFluidShape(float width, float height, float strain, float shear) {
        if (fluidWidth == width && fluidHeight == height && fluidStrain == strain && fluidShear == shear) return;
        fluidWidth = Float.isFinite(width) ? Math.max(0f, width) : 0f;
        fluidHeight = Float.isFinite(height) ? Math.max(0f, height) : 0f;
        fluidStrain = Float.isFinite(strain) ? Math.max(-0.42f, Math.min(0.42f, strain)) : 0f;
        fluidShear = Float.isFinite(shear) ? Math.max(-0.85f, Math.min(0.85f, shear)) : 0f;
        if (config != null) {
            config.FLUID_WIDTH = fluidWidth;
            config.FLUID_HEIGHT = fluidHeight;
            config.FLUID_STRAIN = fluidStrain;
            config.FLUID_SHEAR = fluidShear;
        }
        invalidate();
    }

    /** The lower glass must be a sibling source, never an ancestor of this receiver. */
    public void setSamplingUnderlay(ViewGroup source) {
        View ancestor = this;
        while (source != null && ancestor != null) {
            if (ancestor == source) return;
            android.view.ViewParent parent = ancestor.getParent();
            ancestor = parent instanceof View ? (View) parent : null;
        }
        if (samplingUnderlay == source) return;
        samplingUnderlay = source;
        if (config != null) config.SAMPLING_UNDERLAY = source;
        invalidate();
    }

    public void setSamplingForeground(ViewGroup source) {
        View ancestor = this;
        while (source != null && ancestor != null) {
            if (ancestor == source) return;
            android.view.ViewParent parent = ancestor.getParent();
            ancestor = parent instanceof View ? (View) parent : null;
        }
        if (samplingForeground == source) return;
        samplingForeground = source;
        if (config != null) config.SAMPLING_FOREGROUND = source;
        invalidate();
    }

    /**
     * Set whether the View is draggable or not
     *
     * @param enabled boolean
     */
    public void setDraggableEnabled(boolean enabled) {
        this.draggableEnabled = enabled;
        if (!enabled) {
            liquidTracker.recycle();
        }
    }

    /**
     * Set whether elastic effect is needed or not
     * @param enabled boolean
     */
    public void setElasticEnabled(boolean enabled) {
        this.elasticEnabled = enabled;
        if (!enabled) {
            liquidTracker.recycle();
        }
    }

    /**
     * Set whether the touch effect (iOS style press animation) is enabled
     * @param enabled boolean
     */
    public void setTouchEffectEnabled(boolean enabled) {
        if (touchEffectEnabled == enabled) return;
        this.touchEffectEnabled = enabled;
        invalidate();
        LiquidGlassSharedGlow.changed(this);
    }

    /**
     * Set the visual intensity of touch glow without changing touch deformation.
     *
     * @param intensity float (0f-1f)
     */
    public void setTouchEffectIntensity(float intensity) {
        float next = Math.max(0f, Math.min(1f, intensity));
        if (touchEffectIntensity == next) return;
        this.touchEffectIntensity = next;
        invalidate();
        LiquidGlassSharedGlow.changed(this);
    }

    /**
     * Set whether adaptive tint should follow the sampled content brightness automatically.
     * @param enabled boolean
     */
    public boolean isAdaptiveTintEnabled() {
        return adaptiveTintEnabled;
    }

    public void setAdaptiveTintEnabled(boolean enabled) {
        adaptiveTintEnabled = enabled;
        if (config != null) config.SAMPLE_BRIGHTNESS = enabled;
        adaptiveTintTrackingActive = false;
        Choreographer.getInstance().removeFrameCallback(adaptiveTintFrameCallback);
        if (enabled && isAttachedToWindow()) {
            lastAdaptiveBrightness = -1f;
            adaptiveTargetBrightness = 0.5f;
            adaptiveFilteredBrightness = 0.5f;
            adaptiveAppearanceInitialized = false;
            lastAdaptiveSampleUptimeMs = -1L;
            lastAdaptiveSampleMode = "none";
            adaptiveTintTrackingActive = true;
            Choreographer.getInstance().postFrameCallback(adaptiveTintFrameCallback);
        }
    }

    /**
     * Set brightness listener for adaptive content colors.
     * @param listener callback
     */
    public void setOnBrightnessChangedListener(@Nullable OnBrightnessChangedListener listener) {
        onBrightnessChangedListener = listener;
    }

    public void setOnTransformChangedListener(@Nullable OnTransformChangedListener listener) {
        onTransformChangedListener = listener;
    }

    public void setMetaballGroup(
            @Nullable String groupId,
            boolean anchorRight,
            float mergeGapPx,
            float progress,
            int color
    ) {
        boolean unchanged = (metaballGroupId == null
                ? groupId == null
                : metaballGroupId.equals(groupId))
                && metaballAnchorRight == anchorRight
                && metaballMergeGapPx == mergeGapPx
                && metaballProgress == progress
                && metaballColor == color;
        if (unchanged && (metaballOverlay != null || groupId == null)) return;

        metaballGroupId = groupId;
        metaballAnchorRight = anchorRight;
        metaballMergeGapPx = Math.max(0f, mergeGapPx);
        metaballProgress = Math.max(0f, Math.min(1f, progress));
        metaballColor = color;
        refreshMetaballOverlay();
    }

    public void setMetaballFusionActive(boolean active) {
        if (metaballFusionActive == active) return;
        metaballFusionActive = active;
        if (glass != null) glass.setSamplingEnabled(!active);
        invalidate();
    }

    /** 普通玻璃按实际位置配对，距离只控制连接形变；分开时也保留同一光学表面。 */
    public void setMetaballProximityOnly(boolean enabled) {
        if (metaballProximityOnly == enabled) return;
        metaballProximityOnly = enabled;
        invalidateMetaballOverlay();
    }
    boolean isMetaballProximityOnly() { return metaballProximityOnly; }

    float getMetaballBlurRadius() { return blurRadius; }
    float getMetaballCornerRadius() { return cornerRadius; }
    float getMetaballTintAlpha() { return tintAlpha; }
    public void setMetaballEmergence(float progress) {
        float next = Float.isFinite(progress) ? Math.max(0f, Math.min(1f, progress)) : 1f;
        if (metaballEmergence == next) return;
        metaballEmergence = next;
        invalidateMetaballOverlay();
    }
    float getMetaballEmergence() { return metaballEmergence; }
    public void setMetaballMotionBlur(float radius) {
        float next = Float.isFinite(radius) ? Math.max(0f, radius) : 0f;
        if (metaballMotionBlur == next) return;
        metaballMotionBlur = next;
        invalidateMetaballOverlay();
    }
    float getMetaballMotionBlur() { return metaballMotionBlur; }
    float getOwnTouchGlowX() { return smoothGlowX; }
    float getOwnTouchGlowY() { return smoothGlowY; }
    float getOwnTouchGlowAlpha() { return touchEffectEnabled ? glowAlpha : 0f; }
    float getTouchGlowIntensity() { return touchEffectEnabled ? touchEffectIntensity : 0f; }
    float getOwnTouchGlowStrength() { return getOwnTouchGlowAlpha() * getTouchGlowIntensity(); }
    float getTouchGlowRadiusPx() { return touchEffectRadiusDp * getResources().getDisplayMetrics().density; }
    private final Matrix highlightLightMatrix = new Matrix();

    /** Rotate the original illumination with the shared source; never rotate the glass contour. */
    private void orientSharedHighlight(Paint paint) {
        Shader shader = paint.getShader();
        float strength = sharedTouchGlow[2];
        if (shader == null || strength <= 0.001f) return;
        float cx = getWidth() * 0.5f;
        float cy = getHeight() * 0.5f;
        float dx = sharedTouchGlow[0] - cx;
        float dy = sharedTouchGlow[1] - cy;
        float radius = Math.max(1f, sharedTouchGlow[3]);
        float distance = (float) Math.hypot(
                Math.max(0f, Math.abs(dx) - cx),
                Math.max(0f, Math.abs(dy) - cy));
        float reach = Math.max(0f, 1f - distance / radius);
        float influence = strength * reach * reach;
        float angle = (float) Math.toDegrees(Math.atan2(dx,
                Math.abs(dy) + Math.min(cx, cy))) * 0.72f * influence;
        highlightLightMatrix.setRotate(angle, cx, cy);
        shader.setLocalMatrix(highlightLightMatrix);
    }

    /** 在原高光线的透明度分布上叠加光照，保留原有线宽和端部渐隐。 */
    private void applySharedLightToRim() {
        orientSharedHighlight(fresnelPaint);
        Shader original = fresnelPaint.getShader();
        float intensity = sharedTouchGlow[2];
        if (original == null || intensity <= 0.001f) return;
        Shader light = new RadialGradient(
                sharedTouchGlow[0], sharedTouchGlow[1], Math.max(1f, sharedTouchGlow[3]),
                new int[]{
                        Color.argb((int) (255f * 0.85f * intensity), 255, 255, 255),
                        Color.argb((int) (255f * 0.42f * intensity), 255, 255, 255),
                        Color.argb((int) (255f * 0.10f * intensity), 255, 255, 255),
                        Color.TRANSPARENT
                },
                new float[]{0f, 0.35f, 0.72f, 1f}, Shader.TileMode.CLAMP);
        Shader illuminatedLine = new ComposeShader(light, original, PorterDuff.Mode.DST_IN);
        fresnelPaint.setShader(new ComposeShader(original, illuminatedLine, PorterDuff.Mode.ADD));
    }

    void resolveTouchGlow(float[] result) { LiquidGlassSharedGlow.resolve(this, result); }
    void invalidateSharedTouchGlow() {
        invalidate();
        invalidateMetaballOverlay();
    }
    float[] getMetaballTintColor() {
        return new float[]{tintColorRed, tintColorGreen, tintColorBlue};
    }

    public void drawMetaballContent(Canvas canvas) {
        clipRect.set(0, 0, getWidth(), getHeight());
        clipPath.reset();
        clipPath.addRoundRect(clipRect, cornerRadius, cornerRadius, Path.Direction.CW);
        int save = canvas.save();
        canvas.clipPath(clipPath);
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == glass || child.getVisibility() != VISIBLE) continue;
            int childSave = canvas.save();
            canvas.translate(child.getLeft(), child.getTop());
            child.draw(canvas);
            canvas.restoreToCount(childSave);
        }
        canvas.restoreToCount(save);
    }

    @Nullable
    public ViewGroup getMetaballSamplingSource() {
        return customSource;
    }

    private void refreshMetaballOverlay() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (!opticalEnabled || !isAttachedToWindow() || metaballGroupId == null || metaballGroupId.isEmpty()) {
            LiquidGlassFusionOverlay.detach(metaballOverlay, this);
            metaballOverlay = null;
            return;
        }
        LiquidGlassFusionOverlay next = LiquidGlassFusionOverlay.attach(
                this,
                metaballGroupId,
                metaballAnchorRight,
                metaballMergeGapPx,
                metaballProgress,
                metaballColor
        );
        if (metaballOverlay != null && metaballOverlay != next) {
            LiquidGlassFusionOverlay.detach(metaballOverlay, this);
        }
        metaballOverlay = next;
    }

    private void invalidateMetaballOverlay() {
        if (metaballOverlay != null) metaballOverlay.invalidateSelf();
    }

    /**
     * CLEAR Glass Adaptive Tint Protocol:
     *
     * 根据下方内容的平均亮度动态调节玻璃的色相与不透明度，保障玻璃在任何背景上都清晰可见。
     *
     * 亮内容（brightness > 0.5）：玻璃偏暗，tint=(0,0,0)，alpha 随亮度提高而增大
     * 暗内容（brightness ≤ 0.5）：玻璃偏亮，tint=(1,1,1)，alpha 随亮度降低而增大
     *
     * alpha 范围：0.040（最通透）~ 0.165（极端背景）
     *
     * @param averageBrightness 下方内容的平均亮度 (0.0 ~ 1.0)
     */
    public void updateAdaptiveTint(float averageBrightness) {
        stepAdaptiveTint(averageBrightness, true);
    }

    private boolean stepAdaptiveTint(float averageBrightness, boolean forceSnap) {
        float clamped = Math.max(0f, Math.min(1f, averageBrightness));
        float brightSide = Math.max(0f, (clamped - 0.5f) / 0.5f);
        float darkSide = Math.max(0f, (0.5f - clamped) / 0.5f);
        float brightExtreme = Math.max(0f, (clamped - 0.74f) / 0.26f);
        float darkExtreme = Math.max(0f, (0.26f - clamped) / 0.26f);
        float visibilityBoost = Math.max(brightExtreme, darkExtreme);
        float targetTintAlpha;
        float targetTintRed;
        float targetTintGreen;
        float targetTintBlue;

        if (clamped > 0.5f) {
            // 亮底：整体偏暗，极亮背景下额外抬一点 alpha，避免白底上直接融掉。
            targetTintAlpha = Math.min(0.165f, 0.050f + brightSide * 0.085f + brightExtreme * 0.030f);
            targetTintRed = 0.05f;
            targetTintGreen = 0.06f;
            targetTintBlue = 0.08f;
        } else {
            // 暗底：整体偏亮，纯黑附近再多给一点体积感，但不做刺眼纯白。
            targetTintAlpha = Math.min(0.155f, 0.042f + darkSide * 0.078f + darkExtreme * 0.030f);
            targetTintRed = 0.98f;
            targetTintGreen = 0.985f;
            targetTintBlue = 0.995f;
        }

        float blurBoost = 1f + visibilityBoost * 0.16f;
        float refractionBoost = 1f + visibilityBoost * 0.10f;
        float offsetBoost = 1f + visibilityBoost * 0.08f;
        float dispersionBoost = visibilityBoost * 0.045f;
        float targetBlurRadius = Math.max(0.01f, Math.min(50f, baseBlurRadius * blurBoost));
        float targetRefractionHeight = Math.max(Utils.dp2px(getResources(), 12),
                Math.min(Utils.dp2px(getResources(), 50), baseRefractionHeight * refractionBoost));
        float targetOffsetMagnitude = Math.abs(baseRefractionOffset) * offsetBoost;
        targetOffsetMagnitude = Math.max(Utils.dp2px(getResources(), 20),
                Math.min(Utils.dp2px(getResources(), 120), targetOffsetMagnitude));
        float targetRefractionOffset = -targetOffsetMagnitude;
        float targetDispersion = Math.max(0f, Math.min(1f, baseDispersion + dispersionBoost));

        if (!adaptiveAppearanceInitialized || forceSnap) {
            tintAlpha = targetTintAlpha;
            tintColorRed = targetTintRed;
            tintColorGreen = targetTintGreen;
            tintColorBlue = targetTintBlue;
            blurRadius = targetBlurRadius;
            refractionHeight = targetRefractionHeight;
            refractionOffset = targetRefractionOffset;
            dispersion = targetDispersion;
            adaptiveAppearanceInitialized = true;
            updateConfig();
            return true;
        }

        float transitionFactor = 0.16f;
        float oldTintAlpha = tintAlpha;
        float oldTintRed = tintColorRed;
        float oldTintGreen = tintColorGreen;
        float oldTintBlue = tintColorBlue;
        float oldBlurRadius = blurRadius;
        float oldRefractionHeight = refractionHeight;
        float oldRefractionOffset = refractionOffset;
        float oldDispersion = dispersion;

        tintAlpha = lerp(tintAlpha, targetTintAlpha, transitionFactor);
        tintColorRed = lerp(tintColorRed, targetTintRed, transitionFactor);
        tintColorGreen = lerp(tintColorGreen, targetTintGreen, transitionFactor);
        tintColorBlue = lerp(tintColorBlue, targetTintBlue, transitionFactor);
        blurRadius = lerp(blurRadius, targetBlurRadius, transitionFactor);
        refractionHeight = lerp(refractionHeight, targetRefractionHeight, transitionFactor);
        refractionOffset = lerp(refractionOffset, targetRefractionOffset, transitionFactor);
        dispersion = lerp(dispersion, targetDispersion, transitionFactor);

        snapIfCloseToTarget(targetTintAlpha, targetTintRed, targetTintGreen, targetTintBlue,
                targetBlurRadius, targetRefractionHeight, targetRefractionOffset, targetDispersion);

        boolean changed = Math.abs(tintAlpha - oldTintAlpha) > 0.0005f
                || Math.abs(tintColorRed - oldTintRed) > 0.0005f
                || Math.abs(tintColorGreen - oldTintGreen) > 0.0005f
                || Math.abs(tintColorBlue - oldTintBlue) > 0.0005f
                || Math.abs(blurRadius - oldBlurRadius) > 0.002f
                || Math.abs(refractionHeight - oldRefractionHeight) > 0.01f
                || Math.abs(refractionOffset - oldRefractionOffset) > 0.01f
                || Math.abs(dispersion - oldDispersion) > 0.0008f;
        if (changed) {
            updateConfig();
        }
        return changed;
    }

    private float lerp(float current, float target, float factor) {
        return current + (target - current) * factor;
    }

    private void snapIfCloseToTarget(
            float targetTintAlpha,
            float targetTintRed,
            float targetTintGreen,
            float targetTintBlue,
            float targetBlurRadius,
            float targetRefractionHeight,
            float targetRefractionOffset,
            float targetDispersion
    ) {
        if (Math.abs(tintAlpha - targetTintAlpha) < 0.0008f) tintAlpha = targetTintAlpha;
        if (Math.abs(tintColorRed - targetTintRed) < 0.0008f) tintColorRed = targetTintRed;
        if (Math.abs(tintColorGreen - targetTintGreen) < 0.0008f) tintColorGreen = targetTintGreen;
        if (Math.abs(tintColorBlue - targetTintBlue) < 0.0008f) tintColorBlue = targetTintBlue;
        if (Math.abs(blurRadius - targetBlurRadius) < 0.01f) blurRadius = targetBlurRadius;
        if (Math.abs(refractionHeight - targetRefractionHeight) < 0.02f) refractionHeight = targetRefractionHeight;
        if (Math.abs(refractionOffset - targetRefractionOffset) < 0.02f) refractionOffset = targetRefractionOffset;
        if (Math.abs(dispersion - targetDispersion) < 0.001f) dispersion = targetDispersion;
    }

    /**
     * 获取 LiquidGlassimpl 硬件采样计算的内容亮度
     * @return 0.0~1.0，失败返回 0.5
     */
    public float getContentBrightness() {
        if (glass != null) {
            try {
                return glass.getContentBrightness();
            } catch (Exception ignored) {}
        }
        return 0.5f;
    }

    public String getLastSampleMode() {
        if (glass != null) {
            try {
                return glass.getLastSampleMode();
            } catch (Exception ignored) {}
        }
        return "none";
    }

    public long getLastSampleUptimeMs() {
        if (glass != null) {
            try {
                return glass.getLastSampleUptimeMs();
            } catch (Exception ignored) {}
        }
        return 0L;
    }

    private void updateConfig() {
        if (!opticalEnabled) return;
        LiquidGlass currentGlass = glass;
        if (currentGlass == null) {
            rebuild();
            return;
        }

        int w = getWidth();
        int h = getHeight();
        if (w <= 0) w = Utils.getDeviceWidthPx(context);
        if (h <= 0) h = getResources().getDisplayMetrics().heightPixels;

        config.CORNER_RADIUS_PX = cornerRadius;
        config.SAMPLING_UNDERLAY = samplingUnderlay;
        config.SAMPLING_FOREGROUND = samplingForeground;
        config.SAMPLING_SCREEN_POSITION = samplingScreenPosition;
        config.FLUID_RESTING_TINT = fluidRestingTint;
        config.FLUID_OPTICAL_PROGRESS = fluidOpticalProgress;
        config.FLUID_WIDTH = fluidWidth;
        config.FLUID_HEIGHT = fluidHeight;
        config.FLUID_STRAIN = fluidStrain;
        config.FLUID_SHEAR = fluidShear;
        config.EDGE_ONLY = edgeOnly ? 1f : 0f;
        config.REFRACTION_HEIGHT = refractionHeight;
        config.REFRACTION_OFFSET = refractionOffset;
        config.BLUR_RADIUS = blurRadius;
        config.WIDTH = w;
        config.HEIGHT = h;
        config.DISPERSION = dispersion;
        config.TINT_ALPHA = tintAlpha;
        config.TINT_COLOR_BLUE = tintColorBlue;
        config.TINT_COLOR_GREEN = tintColorGreen;
        config.TINT_COLOR_RED = tintColorRed;
        invalidateMetaballOverlay();
        currentGlass.post(() -> {
            if (glass == currentGlass
                    && currentGlass.getParent() == this
                    && isAttachedToWindow()) {
                currentGlass.updateParameters();
            }
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        LiquidGlassSharedGlow.attach(this);
        refreshMetaballOverlay();
        post(() -> {
            ensureGlass();
            // AndroidView attachment can precede completion of the Compose host tree.
            refreshMetaballOverlay();
        });
        if (adaptiveTintEnabled) {
            adaptiveTintTrackingActive = false;
            Choreographer.getInstance().removeFrameCallback(adaptiveTintFrameCallback);
            lastAdaptiveBrightness = -1f;
            adaptiveTargetBrightness = 0.5f;
            adaptiveFilteredBrightness = 0.5f;
            adaptiveAppearanceInitialized = false;
            lastAdaptiveSampleUptimeMs = -1L;
            lastAdaptiveSampleMode = "none";
            adaptiveTintTrackingActive = true;
            Choreographer.getInstance().postFrameCallback(adaptiveTintFrameCallback);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (exclusiveTouchGesture && getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(false);
        }
        removeCallbacks(menuLongPress);
        externalTouchPressed = false;
        adaptiveTintTrackingActive = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            LiquidGlassFusionOverlay.detach(metaballOverlay, this);
        }
        metaballFusionActive = false;
        metaballOverlay = null;
        glowTrackingActive = false;
        Choreographer.getInstance().removeFrameCallback(adaptiveTintFrameCallback);
        Choreographer.getInstance().removeFrameCallback(glowFrameCallback);
        removeCallbacks(stopGlowTrackingRunnable);
        if (glowAnimator.isRunning()) {
            glowAnimator.cancel();
        }
        glowAlpha = 0f;
        LiquidGlassSharedGlow.detach(this);
        liquidTracker.cancelAnimations();
        setScaleX(1f);
        setScaleY(1f);
        setTranslationX(0f);
        setTranslationY(0f);
        if (onTransformChangedListener != null) {
            onTransformChangedListener.onTransformChanged(1f, 1f, 0f, 0f);
            onTransformChangedListener = null;
        }
        removeGlass();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        invalidateMetaballOverlay();
        if (w != oldw || h != oldh) {
            if (w > 0 && h > 0) {
                float maxPx = h / 2f;
                cornerRadius = Math.min(requestedCornerRadius, maxPx);
                // 首次创建用 rebuild，后续尺寸变化只更新参数避免闪烁
                if (glass == null) {
                    rebuild();
                } else {
                    updateConfig();
                    glass.updateParameters();
                }
            }
        }
    }

    private void rebuild() {
        removeGlass();
        post(this::ensureGlass);
    }

    private void ensureGlass() {
        if (!opticalEnabled || glass != null || !isAttachedToWindow()) return;

        int w = getWidth();
        int h = getHeight();
        if (w <= 0) w = Utils.getDeviceWidthPx(context);
        if (h <= 0) h = getResources().getDisplayMetrics().heightPixels;

        config = new Config();
        config.SAMPLING_UNDERLAY = samplingUnderlay;
        config.SAMPLING_FOREGROUND = samplingForeground;
        config.SAMPLING_SCREEN_POSITION = samplingScreenPosition;
        config.FLUID_RESTING_TINT = fluidRestingTint;
        config.FLUID_OPTICAL_PROGRESS = fluidOpticalProgress;
        config.FLUID_WIDTH = fluidWidth;
        config.FLUID_HEIGHT = fluidHeight;
        config.FLUID_STRAIN = fluidStrain;
        config.FLUID_SHEAR = fluidShear;
        config.EDGE_ONLY = edgeOnly ? 1f : 0f;
        config.SAMPLE_BRIGHTNESS = adaptiveTintEnabled;
        config.configure(new Config.Overrides()
                .noFilter()
                .contrast(0f)
                .whitePoint(0f)
                .chromaMultiplier(1f)
                .blurRadius(blurRadius)
                .cornerRadius(cornerRadius)
                .refractionHeight(refractionHeight)
                .refractionOffset(refractionOffset)
                .tintAlpha(tintAlpha)
                .tintColorRed(tintColorRed)
                .tintColorGreen(tintColorGreen)
                .tintColorBlue(tintColorBlue)
                .dispersion(dispersion)
                .size(w, h)
        );
        glass = new LiquidGlass(getContext(), config);
        LayoutParams lp = new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
        );
        addView(glass, 0, lp);

        ViewGroup source = customSource;
        if (source == null && getParent() instanceof ViewGroup) {
            return;
        }
        glass.init(source);
        glass.setSamplingEnabled(!metaballFusionActive);
    }

    private void removeGlass() {
        if (glass != null) {
            removeView(glass);
            glass = null;
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (!inputEnabled) return false;
        if (exclusiveTouchGesture) trackExclusiveGesture(ev);
        try {
            return dispatchGlassTouchEvent(ev);
        } finally {
            if (exclusiveTouchGesture && (ev.getActionMasked() == MotionEvent.ACTION_UP
                    || ev.getActionMasked() == MotionEvent.ACTION_CANCEL)) {
                releaseExclusiveGesture();
            }
        }
    }

    private boolean exclusiveTouchGesture = false;
    private boolean exclusiveGestureLatched = false;

    /** Opt-in for demonstration surfaces; ordinary buttons still allow list drags. */
    public void setExclusiveTouchGesture(boolean enabled) {
        if (exclusiveTouchGesture == enabled) return;
        exclusiveTouchGesture = enabled;
        releaseExclusiveGesture();
    }

    /** Preview contacts belong to the glass; ordinary controls leave list interception enabled. */
    private void trackExclusiveGesture(android.view.MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN && getParent() != null) {
            // Direction detection is too late for native previews nested inside Compose.
            exclusiveGestureLatched = true;
            getParent().requestDisallowInterceptTouchEvent(true);
        }
    }

    private void releaseExclusiveGesture() {
        if (exclusiveGestureLatched && getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(false);
        }
        exclusiveGestureLatched = false;
    }

    private boolean dispatchGlassTouchEvent(android.view.MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) touchFeedbackTransferred = false;
        if (isLongClickable()) {
            if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                menuDownRawX = ev.getRawX();
                menuDownRawY = ev.getRawY();
                removeCallbacks(menuLongPress);
                postDelayed(menuLongPress, menuLongPressDelay);
            } else if (ev.getActionMasked() == MotionEvent.ACTION_UP ||
                    ev.getActionMasked() == MotionEvent.ACTION_CANCEL ||
                    Math.hypot(ev.getRawX() - menuDownRawX, ev.getRawY() - menuDownRawY)
                            >= menuLongPressSlopDp * getResources().getDisplayMetrics().density) {
                removeCallbacks(menuLongPress);
                // UP still needs View's PREPRESSED state to complete a quick
                // tap inside a scrolling parent.
                if (ev.getActionMasked() != MotionEvent.ACTION_UP) cancelLongPress();
            }
        }
        // 在事件分发给子 View 之前截获，确保点击 Tab 时辉光和形变也能触发
        if (touchEffectEnabled && !externalTouchFeedbackEnabled && !touchFeedbackTransferred && !observerOwnsGesture) {
            switch (ev.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    beginTouchFeedback(ev.getX(), ev.getY(), ev.getRawX(), ev.getRawY());
                    break;
                case android.view.MotionEvent.ACTION_MOVE:
                    moveTouchFeedback(ev.getX(), ev.getY(), ev.getRawX(), ev.getRawY());
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    endTouchFeedback();
                    break;
            }
        }
        if (touchObserver != null) {
            getLocationOnScreen(touchScreenLocation);
            getLocationInWindow(touchWindowLocation);
            boolean owns = touchObserver.onTouch(
                    ev.getRawX() - touchScreenLocation[0] + touchWindowLocation[0],
                    ev.getRawY() - touchScreenLocation[1] + touchWindowLocation[1],
                    ev.getActionMasked());
            if (owns) {
                if (!observerOwnsGesture) {
                    transferTouchFeedback();
                    android.view.MotionEvent cancel = android.view.MotionEvent.obtain(ev);
                    cancel.setAction(android.view.MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(cancel);
                    cancel.recycle();
                }
                observerOwnsGesture = ev.getActionMasked() != android.view.MotionEvent.ACTION_UP
                        && ev.getActionMasked() != android.view.MotionEvent.ACTION_CANCEL;
                return true;
            }
        }
        observerOwnsGesture = false;
        return super.dispatchTouchEvent(ev);
    }

    /** Keep the optical surface alive without letting hidden peers intercept touches. */
    public void setInputEnabled(boolean enabled) {
        inputEnabled = enabled;
    }

    public void setInteractiveContentEnabled(boolean enabled) {
        interactiveContentEnabled = enabled;
    }

    @Override
    public boolean onInterceptTouchEvent(android.view.MotionEvent event) {
        // Decorative Compose children can still own pointer handlers internally.
        // A button owns its complete gesture; an editable panel delegates to its content.
        return !interactiveContentEnabled || super.onInterceptTouchEvent(event);
    }

    /** 基于触摸位移的形变（增量式，不受坐标系影响） */
    private void applyTouchDeformation(float rawX, float rawY) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float density = getResources().getDisplayMetrics().density;

        // 从起始点算位移，不受视图位置/平移影响
        float dx = rawX - touchStartRawX;
        float dy = rawY - touchStartRawY;

        // 使用固定参考尺寸归一化，确保不同宽度的视图形变幅度一致
        float refPx = 60f * density;

        // 位移量除以参考尺寸归一化为偏移因子 (-1 ~ 1)
        float nx = Math.max(-1f, Math.min(1f, dx / refPx));
        float ny = Math.max(-1f, Math.min(1f, dy / refPx));
        float absNx = Math.abs(nx);
        float absNy = Math.abs(ny);
        float total = absNx + absNy;

        // ── 位移：往手指滑动方向推移 ──
        float maxTransDp = 6f;
        float maxTransPx = maxTransDp * density;
        liquidTracker.animateTranslation(nx * maxTransPx, ny * maxTransPx);

        // ── 拉伸：主方向拉伸，垂直方向压缩 ──
        if (total > 0.01f) {
            float wx = absNx / total;
            float wy = absNy / total;
            float stretchPx = 3f * density * Math.min(1f, total);
            float scaleX = pressedScale(w) + stretchPx / w * (wx - wy * 0.4f);
            float scaleY = pressedScale(h) + stretchPx / h * (wy - wx * 0.4f);

            liquidTracker.animateGentleScale(
                    Math.max(0.96f, Math.min(1.18f, scaleX)),
                    Math.max(0.96f, Math.min(1.18f, scaleY))
            );
        } else {
            liquidTracker.animateGentleScale(pressedScale(w), pressedScale(h));
        }
    }

    private float pressedScale(float dimensionPx) {
        float expansion = 4f * getResources().getDisplayMetrics().density;
        return 1f + Math.min(0.12f, expansion / Math.max(dimensionPx, 1f));
    }

    /** Near-field attraction strength; projected glass area supplies the mass. */
    public void setGravityStrength(float strength) {
        float next = Float.isFinite(strength) ? Math.max(0f, Math.min(4f, strength)) : 1f;
        if (gravityStrength == next) return;
        gravityStrength = next;
        invalidateMetaballOverlay();
    }

    float getGravityStrength() { return gravityStrength; }
    void setGlassAttraction(float x, float y) { liquidTracker.animateAttraction(x, y); }
    float getGlassAttractionX() { return liquidTracker.getAttractionX(); }
    float getGlassAttractionY() { return liquidTracker.getAttractionY(); }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(android.view.MotionEvent e) {
        if (!draggableEnabled && !touchEffectEnabled) return super.onTouchEvent(e);
        // touchEffect（辉光、形变）已在 dispatchTouchEvent 中处理，
        // 这里仅处理 draggableEnabled 拖拽逻辑，避免重复启动 animator/frameCallback 造成时序冲突
        if (elasticEnabled && !touchEffectEnabled) liquidTracker.applyMovement(e);

        switch (e.getActionMasked()) {
            case android.view.MotionEvent.ACTION_DOWN:
                if (draggableEnabled) {
                    downX = e.getRawX();
                    downY = e.getRawY();
                    startTx = getTranslationX();
                    startTy = getTranslationY();
                    return true;
                }
                break;
            case android.view.MotionEvent.ACTION_MOVE: {
                if (draggableEnabled) {
                    float dx = e.getRawX() - downX;
                    float dy = e.getRawY() - downY;
                    float tx = startTx + dx;
                    float ty = startTy + dy;

                    ViewGroup parent = (ViewGroup) getParent();
                    if (parent != null) {
                        int pw = parent.getWidth(), ph = parent.getHeight();
                        int w = getWidth(), h = getHeight();
                        if (pw > 0 && ph > 0 && w > 0 && h > 0) {
                            float minX = -getLeft();
                            float maxX = pw - getLeft() - w;
                            float minY = -getTop();
                            float maxY = ph - getTop() - h;
                            if (tx < minX) tx = minX;
                            if (tx > maxX) tx = maxX;
                            if (ty < minY) ty = minY;
                            if (ty > maxY) ty = maxY;
                        }
                    }
                    setTranslationX(tx);
                    setTranslationY(ty);
                    return true;
                }
                break;
            }
            case android.view.MotionEvent.ACTION_UP:
            case android.view.MotionEvent.ACTION_CANCEL:
                if (draggableEnabled) return true;
                break;
        }

        boolean superResult = super.onTouchEvent(e);
        return touchEffectEnabled || superResult;
    }
}
