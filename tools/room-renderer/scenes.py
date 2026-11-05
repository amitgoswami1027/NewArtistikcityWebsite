"""Room scenes for the purchase room preview. Units: inches. Back wall: z = 0 (where the artwork hangs)."""
import numpy as np
from pt import Scene, fbm, vnoise

RW = 110          # half width of the room
RH = 108          # ceiling height
RD = 270          # room depth (towards the camera)


def plaster(base, amount=0.05, scale=0.08, seed=1):
    base = np.array(base, float)

    def f(p, n):
        v = fbm(p * scale, 4, seed)
        fine = vnoise(p * 1.7, seed + 5)
        return base[None, :] * (1 - amount + 2 * amount * v[:, None]) * (0.985 + 0.03 * fine[:, None])
    return f


def flat(c):
    return np.array(c, float)


def fabric(base, amount=0.08, seed=3):
    base = np.array(base, float)

    def f(p, n):
        weave = 0.5 + 0.5 * np.sin(p[:, 0] * 9.0) * np.sin(p[:, 1] * 9.0 + p[:, 2] * 9.0)
        v = fbm(p * 0.25, 3, seed)
        return base[None, :] * (1 - amount + amount * v[:, None] + 0.03 * weave[:, None])
    return f


def oak_floor(light=True, plank_w=7.5, seed=11):
    c1 = np.array([0.62, 0.45, 0.30]) if light else np.array([0.36, 0.24, 0.15])

    def f(p, n):
        x, z = p[:, 0], p[:, 2]
        row = np.floor(x / plank_w)
        off = (np.sin(row * 12.9898) * 43758.5453) % 1 * 60
        seg = np.floor((z + off) / 48)
        pid = row * 131 + seg * 17
        tint = 0.78 + 0.34 * ((np.sin(pid * 78.233) * 43758.5453) % 1)
        grain = fbm(np.stack([x * 1.2, z * 0.06 + pid, np.zeros_like(x)], 1), 4, seed)
        streak = 0.5 + 0.5 * np.sin((x * 3.1 + grain * 6.0) + pid)
        col = c1[None, :] * (tint[:, None] * (0.78 + 0.3 * grain[:, None] + 0.1 * streak[:, None]))
        # seams between planks
        fx = (x / plank_w) % 1
        fz = ((z + off) / 48) % 1
        seam = (np.minimum(fx, 1 - fx) * plank_w < 0.12) | (np.minimum(fz, 1 - fz) * 48 < 0.12)
        col[seam] *= 0.4
        return col
    return f


def concrete(base, seed=21):
    base = np.array(base, float)

    def f(p, n):
        v = fbm(p * 0.05, 5, seed)
        s = vnoise(p * 2.5, seed + 2)
        spots = (vnoise(p * 0.9, seed + 4) > 0.93) * 0.06
        return base[None, :] * (0.8 + 0.35 * v[:, None] + 0.04 * s[:, None] - spots[:, None])
    return f


def brick(seed=31):
    bw, bh, mort = 8.0, 2.6, 0.42

    def f(p, n):
        x, y = p[:, 0], p[:, 1]
        row = np.floor(y / (bh + mort))
        xo = x + (row % 2) * (bw + mort) / 2
        col_i = np.floor(xo / (bw + mort))
        fx = xo - col_i * (bw + mort)
        fy = y - row * (bh + mort)
        is_m = (fx > bw) | (fy > bh)
        h = (np.sin(row * 12.9898 + col_i * 78.233) * 43758.5453) % 1
        h2 = (np.sin(row * 39.3468 + col_i * 11.135) * 43758.5453) % 1
        base = np.stack([0.50 + 0.14 * h, 0.24 + 0.08 * h + 0.03 * h2, 0.17 + 0.05 * h2], 1)
        v = fbm(p * 0.6, 3, seed)
        soot = fbm(p * 0.03, 3, seed + 9)
        c = base * (0.82 + 0.3 * v[:, None]) * (0.85 + 0.25 * soot[:, None])
        m = np.array([0.66, 0.63, 0.58])[None, :] * (0.85 + 0.25 * v[:, None])
        # whitewash patches
        wash = np.clip((fbm(p * 0.02, 3, seed + 3) - 0.55) * 2.5, 0, 0.35)
        c = c * (1 - wash[:, None]) + np.array([0.8, 0.77, 0.72])[None, :] * wash[:, None]
        return np.where(is_m[:, None], m, c)
    return f


