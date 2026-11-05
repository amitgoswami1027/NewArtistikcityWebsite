import React, { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { num } from '@/Components/Shop/shop';
import { ROOMS, roomById } from '@/Components/Shop/rooms';

/*
 * "View in a room": the artwork hung on the wall of a photographed interior, to scale.
 *
 * The rooms (Components/Shop/rooms.js) are path-traced photos with a known camera, so one inch on the back wall is
 * room.pxPerInch photo pixels. The stage is built in layers, all positioned in photo pixels:
 *   1. the room photo, muted (desaturated, lower contrast) so it reads as a backdrop
 *   2. the artwork (frame + print, an HTML box you can drag); no cast shadows or light overlays
 *   3. the furniture cut-out ('front'), muted the same way, so a piece dragged low slips behind the sofa
 *
 * The artwork is the subject, so the stage zooms into the photo around where it hangs (a "camera crop") until the
 * piece fills about half the stage width (more on phones). The enlarged backdrop is softened a little (like a shallow
 * depth of field) so the artwork stays the sharpest thing on screen. Room and artwork are scaled together, so the piece stays
 * exactly to scale against the furniture; only the framing changes. Big pieces need no zoom (zoom = 1).
 */

export { ROOMS };

const FRAMES = {
    NONE: { t: 0, depth: 0.75 },
    BLACK_WOOD: { t: 1.25, depth: 1.5 },
    OAK: { t: 1.25, depth: 1.5 },
    WHITE_FLOAT: { t: 1.1, gap: 0.45, depth: 1.75 },
};

// how much of the stage the artwork should fill: share of the width, capped by share of the height
// and how far the camera may zoom: enough to keep some furniture in view on a wide screen, closer on a phone
const FILL = { desk: { w: 0.46, h: 0.68, max: 3.4 }, phone: { w: 0.62, h: 0.76, max: 4.6 } };

/** Warm the browser cache so switching rooms is instant. */
export function usePreloadRooms() {
    useEffect(() => {
        const keep = [];
        ROOMS.forEach((r) => [r.src, r.front].forEach((u) => { const i = new Image(); i.src = u; keep.push(i); }));
        return () => { keep.length = 0; };
    }, []);
}

/** One-click room thumbnails. */
export function RoomSwitcher({ value, onChange, compact = false }) {
    return (
        <div className={`pz-roomthumbs ${compact ? 'is-compact' : ''}`} role="radiogroup" aria-label="Choose a room">
            {ROOMS.map((r) => (
                <button key={r.id} type="button" role="radio" aria-checked={value === r.id} data-room={r.id}
                        className={`pz-roomthumb ${value === r.id ? 'is-on' : ''}`} onClick={() => onChange(r.id)}>
                    <img src={r.thumb} alt="" width="300" height="200" loading="lazy" />
                    <span>{r.label}</span>
                </button>
            ))}
        </div>
    );
}

/**
 * @param room     room id (see ROOMS)
 * @param image    artwork image URL
 * @param widthIn  artwork width in inches (print size, or the original's size)
 * @param heightIn artwork height in inches
 * @param frame    NONE | BLACK_WOOD | OAK | WHITE_FLOAT
 * @param surface  'canvas' | 'paper' (how the unframed edge looks)
 */
export default function RoomStage({ room = 'living', image, title, widthIn, heightIn, frame = 'NONE', surface = 'canvas', showSize = true, resetToken = 0 }) {
    const r = roomById(room);
    const f = FRAMES[frame] || FRAMES.NONE;
    const ppi = r.pxPerInch;
    const outerWIn = Number(widthIn) + 2 * (f.t + (f.gap || 0));
    const outerHIn = Number(heightIn) + 2 * (f.t + (f.gap || 0));
    const outerW = outerWIn * ppi;
    const outerH = outerHIn * ppi;
    const stage = useRef(null);
    const [stageW, setStageW] = useState(0);
    const [pos, setPos] = useState(null);          // artwork centre, photo pixels
    const [loaded, setLoaded] = useState({});
    const drag = useRef(null);

    const onWall = useCallback((p) => ({
        x: Math.min(r.wall.right - outerW / 2 - 2, Math.max(r.wall.left + outerW / 2 + 2, p.x)),
        y: Math.min(r.floorY - outerH / 2 - 2, Math.max(r.wall.top + outerH / 2 + 2, p.y)),
    }), [r, outerW, outerH]);
    // gallery rule: centre at 57 in; elsewhere the bottom edge sits a hand's width above the furniture
    const homeAt = onWall({ x: r.centerX, y: r.hangCenterY ? r.hangCenterY : r.hangBottomY - outerH / 2 });

    // camera crop around the hanging spot (photo pixels); recomputed for a new room, size, frame or stage width
    const phone = stageW > 0 && stageW < 600;
    const fill = phone ? FILL.phone : FILL.desk;
    // fitted on the bare artwork, so choosing a frame visibly adds the moulding instead of zooming the room out
    const bareW = Number(widthIn) * ppi;
    const bareH = Number(heightIn) * ppi;
    const zoom = Math.max(1, Math.min(fill.max, (fill.w * r.width) / bareW, (fill.h * r.height) / bareH));
    const cw = r.width / zoom;
    const ch = r.height / zoom;
    // the piece sits a little above the middle so the top of the furniture stays in view
    const cx = Math.min(r.width - cw, Math.max(0, homeAt.x - cw / 2));
    const cy = Math.min(r.height - ch, Math.max(0, homeAt.y - ch * 0.4));
    const crop = { x: cx, y: cy, w: cw, h: ch };

    // dragging stays on the wall and inside the visible crop
    const clamp = useCallback((p) => {
        const w = onWall(p);
        const m = 4 / zoom;
        return {
            x: Math.min(crop.x + crop.w - outerW / 2 - m, Math.max(crop.x + outerW / 2 + m, w.x)),
            y: Math.min(crop.y + crop.h - outerH / 2 - m, Math.max(crop.y + outerH / 2 + m, w.y)),
        };
    }, [onWall, crop.x, crop.y, crop.w, crop.h, outerW, outerH, zoom]);
    const home = useCallback(() => clamp(homeAt), [clamp, homeAt.x, homeAt.y]); // eslint-disable-line react-hooks/exhaustive-deps

    useLayoutEffect(() => {
        const el = stage.current;
        if (!el) return undefined;
        const update = () => setStageW(el.clientWidth);
        update();
        if (typeof ResizeObserver === 'undefined') { window.addEventListener('resize', update); return () => window.removeEventListener('resize', update); }
        const ro = new ResizeObserver(update);
        ro.observe(el);
        return () => ro.disconnect();
    }, []);
    // new room: hang it in that room's usual spot
    useEffect(() => { setPos(home()); }, [room, resetToken]); // eslint-disable-line react-hooks/exhaustive-deps
    // new size or frame: keep it where the visitor put it, but on the wall
    useEffect(() => { setPos((p) => (p ? clamp(p) : home())); }, [outerW, outerH, zoom]); // eslint-disable-line react-hooks/exhaustive-deps

    const scale = stageW ? (stageW / r.width) * zoom : 0;   // screen px per photo px

    const p = pos || home();
    const onDown = (e) => {
        if (e.button !== undefined && e.button !== 0) return;
        e.preventDefault();
        if (e.currentTarget.setPointerCapture) e.currentTarget.setPointerCapture(e.pointerId);
        drag.current = { sx: e.clientX, sy: e.clientY, p0: p };
    };
    const onMove = (e) => {
        if (!drag.current || !scale) return;
        const d = drag.current;
        setPos(clamp({ x: d.p0.x + (e.clientX - d.sx) / scale, y: d.p0.y + (e.clientY - d.sy) / scale }));
    };
    const onUp = () => { drag.current = null; };
    const onKey = (e) => {
        const step = (e.shiftKey ? 6 : 1.5) * ppi;
        const moves = { ArrowLeft: [-step, 0], ArrowRight: [step, 0], ArrowUp: [0, -step], ArrowDown: [0, step] };
        if (e.key === 'Home') { e.preventDefault(); setPos(home()); return; }
        if (!moves[e.key]) return;
        e.preventDefault();
        setPos(clamp({ x: p.x + moves[e.key][0], y: p.y + moves[e.key][1] }));
    };

    const left = p.x - outerW / 2;
    const top = p.y - outerH / 2;
    const box = {
        left: `${(left / r.width) * 100}%`, top: `${(top / r.height) * 100}%`,
        width: `${(outerW / r.width) * 100}%`, height: `${(outerH / r.height) * 100}%`,
    };
    const pad = (inches) => `${(inches / outerWIn) * 100}%`;
    const behind = p.y + outerH / 2 > (r.furnitureTopY || r.floorY) + 1;

    return (
        <div className={`pz-stage pz-stage--photo ${loaded[r.id] ? 'is-loaded' : ''}`} ref={stage} data-room={r.id}
             data-zoom={zoom.toFixed(3)} style={{ aspectRatio: `${r.width} / ${r.height}`, '--pz-soft': `${Math.max(0, (zoom - 1.6) * 0.45).toFixed(2)}px` }}>
          <div className="pz-scene" style={{
              width: `${zoom * 100}%`, height: `${zoom * 100}%`,
              transform: `translate(${(-crop.x / r.width) * 100}%, ${(-crop.y / r.height) * 100}%)`,
          }}>
            <img className="pz-stage__photo" src={r.src} alt={`${r.label}, ${r.note}`} draggable="false"
                 onLoad={() => setLoaded((x) => ({ ...x, [r.id]: true }))} />

            <div className={`pz-art pz-art--${frame.toLowerCase()} pz-art--${surface}`} style={box} role="slider" tabIndex={0}
                 data-width-in={num(widthIn)} data-height-in={num(heightIn)}
                 aria-label={`${title}, ${num(widthIn)} by ${num(heightIn)} inches, in the ${r.label.toLowerCase()}. Drag or use the arrow keys to move it on the wall.`}
                 aria-valuetext={`${Math.round((r.floorY - p.y) / ppi)} inches from the floor to its centre`}
                 onPointerDown={onDown} onPointerMove={onMove} onPointerUp={onUp} onPointerCancel={onUp} onKeyDown={onKey}>
                <div className="pz-art__frame" style={{ padding: pad(f.t) }}>
                    <div className="pz-art__float" style={{ padding: f.gap ? pad(f.gap) : 0 }}>
                        <div className="pz-art__canvas">
                            {image ? <img src={image} alt="" draggable="false" /> : <span className="pz-art__blank"></span>}
                        </div>
                    </div>
                </div>
                {showSize && !behind && <span className="pz-art__size" aria-hidden="true">{num(widthIn)}″ × {num(heightIn)}″</span>}
            </div>

            <img className="pz-stage__front" src={r.front} alt="" aria-hidden="true" draggable="false" />
          </div>
            <span className="pz-stage__hint" aria-hidden="true"><i className="fa fa-hand-pointer-o"></i> Drag to move</span>
        </div>
    );
}
