import React from 'react';
import { Price, TrustBadges, dims, dimsCm, hasDiscount, pct, useCountdown, mmss, inr } from '@/Components/Marketplace/shared';

/**
 * One painting in the marketplace grid. Every card has the same image box (4:5), so rows line up in the grid; the
 * artwork is fitted inside it, never cropped. The image is the address saved with the listing (an S3 / CDN URL, or
 * /storage/... on this site) and loads lazily, with its real proportions given up front so nothing jumps.
 */
export default function ArtCard({ p, serverNow }) {
    const held = p.stock_status === 'RESERVED';
    const sold = p.stock_status === 'SOLD';
    const left = useCountdown(held ? p.reserved_until : null, serverNow);
    const w = Number(p.width_inches) || 4;
    const h = Number(p.height_inches) || 3;
    const imgW = 800;
    const imgH = Math.round((imgW * h) / w);

    return (
        <article className={`mk-card ${sold ? 'is-sold' : ''}`}>
            <a href={`/marketplace/${p.slug}`} className="mk-card__link" aria-label={`${p.title} by ${p.artist_name}, ${sold ? 'sold' : inr(p.final_price)}`}>
                <div className="mk-card__media">
                    {p.image_url ? (
                        <img src={p.image_url} alt={`${p.title} by ${p.artist_name}`} loading="lazy" decoding="async"
                             width={imgW} height={imgH} className="mk-card__img" />
                    ) : (
                        <span className="mk-card__noimg" aria-hidden="true"><i className="fa fa-picture-o"></i></span>
                    )}

                    <div className="mk-card__tags">
                        {sold && <span className="mk-tag mk-tag--ink">Sold</span>}
                        {held && left > 0 && <span className="mk-tag mk-tag--hold"><i className="fa fa-lock" aria-hidden="true"></i> On hold · {mmss(left)}</span>}
                        {!sold && !held && hasDiscount(p) && <span className="mk-tag mk-tag--brand">-{pct(p)}</span>}
                        {p.source === 'STUDENT' && <span className="mk-tag mk-tag--student"><i className="fa fa-graduation-cap" aria-hidden="true"></i> Student painting</span>}
                    </div>

                    {/* hover details (pointer devices only) */}
                    <div className="mk-overlay">
                        <p className="mk-overlay__eyebrow">{p.medium} on {String(p.surface || '').toLowerCase()}</p>
                        <p className="mk-overlay__dims">{dims(p)} · {dimsCm(p)}{p.is_framed ? ' · framed' : ''}</p>
                        <div className="mk-overlay__trust"><TrustBadges p={p} compact /></div>
                        <span className="mk-overlay__cta">{sold ? 'See the piece' : 'View artwork'} <i className="fa fa-arrow-right" aria-hidden="true"></i></span>
                    </div>
                </div>

                <div className="mk-card__body">
                    <h3 className="mk-card__title">{p.title}</h3>
                    <p className="mk-card__artist">{p.artist_name}{p.year_created ? ` · ${p.year_created}` : ''}</p>
                    <p className="mk-card__meta">{p.medium} · {dims(p)}</p>
                    <div className="mk-card__price">
                        {sold ? <span className="mk-card__sold">Sold</span> : <Price p={p} />}
                    </div>
                </div>
            </a>
        </article>
    );
}
