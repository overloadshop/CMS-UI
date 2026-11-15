package com.qmdeve.liquidglass.widget;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.RequiresApi;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.WeakHashMap;

/** One window-local optical surface; peers retain their own layout and input. */
@RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
final class LiquidGlassFusionOverlay extends Drawable {
    private static final String SHADER_SOURCE =
            "uniform shader content;\n" +
            "uniform float4 shapeA;\n" +
            "uniform float4 shapeB;\n" +
            "uniform float2 cornerRadius;\n" +
            "uniform float smoothing;\n" +
            "uniform float2 bodyResponse;\n" +
            "uniform float2 highlightWidth;\n" +
            "uniform float3 tintA;\n" +
            "uniform float3 tintB;\n" +
            "uniform float tintAlphaA;\n" +
            "uniform float tintAlphaB;\n" +
            "uniform float density;\n" +
            "uniform float emergence;\n" +
            "uniform float4 touchA;\n" +
            "uniform float4 touchB;\n" +
            "float touchBloom(float2 p, float4 touch) {\n" +
            "  if (touch.z <= 0.001) return 0.0;\n" +
            "  float r = length(p - touch.xy) / max(1.0, touch.w);\n" +
            // 与单体玻璃使用相同的七段径向光照，融合前后不改变亮度曲线。
            "  float bloom = r < 0.08 ? mix(1.0, 210.0/255.0, r/0.08)\n" +
            "    : r < 0.20 ? mix(210.0/255.0, 150.0/255.0, (r-0.08)/0.12)\n" +
            "    : r < 0.40 ? mix(150.0/255.0, 85.0/255.0, (r-0.20)/0.20)\n" +
            "    : r < 0.65 ? mix(85.0/255.0, 40.0/255.0, (r-0.40)/0.25)\n" +
            "    : r < 0.85 ? mix(40.0/255.0, 15.0/255.0, (r-0.65)/0.20)\n" +
            "    : mix(15.0/255.0, 0.0, clamp((r-0.85)/0.15, 0.0, 1.0));\n" +
            "  return bloom * touch.z;\n" +
            "}\n" +
            "float sdRoundRect(float2 p, float4 shape, float radius) {\n" +
            "  float2 halfSize = shape.zw;\n" +
            "  radius = clamp(radius, 0.0, min(halfSize.x, halfSize.y));\n" +
            "  float2 q = abs(p - shape.xy) - (halfSize - float2(radius));\n" +
            "  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;\n" +
            "}\n" +
            "float2 gradRoundRect(float2 p, float4 shape, float radius) {\n" +
            "  radius = clamp(radius, 0.0, min(shape.z, shape.w));\n" +
            "  float2 local = p - shape.xy;\n" +
            "  float2 q = abs(local) - (shape.zw - float2(radius));\n" +
            "  float2 outside = max(q, 0.0);\n" +
            "  float len = length(outside);\n" +
            "  float2 direction = float2(local.x < 0.0 ? -1.0 : 1.0, local.y < 0.0 ? -1.0 : 1.0);\n" +
            "  if (len > 0.001) return direction * outside / len;\n" +
            "  return direction * (q.x > q.y ? float2(1.0, 0.0) : float2(0.0, 1.0));\n" +
            "}\n" +
            "float smoothMin(float a, float b, float k) {\n" +
            "  if (k <= 0.001) return min(a, b);\n" +
            "  float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);\n" +
            "  return mix(b, a, h) - k * h * (1.0 - h);\n" +
            "}\n" +
            "float neckSmoothing() {\n" +
            "  return smoothing * smoothstep(0.08, 0.70, emergence);\n" +
            "}\n" +
            // 仅保留连接附近的轻微表面张力形变，主体轮廓保持稳定。
            "float3 bodyField(float2 p, float4 shape, float radius, float side, float response, float neck) {\n" +
            "  float sx = max(8.0 * density, min(shape.z, shape.w) * 0.85);\n" +
            "  float sy = max(density, shape.w * 0.80);\n" +
            "  float u = (p.x - shape.x) * side - shape.z;\n" +
            "  float v = p.y - shape.y;\n" +
            "  float influence = exp(-0.5 * (u*u/(sx*sx) + v*v/(sy*sy)));\n" +
            "  float pull = neck * response;\n" +
            "  float2 warped = p - float2(side * pull * influence, 0.0);\n" +
            "  float2 g = gradRoundRect(warped, shape, radius);\n" +
            // 链式求导保留拉伸后的真实方向，不能继续使用原始圆角的法线。
            "  float2 gradient = float2(g.x * (1.0 + pull * influence * u/(sx*sx)),\n" +
            "      g.y + g.x * side * pull * influence * v/(sy*sy));\n" +
            "  return float3(sdRoundRect(warped, shape, radius), gradient);\n" +
            "}\n" +
            "float2 safeNormalize(float2 v) {\n" +
            "  float len = length(v);\n" +
            "  return len > 0.001 ? v / len : float2(0.0, -1.0);\n" +
            "}\n" +
            "half4 main(float2 p) {\n" +
            "  float neck = neckSmoothing();\n" +
            "  float3 fieldA = bodyField(p, shapeA, cornerRadius.x, 1.0, bodyResponse.x, neck);\n" +
            "  float3 fieldB = bodyField(p, shapeB, cornerRadius.y, -1.0, bodyResponse.y, neck);\n" +
            "  float da = fieldA.x;\n" +
            "  float growth = smoothstep(0.08, 0.55, emergence);\n" +
            "  float db = fieldB.x + (1.0 - growth) * 2.0 * max(shapeB.z, shapeB.w);\n" +
            "  float d = smoothMin(da, db, neck);\n" +
            "  if (d > 1.0) return half4(0.0);\n" +
            "  float coverage = 1.0 - smoothstep(0.0, 1.0, d);\n" +
            "  float weightA = neck > 0.001\n" +
            "      ? clamp(0.5 + 0.5 * (db - da) / neck, 0.0, 1.0)\n" +
            "      : step(da, db);\n" +
            // Tint diffusion has a wider footprint than the narrow geometric neck.
            "  float tintWidth = max(8.0 * density, smoothing * 2.0);\n" +
            "  float tintWeight = smoothstep(-tintWidth, tintWidth, db - da);\n" +
            // The lobe acquires its own dye after leaving the donor. It has
            // one optical surface throughout, including when fully submerged.
            "  float dye = smoothstep(0.35, 0.90, emergence);\n" +
            "  float3 lobeTint = mix(tintA, tintB, dye);\n" +
            "  float lobeAlpha = mix(tintAlphaA, tintAlphaB, dye);\n" +
            "  float3 tint = mix(lobeTint, tintA, tintWeight);\n" +
            "  float tintAlpha = mix(lobeAlpha, tintAlphaA, tintWeight);\n" +
            "  float2 gradient = mix(fieldB.yz, fieldA.yz, weightA);\n" +
            "  float2 contourNormal = gradient / sqrt(dot(gradient, gradient) + 0.04);\n" +
            // 联合外轮廓承载连续的球冠高度场：细颈沿横向下凹、纵向上拱。
            // 用高度导数构造三维法线，避免在两个原始折射环之间插值。
            "  float thickness = 12.0 * density;\n" +
            "  float q = max(0.0, thickness - max(0.0, -d));\n" +
            "  float height = sqrt(max(0.04*density*density, thickness*thickness - q*q));\n" +
            "  float3 liquidNormal = normalize(float3(gradient * q / height, 1.0));\n" +
            "  float2 junction = float2((shapeA.x+shapeA.z+shapeB.x-shapeB.z)*0.5,\n" +
            "      (shapeA.y+shapeB.y)*0.5);\n" +
            "  float spreadX = max(8.0*density, min(shapeA.w,shapeB.w)*1.15);\n" +
            "  float spreadY = max(density, min(shapeA.w,shapeB.w)*0.95);\n" +
            "  float2 local = (p-junction)/float2(spreadX,spreadY);\n" +
            "  float contact = neck/(neck+2.0*density) * exp(-0.5*dot(local,local));\n" +
            // 远离细颈维持单体玻璃的光学；接近细颈逐渐交给连续液面。
            "  float depth = clamp(-d / thickness, 0.0, 1.0);\n" +
            "  float curve = 1.0 - sqrt(max(0.0, 1.0 - (1.0 - depth) * (1.0 - depth)));\n" +
            "  float2 singleOffset = -contourNormal * curve * 40.0 * density;\n" +
            "  float3 ray = refract(float3(0.0,0.0,-1.0), liquidNormal, 1.0/1.45);\n" +
            "  float travel = 28.0*density + height*0.6;\n" +
            "  float2 liquidOffset = ray.xy * travel / max(0.15,-ray.z);\n" +
            "  float2 refracted = p + mix(singleOffset,liquidOffset,contact);\n" +
            "  float2 normal = mix(contourNormal,liquidNormal.xy,contact);\n" +
            "  float2 dispersion = normal * 0.3 * density;\n" +
            "  half4 center = content.eval(refracted);\n" +
            "  half4 redSample = content.eval(refracted + dispersion);\n" +
            "  half4 blueSample = content.eval(refracted - dispersion);\n" +
            "  half3 color = half3(redSample.r, center.g, blueSample.b);\n" +
            "  color = mix(color, half3(tint), tintAlpha);\n" +
            // The warped smooth-min field is not a unit distance field at the neck.
            "  float edgeDistance = max(0.0, -d) / max(length(gradient), 0.10);\n" +
            "  float width = mix(highlightWidth.y, highlightWidth.x, weightA);\n" +
            "  float edge = width > 0.0 ? 1.0 - smoothstep(0.0, width, edgeDistance) : 0.0;\n" +
            "  float rim = width > 0.0 ? 1.0 - smoothstep(0.0, width * 0.72, edgeDistance) : 0.0;\n" +
            // 同一光源平滑转动联合表面的原高光轴，松手后回到上下光照。
            "  float4 sharedLight = mix(touchB, touchA, weightA);\n" +
            "  float4 lightShape = mix(shapeB, shapeA, weightA);\n" +
            "  float2 lightDelta = sharedLight.xy - lightShape.xy;\n" +
            "  float lightDistance = length(max(abs(lightDelta)-lightShape.zw,float2(0.0)));\n" +
            "  float lightReach = clamp(1.0-lightDistance/max(1.0,sharedLight.w),0.0,1.0);\n" +
            "  float lightAngle = atan(lightDelta.x,abs(lightDelta.y)+min(lightShape.z,lightShape.w))\n" +
            "      * 0.72 * sharedLight.z * lightReach * lightReach;\n" +
            "  float2 lightAxis = float2(sin(lightAngle),-cos(lightAngle));\n" +
            "  float directional = pow(abs(dot(normal,lightAxis)), 3.0);\n" +
            "  float luminance = dot(color, half3(0.2126, 0.7152, 0.0722));\n" +
            "  float bright = smoothstep(0.55, 0.90, luminance);\n" +
            // 连接处的亮暗由三维曲率及上下光源决定，降低固定白边的亮桥感。
            "  float topSpec = pow(max(0.0,dot(liquidNormal,float3(lightAxis*0.60,0.80))),24.0);\n" +
            "  float bottomSpec = pow(max(0.0,dot(liquidNormal,float3(-lightAxis*0.60,0.80))),24.0);\n" +
            "  float fresnel = pow(1.0-liquidNormal.z,5.0);\n" +
            "  float rimLight = 1.0-contact*0.70;\n" +
            "  color = mix(color, half3(0.0), edge * bright * (0.055 + 0.08 * directional));\n" +
            "  color = mix(color, half3(1.0), edge * (0.10 + 0.30 * directional) * rimLight);\n" +
            "  color = mix(color, half3(1.0), rim * (0.08 + 0.32 * directional + 0.12 * bright) * rimLight);\n" +
            "  color = mix(color,half3(1.0),contact*(0.14*(topSpec+bottomSpec)+0.06*fresnel));\n" +
            // 联合表面只照亮一次，光斑也能跨过连接处的细颈。
            "  float glow = mix(touchBloom(p, touchB), touchBloom(p, touchA), weightA);\n" +
            // 联合轮廓也接收公共光场，连接处没有两条独立亮边。
            "  float edgeGlow = glow * rim * 0.46;\n" +
            "  color = mix(color, half3(1.0), clamp(edgeGlow, 0.0, 0.46));\n" +
            "  color = min(half3(1.0), color + half3(glow));\n" +
            "  float alpha = center.a * coverage;\n" +
            "  return half4(color * alpha, alpha);\n" +
            "}\n";

