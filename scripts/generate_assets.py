from pathlib import Path
from urllib.request import Request, urlopen
from io import BytesIO
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageEnhance

OUT = Path('app/src/main/res/drawable-nodpi')
OUT.mkdir(parents=True, exist_ok=True)

BG_W, BG_H = 1080, 2340
FRAME = 768
RADIUS = 382
FRAME_COUNT = 72
BASE_LONGITUDE = -132.0
TILT_DEG = -16.0

SURFACE_URLS = [
    'https://eoimages.gsfc.nasa.gov/images/imagerecords/57000/57730/land_ocean_ice_2048.jpg',
    'https://eoimages.gsfc.nasa.gov/images/imagerecords/57000/57730/land_ocean_ice_2048.png',
]
CLOUD_URLS = [
    'https://eoimages.gsfc.nasa.gov/images/imagerecords/57000/57747/cloud_combined_2048.jpg',
]


def download_first(urls, mode='RGB'):
    last = None
    for url in urls:
        try:
            req = Request(url, headers={'User-Agent': 'NervaEarthLive/3.0'})
            with urlopen(req, timeout=90) as r:
                data = r.read()
            image = Image.open(BytesIO(data)).convert(mode)
            print('Downloaded:', url, image.size)
            return image
        except Exception as e:
            last = e
            print('Download failed:', url, e)
    raise RuntimeError(f'All source downloads failed: {last}')


def fallback_surface():
    w, h = 2048, 1024
    yy, xx = np.mgrid[0:h, 0:w]
    ocean = np.zeros((h, w, 3), dtype=np.float32)
    ocean[..., 0] = 10 + 8 * np.sin(xx / 180.0)
    ocean[..., 1] = 58 + 18 * np.sin(xx / 230.0)
    ocean[..., 2] = 100 + 24 * np.cos(yy / 130.0)
    img = Image.fromarray(np.clip(ocean, 0, 255).astype(np.uint8), 'RGB')
    d = ImageDraw.Draw(img)
    d.ellipse((180, 140, 780, 610), fill=(69, 105, 63))
    d.ellipse((630, 430, 1030, 940), fill=(66, 104, 63))
    d.ellipse((1070, 120, 1830, 760), fill=(100, 109, 65))
    return img.filter(ImageFilter.GaussianBlur(7))


def fallback_clouds(w=2048, h=1024):
    rng = np.random.default_rng(260924)
    small = rng.random((128, 256), dtype=np.float32)
    im = Image.fromarray(np.uint8(small * 255), 'L').resize((w, h), Image.Resampling.BICUBIC)
    im = im.filter(ImageFilter.GaussianBlur(7))
    arr = np.asarray(im, dtype=np.float32) / 255.0
    alpha = np.clip((arr - 0.58) * 3.1, 0, 1)
    return Image.fromarray(np.uint8(alpha * 255), 'L')


def prepare_cloud_luminance(cloud_image):
    gray = cloud_image.convert('L').resize((2048, 1024), Image.Resampling.LANCZOS)
    a = np.asarray(gray, dtype=np.float32) / 255.0
    a = np.clip((a - 0.22) / 0.72, 0, 1)
    a = np.power(a, 1.25)
    a = np.uint8(a * 220)
    return Image.fromarray(a, 'L').filter(ImageFilter.GaussianBlur(0.8))


