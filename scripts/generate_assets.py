from pathlib import Path
from urllib.request import urlopen, Request
from io import BytesIO
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

OUT = Path('app/src/main/res/drawable-nodpi')
OUT.mkdir(parents=True, exist_ok=True)
W, H = 720, 1560
EARTH_SIZE = 720
RADIUS = 322
CENTER = (W // 2, 790)
NASA_URL = 'https://svs.gsfc.nasa.gov/vis/a000000/a002900/a002915/bluemarble-2048.png'


def download_texture():
    req = Request(NASA_URL, headers={'User-Agent': 'Earth3DLiveWallpaper/1.0'})
    with urlopen(req, timeout=60) as r:
        return Image.open(BytesIO(r.read())).convert('RGB')


def make_space_background():
    rng = np.random.default_rng(260923)
    yy, xx = np.mgrid[0:H, 0:W]
    img = np.zeros((H, W, 3), dtype=np.float32)
    img[..., 0] = 1.5
    img[..., 1] = 5.0
    img[..., 2] = 11.0

    for cx, cy, sx, sy, strength in [
        (690, 420, 180, 420, 27), (650, 920, 220, 500, 32),
        (720, 1370, 210, 380, 38), (90, 1240, 210, 350, 9)
    ]:
        g = np.exp(-(((xx-cx)/sx)**2 + ((yy-cy)/sy)**2) * 1.4)
        img[..., 0] += g * strength * 0.10
        img[..., 1] += g * strength * 0.42
        img[..., 2] += g * strength

    noise = rng.normal(0, 1.4, (H, W, 1)).astype(np.float32)
    img += noise
    img = np.clip(img, 0, 255).astype(np.uint8)
    base = Image.fromarray(img, 'RGB')
    draw = ImageDraw.Draw(base)

    for _ in range(2600):
        x = int(rng.integers(0, W)); y = int(rng.integers(0, H))
        b = int(rng.integers(105, 255))
        r = 1 if rng.random() < 0.93 else 2
        c = (int(b*0.72), int(b*0.87), b)
        draw.ellipse((x-r, y-r, x+r, y+r), fill=c)

    glow = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    for _ in range(65):
        x = int(rng.integers(15, W-15)); y = int(rng.integers(15, H-15))
        gd.ellipse((x-5, y-5, x+5, y+5), fill=(80, 180, 255, 100))
        draw.ellipse((x-1, y-1, x+1, y+1), fill=(220, 240, 255))
    glow = glow.filter(ImageFilter.GaussianBlur(5))
    base = Image.alpha_composite(base.convert('RGBA'), glow).convert('RGB')
    base.save(OUT / 'space_bg.png', optimize=True)


def globe_frame(texture: Image.Image, longitude_deg: float):
    tex = np.asarray(texture, dtype=np.float32)
    th, tw = tex.shape[:2]

    n = EARTH_SIZE
    y, x = np.mgrid[0:n, 0:n].astype(np.float32)
    sx = (x - n/2 + 0.5) / RADIUS
    sy = (y - n/2 + 0.5) / RADIUS
    r2 = sx*sx + sy*sy
    mask = r2 <= 1.0
    z = np.sqrt(np.maximum(0.0, 1.0-r2))

    X = sx
    Y = -sy
    Z = z

    tilt = math.radians(-15.0)
    ct, st = math.cos(tilt), math.sin(tilt)
    Y2 = Y*ct - Z*st
    Z2 = Y*st + Z*ct
    X2 = X

    lon = np.arctan2(X2, Z2) + math.radians(longitude_deg)
    lat = np.arcsin(np.clip(Y2, -1.0, 1.0))
    u = ((lon / (2*math.pi) + 0.5) % 1.0) * (tw-1)
    v = (0.5 - lat / math.pi) * (th-1)
    ui = np.clip(u.astype(np.int32), 0, tw-1)
    vi = np.clip(v.astype(np.int32), 0, th-1)
    rgb = tex[vi, ui]

    light = np.array([0.55, 0.50, 0.67], dtype=np.float32)
    light /= np.linalg.norm(light)
    dot = np.clip(X*light[0] + Y*light[1] + Z*light[2], 0, 1)
    shade = 0.20 + 0.87 * np.power(dot, 0.62)
    rgb *= shade[..., None]

    rim = np.power(np.clip(1.0-z, 0, 1), 2.3) * mask
    rgb[..., 0] += rim * 15
    rgb[..., 1] += rim * 75
    rgb[..., 2] += rim * 145
    rgb = np.clip(rgb, 0, 255).astype(np.uint8)

    alpha = (mask * 255).astype(np.uint8)
    globe = Image.fromarray(np.dstack([rgb, alpha]), 'RGBA')

    frame = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    halo = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    hd = ImageDraw.Draw(halo)
    cx, cy = CENTER
    rr = RADIUS + 8
    hd.ellipse((cx-rr, cy-rr, cx+rr, cy+rr), outline=(50, 170, 255, 205), width=10)
    halo = halo.filter(ImageFilter.GaussianBlur(14))
    frame = Image.alpha_composite(frame, halo)
    frame.alpha_composite(globe, (cx - n//2, cy - n//2))
    return frame


def main():
    make_space_background()
    texture = download_texture()
    for i, lon in enumerate([20, 92, 164, 236, 308]):
        globe_frame(texture, lon).save(OUT / f'earth_{i}.png', optimize=True)
    print('Generated:', ', '.join(p.name for p in sorted(OUT.glob('*.png'))))


if __name__ == '__main__':
    main()
