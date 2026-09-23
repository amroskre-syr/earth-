package com.nerva.earthwallpaper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.Random;

final class EarthRenderer {
    private final Bitmap background;
    private final Bitmap[] earthFrames = new Bitmap[5];
    private final Rect[] earthCropRects = new Rect[5];

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final ArrayList<Star> stars = new ArrayList<>();
    private final long startTime = System.currentTimeMillis();
    private final Random random = new Random(240923L);

    private float sensorZoomTarget = 1.0f;
    private float sensorZoomCurrent = 1.0f;
    private float bgShiftTargetX = 0f;
    private float bgShiftTargetY = 0f;
    private float bgShiftCurrentX = 0f;
    private float bgShiftCurrentY = 0f;
    private boolean interactionPaused;

    EarthRenderer(Context context) {
        background = decodeBackground(context);
        for (int i = 0; i < earthFrames.length; i++) {
            Bitmap bmp = decodeEarthFrame(context, i);
            earthFrames[i] = bmp;
            earthCropRects[i] = findOpaqueBounds(bmp, 32);
        }
        buildStars();
    }

    private Bitmap decodeBackground(Context context) {
        int resId = context.getResources().getIdentifier("space_bg", "drawable", context.getPackageName());
        return BitmapFactory.decodeResource(context.getResources(), resId);
    }

    private Bitmap decodeEarthFrame(Context context, int index) {
        int resId = context.getResources().getIdentifier("earth_" + index, "drawable", context.getPackageName());
        return BitmapFactory.decodeResource(context.getResources(), resId);
    }

    void setSensorInput(float horizontal, float vertical, float zoomSignal) {
        if (interactionPaused) return;
        bgShiftTargetX = clamp(horizontal, -1f, 1f);
        bgShiftTargetY = clamp(vertical, -1f, 1f);
        sensorZoomTarget = clamp(1.0f + zoomSignal * 0.12f, 0.84f, 1.18f);
    }

    void addGyro(float x, float y) {
        if (interactionPaused) return;
        bgShiftTargetX = clamp(bgShiftTargetX + x * 0.55f, -1f, 1f);
        bgShiftTargetY = clamp(bgShiftTargetY + y * 0.55f, -1f, 1f);
        float combined = clamp((Math.abs(x) + Math.abs(y)) * 0.45f, 0f, 1f);
        sensorZoomTarget = clamp(1.0f + combined * 0.08f, 0.84f, 1.18f);
    }

