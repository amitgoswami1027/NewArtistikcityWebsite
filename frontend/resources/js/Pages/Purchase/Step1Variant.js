import React, { useMemo, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import { Inertia } from '@inertiajs/inertia';
import axios from 'axios';
import PurchaseLayout from '@/Components/Shop/PurchaseLayout';
import { Qty } from '@/Components/Shop/CartDrawer';
import { FORMAT_INFO, formatOf, normalise, priceOf, inr, num, errorText } from '@/Components/Shop/shop';

/** Step 1: how to buy the artwork (original, numbered canvas, poster, download), its size, finish and quantity. */
export default function Step1Variant() {
    const { painting: p, offer, draft, hold, serverNow } = usePage().props;
    const firstAvailable = (offer.formats.find((f) => f.available) || offer.formats[0]).format;
    const start = draft && draft.format && formatOf(offer, draft.format) && formatOf(offer, draft.format).available ? draft.format : firstAvailable;
    const [sel, setSel] = useState(() => normalise(offer, {
        format: start, size: draft && draft.size, finish: draft && draft.finish, frame: (draft && draft.frame) || 'NONE', qty: (draft && draft.qty) || 1,
    }));
    const [busy, setBusy] = useState(false);
    const [err, setErr] = useState(null);
    const [conflict, setConflict] = useState(null);
    const f = formatOf(offer, sel.format);
    const price = useMemo(() => priceOf(offer, sel), [offer, sel]);
    const update = (patch) => setSel((s) => normalise(offer, { ...s, ...patch }));
    const choose = (format) => {
        const fo = formatOf(offer, format);
        if (!fo || !fo.available) return;
        setErr(null);
        setSel((s) => normalise(offer, { ...s, format, size: s.format === format ? s.size : '', finish: '', qty: 1 }));
    };

    const next = (switchHold = false) => {
        setBusy(true); setErr(null);
        axios.post('/api/v1/purchase/draft', { paintingId: p.id, ...sel, switch: switchHold })
            .then(() => Inertia.visit(sel.format === 'DIGITAL' ? '/purchase/preview' : '/purchase/step-2'))
            .catch((e) => {
                setBusy(false);
                const d = e.response && e.response.data;
                if (d && d.code === 'HOLD_LIMIT') setConflict(d);
                else setErr(errorText(e));
            });
    };

    const edition = offer.edition || {};
    return (
        <PurchaseLayout painting={p} step={1} title="Choose how you'd like it" hold={hold} serverNow={serverNow} skipRoom={sel.format === 'DIGITAL'}>
            <div className="pz-split">
                <aside className="pz-split__aside">
                    <figure className="pz-artcard">
                        <div className="pz-artcard__img">{p.image_url && <img src={p.image_url} alt={p.title} />}</div>
                        <figcaption>
                            <b>{p.title}</b>
                            <span>{p.artist_name} · {p.year_created}</span>
                            <span>{p.medium} on {String(p.surface).toLowerCase()} · {num(p.height_inches)} × {num(p.width_inches)} in</span>
                        </figcaption>
                    </figure>
                </aside>

                <div className="pz-split__main">
                    <fieldset className="pz-formats">
                        <legend className="pz-legend">Format</legend>
                        <div className="pz-formats__grid">
                            {offer.formats.map((fo) => {
                                const info = FORMAT_INFO[fo.format];
                                const on = sel.format === fo.format;
                                return (
                                    <label key={fo.format} className={`pz-format ${on ? 'is-on' : ''} ${fo.available ? '' : 'is-off'}`}>
                                        <input type="radio" name="format" value={fo.format} checked={on} disabled={!fo.available} onChange={() => choose(fo.format)} />
                                        <span className="pz-format__icon" aria-hidden="true"><i className={`fa ${info.icon}`}></i></span>
                                        <span className="pz-format__text">
                                            <span className="pz-format__name">{info.name} <em>{info.tag}</em></span>
                                            <span className="pz-format__blurb">{fo.available ? info.blurb : fo.reason}</span>
                                        </span>
                                        <span className="pz-format__price">{fo.from && fo.available ? <>{fo.format === 'ORIGINAL' ? '' : <small>from</small>} {inr(fo.from)}</> : '—'}</span>
                                    </label>
                                );
                            })}
                        </div>
                    </fieldset>

                    {f && f.available && (
                        <div className="pz-options">
                            {sel.format === 'ORIGINAL' && (
                                <div className="pz-option">
                                    <p className="pz-legend">What you get</p>
                                    <ul className="pz-ticks">
                                        <li>The one-of-one original, {num(p.height_inches)} × {num(p.width_inches)} in</li>
                                        {p.is_signed && <li>Hand-signed by {p.artist_name}</li>}
                                        {p.has_certificate && <li>Certificate of authenticity</li>}
                                        <li>Insured, tracked shipping included</li>
                                    </ul>
                                    {!hold && <p className="pz-fine"><i className="fa fa-lock" aria-hidden="true"></i> Continuing holds it only for you for 15 minutes. No payment is taken yet.</p>}
                                </div>
                            )}

                            {sel.format !== 'ORIGINAL' && sel.format !== 'DIGITAL' && (
                                <fieldset className="pz-option">
                                    <legend className="pz-legend">Size</legend>
                                    <div className="pz-sizes">
                                        {f.sizes.map((s) => (
                                            <label key={s.code} className={`pz-size ${sel.size === s.code ? 'is-on' : ''}`}>
                                                <input type="radio" name="size" value={s.code} checked={sel.size === s.code} onChange={() => update({ size: s.code })} />
                                                <b>{s.label}</b>
                                                <span>{num(s.width)} × {num(s.height)} in</span>
                                                <em>{inr(s.price)}</em>
                                            </label>
                                        ))}
                                    </div>
                                </fieldset>
                            )}

                            {f.finishes.length > 0 && (
                                <fieldset className="pz-option">
                                    <legend className="pz-legend">{sel.format === 'CANVAS' ? 'Finish' : 'Paper'}</legend>
                                    <div className="pz-seg" role="radiogroup">
                                        {f.finishes.map((x) => {
                                            const extra = sel.format === 'CANVAS' && x.code === 'STRETCHED' && price && price.size ? Number(price.size.extra) : 0;
                                            const locked = sel.format === 'CANVAS' && x.code === 'ROLLED' && sel.frame !== 'NONE';
                                            return (
                                                <label key={x.code} className={`${sel.finish === x.code ? 'is-on' : ''} ${locked ? 'is-off' : ''}`} title={locked ? 'Framed canvases come stretched' : undefined}>
                                                    <input type="radio" name="finish" value={x.code} checked={sel.finish === x.code} disabled={locked} onChange={() => update({ finish: x.code })} />
                                                    {x.label}{extra > 0 && <small> +{inr(extra)}</small>}
                                                </label>
                                            );
                                        })}
                                    </div>
                                </fieldset>
                            )}

                            {sel.format === 'CANVAS' && edition.size > 0 && (
                                <p className="pz-edition"><i className="fa fa-hashtag" aria-hidden="true"></i> Limited edition of {edition.size} · <b>{edition.left} left</b>. Each canvas is numbered and signed on the back.</p>
                            )}
                            {sel.format === 'DIGITAL' && (
                                <ul className="pz-ticks">
                                    <li>Full-resolution file, ready to print up to 24 × 36 in</li>
                                    <li>Download link by e-mail, valid for 30 days</li>
                                    <li>Personal use: print it for your home or set it as wallpaper. Not for resale.</li>
                                </ul>
                            )}

                            {f.maxQty > 1 && (
                                <div className="pz-option pz-option--row">
                                    <span className="pz-legend">Quantity</span>
                                    <Qty value={sel.qty} max={f.maxQty} onChange={(q) => update({ qty: q })} />
                                </div>
                            )}
                        </div>
                    )}

                    <div className="pz-pricebar">
                        <div>
                            <p className="pz-pricebar__label">{FORMAT_INFO[sel.format].name}{price && price.width && sel.format !== 'ORIGINAL' ? ` · ${num(price.width)} × ${num(price.height)} in` : ''}{sel.qty > 1 ? ` · ${sel.qty} pcs` : ''}</p>
                            <p className="pz-pricebar__price">{price ? inr(price.total) : '—'}</p>
                            {sel.format !== 'DIGITAL' && <p className="pz-fine">You can add a frame on the next step.</p>}
                        </div>
                        <button type="button" className="pz-btn pz-btn--brand pz-btn--lg" onClick={() => next(false)} disabled={busy || !f || !f.available || !price}>
                            {busy && <i className="fa fa-circle-o-notch fa-spin" aria-hidden="true"></i>}
                            {sel.format === 'DIGITAL' ? 'Next: Review' : 'Next: See it on your wall'} <i className="fa fa-arrow-right" aria-hidden="true"></i>
                        </button>
                    </div>
                    {err && <p className="pz-error" role="alert">{err}</p>}
                    {conflict && (
                        <div className="pz-conflict" role="alertdialog" aria-label="You're already holding an original">
                            <p><b>You're already holding “{conflict.heldTitle}”.</b> You can hold one original at a time. Switch your hold to this piece?</p>
                            <div>
                                <button type="button" className="pz-btn pz-btn--ink" onClick={() => { setConflict(null); next(true); }}>Switch to this piece</button>
                                <a href="/purchase/checkout" className="pz-btn pz-btn--line">Go to my cart</a>
                            </div>
                        </div>
                    )}
                </div>
            </div>
        </PurchaseLayout>
    );
}
