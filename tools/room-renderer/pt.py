"""Tiny vectorised path tracer (numpy) for interior backgrounds.

Units are inches. +x right, +y up, -z away from the camera; the back wall is the plane z = 0.
Geometry: axis-aligned boxes (with bevelled shading normals), spheres, vertical cylinders.
Lighting: sun (directional, through window openings), sky through window portals (area lights), sphere lamps.
Indirect light: cosine-weighted diffuse bounces. Materials are Lambertian with procedural albedo.
"""
import numpy as np

EPS = 1e-3
INF = 1e30


# ------------------------------------------------------------------ noise

def _hash(ix, iy, iz, seed):
    h = (ix * 374761393 + iy * 668265263 + iz * 2147483647 + seed * 144665) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    h = h ^ (h >> 16)
    return (h & 0xFFFFFF) / float(0xFFFFFF)


def vnoise(p, seed=0):
    """3-D value noise in [0,1], p: (N,3)."""
    pi = np.floor(p).astype(np.int64)
    f = p - pi
    f = f * f * (3 - 2 * f)
    out = 0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (f[:, 0] if dx else 1 - f[:, 0]) * (f[:, 1] if dy else 1 - f[:, 1]) * (f[:, 2] if dz else 1 - f[:, 2])
                out = out + w * _hash(pi[:, 0] + dx, pi[:, 1] + dy, pi[:, 2] + dz, seed)
    return out


def fbm(p, octaves=4, seed=0):
    s, a, tot = 0, 1.0, 0
    q = p.copy()
    for o in range(octaves):
        s = s + a * vnoise(q, seed + o * 17)
        tot += a
        a *= 0.5
        q = q * 2.03
    return s / tot


# ------------------------------------------------------------------ scene

