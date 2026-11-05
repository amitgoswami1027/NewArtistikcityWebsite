import React, { useMemo, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import { Inertia } from '@inertiajs/inertia';
import axios from 'axios';
import PurchaseLayout from '@/Components/Shop/PurchaseLayout';
import RoomStage, { RoomSwitcher, usePreloadRooms } from '@/Components/Shop/RoomStage';
import { roomById } from '@/Components/Shop/rooms';
import { TalkToArtist } from '@/Components/Shop/BuyPanel';
import { useConversation } from '@/Components/Shop/ArtistHub';
import { useCart } from '@/Components/Shop/CartStore';
import { FORMAT_INFO, FRAME_INFO, formatOf, normalise, priceOf, frameCost, variantLine, inr, num, errorText } from '@/Components/Shop/shop';

const ROOM_KEY = 'artistikcity.room';
const LINE_KEY = 'artistikcity.flowline.';

const FRAME_NOTES = {
    NONE: 'Hangs as it comes',
    BLACK_WOOD: 'Matte black ash, 1¼″',
    OAK: 'Solid natural oak, 1¼″',
    WHITE_FLOAT: 'Floats the canvas in a white box',
};

/**
 * Screen 2: the artwork in a photographed room, to scale. One click switches the room; size and frame change the
 * piece on the wall instantly. "Continue to payment" puts this exact choice in the cart and opens the checkout.
 */
export default function Step2Room() {
    const { painting: p, offer, draft, hold, serverNow, contact, conversation: conv0, customer } = usePage().props;
    const cart = useCart();
    const conversation = useConversation(p.id, conv0);
    usePreloadRooms();
    const [sel, setSel] = useState(() => normalise(offer, { ...draft }));
    const [room, setRoom] = useState(() => { try { return roomById(window.sessionStorage.getItem(ROOM_KEY) || 'living').id; } catch (e) { return 'living'; } });
    const [reset, setReset] = useState(0);
    const [measure, setMeasure] = useState(true);
    const [busy, setBusy] = useState(false);
    const [err, setErr] = useState(null);
    const f = formatOf(offer, sel.format);
    const price = useMemo(() => priceOf(offer, sel), [offer, sel]);
    const update = (patch) => setSel((s) => normalise(offer, { ...s, ...patch }));
    const pickRoom = (k) => { setRoom(k); try { window.sessionStorage.setItem(ROOM_KEY, k); } catch (e) { /* private mode */ } };
    const surface = sel.format === 'POSTER' || (sel.format === 'ORIGINAL' && !/canvas|panel|linen/i.test(p.surface)) ? 'paper' : 'canvas';
    const r = roomById(room);

    const toPayment = () => {
        setBusy(true); setErr(null);
        const item = { paintingId: p.id, format: sel.format, size: sel.size, finish: sel.finish, frame: sel.frame, qty: sel.qty };
        let previous = null;
        try { previous = JSON.parse(window.sessionStorage.getItem(LINE_KEY + p.id) || 'null'); } catch (e) { /* ignore */ }
        axios.post('/api/v1/purchase/draft', item)
            // coming back to change the frame replaces the line this flow added, instead of adding a second one
            .then(() => (previous && previous.id && (previous.frame !== sel.frame || previous.size !== sel.size || previous.finish !== sel.finish)
                ? cart.remove(previous.id).catch(() => null) : null))
            .then(() => cart.add(item, { openDrawer: false }))
            .then((data) => {
                const line = data && data.lines && data.lines.find((l) => l.paintingId === p.id && l.format === sel.format && l.frame === sel.frame
                    && (l.size || '') === (sel.size || '') && (l.finish || '') === (sel.finish || ''));
                try { window.sessionStorage.setItem(LINE_KEY + p.id, JSON.stringify(line ? { id: line.id, frame: sel.frame, size: sel.size, finish: sel.finish } : null)); } catch (e) { /* ignore */ }
                Inertia.visit('/purchase/checkout');
            })
            .catch((e) => { setBusy(false); setErr(errorText(e)); });
    };

    if (!f || !price) return null;
    return (
        <PurchaseLayout painting={p} step={2} title="See it in your room" hold={hold} serverNow={serverNow}>
            <div className="pz-room pz-room--photo">
                <div className="pz-room__stage">
                    <RoomStage room={room} image={p.image_url} title={p.title} widthIn={price.width} heightIn={price.height} frame={sel.frame}
                               surface={surface} showSize={measure} resetToken={reset} />
                    <RoomSwitcher value={room} onChange={pickRoom} />
                    <div className="pz-room__tools">
                        <p className="pz-fine">Shown to scale, {r.note}. Drag the artwork to try another spot.</p>
                        <div>
                            <label className="pz-check"><input type="checkbox" checked={measure} onChange={(e) => setMeasure(e.target.checked)} /> Show size</label>
                            <button type="button" className="pz-link" onClick={() => setReset((n) => n + 1)}><i className="fa fa-undo" aria-hidden="true"></i> Re-centre</button>
                        </div>
                    </div>
                </div>

                <aside className="pz-room__panel">
                    <div className="pz-mini">
                        <span className="pz-mini__img">{p.image_url && <img src={p.image_url} alt="" />}</span>
                        <div>
                            <b>{p.title}</b>
                            <small>{FORMAT_INFO[sel.format].name} · by {p.artist_name}</small>
                        </div>
                        <a href={`/marketplace/${p.slug}#buy`} className="pz-link">Change</a>
                    </div>

                    {sel.format === 'ORIGINAL' ? (
                        <div className="pz-option">
                            <p className="pz-legend">Size</p>
                            <p className="pz-static">{num(price.width)} × {num(price.height)} in <span className="pz-muted">· the original's real size</span></p>
                        </div>
                    ) : (
                        <fieldset className="pz-option">
                            <legend className="pz-legend">Size</legend>
                            <div className="pz-chips">
                                {f.sizes.map((s) => (
                                    <button key={s.code} type="button" aria-pressed={sel.size === s.code} className={sel.size === s.code ? 'is-on' : ''} onClick={() => update({ size: s.code })}>
                                        {num(s.width)} × {num(s.height)}″
                                    </button>
                                ))}
                            </div>
                        </fieldset>
                    )}

                    <fieldset className="pz-option">
                        <legend className="pz-legend">Frame</legend>
                        {f.frames.length <= 1 ? (
                            <p className="pz-static pz-muted">{p.is_framed ? 'This original arrives framed, ready to hang.' : 'Framing is not offered for this format.'}</p>
                        ) : (
                            <div className="pz-frames2">
                                {f.frames.map((code) => {
                                    const extra = code === 'NONE' ? 0 : frameCost(offer, sel, code);
                                    return (
                                        <label key={code} className={`pz-frame2 ${sel.frame === code ? 'is-on' : ''}`} data-frame={code}>
                                            <input type="radio" name="frame" value={code} checked={sel.frame === code} onChange={() => update({ frame: code })} />
                                            <span className={`pz-frame2__sw pz-frame2__sw--${code.toLowerCase()}`} aria-hidden="true"><span></span></span>
                                            <span className="pz-frame2__name">{FRAME_INFO[code].name}</span>
                                            <span className="pz-frame2__note">{FRAME_NOTES[code]}</span>
                                            <span className="pz-frame2__price">{code === 'NONE' ? 'Included' : `+${inr(extra)}`}</span>
                                        </label>
                                    );
                                })}
                            </div>
                        )}
                    </fieldset>

                    <div className="pz-summary">
                        <div><span>{sel.qty > 1 ? `${sel.qty} × ${inr(price.unit)}` : 'Price'}</span><b data-testid="room-price">{inr(price.total)}</b></div>
                        <p className="pz-fine">{variantLine(sel, price)}</p>
                    </div>
                    {err && <p className="pz-error" role="alert">{err}</p>}
                    <button type="button" className="pz-btn pz-btn--brand pz-btn--lg pz-btn--block" onClick={toPayment} disabled={busy} data-testid="room-continue">
                        {busy ? <i className="fa fa-circle-o-notch fa-spin" aria-hidden="true"></i> : <i className="fa fa-lock" aria-hidden="true"></i>} Continue to payment
                    </button>
                    <TalkToArtist painting={p} contact={contact} conversation={conversation} customer={customer} context={`${variantLine(sel, price)} · ${inr(price.total)}`} />
                </aside>
            </div>
        </PurchaseLayout>
    );
}
