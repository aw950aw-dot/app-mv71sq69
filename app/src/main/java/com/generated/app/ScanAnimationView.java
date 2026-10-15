package com.generated.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.provider.Settings;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ScanAnimationView extends View {

    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint contactPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lensRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lensGlassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lensHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Inside card paints
    private final Paint boardBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tracePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cellGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Floating particle/dot paints for found files
    private final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path cardPath = new Path();
    private final Path lensPath = new Path();
    private final RectF rectF = new RectF();
    private final RectF chipRect = new RectF();
    private final RectF glassRect = new RectF();

    private ValueAnimator animator;
    private long startTime = 0;
    private int foundCount = 0;

    private static final int MAX_PARTICLES = 12;
    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random();
    private int lastKnownFoundCount = 0;

    private static class Particle {
        float x, y;
        float vx, vy;
        float alpha;
        int color;
        boolean active;
        long birthTime;
    }

    public ScanAnimationView(Context context) {
        super(context);
        init();
    }

    private void init() {
        setLayerType(LAYER_TYPE_HARDWARE, null);

        cardPaint.setStyle(Paint.Style.FILL);
        cardPaint.setColor(0xFF1976D2); // Primary Theme Color

        contactPaint.setStyle(Paint.Style.FILL);
        contactPaint.setColor(0xFFFFD700); // Gold contacts

        labelPaint.setStyle(Paint.Style.FILL);
        labelPaint.setColor(0xFF0D47A1);

        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(0x44000000);

        lensRimPaint.setStyle(Paint.Style.STROKE);
        lensRimPaint.setStrokeWidth(6f);
        lensRimPaint.setColor(0xFF0288D1);

        handlePaint.setStyle(Paint.Style.STROKE);
        handlePaint.setStrokeWidth(12f);
        handlePaint.setStrokeCap(Paint.Cap.ROUND);
        handlePaint.setColor(0xFF37474F);

        lensGlassPaint.setStyle(Paint.Style.FILL);
        lensGlassPaint.setColor(0x2200B0FF);

        lensHighlightPaint.setStyle(Paint.Style.STROKE);
        lensHighlightPaint.setStrokeWidth(4f);
        lensHighlightPaint.setStrokeCap(Paint.Cap.ROUND);
        lensHighlightPaint.setColor(0x88FFFFFF);

        // Inside card paints
        boardBgPaint.setStyle(Paint.Style.FILL);
        boardBgPaint.setColor(0xFF0A192F); // Dark circuit board

        chipPaint.setStyle(Paint.Style.FILL);
        chipPaint.setColor(0xFF1E3A5F);

        tracePaint.setStyle(Paint.Style.STROKE);
        tracePaint.setStrokeWidth(1.5f);
        tracePaint.setColor(0xFF00E676); // Copper/Neon green trace

        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(0xFF00E676);

        cellGlowPaint.setStyle(Paint.Style.FILL);
        cellGlowPaint.setColor(0xFFFFD700);

        particlePaint.setStyle(Paint.Style.FILL);

        for (int i = 0; i < MAX_PARTICLES; i++) {
            particles.add(new Particle());
        }
    }

    public void setFoundCount(int count) {
        if (count > lastKnownFoundCount) {
            int diff = count - lastKnownFoundCount;
            for (int i = 0; i < Math.min(diff, 3); i++) {
                spawnParticle();
            }
        }
        lastKnownFoundCount = count;
        this.foundCount = count;
    }

    private void spawnParticle() {
        for (Particle p : particles) {
            if (!p.active) {
                p.active = true;
                p.x = 0f; // centered relative to lens
                p.y = 15f;
                p.vx = (random.nextFloat() - 0.5f) * 1.5f;
                p.vy = -1.5f - random.nextFloat() * 1.5f;
                p.alpha = 1.0f;
                p.birthTime = System.currentTimeMillis();

                int[] themeColors = new int[]{0xFF1976D2, 0xFF00E676, 0xFFFFD700, 0xFFFF5722, 0xFF00B0FF};
                p.color = themeColors[random.nextInt(themeColors.length)];
                break;
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startTime = System.currentTimeMillis();

        float animatorDurationScale = 1.0f;
        try {
            animatorDurationScale = Settings.Global.getFloat(getContext().getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f);
        } catch (Exception ignored) {}

        if (animatorDurationScale <= 0f) {
            // Rule 3: If animator scale is 0, draw one static frame
            invalidate();
            return;
        }

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(6000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> invalidate());
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        float cx = width / 2f;
        float cy = height / 2f;

        float cardWidth = 140f;
        float cardHeight = 100f;
        float left = cx - cardWidth / 2f;
        float top = cy - cardHeight / 2f;
        float right = left + cardWidth;
        float bottom = top + cardHeight;

        // 1. Draw SD Card Body with top-right diagonal cut
        cardPath.reset();
        cardPath.moveTo(left + 12f, top);
        cardPath.lineTo(right - 24f, top);
        cardPath.lineTo(right, top + 24f);
        cardPath.lineTo(right, bottom - 8f);
        rectF.set(right - 16f, bottom - 16f, right, bottom);
        cardPath.arcTo(rectF, 0, 90, false);
        cardPath.lineTo(left + 8f, bottom);
        rectF.set(left, bottom - 16f, left + 16f, bottom);
        cardPath.arcTo(rectF, 90, 90, false);
        cardPath.lineTo(left, top + 12f);
        rectF.set(left, top, left + 16f, top + 16f);
        cardPath.arcTo(rectF, 180, 90, false);
        cardPath.close();

        canvas.drawPath(cardPath, cardPaint);

        // Gold contact strips at top
        float contactWidth = 14f;
        float contactHeight = 22f;
        float contactY = top + 8f;
        float startX = left + 24f;
        for (int i = 0; i < 3; i++) {
            canvas.drawRect(startX + i * 22f, contactY, startX + i * 22f + contactWidth, contactY + contactHeight, contactPaint);
        }

        // Label area
        canvas.drawRect(left + 16f, top + 38f, right - 16f, bottom - 12f, labelPaint);

        // Magnifying glass movement
        long t = System.currentTimeMillis() - startTime;
        float xOffset = (float) (35.0 * Math.sin(2.0 * Math.PI * t / 6000.0));
        float yOffset = (float) (25.0 * Math.sin(2.0 * Math.PI * t / 4300.0));
        float lensX = cx + xOffset;
        float lensY = cy + yOffset;
        float lensRadius = 34f;

        // 2. "Inside the card" effect under the lens circle
        canvas.save();
        lensPath.reset();
        lensPath.addCircle(lensX, lensY, lensRadius, Path.Direction.CW);
        canvas.clipPath(lensPath);

        // Magnified view transformation (1.4x around lens center)
        canvas.save();
        canvas.scale(1.4f, 1.4f, lensX, lensY);

        // Draw dark circuit-board background
        canvas.drawRect(left, top, right, bottom, boardBgPaint);

        // Draw thin copper traces
        canvas.drawLine(left + 20f, top + 20f, left + 60f, top + 50f, tracePaint);
        canvas.drawLine(left + 60f, top + 50f, right - 30f, top + 30f, tracePaint);
        canvas.drawLine(right - 30f, top + 30f, right - 20f, bottom - 20f, tracePaint);
        canvas.drawLine(left + 30f, bottom - 30f, left + 80f, bottom - 30f, tracePaint);

        canvas.drawCircle(left + 60f, top + 50f, 2.5f, dotPaint);
        canvas.drawCircle(right - 30f, top + 30f, 2.5f, dotPaint);

        // Small memory chip (rounded rectangle) with fine grid lines
        chipRect.set(cx - 22f, cy - 18f, cx + 22f, cy + 18f);
        canvas.drawRoundRect(chipRect, 4f, 4f, chipPaint);

        for (float gx = chipRect.left + 6f; gx < chipRect.right; gx += 8f) {
            canvas.drawLine(gx, chipRect.top, gx, chipRect.bottom, tracePaint);
        }
        for (float gy = chipRect.top + 6f; gy < chipRect.bottom; gy += 8f) {
            canvas.drawLine(chipRect.left, gy, chipRect.right, gy, tracePaint);
        }

        // Glowing squares (memory cells) that blink at random
        long pulseTime = System.currentTimeMillis();
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 3; col++) {
                float cellX = cx - 14f + col * 12f;
                float cellY = cy - 8f + row * 16f;
                int alpha = (int) (128 + 127 * Math.sin(pulseTime * 0.005 + row * col));
                cellGlowPaint.setAlpha(Math.max(20, Math.min(255, alpha)));
                canvas.drawRect(cellX, cellY, cellX + 6f, cellY + 6f, cellGlowPaint);
            }
        }

        canvas.restore(); // restore scale
        canvas.restore(); // restore clipPath

        // 3. Soft shadow under magnifying glass
        canvas.drawCircle(lensX + 4f, lensY + 6f, lensRadius, shadowPaint);

        // 4. Lens glass tint & rim
        canvas.drawCircle(lensX, lensY, lensRadius, lensGlassPaint);
        canvas.drawCircle(lensX, lensY, lensRadius, lensRimPaint);

        // Bright highlight arc on lens glass
        glassRect.set(lensX - lensRadius + 6f, lensY - lensRadius + 6f, lensX + lensRadius - 6f, lensY + lensRadius - 6f);
        canvas.drawArc(glassRect, -135f, 90f, false, lensHighlightPaint);

        // Handle extending downwards and slightly tilted
        float tilt = (float) (10.0 * Math.cos(2.0 * Math.PI * t / 6000.0));
        float handleEndX = lensX + 35f + tilt;
        float handleEndY = lensY + 35f;
        canvas.drawLine(lensX + 22f, lensY + 22f, handleEndX, handleEndY, handlePaint);

        // 5. Draw and update floating particles (file found rising dots)
        long currentTime = System.currentTimeMillis();
        boolean needsMoreFrames = false;
        for (Particle p : particles) {
            if (p.active) {
                long age = currentTime - p.birthTime;
                if (age > 600) {
                    p.active = false;
                } else {
                    needsMoreFrames = true;
                    p.x += p.vx;
                    p.y += p.vy;
                    p.alpha = 1.0f - ((float) age / 600f);

                    particlePaint.setColor(p.color);
                    particlePaint.setAlpha((int) (p.alpha * 255));
                    canvas.drawCircle(lensX + p.x, lensY + p.y, 4f, particlePaint);
                }
            }
        }

        if (needsMoreFrames) {
            invalidate();
        }
    }
}
