"""Turn the rendered rooms (final/) into web assets for the room preview:
  static/assets/rooms/<room>.jpg          the photo (1200 x 800, progressive JPEG)
  static/assets/rooms/<room>-sm.jpg       thumbnail for the room switcher (300 x 200)
  static/assets/rooms/<room>-front.webp   furniture / floor cut-out, drawn over the artwork
  static/assets/rooms/<room>-shade.jpg    wall lighting <= 1, multiplied onto the artwork (darker corners, lamp falloff)
  static/assets/rooms/<room>-glow.jpg     wall lighting above the hang-spot level (sun patches), colour-dodged onto the artwork (it brightens it the way more light would)
and frontend/resources/js/Components/Shop/rooms.js with the calibration (pixels per inch, wall, hang line, light)."""
import json, os, shutil, sys
import numpy as np
import cv2

SRC = os.environ.get('ROOMS_SRC') or os.path.join(os.path.dirname(os.path.abspath(__file__)), 'final')
APP = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
OUT = os.environ.get('ROOMS_OUT') or f'{APP}/src/main/resources/static/assets/rooms'
JS = os.environ.get('ROOMS_JS') or f'{APP}/frontend/resources/js/Components/Shop/rooms.js'
ORDER = ['living', 'studio', 'bedroom', 'gallery']
LABELS = {
    'living': ('Minimalist living room', 'fa-home'),
    'studio': ('Industrial loft', 'fa-industry'),
    'bedroom': ('Bohemian bedroom', 'fa-bed'),
    'gallery': ('Gallery wall', 'fa-university'),
}
os.makedirs(OUT, exist_ok=True)
rooms = []
for name in ORDER:
    meta_path = f'{SRC}/{name}.json'
    if not os.path.exists(meta_path) or not os.path.exists(f'{SRC}/{name}-front.png'):
        print('missing', name, file=sys.stderr)
        continue
    meta = json.load(open(meta_path))
    W, H = meta['width'], meta['height']
    raw = np.load(f'{SRC}/{name}-raw.npy')
    img, alb, dep = raw[:, :, 0:3], raw[:, :, 3:6], raw[:, :, 9]
    front = cv2.imread(f'{SRC}/{name}-front.png', cv2.IMREAD_UNCHANGED)
    wall = front[:, :, 3] < 8

    # lighting falling on the wall, split in two: the soft light (lamps, sky, bounces: noisy at 16 spp but smooth, so it is
    # blurred over wall pixels only) and the direct sun (crisp edges, kept sharp)
    sun = raw[:, :, 10:13]
    lum_w = np.array([0.2126, 0.7152, 0.0722])
    a_l = np.maximum((alb * lum_w).sum(2), 0.03)
    soft = np.maximum((img - sun) * lum_w, 0).sum(2) / a_l
    sun_l = (sun * lum_w).sum(2) / a_l
    soft = np.where(wall, soft, np.nan)
    small = cv2.resize(np.nan_to_num(soft, nan=0).astype(np.float32), (W // 4, H // 4), interpolation=cv2.INTER_AREA)
    wsmall = cv2.resize(wall.astype(np.float32), (W // 4, H // 4), interpolation=cv2.INTER_AREA)
    num = cv2.GaussianBlur(small * wsmall, (0, 0), 3)
    den = cv2.GaussianBlur(wsmall, (0, 0), 3)
    sm = np.where(den > 0.05, num / np.maximum(den, 1e-6), 0).astype(np.float32)
    hole = (den <= 0.05).astype(np.uint8)
    if hole.any():
        m = float(sm[den > 0.05].max()) or 1.0
        sm8 = np.clip(sm / m * 255, 0, 255).astype(np.uint8)
        sm = cv2.inpaint(sm8, hole, 6, cv2.INPAINT_TELEA).astype(np.float32) / 255 * m
    light = cv2.resize(sm, (W, H), interpolation=cv2.INTER_CUBIC)
    # 1.0 = the wall's soft light around the usual hanging spot (the artwork shows as photographed there)
    hy = meta.get('hangCenterY') or (meta['hangBottomY'] - 14 * meta['pxPerInch'])
    cx, ppi = meta['centerX'], meta['pxPerInch']
    y0, y1 = int(hy - 16 * ppi), int(hy + 16 * ppi)
    x0, x1 = int(cx - 30 * ppi), int(cx + 30 * ppi)
    ref = float(np.median(light[y0:y1, x0:x1]))
    shade_map = np.clip(light / max(ref, 1e-6) * 0.98, 0.25, 1.0)     # multiply: lamp falloff, darker corners
    sun_rel = cv2.GaussianBlur((np.where(wall, sun_l, 0) / max(ref, 1e-6)).astype(np.float32), (0, 0), 1.4)
    # sun on the artwork multiplies its light: drawn with 'color-dodge' (c / (1 - g)), so g = 1 - 1 / gain
    gain = 1 + 0.16 * sun_rel
    glow_map = np.clip(1 - 1 / gain, 0, 0.3)
    cv2.imwrite(f'{OUT}/{name}-shade.jpg', (shade_map * 255).astype(np.uint8), [cv2.IMWRITE_JPEG_QUALITY, 80])
    g = (glow_map * 255).astype(np.uint8)
    warm = np.dstack([g * 0.82, g * 0.94, g]).astype(np.uint8)   # BGR: sunlight is warm
    cv2.imwrite(f'{OUT}/{name}-glow.jpg', warm, [cv2.IMWRITE_JPEG_QUALITY, 80])

    photo = cv2.imread(f'{SRC}/{name}.jpg')
    cv2.imwrite(f'{OUT}/{name}.jpg', photo, [cv2.IMWRITE_JPEG_QUALITY, 84, cv2.IMWRITE_JPEG_PROGRESSIVE, 1, cv2.IMWRITE_JPEG_OPTIMIZE, 1])
    cv2.imwrite(f'{OUT}/{name}-sm.jpg', cv2.resize(photo, (300, 200), interpolation=cv2.INTER_AREA), [cv2.IMWRITE_JPEG_QUALITY, 78])
    shutil.copy(f'{SRC}/{name}-front.webp', f'{OUT}/{name}-front.webp')

    label, icon = LABELS[name]
    room = dict(id=name, label=label, icon=icon, note=meta['note'], width=W, height=H, pxPerInch=meta['pxPerInch'],
                wall=meta['wall'], floorY=meta['floorY'], centerX=meta['centerX'], eyeY=meta['eyeY'], light=meta['light'],
                src=f'/assets/rooms/{name}.jpg', thumb=f'/assets/rooms/{name}-sm.jpg', front=f'/assets/rooms/{name}-front.webp',
                shade=f'/assets/rooms/{name}-shade.jpg', glow=f'/assets/rooms/{name}-glow.jpg')
    if 'hangBottomY' in meta:
        room['hangBottomY'] = meta['hangBottomY']
    if 'hangCenterY' in meta:
        room['hangCenterY'] = meta['hangCenterY']
    # lowest point the artwork may go: the top of whatever stands below the centre of the wall (it may slip behind it)
    col = front[:, int(cx), 3]
    start = int(meta.get('hangBottomY') or meta.get('hangCenterY'))
    above = np.where(col[start: int(meta['floorY'])] > 128)[0]
    room['furnitureTopY'] = float(start + above.min()) if len(above) else meta['floorY']
    rooms.append(room)
    print(name, 'ref light', round(ref, 3), 'glow max', round(float(glow_map.max()), 2))

js = """/*
 * Photographic rooms for the "View in a room" preview. GENERATED by the room renderer (publish.py): the photos are
 * path-traced from 3D scenes with a known camera, so the scale is exact: on the back wall one inch is pxPerInch
 * pixels of the 'width' x 'height' photo. Coordinates are photo pixels.
 *   wall           the visible back wall {left, top, right, bottom}
 *   hangBottomY    where the artwork's bottom edge sits by default (a hand's width above the furniture)
 *   hangCenterY    or: where its centre sits (gallery rule, 57 in from the floor)
 *   light          main light direction for the cast shadow (dx, dy on screen, y down) and its softness
 *   front          furniture cut-out drawn over the artwork; shade / glow: the wall's light, blended onto the artwork
 */
export const ROOMS = %s;

export const ROOM_IDS = ROOMS.map((r) => r.id);
export const roomById = (id) => ROOMS.find((r) => r.id === id) || ROOMS[0];
""" % json.dumps(rooms, indent=4)
open(JS, 'w').write(js)
print('wrote', JS, len(rooms), 'rooms')
