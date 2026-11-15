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

package com.qmdeve.liquidglass.util;

import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

public class LiquidTracker {
    public interface OnTransformChangedListener {
        void onTransformChanged(float scaleX, float scaleY, float translationX, float translationY);
    }

    private VelocityTracker velocityTracker;
    private final SpringAnimation springAnimX, springAnimY;
    private final SpringAnimation springAnimRotX, springAnimRotY;
    private final SpringAnimation springTransX, springTransY;
    private final FloatValueHolder touchX = new FloatValueHolder();
    private final FloatValueHolder touchY = new FloatValueHolder();
    private final FloatValueHolder attractionX = new FloatValueHolder();
    private final FloatValueHolder attractionY = new FloatValueHolder();
    private final SpringAnimation springAttractionX, springAttractionY;
    private final View trackedView;
    private float attractionTargetX, attractionTargetY;
    private final Handler liquidHandler;
    private OnTransformChangedListener transformChangedListener;

    public LiquidTracker(View view) {
        trackedView = view;
        SpringForce springX = new SpringForce();
        springX.setStiffness(180f);
        springX.setDampingRatio(0.35f);
        springAnimX = new SpringAnimation(view, DynamicAnimation.SCALE_X);
        springAnimX.setSpring(springX);

        SpringForce springY = new SpringForce();
        springY.setStiffness(180f);
        springY.setDampingRatio(0.35f);
        springAnimY = new SpringAnimation(view, DynamicAnimation.SCALE_Y);
        springAnimY.setSpring(springY);

        SpringForce springRot = new SpringForce();
        springRot.setStiffness(180f);
        springRot.setDampingRatio(0.5f);
        
        springAnimRotX = new SpringAnimation(view, DynamicAnimation.ROTATION_X);
        springAnimRotX.setSpring(springRot);
        
        springAnimRotY = new SpringAnimation(view, DynamicAnimation.ROTATION_Y);
        springAnimRotY.setSpring(new SpringForce().setStiffness(180f).setDampingRatio(0.5f));

        // 位移弹簧：用于自由方向推动感（高刚度快速跟随，带回弹）
        SpringForce springTrans = new SpringForce();
        springTrans.setStiffness(300f);
        springTrans.setDampingRatio(0.5f);
        springTransX = new SpringAnimation(touchX);
        springTransX.setSpring(springTrans);
        springTransY = new SpringAnimation(touchY);
        springTransY.setSpring(new SpringForce().setStiffness(300f).setDampingRatio(0.5f));
        springAttractionX = new SpringAnimation(attractionX)
                .setSpring(new SpringForce(0f).setStiffness(240f).setDampingRatio(0.85f))
                .setMinimumVisibleChange(0.01f);
        springAttractionY = new SpringAnimation(attractionY)
                .setSpring(new SpringForce(0f).setStiffness(240f).setDampingRatio(0.85f))
                .setMinimumVisibleChange(0.01f);

        DynamicAnimation.OnAnimationUpdateListener updateListener =
                (animation, value, velocity) -> notifyTransform(view);
        springAnimX.addUpdateListener(updateListener);
        springAnimY.addUpdateListener(updateListener);
        DynamicAnimation.OnAnimationUpdateListener translationListener = (animation, value, velocity) -> {
            view.setTranslationX(touchX.getValue() + attractionX.getValue());
            view.setTranslationY(touchY.getValue() + attractionY.getValue());
            notifyTransform(view);
        };
        springTransX.addUpdateListener(translationListener);
        springTransY.addUpdateListener(translationListener);
        springAttractionX.addUpdateListener(translationListener);
        springAttractionY.addUpdateListener(translationListener);

        liquidHandler = new Handler(Looper.getMainLooper());
    }

    public void setOnTransformChangedListener(OnTransformChangedListener listener) {
        transformChangedListener = listener;
    }

    private void notifyTransform(View view) {
        if (transformChangedListener != null) {
            transformChangedListener.onTransformChanged(
                    view.getScaleX(),
                    view.getScaleY(),
                    view.getTranslationX(),
                    view.getTranslationY()
            );
        }
    }