    private static final WeakHashMap<ViewGroup, LiquidGlassFusionOverlay> OVERLAYS =
            new WeakHashMap<>();
    private static final String TAG = "LiquidGlassFusion";

    private final ViewGroup host;
    private final ViewTreeObserver hostObserver;
    private final ArrayList<Peer> peers = new ArrayList<>();
    private final Matrix hostInverse = new Matrix();
    private final Matrix highlightTransform = new Matrix();
    private final float[] highlightTransformValues = new float[9];
    private final RenderNode sampleNode = new RenderNode("LiquidGlassFusionBackdrop");
    private final RenderNode motionNode = new RenderNode("LiquidGlassFusionMotion");
    private final RectF layerBounds = new RectF();
    private final RuntimeShader shader = new RuntimeShader(SHADER_SOURCE);
    private ViewGroup sampleSource;
    private int sampleWidth = -1;
    private int sampleHeight = -1;
    private int sampleLeft;
    private int sampleTop;
    private int sampleRight;
    private int sampleBottom;
    private long lastStatusLogAt;
    private String lastStatus;
    private String lastPeerState;
    private LiquidGlassView fusedFirst;
    private LiquidGlassView fusedSecond;
    private boolean frameReady;
    private RenderEffect backdropBlur;
    private float backdropBlurRadius = -1f;
    private float surfaceSmoothing;
    private float surfaceTarget;
    private float smoothingVelocity;
    private long lastSurfaceFrame;
    private float surfaceAlpha = 1f;
    private float surfaceMotionBlur;
    private float lastMotionBlur = -1f;
    private float secondAlignmentY;
    private boolean frameDirty = true;
    private final float[] lastGeometry = new float[8];
    private final float[] touchGlow = new float[4];
    private final float[] touchPoint = new float[2];