    boolean onTouch(MotionEvent event, int width) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                interactionPaused = true;
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                interactionPaused = false;
                return true;
            default:
                return false;
        }
    }

    void draw(Canvas canvas, int width, int height) {
        if (width <= 0 || height <= 0) return;

        canvas.drawColor(Color.BLACK);

        if (!interactionPaused) {
            sensorZoomCurrent += (sensorZoomTarget - sensorZoomCurrent) * 0.085f;
            bgShiftCurrentX += (bgShiftTargetX - bgShiftCurrentX) * 0.08f;
            bgShiftCurrentY += (bgShiftTargetY - bgShiftCurrentY) * 0.08f;
        }

        drawBackground(canvas, width, height);
        drawStars(canvas, width, height);

        double seconds = (System.currentTimeMillis() - startTime) / 1000.0;
        double phase = (seconds / 16.0) % 1.0;
        if (phase < 0) phase += 1.0;

        float framePosition = (float) (phase * earthFrames.length);
        int current = ((int) Math.floor(framePosition)) % earthFrames.length;
        int next = (current + 1) % earthFrames.length;
        float blend = framePosition - (float) Math.floor(framePosition);

        Rect currentSrc = earthCropRects[current];
        Rect nextSrc = earthCropRects[next];

        float earthWidth = Math.min(width, height) * 0.93f * sensorZoomCurrent;
        float earthHeight = earthWidth * currentSrc.height() / (float) currentSrc.width();
        float earthX = (width - earthWidth) * 0.5f;
        float earthY = (height - earthHeight) * 0.5f;
        RectF earthRect = new RectF(earthX, earthY, earthX + earthWidth, earthY + earthHeight);

        drawEarthShadow(canvas, earthRect);
        drawEarthGlow(canvas, earthRect);

        bitmapPaint.setAlpha((int) (255 * (1f - blend)));
        canvas.drawBitmap(earthFrames[current], currentSrc, earthRect, bitmapPaint);
        bitmapPaint.setAlpha((int) (255 * blend));
        canvas.drawBitmap(earthFrames[next], nextSrc, earthRect, bitmapPaint);
        bitmapPaint.setAlpha(255);

        drawEarthHighlights(canvas, earthRect);
    }

    private void drawBackground(Canvas canvas, int width, int height) {
        float parallaxX = bgShiftCurrentX * 26f;
        float parallaxY = bgShiftCurrentY * 20f;
        RectF bgRect = new RectF(-22f + parallaxX, -22f + parallaxY, width + 22f + parallaxX, height + 22f + parallaxY);
        backgroundPaint.setAlpha(238);
        canvas.drawBitmap(background, null, bgRect, backgroundPaint);

        Paint vignette = new Paint(Paint.ANTI_ALIAS_FLAG);
        vignette.setShader(new RadialGradient(
                width * 0.5f,
                height * 0.48f,
                Math.max(width, height) * 0.70f,
                new int[]{Color.argb(0, 0, 0, 0), Color.argb(150, 0, 5, 12)},
                new float[]{0.55f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0f, 0f, width, height, vignette);
    }

    private void drawEarthShadow(Canvas canvas, RectF earthRect) {
        RectF shadowRect = new RectF(
                earthRect.left + earthRect.width() * 0.13f,
                earthRect.bottom - earthRect.height() * 0.06f,
                earthRect.right - earthRect.width() * 0.13f,
                earthRect.bottom + earthRect.height() * 0.16f
        );
        shadowPaint.setShader(new RadialGradient(
                shadowRect.centerX(),
                shadowRect.centerY(),
                shadowRect.width() * 0.52f,
                new int[]{Color.argb(95, 0, 0, 0), Color.argb(0, 0, 0, 0)},
                new float[]{0f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawOval(shadowRect, shadowPaint);
    }

    private void drawEarthGlow(Canvas canvas, RectF earthRect) {
        float glowRadius = earthRect.width() * 0.62f;
        glowPaint.setShader(new RadialGradient(
                earthRect.centerX() + earthRect.width() * 0.08f,
                earthRect.centerY() - earthRect.height() * 0.08f,
                glowRadius,
                new int[]{Color.argb(48, 75, 180, 255), Color.argb(0, 75, 180, 255)},
                new float[]{0.72f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawCircle(earthRect.centerX(), earthRect.centerY(), glowRadius, glowPaint);
    }

    private void drawEarthHighlights(Canvas canvas, RectF earthRect) {
        highlightPaint.setShader(new LinearGradient(
                earthRect.left,
                earthRect.top,
                earthRect.right,
                earthRect.bottom,
                new int[]{Color.argb(48, 255, 255, 255), Color.argb(0, 255, 255, 255), Color.argb(58, 0, 0, 0)},
                new float[]{0.05f, 0.45f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawOval(earthRect, highlightPaint);

        Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(Math.max(2f, earthRect.width() * 0.008f));
        rimPaint.setColor(Color.argb(120, 90, 185, 255));
        canvas.drawOval(earthRect, rimPaint);
    }

    private void drawStars(Canvas canvas, int width, int height) {
        double seconds = (System.currentTimeMillis() - startTime) / 1000.0;
        for (int i = 0; i < stars.size(); i++) {
            Star star = stars.get(i);
            if (!interactionPaused) {
                star.phase += star.speed;
            }
            float alphaFactor = (float) (0.60 + 0.40 * Math.sin(star.phase));
            int alpha = (int) (star.baseAlpha * alphaFactor);
            starPaint.setColor(Color.argb(alpha, 210, 230, 255));
            float driftX = interactionPaused ? 0f : (float) Math.sin(seconds * 0.05 + star.phase) * 1.2f;
            float driftY = interactionPaused ? 0f : (float) Math.cos(seconds * 0.05 + star.phase) * 0.8f;
            canvas.drawCircle(star.x * width + driftX, star.y * height + driftY, star.radius, starPaint);
        }
    }

    private void buildStars() {
        stars.clear();
        for (int i = 0; i < 120; i++) {
            Star star = new Star();
            star.x = random.nextFloat();
            star.y = random.nextFloat();
            star.radius = random.nextFloat() < 0.82f ? 1.1f + random.nextFloat() * 1.4f : 2.2f + random.nextFloat() * 1.8f;
            star.baseAlpha = 70 + random.nextInt(110);
            star.phase = (float) (random.nextFloat() * Math.PI * 2.0);
            star.speed = 0.010f + random.nextFloat() * 0.022f;
            stars.add(star);
        }
    }

    private static Rect findOpaqueBounds(Bitmap bitmap, int alphaThreshold) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int left = width;
        int top = height;
        int right = -1;
        int bottom = -1;

        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int y = 0; y < height; y++) {
            int offset = y * width;
            for (int x = 0; x < width; x++) {
                int alpha = (pixels[offset + x] >>> 24) & 0xFF;
                if (alpha >= alphaThreshold) {
                    if (x < left) left = x;
                    if (x > right) right = x;
                    if (y < top) top = y;
                    if (y > bottom) bottom = y;
                }
            }
        }

        if (right < left || bottom < top) {
            return new Rect(0, 0, width, height);
        }
        return new Rect(left, top, right + 1, bottom + 1);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class Star {
        float x;
        float y;
        float radius;
        int baseAlpha;
        float phase;
        float speed;
    }
}
