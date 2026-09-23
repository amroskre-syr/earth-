from pathlib import Path

ROOT = Path('.')


def edit(path, replacements):
    p = ROOT / path
    text = p.read_text(encoding='utf-8')
    for label, old, new in replacements:
        if old not in text:
            raise RuntimeError(f'{path}: missing patch target: {label}')
        text = text.replace(old, new, 1)
    p.write_text(text, encoding='utf-8')
    print('patched', path)


edit('app/src/main/java/com/nerva/earthwallpaper/EarthRenderer.java', [
    ('cloud cycle', 'private static final double CLOUD_CYCLE_SECONDS = 480.0;', 'private static final double CLOUD_CYCLE_SECONDS = 500.0;'),
    ('earth width', 'private static final float EARTH_WIDTH_RATIO = 1.055f;', 'private static final float EARTH_WIDTH_RATIO = 0.835f;'),
    ('earth center', 'private static final float EARTH_CENTER_Y = 0.50f;', 'private static final float EARTH_CENTER_Y = 0.510f;'),
    ('touch phase limit', 'private static final float CLOUD_TOUCH_PHASE_LIMIT = 0.025f;', 'private static final float CLOUD_TOUCH_PHASE_LIMIT = 0.030f;'),
    ('cloud vertical limit', 'private static final float CLOUD_VERTICAL_LIMIT_DP = 4.0f;', 'private static final float CLOUD_VERTICAL_LIMIT_DP = 5.0f;'),
    ('sensor zoom', 'targetZoom = clamp(1f + zoomSignal * 0.010f, 0.990f, 1.010f);', 'targetZoom = clamp(1f + zoomSignal * 0.009f, 0.991f, 1.009f);'),
    ('gyro x', 'targetTiltX = clamp(targetTiltX + x * 0.11f, -1f, 1f);', 'targetTiltX = clamp(targetTiltX + x * 0.10f, -1f, 1f);'),
    ('gyro y', 'targetTiltY = clamp(targetTiltY + y * 0.11f, -1f, 1f);', 'targetTiltY = clamp(targetTiltY + y * 0.10f, -1f, 1f);'),
    ('touch phase gain', 'cloudTouchPhase += (dx / safeWidth) * 0.035f;', 'cloudTouchPhase += (dx / safeWidth) * 0.040f;'),
    ('touch vertical gain', 'cloudVertical += dy * 0.040f;', 'cloudVertical += dy * 0.043f;'),
    ('touch inertia x', 'cloudVelocity = (dx / safeWidth) * 0.20f;', 'cloudVelocity = (dx / safeWidth) * 0.22f;'),
    ('touch inertia y', 'cloudVerticalVelocity = dy * 1.8f;', 'cloudVerticalVelocity = dy * 1.9f;'),
    ('page parallax', 'float pageX = -(launcherOffset - .5f) * dp(14f);', 'float pageX = -(launcherOffset - .5f) * dp(10f);'),
    ('earth parallax x', 'float cx = width * .5f + tiltX * dp(13f) + pageX;', 'float cx = width * .5f + tiltX * dp(11f) + pageX;'),
    ('earth parallax y', 'float cy = height * EARTH_CENTER_Y + tiltY * dp(10f);', 'float cy = height * EARTH_CENTER_Y + tiltY * dp(8f);'),
    ('page cloud nudge', 'double pageNudge = (launcherOffset - .5f) * 0.010;', 'double pageNudge = (launcherOffset - .5f) * 0.008;'),
    ('cloud grow', 'float grow = earthRect.width() * .0035f;', 'float grow = earthRect.width() * .004f;'),
    ('cloud opacity', 'drawCloudPair(canvas, cloudPhase, cloudRect, 118);', 'drawCloudPair(canvas, cloudPhase, cloudRect, 245);'),
    ('sensor follow', 'float sensorFollow = 1f - (float) Math.exp(-8.5f * dt);', 'float sensorFollow = 1f - (float) Math.exp(-8.0f * dt);'),
    ('launcher follow', 'float launcherFollow = 1f - (float) Math.exp(-12f * dt);', 'float launcherFollow = 1f - (float) Math.exp(-11f * dt);'),
    ('cloud decay x', 'cloudVelocity *= (float) Math.exp(-5.5f * dt);', 'cloudVelocity *= (float) Math.exp(-5.3f * dt);'),
    ('cloud decay y', 'cloudVerticalVelocity *= (float) Math.exp(-6.5f * dt);', 'cloudVerticalVelocity *= (float) Math.exp(-6.2f * dt);'),
    ('cloud return x', 'Math.exp(-1.15f * dt)', 'Math.exp(-1.10f * dt)'),
    ('cloud return y', 'Math.exp(-2.2f * dt)', 'Math.exp(-2.0f * dt)'),
    ('background x', 'float px = tiltX * dp(7f) - (launcherOffset - .5f) * dp(8f);', 'float px = tiltX * dp(6f) - (launcherOffset - .5f) * dp(6f);'),
    ('background y', 'float py = tiltY * dp(5f);', 'float py = tiltY * dp(4f);'),
    ('background overscan', 'float over = dp(18f);', 'float over = dp(14f);'),
    ('atmosphere method', '''    private void drawAtmosphere(Canvas c, RectF r) {
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
''', '''    private void drawAtmosphere(Canvas c, RectF r) {
        float cx = r.centerX();
        float cy = r.centerY();
        float radius = r.width() * .60f;

        fxPaint.setShader(new RadialGradient(
                cx, cy, radius,
                new int[]{
                        Color.argb(0, 35, 120, 205),
                        Color.argb(18, 36, 122, 205),
                        Color.argb(12, 32, 104, 180),
                        Color.argb(0, 20, 70, 125)
                },
                new float[]{.72f, .84f, .93f, 1f},
                Shader.TileMode.CLAMP
        ));
        c.drawCircle(cx, cy, radius, fxPaint);
        fxPaint.setShader(null);

        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(Math.max(dp(.55f), r.width() * .0012f));
        float[] scales = {1.045f, 1.090f, 1.145f};
        int[] colors = {
                Color.argb(78, 78, 165, 225),
                Color.argb(57, 64, 145, 210),
                Color.argb(32, 52, 128, 195)
        };
        for (int i = 0; i < scales.length; i++) {
            float hw = r.width() * scales[i] * .5f;
            float hh = r.height() * scales[i] * .5f;
            tmpRect.set(cx - hw, cy - hh, cx + hw, cy + hh);
            rimPaint.setColor(colors[i]);
            c.drawOval(tmpRect, rimPaint);
        }
    }
'''),
    ('lighting shadow 1', 'Color.argb(112, 0, 0, 0),', 'Color.argb(72, 0, 0, 0),'),
    ('lighting shadow 2', 'Color.argb(44, 0, 2, 6),', 'Color.argb(22, 0, 2, 7),'),
    ('lighting highlight', 'Color.argb(8, 115, 190, 235)', 'Color.argb(10, 110, 185, 235)'),
    ('rim stroke', 'rimPaint.setStrokeWidth(Math.max(dp(.55f), r.width() * .0015f));', 'rimPaint.setStrokeWidth(Math.max(dp(.75f), r.width() * .0017f));'),
    ('rim color', 'rimPaint.setColor(Color.argb(58, 82, 172, 228));', 'rimPaint.setColor(Color.argb(92, 88, 178, 235));'),
    ('vignette center', 'h * .49f,', 'h * .50f,'),
    ('vignette radius', 'Math.max(w, h) * .74f,', 'Math.max(w, h) * .76f,'),
    ('vignette mid', 'Color.argb(22, 0, 2, 5),', 'Color.argb(14, 0, 2, 6),'),
    ('vignette edge', 'Color.argb(72, 0, 2, 7)', 'Color.argb(48, 0, 2, 7)'),
    ('vignette stops', 'new float[]{.52f, .84f, 1f},', 'new float[]{.55f, .85f, 1f},'),
])

