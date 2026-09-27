import React from 'react';

/** Small illustrative placeholders showing the feel of each art style (pure SVG, no image files). */
export default function ThemeArt({ theme, className = '' }) {
    const common = { viewBox: '0 0 320 200', className: `w-full h-full ${className}`, role: 'img', preserveAspectRatio: 'xMidYMid slice' };
    if (theme === 'PORTRAIT') {
        return (
            <svg {...common} aria-label="Classic portrait style example">
                <defs>
                    <radialGradient id="pg" cx="50%" cy="40%" r="70%"><stop offset="0" stopColor="#6b4a2f" /><stop offset="1" stopColor="#1f140c" /></radialGradient>
                    <filter id="pb"><feTurbulence type="fractalNoise" baseFrequency="0.9" numOctaves="2" /><feColorMatrix values="0 0 0 0 0.5  0 0 0 0 0.4  0 0 0 0 0.3  0 0 0 0.25 0" /><feBlend in2="SourceGraphic" mode="multiply" /></filter>
                </defs>
                <rect width="320" height="200" fill="url(#pg)" />
                <g filter="url(#pb)">
                    <ellipse cx="160" cy="92" rx="38" ry="46" fill="#d9a47c" />
                    <path d="M122 80c4-34 72-40 78 2-8-18-60-22-78-2z" fill="#3b2415" />
                    <path d="M92 200c6-44 34-62 68-62s62 18 68 62z" fill="#7a2c2c" />
                    <ellipse cx="146" cy="92" rx="4" ry="3" fill="#2b1a10" /><ellipse cx="174" cy="92" rx="4" ry="3" fill="#2b1a10" />
                    <path d="M150 116c6 5 14 5 20 0" stroke="#8a4b3a" strokeWidth="3" fill="none" strokeLinecap="round" />
                </g>
            </svg>
        );
    }
    if (theme === 'LANDSCAPE') {
        return (
            <svg {...common} aria-label="Scenic landscape style example">
                <defs><linearGradient id="ls" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#f7b267" /><stop offset=".55" stopColor="#f79d65" /><stop offset="1" stopColor="#4a6fa5" /></linearGradient></defs>
                <rect width="320" height="200" fill="url(#ls)" />
                <circle cx="230" cy="70" r="24" fill="#fff3c4" />
                <path d="M0 130l60-50 50 36 60-60 70 62 40-26 40 30v78H0z" fill="#3d5a80" />
                <path d="M0 150l80-30 70 26 80-34 90 36v52H0z" fill="#2a9d8f" />
                <path d="M0 176c60-14 120-14 180-4s100 8 140-4v32H0z" fill="#264653" />
            </svg>
        );
    }
    if (theme === 'WATERCOLOR') {
        return (
            <svg {...common} aria-label="Watercolor style example">
                <defs>
                    <filter id="wc"><feTurbulence type="fractalNoise" baseFrequency="0.02" numOctaves="3" result="n" /><feDisplacementMap in="SourceGraphic" in2="n" scale="18" /><feGaussianBlur stdDeviation="1.4" /></filter>
                    <filter id="wp"><feTurbulence type="fractalNoise" baseFrequency="0.8" numOctaves="2" /><feColorMatrix values="0 0 0 0 0.95  0 0 0 0 0.93  0 0 0 0 0.88  0 0 0 0.35 0" /></filter>
                </defs>
                <rect width="320" height="200" fill="#fbf8f1" />
                <g filter="url(#wc)" opacity=".85">
                    <circle cx="110" cy="90" r="52" fill="#f28ab2" />
                    <circle cx="170" cy="110" r="48" fill="#8ecae6" />
                    <circle cx="220" cy="80" r="40" fill="#ffd166" />
                    <path d="M40 170c60-20 180-24 250-4" stroke="#90be6d" strokeWidth="18" fill="none" />
                </g>
                <rect width="320" height="200" filter="url(#wp)" />
            </svg>
        );
    }
    return (
        <svg {...common} aria-label="Charcoal sketch style example">
            <defs><filter id="ch"><feTurbulence type="fractalNoise" baseFrequency="1.2" numOctaves="2" /><feColorMatrix values="0 0 0 0 0.9  0 0 0 0 0.9  0 0 0 0 0.88  0 0 0 0.5 0" /></filter></defs>
            <rect width="320" height="200" fill="#efece6" />
            <g stroke="#1b1b1b" fill="none" strokeLinecap="round">
                <path d="M70 160c20-70 60-110 110-112s80 40 84 112" strokeWidth="5" />
                <path d="M100 150c16-50 40-78 78-80" strokeWidth="2" opacity=".7" />
                <path d="M120 72c10-22 30-34 56-30" strokeWidth="3" />
                {Array.from({ length: 16 }).map((_, i) => <path key={i} d={`M${170 + i * 5} ${60 + i * 5}l22 -18`} strokeWidth="1.5" opacity=".55" />)}
                <circle cx="176" cy="96" r="10" fill="#1b1b1b" />
            </g>
            <rect width="320" height="200" filter="url(#ch)" />
        </svg>
    );
}
