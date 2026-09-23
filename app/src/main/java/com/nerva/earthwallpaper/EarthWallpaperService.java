package com.nerva.earthwallpaper;

import android.graphics.Canvas;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

public class EarthWallpaperService extends WallpaperService {
    @Override
    public Engine onCreateEngine() {
        return new EarthEngine();
    }

    private final class EarthEngine extends Engine implements SensorEventListener {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final EarthRenderer renderer = new EarthRenderer(EarthWallpaperService.this);
        private final float[] rotationMatrix = new float[9];
        private final float[] orientation = new float[3];

        private final SensorManager sensorManager;
        private final Sensor rotationSensor;
        private final Sensor gyroSensor;
        private final boolean usingRotationVector;
        private boolean visible;

        private final Runnable drawFrame = this::frame;

        EarthEngine() {
            sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            usingRotationVector = rotationSensor != null;
            setTouchEventsEnabled(true);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            this.visible = visible;
            if (visible) {
                Sensor sensor = usingRotationVector ? rotationSensor : gyroSensor;
                if (sensor != null) {
                    sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
                }
                frame();
            } else {
                sensorManager.unregisterListener(this);
                handler.removeCallbacks(drawFrame);
            }
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            visible = false;
            sensorManager.unregisterListener(this);
            handler.removeCallbacks(drawFrame);
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
                SensorManager.getOrientation(rotationMatrix, orientation);
                float pitch = orientation[1];
                float roll = orientation[2];
                renderer.setTilt(roll / 0.75f, -pitch / 0.75f);
            } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                renderer.addGyro(event.values[1] * 0.018f, event.values[0] * 0.018f);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

        @Override
        public void onTouchEvent(MotionEvent event) {
            int width = getResources().getDisplayMetrics().widthPixels;
            renderer.onTouch(event, width);
            super.onTouchEvent(event);
        }

        private void frame() {
            if (!visible) return;

            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas != null) {
                    renderer.draw(canvas, canvas.getWidth(), canvas.getHeight());
                }
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas);
            }

            handler.removeCallbacks(drawFrame);
            handler.postDelayed(drawFrame, 33L);
        }
    }
}