edit('app/src/main/java/com/nerva/earthwallpaper/MainActivity.java', [
    ('button size', 'apply.setTextSize(16f);', 'apply.setTextSize(15.5f);'),
    ('button padding', 'apply.setPadding(dp(24), dp(12), dp(24), dp(12));', 'apply.setPadding(dp(25), dp(12), dp(25), dp(12));'),
    ('button bottom', 'params.bottomMargin = dp(38);', 'params.bottomMargin = dp(24);'),
])

edit('scripts/generate_assets.py', [
    ('user agent', "'User-Agent': 'NervaEarthLive/3.1'", "'User-Agent': 'NervaEarthLive/4.0'"),
    ('cloud normalize', '''    a = np.clip((a - 0.22) / 0.72, 0, 1)
    a = np.power(a, 1.25)
    a = np.uint8(a * 220)
    return Image.fromarray(a, 'L').filter(ImageFilter.GaussianBlur(0.8))''', '''    a = np.clip((a - 0.14) / 0.64, 0, 1)
    a = np.power(a, 1.02)
    a = np.uint8(a * 255)
    return Image.fromarray(a, 'L').filter(ImageFilter.GaussianBlur(0.65))'''),
    ('star count', 'for _ in range(760):', 'for _ in range(1020):'),
    ('star brightness', 'b = int(rng.integers(90, 205))', 'b = int(rng.integers(95, 230))'),
    ('star point threshold', 'if u < .90:', 'if u < .57:'),
    ('star medium threshold', 'elif u < .985:', 'elif u < .90:'),
    ('glow count', 'for _ in range(10):', 'for _ in range(18):'),
    ('ocean grade', '''    rgb[..., 0] = np.where(ocean, np.maximum(r * .90, b * .22), r)
    rgb[..., 1] = np.where(ocean, np.maximum(g * 1.02, b * .68), g)
    rgb[..., 2] = np.where(ocean, b * 1.02, b)''', '''    rgb[..., 0] = np.where(ocean, np.maximum(r * .98, b * .20), r)
    rgb[..., 1] = np.where(ocean, np.maximum(g * .72, b * .28), g)
    rgb[..., 2] = np.where(ocean, b * 1.22, b)'''),
    ('earth shade', 'shade = 0.145 + 0.93 * np.power(ndotl, 0.60)', 'shade = 0.13 + 0.95 * np.power(ndotl, 0.60)'),
    ('earth spec', 'spec = np.power(ndotl, 18.0) * ocean * 10.0', 'spec = np.power(ndotl, 18.0) * ocean * 8.0'),
    ('earth rim g', 'rgb[..., 1] += rim * 8', 'rgb[..., 1] += rim * 7'),
    ('earth rim b', 'rgb[..., 2] += rim * 16', 'rgb[..., 2] += rim * 15'),
    ('earth color', 'rgb_image = ImageEnhance.Color(rgb_image).enhance(0.98)', 'rgb_image = ImageEnhance.Color(rgb_image).enhance(1.035)'),
    ('earth contrast', 'rgb_image = ImageEnhance.Contrast(rgb_image).enhance(1.015)', 'rgb_image = ImageEnhance.Contrast(rgb_image).enhance(1.035)'),
    ('cloud alpha block', '''    alpha = lum * (0.45 + 0.55 * ndotl)
    alpha *= np.power(np.clip(Z, 0, 1), 0.18)
    alpha *= mask
    alpha = np.uint8(np.clip(alpha, 0, 1) * 215)''', '''    alpha = lum * (0.58 + 0.42 * ndotl)
    alpha *= np.power(np.clip(Z, 0, 1), 0.12)
    alpha *= mask
    alpha = np.clip(alpha * 1.80, 0, 1)
    alpha = np.power(alpha, 0.94)
    alpha = np.uint8(alpha * 238)'''),
    ('cloud red', 'cloud_rgb[..., 0] = 232', 'cloud_rgb[..., 0] = 220'),
    ('cloud green', 'cloud_rgb[..., 1] = 242', 'cloud_rgb[..., 1] = 232'),
    ('cloud blue', 'cloud_rgb[..., 2] = 248', 'cloud_rgb[..., 2] = 244'),
    ('cloud return', "return Image.fromarray(np.dstack([cloud_rgb, alpha]), 'RGBA')", "return Image.fromarray(np.dstack([cloud_rgb, alpha]), 'RGBA').filter(ImageFilter.GaussianBlur(0.32))"),
])

print('Reference-video patch applied successfully.')
