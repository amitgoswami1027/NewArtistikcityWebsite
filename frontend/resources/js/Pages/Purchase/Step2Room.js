import React, { useEffect, useMemo, useRef, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import { Inertia } from '@inertiajs/inertia';
import axios from 'axios';
import PurchaseLayout from '@/Components/Shop/PurchaseLayout';
import ArtPreview, { FRAMES } from '@/Components/Shop/ArtPreview';
import { TalkToArtist } from '@/Components/Shop/BuyPanel';
import { Qty } from '@/Components/Shop/CartDrawer';
import { useCart } from '@/Components/Shop/CartStore';
import { useConversation } from '@/Components/Shop/ArtistHub';
import OfferDialog, { OfferButton, useOfferTerms } from '@/Components/Shop/OfferDialog';
import { FORMAT_INFO, FRAME_INFO, formatOf, normalise, priceOf, frameCost, variantLine, customSide, customSize, inr, num, errorText } from '@/Components/Shop/shop';

const FRAME_NOTES = {
    NONE: 'Hangs as it comes',
    BLACK_WOOD: 'Matte black ash',
    OAK: 'Solid natural oak',
    WHITE_FLOAT: 'Floats in a white box',
};

/** The three parts of this screen, each with its own address (#size, #frame, #wall) so Back and refresh keep the place. */
const PARTS = [
    { id: 'size', letter: 'A', label: 'Size' },
    { id: 'frame', letter: 'B', label: 'Frame' },
    { id: 'wall', letter: 'C', label: 'See it on your wall' },
];

const cm = (inches) => Math.round(Number(inches) * 2.54);

function partFromHash() {
    try {
        const h = window.location.hash.replace('#', '');
        const i = PARTS.findIndex((p) => p.id === h);
        return i >= 0 ? i : 0;
    } catch (e) { return 0; }
}

/** "Custom size": long side by slider or typed number, in inches or centimetres; the other side follows the artwork. */
function CustomSize({ painting, f, sel, onPick }) {
    const side = customSide(sel.size);
    const [unit, setUnit] = useState(() => { try { return window.localStorage.getItem('artistikcity.unit') === 'cm' ? 'cm' : 'in'; } catch (e) { return 'in'; } });
    const value = side || Math.round((f.custom.min + f.custom.max) / 3);
    const [typed, setTyped] = useState(null);
    const shown = typed !== null ? typed : String(unit === 'cm' ? cm(value) : value);
    const setUnitKeep = (u) => { setUnit(u); setTyped(null); try { window.localStorage.setItem('artistikcity.unit', u); } catch (e) { /* ignore */ } };
    const commit = (raw) => {
        const n = Number(raw);
        if (!isFinite(n) || n <= 0) { setTyped(null); return; }
        const inches = Math.round(unit === 'cm' ? n / 2.54 : n);
        onPick(`C${Math.max(f.custom.min, Math.min(f.custom.max, inches))}`);
        setTyped(null);
    };
    const s = customSize(painting, f, value);
    const lo = unit === 'cm' ? cm(f.custom.min) : f.custom.min;
    const hi = unit === 'cm' ? cm(f.custom.max) : f.custom.max;
    return (
        <div className="ap-custom" data-testid="custom-size">
            <div className="ap-custom__row">
                <label htmlFor="ap-custom-range" className="ap-custom__label">Long side</label>
                <span className="ap-unit" role="group" aria-label="Units">
                    <button type="button" aria-pressed={unit === 'in'} className={unit === 'in' ? 'is-on' : ''} onClick={() => setUnitKeep('in')}>in</button>
                    <button type="button" aria-pressed={unit === 'cm'} className={unit === 'cm' ? 'is-on' : ''} onClick={() => setUnitKeep('cm')}>cm</button>
                </span>
            </div>
            <div className="ap-custom__row">
                <input id="ap-custom-range" type="range" min={f.custom.min} max={f.custom.max} step="1" value={value}
                       onChange={(e) => onPick(`C${e.target.value}`)} aria-valuetext={`${value} inches`} data-testid="custom-range" />
                <span className="ap-custom__num">
                    <input type="number" inputMode="numeric" min={lo} max={hi} value={shown} aria-label={`Long side in ${unit === 'cm' ? 'centimetres' : 'inches'}`}
                           onChange={(e) => setTyped(e.target.value)} onBlur={(e) => commit(e.target.value)}
                           onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); commit(e.currentTarget.value); } }} data-testid="custom-input" />
                    <small>{unit}</small>
                </span>
            </div>
            {s && (
                <p className="ap-custom__out" aria-live="polite">
                    <b>{num(s.width)} × {num(s.height)} in</b> ({cm(s.width)} × {cm(s.height)} cm) · {inr(s.price + (f.format === 'CANVAS' && sel.finish !== 'ROLLED' ? s.extra : 0))}
                </p>
            )}
            <p className="ap-fine">Any long side from {lo} to {hi} {unit}; the other side keeps the artwork's proportions.</p>
        </div>
    );
}

