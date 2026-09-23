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
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;

public class MainActivity extends Activity implements SensorEventListener {
    private EarthView earthView;
    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private Sensor gyroSensor;
    private boolean usingRotationVector;
    private final float[] rotationMatrix = new float[9];
    private final float[] orientation = new float[3];

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        FrameLayout root = new FrameLayout(this);
        earthView = new EarthView();
        root.addView(earthView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        Button applyButton = new Button(this);
        applyButton.setText("تعيين كخلفية متحركة");
        applyButton.setTextColor(Color.WHITE);
        applyButton.setTextSize(16f);
        applyButton.setAllCaps(false);
        applyButton.setPadding(dp(24), dp(12), dp(24), dp(12));

        GradientDrawable buttonBackground = new GradientDrawable();
        buttonBackground.setColor(Color.argb(210, 8, 25, 45));
        buttonBackground.setCornerRadius(dp(24));
        buttonBackground.setStroke(dp(1), Color.argb(190, 102, 191, 255));
        applyButton.setBackground(buttonBackground);

        FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        buttonParams.bottomMargin = dp(42);
        root.addView(applyButton, buttonParams);

        applyButton.setOnClickListener(v -> {
            Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            intent.putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    new ComponentName(this, EarthWallpaperService.class));
            startActivity(intent);
        });

        setContentView(root);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        usingRotationVector = rotationSensor != null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null) {
            Sensor sensor = usingRotationVector ? rotationSensor : gyroSensor;
            if (sensor != null) {
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
            }
        }
        if (earthView != null) earthView.setRunning(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) sensorManager.unregisterListener(this);
        if (earthView != null) earthView.setRunning(false);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (earthView == null) return;

        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            SensorManager.getOrientation(rotationMatrix, orientation);
            float pitch = orientation[1];
            float roll = orientation[2];
            earthView.renderer.setTilt(roll / 0.75f, -pitch / 0.75f);
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            earthView.renderer.addGyro(event.values[1] * 0.018f, event.values[0] * 0.018f);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class EarthView extends View {
        private final EarthRenderer renderer = new EarthRenderer(MainActivity.this);
        private boolean running = true;

        EarthView() {
            super(MainActivity.this);
            setFocusable(true);
        }

        void setRunning(boolean value) {
            running = value;
            if (running) postInvalidateOnAnimation();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            renderer.draw(canvas, getWidth(), getHeight());
            if (running) postInvalidateOnAnimation();
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