class Scene:
    def __init__(self):
        self.boxes = []      # (min, max, mat, bevel)
        self.spheres = []    # (center, radius, mat)
        self.cyls = []       # (cx, cz, r, y0, y1, mat)
        self.mats = {}       # name -> callable(points, normals) -> albedo (N,3)
        self.emit = {}       # mat -> radiance (3,)
        self.sun = None      # (direction to sun (3,), radiance (3,))
        self.portals = []    # (corner (3,), edge u (3,), edge v (3,), normal into room (3,), radiance (3,))
        self.lamps = []      # (center, radius, radiance)
        self.sky = np.array([0.0, 0.0, 0.0])
        self.gloss = {}      # mat -> (probability of a glossy bounce, roughness)

    def box(self, mn, mx, mat, bevel=0.0):
        self.boxes.append((np.array(mn, float), np.array(mx, float), mat, bevel))

    def sphere(self, c, r, mat):
        self.spheres.append((np.array(c, float), float(r), mat))

    def cyl(self, cx, cz, r, y0, y1, mat):
        self.cyls.append((cx, cz, r, y0, y1, mat))

    def compile(self):
        names = sorted(set([b[2] for b in self.boxes] + [s[2] for s in self.spheres] + [c[5] for c in self.cyls]))
        self.mat_index = {n: i for i, n in enumerate(names)}
        self.mat_names = names
        if self.boxes:
            self.bmin = np.array([b[0] for b in self.boxes])
            self.bmax = np.array([b[1] for b in self.boxes])
            self.bmat = np.array([self.mat_index[b[2]] for b in self.boxes])
            self.bbev = np.array([b[3] for b in self.boxes])

    # -------------------------------------------------------------- intersection
    def intersect(self, o, d, tmax=None, any_hit=False):
        n = o.shape[0]
        t = np.full(n, INF if tmax is None else 0.0)
        if tmax is not None:
            t = tmax.copy()
        kind = np.full(n, -1, np.int32)     # 0 box, 1 sphere, 2 cyl
        idx = np.full(n, -1, np.int32)
        inv = 1.0 / np.where(np.abs(d) < 1e-12, 1e-12, d)
        for i, (mn, mx, mat, bev) in enumerate(self.boxes):
            t1 = (mn - o) * inv
            t2 = (mx - o) * inv
            tn = np.minimum(t1, t2).max(axis=1)
            tf = np.maximum(t1, t2).min(axis=1)
            th = np.where(tn > EPS, tn, tf)
            hit = (tf >= np.maximum(tn, EPS)) & (th < t)
            if hit.any():
                t = np.where(hit, th, t)
                kind = np.where(hit, 0, kind)
                idx = np.where(hit, i, idx)
        for i, (c, r, mat) in enumerate(self.spheres):
            oc = o - c
            b = (oc * d).sum(1)
            cc = (oc * oc).sum(1) - r * r
            disc = b * b - cc
            ok = disc > 0
            sq = np.sqrt(np.where(ok, disc, 0))
            t0 = -b - sq
            t1 = -b + sq
            th = np.where(t0 > EPS, t0, t1)
            hit = ok & (th > EPS) & (th < t)
            if hit.any():
                t = np.where(hit, th, t)
                kind = np.where(hit, 1, kind)
                idx = np.where(hit, i, idx)
        for i, (cx, cz, r, y0, y1, mat) in enumerate(self.cyls):
            ox = o[:, 0] - cx
            oz = o[:, 2] - cz
            a = d[:, 0] ** 2 + d[:, 2] ** 2
            b = ox * d[:, 0] + oz * d[:, 2]
            cc = ox * ox + oz * oz - r * r
            disc = b * b - a * cc
            ok = (disc > 0) & (a > 1e-12)
            sq = np.sqrt(np.where(ok, disc, 0))
            aa = np.where(a > 1e-12, a, 1)
            best = np.full(n, INF)
            for tt in ((-b - sq) / aa, (-b + sq) / aa):
                y = o[:, 1] + tt * d[:, 1]
                good = ok & (tt > EPS) & (y >= y0) & (y <= y1)
                best = np.where(good & (tt < best), tt, best)
            # caps
            for yc in (y0, y1):
                dy = np.where(np.abs(d[:, 1]) < 1e-12, 1e-12, d[:, 1])
                tt = (yc - o[:, 1]) / dy
                px = o[:, 0] + tt * d[:, 0] - cx
                pz = o[:, 2] + tt * d[:, 2] - cz
                good = (tt > EPS) & (px * px + pz * pz <= r * r)
                best = np.where(good & (tt < best), tt, best)
            hit = best < t
            if hit.any():
                t = np.where(hit, best, t)
                kind = np.where(hit, 2, kind)
                idx = np.where(hit, i, idx)
        return t, kind, idx

    def occluded(self, o, d, dist):
        t, kind, idx = self.intersect(o, d, tmax=dist - 2 * EPS)
        return kind >= 0

    # -------------------------------------------------------------- shading data
    def surface(self, o, d, t, kind, idx):
        p = o + d * t[:, None]
        n = np.zeros_like(p)
        mat = np.full(len(t), -1, np.int32)
        geo_n = np.zeros_like(p)
        m = kind == 0
        if m.any():
            bi = idx[m]
            mn = self.bmin[bi]
            mx = self.bmax[bi]
            pp = p[m]
            dist = np.stack([np.abs(pp - mn), np.abs(pp - mx)], 1)   # (k,2,3)
            flat = dist.reshape(len(pp), 6)
            face = flat.argmin(1)
            side = face // 3      # 0 min face, 1 max face
            axis = face % 3
            nn = np.zeros_like(pp)
            nn[np.arange(len(pp)), axis] = np.where(side == 0, -1.0, 1.0)
            geo = nn.copy()
            # bevel: lean the normal toward neighbouring faces near the edges
            bev = self.bbev[bi]
            if (bev > 0).any():
                for ax in range(3):
                    lo = pp[:, ax] - mn[:, ax]
                    hi = mx[:, ax] - pp[:, ax]
                    near_lo = (lo < bev) & (axis != ax)
                    near_hi = (hi < bev) & (axis != ax)
                    w_lo = np.where(near_lo, 1 - lo / np.maximum(bev, 1e-6), 0)
                    w_hi = np.where(near_hi, 1 - hi / np.maximum(bev, 1e-6), 0)
                    w_lo = w_lo * w_lo * (3 - 2 * w_lo)
                    w_hi = w_hi * w_hi * (3 - 2 * w_hi)
                    nn[:, ax] += -w_lo * 0.9 + w_hi * 0.9
                nn /= np.linalg.norm(nn, axis=1, keepdims=True)
            n[m] = nn
            geo_n[m] = geo
            mat[m] = self.bmat[bi]
        m = kind == 1
        if m.any():
            for i, (c, r, mt) in enumerate(self.spheres):
                mm = m & (idx == i)
                if mm.any():
                    nn = (p[mm] - c) / r
                    n[mm] = nn
                    geo_n[mm] = nn
                    mat[mm] = self.mat_index[mt]
        m = kind == 2
        if m.any():
            for i, (cx, cz, r, y0, y1, mt) in enumerate(self.cyls):
                mm = m & (idx == i)
                if mm.any():
                    pp = p[mm]
                    nn = np.zeros_like(pp)
                    cap_lo = np.abs(pp[:, 1] - y0) < 1e-3
                    cap_hi = np.abs(pp[:, 1] - y1) < 1e-3
                    side = ~(cap_lo | cap_hi)
                    nn[side, 0] = (pp[side, 0] - cx) / r
                    nn[side, 2] = (pp[side, 2] - cz) / r
                    nn[cap_lo, 1] = -1
                    nn[cap_hi, 1] = 1
                    n[mm] = nn
                    geo_n[mm] = nn
                    mat[mm] = self.mat_index[mt]
        # face the ray
        flip = (geo_n * d).sum(1) > 0
        geo_n[flip] *= -1
        n[flip] *= -1
        return p, n, geo_n, mat

    def albedo(self, p, n, mat):
        a = np.zeros_like(p)
        for i, name in enumerate(self.mat_names):
            m = mat == i
            if m.any():
                f = self.mats[name]
                a[m] = f(p[m], n[m]) if callable(f) else np.asarray(f, float)
        return np.clip(a, 0, 0.95)

    def emission(self, mat):
        e = np.zeros((len(mat), 3))
        for name, rad in self.emit.items():
            if name in self.mat_index:
                e[mat == self.mat_index[name]] = rad
        return e


