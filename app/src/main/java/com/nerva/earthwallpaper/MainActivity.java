package com.nerva.earthwallpaper;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;

public class MainActivity extends Activity implements SensorEventListener, Choreographer.FrameCallback {
    private PreviewView preview;
    private SensorManager sensorManager;
    private Sensor sensor;
    private boolean gyroFallback;
    private boolean running;
    private boolean calibrated;
    private float neutralPitch;
    private float neutralRoll;
    private float gyroX;
    private float gyroY;
    private final float[] matrix = new float[9];
    private final float[] orientation = new float[3];

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        FrameLayout root = new FrameLayout(this);
        preview = new PreviewView();
        root.addView(preview, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        Button apply = new Button(this);
        apply.setText("تعيين الخلفية المتحركة");
        apply.setTextColor(Color.WHITE);
        apply.setTextSize(16f);
        apply.setAllCaps(false);
        apply.setPadding(dp(24), dp(12), dp(24), dp(12));

        GradientDrawable buttonBg = new GradientDrawable();
        buttonBg.setColor(Color.argb(210, 5, 18, 32));
        buttonBg.setCornerRadius(dp(26));
        buttonBg.setStroke(dp(1), Color.argb(180, 88, 170, 235));
        apply.setBackground(buttonBg);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.bottomMargin = dp(38);
        root.addView(apply, params);

        apply.setOnClickListener(v -> {
            Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            intent.putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    new ComponentName(this, EarthWallpaperService.class));
            startActivity(intent);
        });

        setContentView(root);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        if (sensor == null) sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (sensor == null) {
            sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            gyroFallback = sensor != null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        running = true;
        calibrated = false;
        gyroX = 0f;
        gyroY = 0f;
        if (sensor != null) {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
        }
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        running = false;
        sensorManager.unregisterListener(this);
        Choreographer.getInstance().removeFrameCallback(this);
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!running) return;
        preview.invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor != sensor) return;

        if (!gyroFallback) {
            SensorManager.getRotationMatrixFromVector(matrix, event.values);
            SensorManager.getOrientation(matrix, orientation);
            float pitch = orientation[1];
            float roll = orientation[2];

            if (!calibrated) {
                neutralPitch = pitch;
                neutralRoll = roll;
                calibrated = true;
                return;
            }

            float deltaPitch = normalize(pitch - neutralPitch);
            float deltaRoll = normalize(roll - neutralRoll);
            float x = clamp(-deltaRoll / .24f, -1f, 1f);
            float y = clamp(deltaPitch / .24f, -1f, 1f);

            // Keep preview behavior exactly the same as the actual wallpaper.
            preview.renderer.setSensorInput(x, y, -y * .65f);
        } else {
            gyroX = (gyroX + event.values[1] * .010f) * .992f;
            gyroY = (gyroY + event.values[0] * .010f) * .992f;
            preview.renderer.setSensorInput(
                    clamp(gyroX, -1f, 1f),
                    clamp(gyroY, -1f, 1f),
                    0f);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float normalize(float angle) {
        while (angle > Math.PI) angle -= (float) (Math.PI * 2.0);
        while (angle < -Math.PI) angle += (float) (Math.PI * 2.0);
        return angle;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private final class PreviewView extends View {
        final EarthRenderer renderer = new EarthRenderer(MainActivity.this);

        PreviewView() {
            super(MainActivity.this);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            renderer.draw(canvas, getWidth(), getHeight());
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            boolean handled = renderer.onTouch(event, getWidth());
            if (event.getActionMasked() == MotionEvent.ACTION_UP) performClick();
            return handled || super.onTouchEvent(event);
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }
    }
}