    // Decide ownership before any peer display list is recorded for this frame.
    private final ViewTreeObserver.OnPreDrawListener hostPreDrawListener = () -> {
        if (frameDirty || sourceDirty(sampleSource) || sourceDirty(fusedFirst)
                || sourceDirty(fusedSecond) || geometryChanged()) {
            prepareFrame();
            frameDirty = false;
            super.invalidateSelf();
            if (Math.abs(surfaceTarget - surfaceSmoothing) > 0.01f
                    || Math.abs(smoothingVelocity) > 0.05f) invalidateSelf();
        }
        return true;
    };

    @Override public void invalidateSelf() {
        frameDirty = true;
        super.invalidateSelf();
    }

    private boolean sourceDirty(View view) {
        if (view == null) return false;
        if (view.isDirty()) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (sourceDirty(group.getChildAt(i))) return true;
            }
        }
        return false;
    }

    private boolean geometryChanged() {
        if (fusedFirst == null || fusedSecond == null) return false;
        Matrix matrix = new Matrix();
        host.transformMatrixToGlobal(matrix);
        if (!matrix.invert(hostInverse)) return true;
        float[] first = hostRect(fusedFirst);
        float[] second = hostRect(fusedSecond);
        boolean changed = false;
        for (int i = 0; i < 4; i++) {
            changed |= Math.abs(first[i] - lastGeometry[i]) > 0.01f
                    || Math.abs(second[i] - lastGeometry[i + 4]) > 0.01f;
            lastGeometry[i] = first[i];
            lastGeometry[i + 4] = second[i];
        }
        return changed;
    }

    static LiquidGlassFusionOverlay attach(
            LiquidGlassView view,
            String groupId,
            boolean anchorRight,
            float mergeGapPx,
            float progress,
            int tintColor
    ) {
        if (groupId == null || groupId.isEmpty() || !view.isAttachedToWindow()) return null;
        View root = view.getRootView();
        View content = root.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return null;

        ViewGroup host = (ViewGroup) content;
        LiquidGlassFusionOverlay overlay = OVERLAYS.get(host);
        if (overlay == null) {
            overlay = new LiquidGlassFusionOverlay(host);
            OVERLAYS.put(host, overlay);
            host.getOverlay().add(overlay);
            overlay.hostObserver.addOnPreDrawListener(overlay.hostPreDrawListener);
        }
        overlay.updatePeer(view, groupId, anchorRight, mergeGapPx, progress, tintColor);
        return overlay;
    }

    static void detach(LiquidGlassFusionOverlay overlay, LiquidGlassView view) {
        if (overlay == null) return;
        overlay.removePeer(view);
        if (overlay.peers.isEmpty()) {
            OVERLAYS.remove(overlay.host);
            overlay.clearFusion();
            overlay.sampleSource = null;
            if (overlay.hostObserver.isAlive()) {
                overlay.hostObserver.removeOnPreDrawListener(overlay.hostPreDrawListener);
            }
            overlay.host.getOverlay().remove(overlay);
            overlay.sampleNode.discardDisplayList();
            overlay.motionNode.discardDisplayList();
        } else {
            overlay.clearFusion();
            overlay.invalidateSelf();
        }
    }

    private LiquidGlassFusionOverlay(ViewGroup host) {
        this.host = host;
        this.hostObserver = host.getViewTreeObserver();
        shader.setFloatUniform("density", host.getResources().getDisplayMetrics().density);
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
                invalidateSelf();
                return;
            }
        }
        peers.add(new Peer(view, groupId, anchorRight, mergeGapPx, progress, tintColor));
        invalidateSelf();
        Log.i(TAG, "registered " + (anchorRight ? "right" : "left")
                + " host=" + host.getWidth() + "x" + host.getHeight());
    }

    private void removePeer(LiquidGlassView view) {
        view.setGlassAttraction(0f, 0f);
        view.setMetaballFusionActive(false);
        Iterator<Peer> iterator = peers.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().view.get() == view) iterator.remove();
        }
    }

    private void removeDetachedPeers() {
        Iterator<Peer> iterator = peers.iterator();
        while (iterator.hasNext()) {
            LiquidGlassView view = iterator.next().view.get();
            if (view == null || !view.isAttachedToWindow()) {
                if (view != null) view.setMetaballFusionActive(false);
                iterator.remove();
            }
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (!frameReady || fusedFirst == null || fusedSecond == null) return;
        if (!canvas.isHardwareAccelerated()) {
            clearFusion();
            return;
        }
        layerBounds.set(sampleLeft, sampleTop, sampleRight, sampleBottom);
        int save = surfaceAlpha < 0.999f
                ? canvas.saveLayerAlpha(layerBounds, Math.round(255f * surfaceAlpha))
                : canvas.save();
        if (surfaceMotionBlur > 0.1f) {
            // Blur optics and labels together during the visibility transition.
            motionNode.setPosition(sampleLeft, sampleTop, sampleRight, sampleBottom);
            Canvas recording = motionNode.beginRecording(sampleWidth, sampleHeight);
            try {
                recording.translate(-sampleLeft, -sampleTop);
                drawSurface(recording);
            } finally {
                motionNode.endRecording();
            }
            if (Math.abs(surfaceMotionBlur - lastMotionBlur) > 0.01f) {
                motionNode.setRenderEffect(RenderEffect.createBlurEffect(
                        surfaceMotionBlur, surfaceMotionBlur, Shader.TileMode.CLAMP));
                lastMotionBlur = surfaceMotionBlur;
            }
            canvas.drawRenderNode(motionNode);
        } else {
            drawSurface(canvas);
        }
        canvas.restoreToCount(save);
    }

    private void drawSurface(Canvas canvas) {
        canvas.drawRenderNode(sampleNode);
        drawPeerContent(canvas, fusedFirst);
        drawPeerContent(canvas, fusedSecond);
    }

    @Override public void setAlpha(int alpha) {}
    @Override public void setColorFilter(ColorFilter colorFilter) {}
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    private void prepareFrame() {
        setBounds(0, 0, host.getWidth(), host.getHeight());
        Matrix hostMatrix = new Matrix();
        host.transformMatrixToGlobal(hostMatrix);
        if (!host.isHardwareAccelerated() || !hostMatrix.invert(hostInverse)) {
            clearFusion();
            return;
        }
        removeDetachedPeers();
        ArrayList<FusionPair> candidates = new ArrayList<>();
        // Geometry changes are frequent; avoid per-frame diagnostic strings.
        if (peers.size() >= 2) {
            for (int firstIndex = 0; firstIndex < peers.size(); firstIndex++) {
                Peer first = peers.get(firstIndex);
                if (first.progress <= 0.001f) continue;
                LiquidGlassView firstView = first.view.get();
                if (firstView == null) continue;
                for (int secondIndex = 0; secondIndex < peers.size(); secondIndex++) {
                    Peer second = peers.get(secondIndex);
                    if (first == second || second.progress <= 0.001f
                            || !first.groupId.equals(second.groupId)) {
                        continue;
                    }
                    LiquidGlassView secondView = second.view.get();
                    if (secondView == null) {
                        continue;
                    }
                    boolean proximityOnly = firstView.isMetaballProximityOnly()
                            || secondView.isMetaballProximityOnly();
                    // 普通玻璃按实际屏幕位置配对，无需供体标记；供体模式仍保留显式方向。
                    if (proximityOnly ? secondIndex <= firstIndex
                            : !first.anchorRight || second.anchorRight) continue;
                    if (firstView.getMetaballSamplingSource()
                            != secondView.getMetaballSamplingSource()) {
                        logFusionStatus("separate: sampling sources differ");
                        continue;
                    }
                    Peer left = first;
                    Peer right = second;
                    LiquidGlassView leftView = firstView;
                    LiquidGlassView rightView = secondView;
                    if (proximityOnly && attractionFreeRect(firstView)[0]
                            > attractionFreeRect(secondView)[0]) {
                        left = second;
                        right = first;
                        leftView = secondView;
                        rightView = firstView;
                    }
                    float separation = proximityOnly ? Math.max(0f,
                            attractionFreeRect(rightView)[0] - attractionFreeRect(leftView)[2]
                                    - Math.min(left.mergeGapPx, right.mergeGapPx)) : 0f;
                    candidates.add(new FusionPair(left, leftView, right, rightView, separation));
                }
            }
        } else {
            logFusionStatus("waiting: peers=" + peers.size());
        }
        // Prefer a pair within merge range before keeping a separated pair on
        // the same optical surface. A distant early peer must not block a close pair.
        candidates.sort((a, b) -> Float.compare(a.separation, b.separation));
        for (FusionPair pair : candidates) {
            if (!prepareFusion(pair.first, pair.firstView, pair.second, pair.secondView)) continue;
            for (Peer peer : peers) {
                LiquidGlassView view = peer.view.get();
                if (view != null) {
                    boolean selected = view == pair.firstView || view == pair.secondView;
                    view.setMetaballFusionActive(selected);
                    if (!selected) view.setGlassAttraction(0f, 0f);
                }
            }
            fusedFirst = pair.firstView;
            fusedSecond = pair.secondView;
            frameReady = true;
            return;
        }
        clearFusion();
    }

    private void clearFusion() {
        frameReady = false;
        fusedFirst = null;
        fusedSecond = null;
        surfaceSmoothing = 0f;
        surfaceTarget = 0f;
        smoothingVelocity = 0f;
        lastSurfaceFrame = 0L;
        for (Peer peer : peers) {
            LiquidGlassView view = peer.view.get();
            if (view != null) {
                view.setMetaballFusionActive(false);
                view.setGlassAttraction(0f, 0f);
            }
        }
    }

    private boolean prepareFusion(
            Peer first,
            LiquidGlassView firstView,
            Peer second,
            LiquidGlassView secondView
    ) {
        float[] rectA = hostRect(firstView);
        float[] rectB = hostRect(secondView);
        // While submerged, the small lobe shares the donor's center line.
        // Its own layout takes over continuously as it finishes separating.
        float alignmentProgress = clamp((secondView.getMetaballEmergence() - 0.80f) / 0.20f);
        alignmentProgress = alignmentProgress * alignmentProgress * (3f - 2f * alignmentProgress);
        secondAlignmentY = ((rectA[1] + rectA[3]) - (rectB[1] + rectB[3])) *
                0.5f * (1f - alignmentProgress);
        rectB[1] += secondAlignmentY;
        rectB[3] += secondAlignmentY;
        float gap = rectB[0] - rectA[2];
        float mergeGap = Math.min(first.mergeGapPx, second.mergeGapPx);
        float[] baseA = attractionFreeRect(firstView);
        float[] baseB = attractionFreeRect(secondView);
        float baseGap = baseB[0] - baseA[2];
        // Distance controls neck geometry, never optical ownership. Crossing
        // mergeGap must not swap between individual and union refraction/rims.
        float centerDeltaY = Math.abs((rectA[1] + rectA[3]) * 0.5f
                - (rectB[1] + rectB[3]) * 0.5f);
        if (firstView.getWidth() <= 0 || secondView.getWidth() <= 0
                || !firstView.isShown() || !secondView.isShown()
                || mergeGap <= 0f
                || centerDeltaY > Math.min(rectA[3] - rectA[1], rectB[3] - rectB[1]) * 0.35f) {
            logFusionStatus("separate: gap=" + pxToDp(gap)
                    + "dp limit=" + pxToDp(mergeGap)
                    + "dp y=" + pxToDp(centerDeltaY) + "dp");
            return false;
        }
        sampleSource = firstView.getMetaballSamplingSource();
        // Never record a tree that contains the glass peers or this optical surface.
        if (sampleSource == null || isAncestor(sampleSource, firstView)
                || isAncestor(sampleSource, secondView)) {
            logFusionStatus("sample unavailable: source=" + (sampleSource != null)
                    + " sourceSize=" + (sampleSource == null ? "null"
                    : sampleSource.getWidth() + "x" + sampleSource.getHeight())
                    + " overlaySize=" + getBounds().width() + "x" + getBounds().height());
            return false;
        }

        // Evaluate proximity without the previous attraction offset, so the
        // springs cannot feed their own displacement back into the force.
        float proximity = clamp((mergeGap - baseGap) / mergeGap);
        proximity = proximity * proximity * (3f - 2f * proximity);
        float massA = (float) firstView.getWidth() * firstView.getHeight();
        float massB = (float) secondView.getWidth() * secondView.getHeight();
        float massSum = Math.max(1f, massA + massB);
        float strength = (float) Math.sqrt(firstView.getGravityStrength() * secondView.getGravityStrength());
        float pull = Math.min(0.9f, strength * 0.65f) *
                host.getResources().getDisplayMetrics().density * proximity;
        setHostAttraction(firstView, pull * massB / massSum);
        setHostAttraction(secondView, -pull * massA / massSum);

        float halfWidthA = (rectA[2] - rectA[0]) * 0.5f;
        float halfHeightA = (rectA[3] - rectA[1]) * 0.5f;
        float halfWidthB = (rectB[2] - rectB[0]) * 0.5f;
        float halfHeightB = (rectB[3] - rectB[1]) * 0.5f;
        float centerAX = (rectA[0] + rectA[2]) * 0.5f;
        float centerAY = (rectA[1] + rectA[3]) * 0.5f;
        float centerBX = (rectB[0] + rectB[2]) * 0.5f;
        float centerBY = (rectB[1] + rectB[3]) * 0.5f;
        surfaceAlpha = Math.min(first.progress, second.progress);

        // Render separated capsules with the same material too, so entering the
        // merge zone changes geometry continuously without swapping optical layers.
        float mergeAmount = clamp((mergeGap - gap) / mergeGap);
        mergeAmount = mergeAmount * mergeAmount * (3f - 2f * mergeAmount);
        float smoothingLimit = Math.min(mergeGap * 3f, Math.min(halfHeightA, halfHeightB) * 0.44f);
        float smoothing = advanceSurface(smoothingLimit * mergeAmount, smoothingLimit,
                firstView, secondView);
        float blur = Math.max(firstView.getMetaballBlurRadius(), secondView.getMetaballBlurRadius());
        // Full-resolution local capture, with enough margin for blur and refraction.
        // Avoid filtering the entire window for two small glass controls.
        surfaceMotionBlur = Math.max(firstView.getMetaballMotionBlur(), secondView.getMetaballMotionBlur());
        float margin = (blur + surfaceMotionBlur) * 3f + 41f * host.getResources().getDisplayMetrics().density
                + smoothingLimit * 0.25f;
        sampleLeft = Math.max(0, (int) Math.floor(Math.min(rectA[0], rectB[0]) - margin));
        sampleTop = Math.max(0, (int) Math.floor(Math.min(rectA[1], rectB[1]) - margin));
        sampleRight = Math.min(host.getWidth(), (int) Math.ceil(Math.max(rectA[2], rectB[2]) + margin));
        sampleBottom = Math.min(host.getHeight(), (int) Math.ceil(Math.max(rectA[3], rectB[3]) + margin));
        if (!recordSampleSource()) {
            logFusionStatus("sample unavailable: local capture");
            return false;
        }

        shader.setFloatUniform("shapeA", centerAX - sampleLeft, centerAY - sampleTop, halfWidthA, halfHeightA);
        shader.setFloatUniform("shapeB", centerBX - sampleLeft, centerBY - sampleTop, halfWidthB, halfHeightB);
        shader.setFloatUniform("cornerRadius",
                toHostMatrix(firstView).mapRadius(firstView.getMetaballCornerRadius()),
                toHostMatrix(secondView).mapRadius(secondView.getMetaballCornerRadius()));
        shader.setFloatUniform("smoothing", smoothing);
        // 体积大的主体更克制，小体积的曲面随引力产生更明显的拉伸。
        shader.setFloatUniform("bodyResponse",
                0.035f + 0.095f * massB / massSum,
                0.035f + 0.095f * massA / massSum);
        shader.setFloatUniform("emergence", secondView.getMetaballEmergence());
        float density = host.getResources().getDisplayMetrics().density;
        highlightTransform.reset();
        host.transformMatrixToGlobal(highlightTransform);
        highlightTransform.getValues(highlightTransformValues);
        float hostStretch = LiquidGlassHighlightMetrics.maximumStretch(
                highlightTransformValues[Matrix.MSCALE_X], highlightTransformValues[Matrix.MSKEW_X],
                highlightTransformValues[Matrix.MSKEW_Y], highlightTransformValues[Matrix.MSCALE_Y]);
        shader.setFloatUniform("highlightWidth",
                LiquidGlassHighlightMetrics.widthPx(halfWidthA * 2f, halfHeightA * 2f,
                        density, firstView.getHighlightWidthScale(), firstView.getHighlightMaxWidth(), hostStretch),
                LiquidGlassHighlightMetrics.widthPx(halfWidthB * 2f, halfHeightB * 2f,
                        density, secondView.getHighlightWidthScale(), secondView.getHighlightMaxWidth(), hostStretch));
        shader.setFloatUniform("tintA", firstView.getMetaballTintColor());
        shader.setFloatUniform("tintB", secondView.getMetaballTintColor());
        shader.setFloatUniform("tintAlphaA", firstView.getMetaballTintAlpha());
        shader.setFloatUniform("tintAlphaB", secondView.getMetaballTintAlpha());
        setTouchUniform("touchA", firstView);
        setTouchUniform("touchB", secondView);
        if (backdropBlur == null || Math.abs(blur - backdropBlurRadius) > 0.01f) {
            backdropBlur = RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP);
            backdropBlurRadius = blur;
        }
        // Recreate the effect after updating uniforms so it uses this frame's surface.
        sampleNode.setRenderEffect(RenderEffect.createChainEffect(
                RenderEffect.createRuntimeShaderEffect(shader, "content"), backdropBlur));
        return true;
    }

    private float advanceSurface(float target, float limit,
                                 LiquidGlassView first, LiquidGlassView second) {
        long now = SystemClock.uptimeMillis();
        surfaceTarget = target;
        if (fusedFirst != first || fusedSecond != second || lastSurfaceFrame == 0L) {
            // A newly registered ordinary pair grows from its separate contours,
            // including when the first valid layout is already inside merge range.
            surfaceSmoothing = first.isMetaballProximityOnly() || second.isMetaballProximityOnly()
                    ? 0f : target;
            smoothingVelocity = 0f;
        } else {
            float remaining = Math.min(0.032f, (now - lastSurfaceFrame) / 1000f);
            // Damped surface tension; bounded substeps keep fast gestures stable.
            while (remaining > 0f) {
                float step = Math.min(remaining, 1f / 120f);
                smoothingVelocity += ((target - surfaceSmoothing) * 320f
                        - smoothingVelocity * 32f) * step;
                surfaceSmoothing += smoothingVelocity * step;
                remaining -= step;
            }
            if (Math.abs(target - surfaceSmoothing) < 0.01f
                    && Math.abs(smoothingVelocity) < 0.05f) {
                surfaceSmoothing = target;
                smoothingVelocity = 0f;
            }
        }
        lastSurfaceFrame = now;
        surfaceSmoothing = Math.max(0f, Math.min(limit, surfaceSmoothing));
        return surfaceSmoothing;
    }

    private float pxToDp(float px) {
        return px / host.getResources().getDisplayMetrics().density;
    }

    private void setTouchUniform(String name, LiquidGlassView view) {
        view.resolveTouchGlow(touchGlow);
        touchPoint[0] = touchGlow[0];
        touchPoint[1] = touchGlow[1];
        Matrix transform = toHostMatrix(view);
        transform.mapPoints(touchPoint);
        // 共享触点属于窗口光场，不随分离时的几何对齐偏移。
        shader.setFloatUniform(name, touchPoint[0] - sampleLeft,
                touchPoint[1] - sampleTop, touchGlow[2], transform.mapRadius(touchGlow[3]));
    }

    private void logFusionStatus(String status) {
        long now = SystemClock.uptimeMillis();
        if (status.equals(lastStatus) && now - lastStatusLogAt < 1000L) return;
        lastStatus = status;
        lastStatusLogAt = now;
        Log.i(TAG, status);
    }

    private void logPeerState() {
        StringBuilder state = new StringBuilder("peers=").append(peers.size());
        for (Peer peer : peers) {
            LiquidGlassView view = peer.view.get();
            state.append(" [").append(peer.anchorRight ? "right" : "left")
                    .append(",progress=").append(Math.round(peer.progress * 100f))
                    .append(",source=").append(view != null
                            && view.getMetaballSamplingSource() != null)
                    .append(']');
        }
        String current = state.toString();
        if (!current.equals(lastPeerState)) {
            lastPeerState = current;
            Log.i(TAG, current);
        }
    }

    private void drawPeerContent(Canvas canvas, LiquidGlassView view) {
        float progress = clamp((view.getMetaballEmergence() - 0.55f) / 0.35f);
        float contentAlpha = progress * progress * (3f - 2f * progress);
        if (contentAlpha <= 0f) return;
        Matrix matrix = toHostMatrix(view);
        if (view == fusedSecond) matrix.postTranslate(0f, secondAlignmentY);
        int save = canvas.save();
        canvas.concat(matrix);
        int contentSave = contentAlpha < 0.999f
                ? canvas.saveLayerAlpha(new RectF(0f, 0f, view.getWidth(), view.getHeight()),
                        Math.round(255f * contentAlpha))
                : canvas.save();
        view.drawMetaballContent(canvas);
        canvas.restoreToCount(contentSave);
        canvas.restoreToCount(save);
    }

    private Matrix toHostMatrix(View view) {
        Matrix matrix = new Matrix();
        view.transformMatrixToGlobal(matrix);
        matrix.postConcat(hostInverse);
        return matrix;
    }

    private float[] hostRect(LiquidGlassView view) {
        RectF rect = new RectF(0, 0, view.getWidth(), view.getHeight());
        toHostMatrix(view).mapRect(rect);
        return new float[]{rect.left, rect.top, rect.right, rect.bottom};
    }

    private float[] attractionFreeRect(LiquidGlassView view) {
        float[] rect = hostRect(view);
        float[] offset = {view.getGlassAttractionX(), view.getGlassAttractionY()};
        // Translation lives in the parent coordinate system, not the scaled
        // local glass coordinates.
        if (view.getParent() instanceof View) {
            toHostMatrix((View) view.getParent()).mapVectors(offset);
        }
        rect[0] -= offset[0];
        rect[2] -= offset[0];
        rect[1] -= offset[1];
        rect[3] -= offset[1];
        return rect;
    }

    private void setHostAttraction(LiquidGlassView view, float x) {
        float[] offset = {x, 0f};
        if (view.getParent() instanceof View) {
            Matrix inverse = new Matrix();
            if (!toHostMatrix((View) view.getParent()).invert(inverse)) return;
            inverse.mapVectors(offset);
        }
        view.setGlassAttraction(offset[0], offset[1]);
    }

    private boolean isAncestor(ViewGroup ancestor, View view) {
        View current = view;
        while (current != null) {
            if (current == ancestor) return true;
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        return false;
    }

    private boolean recordSampleSource() {
        int width = sampleRight - sampleLeft;
        int height = sampleBottom - sampleTop;
        if (width <= 0 || height <= 0 || sampleSource == null
                || sampleSource.getWidth() <= 0 || sampleSource.getHeight() <= 0) {
            return false;
        }
        sampleNode.setPosition(sampleLeft, sampleTop, sampleRight, sampleBottom);
        sampleWidth = width;
        sampleHeight = height;

        Canvas recording = sampleNode.beginRecording(width, height);
        try {
            recording.translate(-sampleLeft, -sampleTop);
            recording.concat(toHostMatrix(sampleSource));
            sampleSource.draw(recording);
        } catch (RuntimeException error) {
            Log.e(TAG, "Backdrop recording failed; retaining individual glass", error);
            return false;
        } finally {
            sampleNode.endRecording();
        }
        return true;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static final class FusionPair {
        final Peer first;
        final LiquidGlassView firstView;
        final Peer second;
        final LiquidGlassView secondView;
        final float separation;

        FusionPair(Peer first, LiquidGlassView firstView,
                   Peer second, LiquidGlassView secondView, float separation) {
            this.first = first;
            this.firstView = firstView;
            this.second = second;
            this.secondView = secondView;
            this.separation = separation;
        }
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