def jute(seed=41):
    def f(p, n):
        x, z = p[:, 0], p[:, 2]
        weave = 0.5 + 0.5 * np.sin(x * 4.0) * np.sin(z * 4.0)
        v = fbm(p * 0.8, 3, seed)
        border = (np.abs(x) > 52) | (z < 18) | (z > 112)
        base = np.where(border[:, None], np.array([0.42, 0.34, 0.24])[None, :], np.array([0.66, 0.55, 0.40])[None, :])
        return base * (0.82 + 0.12 * weave[:, None] + 0.12 * v[:, None])
    return f


def kilim(seed=43):
    def f(p, n):
        x, z = p[:, 0], p[:, 2]
        u = np.abs(x) / 8.0
        w = np.abs(z - 70) / 8.0
        diamond = ((np.floor(u + w) + np.floor(np.abs(u - w))) % 3)
        cols = np.array([[0.62, 0.30, 0.20], [0.80, 0.70, 0.52], [0.30, 0.38, 0.42]])
        c = cols[diamond.astype(int) % 3]
        border = (np.abs(x) > 50) | (np.abs(z - 70) > 46)
        c = np.where(border[:, None], np.array([0.45, 0.20, 0.16])[None, :], c)
        v = fbm(p * 1.3, 3, seed)
        return c * (0.8 + 0.25 * v[:, None])
    return f


def room_shell(s, wall_mat, floor_mat, ceil_mat='ceiling', window=None, window_side=-1):
    """Walls, floor, ceiling. window = (z0, z1, y0, y1) opening in the side wall at x = window_side * RW."""
    s.box((-RW - 6, -2, -6), (RW + 6, RH + 2, 0), wall_mat)                 # back wall
    s.box((-RW - 6, -4, -6), (RW + 6, 0, RD), floor_mat)                     # floor
    s.box((-RW - 6, RH, -6), (RW + 6, RH + 4, RD), ceil_mat)                 # ceiling
    for side in (-1, 1):
        x0, x1 = (-RW - 6, -RW) if side < 0 else (RW, RW + 6)
        if window and side == window_side:
            z0, z1, y0, y1 = window
            s.box((x0, 0, -6), (x1, RH, z0), wall_mat)
            s.box((x0, 0, z1), (x1, RH, RD), wall_mat)
            s.box((x0, 0, z0), (x1, y0, z1), wall_mat)
            s.box((x0, y1, z0), (x1, RH, z1), wall_mat)
            # window frame (thin mullion + sill)
            xf0, xf1 = (x0 + 2, x0 + 3.2) if side < 0 else (x1 - 3.2, x1 - 2)
            s.box((xf0, y0, (z0 + z1) / 2 - 0.9), (xf1, y1, (z0 + z1) / 2 + 0.9), 'frame')
            s.box((xf0, (y0 + y1) / 2 - 0.9, z0), (xf1, (y0 + y1) / 2 + 0.9, z1), 'frame')
            sx0, sx1 = (-RW - 1, -RW + 4) if side < 0 else (RW - 4, RW + 1)
            s.box((sx0, y0 - 1.5, z0 - 3), (sx1, y0, z1 + 3), 'frame', 0.3)
        else:
            s.box((x0, 0, -6), (x1, RH, RD), wall_mat)
    s.mats['frame'] = flat([0.88, 0.87, 0.85])
    s.mats.setdefault(ceil_mat, flat([0.86, 0.86, 0.85]))


def portal_for(window, side=-1, radiance=(2.2, 2.5, 3.0)):
    z0, z1, y0, y1 = window
    x = side * (RW + 6.5)
    corner = np.array([x, y0, z0])
    eu = np.array([0, 0, z1 - z0], float)
    ev = np.array([0, y1 - y0, 0], float)
    normal = np.array([-side, 0, 0], float)
    return (corner, eu, ev, normal, np.array(radiance, float))


def sun_dir(az_from_side, elev, side=-1, toward_back=0.55):
    d = np.array([side * 1.0, np.tan(np.radians(elev)), toward_back])
    return d / np.linalg.norm(d)