# ------------------------------------------------------------------ sampling helpers

def onb(n):
    sign = np.where(n[:, 2] >= 0, 1.0, -1.0)
    a = -1.0 / (sign + n[:, 2])
    b = n[:, 0] * n[:, 1] * a
    t = np.stack([1 + sign * n[:, 0] ** 2 * a, sign * b, -sign * n[:, 0]], 1)
    bt = np.stack([b, sign + n[:, 1] ** 2 * a, -n[:, 1]], 1)
    return t, bt


def cosine_dir(n, rng):
    u1 = rng.random(len(n))
    u2 = rng.random(len(n))
    r = np.sqrt(u1)
    phi = 2 * np.pi * u2
    x, y, z = r * np.cos(phi), r * np.sin(phi), np.sqrt(np.maximum(0, 1 - u1))
    t, b = onb(n)
    return t * x[:, None] + b * y[:, None] + n * z[:, None]


def direct(scene, p, n, rng):
    """Next-event estimation from sun, window portals and lamps. Returns irradiance/pi weighted radiance (N,3)."""
    L = np.zeros_like(p)
    Ls = np.zeros_like(p)
    po = p + n * 0.02
    if scene.sun is not None:
        sd, srad = scene.sun
        # soft sun: jitter the direction a little (sun disc + window diffusion)
        jitter = (rng.random((len(p), 3)) - 0.5) * 0.035
        d = sd[None, :] + jitter
        d /= np.linalg.norm(d, axis=1, keepdims=True)
        cos = (n * d).sum(1)
        ok = cos > 0
        if ok.any():
            occ = scene.occluded(po[ok], d[ok], np.full(ok.sum(), 1e5))
            vis = ~occ
            contrib = np.zeros((ok.sum(), 3))
            contrib[vis] = srad[None, :] * cos[ok][vis, None]
            Ls[ok] += contrib
    for corner, eu, ev, pn, rad in scene.portals:
        u = rng.random(len(p))
        v = rng.random(len(p))
        q = corner[None, :] + eu[None, :] * u[:, None] + ev[None, :] * v[:, None]
        w = q - po
        dist = np.linalg.norm(w, axis=1)
        d = w / dist[:, None]
        cos = (n * d).sum(1)
        cosl = -(d * pn[None, :]).sum(1)
        area = np.linalg.norm(np.cross(eu, ev))
        ok = (cos > 0) & (cosl > 0)
        if ok.any():
            occ = scene.occluded(po[ok], d[ok], dist[ok] - 0.5)
            g = cos[ok] * cosl[ok] * area / (dist[ok] ** 2) / np.pi
            contrib = np.zeros((ok.sum(), 3))
            contrib[~occ] = rad[None, :] * g[~occ, None]
            L[ok] += contrib
    for lamp in scene.lamps:
        c, r, rad = lamp[0], lamp[1], lamp[2]
        spot = lamp[3] if len(lamp) > 3 else None
        # sample a point on the visible cap: uniform on sphere, keep facing points
        z = rng.random(len(p)) * 2 - 1
        phi = rng.random(len(p)) * 2 * np.pi
        s = np.sqrt(1 - z * z)
        ln = np.stack([s * np.cos(phi), s * np.sin(phi), z], 1)
        q = c[None, :] + ln * (r + 0.05)
        w = q - po
        dist = np.linalg.norm(w, axis=1)
        d = w / dist[:, None]
        cos = (n * d).sum(1)
        cosl = -(d * ln).sum(1)
        area = 4 * np.pi * r * r
        ok = (cos > 0) & (cosl > 0)
        if ok.any():
            occ = scene.occluded(po[ok], d[ok], dist[ok] - 0.1)
            g = cos[ok] * cosl[ok] * area / (dist[ok] ** 2) / np.pi
            if spot is not None:
                sdir, cin, cout = spot
                ca = -(d[ok] * sdir[None, :]).sum(1)
                cone = np.clip((ca - cout) / (cin - cout), 0, 1)
                g = g * cone * cone * (3 - 2 * cone)
            contrib = np.zeros((ok.sum(), 3))
            contrib[~occ] = rad[None, :] * g[~occ, None]
            L[ok] += contrib
    return L, Ls


