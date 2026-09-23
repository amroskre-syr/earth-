from pathlib import Path
from urllib.request import urlopen, Request
from io import BytesIO
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageEnhance

OUT = Path('app/src/main/res/drawable-nodpi')
OUT.mkdir(parents=True, exist_ok=True)
BG_W, BG_H = 720, 1560
FRAME = 768
RADIUS = 354
FRAME_COUNT = 36
NASA_URL = 'https://svs.gsfc.nasa.gov/vis/a000000/a002900/a002915/bluemarble-2048.png'


def download_texture():
    req = Request(NASA_URL, headers={'User-Agent': 'NervaEarthLive/2.0'})
    with urlopen(req, timeout=90) as r:
        return Image.open(BytesIO(r.read())).convert('RGB')


def fallback_texture():
    w, h = 2048, 1024
    yy, xx = np.mgrid[0:h, 0:w]
    ocean = np.zeros((h, w, 3), dtype=np.float32)
    ocean[..., 0] = 18 + 8*np.sin(xx/170)
    ocean[..., 1] = 62 + 18*np.sin(xx/220)
    ocean[..., 2] = 105 + 24*np.cos(yy/120)
    img = Image.fromarray(np.clip(ocean, 0, 255).astype(np.uint8), 'RGB')
    d = ImageDraw.Draw(img)
    d.ellipse((300,190,850,650), fill=(70,112,64))
    d.ellipse((730,520,1110,930), fill=(71,110,62))
    d.ellipse((1150,170,1780,720), fill=(98,112,66))
    return img.filter(ImageFilter.GaussianBlur(8))


def make_cloud_texture(w=2048, h=1024):
    rng = np.random.default_rng(230926)
    layers = []
    for lw, lh, blur, weight in [
        (64, 32, 2.0, 0.33),
        (128, 64, 1.5, 0.29),
        (256, 128, 1.1, 0.23),
        (512, 256, 0.7, 0.15),
    ]:
        a = rng.random((lh, lw), dtype=np.float32)
        im = Image.fromarray(np.uint8(a * 255), 'L').resize((w, h), Image.Resampling.BICUBIC)
        if blur:
            im = im.filter(ImageFilter.GaussianBlur(blur))
        layers.append(np.asarray(im, dtype=np.float32) / 255.0 * weight)
    n = np.sum(layers, axis=0)
    yy, xx = np.mgrid[0:h, 0:w]
    bands = 0.10*np.sin((xx/170.0) + np.sin(yy/85.0)) + 0.06*np.sin(yy/43.0 + xx/290.0)
    n = n + bands
    n = (n - n.min()) / max(1e-6, n.max() - n.min())
    a = np.clip((n - 0.48) * 4.2, 0, 1)
    a = np.uint8(a * 215)
    alpha = Image.fromarray(a, 'L').filter(ImageFilter.GaussianBlur(1.4))
    cloud = Image.new('RGBA', (w, h), (238, 247, 255, 0))
    cloud.putalpha(alpha)
    return cloud


def make_space_background():
    rng = np.random.default_rng(260923)
    yy, xx = np.mgrid[0:BG_H, 0:BG_W]
    img = np.zeros((BG_H, BG_W, 3), dtype=np.float32)
    img[..., 0] = 1.4
    img[..., 1] = 5.0
    img[..., 2] = 9.0
    for cx, cy, sx, sy, strength in [
        (BG_W*0.80, BG_H*0.18, 170, 270, 18),
        (BG_W*0.18, BG_H*0.72, 180, 250, 12),
        (BG_W*0.72, BG_H*0.83, 190, 290, 15),
    ]:
        g = np.exp(-(((xx-cx)/sx)**2 + ((yy-cy)/sy)**2)*1.3)
        img[..., 0] += g*strength*0.08
        img[..., 1] += g*strength*0.28
        img[..., 2] += g*strength*0.70
    noise = rng.normal(0, 0.75, (BG_H, BG_W, 1)).astype(np.float32)
    img += noise
    base = Image.fromarray(np.uint8(np.clip(img, 0, 255)), 'RGB')
    draw = ImageDraw.Draw(base)
    for _ in range(1200):
        x = int(rng.integers(0, BG_W)); y = int(rng.integers(0, BG_H))
        u = rng.random(); r = 1 if u < .965 else 2
        b = int(rng.integers(105, 230)); color = (int(b*.78), int(b*.88), b)
        draw.ellipse((x-r, y-r, x+r, y+r), fill=color)
    glow = Image.new('RGBA', (BG_W, BG_H), (0,0,0,0)); gd = ImageDraw.Draw(glow)
    for _ in range(18):
        x = int(rng.integers(20, BG_W-20)); y = int(rng.integers(20, BG_H-20))
        gd.ellipse((x-5,y-5,x+5,y+5), fill=(90,160,235,70))
        draw.ellipse((x-1,y-1,x+1,y+1), fill=(215,235,255))
    glow = glow.filter(ImageFilter.GaussianBlur(5))
    base = Image.alpha_composite(base.convert('RGBA'), glow).convert('RGB')
    ImageEnhance.Contrast(base).enhance(1.08).save(OUT/'space_bg.png', optimize=True)


