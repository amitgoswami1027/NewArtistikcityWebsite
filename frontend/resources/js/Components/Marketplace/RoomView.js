import React, { useState } from 'react';
import { num } from '@/Components/Marketplace/shared';

/*
 * "View in a room": the painting drawn to its real proportions against furniture of a known size.
 * Every scene is 1000 × 620 SVG units representing a wall 160 inches wide (6.25 units per inch).
 */
const U = 6.25;
const FLOOR = 520;

const SCENES = {
    living: {
        label: 'Living room', icon: 'fa-bed', wall: ['#f3efe8', '#e7dfd2'], floor: ['#b98a5e', '#8c6440'],
        hangBottom: FLOOR - 34 * U,          // sofa back is ~34 in high; hang ~8 in above it
        caption: 'Above an 84-inch sofa',
        furniture: () => (
            <g>
                <rect x={500 - 42 * U} y={FLOOR - 32 * U} width={84 * U} height={14 * U} rx="18" fill="#5b7083" />
                <rect x={500 - 44 * U} y={FLOOR - 24 * U} width={9 * U} height={20 * U} rx="14" fill="#4d6175" />
                <rect x={500 + 35 * U} y={FLOOR - 24 * U} width={9 * U} height={20 * U} rx="14" fill="#4d6175" />
                <rect x={500 - 36 * U} y={FLOOR - 20 * U} width={72 * U} height={10 * U} rx="10" fill="#566b80" />
                <rect x={500 - 38 * U} y={FLOOR - 4 * U} width="12" height={4 * U} fill="#3a2b1e" />
                <rect x={500 + 38 * U - 12} y={FLOOR - 4 * U} width="12" height={4 * U} fill="#3a2b1e" />
                <rect x={500 - 24 * U} y={FLOOR - 28 * U} width={12 * U} height={8 * U} rx="8" fill="#e9c46a" />
                <rect x={500 + 12 * U} y={FLOOR - 28 * U} width={12 * U} height={8 * U} rx="8" fill="#e76f51" />
                <rect x="860" y={FLOOR - 12 * U} width={8 * U} height={12 * U} rx="6" fill="#c8b6a6" />
                <path d={`M885 ${FLOOR - 12 * U}c-30-40-40-90-10-120 8 40 22 70 10 120zm0 0c28-38 50-70 30-110-14 34-30 60-30 110z`} fill="#4f7d5c" />
            </g>
        ),
    },
    office: {
        label: 'Office', icon: 'fa-briefcase', wall: ['#e9edf1', '#d9dfe6'], floor: ['#6b7280', '#4b5563'],
        hangBottom: FLOOR - 42 * U,
        caption: 'Above a 60-inch desk',
        furniture: () => (
            <g>
                <rect x={500 - 30 * U} y={FLOOR - 30 * U} width={60 * U} height={2.5 * U} fill="#8b5e3c" />
                <rect x={500 - 28 * U} y={FLOOR - 27.5 * U} width={2 * U} height={27.5 * U} fill="#5c3d27" />
                <rect x={500 + 26 * U} y={FLOOR - 27.5 * U} width={2 * U} height={27.5 * U} fill="#5c3d27" />
                <rect x={500 - 8 * U} y={FLOOR - 42 * U} width={16 * U} height={10 * U} rx="4" fill="#1f2937" />
                <rect x={500 - 1 * U} y={FLOOR - 32 * U} width={2 * U} height={2 * U} fill="#374151" />
                <rect x={500 + 16 * U} y={FLOOR - 36 * U} width={2 * U} height={6 * U} fill="#9ca3af" />
                <circle cx={500 + 17 * U} cy={FLOOR - 37 * U} r={3 * U} fill="#fcd34d" opacity=".85" />
                <rect x={500 - 10 * U} y={FLOOR - 20 * U} width={20 * U} height={3 * U} rx="6" fill="#111827" />
                <rect x={500 - 1 * U} y={FLOOR - 17 * U} width={2 * U} height={14 * U} fill="#111827" />
                <rect x={500 - 8 * U} y={FLOOR - 3 * U} width={16 * U} height={1.5 * U} rx="4" fill="#111827" />
            </g>
        ),
    },
    hallway: {
        label: 'Hallway', icon: 'fa-columns', wall: ['#f7f5f2', '#ece8e1'], floor: ['#d6cfc4', '#b8ae9f'],
        hangBottom: FLOOR - 40 * U,
        caption: 'Above a 48-inch console, beside an 80-inch door',
        furniture: () => (
            <g>
                <rect x={60} y={FLOOR - 80 * U} width={34 * U} height={80 * U} fill="#d4c7b5" stroke="#bfae98" strokeWidth="4" />
                <circle cx={60 + 30 * U} cy={FLOOR - 38 * U} r="6" fill="#8a7a63" />
                <rect x={500 - 24 * U} y={FLOOR - 31 * U} width={48 * U} height={3 * U} fill="#2f2a25" />
                <rect x={500 - 22 * U} y={FLOOR - 28 * U} width={1.5 * U} height={28 * U} fill="#2f2a25" />
                <rect x={500 + 20.5 * U} y={FLOOR - 28 * U} width={1.5 * U} height={28 * U} fill="#2f2a25" />
                <rect x={500 - 20 * U} y={FLOOR - 36 * U} width={5 * U} height={5 * U} rx="10" fill="#c2410c" opacity=".8" />
                <rect x={500 + 14 * U} y={FLOOR - 38 * U} width={4 * U} height={7 * U} rx="6" fill="#e5e7eb" stroke="#9ca3af" />
            </g>
        ),
    },
};