    public void applyMovement(@NonNull MotionEvent e) {
        int action = e.getAction();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                ensureAddMovement(e);
                break;
            case MotionEvent.ACTION_MOVE:
                ensureAddMovement(e);

                float[] scaleXY = getLiquidScale();
                animateToFinalPosition(scaleXY[0], scaleXY[1]);

                liquidHandler.removeCallbacksAndMessages(null);
                liquidHandler.postDelayed(() -> {
                    animateToFinalPosition(1f, 1f);
                }, 200);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                recycle();
                animateToFinalPosition(1f, 1f);
                break;
        }
    }

    public void recycle() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    public void cancelAnimations() {
        liquidHandler.removeCallbacksAndMessages(null);
        recycle();
        springAnimX.cancel();
        springAnimY.cancel();
        springAnimRotX.cancel();
        springAnimRotY.cancel();
        springTransX.cancel();
        springTransY.cancel();
        springAttractionX.cancel();
        springAttractionY.cancel();
        touchX.setValue(0f);
        touchY.setValue(0f);
        attractionX.setValue(0f);
        attractionY.setValue(0f);
        attractionTargetX = attractionTargetY = 0f;
        trackedView.setTranslationX(0f);
        trackedView.setTranslationY(0f);
    }

    private float getVelocity() {
        if (velocityTracker == null)
            return 0f;

        velocityTracker.computeCurrentVelocity(1);
        float velocityX = velocityTracker.getXVelocity();
        float velocityY = velocityTracker.getYVelocity();
        return (float)Math.sqrt(velocityX * velocityX + velocityY * velocityY) * (velocityX > 0f ? 1f : -1f);
    }

    private float[] getLiquidScale() {
        if (velocityTracker == null)
            return new float[] { 1f, 1f };

        velocityTracker.computeCurrentVelocity(1);
        float velocityX = velocityTracker.getXVelocity();
        float velocityY = velocityTracker.getYVelocity();

        // Calculate deformation separately for X and Y directions
        // Stretch along movement direction, compress perpendicular to it
        float absVx = Math.abs(velocityX);
        float absVy = Math.abs(velocityY);

        // Deformation intensity factor, adjust this value to control the degree of deformation
        float stretchFactor = 0.8f;

        float scaleX, scaleY;
        if (absVx > absVy) {
            // Horizontal movement dominant: stretch X axis, compress Y axis
            scaleX = 1f + absVx * stretchFactor;
            scaleY = 1f - absVx * stretchFactor * 0.5f;
        } else {
            // Vertical movement dominant: stretch Y axis, compress X axis
            scaleX = 1f - absVy * stretchFactor * 0.5f;
            scaleY = 1f + absVy * stretchFactor;
        }

        float expansionPx = 6f * trackedView.getResources().getDisplayMetrics().density;
        float limitX = Math.min(0.18f, expansionPx / Math.max(1, trackedView.getWidth()));
        float limitY = Math.min(0.18f, expansionPx / Math.max(1, trackedView.getHeight()));
        return new float[] {
                Math.clamp(scaleX, 1f - limitX * 0.5f, 1f + limitX),
                Math.clamp(scaleY, 1f - limitY * 0.5f, 1f + limitY)
        };
    }

    private void ensureAddMovement(MotionEvent e) {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        velocityTracker.addMovement(e);
    }

    public void animateScale(float scale) {
        animateToFinalPosition(scale, scale);
    }

    /** 独立控制 X/Y 轴的弹簧缩放（轻柔形变用） */
    public void animateGentleScale(float scaleX, float scaleY) {
        springAnimX.animateToFinalPosition(scaleX);
        springAnimY.animateToFinalPosition(scaleY);
    }

    /** 自由方向位移弹簧：触摸偏哪就往哪推 */
    public void animateTranslation(float tx, float ty) {
        springTransX.animateToFinalPosition(tx);
        springTransY.animateToFinalPosition(ty);
    }

    public void animateAttraction(float tx, float ty) {
        if (Math.abs(tx - attractionTargetX) > 0.01f) {
            attractionTargetX = tx;
            springAttractionX.animateToFinalPosition(tx);
        }
        if (Math.abs(ty - attractionTargetY) > 0.01f) {
            attractionTargetY = ty;
            springAttractionY.animateToFinalPosition(ty);
        }
    }

    public float getAttractionX() { return attractionX.getValue(); }
    public float getAttractionY() { return attractionY.getValue(); }

    public void animateTilt(float rotX, float rotY) {
        springAnimRotX.animateToFinalPosition(rotX);
        springAnimRotY.animateToFinalPosition(rotY);
    }

    private void animateToFinalPosition(float x, float y) {
        springAnimX.animateToFinalPosition(x);
        springAnimY.animateToFinalPosition(y);
    }
}