# ------------------------------------------------------------------ render

def render(scene, cam, W, H, spp=16, bounces=3, seed=1, log=print):
    """cam: dict(pos, look (point), up, fov_y degrees). Returns radiance, first-hit albedo and normal, each (H,W,3)."""
    scene.compile()
    rng = np.random.default_rng(seed)
    pos = np.array(cam['pos'], float)
    fwd = np.array(cam['look'], float) - pos
    fwd /= np.linalg.norm(fwd)
    right = np.cross(fwd, np.array([0, 1.0, 0]))
    right /= np.linalg.norm(right)
    up = np.cross(right, fwd)
    th = np.tan(np.radians(cam['fov_y']) / 2)
    tw = th * W / H
    ys, xs = np.mgrid[0:H, 0:W]
    xs = xs.reshape(-1).astype(float)
    ys = ys.reshape(-1).astype(float)
    N = W * H
    acc = np.zeros((N, 3))
    alb_acc = np.zeros((N, 3))
    nrm_acc = np.zeros((N, 3))
    dep_acc = np.zeros(N)
    sun_acc = np.zeros((N, 3))
    for s in range(spp):
        jx = rng.random(N)
        jy = rng.random(N)
        sx = ((xs + jx) / W * 2 - 1) * tw
        sy = (1 - (ys + jy) / H * 2) * th
        d = fwd[None, :] + right[None, :] * sx[:, None] + up[None, :] * sy[:, None]
        d /= np.linalg.norm(d, axis=1, keepdims=True)
        o = np.repeat(pos[None, :], N, 0)
        thr = np.ones((N, 3))
        rad = np.zeros((N, 3))
        alive = np.arange(N)
        spec = np.zeros(N, bool)          # the ray came from a glossy reflection
        for bnc in range(bounces + 1):
            t, kind, idx = scene.intersect(o, d)
            hit = kind >= 0
            miss_sky = ~hit & ((bnc == 0) | spec)
            rad[alive[miss_sky]] += thr[miss_sky] * scene.sky[None, :]
            if not hit.any():
                break
            alive = alive[hit]
            o, d, t, kind, idx, thr, spec = o[hit], d[hit], t[hit], kind[hit], idx[hit], thr[hit], spec[hit]
            p, n, gn, mat = scene.surface(o, d, t, kind, idx)
            if bnc == 0:
                rad[alive] += thr * scene.emission(mat)
            elif spec.any():
                rad[alive[spec]] += thr[spec] * scene.emission(mat[spec])
            a = scene.albedo(p, n, mat)
            if bnc == 0:
                alb_acc[alive] += a
                nrm_acc[alive] += n
                dep_acc[alive] += t
            ld, lsun = direct(scene, p, n, rng)
            rad[alive] += thr * a * (ld + lsun)
            if bnc == 0:
                sun_acc[alive] += np.minimum(thr * a * lsun, 8.0)
            if bnc == bounces:
                break
            nd = cosine_dir(n, rng)
            nthr = thr * a
            nspec = np.zeros(len(nd), bool)
            for gname, (prob, rough) in scene.gloss.items():
                if gname not in scene.mat_index:
                    continue
                gm = (mat == scene.mat_index[gname]) & (rng.random(len(nd)) < prob)
                if gm.any():
                    r = d[gm] - 2 * (d[gm] * n[gm]).sum(1)[:, None] * n[gm]
                    r = r + rough * (rng.random((gm.sum(), 3)) - 0.5)
                    r /= np.linalg.norm(r, axis=1, keepdims=True)
                    nd[gm] = r
                    nthr[gm] = thr[gm] * 0.85
                    nspec[gm] = True
            thr = nthr
            spec = nspec
            o = p + gn * 0.02
            d = nd
        np.minimum(rad, 8.0, out=rad)       # tame fireflies
        acc += rad
        if log and (s + 1) % max(1, spp // 8) == 0:
            log(f'  spp {s + 1}/{spp}')
    img = (acc / spp).reshape(H, W, 3)
    alb = (alb_acc / spp).reshape(H, W, 3)
    nrm = (nrm_acc / spp).reshape(H, W, 3)
    dep = (dep_acc / spp).reshape(H, W)
    sun = (sun_acc / spp).reshape(H, W, 3)
    return img, alb, nrm, dep, sun
