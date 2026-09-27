import React, { useEffect, useRef, useState } from 'react';

/* ------------------------------------------------------------------ formatting */

export const inr = (n) => '₹' + Number(n || 0).toLocaleString('en-IN', { maximumFractionDigits: 0 });
export const usd = (n) => '$' + Number(n || 0).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
export const num = (n, d = 1) => {
    const v = Number(n);
    if (!isFinite(v)) return '';
    return Number.isInteger(v) ? String(v) : v.toFixed(d).replace(/\.0+$/, '');
};
export const dims = (p) => `${num(p.height_inches)} × ${num(p.width_inches)} in`;
export const dimsCm = (p) => `${num(p.height_cm)} × ${num(p.width_cm)} cm`;
export const errorText = (e, fallback = 'Something went wrong. Please try again.') =>
    (e && e.response && e.response.data && e.response.data.message) || fallback;
export const hasDiscount = (p) => Number(p.discount_percentage) > 0;
export const pct = (p) => `${num(p.discount_percentage, 0)}%`;

/** The one client-side mirror of ListingInput.finalPrice (the server recalculates; this is for live preview only). */
export const finalPrice = (base, discount) => {
    const b = Number(base) || 0;
    const d = Math.min(90, Math.max(0, Number(discount) || 0));
    return Math.round(b * (100 - d)) / 100;
};

export const STATUS = {
    AVAILABLE: { label: 'Available', chip: 'bg-green-50 text-green-800 border-green-200', dot: 'bg-green-500' },
    RESERVED: { label: 'On hold', chip: 'bg-yellow-50 text-yellow-800 border-yellow-200', dot: 'bg-yellow-500' },
    SOLD: { label: 'Sold', chip: 'bg-gray-900 text-white border-gray-900', dot: 'bg-gray-900' },
    PENDING_REVIEW: { label: 'In review', chip: 'bg-blue-50 text-blue-800 border-blue-200', dot: 'bg-blue-500' },
    DRAFT: { label: 'Draft', chip: 'bg-gray-100 text-gray-700 border-gray-200', dot: 'bg-gray-400' },
    ARCHIVED: { label: 'Archived', chip: 'bg-gray-100 text-gray-500 border-gray-200', dot: 'bg-gray-300' },
};

export function StatusChip({ status, className = '' }) {
    const s = STATUS[status] || STATUS.DRAFT;
    return (
        <span className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-bold ${s.chip} ${className}`}>
            <span className={`w-1.5 h-1.5 rounded-full ${s.dot}`} aria-hidden="true"></span>{s.label}
        </span>
    );
}

/* ------------------------------------------------------------------ countdown */

/**
 * Seconds left until `expiresAt` (epoch ms), corrected for the difference between the browser clock
 * and the server clock (`serverNow` sent with the page), ticking every second.
 */
export function useCountdown(expiresAt, serverNow) {
    const skew = useRef(serverNow ? serverNow - Date.now() : 0);
    useEffect(() => { if (serverNow) skew.current = serverNow - Date.now(); }, [serverNow]);
    const calc = () => (expiresAt ? Math.max(0, Math.round((expiresAt - (Date.now() + skew.current)) / 1000)) : 0);
    const [left, setLeft] = useState(calc);
    useEffect(() => {
        setLeft(calc());
        if (!expiresAt) return undefined;
        const t = setInterval(() => setLeft(calc()), 1000);
        return () => clearInterval(t);
    }, [expiresAt]);
    return left;
}

export const mmss = (s) => `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;

/* ------------------------------------------------------------------ pricing */

export function Price({ p, size = 'md', className = '' }) {
    const big = size === 'lg';
    return (
        <div className={`flex flex-wrap items-baseline gap-x-3 gap-y-1 ${className}`}>
            <span className={`font-black tracking-tight text-ink ${big ? 'text-4xl' : 'text-xl'}`}>{inr(p.final_price)}</span>
            {hasDiscount(p) && (
                <>
                    <span className={`text-gray-400 line-through ${big ? 'text-xl' : 'text-sm'}`}>{inr(p.base_price)}</span>
                    <span className={`rounded-full bg-brand text-white font-bold ${big ? 'px-3 py-1 text-sm' : 'px-2 py-0.5 text-xs'}`}>-{pct(p)} off</span>
                </>
            )}
        </div>
    );
}

export function TrustBadges({ p, compact = false }) {
    const items = [
        ['fa-diamond', '1-of-1 original'],
        p.has_certificate && ['fa-certificate', compact ? 'COA included' : 'Certificate of authenticity'],
        p.is_signed && ['fa-pencil', 'Signed by the artist'],
    ].filter(Boolean);
    return (
        <ul className="flex flex-wrap gap-2">
            {items.map(([icon, label]) => (
                <li key={label} className="inline-flex items-center gap-1.5 rounded-full bg-white bg-opacity-90 border border-gray-200 px-2.5 py-1 text-xs font-bold text-ink">
                    <i className={`fa ${icon} text-brand`} aria-hidden="true"></i>{label}
                </li>
            ))}
        </ul>
    );
}

/* ------------------------------------------------------------------ "held for you" banner */

/** Site-wide reminder of the visitor's active 15-minute hold (shown on marketplace pages). */
export function HoldBanner({ holds = [], serverNow, exceptSlug }) {
    const h = holds.find((x) => x.slug !== exceptSlug);
    const left = useCountdown(h ? h.expiresAt : null, serverNow);
    if (!h || left <= 0) return null;
    return (
        <div className="fixed bottom-4 inset-x-4 z-40 flex justify-center pointer-events-none" role="status" aria-live="polite">
            <div className="pointer-events-auto flex items-center gap-4 rounded-2xl bg-ink text-white shadow-2xl pl-3 pr-4 py-3 max-w-xl w-full">
                {h.imageUrl && <img src={h.imageUrl} alt="" className="w-12 h-12 rounded-lg object-cover flex-none" />}
                <div className="flex-1 min-w-0">
                    <p className="text-sm font-bold truncate"><i className="fa fa-lock mr-1.5 text-pink-300" aria-hidden="true"></i>Held for you: {h.title}</p>
                    <p className="text-xs text-gray-300">Released in <span className="font-mono font-bold text-white">{mmss(left)}</span></p>
                </div>
                <a href={`/marketplace/${h.slug}/checkout`} className="flex-none inline-flex items-center h-10 px-4 rounded-full bg-brand text-white text-sm font-bold hover:bg-brand-dark">Checkout</a>
            </div>
        </div>
    );
}

/* ------------------------------------------------------------------ misc */

export function Spinner({ className = '' }) {
    return <i className={`fa fa-circle-o-notch fa-spin ${className}`} aria-hidden="true"></i>;
}

export const MEDIUMS = ['Oil', 'Acrylic', 'Watercolour', 'Gouache', 'Graphite', 'Charcoal', 'Soft pastel', 'Oil pastel', 'Ink', 'Coloured pencil', 'Mixed media'];
export const SURFACES = ['Stretched cotton canvas', 'Gallery-wrapped cotton canvas', 'Linen canvas', 'Canvas board', 'Cold-press 300gsm paper', 'Archival 300gsm acid-free paper', 'Smooth bristol board', 'Toned drawing paper', 'Wood panel'];
export const SUBJECTS = ['Landscape', 'Portrait', 'Wildlife', 'Still life', 'Botanical', 'Abstract', 'Urban', 'Figurative', 'Seascape'];
