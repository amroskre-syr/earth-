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
    private static final int FRAME_COUNT = 72;

    // The reference video shows an almost imperceptible automatic rotation.
    // Keep it continuous, but much slower than the previous build.
    private static final double EARTH_CYCLE_SECONDS = 720.0;
    private static final double CLOUD_CYCLE_SECONDS = 480.0;

    // Measured from the supplied reference video: visible globe is about
    // 1.04x the screen width and centered very close to mid-height.
    private static final float EARTH_WIDTH_RATIO = 1.055f;
    private static final float EARTH_CENTER_Y = 0.50f;

    private static final float CLOUD_TOUCH_PHASE_LIMIT = 0.025f;
    private static final float CLOUD_VERTICAL_LIMIT_DP = 4.0f;

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
        targetZoom = clamp(1f + zoomSignal * 0.010f, 0.990f, 1.010f);
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

                // Touch affects clouds only. The Earth never rotates from touch.
                // The reference interaction is subtle, not a fast cloud spin.
                cloudTouchPhase += (dx / safeWidth) * 0.035f;
                cloudTouchPhase = clamp(cloudTouchPhase, -CLOUD_TOUCH_PHASE_LIMIT, CLOUD_TOUCH_PHASE_LIMIT);

                cloudVertical += dy * 0.040f;
                cloudVertical = clamp(cloudVertical, -dp(CLOUD_VERTICAL_LIMIT_DP), dp(CLOUD_VERTICAL_LIMIT_DP));

                // Store a small physical-looking inertia for release.
                cloudVelocity = (dx / safeWidth) * 0.20f;
                cloudVerticalVelocity = dy * 1.8f;

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
        float dt = lastFrameNs == 0L
                ? 1f / 60f
                : clamp((now - lastFrameNs) / 1_000_000_000f, 1f / 240f, .05f);
        lastFrameNs = now;
        update(dt);

        canvas.drawColor(Color.rgb(1, 4, 8));
        drawBackground(canvas, width, height);

        float diameter = width * EARTH_WIDTH_RATIO * zoom;

        // The Samsung launcher page swipe moves the whole wallpaper slightly,
        // exactly as seen in the reference, without changing Earth rotation.
        float pageX = -(launcherOffset - .5f) * dp(14f);
        float cx = width * .5f + tiltX * dp(13f) + pageX;
        float cy = height * EARTH_CENTER_Y + tiltY * dp(10f);

        RectF earthRect = new RectF(
                cx - diameter / 2f,
                cy - diameter / 2f,
                cx + diameter / 2f,
                cy + diameter / 2f
        );

        drawAtmosphere(canvas, earthRect);

        double elapsed = (now - startNs) / 1_000_000_000.0;
        drawEarthPair(canvas, mod1(elapsed / EARTH_CYCLE_SECONDS), earthRect);

        double pageNudge = (launcherOffset - .5f) * 0.010;
        double cloudPhase = mod1(elapsed / CLOUD_CYCLE_SECONDS + cloudTouchPhase + pageNudge);

        RectF cloudRect = new RectF(earthRect);
        float grow = earthRect.width() * .0035f;
        cloudRect.inset(-grow, -grow);
        cloudRect.offset(0f, cloudVertical);

        Path clip = new Path();
        clip.addOval(earthRect, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        drawCloudPair(canvas, cloudPhase, cloudRect, 118);
        canvas.restore();

        drawLighting(canvas, earthRect);
        drawVignette(canvas, width, height);
    }

    private void update(float dt) {
        float sensorFollow = 1f - (float) Math.exp(-8.5f * dt);
        tiltX += (targetTiltX - tiltX) * sensorFollow;
        tiltY += (targetTiltY - tiltY) * sensorFollow;
        zoom += (targetZoom - zoom) * sensorFollow;

        float launcherFollow = 1f - (float) Math.exp(-12f * dt);
        launcherOffset += (launcherTarget - launcherOffset) * launcherFollow;

        if (!touching) {
            cloudTouchPhase += cloudVelocity * dt;
            cloudVertical += cloudVerticalVelocity * dt;

            cloudVelocity *= (float) Math.exp(-5.5f * dt);
            cloudVerticalVelocity *= (float) Math.exp(-6.5f * dt);

            // Soft elastic return after the gesture, like the reference.
            cloudTouchPhase += (0f - cloudTouchPhase) * (1f - (float) Math.exp(-1.15f * dt));
            cloudVertical += (0f - cloudVertical) * (1f - (float) Math.exp(-2.2f * dt));
        }

        cloudTouchPhase = clamp(cloudTouchPhase, -CLOUD_TOUCH_PHASE_LIMIT, CLOUD_TOUCH_PHASE_LIMIT);
        cloudVertical = clamp(cloudVertical, -dp(CLOUD_VERTICAL_LIMIT_DP), dp(CLOUD_VERTICAL_LIMIT_DP));
    }

    private void drawBackground(Canvas c, int w, int h) {
        if (background == null) return;

        // Background moves less than Earth to create depth. Direction of the
        // launcher offset is matched to the page swipe in the original video.
        float px = tiltX * dp(7f) - (launcherOffset - .5f) * dp(8f);
        float py = tiltY * dp(5f);
        float over = dp(18f);

        drawCenterCrop(c, background,
                -over + px, -over + py,
                w + over + px, h + over + py,
                255);
    }

    private void drawEarthPair(Canvas c, double phase, RectF dst) {
        double pos = phase * FRAME_COUNT;
        int aIdx = ((int) Math.floor(pos)) % FRAME_COUNT;
        int bIdx = (aIdx + 1) % FRAME_COUNT;
        float blend = smooth((float) (pos - Math.floor(pos)));

        Bitmap a = earthCache.getFrame(aIdx);
        Bitmap b = earthCache.getFrame(bIdx);
        if (a == null) return;

        bitmapPaint.setAlpha(255);
        c.drawBitmap(a, null, dst, bitmapPaint);

        if (b != null && b != a && blend > .002f) {
            bitmapPaint.setAlpha(Math.round(255f * blend));
            c.drawBitmap(b, null, dst, bitmapPaint);
        }
        bitmapPaint.setAlpha(255);
    }

    private void drawCloudPair(Canvas c, double phase, RectF dst, int maxAlpha) {
        double pos = phase * FRAME_COUNT;
        int aIdx = ((int) Math.floor(pos)) % FRAME_COUNT;
        int bIdx = (aIdx + 1) % FRAME_COUNT;
        float blend = smooth((float) (pos - Math.floor(pos)));

        Bitmap a = cloudCache.getFrame(aIdx);
        Bitmap b = cloudCache.getFrame(bIdx);
        if (a == null) return;

        bitmapPaint.setAlpha(Math.round(maxAlpha * (1f - blend)));
        c.drawBitmap(a, null, dst, bitmapPaint);

        if (b != null && b != a && blend > .002f) {
            bitmapPaint.setAlpha(Math.round(maxAlpha * blend));
            c.drawBitmap(b, null, dst, bitmapPaint);
        }
        bitmapPaint.setAlpha(255);
    }

    private void drawAtmosphere(Canvas c, RectF r) {
        float radius = r.width() * .515f;
        fxPaint.setShader(new RadialGradient(
                r.centerX() + r.width() * .035f,
                r.centerY() - r.height() * .045f,
                radius,
                new int[]{
                        Color.argb(0, 70, 165, 245),
                        Color.argb(18, 62, 155, 235),
                        Color.argb(0, 18, 65, 110)
                },
                new float[]{.88f, .975f, 1f},
                Shader.TileMode.CLAMP
        ));
        c.drawCircle(r.centerX(), r.centerY(), radius, fxPaint);
        fxPaint.setShader(null);
    }

    private void drawLighting(Canvas c, RectF r) {
        Path clip = new Path();
        clip.addOval(r, Path.Direction.CW);
        c.save();
        c.clipPath(clip);

        fxPaint.setShader(new LinearGradient(
                r.left, r.bottom,
                r.right, r.top,
                new int[]{
                        Color.argb(112, 0, 0, 0),
                        Color.argb(44, 0, 2, 6),
                        Color.argb(0, 0, 0, 0),
                        Color.argb(8, 115, 190, 235)
                },
                new float[]{0f, .34f, .72f, 1f},
                Shader.TileMode.CLAMP
        ));
        c.drawOval(r, fxPaint);
        fxPaint.setShader(null);
        c.restore();

        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(Math.max(dp(.55f), r.width() * .0015f));
        rimPaint.setColor(Color.argb(58, 82, 172, 228));
        c.drawOval(r, rimPaint);
    }

    private void drawVignette(Canvas c, int w, int h) {
        fxPaint.setShader(new RadialGradient(
                w * .5f,
                h * .49f,
                Math.max(w, h) * .74f,
                new int[]{
                        Color.argb(0, 0, 0, 0),
                        Color.argb(22, 0, 2, 5),
                        Color.argb(72, 0, 2, 7)
                },
                new float[]{.52f, .84f, 1f},
                Shader.TileMode.CLAMP
        ));
        c.drawRect(0, 0, w, h, fxPaint);
        fxPaint.setShader(null);
    }

    private void drawCenterCrop(Canvas c, Bitmap bmp, float l, float t, float r, float b, int alpha) {
        float dw = r - l;
        float dh = b - t;
        float sa = bmp.getWidth() / (float) bmp.getHeight();
        float da = dw / dh;

        if (sa > da) {
            float wanted = bmp.getHeight() * da;
            float x = (bmp.getWidth() - wanted) / 2f;
            tmpSrc.set(Math.round(x), 0, Math.round(x + wanted), bmp.getHeight());
        } else {
            float wanted = bmp.getWidth() / da;
            float y = (bmp.getHeight() - wanted) / 2f;
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

        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Bitmap> e) {
            if (size() > max) {
                Bitmap b = e.getValue();
                if (b != null && !b.isRecycled()) b.recycle();
                return true;
            }
            return false;
        }
    }

    private float dp(float v) {
        return v * density;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double mod1(double v) {
        v %= 1.0;
        return v < 0 ? v + 1.0 : v;
    }

    private static float smooth(float x) {
        x = clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }
}
