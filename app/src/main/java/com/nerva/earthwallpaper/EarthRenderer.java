package com.nerva.earthwallpaper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class EarthRenderer {
    private static final int FRAME_COUNT = 36;
    private static final double EARTH_CYCLE_SECONDS = 150.0;
    private static final double CLOUD_CYCLE_SECONDS = 112.0;
    private static final float EARTH_WIDTH_RATIO = 0.92f;
    private static final float EARTH_CENTER_Y = 0.50f;

    private final Context context;
    private final float density;
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint fxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmpRect = new RectF();
    private final Rect tmpSrc = new Rect();
    private final Bitmap background;
    private final long startNs = System.nanoTime();

    private final FrameCache earthCache = new FrameCache(4, "earth_");
    private final FrameCache cloudCache = new FrameCache(4, "cloud_");

    private float targetTiltX, targetTiltY, tiltX, tiltY;
    private float targetZoom = 1f, zoom = 1f;
    private float launcherOffset = 0.5f, launcherTarget = 0.5f;
    private boolean touching;
    private float lastTouchX, lastTouchY;
    private float cloudTouchPhase;
    private float cloudVelocity;
    private float cloudVertical;
    private float cloudVerticalVelocity;
    private long lastFrameNs;

    EarthRenderer(Context context) {
        this.context = context.getApplicationContext();
        this.density = context.getResources().getDisplayMetrics().density;
        this.background = decodeByName("space_bg");
    }

    void setSensorInput(float horizontal, float vertical, float zoomSignal) {
        targetTiltX = clamp(horizontal, -1f, 1f);
        targetTiltY = clamp(vertical, -1f, 1f);
        targetZoom = clamp(1f + zoomSignal * 0.018f, 0.982f, 1.018f);
    }

    void addGyro(float x, float y) {
        targetTiltX = clamp(targetTiltX + x * 0.11f, -1f, 1f);
        targetTiltY = clamp(targetTiltY + y * 0.11f, -1f, 1f);
    }

    void setLauncherOffset(float xOffset) {
        launcherTarget = clamp(xOffset, 0f, 1f);
    }

    boolean onTouch(MotionEvent e, int width) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touching = true;
                lastTouchX = e.getX();
                lastTouchY = e.getY();
                cloudVelocity = 0f;
                cloudVerticalVelocity = 0f;
                return true;
            case MotionEvent.ACTION_MOVE:
                float x = e.getX();
                float y = e.getY();
                float dx = x - lastTouchX;
                float dy = y - lastTouchY;
                float safeWidth = Math.max(1f, width);
                cloudTouchPhase += (dx / safeWidth) * 0.20f;
                cloudVertical += dy * 0.10f;
                cloudVertical = clamp(cloudVertical, -dp(18), dp(18));
                cloudVelocity = (dx / safeWidth) * 0.012f;
                cloudVerticalVelocity = dy * 0.025f;
                lastTouchX = x;
                lastTouchY = y;
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                touching = false;
                return true;
            default:
                return false;
        }
    }

    void draw(Canvas canvas, int width, int height) {
        if (width <= 0 || height <= 0) return;
        long now = System.nanoTime();
        float dt = lastFrameNs == 0L ? 1f / 60f : clamp((now - lastFrameNs) / 1_000_000_000f, 1f / 240f, .05f);
        lastFrameNs = now;
        update(dt);

        canvas.drawColor(Color.rgb(1, 4, 9));
        drawBackground(canvas, width, height);

        float diameter = width * EARTH_WIDTH_RATIO * zoom;
        float cx = width * .5f + tiltX * dp(13f);
        float cy = height * EARTH_CENTER_Y + tiltY * dp(10f);
        RectF earthRect = new RectF(cx - diameter / 2f, cy - diameter / 2f, cx + diameter / 2f, cy + diameter / 2f);

        drawAtmosphere(canvas, earthRect);

        double elapsed = (now - startNs) / 1_000_000_000.0;
        drawFramePair(canvas, earthCache, mod1(elapsed / EARTH_CYCLE_SECONDS), earthRect, 255);

        double pageNudge = (launcherOffset - .5f) * 0.035;
        double cloudPhase = mod1(elapsed / CLOUD_CYCLE_SECONDS + cloudTouchPhase + pageNudge);
        RectF cloudRect = new RectF(earthRect);
        float grow = earthRect.width() * .008f;
        cloudRect.inset(-grow, -grow);
        cloudRect.offset(0f, cloudVertical);
        Path clip = new Path();
        clip.addOval(earthRect, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        drawFramePair(canvas, cloudCache, cloudPhase, cloudRect, 185);
        canvas.restore();

        drawLighting(canvas, earthRect);
        drawVignette(canvas, width, height);
    }

    private void update(float dt) {
        float s = 1f - (float) Math.exp(-8.5f * dt);
        tiltX += (targetTiltX - tiltX) * s;
        tiltY += (targetTiltY - tiltY) * s;
        zoom += (targetZoom - zoom) * s;
        launcherOffset += (launcherTarget - launcherOffset) * (1f - (float) Math.exp(-12f * dt));

        if (!touching) {
            cloudTouchPhase += cloudVelocity;
            cloudVertical += cloudVerticalVelocity;
            cloudVelocity *= (float) Math.pow(.91, dt * 60f);
            cloudVerticalVelocity *= (float) Math.pow(.87, dt * 60f);
            cloudVertical += (0f - cloudVertical) * (1f - (float) Math.exp(-1.8f * dt));
            cloudTouchPhase *= (float) Math.pow(.997, dt * 60f);
        }
        cloudVertical = clamp(cloudVertical, -dp(18), dp(18));
    }

    private void drawBackground(Canvas c, int w, int h) {
        if (background == null) return;
        float px = tiltX * dp(7f) + (launcherOffset - .5f) * dp(10f);
        float py = tiltY * dp(5f);
        float over = dp(24f);
        drawCenterCrop(c, background, -over + px, -over + py, w + over + px, h + over + py, 255);
    }

    private void drawFramePair(Canvas c, FrameCache cache, double phase, RectF dst, int maxAlpha) {
        double pos = phase * FRAME_COUNT;
        int aIdx = ((int) Math.floor(pos)) % FRAME_COUNT;
        int bIdx = (aIdx + 1) % FRAME_COUNT;
        float blend = smooth((float) (pos - Math.floor(pos)));
        Bitmap a = cache.getFrame(aIdx);
        Bitmap b = cache.getFrame(bIdx);
        if (a == null) return;
        bitmapPaint.setAlpha(maxAlpha);
        c.drawBitmap(a, null, dst, bitmapPaint);
        if (b != null && b != a && blend > .002f) {
            bitmapPaint.setAlpha(Math.round(maxAlpha * blend));
            c.drawBitmap(b, null, dst, bitmapPaint);
        }
        bitmapPaint.setAlpha(255);
    }

    private void drawAtmosphere(Canvas c, RectF r) {
        float radius = r.width() * .545f;
        fxPaint.setShader(new RadialGradient(r.centerX() + r.width() * .04f, r.centerY() - r.height() * .05f, radius,
                new int[]{Color.argb(0, 70, 170, 255), Color.argb(38, 58, 150, 245), Color.argb(0, 20, 70, 120)},
                new float[]{.80f, .94f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(r.centerX(), r.centerY(), radius, fxPaint);
        fxPaint.setShader(null);
    }

    private void drawLighting(Canvas c, RectF r) {
        Path clip = new Path();
        clip.addOval(r, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        fxPaint.setShader(new LinearGradient(r.left, r.bottom, r.right, r.top,
                new int[]{Color.argb(90, 0, 0, 0), Color.argb(28, 0, 2, 6), Color.argb(0, 0, 0, 0), Color.argb(12, 130, 205, 255)},
                new float[]{0f, .32f, .70f, 1f}, Shader.TileMode.CLAMP));
        c.drawOval(r, fxPaint);
        fxPaint.setShader(null);
        c.restore();
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(Math.max(dp(.8f), r.width() * .0023f));
        rimPaint.setColor(Color.argb(96, 82, 175, 235));
        c.drawOval(r, rimPaint);
    }

    private void drawVignette(Canvas c, int w, int h) {
        fxPaint.setShader(new RadialGradient(w * .5f, h * .49f, Math.max(w, h) * .72f,
                new int[]{Color.argb(0, 0, 0, 0), Color.argb(28, 0, 2, 6), Color.argb(86, 0, 2, 7)},
                new float[]{.52f, .82f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, fxPaint);
        fxPaint.setShader(null);
    }

    private void drawCenterCrop(Canvas c, Bitmap bmp, float l, float t, float r, float b, int alpha) {
        float dw = r - l, dh = b - t, sa = bmp.getWidth() / (float) bmp.getHeight(), da = dw / dh;
        if (sa > da) {
            float wanted = bmp.getHeight() * da, x = (bmp.getWidth() - wanted) / 2f;
            tmpSrc.set(Math.round(x), 0, Math.round(x + wanted), bmp.getHeight());
        } else {
            float wanted = bmp.getWidth() / da, y = (bmp.getHeight() - wanted) / 2f;
            tmpSrc.set(0, Math.round(y), bmp.getWidth(), Math.round(y + wanted));
        }
        tmpRect.set(l, t, r, b);
        bitmapPaint.setAlpha(alpha);
        c.drawBitmap(bmp, tmpSrc, tmpRect, bitmapPaint);
        bitmapPaint.setAlpha(255);
    }

    private Bitmap decodeByName(String name) {
        int id = context.getResources().getIdentifier(name, "drawable", context.getPackageName());
        if (id == 0) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        return BitmapFactory.decodeResource(context.getResources(), id, o);
    }

    private final class FrameCache extends LinkedHashMap<Integer, Bitmap> {
        private final int max;
        private final String prefix;
        FrameCache(int max, String prefix) {
            super(8, .75f, true);
            this.max = max;
            this.prefix = prefix;
        }
        Bitmap getFrame(int index) {
            Bitmap b = super.get(index);
            if (b != null) return b;
            b = decodeByName(prefix + String.format(Locale.US, "%02d", index));
            if (b != null) put(index, b);
            return b;
        }
        @Override protected boolean removeEldestEntry(Map.Entry<Integer, Bitmap> e) {
            if (size() > max) {
                Bitmap b = e.getValue();
                if (b != null && !b.isRecycled()) b.recycle();
                return true;
            }
            return false;
        }
    }

    private float dp(float v) { return v * density; }
    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
    private static double mod1(double v) { v %= 1.0; return v < 0 ? v + 1.0 : v; }
    private static float smooth(float x) { x = clamp(x, 0f, 1f); return x * x * (3f - 2f * x); }
}
