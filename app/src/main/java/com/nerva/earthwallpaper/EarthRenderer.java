package com.nerva.earthwallpaper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;

import java.util.ArrayList;

final class EarthRenderer {
    private final Bitmap background;
    private final Bitmap[] earthFrames = new Bitmap[5];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final ArrayList<Spark> sparks = new ArrayList<>();

    private float tiltX;
    private float tiltY;
    private float drag;
    private float lastTouchX;
    private long lastSparkTime;
    private final long startTime = System.currentTimeMillis();

    EarthRenderer(Context context) {
        background = BitmapFactory.decodeResource(context.getResources(), R.drawable.space_bg);
        int[] ids = {
                R.drawable.earth_0,
                R.drawable.earth_1,
                R.drawable.earth_2,
                R.drawable.earth_3,
                R.drawable.earth_4
        };
        for (int i = 0; i < ids.length; i++) {
            earthFrames[i] = BitmapFactory.decodeResource(context.getResources(), ids[i]);
        }
    }

    void setTilt(float x, float y) {
        tiltX = clamp(x, -1f, 1f);
        tiltY = clamp(y, -1f, 1f);
    }

    void addGyro(float x, float y) {
        tiltX = clamp((tiltX + x) * 0.985f, -1f, 1f);
        tiltY = clamp((tiltY + y) * 0.985f, -1f, 1f);
    }

    boolean onTouch(MotionEvent event, int width) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                lastSparkTime = System.currentTimeMillis();
                addSpark(event.getX(), event.getY());
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - lastTouchX;
                lastTouchX = event.getX();
                if (width > 0) {
                    drag += (dx / width) * 0.85f;
                }
                long now = System.currentTimeMillis();
                if (now - lastSparkTime > 35) {
                    addSpark(event.getX(), event.getY());
                    lastSparkTime = now;
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                return true;
            default:
                return false;
        }
    }

    void draw(Canvas canvas, int width, int height) {
        if (width <= 0 || height <= 0) return;

        canvas.drawColor(Color.BLACK);

        float bgX = tiltX * 26f;
        float bgY = tiltY * 18f;
        paint.setAlpha(255);
        canvas.drawBitmap(background, null,
                new RectF(-45f + bgX, -45f + bgY,
                        width + 45f + bgX, height + 45f + bgY), paint);

        double seconds = (System.currentTimeMillis() - startTime) / 1000.0;
        double phase = (seconds / 24.0 + drag) % 1.0;
        if (phase < 0) phase += 1.0;

        float framePosition = (float) (phase * earthFrames.length);
        int current = ((int) Math.floor(framePosition)) % earthFrames.length;
        int next = (current + 1) % earthFrames.length;
        float blend = framePosition - (float) Math.floor(framePosition);

        Bitmap currentBitmap = earthFrames[current];
        float earthWidth = width * 1.08f;
        float earthHeight = earthWidth * currentBitmap.getHeight() / currentBitmap.getWidth();
        float earthX = (width - earthWidth) / 2f + tiltX * 42f;
        float earthY = height * 0.13f + tiltY * 28f;
        RectF earthRect = new RectF(earthX, earthY, earthX + earthWidth, earthY + earthHeight);

        paint.setAlpha((int) (255 * (1f - blend)));
        canvas.drawBitmap(currentBitmap, null, earthRect, paint);
        paint.setAlpha((int) (255 * blend));
        canvas.drawBitmap(earthFrames[next], null, earthRect, paint);
        paint.setAlpha(255);

        paint.setStyle(Paint.Style.FILL);
        for (int i = sparks.size() - 1; i >= 0; i--) {
            Spark spark = sparks.get(i);
            spark.x += spark.vx;
            spark.y += spark.vy;
            spark.life -= 0.035f;
            if (spark.life <= 0f) {
                sparks.remove(i);
                continue;
            }
            paint.setColor(Color.argb((int) (185 * spark.life), 180, 225, 255));
            canvas.drawCircle(spark.x, spark.y, 3f + 7f * (1f - spark.life), paint);
        }
        paint.setAlpha(255);
    }

    private void addSpark(float x, float y) {
        for (int i = 0; i < 8; i++) {
            sparks.add(new Spark(
                    x,
                    y,
                    (float) (Math.random() - 0.5) * 5f,
                    (float) (Math.random() - 0.5) * 5f));
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class Spark {
        float x;
        float y;
        final float vx;
        final float vy;
        float life = 1f;

        Spark(float x, float y, float vx, float vy) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
        }
    }
}
