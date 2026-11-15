package com.qmdeve.liquidglass.widget;

import android.graphics.Matrix;
import android.os.Build;
import android.view.View;

import java.util.Map;
import java.util.WeakHashMap;

/** 公共触点光场：只共享光照，不转发触摸事件或形变；注册表不持有控件。 */
final class LiquidGlassSharedGlow {
    private static final WeakHashMap<LiquidGlassView, Boolean> receivers = new WeakHashMap<>();
    private static final WeakHashMap<LiquidGlassView, Boolean> sources = new WeakHashMap<>();
    // 所有读写均来自 UI 线程；复用坐标转换，避免辉光每帧分配。
    private static final Matrix targetMatrix = new Matrix();
    private static final Matrix inverse = new Matrix();
    private static final Matrix sourceMatrix = new Matrix();
    private static final float[] point = new float[2];
    private static final float[] scratch = new float[4];
    private static final int[] location = new int[2];

    static void attach(LiquidGlassView view) {
        receivers.put(view, false);
    }

    static void detach(LiquidGlassView view) {
        receivers.remove(view);
        sources.remove(view);
        refresh();
    }

    static void changed(LiquidGlassView view) {
        if (!receivers.containsKey(view)) return;
        if (view.getOwnTouchGlowStrength() > 0.001f) sources.put(view, true);
        else sources.remove(view);
        refresh();
    }

    private static void refresh() {
        for (Map.Entry<LiquidGlassView, Boolean> entry : receivers.entrySet()) {
            LiquidGlassView target = entry.getKey();
            resolve(target, scratch);
            boolean lit = scratch[2] > 0.001f;
            // 只刷新光场覆盖的玻璃，以及上一帧需要清除辉光的玻璃。
            if (lit || entry.getValue()) target.invalidateSharedTouchGlow();
            entry.setValue(lit);
        }
    }

    /** 输出接收控件局部坐标中的 x、y、强度、半径。 */
    static void resolve(LiquidGlassView target, float[] result) {
        result[0] = result[1] = result[2] = 0f;
        result[3] = 1f;
        if (!target.isShown() || target.getWidth() <= 0 || target.getHeight() <= 0
                || target.getTouchGlowIntensity() <= 0f) return;
        globalMatrix(target, targetMatrix);
        if (!targetMatrix.invert(inverse)) return;
        float best = 0f;
        for (LiquidGlassView source : sources.keySet()) {
            if (!source.isShown()
                    || source.getApplicationWindowToken() != target.getApplicationWindowToken()) continue;
            float strength = source.getOwnTouchGlowAlpha()
                    * Math.min(source.getTouchGlowIntensity(), target.getTouchGlowIntensity());
            if (strength <= 0.001f) continue;
            globalMatrix(source, sourceMatrix);
            point[0] = source.getOwnTouchGlowX();
            point[1] = source.getOwnTouchGlowY();
            sourceMatrix.mapPoints(point);
            inverse.mapPoints(point);
            // 半径保持物理尺寸，不随玻璃按压缩放而放大。
            float radius = inverse.mapRadius(source.getTouchGlowRadiusPx());
            float dx = Math.max(0f, Math.max(-point[0], point[0] - target.getWidth()));
            float dy = Math.max(0f, Math.max(-point[1], point[1] - target.getHeight()));
            float distance = (float) Math.hypot(dx, dy);
            if (radius <= 0f || distance >= radius) continue;
            float score = strength * (1f - distance / radius);
            // 多个触点同时存在时取主要光源，避免重叠加亮突破菜单的强度上限。
            if (score > best) {
                best = score;
                result[0] = point[0];
                result[1] = point[1];
                result[2] = strength;
                result[3] = radius;
            }
        }
    }

    private static void globalMatrix(View view, Matrix result) {
        result.reset();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            view.transformMatrixToGlobal(result);
        } else {
            // 旧系统使用屏幕位置与自身变换，毛玻璃降级仍能共享辉光。
            view.getLocationOnScreen(location);
            result.set(view.getMatrix());
            point[0] = point[1] = 0f;
            result.mapPoints(point);
            result.postTranslate(location[0] - point[0], location[1] - point[1]);
        }
    }
}