# ================================================================== scenes

def living():
    s = Scene()
    win = (50, 200, 18, 100)
    room_shell(s, 'wall', 'floor', window=win, window_side=-1)
    s.mats['wall'] = plaster([0.84, 0.81, 0.76], 0.04)
    s.mats['floor'] = oak_floor(True)
    s.mats['ceiling'] = flat([0.88, 0.87, 0.85])
    s.gloss['floor'] = (0.22, 0.12)
    # skirting
    s.box((-RW, 0, 0), (RW, 4.5, 0.8), 'skirt', 0.15)
    s.mats['skirt'] = flat([0.88, 0.86, 0.82])
    # rug
    s.box((-66, 0, 8), (66, 0.35, 118), 'rug', 0.2)
    s.mats['rug'] = jute(7)
    # sofa 86 in wide, 34 in high
    sofa = 'sofa'
    s.mats[sofa] = fabric([0.24, 0.29, 0.24], 0.12, 3)
    s.box((-43, 5, 3), (43, 17, 39), sofa, 2.0)          # base
    s.box((-43, 17, 3), (43, 27, 9), sofa, 2.5)          # back frame
    s.box((-36, 19, 7), (-0.4, 34, 14), sofa, 4.0)       # back cushions
    s.box((0.4, 19, 7), (36, 34, 14), sofa, 4.0)
    s.box((-43, 5, 3), (-36, 25, 39), sofa, 3.4)         # arms
    s.box((36, 5, 3), (43, 25, 39), sofa, 3.4)
    s.box((-36, 17, 13), (-0.4, 22.5, 39), sofa, 2.8)    # seat cushions
    s.box((0.4, 17, 13), (36, 22.5, 39), sofa, 2.8)
    s.box((-31, 22.5, 14), (-15, 37, 19), 'pillow1', 3.8)
    s.box((16, 22.5, 14), (31, 35.5, 19), 'pillow2', 3.8)
    s.mats['pillow1'] = fabric([0.66, 0.36, 0.22], 0.12, 9)
    s.mats['pillow2'] = fabric([0.86, 0.84, 0.79], 0.10, 13)
    for x in (-40, 38):
        for z in (6, 35):
            s.box((x, 0, z - 1), (x + 2, 5, z + 1), 'wood_dark', 0.3)
    s.mats['wood_dark'] = flat([0.16, 0.11, 0.08])
    # side table + globe lamp
    s.cyl(62, 22, 10, 21, 22.2, 'oak_top')
    s.cyl(62, 22, 1.3, 0, 21, 'metal')
    s.cyl(62, 22, 6, 0, 0.6, 'metal')
    s.mats['oak_top'] = oak_floor(False, 30, 41)
    s.mats['metal'] = flat([0.12, 0.12, 0.12])
    s.gloss['oak_top'] = (0.12, 0.1)
    s.cyl(62, 22, 1.4, 22.2, 26, 'ceramic')
    s.mats['ceramic'] = flat([0.80, 0.78, 0.72])
    s.sphere((62, 32.5, 22), 6.5, 'globe')
    s.mats['globe'] = flat([0.9, 0.88, 0.84])
    s.emit['globe'] = np.array([6.5, 5.4, 4.0])
    s.lamps.append((np.array([62, 32.5, 22.0]), 6.5, np.array([1.6, 1.3, 0.95])))
    # books on the side table
    s.box((54, 22.2, 14), (64, 24, 22), 'book1', 0.2)
    s.box((55, 24, 15), (63, 25.3, 21), 'book2', 0.2)
    s.mats['book1'] = flat([0.30, 0.38, 0.40])
    s.mats['book2'] = flat([0.75, 0.70, 0.62])
    # coffee table in the foreground
    s.box((-30, 14, 76), (30, 16, 104), 'oak_top', 1.0)
    for x in (-27, 25):
        for z in (79, 99):
            s.box((x, 0, z), (x + 2, 14, z + 2), 'oak_top', 0.4)
    s.box((-12, 16, 84), (2, 18.5, 94), 'book2', 0.3)
    s.cyl(14, 89, 3.2, 16, 25, 'vase')
    s.mats['vase'] = flat([0.30, 0.33, 0.30])
    # tall ribbed floor vase with dried branches, and a pouf
    s.cyl(-86, 26, 8, 0, 30, 'vase_floor')
    s.cyl(-86, 26, 5.5, 30, 34, 'vase_floor')
    s.mats['vase_floor'] = plaster([0.64, 0.58, 0.50], 0.08, 0.4, 5)
    s.cyl(-60, 70, 11, 0, 15, 'pouf')
    s.mats['pouf'] = fabric([0.62, 0.55, 0.45], 0.16, 61)
    s.sun = (sun_dir(0, 20, -1, 1.25), np.array([5.2, 4.8, 4.2]))
    s.portals.append(portal_for(win, -1, (1.5, 1.65, 1.95)))
    s.sky = np.array([1.6, 1.8, 2.1])
    meta = dict(name='Minimalist living room', hang_bottom=42, note='above an 86-inch sofa')
    return s, meta