def project_sphere(texture, longitude_deg, cloud=False):
    tex = np.asarray(texture, dtype=np.float32)
    th, tw = tex.shape[:2]
    n = FRAME
    y, x = np.mgrid[0:n, 0:n].astype(np.float32)
    sx = (x - n/2 + 0.5) / RADIUS
    sy = (y - n/2 + 0.5) / RADIUS
    r2 = sx*sx + sy*sy
    mask = r2 <= 1.0
    z = np.sqrt(np.maximum(0.0, 1.0-r2))
    X, Y, Z = sx, -sy, z
    tilt = math.radians(-17.0)
    ct, st = math.cos(tilt), math.sin(tilt)
    Y2 = Y*ct - Z*st; Z2 = Y*st + Z*ct; X2 = X
    lon = np.arctan2(X2, Z2) + math.radians(longitude_deg)
    lat = np.arcsin(np.clip(Y2, -1.0, 1.0))
    u = ((lon/(2*math.pi) + 0.5) % 1.0) * (tw-1)
    v = (0.5 - lat/math.pi) * (th-1)
    ui = np.clip(u.astype(np.int32), 0, tw-1); vi = np.clip(v.astype(np.int32), 0, th-1)
    if cloud:
        rgba = tex[vi, ui]; rgb = rgba[..., :3]; a = rgba[..., 3] / 255.0
        a *= np.power(np.clip(z, 0, 1), 0.22)
        alpha = np.uint8(np.clip(a*mask, 0, 1)*255)
        return Image.fromarray(np.dstack([np.uint8(np.clip(rgb,0,255)), alpha]), 'RGBA')
    rgb = tex[vi, ui]
    light = np.array([0.42, 0.44, 0.79], dtype=np.float32); light /= np.linalg.norm(light)
    dot = np.clip(X*light[0] + Y*light[1] + Z*light[2], 0, 1)
    shade = 0.22 + 0.90*np.power(dot, 0.58)
    shade *= 0.78 + 0.22*np.power(np.clip(z,0,1), 0.35)
    rgb = rgb * shade[..., None]
    rim = np.power(np.clip(1.0-z,0,1), 2.4) * mask
    rgb[..., 0] += rim*10; rgb[..., 1] += rim*45; rgb[..., 2] += rim*90
    out = Image.fromarray(np.dstack([np.uint8(np.clip(rgb,0,255)), np.uint8(mask*255)]), 'RGBA')
    out = ImageEnhance.Color(out).enhance(1.04)
    return ImageEnhance.Contrast(out).enhance(1.05)


def main():
    make_space_background()
    try:
        earth_tex = download_texture(); print('NASA texture downloaded')
    except Exception as e:
        print('NASA texture unavailable, using fallback:', e); earth_tex = fallback_texture()
    cloud_tex = make_cloud_texture()
    for i in range(FRAME_COUNT):
        lon = i * (360.0/FRAME_COUNT) + 18.0
        project_sphere(earth_tex, lon, False).save(OUT/f'earth_{i:02d}.png', optimize=True)
        project_sphere(cloud_tex, lon*1.035 + 12.0, True).save(OUT/f'cloud_{i:02d}.png', optimize=True)
    print(f'Generated background + {FRAME_COUNT} earth + {FRAME_COUNT} cloud frames')

if __name__ == '__main__':
    main()
