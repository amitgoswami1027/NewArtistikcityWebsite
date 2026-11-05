import React, { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { num } from '@/Components/Shop/shop';
import { ROOMS, roomById } from '@/Components/Shop/rooms';

/*
 * "View in a room": the artwork hung on the wall of a photographed interior, to scale.
 *
 * The rooms (Components/Shop/rooms.js) are path-traced photos with a known camera, so one inch on the back wall is
 * room.pxPerInch photo pixels. The stage is built in layers, all positioned in photo pixels and scaled to the box:
 *   1. the room photo
 *   2. the artwork (frame + print, an HTML box you can drag), with the cast shadow from the room's main light
 *   3. the room's wall light blended onto the artwork: 'shade' (multiply: lamp falloff, darker corners) and
 *      'glow' (screen: a sun patch falling across it), so the piece is lit like the wall it hangs on
 *   4. the furniture cut-out ('front'), so a piece dragged low slips behind the sofa instead of floating over it
 */

export { ROOMS };

const FRAMES = {
    NONE: { t: 0, depth: 0.75 },
    BLACK_WOOD: { t: 1.25, depth: 1.5 },
    OAK: { t: 1.25, depth: 1.5 },
    WHITE_FLOAT: { t: 1.1, gap: 0.45, depth: 1.75 },
};

/** Warm the browser cache so switching rooms is instant. */
export function usePreloadRooms() {
    useEffect(() => {
        const keep = [];
        ROOMS.forEach((r) => [r.src, r.front, r.shade, r.glow].forEach((u) => { const i = new Image(); i.src = u; keep.push(i); }));
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
    const [scale, setScale] = useState(0);
    const [pos, setPos] = useState(null);          // artwork centre, photo pixels
    const [loaded, setLoaded] = useState({});
    const drag = useRef(null);

    const clamp = useCallback((p) => ({
        x: Math.min(r.wall.right - outerW / 2 - 2, Math.max(r.wall.left + outerW / 2 + 2, p.x)),
        y: Math.min(r.floorY - outerH / 2 - 2, Math.max(r.wall.top + outerH / 2 + 2, p.y)),
    }), [r, outerW, outerH]);
    // gallery rule: centre at 57 in; elsewhere the bottom edge sits a hand's width above the furniture
    const home = useCallback(() => clamp({ x: r.centerX, y: r.hangCenterY ? r.hangCenterY : r.hangBottomY - outerH / 2 }), [clamp, r, outerH]);

    useLayoutEffect(() => {
        const el = stage.current;
        if (!el) return undefined;
        const update = () => setScale(el.clientWidth / r.width);
        update();
        if (typeof ResizeObserver === 'undefined') { window.addEventListener('resize', update); return () => window.removeEventListener('resize', update); }
        const ro = new ResizeObserver(update);
        ro.observe(el);
        return () => ro.disconnect();
    }, [r.width]);
    // new room: hang it in that room's usual spot
    useEffect(() => { setPos(home()); }, [room, resetToken]); // eslint-disable-line react-hooks/exhaustive-deps
    // new size or frame: keep it where the visitor put it, but on the wall
    useEffect(() => { setPos((p) => (p ? clamp(p) : home())); }, [outerW, outerH]); // eslint-disable-line react-hooks/exhaustive-deps

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
    const s = scale || 1;
    // cast shadow: away from the main light, longer for deeper frames, softer for diffuse light; plus a contact shadow
    const L = r.light || { dx: 0, dy: 1, soft: 0.5 };
    const reach = (f.depth * ppi * 1.1 + 1.5) * s;
    const blur = (2 + f.depth * ppi * (1 + 3 * L.soft)) * s;
    const shadow = [
        `${(L.dx * reach).toFixed(1)}px ${(Math.max(0.25, L.dy) * reach + 1.5 * s).toFixed(1)}px ${blur.toFixed(1)}px rgba(18, 14, 8, ${(0.5 - 0.25 * L.soft).toFixed(2)})`,
        `0 ${(0.6 * s).toFixed(1)}px ${(1.6 * s).toFixed(1)}px rgba(0, 0, 0, .35)`,
    ].join(', ');
    // the room's light, cropped to where the artwork hangs
    const lightBg = (url) => ({
        backgroundImage: `url("${url}")`,
        backgroundSize: `${(r.width * s).toFixed(1)}px ${(r.height * s).toFixed(1)}px`,
        backgroundPosition: `${(-left * s).toFixed(1)}px ${(-top * s).toFixed(1)}px`,
    });
    const box = {
        left: `${(left / r.width) * 100}%`, top: `${(top / r.height) * 100}%`,
        width: `${(outerW / r.width) * 100}%`, height: `${(outerH / r.height) * 100}%`, boxShadow: shadow,
    };
    const pad = (inches) => `${(inches / outerWIn) * 100}%`;
    const behind = p.y + outerH / 2 > (r.furnitureTopY || r.floorY) + 1;

    return (
        <div className={`pz-stage pz-stage--photo ${loaded[r.id] ? 'is-loaded' : ''}`} ref={stage} data-room={r.id}
             style={{ aspectRatio: `${r.width} / ${r.height}` }}>
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
                <span className="pz-art__shade" style={lightBg(r.shade)} aria-hidden="true"></span>
                <span className="pz-art__glow" style={lightBg(r.glow)} aria-hidden="true"></span>
                {showSize && !behind && <span className="pz-art__size" aria-hidden="true">{num(widthIn)}″ × {num(heightIn)}″</span>}
            </div>

            <img className="pz-stage__front" src={r.front} alt="" aria-hidden="true" draggable="false" />
            <span className="pz-stage__hint" aria-hidden="true"><i className="fa fa-hand-pointer-o"></i> Drag to move</span>
        </div>
    );
}