export default function RoomView({ painting }) {
    const [scene, setScene] = useState('living');
    const s = SCENES[scene];
    const hIn = Number(painting.height_inches) || 12;
    const wIn = Number(painting.width_inches) || 16;
    const frame = painting.is_framed ? 1.5 : 0; // add a visual 1.5 in moulding for framed pieces
    const w = (wIn + frame * 2) * U;
    const h = (hIn + frame * 2) * U;
    const x = 500 - w / 2;
    const bottom = Math.min(s.hangBottom, FLOOR - 12);
    const y = Math.max(20, bottom - h);
    const t = { transition: 'all .45s cubic-bezier(.2,.8,.2,1)' };
    const id = `rv-${painting.id}`;

    return (
        <div>
            <div className="flex flex-wrap gap-2 mb-3" role="tablist" aria-label="Room">
                {Object.entries(SCENES).map(([k, v]) => (
                    <button key={k} type="button" role="tab" aria-selected={scene === k} onClick={() => setScene(k)}
                            className={`inline-flex items-center gap-2 h-10 px-4 rounded-full text-sm font-bold border ${scene === k ? 'bg-ink text-white border-ink' : 'bg-white text-gray-700 border-gray-300 hover:border-ink'}`}>
                        <i className={`fa ${v.icon}`} aria-hidden="true"></i>{v.label}
                    </button>
                ))}
            </div>
            <figure className="rounded-2xl overflow-hidden border border-gray-200 bg-white">
                <svg viewBox="0 0 1000 620" className="w-full h-auto block" role="img"
                     aria-label={`${painting.title}, ${num(hIn)} by ${num(wIn)} inches, shown to scale in a ${s.label.toLowerCase()}`}>
                    <defs>
                        <linearGradient id={`${id}-wall`} x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor={s.wall[0]} /><stop offset="1" stopColor={s.wall[1]} /></linearGradient>
                        <linearGradient id={`${id}-floor`} x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor={s.floor[0]} /><stop offset="1" stopColor={s.floor[1]} /></linearGradient>
                        <filter id={`${id}-sh`} x="-20%" y="-20%" width="140%" height="160%"><feDropShadow dx="0" dy="10" stdDeviation="9" floodOpacity=".3" /></filter>
                    </defs>
                    <rect width="1000" height={FLOOR} fill={`url(#${id}-wall)`} />
                    <rect y={FLOOR} width="1000" height={620 - FLOOR} fill={`url(#${id}-floor)`} />
                    <rect y={FLOOR - 8} width="1000" height="10" fill="rgba(0,0,0,.08)" />
                    {s.furniture()}
                    <g filter={`url(#${id}-sh)`} style={t}>
                        <rect x={x} y={y} width={w} height={h} fill={painting.is_framed ? '#1f1a17' : '#fff'} style={t} />
                    </g>
                    {painting.image_url && (
                        <image href={painting.image_url} x={x + frame * U} y={y + frame * U} width={w - frame * 2 * U} height={h - frame * 2 * U}
                               preserveAspectRatio="xMidYMid slice" style={t} />
                    )}
                    <g style={t}>
                        <line x1={x} x2={x + w} y1={y - 14} y2={y - 14} stroke="#111" strokeWidth="1.5" />
                        <line x1={x} x2={x} y1={y - 20} y2={y - 8} stroke="#111" strokeWidth="1.5" />
                        <line x1={x + w} x2={x + w} y1={y - 20} y2={y - 8} stroke="#111" strokeWidth="1.5" />
                        <text x={500} y={y - 22} textAnchor="middle" fontSize="17" fontWeight="700" fill="#111">{num(wIn)}" × {num(hIn)}"</text>
                    </g>
                </svg>
                <figcaption className="px-5 py-3 text-sm text-gray-500 border-t border-gray-100 flex flex-wrap justify-between gap-2">
                    <span>Drawn to scale · {s.caption}</span>
                    <span className="font-mono">{num(painting.height_cm)} × {num(painting.width_cm)} cm</span>
                </figcaption>
            </figure>
        </div>
    );
}