def foliage(seed=51):
    def f(p, n):
        v = fbm(p * 0.9, 4, seed)
        leaf = vnoise(p * 3.5, seed + 1)
        g = np.array([0.10, 0.26, 0.10])[None, :] * (0.6 + 0.8 * v[:, None])
        g = g * (0.7 + 0.5 * leaf[:, None])
        return g
    return f


def studio():
    s = Scene()
    win = (45, 205, 22, 104)
    room_shell(s, 'wall', 'floor', window=win, window_side=1)
    s.mats['wall'] = plaster([0.70, 0.69, 0.66], 0.06, 0.06, 4)
    s.mats['floor'] = concrete([0.46, 0.45, 0.43])
    s.gloss['floor'] = (0.18, 0.18)
    s.mats['ceiling'] = concrete([0.55, 0.55, 0.54], 25)
    # back wall faced with brick
    s.box((-RW, 0, 0), (RW, RH, 0.6), 'brick')
    s.mats['brick'] = brick()
    # steel window grid (on the right wall)
    z0, z1, y0, y1 = win
    for k in range(1, 4):
        zz = z0 + (z1 - z0) * k / 4
        s.box((RW - 3, y0, zz - 0.6), (RW - 1.6, y1, zz + 0.6), 'steel')
    for k in range(1, 3):
        yy = y0 + (y1 - y0) * k / 3
        s.box((RW - 3, yy - 0.6, z0), (RW - 1.6, yy + 0.6, z1), 'steel')
    s.mats['steel'] = flat([0.07, 0.07, 0.07])
    # long reclaimed-wood bench / console with steel legs
    s.box((-40, 17, 3), (40, 19, 19), 'reclaimed', 0.5)
    for x in (-38, 36):
        s.box((x, 0, 4), (x + 2, 17, 6), 'steel')
        s.box((x, 0, 16), (x + 2, 17, 18), 'steel')
        s.box((x, 2, 4), (x + 2, 3.5, 18), 'steel')
    s.mats['reclaimed'] = oak_floor(False, 5.5, 13)
    # objects on the bench
    s.cyl(-26, 10, 4, 19, 31, 'vase_dark')
    s.mats['vase_dark'] = flat([0.18, 0.18, 0.17])
    s.box((-6, 19, 6), (12, 21, 16), 'book1', 0.2)
    s.box((-4, 21, 7), (10, 22.3, 15), 'book2', 0.2)
    s.mats['book1'] = flat([0.70, 0.62, 0.48])
    s.mats['book2'] = flat([0.22, 0.24, 0.26])
    s.cyl(26, 10, 3.0, 19, 25, 'ceramic')
    s.mats['ceramic'] = flat([0.82, 0.80, 0.75])
    # leather lounge chair (blocky) on the left
    s.box((-92, 6, 30), (-62, 16, 60), 'leather', 3.0)
    s.box((-92, 16, 30), (-62, 34, 37), 'leather', 3.5)
    s.box((-92, 6, 30), (-87, 24, 60), 'leather', 2.5)
    s.box((-67, 6, 30), (-62, 24, 60), 'leather', 2.5)
    s.mats['leather'] = fabric([0.40, 0.22, 0.12], 0.14, 17)
    for x in (-90, -65):
        for z in (32, 57):
            s.box((x, 0, z), (x + 1.5, 6, z + 1.5), 'steel')
    # floor plant (snake plant style) on the right
    s.cyl(78, 34, 8, 0, 18, 'pot_dark')
    s.mats['pot_dark'] = concrete([0.32, 0.32, 0.31], 23)
    for i, (dx, dz, h) in enumerate([(-3, 0, 52), (2, -2, 46), (0, 3, 40), (4, 2, 34), (-4, -3, 38)]):
        s.box((78 + dx - 0.9, 18, 34 + dz - 0.3), (78 + dx + 0.9, h, 34 + dz + 0.3), 'leaf_dark', 0.4)
    s.mats['leaf_dark'] = plaster([0.16, 0.24, 0.13], 0.25, 0.5, 57)
    # pendant lamp
    s.cyl(-62, 70, 0.25, 72, RH, 'steel')
    s.sphere((-62, 66, 70), 5.5, 'bulb')
    s.mats['bulb'] = flat([0.9, 0.85, 0.75])
    s.emit['bulb'] = np.array([9.0, 6.5, 4.0])
    s.lamps.append((np.array([-62, 66, 70.0]), 5.5, np.array([2.2, 1.7, 1.1])))
    s.sun = (sun_dir(0, 18, 1, 1.2), np.array([3.4, 3.1, 2.7]))
    s.portals.append(portal_for(win, 1, (2.4, 2.6, 2.9)))
    s.sky = np.array([1.5, 1.7, 2.0])
    meta = dict(name='Industrial studio', hang_bottom=44, note='above an 80-inch console on a brick wall')
    return s, meta


