package com.qmdeve.liquidglass.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.RuntimeShader;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.RequiresApi;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.WeakHashMap;

/** Draws only the smooth-min bridge between grouped glass end caps. */
@RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
final class LiquidGlassMetaballOverlay extends View {
    private static final String SHADER_SOURCE =
            "uniform float2 centerA;\n" +
            "uniform float2 centerB;\n" +
            "uniform float radiusA;\n" +
            "uniform float radiusB;\n" +
            "uniform float smoothing;\n" +
            "uniform float opacity;\n" +
            "uniform float3 tint;\n" +
            "float smoothMin(float a, float b, float k) {\n" +
            "  float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);\n" +
            "  return mix(b, a, h) - k * h * (1.0 - h);\n" +
            "}\n" +
            "half4 main(float2 p) {\n" +
            "  float da = length(p - centerA) - radiusA;\n" +
            "  float db = length(p - centerB) - radiusB;\n" +
            "  float hardDistance = min(da, db);\n" +
            "  float mergedDistance = smoothMin(da, db, smoothing);\n" +
            "  if (hardDistance < -0.8 || mergedDistance >= 0.0) return half4(0.0);\n" +
            "  float join = smoothstep(-0.8, 2.5, hardDistance);\n" +
            "  float body = 1.0 - smoothstep(-2.0, 0.0, mergedDistance);\n" +
            "  float edge = 1.0 - smoothstep(0.0, 2.5, abs(mergedDistance));\n" +
            "  float alpha = opacity * join * body;\n" +
            "  half3 color = mix(half3(tint), half3(1.0), edge * 0.28);\n" +
            "  return half4(color * alpha, alpha);\n" +
            "}\n";

    private static final WeakHashMap<ViewGroup, LiquidGlassMetaballOverlay> OVERLAYS =
            new WeakHashMap<>();

    private final ViewGroup host;
    private final ArrayList<Peer> peers = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RuntimeShader shader;
    private final RectF drawBounds = new RectF();
    private final int[] hostLocation = new int[2];
    private final int[] peerLocation = new int[2];

    static LiquidGlassMetaballOverlay attach(
            LiquidGlassView view,
            String groupId,
            boolean anchorRight,
            float mergeGapPx,
            float progress,
            int tintColor
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || groupId == null
                || groupId.isEmpty()
                || !view.isAttachedToWindow()) {
            return null;
        }

        View rootView = view.getRootView();
        View content = rootView.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return null;
        ViewGroup host = (ViewGroup) content;

