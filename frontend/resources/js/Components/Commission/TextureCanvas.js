import React, { useEffect, useRef, useState } from 'react';

/**
 * Browser-side "texture simulator": redraws the uploaded photo on an HTML5 canvas with per-pixel
 * operations and procedural textures so the customer can feel how the finished painting will look.
 *   PORTRAIT   - warm oil tones, softened brushwork (posterise), canvas weave, vignette
 *   LANDSCAPE  - vivid colour, impasto posterisation, canvas weave
 *   WATERCOLOR - soft blur, lighter washes, pigment pooling on edges, cold-press paper grain
 *   CHARCOAL   - high-contrast monochrome, Sobel edge strokes, paper tooth
 * `intensity` (0..1) blends between the original photo and the fully stylised version.
 */

const cache = new Map();
function loadImage(src) {
    if (!cache.has(src)) {
        cache.set(src, new Promise((resolve, reject) => {
            const img = new Image();
            img.decoding = 'async';
            img.onload = () => resolve(img);
            img.onerror = reject;
            img.src = src;
        }));
    }
    return cache.get(src);
}

// deterministic noise so the texture doesn't "shimmer" when the slider moves
function noiseCanvas(w, h, seed, contrast) {
    const c = document.createElement('canvas');
    c.width = w; c.height = h;
    const ctx = c.getContext('2d');
    const d = ctx.createImageData(w, h);
    let s = seed;
    for (let i = 0; i < d.data.length; i += 4) {
        s = (s * 1664525 + 1013904223) >>> 0;
        const v = 128 + ((s / 4294967296) - 0.5) * contrast;
        d.data[i] = d.data[i + 1] = d.data[i + 2] = v;
        d.data[i + 3] = 255;
    }
    ctx.putImageData(d, 0, 0);
    return c;
}

function weavePattern(ctx) {
    const t = document.createElement('canvas');
    t.width = 6; t.height = 6;
    const c = t.getContext('2d');
    c.fillStyle = '#808080'; c.fillRect(0, 0, 6, 6);
    c.fillStyle = '#9a9a9a'; c.fillRect(0, 0, 3, 3); c.fillRect(3, 3, 3, 3);
    c.fillStyle = '#6a6a6a'; c.fillRect(3, 0, 3, 1); c.fillRect(0, 3, 3, 1);
    return ctx.createPattern(t, 'repeat');
}

const clamp = (v) => (v < 0 ? 0 : v > 255 ? 255 : v);

function adjust(data, { sat = 1, con = 1, bright = 0, warm = 0, gamma = 1, levels = 0, gray = false }) {
    const step = levels ? 255 / (levels - 1) : 0;
    for (let i = 0; i < data.length; i += 4) {
        let r = data[i], g = data[i + 1], b = data[i + 2];
        const l = 0.299 * r + 0.587 * g + 0.114 * b;
        if (gray) { r = g = b = l; } else { r = l + (r - l) * sat; g = l + (g - l) * sat; b = l + (b - l) * sat; }
        r = (r - 128) * con + 128 + bright + warm; g = (g - 128) * con + 128 + bright; b = (b - 128) * con + 128 + bright - warm;
        if (gamma !== 1) { r = 255 * Math.pow(clamp(r) / 255, gamma); g = 255 * Math.pow(clamp(g) / 255, gamma); b = 255 * Math.pow(clamp(b) / 255, gamma); }
        if (step) { r = Math.round(r / step) * step; g = Math.round(g / step) * step; b = Math.round(b / step) * step; }
        data[i] = clamp(r); data[i + 1] = clamp(g); data[i + 2] = clamp(b);
    }
}

function boxBlur(src, w, h, radius) {
    if (radius < 1) return src;
    const tmp = new Uint8ClampedArray(src.length);
    const out = new Uint8ClampedArray(src.length);
    const pass = (from, to, horizontal) => {
        for (let y = 0; y < h; y++) {
            for (let x = 0; x < w; x++) {
                let r = 0, g = 0, b = 0, n = 0;
                for (let k = -radius; k <= radius; k++) {
                    const xx = horizontal ? x + k : x, yy = horizontal ? y : y + k;
                    if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue;
                    const j = (yy * w + xx) * 4;
                    r += from[j]; g += from[j + 1]; b += from[j + 2]; n++;
                }
                const i = (y * w + x) * 4;
                to[i] = r / n; to[i + 1] = g / n; to[i + 2] = b / n; to[i + 3] = from[i + 3];
            }
        }
    };
    pass(src, tmp, true);
    pass(tmp, out, false);
    return out;
}

function edges(data, w, h) {
    const lum = new Float32Array(w * h);
    for (let i = 0, p = 0; i < data.length; i += 4, p++) lum[p] = 0.299 * data[i] + 0.587 * data[i + 1] + 0.114 * data[i + 2];
    const out = new Float32Array(w * h);
    for (let y = 1; y < h - 1; y++) {
        for (let x = 1; x < w - 1; x++) {
            const p = y * w + x;
            const gx = -lum[p - w - 1] - 2 * lum[p - 1] - lum[p + w - 1] + lum[p - w + 1] + 2 * lum[p + 1] + lum[p + w + 1];
            const gy = -lum[p - w - 1] - 2 * lum[p - w] - lum[p - w + 1] + lum[p + w - 1] + 2 * lum[p + w] + lum[p + w + 1];
            out[p] = Math.min(255, Math.sqrt(gx * gx + gy * gy));
        }
    }
    return out;
}

