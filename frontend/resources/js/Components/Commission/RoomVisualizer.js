import React from 'react';
import ThemeArt from '@/Components/Commission/ThemeArt';

/**
 * SVG room mock-up: a living-room wall with a sofa (drawn at a real-world scale of ~84 in wide).
 * The canvas box is scaled from the selected inches, so customers can judge the size at a glance.
 */
export default function RoomVisualizer({ widthIn, heightIn, theme, photoUrl }) {
    const PX_PER_IN = 6.2;                 // sofa: 84 in -> ~520 px
    const w = widthIn * PX_PER_IN;
    const h = heightIn * PX_PER_IN;
    const cx = 500;
    const sofaTop = 430;
    const artBottom = sofaTop - 36;        // hang ~6 in above the sofa back
    const x = cx - w / 2;
    const y = artBottom - h;
    const transition = { transition: 'all .45s cubic-bezier(.2,.8,.2,1)' };

    return (
        <figure className="rounded-2xl overflow-hidden border border-gray-200 bg-white shadow-sm">
            <svg viewBox="0 0 1000 620" className="w-full h-auto" role="img"
                 aria-label={`Scale preview: a ${widthIn} by ${heightIn} inch canvas above an 84 inch sofa`}>
                <defs>
                    <linearGradient id="rv-wall" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#f3efe8" /><stop offset="1" stopColor="#e8e1d6" /></linearGradient>
                    <linearGradient id="rv-floor" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#b98a5e" /><stop offset="1" stopColor="#8c6440" /></linearGradient>
                    <linearGradient id="rv-sofa" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#5b7083" /><stop offset="1" stopColor="#44566a" /></linearGradient>
                    <filter id="rv-shadow" x="-20%" y="-20%" width="140%" height="160%"><feDropShadow dx="0" dy="8" stdDeviation="8" floodOpacity=".28" /></filter>
                    <clipPath id="rv-clip"><rect x={x} y={y} width={w} height={h} style={transition} /></clipPath>
                </defs>
                <rect width="1000" height="520" fill="url(#rv-wall)" />
                <rect y="520" width="1000" height="100" fill="url(#rv-floor)" />
                <rect y="512" width="1000" height="10" fill="#d8cfc1" />

                {/* artwork */}
                <g filter="url(#rv-shadow)">
                    <rect x={x} y={y} width={w} height={h} fill="#fff" style={transition} />
                </g>
                <g clipPath="url(#rv-clip)">
                    {photoUrl ? (
                        <image href={photoUrl} x={x} y={y} width={w} height={h} preserveAspectRatio="xMidYMid slice" style={transition} />
                    ) : (
                        <foreignObject x={x} y={y} width={w} height={h} style={transition}>
                            <div xmlns="http://www.w3.org/1999/xhtml" style={{ width: '100%', height: '100%' }}><ThemeArt theme={theme || 'LANDSCAPE'} /></div>
                        </foreignObject>
                    )}
                </g>
                <rect x={x} y={y} width={w} height={h} fill="none" stroke="rgba(0,0,0,.25)" strokeWidth="1.5" style={transition} />

                {/* dimension label */}
                <g style={transition}>
                    <line x1={x} x2={x + w} y1={y - 16} y2={y - 16} stroke="#111" strokeWidth="1.5" style={transition} />
                    <line x1={x} x2={x} y1={y - 22} y2={y - 10} stroke="#111" strokeWidth="1.5" style={transition} />
                    <line x1={x + w} x2={x + w} y1={y - 22} y2={y - 10} stroke="#111" strokeWidth="1.5" style={transition} />
                    <text x={cx} y={y - 26} textAnchor="middle" fontSize="18" fontWeight="700" fill="#111" style={transition}>{widthIn}" × {heightIn}"</text>
                </g>

                {/* sofa (84 in) */}
                <g>
                    <rect x={cx - 260} y={sofaTop} width="520" height="70" rx="18" fill="url(#rv-sofa)" />
                    <rect x={cx - 285} y={sofaTop + 40} width="60" height="80" rx="16" fill="#4d6175" />
                    <rect x={cx + 225} y={sofaTop + 40} width="60" height="80" rx="16" fill="#4d6175" />
                    <rect x={cx - 230} y={sofaTop + 58} width="460" height="50" rx="12" fill="#566b80" />
                    <rect x={cx - 240} y={sofaTop + 118} width="14" height="16" fill="#3a2b1e" />
                    <rect x={cx + 226} y={sofaTop + 118} width="14" height="16" fill="#3a2b1e" />
                    <rect x={cx - 150} y={sofaTop + 22} width="80" height="46" rx="10" fill="#e9c46a" />
                    <rect x={cx + 70} y={sofaTop + 22} width="80" height="46" rx="10" fill="#e76f51" />
                </g>
                {/* plant for extra scale cue */}
                <g>
                    <rect x="840" y="450" width="56" height="70" rx="6" fill="#c8b6a6" />
                    <path d="M868 450c-30-40-40-90-10-120 8 40 22 70 10 120zm0 0c28-38 50-70 30-110-14 34-30 60-30 110zm0 0c-40-20-70-10-80 10 30 0 52-4 80-10z" fill="#4f7d5c" />
                </g>
            </svg>
            <figcaption className="px-5 py-3 text-xs text-gray-500 border-t border-gray-100">
                Shown to scale above an 84-inch sofa. Hang the canvas about 6–10 inches above the furniture.
            </figcaption>
        </figure>
    );
}
