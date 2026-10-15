package com.generated.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.LinearInterpolator;

public class ScanAnimationView extends View {

    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardCutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint contactPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lensPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lensRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sweepPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path cardPath = new Path();
    private final Path lensPath = new Path();
    private final Path handlePath = new Path();
    private final RectF rectF = new RectF();

    private ValueAnimator animator;
    private long startTime = 0;

    public ScanAnimationView(Context context) {
        super(context);
        init();
    }

    private void init() {
        cardPaint.setStyle(Paint.Style.FILL);
        cardPaint.setColor(0xFF1976D2); // Primary Theme Color

        cardCutPaint.setStyle(Paint.Style.FILL);
        cardCutPaint.setColor(0xFFF8F9FA); // Background color to mask cut corner

        contactPaint.setStyle(Paint.Style.FILL);
        contactPaint.setColor(0xFFFFD700); // Gold contacts

        labelPaint.setStyle(Paint.Style.FILL);
        labelPaint.setColor(0xFF0D47A1);

        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(0x33000000);

        lensPaint.setStyle(Paint.Style.FILL);
        lensPaint.setColor(0x4000B0FF); // Translucent blue lens

        lensRimPaint.setStyle(Paint.Style.STROKE);
        lensRimPaint.setStrokeWidth(6f);
        lensRimPaint.setColor(0xFF0288D1);

        handlePaint.setStyle(Paint.Style.STROKE);
        handlePaint.setStrokeWidth(12f);
        handlePaint.setStrokeCap(Paint.Cap.ROUND);
        handlePaint.setColor(0xFF37474F);

        sweepPaint.setStyle(Paint.Style.FILL);
        sweepPaint.setColor(0x22FFFFFF);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startTime = System.currentTimeMillis();
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

        // Card dimensions (scaled to fit nicely in 240dp x 160dp)
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

        // Time elapsed for smooth magnifying glass movement
        long t = System.currentTimeMillis() - startTime;
        float xOffset = (float) (35.0 * Math.sin(2.0 * Math.PI * t / 6000.0));
        float yOffset = (float) (25.0 * Math.sin(2.0 * Math.PI * t / 4300.0));
        float lensX = cx + xOffset;
        float lensY = cy + yOffset;
        float lensRadius = 32f;

        // 2. Soft shadow under magnifying glass
        canvas.drawCircle(lensX + 4f, lensY + 6f, lensRadius, shadowPaint);

        // 3. Magnifying glass lens and handle
        canvas.drawCircle(lensX, lensY, lensRadius, lensPaint);
        canvas.drawCircle(lensX, lensY, lensRadius, lensRimPaint);

        // Handle extending downwards and slightly tilted in movement direction
        float tilt = (float) (10.0 * Math.cos(2.0 * Math.PI * t / 6000.0));
        float handleEndX = lensX + 35f + tilt;
        float handleEndY = lensY + 35f;
        canvas.drawLine(lensX + 22f, lensY + 22f, handleEndX, handleEndY, handlePaint);
    }
}
