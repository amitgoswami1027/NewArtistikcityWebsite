import React, { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { num } from '@/Components/Shop/shop';

/*
 * Interactive room visualiser. Each scene is a 1000 × 680 unit stage: a wall 160 inches wide and 96 inches high
 * (6.25 units per inch) and a floor. Furniture is drawn to real sizes, so an artwork of W × H inches is W × H × 6.25 units and its size is
 * honest next to the sofa, bed or desk. Layers: wall (back SVG) → the artwork (an HTML box you can drag) → furniture
 * (front SVG), so the piece always hangs behind the furniture. Its shadow falls away from the scene's light source and
 * grows with the frame depth.
 */

export const U = 6.25;               // stage units per inch
const W = 1000;
const H = 680;
const FLOOR = 600;            // a 96-inch wall: floor to ceiling

const FRAMES = {
    NONE: { t: 0, depth: 0.75 },
    BLACK_WOOD: { t: 1.25, depth: 1.5 },
    OAK: { t: 1.25, depth: 1.5 },
    WHITE_FLOAT: { t: 1.1, gap: 0.45, depth: 1.75 },
};

/* ------------------------------------------------------------------ scenes */

export const SCENES = {
    living: {
        label: 'Modern living room', icon: 'fa-home', wall: ['#efe9e1', '#e4dbcf'], floor: ['#c79f78', '#a17a55'],
        hang: FLOOR - 42 * U, light: { x: 860, y: FLOOR - 52 * U }, note: 'above an 84-inch sofa',
        back: () => (
            <g>
                <rect x="0" y={FLOOR - 4} width={W} height="6" fill="#d9cdbd" />
                <ellipse cx="500" cy={FLOOR + 62} rx="360" ry="26" fill="#e6dccd" opacity=".9" />
            </g>
        ),
        front: () => (
            <g>
                {/* sofa 84 x 34 in */}
                <rect x={500 - 42 * U} y={FLOOR - 30 * U} width={84 * U} height={16 * U} rx="22" fill="#6d7f74" />
                <rect x={500 - 44 * U} y={FLOOR - 22 * U} width={9 * U} height={18 * U} rx="16" fill="#5f7166" />
                <rect x={500 + 35 * U} y={FLOOR - 22 * U} width={9 * U} height={18 * U} rx="16" fill="#5f7166" />
                <rect x={500 - 36 * U} y={FLOOR - 17 * U} width={72 * U} height={11 * U} rx="12" fill="#77897e" />
                <rect x={500 - 38 * U} y={FLOOR - 4 * U} width="10" height={4 * U} fill="#2f2a25" />
                <rect x={500 + 38 * U - 10} y={FLOOR - 4 * U} width="10" height={4 * U} fill="#2f2a25" />
                <rect x={500 - 26 * U} y={FLOOR - 25 * U} width={13 * U} height={9 * U} rx="9" fill="#e9c46a" transform={`rotate(-6 ${500 - 20 * U} ${FLOOR - 21 * U})`} />
                <rect x={500 + 13 * U} y={FLOOR - 25 * U} width={13 * U} height={9 * U} rx="9" fill="#e9e1d6" transform={`rotate(5 ${500 + 20 * U} ${FLOOR - 21 * U})`} />
                {/* side table + lamp (the light source) */}
                <rect x="805" y={FLOOR - 24 * U} width={18 * U} height="8" rx="3" fill="#3b3128" />
                <rect x={805 + 8.5 * U} y={FLOOR - 24 * U} width="6" height={24 * U} fill="#3b3128" />
                <rect x={805 + 2 * U} y={FLOOR - 1.5 * U} width={14 * U} height="6" rx="3" fill="#3b3128" />
                <rect x={805 + 8.5 * U} y={FLOOR - 44 * U} width="4" height={20 * U} fill="#2b2b2b" />
                <path d={`M${805 + 3 * U} ${FLOOR - 44 * U} L${805 + 15 * U} ${FLOOR - 44 * U} L${805 + 12.5 * U} ${FLOOR - 53 * U} L${805 + 5.5 * U} ${FLOOR - 53 * U} Z`} fill="#f7ecd2" />
                <ellipse cx={805 + 9 * U} cy={FLOOR - 44 * U} rx={6.5 * U} ry="5" fill="#fff3d6" opacity=".9" />
                {/* plant */}
                <rect x="70" y={FLOOR - 14 * U} width={10 * U} height={14 * U} rx="8" fill="#d8c8b4" />
                <path d={`M101 ${FLOOR - 14 * U}c-34-46-40-104-6-140 6 46 22 86 6 140zm0 0c30-42 58-74 34-122-18 40-34 70-34 122zm0 0c-46-22-80-8-92 14 34 2 60-4 92-14z`} fill="#4f7d5c" />
            </g>
        ),
    },
    bedroom: {
        label: 'Minimalist bedroom', icon: 'fa-bed', wall: ['#e6ece6', '#d8e1d8'], floor: ['#d9c7ad', '#bea686'],
        hang: FLOOR - 52 * U, light: { x: 60, y: 260 }, note: 'above a 64-inch queen bed',
        back: () => (
            <g>
                {/* window on the left: the light source */}
                <rect x="20" y="130" width={26 * U} height={48 * U} rx="4" fill="#f7fbff" stroke="#c9d3c9" strokeWidth="6" />
                <line x1={20 + 13 * U} x2={20 + 13 * U} y1="130" y2={130 + 48 * U} stroke="#c9d3c9" strokeWidth="4" />
                <rect x="20" y="130" width={26 * U} height={48 * U} fill="url(#pz-sun)" />
                <rect x="0" y={FLOOR - 4} width={W} height="6" fill="#cfd8cf" />
            </g>
        ),
        front: () => (
            <g>
                {/* headboard 64 in wide, 44 in high */}
                <rect x={500 - 34 * U} y={FLOOR - 44 * U} width={68 * U} height={30 * U} rx="14" fill="#c7b9a6" />
                <rect x={500 - 32 * U} y={FLOOR - 25 * U} width={64 * U} height={14 * U} rx="10" fill="#f4f1ec" />
                <rect x={500 - 32 * U} y={FLOOR - 15 * U} width={64 * U} height={10 * U} rx="6" fill="#9fb3a4" />
                <rect x={500 - 26 * U} y={FLOOR - 30 * U} width={20 * U} height={8 * U} rx="10" fill="#fff" />
                <rect x={500 + 6 * U} y={FLOOR - 30 * U} width={20 * U} height={8 * U} rx="10" fill="#fff" />
                <rect x={500 - 33 * U} y={FLOOR - 5 * U} width="10" height={5 * U} fill="#8c7b66" />
                <rect x={500 + 33 * U - 10} y={FLOOR - 5 * U} width="10" height={5 * U} fill="#8c7b66" />
                {/* nightstands with lamps */}
                {[-1, 1].map((s) => (
                    <g key={s}>
                        <rect x={500 + s * 44 * U - 9 * U} y={FLOOR - 22 * U} width={18 * U} height={22 * U} rx="4" fill="#d9cbb8" />
                        <line x1={500 + s * 44 * U - 9 * U} x2={500 + s * 44 * U + 9 * U} y1={FLOOR - 11 * U} y2={FLOOR - 11 * U} stroke="#bfae98" strokeWidth="3" />
                        <circle cx={500 + s * 44 * U} cy={FLOOR - 26.5 * U} r={4.5 * U} fill="#f6efe2" stroke="#e1d5c2" strokeWidth="2" />
                    </g>
                ))}
            </g>
        ),
    },
    office: {
        label: 'Professional office', icon: 'fa-briefcase', wall: ['#e7eaee', '#d9dee5'], floor: ['#7b8088', '#5f646c'],
        hang: FLOOR - 50 * U, light: { x: 762, y: FLOOR - 37 * U }, note: 'above a 60-inch desk',
        back: () => (
            <g>
                {/* floating shelf with books */}
                <rect x="70" y="250" width={28 * U} height="10" fill="#4b3b2e" />
                {[0, 1, 2, 3, 4].map((i) => <rect key={i} x={80 + i * 15} y={250 - (40 + (i % 3) * 8)} width="12" height={40 + (i % 3) * 8} fill={['#c2410c', '#1f2937', '#a3a3a3', '#0e7490', '#e5c07b'][i]} />)}
                <rect x="0" y={FLOOR - 4} width={W} height="6" fill="#c9ced6" />
            </g>
        ),
        front: () => (
            <g>
                {/* desk 60 x 30 in */}
                <rect x={500 - 30 * U} y={FLOOR - 30 * U} width={60 * U} height={2.5 * U} fill="#8b5e3c" />
                <rect x={500 - 28 * U} y={FLOOR - 27.5 * U} width={2 * U} height={27.5 * U} fill="#5c3d27" />
                <rect x={500 + 26 * U} y={FLOOR - 27.5 * U} width={2 * U} height={27.5 * U} fill="#5c3d27" />
                {/* monitor */}
                <rect x={500 - 12 * U} y={FLOOR - 46 * U} width={24 * U} height={14 * U} rx="4" fill="#111827" />
                <rect x={500 - 1 * U} y={FLOOR - 32 * U} width={2 * U} height={2 * U} fill="#374151" />
                {/* desk lamp (light source) */}
                <rect x={500 + 20 * U} y={FLOOR - 31 * U} width={6 * U} height="5" rx="2" fill="#374151" />
                <line x1={500 + 23 * U} y1={FLOOR - 31 * U} x2={500 + 19 * U} y2={FLOOR - 40 * U} stroke="#374151" strokeWidth="4" />
                <path d={`M${500 + 17 * U} ${FLOOR - 40 * U} l${8 * U} -3 l-2 ${4 * U} z`} fill="#374151" />
                <circle cx={500 + 22 * U} cy={FLOOR - 37 * U} r={3 * U} fill="#fde68a" opacity=".55" />
                {/* chair */}
                <rect x={500 - 10 * U} y={FLOOR - 36 * U} width={20 * U} height={14 * U} rx="10" fill="#1f2937" />
                <rect x={500 - 11 * U} y={FLOOR - 20 * U} width={22 * U} height={4 * U} rx="8" fill="#111827" />
                <rect x={500 - 1 * U} y={FLOOR - 16 * U} width={2 * U} height={13 * U} fill="#111827" />
                <rect x={500 - 9 * U} y={FLOOR - 3 * U} width={18 * U} height={1.5 * U} rx="4" fill="#111827" />
                {/* plant */}
                <rect x="880" y={FLOOR - 12 * U} width={9 * U} height={12 * U} rx="6" fill="#e5e7eb" />
                <path d={`M908 ${FLOOR - 12 * U}c-24-34-28-80-4-106 4 34 16 64 4 106zm0 0c22-30 42-54 24-92-14 28-24 52-24 92z`} fill="#3f6b4f" />
            </g>
        ),
    },
    gallery: {
        label: 'Blank gallery wall', icon: 'fa-square-o', wall: ['#fbfbfa', '#f0efec'], floor: ['#d4d0c8', '#bcb7ad'],
        hang: FLOOR - 38 * U, center: 57, light: { x: 500, y: -60 }, note: 'centred at eye level, 57 in from the floor',
        back: () => (
            <g>
                {/* track spotlights */}
                <rect x="140" y="0" width="720" height="8" fill="#2b2b2b" />
                {[260, 500, 740].map((x) => (
                    <g key={x}>
                        <rect x={x - 6} y="8" width="12" height="18" fill="#2b2b2b" />
                        <path d={`M${x - 14} 26 h28 l-6 22 h-16z`} fill="#3a3a3a" />
                        <path d={`M${x - 8} 48 L${x - 120} 420 L${x + 120} 420 L${x + 8} 48 Z`} fill="url(#pz-beam)" />
                    </g>
                ))}
                <rect x="0" y={FLOOR - 4} width={W} height="6" fill="#e2dfd8" />
            </g>
        ),
        front: () => (
            <g>
                {/* gallery bench 48 in */}
                <rect x={500 - 24 * U} y={FLOOR - 18 * U} width={48 * U} height={3 * U} rx="3" fill="#e8e2d6" stroke="#cfc6b6" />
                <rect x={500 - 21 * U} y={FLOOR - 15 * U} width={2 * U} height={15 * U} fill="#9a8f7e" />
                <rect x={500 + 19 * U} y={FLOOR - 15 * U} width={2 * U} height={15 * U} fill="#9a8f7e" />
                {/* person for scale: 5 ft 7 in */}
                <g opacity=".18" fill="#0e1a3a">
                    <circle cx="880" cy={FLOOR - 63 * U} r={4.5 * U} />
                    <rect x={880 - 7 * U} y={FLOOR - 57 * U} width={14 * U} height={26 * U} rx="20" />
                    <rect x={880 - 5 * U} y={FLOOR - 33 * U} width={4 * U} height={33 * U} rx="8" />
                    <rect x={880 + 1 * U} y={FLOOR - 33 * U} width={4 * U} height={33 * U} rx="8" />
                </g>
            </g>
        ),
    },
};

/* ------------------------------------------------------------------ stage */

/**
 * @param image    artwork image URL
 * @param widthIn  artwork width in inches (print size, or the original's size)
 * @param heightIn artwork height in inches
 * @param frame    NONE | BLACK_WOOD | OAK | WHITE_FLOAT
 * @param surface  'canvas' | 'paper' (how the unframed edge looks)
 */
export default function RoomStage({ image, title, widthIn, heightIn, frame = 'NONE', surface = 'canvas', scene = 'living', showSize = true, resetToken = 0 }) {
    const s = SCENES[scene] || SCENES.living;
    const f = FRAMES[frame] || FRAMES.NONE;
    const outerW = (Number(widthIn) + 2 * (f.t + (f.gap || 0))) * U;
    const outerH = (Number(heightIn) + 2 * (f.t + (f.gap || 0))) * U;
    const stage = useRef(null);
    const [scale, setScale] = useState(1);
    const [pos, setPos] = useState(null);          // centre in stage units
    const drag = useRef(null);

    const clamp = useCallback((p) => ({
        x: Math.min(W - outerW / 2 - 4, Math.max(outerW / 2 + 4, p.x)),
        y: Math.min(FLOOR - outerH / 2 - 6, Math.max(outerH / 2 + 10, p.y)),
    }), [outerW, outerH]);
    // gallery rule: centre at 57 in; elsewhere the bottom edge sits a hand's width above the furniture
    const home = useCallback(() => clamp({ x: 500, y: s.center ? FLOOR - s.center * U : s.hang - outerH / 2 }), [clamp, s, outerH]);

    useLayoutEffect(() => {
        const el = stage.current;
        if (!el) return undefined;
        const update = () => setScale(el.clientWidth / W);
        update();
        if (typeof ResizeObserver === 'undefined') { window.addEventListener('resize', update); return () => window.removeEventListener('resize', update); }
        const ro = new ResizeObserver(update);
        ro.observe(el);
        return () => ro.disconnect();
    }, []);
    // new room: hang it in that room's usual spot
    useEffect(() => { setPos(home()); }, [scene, resetToken]); // eslint-disable-line react-hooks/exhaustive-deps
    // new size or frame: keep it where the visitor put it, but inside the wall
    useEffect(() => { setPos((p) => (p ? clamp(p) : home())); }, [outerW, outerH]); // eslint-disable-line react-hooks/exhaustive-deps

    const p = pos || home();
    const onDown = (e) => {
        if (e.button !== undefined && e.button !== 0) return;
        e.preventDefault();
        e.currentTarget.setPointerCapture && e.currentTarget.setPointerCapture(e.pointerId);
        drag.current = { sx: e.clientX, sy: e.clientY, p0: p };
    };
    const onMove = (e) => {
        if (!drag.current) return;
        const d = drag.current;
        setPos(clamp({ x: d.p0.x + (e.clientX - d.sx) / scale, y: d.p0.y + (e.clientY - d.sy) / scale }));
    };
    const onUp = () => { drag.current = null; };
    const onKey = (e) => {
        const step = e.shiftKey ? 40 : 10;
        const moves = { ArrowLeft: [-step, 0], ArrowRight: [step, 0], ArrowUp: [0, -step], ArrowDown: [0, step] };
        if (e.key === 'Home') { e.preventDefault(); setPos(home()); return; }
        if (!moves[e.key]) return;
        e.preventDefault();
        setPos(clamp({ x: p.x + moves[e.key][0], y: p.y + moves[e.key][1] }));
    };

    // shadow: away from the light, longer for deeper frames, softer further from the light
    const dx = p.x - s.light.x;
    const dy = p.y - s.light.y;
    const dist = Math.max(1, Math.hypot(dx, dy));
    const depthPx = (f.depth * U * 0.9 + 3) * scale;
    const sx = (dx / dist) * depthPx;
    const sy = (dy / dist) * depthPx + 2 * scale;
    const blur = (8 + dist / 60) * scale;
    const shadow = `${sx.toFixed(1)}px ${sy.toFixed(1)}px ${blur.toFixed(1)}px rgba(20, 16, 10, ${Math.max(0.22, 0.42 - dist / 3000).toFixed(2)})`;
    const sheenAngle = Math.round((Math.atan2(dy, dx) * 180) / Math.PI + 90);

    const box = {
        left: `${((p.x - outerW / 2) / W) * 100}%`, top: `${((p.y - outerH / 2) / H) * 100}%`,
        width: `${(outerW / W) * 100}%`, height: `${(outerH / H) * 100}%`, boxShadow: shadow,
    };
    const pad = (inches) => `${(inches / (Number(widthIn) + 2 * (f.t + (f.gap || 0)))) * 100}%`;

    return (
        <div className="pz-stage" ref={stage} style={{ aspectRatio: `${W} / ${H}` }}>
            <svg className="pz-stage__layer" viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="xMidYMid slice" aria-hidden="true">
                <defs>
                    <linearGradient id="pz-wall" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor={s.wall[0]} /><stop offset="1" stopColor={s.wall[1]} /></linearGradient>
                    <linearGradient id="pz-floor" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor={s.floor[0]} /><stop offset="1" stopColor={s.floor[1]} /></linearGradient>
                    <radialGradient id="pz-glow" cx={s.light.x / W} cy={Math.max(0, s.light.y) / FLOOR} r=".55"><stop offset="0" stopColor="#fff8e7" stopOpacity=".55" /><stop offset="1" stopColor="#fff8e7" stopOpacity="0" /></radialGradient>
                    <linearGradient id="pz-sun" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stopColor="#fff" stopOpacity=".7" /><stop offset="1" stopColor="#dfeaf5" stopOpacity=".5" /></linearGradient>
                    <linearGradient id="pz-beam" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#fff6dc" stopOpacity=".55" /><stop offset="1" stopColor="#fff6dc" stopOpacity="0" /></linearGradient>
                </defs>
                <rect width={W} height={FLOOR} fill="url(#pz-wall)" />
                <rect width={W} height={FLOOR} fill="url(#pz-glow)" />
                <rect y={FLOOR} width={W} height={H - FLOOR} fill="url(#pz-floor)" />
                {s.back()}
            </svg>

            <div className={`pz-art pz-art--${frame.toLowerCase()} pz-art--${surface}`} style={box} role="slider" tabIndex={0}
                 aria-label={`${title}, ${num(widthIn)} by ${num(heightIn)} inches. Drag or use the arrow keys to move it on the wall.`}
                 aria-valuetext={`${Math.round(p.x)}, ${Math.round(p.y)}`}
                 onPointerDown={onDown} onPointerMove={onMove} onPointerUp={onUp} onPointerCancel={onUp} onKeyDown={onKey}>
                <div className="pz-art__frame" style={{ padding: pad(f.t) }}>
                    <div className="pz-art__float" style={{ padding: f.gap ? pad(f.gap) : 0 }}>
                        <div className="pz-art__canvas">
                            {image ? <img src={image} alt="" draggable="false" /> : <span className="pz-art__blank"></span>}
                            <span className="pz-art__sheen" style={{ background: `linear-gradient(${sheenAngle}deg, rgba(255,255,255,.16), rgba(255,255,255,0) 55%)` }}></span>
                        </div>
                    </div>
                </div>
                {showSize && (
                    <span className="pz-art__size" aria-hidden="true">{num(widthIn)}″ × {num(heightIn)}″</span>
                )}
            </div>

            <svg className="pz-stage__layer pz-stage__front" viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="xMidYMid slice" aria-hidden="true">
                {s.front()}
            </svg>
            <span className="pz-stage__hint" aria-hidden="true"><i className="fa fa-hand-pointer-o"></i> Drag to move</span>
        </div>
    );
}

