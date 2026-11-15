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

package com.qmdeve.liquidglass;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.qmdeve.liquidglass.impl.Impl;
import com.qmdeve.liquidglass.impl.LiquidGlassimpl;

import java.lang.ref.WeakReference;

@SuppressLint("ViewConstructor")
public class LiquidGlass extends FrameLayout {
    private Impl impl;
    private ViewGroup target;
    private ViewTreeObserver sourceObserver;
    private ViewTreeObserver hostObserver;
    private boolean samplingEnabled = true;
    private final Config config;

    private static class PreDrawListener implements ViewTreeObserver.OnPreDrawListener {
        private final WeakReference<LiquidGlass> liquidGlassRef;
        private final boolean refreshHost;

        public PreDrawListener(LiquidGlass liquidGlass, boolean refreshHost) {
            this.liquidGlassRef = new WeakReference<>(liquidGlass);
            this.refreshHost = refreshHost;
        }

        @Override
        public boolean onPreDraw() {
            LiquidGlass liquidGlass = liquidGlassRef.get();
            if (liquidGlass != null && liquidGlass.samplingEnabled && liquidGlass.isShown()
                    && liquidGlass.impl != null) {
                liquidGlass.impl.onPreDraw();
                if (refreshHost && liquidGlass.target != null &&
                        liquidGlass.target.getRootView() != liquidGlass.getRootView()) {
                    liquidGlass.invalidate();
                }
            }
            return true;
        }
    }

    private static class RoundRectOutlineProvider extends ViewOutlineProvider {
        private final float cornerRadius;

        public RoundRectOutlineProvider(float cornerRadius) {
            this.cornerRadius = cornerRadius;
        }

        @Override
        public void getOutline(View v, Outline o) {
            o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), cornerRadius);
        }
    }

    private final PreDrawListener preDrawListener = new PreDrawListener(this, true);
    private final PreDrawListener hostPreDrawListener = new PreDrawListener(this, false);

    public LiquidGlass(Context c, Config config) {
        super(c);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        this.config = config;
        init();
    }

    public void init(ViewGroup target) {
        if (target == null) return;
        if (this.target == target && impl != null) {
            addPreDrawListener();
            updateParameters();
            return;
        }
        if (this.target != null) removePreDrawListener();
        if (impl != null) impl.dispose();

        this.target = target;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            impl = new LiquidGlassimpl(this, target, config);
            // A late bind does not trigger onSizeChanged when layout is unchanged.
            if (getWidth() > 0 && getHeight() > 0) {
                impl.onSizeChanged(getWidth(), getHeight());
            }
            addPreDrawListener();
            requestLayout();
            invalidate();
        } else {
            removePreDrawListener();
        }
    }

    private void init() {
        setWillNotDraw(false);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        updateOutlineProvider();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (impl != null) impl.draw(canvas);
    }

    public void updateParameters() {
        if (impl != null && samplingEnabled) {
            impl.onPreDraw();
            invalidate();
        }
        updateOutlineProvider();
    }

    public void setSamplingEnabled(boolean enabled) {
        if (samplingEnabled == enabled) return;
        samplingEnabled = enabled;
        if (enabled) {
            addPreDrawListener();
            updateParameters();
        } else {
            removePreDrawListener();
        }
    }

    /** 获取硬件采样计算的内容亮度 (0.0~1.0) */
    public float getContentBrightness() {
        if (impl != null) {
            return impl.getContentBrightness();
        }
        return 0.5f;
    }

    public String getLastSampleMode() {
        if (impl != null) {
            return impl.getLastSampleMode();
        }
        return "none";
    }

    public long getLastSampleUptimeMs() {
        if (impl != null) {
            return impl.getLastSampleUptimeMs();
        }
        return 0L;
    }

    private void updateOutlineProvider() {
        if (config.CORNER_RADIUS_PX > 0) {
            setOutlineProvider(new RoundRectOutlineProvider(config.CORNER_RADIUS_PX));
            // 关闭 clipToOutline，避免硬件层缩放时内容被裁切。
            // 圆角由 LiquidGlassView 的 clipPath 或 outline 控制。
            setClipToOutline(false);
            invalidateOutline();
        } else {
            setOutlineProvider(null);
            setClipToOutline(false);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (impl != null) impl.onSizeChanged(w, h);
        updateOutlineProvider();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        addPreDrawListener();
    }

    @Override
    protected void onDetachedFromWindow() {
        removePreDrawListener();
        if (impl != null) impl.dispose();
        super.onDetachedFromWindow();
    }

    private void addPreDrawListener() {
        if (!samplingEnabled || target == null) return;
        ViewTreeObserver source = target.getViewTreeObserver();
        if (sourceObserver != source) {
            if (sourceObserver != null && sourceObserver.isAlive())
                sourceObserver.removeOnPreDrawListener(preDrawListener);
            source.addOnPreDrawListener(preDrawListener);
            sourceObserver = source;
        }
        // Popup 动画只重绘菜单窗口，底层页面静止时也必须更新采样位置。
        ViewTreeObserver host = isAttachedToWindow() ? getViewTreeObserver() : null;
        if (host == source) host = null;
        if (hostObserver != host) {
            if (hostObserver != null && hostObserver.isAlive())
                hostObserver.removeOnPreDrawListener(hostPreDrawListener);
            hostObserver = host;
            if (hostObserver != null) hostObserver.addOnPreDrawListener(hostPreDrawListener);
        }
    }

    private void removePreDrawListener() {
        if (sourceObserver != null && sourceObserver.isAlive())
            sourceObserver.removeOnPreDrawListener(preDrawListener);
        if (hostObserver != null && hostObserver.isAlive())
            hostObserver.removeOnPreDrawListener(hostPreDrawListener);
        sourceObserver = null;
        hostObserver = null;
    }
}