/**
 * Screen 2: see the piece before paying, in three parts with Back / Next: A size (presets or a custom size for prints;
 * an original's real size), B frame, C see it on your wall (swipe through the backgrounds). The picture on the left follows
 * every choice at true scale. "Buy now" and "Add to cart" are always there for anyone who has already decided.
 */
export default function Step2Room() {
    const { painting: p, offer, draft, hold, serverNow, contact, conversation: conv0, customer, rooms = [], roomsEnabled = true } = usePage().props;
    const conversation = useConversation(p.id, conv0);
    const cart = useCart();
    const [sel, setSel] = useState(() => normalise(offer, { ...draft }));
    const [part, setPart] = useState(partFromHash);
    const [busy, setBusy] = useState(false);
    const [err, setErr] = useState(null);
    const [note, setNote] = useState(null);
    const [talk, setTalk] = useState(false);
    const [offerOpen, setOfferOpen] = useState(false);
    const [terms, reloadTerms] = useOfferTerms(p.id, sel.format === 'ORIGINAL');
    const panel = useRef(null);
    const f = formatOf(offer, sel.format);
    const price = useMemo(() => priceOf(offer, sel, p), [offer, sel, p]);
    const update = (patch) => { setNote(null); setSel((s) => normalise(offer, { ...s, ...patch })); };
    const surface = sel.format === 'POSTER' || (sel.format === 'ORIGINAL' && !/canvas|panel|linen/i.test(p.surface || '')) ? 'paper' : 'canvas';
    const custom = customSide(sel.size) !== null;

    // keep the server's draft in step with the choices (a refresh or the Back button comes back to them)
    const saved = useRef(JSON.stringify(sel));
    useEffect(() => {
        const now = JSON.stringify(sel);
        if (now === saved.current) return undefined;
        const t = setTimeout(() => {
            axios.post('/api/v1/purchase/draft', { paintingId: p.id, format: sel.format, size: sel.size, finish: sel.finish, frame: sel.frame, qty: sel.qty })
                .then(() => { saved.current = now; }).catch(() => { /* shown when buying */ });
        }, 500);
        return () => clearTimeout(t);
    }, [sel]); // eslint-disable-line react-hooks/exhaustive-deps

    // A / B / C live in the address bar: Back goes to the previous part, a shared link opens the same part
    useEffect(() => {
        const onPop = () => setPart(partFromHash());
        window.addEventListener('popstate', onPop);
        return () => window.removeEventListener('popstate', onPop);
    }, []);
    const go = (i) => {
        const n = Math.max(0, Math.min(PARTS.length - 1, i));
        setPart(n);
        try { window.history.pushState(window.history.state, '', `${window.location.pathname}${window.location.search}#${PARTS[n].id}`); } catch (e) { /* old browser */ }
        setTimeout(() => {
            const h = panel.current && panel.current.querySelector('.ap-part h3');
            if (h) h.focus({ preventScroll: true });
            if (panel.current && window.innerWidth < 900) panel.current.scrollIntoView({ block: 'start', behavior: 'smooth' });
        }, 30);
    };

    const save = () => axios.post('/api/v1/purchase/draft', { paintingId: p.id, format: sel.format, size: sel.size, finish: sel.finish, frame: sel.frame, qty: sel.qty });
    const buy = () => {
        setBusy(true); setErr(null);
        save().then(() => Inertia.visit('/purchase/buy-now'))
            .catch((e) => {
                const d = e.response && e.response.data;
                if (d && d.code === 'SIGN_IN' && d.redirect) { window.location.assign(d.redirect); return; }
                setBusy(false); setErr(errorText(e));
            });
    };
    const addToCart = () => {
        setBusy(true); setErr(null); setNote(null);
        cart.add({ paintingId: p.id, format: sel.format, size: sel.size, finish: sel.finish, frame: sel.frame, qty: sel.qty })
            .then(() => setNote(sel.format === 'ORIGINAL' ? 'In your cart. The original stays held for you.' : 'Added to your cart.'))
            .catch((e) => {
                const d = e.response && e.response.data;
                if (d && d.code === 'SIGN_IN' && d.redirect) { window.location.assign(d.redirect); return; }
                cart.clearError(); setErr(errorText(e));
            })
            .finally(() => setBusy(false));
    };

    if (!f || !price) return null;
    const facts = [
        sel.format === 'ORIGINAL' && p.is_signed && 'Signed by the artist',
        sel.format === 'ORIGINAL' && p.has_certificate && 'Certificate of authenticity',
        sel.format === 'ORIGINAL' ? 'The one and only original' : sel.format === 'CANVAS' && offer.edition && offer.edition.size ? `Numbered edition of ${offer.edition.size}` : null,
        'Insured, tracked delivery',
    ].filter(Boolean);
    const shownRooms = roomsEnabled ? rooms : [];
    const current = PARTS[part];

    return (
        <PurchaseLayout painting={p} step={2} title="Preview and frame" hold={hold} serverNow={serverNow}>
            <div className="ap-page">
                <div className="ap-page__stage">
                    <ArtPreview rooms={shownRooms} image={p.image_url} title={p.title} widthIn={price.width} heightIn={price.height}
                                frame={sel.frame} surface={surface} showRoom={current.id === 'wall'} />
                </div>

                <aside className="ap-page__panel" aria-label="Your choice" ref={panel}>
                    <header className="ap-head">
                        <h2>{p.title}</h2>
                        <p>by {p.artist_name}{p.year_created ? `, ${p.year_created}` : ''}</p>
                        <p className="ap-head__format">
                            {FORMAT_INFO[sel.format].name}{p.medium ? ` · ${p.medium}` : ''}
                            <a href={`/marketplace/${p.slug}#buy`} className="ap-link">Change</a>
                        </p>
                    </header>

                    <div className="ap-price">
                        <b data-testid="room-price">{inr(price.total)}</b>
                        <span>{variantLine(sel, price)}</span>
                    </div>

                    <ol className="ap-parts" aria-label="Steps on this page">
                        {PARTS.map((x, i) => (
                            <li key={x.id} className={`${i === part ? 'is-on' : ''} ${i < part ? 'is-done' : ''}`}>
                                <button type="button" onClick={() => go(i)} aria-current={i === part ? 'step' : undefined} data-part={x.id}>
                                    <span className="ap-parts__dot" aria-hidden="true">{i < part ? <i className="fa fa-check"></i> : x.letter}</span>
                                    <span className="ap-parts__label">{x.label}</span>
                                </button>
                            </li>
                        ))}
                    </ol>

                    <section className="ap-part" aria-labelledby="ap-part-title" data-part={current.id}>
                        <h3 id="ap-part-title" tabIndex={-1}>{current.letter}. {current.label}</h3>

                        {current.id === 'size' && (sel.format === 'ORIGINAL' ? (
                            <div className="ap-opt">
                                <p className="ap-static">Original size: <b>{num(price.width)} × {num(price.height)} in</b> ({cm(price.width)} × {cm(price.height)} cm).</p>
                                <p className="ap-fine">An original can't be resized. Want it bigger or smaller? A canvas or poster print can be made in any size.</p>
                            </div>
                        ) : (
                            <>
                                <fieldset className="ap-opt">
                                    <legend className="ap-opt__label">Size</legend>
                                    <div className="ap-sizes">
                                        {f.sizes.map((s) => (
                                            <button key={s.code} type="button" aria-pressed={sel.size === s.code} className={`ap-size ${sel.size === s.code ? 'is-on' : ''}`} onClick={() => update({ size: s.code })}>
                                                <span className="ap-size__shape" aria-hidden="true" style={{ aspectRatio: `${s.width} / ${s.height}` }}></span>
                                                <span>{num(s.width)} × {num(s.height)}″</span>
                                                <small>{cm(s.width)} × {cm(s.height)} cm · {inr(Number(s.price) + (sel.format === 'CANVAS' && sel.finish !== 'ROLLED' ? Number(s.extra || 0) : 0))}</small>
                                            </button>
                                        ))}
                                        {f.custom && (
                                            <button type="button" aria-pressed={custom} className={`ap-size ap-size--custom ${custom ? 'is-on' : ''}`} data-testid="size-custom"
                                                    onClick={() => { if (!custom) update({ size: `C${Math.round(Number(price.width) >= Number(price.height) ? price.width : price.height)}` }); }}>
                                                <span className="ap-size__shape ap-size__shape--custom" aria-hidden="true"><i className="fa fa-arrows-alt"></i></span>
                                                <span>Custom size</span>
                                                <small>{f.custom.min}–{f.custom.max} in</small>
                                            </button>
                                        )}
                                    </div>
                                </fieldset>
                                {custom && f.custom && <CustomSize painting={p} f={f} sel={sel} onPick={(code) => update({ size: code })} />}
                                {f.finishes.length > 0 && (
                                    <fieldset className="ap-opt">
                                        <legend className="ap-opt__label">{sel.format === 'CANVAS' ? 'Finish' : 'Paper'}</legend>
                                        <div className="ap-seg" role="radiogroup">
                                            {f.finishes.map((x) => (
                                                <label key={x.code} className={sel.finish === x.code ? 'is-on' : ''}>
                                                    <input type="radio" name="finish" value={x.code} checked={sel.finish === x.code} onChange={() => update({ finish: x.code })} />
                                                    {x.label}
                                                </label>
                                            ))}
                                        </div>
                                    </fieldset>
                                )}
                                {f.maxQty > 1 && (
                                    <div className="ap-opt ap-opt--row">
                                        <span className="ap-opt__label">Quantity</span>
                                        <Qty value={sel.qty} max={f.maxQty} onChange={(q) => update({ qty: q })} />
                                    </div>
                                )}
                            </>
                        ))}

                        {current.id === 'frame' && (
                            <fieldset className="ap-opt">
                                <legend className="sr-only">Frame</legend>
                                {f.frames.length <= 1 ? (
                                    <p className="ap-static">{p.is_framed ? 'This original arrives framed, ready to hang.' : 'Framing is not offered for this format.'}</p>
                                ) : (
                                    <div className="ap-frames">
                                        {f.frames.map((code) => {
                                            const extra = code === 'NONE' ? 0 : frameCost(offer, sel, code, p);
                                            return (
                                                <label key={code} className={`ap-frame ${sel.frame === code ? 'is-on' : ''}`} data-frame={code}>
                                                    <input type="radio" name="frame" value={code} checked={sel.frame === code} onChange={() => update({ frame: code })} />
                                                    <span className={`ap-frame__sw ap-frame__sw--${code.toLowerCase()}`} aria-hidden="true"><span></span></span>
                                                    <span className="ap-frame__text">
                                                        <b>{FRAME_INFO[code].name}</b>
                                                        <small>{FRAME_NOTES[code]}{FRAMES[code] && FRAMES[code].t ? `, ${num(FRAMES[code].t)}″` : ''}</small>
                                                    </span>
                                                    <span className="ap-frame__price">{code === 'NONE' ? 'Included' : `+${inr(extra)}`}</span>
                                                </label>
                                            );
                                        })}
                                    </div>
                                )}
                            </fieldset>
                        )}

                        {current.id === 'wall' && (
                            <div className="ap-opt">
                                <p className="ap-static">{shownRooms.length
                                    ? `Swipe the picture or use its arrows to see your choice in ${shownRooms.length === 1 ? 'the room' : `each of the ${shownRooms.length} rooms`}, drawn to scale where the wall is measured.`
                                    : 'The picture shows your choice at its real proportions, with its size marked.'}</p>
                                <dl className="ap-summary" data-testid="wall-summary">
                                    <div><dt>Format</dt><dd>{FORMAT_INFO[sel.format].name}</dd></div>
                                    <div><dt>Size</dt><dd>{num(price.width)} × {num(price.height)} in{custom ? ' (custom)' : ''} · {cm(price.width)} × {cm(price.height)} cm</dd></div>
                                    {sel.finish && <div><dt>{sel.format === 'CANVAS' ? 'Finish' : 'Paper'}</dt><dd>{(f.finishes.find((x) => x.code === sel.finish) || {}).label}</dd></div>}
                                    <div><dt>Frame</dt><dd>{FRAME_INFO[sel.frame] ? FRAME_INFO[sel.frame].name : 'No frame'}</dd></div>
                                    {sel.qty > 1 && <div><dt>Quantity</dt><dd>{sel.qty}</dd></div>}
                                    <div className="is-total"><dt>Price</dt><dd>{inr(price.total)}</dd></div>
                                </dl>
                            </div>
                        )}

                        <div className="ap-partnav">
                            {part > 0 ? <button type="button" className="pz-btn pz-btn--line" onClick={() => go(part - 1)} data-testid="part-back"><i className="fa fa-arrow-left" aria-hidden="true"></i> Back</button> : <span></span>}
                            {part < PARTS.length - 1 && (
                                <button type="button" className="pz-btn pz-btn--ink" onClick={() => go(part + 1)} data-testid="part-next">
                                    Next: {PARTS[part + 1].label} <i className="fa fa-arrow-right" aria-hidden="true"></i></button>
                            )}
                        </div>
                    </section>

                    {err && <p className="pz-error" role="alert">{err}</p>}
                    {note && <p className="pz-added" role="status"><i className="fa fa-check" aria-hidden="true"></i> {note} <a href="/cart">View cart</a></p>}
                    <div className="ap-buy">
                        <button type="button" className="ap-buy__btn" onClick={buy} disabled={busy} data-testid="room-continue">
                            {busy ? <i className="fa fa-circle-o-notch fa-spin" aria-hidden="true"></i> : <i className="fa fa-lock" aria-hidden="true"></i>}
                            <span>Buy now</span><b>{inr(price.total)}</b>
                        </button>
                        <button type="button" className="ap-buy__cart" onClick={addToCart} disabled={busy} data-testid="room-add">
                            <i className="fa fa-shopping-bag" aria-hidden="true"></i> Add to cart
                        </button>
                        {sel.format === 'ORIGINAL' && <OfferButton terms={terms} onOpen={() => setOfferOpen(true)} />}
                        <p>{part < PARTS.length - 1 ? 'Already decided? Buy now goes straight to delivery and payment.' : 'Next: delivery address and secure payment.'}</p>
                    </div>
                    {offerOpen && terms && terms.open && <OfferDialog painting={p} terms={terms} onClose={() => { setOfferOpen(false); reloadTerms(); }} onSent={() => reloadTerms()} />}

                    <ul className="ap-facts">
                        {facts.map((x) => <li key={x}><i className="fa fa-check" aria-hidden="true"></i>{x}</li>)}
                    </ul>

                    <div className="ap-talk">
                        {talk ? (
                            <TalkToArtist painting={p} contact={contact} conversation={conversation} customer={customer} context={`${variantLine(sel, price)} · ${inr(price.total)}`} />
                        ) : (
                            <button type="button" className="ap-link" onClick={() => setTalk(true)}>
                                <i className="fa fa-comments-o" aria-hidden="true"></i> Questions about size or framing? Ask {String(p.artist_name).split(' ')[0]}
                            </button>
                        )}
                    </div>
                </aside>
            </div>

            <div className="ap-mobilebar">
                <span><b>{inr(price.total)}</b><small>{current.letter}. {current.label}</small></span>
                {part < PARTS.length - 1
                    ? <button type="button" onClick={() => go(part + 1)}>Next</button>
                    : <button type="button" onClick={buy} disabled={busy}>Buy now</button>}
            </div>
        </PurchaseLayout>
    );
}