function stylise(img, theme, maxSize) {
    const scale = Math.min(1, maxSize / Math.max(img.naturalWidth, img.naturalHeight));
    const w = Math.max(1, Math.round(img.naturalWidth * scale));
    const h = Math.max(1, Math.round(img.naturalHeight * scale));
    const c = document.createElement('canvas');
    c.width = w; c.height = h;
    const ctx = c.getContext('2d');
    ctx.drawImage(img, 0, 0, w, h);
    const original = ctx.getImageData(0, 0, w, h);
    let px = new Uint8ClampedArray(original.data);

    if (theme === 'PORTRAIT') {
        px = boxBlur(px, w, h, 1);
        adjust(px, { sat: 1.12, con: 1.12, warm: 10, levels: 22 });
    } else if (theme === 'LANDSCAPE') {
        adjust(px, { sat: 1.38, con: 1.14, bright: 4, levels: 14 });
    } else if (theme === 'WATERCOLOR') {
        const e = edges(px, w, h);
        px = boxBlur(px, w, h, 2);
        adjust(px, { sat: 1.08, con: 0.92, gamma: 0.82, levels: 10 });
        for (let p = 0, i = 0; p < e.length; p++, i += 4) {           // pigment pooling along edges
            const k = Math.min(1, e[p] / 180) * 0.35;
            px[i] = px[i] * (1 - k); px[i + 1] = px[i + 1] * (1 - k); px[i + 2] = px[i + 2] * (1 - k);
        }
    } else if (theme === 'CHARCOAL') {
        const e = edges(px, w, h);
        adjust(px, { gray: true, con: 1.65, bright: 18 });
        for (let p = 0, i = 0; p < e.length; p++, i += 4) {
            const v = clamp(px[i] - e[p] * 0.9);
            px[i] = px[i + 1] = px[i + 2] = v;
        }
    }
    ctx.putImageData(new ImageData(px, w, h), 0, 0);

    // textures
    ctx.save();
    if (theme === 'PORTRAIT' || theme === 'LANDSCAPE') {
        ctx.globalCompositeOperation = 'overlay';
        ctx.globalAlpha = theme === 'PORTRAIT' ? 0.35 : 0.28;
        ctx.fillStyle = weavePattern(ctx);
        ctx.fillRect(0, 0, w, h);
    } else {
        ctx.globalCompositeOperation = 'multiply';
        ctx.globalAlpha = theme === 'CHARCOAL' ? 0.55 : 0.4;
        ctx.drawImage(noiseCanvas(w, h, theme === 'CHARCOAL' ? 7 : 3, theme === 'CHARCOAL' ? 90 : 60), 0, 0);
    }
    ctx.restore();

    // edge treatment: dark vignette for oils, white paper border for watercolour / charcoal
    const g = ctx.createRadialGradient(w / 2, h / 2, Math.min(w, h) * 0.35, w / 2, h / 2, Math.max(w, h) * 0.75);
    if (theme === 'PORTRAIT' || theme === 'LANDSCAPE') {
        g.addColorStop(0, 'rgba(0,0,0,0)'); g.addColorStop(1, theme === 'PORTRAIT' ? 'rgba(20,10,0,0.45)' : 'rgba(0,0,0,0.2)');
    } else {
        g.addColorStop(0, 'rgba(255,255,255,0)'); g.addColorStop(1, 'rgba(250,248,242,0.75)');
    }
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, w, h);

    return { styled: c, original, w, h };
}

export default function TextureCanvas({ src, theme, intensity = 0.85, showOriginal = false, maxSize = 900, className = '', onReady, label }) {
    const canvasRef = useRef(null);
    const [result, setResult] = useState(null);
    const [error, setError] = useState(false);

    useEffect(() => {
        let alive = true;
        setError(false);
        if (!src || !theme) { setResult(null); return undefined; }
        loadImage(src)
            .then((img) => {
                if (!alive) return;
                // let the browser paint the loading state first
                requestAnimationFrame(() => { if (alive) setResult(stylise(img, theme, maxSize)); });
            })
            .catch(() => alive && setError(true));
        return () => { alive = false; };
    }, [src, theme, maxSize]);

    useEffect(() => {
        const canvas = canvasRef.current;
        if (!canvas || !result) return;
        canvas.width = result.w;
        canvas.height = result.h;
        const ctx = canvas.getContext('2d');
        ctx.putImageData(result.original, 0, 0);
        if (!showOriginal) {
            ctx.globalAlpha = Math.max(0, Math.min(1, intensity));
            ctx.drawImage(result.styled, 0, 0);
            ctx.globalAlpha = 1;
        }
        if (onReady) onReady(canvas);
    }, [result, intensity, showOriginal]);

    if (error) {
        return <div className={`flex items-center justify-center bg-gray-100 text-sm text-gray-500 p-6 ${className}`}>We couldn't load this photo. Please upload it again.</div>;
    }
    if (!result) {
        return (
            <div className={`flex items-center justify-center bg-gray-100 text-sm text-gray-500 ${className}`} style={{ aspectRatio: '4 / 3' }} role="status">
                <i className="fa fa-spinner fa-spin mr-2" aria-hidden="true"></i> Applying texture…
            </div>
        );
    }
    return <canvas ref={canvasRef} className={`w-full h-auto ${className}`} role="img" aria-label={label || 'Preview of your photo with the selected painting texture'} />;
}
