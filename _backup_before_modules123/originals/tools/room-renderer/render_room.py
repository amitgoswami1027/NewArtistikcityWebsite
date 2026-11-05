"""Render one room: python3 render_room.py <name> <width> <height> <spp> [outdir]
Writes <name>.jpg (the photo), <name>-light.jpg (wall light map for the artwork) and <name>.json (camera / scale)."""
import json, sys, time
import numpy as np
import cv2
from pt import render
from scenes import SCENES

name, W, H, spp = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4])
out = sys.argv[5] if len(sys.argv) > 5 else '.'
CAM = dict(pos=(0, 50, 230), look=(0, 50, 0), fov_y=38)

scene, meta = SCENES[name]()
t0 = time.time()
img, alb, nrm, dep, sun = render(scene, CAM, W, H, spp=spp, bounces=3, seed=7, log=lambda m: print(m, f'{time.time() - t0:.0f}s', flush=True))
np.save(f'{out}/{name}-raw.npy', np.concatenate([img, alb, nrm, dep[:, :, None], sun], 2).astype(np.float32))
print('render', f'{time.time() - t0:.0f}s')


def aces(x):
    a, b, c, d, e = 2.51, 0.03, 2.43, 0.59, 0.14
    return np.clip((x * (a * x + b)) / (x * (c * x + d) + e), 0, 1)


def atrous(shade, alb, nrm, dep, iterations=5, s_n=64.0, s_a=0.06, s_z=0.02, s_l=4.0):
    """Edge-aware a-trous wavelet filter (SVGF style) on the lighting, stopping at normal, albedo and depth edges."""
    k = np.array([1 / 16, 1 / 4, 3 / 8, 1 / 4, 1 / 16])
    out = shade.copy()
    nrm = nrm / np.maximum(np.linalg.norm(nrm, axis=2, keepdims=True), 1e-6)
    for it in range(iterations):
        step = 2 ** it
        acc = np.zeros_like(out)
        wsum = np.zeros(dep.shape)
        lum = out.mean(2)
        for i, ky in enumerate(k):
            for j, kx in enumerate(k):
                dy, dx = (i - 2) * step, (j - 2) * step
                sh_ = np.roll(np.roll(out, dy, 0), dx, 1)
                n_ = np.roll(np.roll(nrm, dy, 0), dx, 1)
                a_ = np.roll(np.roll(alb, dy, 0), dx, 1)
                z_ = np.roll(np.roll(dep, dy, 0), dx, 1)
                l_ = np.roll(np.roll(lum, dy, 0), dx, 1)
                wn = np.clip((nrm * n_).sum(2), 0, 1) ** s_n
                wa = np.exp(-np.abs(alb - a_).sum(2) / s_a)
                wz = np.exp(-np.abs(dep - z_) / (s_z * dep + 1e-3))
                wl = np.exp(-np.abs(lum - l_) / (s_l * (0.15 + lum))) if it >= 2 else 1.0
                w = ky * kx * wn * wa * wz * wl
                acc += sh_ * w[:, :, None]
                wsum += w
        good = wsum > 1e-6
        out = np.where(good[:, :, None], acc / np.maximum(wsum, 1e-12)[:, :, None], out)
    return out


def finish(img, alb, nrm, dep, sun, exposure):
    # denoise the soft lighting (everything except direct sun) strongly and edge-aware; the direct sun is already clean,
    # so it only gets a light pass that keeps the window's crisp shadow lines; then the textures go back on
    a_ = np.maximum(alb, 0.02)
    soft = np.maximum(img - sun, 0) / a_
    sh_soft = atrous(soft, alb, nrm, dep)
    sh_sun = atrous(sun / a_, alb, nrm, dep, iterations=2, s_l=0.6)
    sh = sh_soft + sh_sun
    lit = sh * alb
    # keep emitters / sky (albedo ~ 0 or > threshold radiance) from the raw image
    hot = (img.max(2) > 5.0)[:, :, None]
    lit = np.where(hot, img, lit)
    x = aces(lit * exposure)
    # glow only around light sources
    bright = np.clip(x - 0.97, 0, 1) * hot
    bloom = cv2.GaussianBlur(bright.astype(np.float32), (0, 0), W / 120)
    x = np.clip(x + bloom * 2.0, 0, 1)
    # gentle S-curve and saturation, like a camera profile
    lum = (x * np.array([0.2126, 0.7152, 0.0722])).sum(2, keepdims=True)
    x = np.clip(lum + (x - lum) * 1.12, 0, 1)
    x = x * x * (3 - 2 * x) * 0.35 + x * 0.65
    # vignette + mild warm grade + grain
    yy, xx = np.mgrid[0:H, 0:W]
    r = np.sqrt(((xx - W / 2) / (W / 2)) ** 2 + ((yy - H / 2) / (H / 2)) ** 2)
    x = x * (1 - 0.18 * np.clip(r - 0.35, 0, 1) ** 1.6)[:, :, None]
    x = x ** (1 / 2.2)
    x = x * np.array([1.01, 1.0, 0.98])
    rng = np.random.default_rng(3)
    x = np.clip(x + rng.normal(0, 0.008, x.shape), 0, 1)
    return x, sh


