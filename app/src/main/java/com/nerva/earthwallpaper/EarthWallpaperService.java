package com.nerva.earthwallpaper;

import android.graphics.Canvas;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.service.wallpaper.WallpaperService;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

public class EarthWallpaperService extends WallpaperService {
    @Override
    public Engine onCreateEngine() {
        return new EarthEngine();
    }

    private final class EarthEngine extends Engine implements SensorEventListener, Choreographer.FrameCallback {
        private final EarthRenderer renderer = new EarthRenderer(EarthWallpaperService.this);
        private final SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        private Sensor sensor;
        private boolean gyroFallback;
        private boolean visible;
        private boolean framePosted;
        private boolean calibrated;
        private float neutralPitch;
        private float neutralRoll;
        private final float[] matrix = new float[9];
        private final float[] orientation = new float[3];
        private float gyroX;
        private float gyroY;

        EarthEngine() {
            sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
            if (sensor == null) sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            if (sensor == null) {
                sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
                gyroFallback = sensor != null;
            }
            setTouchEventsEnabled(true);
            setOffsetNotificationsEnabled(true);
        }

        @Override
        public void onVisibilityChanged(boolean value) {
            visible = value;
            if (value) {
                calibrated = false;
                gyroX = 0f;
                gyroY = 0f;
                if (sensor != null) {
                    sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
                }
                postFrame();
            } else {
                sensorManager.unregisterListener(this);
                removeFrame();
            }
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            visible = false;
            sensorManager.unregisterListener(this);
            removeFrame();
        }

        private void postFrame() {
            if (!visible || framePosted) return;
            framePosted = true;
            Choreographer.getInstance().postFrameCallback(this);
        }

        private void removeFrame() {
            if (!framePosted) return;
            Choreographer.getInstance().removeFrameCallback(this);
            framePosted = false;
        }

        @Override
        public void doFrame(long frameTimeNanos) {
            framePosted = false;
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
            postFrame();
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
                renderer.setSensorInput(x, y, -y * .65f);
            } else {
                gyroX = (gyroX + event.values[1] * .010f) * .992f;
                gyroY = (gyroY + event.values[0] * .010f) * .992f;
                renderer.setSensorInput(
                        clamp(gyroX, -1f, 1f),
                        clamp(gyroY, -1f, 1f),
                        0f);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

        @Override
        public void onTouchEvent(MotionEvent event) {
            renderer.onTouch(event, getResources().getDisplayMetrics().widthPixels);
            super.onTouchEvent(event);
        }

        @Override
        public void onOffsetsChanged(
                float xOffset,
                float yOffset,
                float xOffsetStep,
                float yOffsetStep,
                int xPixelOffset,
                int yPixelOffset
        ) {
            renderer.setLauncherOffset(xOffset);
            super.onOffsetsChanged(
                    xOffset,
                    yOffset,
                    xOffsetStep,
                    yOffsetStep,
                    xPixelOffset,
                    yPixelOffset);
        }

        private float normalize(float angle) {
            while (angle > Math.PI) angle -= (float) (Math.PI * 2.0);
            while (angle < -Math.PI) angle += (float) (Math.PI * 2.0);
            return angle;
        }

        private float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}