def bedroom():
    s = Scene()
    win = (60, 200, 22, 100)
    room_shell(s, 'wall', 'floor', window=win, window_side=-1)
    s.mats['wall'] = plaster([0.74, 0.55, 0.44], 0.05, 0.07, 8)   # terracotta lime-wash
    s.mats['floor'] = oak_floor(True, 6.5, 19)
    s.gloss['floor'] = (0.18, 0.14)
    s.mats['ceiling'] = flat([0.88, 0.86, 0.83])
    s.box((-RW, 0, 0), (RW, 4.5, 0.8), 'skirt', 0.15)
    s.mats['skirt'] = flat([0.82, 0.74, 0.66])
    # kilim rug
    s.box((-58, 0, 20), (58, 0.35, 124), 'rug', 0.2)
    s.mats['rug'] = kilim()
    # bed (queen, 64 wide) with rattan-style headboard
    s.box((-35, 0, 2), (35, 44, 5), 'rattan', 1.5)
    s.mats['rattan'] = rattan()
    s.box((-33, 8, 5), (33, 20, 86), 'linen_white', 2.5)            # mattress
    s.box((-34, 3, 5), (34, 8, 86), 'bedframe', 0.8)
    s.mats['bedframe'] = flat([0.42, 0.30, 0.20])
    s.box((-34, 18, 40), (34, 21.5, 88), 'throw', 3.0)              # duvet
    s.mats['throw'] = fabric([0.62, 0.55, 0.45], 0.12, 23)
    s.box((-34, 20.5, 66), (34, 22.5, 82), 'blanket', 1.0)          # folded blanket
    s.mats['blanket'] = fabric([0.62, 0.42, 0.28], 0.14, 29)
    s.box((-29, 20, 7), (-2, 29, 15), 'linen_white', 3.5)            # pillows
    s.box((2, 20, 7), (29, 29, 15), 'linen_white', 3.5)
    s.box((-16, 20, 14), (0, 30, 19), 'pillow_mustard', 3.0)
    s.box((1, 20, 14), (17, 29, 19), 'pillow_sage', 3.0)
    s.mats['linen_white'] = fabric([0.80, 0.78, 0.73], 0.08, 31)
    s.mats['pillow_mustard'] = fabric([0.76, 0.56, 0.22], 0.12, 33)
    s.mats['pillow_sage'] = fabric([0.50, 0.56, 0.44], 0.12, 35)
    # nightstands with lamps
    for side in (-1, 1):
        x = side * 50
        s.cyl(x, 14, 9, 0, 22, 'rattan')
        s.cyl(x, 14, 9.5, 22, 23.5, 'oak_top')
    s.mats['oak_top'] = flat([0.52, 0.38, 0.25])
    s.cyl(-50, 14, 3.5, 23.5, 31, 'ceramic')
    s.mats['ceramic'] = flat([0.80, 0.74, 0.66])
    s.sphere((-50, 36, 14), 5, 'globe')
    s.sphere((50, 30, 14), 5, 'globe')
    s.cyl(50, 14, 3.0, 23.5, 25, 'ceramic')
    s.mats['globe'] = flat([0.92, 0.86, 0.76])
    s.emit['globe'] = np.array([5.0, 3.6, 2.2])
    s.lamps.append((np.array([-50, 36, 14.0]), 5, np.array([1.4, 1.05, 0.7])))
    s.lamps.append((np.array([50, 30, 14.0]), 5, np.array([1.4, 1.05, 0.7])))
    # floor basket with a rolled throw near the window
    s.cyl(-82, 44, 9, 0, 17, 'rattan')
    s.cyl(-82, 44, 5, 17, 24, 'blanket')
    s.sun = (sun_dir(0, 18, -1, 1.2), np.array([3.6, 3.1, 2.5]))
    s.portals.append(portal_for(win, -1, (2.4, 2.5, 2.8)))
    s.sky = np.array([1.6, 1.7, 1.9])
    meta = dict(name='Bohemian bedroom', hang_bottom=54, note='above a 64-inch queen bed', exposure=0.62)
    return s, meta