# auto exposure: the wall around the hanging spot should read as a bright, natural white
f0 = (H / 2) / np.tan(np.radians(CAM['fov_y']) / 2) / CAM['pos'][2]
hang_y = meta.get('center', meta.get('hang_bottom', 45) + 14)
yy0 = int(H / 2 - (hang_y + 18 - CAM['pos'][1]) * f0)
yy1 = int(H / 2 - (hang_y - 10 - CAM['pos'][1]) * f0)
xx0, xx1 = int(W / 2 - 40 * f0), int(W / 2 + 40 * f0)
wall_sh = img[max(0, yy0):yy1, xx0:xx1] / np.maximum(alb[max(0, yy0):yy1, xx0:xx1], 0.02)
wall_lum = float(np.median((wall_sh * alb[max(0, yy0):yy1, xx0:xx1]).mean(2)))
exposure = meta.get('exposure', 1.0) * 0.5 / max(wall_lum, 1e-3)
print('wall luminance', round(wall_lum, 3), 'exposure', round(exposure, 3))
x, sh = finish(img, alb, nrm, dep, sun, exposure)
cv2.imwrite(f'{out}/{name}.jpg', (x[:, :, ::-1] * 255).astype(np.uint8), [cv2.IMWRITE_JPEG_QUALITY, 86, cv2.IMWRITE_JPEG_PROGRESSIVE, 1])


def front_layer(x, dep):
    # everything that stands in front of the back wall (furniture, plants, lamps, the floor) as a cut-out with alpha,
    # drawn over the artwork in the browser so a piece dragged low slips behind the sofa instead of floating on it
    th = np.tan(np.radians(CAM['fov_y']) / 2)
    tw = th * W / H
    yy, xx = np.mgrid[0:H, 0:W]
    sx = ((xx + 0.5) / W * 2 - 1) * tw
    sy = (1 - (yy + 0.5) / H * 2) * th
    t_wall = CAM['pos'][2] * np.sqrt(1 + sx ** 2 + sy ** 2)
    a = np.clip((t_wall - dep - 1.0) / 2.0, 0, 1)
    a[dep <= 0] = 0
    a = cv2.GaussianBlur(a.astype(np.float32), (3, 3), 0.6)
    rgba = np.dstack([(x[:, :, ::-1] * 255).astype(np.uint8), (a * 255).astype(np.uint8)])
    rgba[a < 0.004, :3] = 0
    cv2.imwrite(f'{out}/{name}-front.png', rgba, [cv2.IMWRITE_PNG_COMPRESSION, 9])
    cv2.imwrite(f'{out}/{name}-front.webp', rgba, [cv2.IMWRITE_WEBP_QUALITY, 88])


front_layer(x, dep)

# light map: the wall's lighting (shade), tone mapped like the photo and normalised so the brightest wall area ~ white
lm = aces(sh * exposure * 0.8) ** (1 / 2.2)
cv2.imwrite(f'{out}/{name}-light.jpg', (np.clip(lm[:, :, ::-1], 0, 1) * 255).astype(np.uint8), [cv2.IMWRITE_JPEG_QUALITY, 80])

f = (H / 2) / np.tan(np.radians(CAM['fov_y']) / 2)
D = CAM['pos'][2]
ppi = f / D                            # pixels per inch on the back wall
cx, cy = W / 2, H / 2


def to_px(X, Y):
    return cx + X * ppi, cy - (Y - CAM['pos'][1]) * ppi


wall_l, wall_t = to_px(-110, 108)
wall_r, wall_b = to_px(110, 0)
info = dict(name=meta['name'], note=meta['note'], width=W, height=H, pxPerInch=round(ppi, 4),
            wall=dict(left=round(max(0, wall_l), 1), top=round(max(0, wall_t), 1), right=round(min(W, wall_r), 1), bottom=round(min(H, wall_b), 1)),
            floorY=round(wall_b, 1), centerX=cx, eyeY=round(to_px(0, 57)[1], 1))
if 'hang_bottom' in meta:
    info['hangBottomY'] = round(to_px(0, meta['hang_bottom'])[1], 1)
if 'center' in meta:
    info['hangCenterY'] = round(to_px(0, meta['center'])[1], 1)
# main light direction on the wall (for the artwork's cast shadow): sun if any, else the brightest lamp
if scene.sun is not None:
    d = scene.sun[0]
    info['light'] = dict(dx=round(float(-d[0]), 3), dy=round(float(d[1]), 3), soft=0.35)
else:
    info['light'] = dict(dx=0.0, dy=1.0, soft=0.6)
json.dump(info, open(f'{out}/{name}.json', 'w'), indent=1)
print(json.dumps(info))