        LiquidGlassMetaballOverlay overlay = OVERLAYS.get(host);
        if (overlay == null) {
            overlay = new LiquidGlassMetaballOverlay(host.getContext(), host);
            OVERLAYS.put(host, overlay);
            host.addView(
                    overlay,
                    new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                    )
            );
            overlay.bringToFront();
        }
        overlay.updatePeer(view, groupId, anchorRight, mergeGapPx, progress, tintColor);
        return overlay;
    }

    static void detach(LiquidGlassMetaballOverlay overlay, LiquidGlassView view) {
        if (overlay == null) return;
        overlay.removePeer(view);
        if (overlay.peers.isEmpty()) {
            OVERLAYS.remove(overlay.host);
            overlay.host.removeView(overlay);
        } else {
            overlay.invalidate();
        }
    }

    private LiquidGlassMetaballOverlay(Context context, ViewGroup host) {
        super(context);
        this.host = host;
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        shader = new RuntimeShader(SHADER_SOURCE);
        paint.setShader(shader);
    }

    private void updatePeer(
            LiquidGlassView view,
            String groupId,
            boolean anchorRight,
            float mergeGapPx,
            float progress,
            int tintColor
    ) {
        removeDetachedPeers();
        for (Peer peer : peers) {
            if (peer.view.get() == view) {
                peer.groupId = groupId;
                peer.anchorRight = anchorRight;
                peer.mergeGapPx = Math.max(0f, mergeGapPx);
                peer.progress = clamp(progress);
                peer.tintColor = tintColor;
                invalidate();
                return;
            }
        }
        peers.add(new Peer(view, groupId, anchorRight, mergeGapPx, progress, tintColor));
        invalidate();
    }

    private void removePeer(LiquidGlassView view) {
        Iterator<Peer> iterator = peers.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().view.get() == view) iterator.remove();
        }
    }

    private void removeDetachedPeers() {
        Iterator<Peer> iterator = peers.iterator();
        while (iterator.hasNext()) {
            LiquidGlassView view = iterator.next().view.get();
            if (view == null || !view.isAttachedToWindow()) iterator.remove();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!canvas.isHardwareAccelerated() || peers.size() < 2) return;

        removeDetachedPeers();
        for (int i = 0; i < peers.size(); i++) {
            Peer first = peers.get(i);
            if (!first.anchorRight || first.progress <= 0f) continue;
            LiquidGlassView firstView = first.view.get();
            if (firstView == null || firstView.getWidth() <= 0 || firstView.getHeight() <= 0) continue;

            for (int j = 0; j < peers.size(); j++) {
                Peer second = peers.get(j);
                if (second.anchorRight
                        || second.progress <= 0f
                        || !first.groupId.equals(second.groupId)) {
                    continue;
                }
                LiquidGlassView secondView = second.view.get();
                if (secondView == null
                        || secondView.getWidth() <= 0
                        || secondView.getHeight() <= 0) {
                    continue;
                }
                drawBridge(canvas, first, firstView, second, secondView);
            }
        }
    }

    private void drawBridge(
            Canvas canvas,
            Peer first,
            LiquidGlassView firstView,
            Peer second,
            LiquidGlassView secondView
    ) {
        host.getLocationOnScreen(hostLocation);
        firstView.getLocationOnScreen(peerLocation);
        float firstLeft = peerLocation[0] - hostLocation[0];
        float firstTop = peerLocation[1] - hostLocation[1];
        float firstScaleX = Math.abs(firstView.getScaleX());
        float firstScaleY = Math.abs(firstView.getScaleY());
        float firstWidth = firstView.getWidth() * firstScaleX;
        float firstHeight = firstView.getHeight() * firstScaleY;
        float radiusA = firstHeight * 0.5f;
        float centerAX = firstLeft + firstWidth - radiusA;
        float centerAY = firstTop + firstHeight * 0.5f;

        secondView.getLocationOnScreen(peerLocation);
        float secondLeft = peerLocation[0] - hostLocation[0];
        float secondTop = peerLocation[1] - hostLocation[1];
        float secondScaleX = Math.abs(secondView.getScaleX());
        float secondScaleY = Math.abs(secondView.getScaleY());
        float secondWidth = secondView.getWidth() * secondScaleX;
        float secondHeight = secondView.getHeight() * secondScaleY;
        float radiusB = secondHeight * 0.5f;
        float centerBX = secondLeft + radiusB;
        float centerBY = secondTop + secondHeight * 0.5f;

        float centerDistance = (float) Math.hypot(centerBX - centerAX, centerBY - centerAY);
        float mergeGap = Math.min(first.mergeGapPx, second.mergeGapPx);
        if (centerDistance <= radiusA + radiusB
                || centerDistance - radiusA - radiusB >= mergeGap
                || radiusA <= 0f
                || radiusB <= 0f) {
            return;
        }

        float smoothing = Math.max(1f, mergeGap * 2f);
        float padding = smoothing * 0.5f;
        float left = Math.min(centerAX + radiusA, centerBX - radiusB) - padding;
        float right = Math.max(centerAX + radiusA, centerBX - radiusB) + padding;
        float top = Math.min(centerAY - radiusA, centerBY - radiusB) - padding;
        float bottom = Math.max(centerAY + radiusA, centerBY + radiusB) + padding;
        drawBounds.set(left, top, right, bottom);

        float opacity = 0.22f * Math.min(first.progress, second.progress);
        int tintColor = second.tintColor != Color.WHITE ? second.tintColor : first.tintColor;
        shader.setFloatUniform("centerA", centerAX, centerAY);
        shader.setFloatUniform("centerB", centerBX, centerBY);
        shader.setFloatUniform("radiusA", radiusA);
        shader.setFloatUniform("radiusB", radiusB);
        shader.setFloatUniform("smoothing", smoothing);
        shader.setFloatUniform("opacity", opacity);
        shader.setFloatUniform(
                "tint",
                Color.red(tintColor) / 255f,
                Color.green(tintColor) / 255f,
                Color.blue(tintColor) / 255f
        );
        canvas.drawRect(drawBounds, paint);
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static final class Peer {
        final WeakReference<LiquidGlassView> view;
        String groupId;
        boolean anchorRight;
        float mergeGapPx;
        float progress;
        int tintColor;

        Peer(
                LiquidGlassView view,
                String groupId,
                boolean anchorRight,
                float mergeGapPx,
                float progress,
                int tintColor
        ) {
            this.view = new WeakReference<>(view);
            this.groupId = groupId;
            this.anchorRight = anchorRight;
            this.mergeGapPx = Math.max(0f, mergeGapPx);
            this.progress = clamp(progress);
            this.tintColor = tintColor;
        }
    }
}