def rattan(seed=61):
    def f(p, n):
        x, y = p[:, 0], p[:, 1]
        u = np.sin((x + y) * 1.6) * np.sin((x - y) * 1.6)
        v = fbm(p * 0.7, 3, seed)
        base = np.array([0.62, 0.48, 0.30])[None, :]
        holes = (u > 0.7)
        c = base * (0.8 + 0.3 * v[:, None]) * (0.92 + 0.08 * u[:, None])
        c[holes] *= 0.7
        return c
    return f


def gallery():
    s = Scene()
    room_shell(s, 'wall', 'floor')
    s.mats['wall'] = plaster([0.90, 0.89, 0.87], 0.02, 0.05, 71)
    s.mats['floor'] = concrete([0.62, 0.61, 0.58], 73)
    s.gloss['floor'] = (0.25, 0.1)
    s.mats['ceiling'] = flat([0.9, 0.9, 0.9])
    # bench
    s.box((-30, 16, 60), (30, 18.5, 76), 'oak_top', 0.8)
    for x in (-27, 25):
        s.box((x, 0, 61), (x + 2, 16, 75), 'oak_top', 0.4)
    s.mats['oak_top'] = flat([0.60, 0.46, 0.32])
    # track + spotlights
    s.box((-90, RH - 2, 58), (90, RH, 61), 'steel')
    s.mats['steel'] = flat([0.08, 0.08, 0.08])
    for x in (-56, 0, 56):
        s.cyl(x, 60, 1.6, RH - 9, RH - 2, 'steel')
        aim = np.array([x * 1.0, 60.0, 0.0]) - np.array([x * 1.0, RH - 11, 54.0])
        aim /= np.linalg.norm(aim)
        s.lamps.append((np.array([x, RH - 11, 54.0]), 1.6, np.array([420.0, 380.0, 320.0]), (aim, np.cos(np.radians(14)), np.cos(np.radians(30)))))
        s.cyl(x, 56, 2.6, RH - 15, RH - 8, 'steel')
        s.sphere((x, RH - 15.2, 55.2), 1.6, 'spot')
    s.mats['spot'] = flat([0.9, 0.9, 0.9])
    s.emit['spot'] = np.array([60.0, 54.0, 46.0])
    s.portals.append((np.array([-RW, RH - 0.5, 120]), np.array([2 * RW, 0, 0]), np.array([0, 0, 120.0]), np.array([0, -1.0, 0]), np.array([0.18, 0.18, 0.19])))
    s.sky = np.array([0.0, 0.0, 0.0])
    meta = dict(name='Gallery wall', center=57, note='centred 57 in from the floor, gallery style', exposure=1.0)
    return s, meta


SCENES = {'living': living, 'studio': studio, 'bedroom': bedroom, 'gallery': gallery}
