import React from 'react';
import { Price, TrustBadges, dims, dimsCm, hasDiscount, pct, useCountdown, mmss, inr } from '@/Components/Marketplace/shared';

/** One painting in the masonry gallery. Keeps the artwork's natural aspect ratio (no cropping). */
export default function ArtCard({ p, serverNow }) {
    const held = p.stock_status === 'RESERVED';
    const sold = p.stock_status === 'SOLD';
    const left = useCountdown(held ? p.reserved_until : null, serverNow);
    const ratio = Number(p.height_inches) && Number(p.width_inches) ? Number(p.height_inches) / Number(p.width_inches) : 0.75;

    return (
        <article className="mk-card group relative rounded-2xl bg-white border border-gray-200 overflow-hidden">
            <a href={`/marketplace/${p.slug}`} className="block focus:outline-none" aria-label={`${p.title} by ${p.artist_name}, ${sold ? 'sold' : inr(p.final_price)}`}>
                <div className="relative bg-gray-50">
                    {p.image_url ? (
                        <img src={p.image_url} alt={`${p.title} by ${p.artist_name}`} loading="lazy"
                             className={`block w-full h-auto ${sold ? 'filter grayscale opacity-80' : ''}`} />
                    ) : (
                        <div style={{ paddingTop: `${ratio * 100}%` }}></div>
                    )}

                    {/* status ribbons */}
                    <div className="absolute top-3 left-3 flex flex-wrap gap-2">
                        {sold && <span className="rounded-full bg-ink text-white px-3 py-1 text-xs font-bold tracking-wide uppercase">Sold</span>}
                        {held && left > 0 && <span className="rounded-full bg-yellow-400 text-ink px-3 py-1 text-xs font-bold"><i className="fa fa-lock mr-1" aria-hidden="true"></i>On hold · {mmss(left)}</span>}
                        {!sold && !held && hasDiscount(p) && <span className="rounded-full bg-brand text-white px-3 py-1 text-xs font-bold">-{pct(p)}</span>}
                        {p.source === 'STUDENT' && <span className="rounded-full bg-white text-ink border border-gray-200 px-3 py-1 text-xs font-bold">Student original</span>}
                    </div>

                    {/* hover overlay (pointer devices) */}
                    <div className="mk-overlay absolute inset-0 flex flex-col justify-end p-5 text-white" style={{ background: 'linear-gradient(to top, rgba(0,0,0,.85) 0%, rgba(0,0,0,.35) 55%, rgba(0,0,0,0) 100%)' }}>
                        <p className="text-xs font-mono uppercase tracking-widest text-pink-200">{p.medium} on {String(p.surface || '').toLowerCase()}</p>
                        <p className="mt-1 text-sm text-gray-200">{dims(p)} · {dimsCm(p)}{p.is_framed ? ' · framed' : ''}</p>
                        <div className="mt-3"><TrustBadges p={p} compact /></div>
                        <span className="mt-4 inline-flex items-center gap-2 text-sm font-bold">{sold ? 'See the piece' : 'View artwork'} <i className="fa fa-arrow-right" aria-hidden="true"></i></span>
                    </div>
                </div>

                {/* always-visible caption */}
                <div className="p-4">
                    <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                            <h3 className="text-base font-extrabold text-ink leading-snug truncate">{p.title}</h3>
                            <p className="text-sm text-gray-500 truncate">{p.artist_name} · {p.year_created}</p>
                        </div>
                        {sold ? <span className="text-sm font-bold text-gray-400 whitespace-nowrap">Sold</span> : null}
                    </div>
                    {!sold && <Price p={p} className="mt-2" />}
                    <p className="mt-1 text-xs text-gray-500 md:hidden">{p.medium} · {dims(p)}</p>
                </div>
            </a>
        </article>
    );
}
