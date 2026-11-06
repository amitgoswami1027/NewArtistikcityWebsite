import React, { useMemo, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import { Inertia } from '@inertiajs/inertia';
import axios from 'axios';
import PurchaseLayout from '@/Components/Shop/PurchaseLayout';
import ArtPreview, { FRAMES } from '@/Components/Shop/ArtPreview';
import { TalkToArtist } from '@/Components/Shop/BuyPanel';
import { useConversation } from '@/Components/Shop/ArtistHub';
import { FORMAT_INFO, FRAME_INFO, formatOf, normalise, priceOf, frameCost, variantLine, inr, num, errorText } from '@/Components/Shop/shop';

const FRAME_NOTES = {
    NONE: 'Hangs as it comes',
    BLACK_WOOD: 'Matte black ash',
    OAK: 'Solid natural oak',
    WHITE_FLOAT: 'Floats in a white box',
};

/**
 * Screen 2: see the piece before paying. The artwork itself is the big picture (on a lit wall, real size drawn
 * beside it); the room photos from Admin > Room preview are one click away. Size and frame change the picture at once,
 * and "Buy now" pays for exactly this choice.
 */
export default function Step2Room() {
    const { painting: p, offer, draft, hold, serverNow, contact, conversation: conv0, customer, rooms = [], roomsEnabled = true } = usePage().props;
    const conversation = useConversation(p.id, conv0);
    const [sel, setSel] = useState(() => normalise(offer, { ...draft }));
    const [busy, setBusy] = useState(false);
    const [err, setErr] = useState(null);
    const [talk, setTalk] = useState(false);
    const f = formatOf(offer, sel.format);
    const price = useMemo(() => priceOf(offer, sel), [offer, sel]);
    const update = (patch) => setSel((s) => normalise(offer, { ...s, ...patch }));
    const surface = sel.format === 'POSTER' || (sel.format === 'ORIGINAL' && !/canvas|panel|linen/i.test(p.surface || '')) ? 'paper' : 'canvas';

    const buy = () => {
        setBusy(true); setErr(null);
        axios.post('/api/v1/purchase/draft', { paintingId: p.id, format: sel.format, size: sel.size, finish: sel.finish, frame: sel.frame, qty: sel.qty })
            .then(() => Inertia.visit('/purchase/buy-now'))
            .catch((e) => {
                const d = e.response && e.response.data;
                if (d && d.code === 'SIGN_IN' && d.redirect) { window.location.assign(d.redirect); return; }
                setBusy(false); setErr(errorText(e));
            });
    };

    if (!f || !price) return null;
    const facts = [
        sel.format === 'ORIGINAL' && p.is_signed && 'Signed by the artist',
        sel.format === 'ORIGINAL' && p.has_certificate && 'Certificate of authenticity',
        sel.format === 'ORIGINAL' ? 'The one and only original' : sel.format === 'CANVAS' && offer.edition && offer.edition.size ? `Numbered edition of ${offer.edition.size}` : null,
        'Insured, tracked delivery',
    ].filter(Boolean);

    return (
        <PurchaseLayout painting={p} step={2} title="Preview and frame" hold={hold} serverNow={serverNow}>
            <div className="ap-page">
                <div className="ap-page__stage">
                    <ArtPreview rooms={roomsEnabled ? rooms : []} image={p.image_url} title={p.title} widthIn={price.width} heightIn={price.height}
                                frame={sel.frame} surface={surface} />
                </div>

                <aside className="ap-page__panel" aria-label="Your choice">
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

                    {sel.format === 'ORIGINAL' ? (
                        <div className="ap-opt">
                            <p className="ap-opt__label">Size</p>
                            <p className="ap-static">{num(price.width)} × {num(price.height)} in, the original's real size</p>
                        </div>
                    ) : (
                        <fieldset className="ap-opt">
                            <legend className="ap-opt__label">Size</legend>
                            <div className="ap-sizes">
                                {f.sizes.map((s) => (
                                    <button key={s.code} type="button" aria-pressed={sel.size === s.code} className={`ap-size ${sel.size === s.code ? 'is-on' : ''}`} onClick={() => update({ size: s.code })}>
                                        <span className="ap-size__shape" aria-hidden="true" style={{ aspectRatio: `${s.width} / ${s.height}` }}></span>
                                        <span>{num(s.width)} × {num(s.height)}″</span>
                                    </button>
                                ))}
                            </div>
                        </fieldset>
                    )}

                    <fieldset className="ap-opt">
                        <legend className="ap-opt__label">Frame</legend>
                        {f.frames.length <= 1 ? (
                            <p className="ap-static">{p.is_framed ? 'This original arrives framed, ready to hang.' : 'Framing is not offered for this format.'}</p>
                        ) : (
                            <div className="ap-frames">
                                {f.frames.map((code) => {
                                    const extra = code === 'NONE' ? 0 : frameCost(offer, sel, code);
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

                    {err && <p className="pz-error" role="alert">{err}</p>}
                    <div className="ap-buy">
                        <button type="button" className="ap-buy__btn" onClick={buy} disabled={busy} data-testid="room-continue">
                            {busy ? <i className="fa fa-circle-o-notch fa-spin" aria-hidden="true"></i> : <i className="fa fa-lock" aria-hidden="true"></i>}
                            <span>Buy now</span><b>{inr(price.total)}</b>
                        </button>
                        <p>Next: delivery address and secure payment.</p>
                    </div>

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

            <div className="ap-mobilebar" aria-hidden="true">
                <span><b>{inr(price.total)}</b><small>{FRAME_INFO[sel.frame] ? FRAME_INFO[sel.frame].name : ''}</small></span>
                <button type="button" tabIndex={-1} onClick={buy} disabled={busy}>Buy now</button>
            </div>
        </PurchaseLayout>
    );
}