def make_space_background():
    rng = np.random.default_rng(260924)
    yy, xx = np.mgrid[0:BG_H, 0:BG_W]

    img = np.zeros((BG_H, BG_W, 3), dtype=np.float32)
    img[..., 0] = 1.1
    img[..., 1] = 4.0
    img[..., 2] = 7.0

    band = np.exp(-((yy - (0.56 * xx + BG_H * 0.18)) / 235.0) ** 2)
    band *= 0.55 + 0.45 * np.sin((xx + yy) / 310.0) ** 2
    img[..., 0] += band * 2.2
    img[..., 1] += band * 4.0
    img[..., 2] += band * 5.7

    for cx, cy, sx, sy, strength in [
        (BG_W * .16, BG_H * .73, 240, 330, 4.0),
        (BG_W * .84, BG_H * .22, 260, 360, 3.5),
        (BG_W * .70, BG_H * .86, 300, 390, 3.0),
    ]:
        g = np.exp(-(((xx - cx) / sx) ** 2 + ((yy - cy) / sy) ** 2) * 1.2)
        img[..., 0] += g * strength * .20
        img[..., 1] += g * strength * .45
        img[..., 2] += g * strength * .68

    img += rng.normal(0, 0.48, (BG_H, BG_W, 1)).astype(np.float32)
    base = Image.fromarray(np.uint8(np.clip(img, 0, 255)), 'RGB')
    draw = ImageDraw.Draw(base)

    for _ in range(520):
        x = int(rng.integers(0, BG_W))
        y = int(rng.integers(0, BG_H))
        b = int(rng.integers(90, 205))
        color = (int(b * .78), int(b * .88), b)
        u = rng.random()
        if u < .90:
            draw.point((x, y), fill=color)
        elif u < .985:
            draw.ellipse((x - 1, y - 1, x + 1, y + 1), fill=color)
        else:
            draw.ellipse((x - 2, y - 2, x + 2, y + 2), fill=color)

    glow = Image.new('RGBA', (BG_W, BG_H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    for _ in range(10):
        x = int(rng.integers(30, BG_W - 30))
        y = int(rng.integers(30, BG_H - 30))
        gd.ellipse((x - 5, y - 5, x + 5, y + 5), fill=(110, 175, 225, 42))
        draw.ellipse((x - 1, y - 1, x + 1, y + 1), fill=(205, 225, 245))
    glow = glow.filter(ImageFilter.GaussianBlur(5))
    base = Image.alpha_composite(base.convert('RGBA'), glow).convert('RGB')
    ImageEnhance.Contrast(base).enhance(1.05).save(OUT / 'space_bg.png', optimize=True)


def bilinear_sample(tex, u, v, wrap_x=True):
    h, w = tex.shape[:2]
    x0 = np.floor(u).astype(np.int32)
    y0 = np.floor(v).astype(np.int32)
    if wrap_x:
        x0 %= w
        x1 = (x0 + 1) % w
    else:
        x0 = np.clip(x0, 0, w - 1)
        x1 = np.clip(x0 + 1, 0, w - 1)
    y0 = np.clip(y0, 0, h - 1)
    y1 = np.clip(y0 + 1, 0, h - 1)

    fx = (u - np.floor(u))[..., None]
    fy = (v - np.floor(v))[..., None]
    a = tex[y0, x0]
    b = tex[y0, x1]
    c = tex[y1, x0]
    d = tex[y1, x1]
    top = a * (1.0 - fx) + b * fx
    bottom = c * (1.0 - fx) + d * fx
    return top * (1.0 - fy) + bottom * fy


def sphere_coordinates(longitude_deg):
    n = FRAME
    y, x = np.mgrid[0:n, 0:n].astype(np.float32)
    sx = (x - n / 2 + 0.5) / RADIUS
    sy = (y - n / 2 + 0.5) / RADIUS
    r2 = sx * sx + sy * sy
    mask = r2 <= 1.0
    z = np.sqrt(np.maximum(0.0, 1.0 - r2))

    X, Y, Z = sx, -sy, z
    tilt = math.radians(TILT_DEG)
    ct, st = math.cos(tilt), math.sin(tilt)
    Y2 = Y * ct - Z * st
    Z2 = Y * st + Z * ct
    X2 = X

    lon = np.arctan2(X2, Z2) + math.radians(longitude_deg)
    lat = np.arcsin(np.clip(Y2, -1.0, 1.0))
    return X, Y, Z, lon, lat, mask


def project_earth(surface_tex, longitude_deg):
    tex = np.asarray(surface_tex, dtype=np.float32)
    th, tw = tex.shape[:2]
    X, Y, Z, lon, lat, mask = sphere_coordinates(longitude_deg)

    u = ((lon / (2 * math.pi) + 0.5) % 1.0) * tw
    v = (0.5 - lat / math.pi) * (th - 1)
    rgb = bilinear_sample(tex, u, v)

    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    ocean = (b > 48) & (b > r * 1.16) & (b > g * 1.05)
    rgb[..., 0] = np.where(ocean, r * .82, r)
    rgb[..., 1] = np.where(ocean, g * 1.09, g)
    rgb[..., 2] = np.where(ocean, b * 1.08, b)

    light = np.array([0.53, 0.29, 0.80], dtype=np.float32)
    light /= np.linalg.norm(light)
    ndotl = np.clip(X * light[0] + Y * light[1] + Z * light[2], 0, 1)
    shade = 0.115 + 0.94 * np.power(ndotl, 0.60)
    shade *= 0.88 + 0.12 * np.power(np.clip(Z, 0, 1), 0.45)
    rgb *= shade[..., None]

    spec = np.power(ndotl, 18.0) * ocean * 13.0
    rgb[..., 0] += spec * .40
    rgb[..., 1] += spec * .75
    rgb[..., 2] += spec

    rim = np.power(np.clip(1.0 - Z, 0, 1), 3.2) * mask
    rgb[..., 1] += rim * 10
    rgb[..., 2] += rim * 20

    alpha = np.uint8(mask * 255)
    rgba = np.dstack([np.uint8(np.clip(rgb, 0, 255)), alpha])
    image = Image.fromarray(rgba, 'RGBA')
    image = ImageEnhance.Color(image).enhance(1.05)
    return ImageEnhance.Contrast(image).enhance(1.025)


def project_clouds(cloud_luma, longitude_deg):
    tex = np.asarray(cloud_luma, dtype=np.float32)[..., None]
    th, tw = tex.shape[:2]
    X, Y, Z, lon, lat, mask = sphere_coordinates(longitude_deg)

    u = ((lon / (2 * math.pi) + 0.5) % 1.0) * tw
    v = (0.5 - lat / math.pi) * (th - 1)
    lum = bilinear_sample(tex, u, v)[..., 0] / 255.0

    light = np.array([0.53, 0.29, 0.80], dtype=np.float32)
    light /= np.linalg.norm(light)
    ndotl = np.clip(X * light[0] + Y * light[1] + Z * light[2], 0, 1)

    alpha = lum * (0.45 + 0.55 * ndotl)
    alpha *= np.power(np.clip(Z, 0, 1), 0.18)
    alpha *= mask
    alpha = np.uint8(np.clip(alpha, 0, 1) * 205)

    cloud_rgb = np.zeros((FRAME, FRAME, 3), dtype=np.uint8)
    cloud_rgb[..., 0] = 220
    cloud_rgb[..., 1] = 234
    cloud_rgb[..., 2] = 244
    return Image.fromarray(np.dstack([cloud_rgb, alpha]), 'RGBA')


def main():
    make_space_background()

    try:
        surface = download_first(SURFACE_URLS, 'RGB')
        surface = surface.resize((2048, 1024), Image.Resampling.LANCZOS)
    except Exception as e:
        print('Surface fallback:', e)
        surface = fallback_surface()

    try:
        clouds_src = download_first(CLOUD_URLS, 'RGB')
        cloud_luma = prepare_cloud_luminance(clouds_src)
    except Exception as e:
        print('Cloud fallback:', e)
        cloud_luma = fallback_clouds()

    for i in range(FRAME_COUNT):
        lon = BASE_LONGITUDE + i * (360.0 / FRAME_COUNT)
        earth = project_earth(surface, lon)
        clouds = project_clouds(cloud_luma, lon)
        earth.save(OUT / f'earth_{i:02d}.png', optimize=True)
        clouds.save(OUT / f'cloud_{i:02d}.png', optimize=True)

    print(f'Generated background + {FRAME_COUNT} Earth + {FRAME_COUNT} cloud frames')


if __name__ == '__main__':
    main()
