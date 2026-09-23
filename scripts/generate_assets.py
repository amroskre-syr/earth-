from pathlib import Path
from urllib.request import urlopen, Request
from io import BytesIO
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageEnhance

OUT = Path('app/src/main/res/drawable-nodpi')
OUT.mkdir(parents=True, exist_ok=True)
W, H = 720, 1560
EARTH_SIZE = 720
RADIUS = 322
CENTER = (W // 2, H // 2)
NASA_URL = 'https://svs.gsfc.nasa.gov/vis/a000000/a002900/a002915/bluemarble-2048.png'


def download_texture():
    req = Request(NASA_URL, headers={'User-Agent': 'Earth3DLiveWallpaper/1.0'})
    with urlopen(req, timeout=60) as r:
        return Image.open(BytesIO(r.read())).convert('RGB')


def make_space_background():
    rng = np.random.default_rng(260923)
    yy, xx = np.mgrid[0:H, 0:W]
    img = np.zeros((H, W, 3), dtype=np.float32)
    img[..., 0] = 2.0
    img[..., 1] = 5.0
    img[..., 2] = 10.0

    # Subtle realistic blue nebula patches, much calmer than before.
    for cx, cy, sx, sy, strength in [
        (W * 0.84, H * 0.28, 130, 200, 24),
        (W * 0.18, H * 0.72, 120, 180, 14),
        (W * 0.88, H * 0.85, 150, 240, 30),
    ]:
        g = np.exp(-(((xx - cx) / sx) ** 2 + ((yy - cy) / sy) ** 2) * 1.25)
        img[..., 0] += g * strength * 0.11
        img[..., 1] += g * strength * 0.38
        img[..., 2] += g * strength * 0.90

    # Very subtle film-grain / space dust.
    noise = rng.normal(0, 0.9, (H, W, 1)).astype(np.float32)
    img += noise
    img = np.clip(img, 0, 255).astype(np.uint8)
    base = Image.fromarray(img, 'RGB')
    draw = ImageDraw.Draw(base)

    # Fewer fine stars for a cleaner and more believable look.
    for _ in range(850):
        x = int(rng.integers(0, W)); y = int(rng.integers(0, H))
        b = int(rng.integers(125, 230))
        r = 1 if rng.random() < 0.96 else 2
        c = (int(b * 0.84), int(b * 0.91), b)
        draw.ellipse((x - r, y - r, x + r, y + r), fill=c)

    # Select few bright stars with glow.
    glow = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    for _ in range(28):
        x = int(rng.integers(15, W - 15)); y = int(rng.integers(15, H - 15))
        gd.ellipse((x - 5, y - 5, x + 5, y + 5), fill=(85, 170, 255, 90))
        draw.ellipse((x - 1, y - 1, x + 1, y + 1), fill=(225, 242, 255))
    glow = glow.filter(ImageFilter.GaussianBlur(5))
    base = Image.alpha_composite(base.convert('RGBA'), glow).convert('RGB')
    base = ImageEnhance.Contrast(base).enhance(1.06)
    base.save(OUT / 'space_bg.png', optimize=True)


def globe_frame(texture: Image.Image, longitude_deg: float):
    tex = np.asarray(texture, dtype=np.float32)
    th, tw = tex.shape[:2]

    n = EARTH_SIZE
    y, x = np.mgrid[0:n, 0:n].astype(np.float32)
    sx = (x - n / 2 + 0.5) / RADIUS
    sy = (y - n / 2 + 0.5) / RADIUS
    r2 = sx * sx + sy * sy
    mask = r2 <= 1.0
    z = np.sqrt(np.maximum(0.0, 1.0 - r2))

    X = sx
    Y = -sy
    Z = z

    tilt = math.radians(-15.0)
    ct, st = math.cos(tilt), math.sin(tilt)
    Y2 = Y * ct - Z * st
    Z2 = Y * st + Z * ct
    X2 = X

    lon = np.arctan2(X2, Z2) + math.radians(longitude_deg)
    lat = np.arcsin(np.clip(Y2, -1.0, 1.0))
    u = ((lon / (2 * math.pi) + 0.5) % 1.0) * (tw - 1)
    v = (0.5 - lat / math.pi) * (th - 1)
    ui = np.clip(u.astype(np.int32), 0, tw - 1)
    vi = np.clip(v.astype(np.int32), 0, th - 1)
    rgb = tex[vi, ui]

    # Slightly stronger directional lighting for a more 3D feel.
    light = np.array([0.62, 0.44, 0.65], dtype=np.float32)
    light /= np.linalg.norm(light)
    dot = np.clip(X * light[0] + Y * light[1] + Z * light[2], 0, 1)
    shade = 0.18 + 0.92 * np.power(dot, 0.55)
    rgb *= shade[..., None]

    rim = np.power(np.clip(1.0 - z, 0, 1), 2.1) * mask
    rgb[..., 0] += rim * 18
    rgb[..., 1] += rim * 85
    rgb[..., 2] += rim * 155
    rgb = np.clip(rgb, 0, 255).astype(np.uint8)

    alpha = (mask * 255).astype(np.uint8)
    globe = Image.fromarray(np.dstack([rgb, alpha]), 'RGBA')

    frame = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    halo = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    hd = ImageDraw.Draw(halo)
    cx, cy = CENTER
    rr = RADIUS + 10
    hd.ellipse((cx - rr, cy - rr, cx + rr, cy + rr), outline=(55, 175, 255, 215), width=10)
    halo = halo.filter(ImageFilter.GaussianBlur(16))
    frame = Image.alpha_composite(frame, halo)
    frame.alpha_composite(globe, (cx - n // 2, cy - n // 2))

    # Enhance punch and clarity slightly.
    rgb_frame = frame.convert('RGB')
    rgb_frame = ImageEnhance.Color(rgb_frame).enhance(1.06)
    rgb_frame = ImageEnhance.Contrast(rgb_frame).enhance(1.06)
    enhanced = rgb_frame.convert('RGBA')
    enhanced.putalpha(frame.getchannel('A'))
    return enhanced


def main():
    make_space_background()
    texture = download_texture()
    # Five evenly spaced rotation keyframes for continuous automated rotation.
    for i, lon in enumerate([20, 92, 164, 236, 308]):
        globe_frame(texture, lon).save(OUT / f'earth_{i}.png', optimize=True)
    print('Generated:', ', '.join(p.name for p in sorted(OUT.glob('*.png'))))


if __name__ == '__main__':
    main()
