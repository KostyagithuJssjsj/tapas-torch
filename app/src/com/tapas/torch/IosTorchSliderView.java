package com.tapas.torch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

public class IosTorchSliderView extends View {
    public interface Listener {
        void onLevelChanged(float frac, int hw);
    }

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path clipPath = new Path();

    private float frac = 0f;
    private float overscroll = 0f;
    private int lastHapticStep = -1;
    private float downY;
    private float startFrac;
    private boolean moved;
    private Listener listener;

    public IosTorchSliderView(Context ctx) {
        super(ctx);
        trackPaint.setColor(0x5A3A3A3C);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(1.2f));
        borderPaint.setColor(0x2EFFFFFF);
        fillPaint.setColor(0xFAFFFFFF);
        tickPaint.setColor(0x28FFFFFF);
        tickPaint.setStrokeWidth(dp(1f));
        setHapticFeedbackEnabled(true);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    public void setListener(Listener l) { this.listener = l; }

    public void setFraction(float f) {
        this.frac = Math.max(0f, Math.min(1f, f));
        invalidate();
    }

    public float getFraction() { return frac; }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        float h = getHeight();
        float radius = dp(38f);

        c.save();
        // iOS elastic stretch at 0% and 100% edges
        float scaleY = 1f + Math.min(0.045f, Math.abs(overscroll) * 0.08f);
        float scaleX = 1f - Math.min(0.025f, Math.abs(overscroll) * 0.04f);
        float pivotY = overscroll > 0 ? h : 0f;
        c.scale(scaleX, scaleY, w * 0.5f, pivotY);

        rect.set(dp(4), dp(4), w - dp(4), h - dp(4));
        clipPath.reset();
        clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW);

        // 1. Frosted track
        c.drawPath(clipPath, trackPaint);

        // 2. White fill clipped inside rounded capsule
        c.save();
        c.clipPath(clipPath);
        float fillTop = rect.bottom - rect.height() * frac;
        if (frac > 0.005f) {
            c.drawRect(rect.left, fillTop, rect.right, rect.bottom, fillPaint);
        }

        // Subtle iOS-like 4 reference dividers (very faint, authentic look)
        for (int i = 1; i <= 3; i++) {
            float y = rect.bottom - rect.height() * (i / 4f);
            tickPaint.setColor(y > fillTop ? 0x16000000 : 0x18FFFFFF);
            c.drawLine(rect.left + dp(24), y, rect.right - dp(24), y, tickPaint);
        }

        // 3. Minimalist flashlight icon near bottom of capsule (auto-inverts contrast)
        float cx = w * 0.5f;
        float cy = rect.bottom - dp(42);
        boolean onWhite = cy > fillTop;
        iconPaint.setColor(onWhite ? 0xFF1C1C1E : 0xCCFFFFFF);
        drawTorchIcon(c, cx, cy, dp(15), frac > 0.005f);
        c.restore();

        // 4. Subtle glass rim
        c.drawPath(clipPath, borderPaint);
        c.restore();
    }

    private void drawTorchIcon(Canvas c, float cx, float cy, float s, boolean on) {
        // Flashlight body
        RectF body = new RectF(cx - s * 0.30f, cy - s * 0.15f, cx + s * 0.30f, cy + s * 0.85f);
        c.drawRoundRect(body, dp(3), dp(3), iconPaint);
        // Flashlight head
        Path head = new Path();
        head.moveTo(cx - s * 0.52f, cy - s * 0.65f);
        head.lineTo(cx + s * 0.52f, cy - s * 0.65f);
        head.lineTo(cx + s * 0.30f, cy - s * 0.18f);
        head.lineTo(cx - s * 0.30f, cy - s * 0.18f);
        head.close();
        c.drawPath(head, iconPaint);
        // Light rays when ON
        if (on) {
            Paint ray = new Paint(iconPaint);
            ray.setStrokeWidth(dp(2.2f));
            ray.setStrokeCap(Paint.Cap.ROUND);
            c.drawLine(cx, cy - s * 0.90f, cx, cy - s * 1.20f, ray);
            c.drawLine(cx - s * 0.42f, cy - s * 0.86f, cx - s * 0.62f, cy - s * 1.10f, ray);
            c.drawLine(cx + s * 0.42f, cy - s * 0.86f, cx + s * 0.62f, cy - s * 1.10f, ray);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float usableH = Math.max(1f, getHeight() - dp(8));
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downY = e.getY();
                startFrac = frac;
                moved = false;
                overscroll = 0f;
                return true;

            case MotionEvent.ACTION_MOVE:
                float dy = downY - e.getY();
                if (Math.abs(dy) > dp(4)) moved = true;
                float raw = startFrac + dy / usableH;
                if (raw > 1f) {
                    overscroll = raw - 1f;
                    raw = 1f;
                } else if (raw < 0f) {
                    overscroll = raw;
                    raw = 0f;
                } else {
                    overscroll = 0f;
                }
                applyFrac(raw);
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!moved) {
                    // Direct tap sets level directly (bottom 14% toggles OFF)
                    float tapFrac = 1f - ((e.getY() - dp(4)) / usableH);
                    if (tapFrac < 0.14f) tapFrac = 0f;
                    applyFrac(Math.max(0f, Math.min(1f, tapFrac)));
                }
                overscroll = 0f;
                invalidate();
                return true;
        }
        return super.onTouchEvent(e);
    }

    private void applyFrac(float newFrac) {
        frac = newFrac;
        int hw = TorchController.fracToHw(frac);
        TorchController.writeHw(hw);
        if (frac > 0.02f) TorchController.saveLastFrac(getContext(), frac);

        int step = (int) Math.round(frac * 24f);
        if (step != lastHapticStep) {
            lastHapticStep = step;
            performHapticFeedback(
                (step == 0 || step == 24) ? HapticFeedbackConstants.LONG_PRESS : HapticFeedbackConstants.CLOCK_TICK
            );
        }
        if (listener != null) listener.onLevelChanged(frac, hw);
        invalidate();
    }
}
