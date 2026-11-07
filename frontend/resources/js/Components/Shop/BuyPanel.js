import React, { useMemo, useState } from 'react';
import axios from 'axios';
import { Inertia } from '@inertiajs/inertia';
import { Qty } from '@/Components/Shop/CartDrawer';
import { useCart } from '@/Components/Shop/CartStore';
import OfferDialog, { OfferButton, useOfferTerms } from '@/Components/Shop/OfferDialog';
import { ChatWindow } from '@/Components/Shop/ArtistHub';
import { FORMAT_INFO, formatOf, normalise, priceOf, variantLine, inr, num, errorText } from '@/Components/Shop/shop';
import { useCountdown, mmss } from '@/Components/Marketplace/shared';

/*
 * Screen 1 of the purchase flow, inside the artwork page's buy box: how to own it (original, numbered canvas, poster,
 * download), size and finish, the live price, one "Continue" button, and the artist one click away. Nothing else:
 * framing and the room preview are screen 2, contact and payment screen 3.
 */

function initials(name) {
    return String(name || 'A').split(/\s+/).filter(Boolean).slice(0, 2).map((w) => w[0].toUpperCase()).join('');
}

/** The artist, one click away: chat in the page, or WhatsApp / Instagram / e-mail with the piece already described. */
export function TalkToArtist({ painting, contact = {}, conversation, customer, context }) {
    const [chat, setChat] = useState(false);
    const url = typeof window !== 'undefined' ? `${window.location.origin}/marketplace/${painting.slug}` : '';
    const intro = `Hi ${String(painting.artist_name).split(' ')[0]}, I'm interested in “${painting.title}”${context ? ` (${context})` : ''}. ${url}`;
    const replies = conversation && conversation.conv && conversation.conv.messages
        ? conversation.conv.messages.filter((m) => m.sender === 'ARTIST').length : 0;
    return (
        <div className="pz-talk" data-testid="talk-to-artist">
            <span className="pz-avatar" aria-hidden="true">{initials(painting.artist_name)}</span>
            <div className="pz-talk__text">
                <b>Talk to {painting.artist_name}</b>
                <small>Ask about colours, size or framing before you buy.</small>
            </div>
            <div className="pz-talk__actions">
                <button type="button" className="pz-talk__btn pz-talk__btn--chat" onClick={() => setChat(true)} aria-haspopup="dialog">
                    <i className="fa fa-comments-o" aria-hidden="true"></i><span>Chat</span>{replies > 0 && <em>{replies}</em>}
                </button>
                {contact.whatsapp && (
                    <a className="pz-talk__btn pz-talk__btn--wa" href={`https://wa.me/${contact.whatsapp}?text=${encodeURIComponent(intro)}`} target="_blank" rel="noopener noreferrer" aria-label="WhatsApp the artist">
                        <i className="fa fa-whatsapp" aria-hidden="true"></i></a>
                )}
                {contact.instagram && (
                    <a className="pz-talk__btn pz-talk__btn--ig" href={`https://ig.me/m/${contact.instagram}`} target="_blank" rel="noopener noreferrer" aria-label="Instagram message">
                        <i className="fa fa-instagram" aria-hidden="true"></i></a>
                )}
                {contact.email && (
                    <a className="pz-talk__btn" href={`mailto:${contact.email}?subject=${encodeURIComponent(`Question about “${painting.title}”`)}&body=${encodeURIComponent(intro + '\n\n')}`} aria-label="E-mail the artist">
                        <i className="fa fa-envelope-o" aria-hidden="true"></i></a>
                )}
            </div>
            {chat && conversation && <ChatWindow painting={painting} conversation={conversation} customer={customer} context={context} onClose={() => setChat(false)} />}
        </div>
    );
}

/** Initial selection: the visitor's last choice for this piece, or ?format=, or the original if it can be bought. */
export function startSelection(offer, draft, originalOk) {
    const usable = (fmt) => { const f = formatOf(offer, fmt); return f && f.available && (fmt !== 'ORIGINAL' || originalOk); };
    const first = (offer.formats.find((f) => usable(f.format)) || offer.formats[0] || {}).format;
    const start = draft && draft.format && usable(draft.format) ? draft.format : first;
    return normalise(offer, {
        format: start, size: draft && draft.format === start ? draft.size : '', finish: draft && draft.format === start ? draft.finish : '',
        frame: (draft && draft.format === start && draft.frame) || 'NONE', qty: (draft && draft.format === start && draft.qty) || 1,
    });
}

export default function BuyPanel({ p, offer, sel, setSel, hold, serverNow, preview, contact, conversation, customer }) {
    const cart = useCart();
    const [busy, setBusy] = useState(false);
    const [added, setAdded] = useState(null);
    const [offerOpen, setOfferOpen] = useState(() => { try { return new URLSearchParams(window.location.search).get('offer') === '1'; } catch (e) { return false; } });
    const [terms, reloadTerms] = useOfferTerms(p.id, !preview);
    const [err, setErr] = useState(null);
    const [conflict, setConflict] = useState(null);
    const mineLeft = useCountdown(hold && hold.mine ? hold.expiresAt : null, serverNow);
    const otherLeft = useCountdown(p.stock_status === 'RESERVED' && !(hold && hold.mine) ? p.reserved_until : null, serverNow);
    const mine = hold && hold.mine && mineLeft > 0;
    const heldByOther = p.stock_status === 'RESERVED' && !mine && otherLeft > 0;
    const sold = p.stock_status === 'SOLD';
    const f = formatOf(offer, sel.format);
    const price = useMemo(() => priceOf(offer, sel, p), [offer, sel, p]);
    const update = (patch) => setSel((s) => normalise(offer, { ...s, ...patch }));
    const originalBlocked = (fo) => fo.format === 'ORIGINAL' && (sold || heldByOther || !!preview);

    const choose = (format) => {
        const fo = formatOf(offer, format);
        if (!fo || !fo.available || originalBlocked(fo)) return;
        setErr(null);
        setSel((s) => normalise(offer, { ...s, format, size: s.format === format ? s.size : '', finish: s.format === format ? s.finish : '', qty: 1 }));
    };

    const go = (switchHold = false) => {
        setBusy(true); setErr(null); setConflict(null);
        axios.post('/api/v1/purchase/draft', { paintingId: p.id, ...sel, switch: switchHold })
            // nothing to hang for a download: straight to payment; everything else is previewed on a wall first
            .then(() => Inertia.visit(sel.format === 'DIGITAL' ? '/purchase/buy-now' : '/purchase/step-2'))
            .catch((e) => {
                const d = e.response && e.response.data;
                // buying starts with an account: the choice is saved, log in or join, then straight on to the next step
                if (d && d.code === 'SIGN_IN' && d.redirect) { window.location.assign(d.redirect); return; }
                setBusy(false);
                if (d && d.code === 'HOLD_LIMIT') setConflict(d);
                else if (d && (d.code === 'HELD_BY_OTHER' || d.code === 'JUST_TAKEN' || d.code === 'SOLD')) { setErr(d.message); setTimeout(() => window.location.reload(), 2200); }
                else setErr(errorText(e));
            });
    };
    const addToCart = (switchHold = false) => {
        setBusy(true); setErr(null); setConflict(null); setAdded(null);
        cart.add({ paintingId: p.id, ...sel, switch: switchHold })
            .then(() => { setBusy(false); setAdded(sel.format === 'ORIGINAL' ? 'Added. The original is held for you for 15 minutes.' : 'Added to your cart.'); })
            .catch((e) => {
                const d = e.response && e.response.data;
                if (d && d.code === 'SIGN_IN' && d.redirect) { window.location.assign(d.redirect); return; }
                setBusy(false);
                cart.clearError();
                if (d && d.code === 'HOLD_LIMIT') setConflict({ ...d, cart: true });
                else setErr(errorText(e));
            });
    };
    const release = () => { setBusy(true); axios.delete(`/api/v1/marketplace/reservations/${p.id}`).finally(() => window.location.reload()); };

    const edition = offer.edition || {};
    const context = price ? `${variantLine(sel, price)} · ${inr(price.total)}` : null;
    const canGo = f && f.available && price && !(sel.format === 'ORIGINAL' && originalBlocked(f)) && !preview;

    return (
        <div className="pz-buy" id="buy">
            <fieldset className="pz-buy__formats">
                <legend className="pz-legend">How would you like it?</legend>
                {offer.formats.map((fo) => {
                    const info = FORMAT_INFO[fo.format];
                    const blocked = !fo.available || originalBlocked(fo);
                    const on = sel.format === fo.format;
                    const why = fo.format === 'ORIGINAL' && heldByOther ? `Another collector is checking out · back in ${mmss(otherLeft)} if they don't`
                        : fo.format === 'ORIGINAL' && sold ? 'Sold · prints are still available' : !fo.available ? fo.reason : info.blurb;
                    return (
                        <label key={fo.format} className={`pz-buyopt ${on ? 'is-on' : ''} ${blocked ? 'is-off' : ''}`} data-format={fo.format}>
                            <input type="radio" name="buy-format" value={fo.format} checked={on} disabled={blocked} onChange={() => choose(fo.format)} />
                            <span className="pz-buyopt__icon" aria-hidden="true"><i className={`fa ${info.icon}`}></i></span>
                            <span className="pz-buyopt__text">
                                <span className="pz-buyopt__name">{info.name}{info.tag && <em>{info.tag}</em>}</span>
                                <span className="pz-buyopt__blurb">{why}</span>
                            </span>
                            <span className="pz-buyopt__price">
                                {fo.from && !blocked ? <>{fo.format === 'ORIGINAL' ? '' : <small>from </small>}{inr(fo.from)}</> : '—'}
                            </span>
                        </label>
                    );
                })}
            </fieldset>

            {f && f.available && sel.format !== 'ORIGINAL' && sel.format !== 'DIGITAL' && (
                <div className="pz-buy__opts">
                    <fieldset className="pz-option">
                        <legend className="pz-legend">Size</legend>
                        <div className="pz-chips">
                            {f.sizes.map((s) => (
                                <button key={s.code} type="button" aria-pressed={sel.size === s.code} className={sel.size === s.code ? 'is-on' : ''} onClick={() => update({ size: s.code })}>
                                    {num(s.width)} × {num(s.height)}″ <small>{inr(s.price)}</small>
                                </button>
                            ))}
                            {price && price.size && price.size.custom && (
                                <button type="button" aria-pressed="true" className="is-on">{num(price.width)} × {num(price.height)}″ <small>custom · {inr(price.size.price)}</small></button>
                            )}
                        </div>
                        {f.custom && <p className="pz-fine">Need another size? Choose any size from {f.custom.min} to {f.custom.max} in on the next step.</p>}
                    </fieldset>
                    {f.finishes.length > 0 && (
                        <fieldset className="pz-option">
                            <legend className="pz-legend">{sel.format === 'CANVAS' ? 'Finish' : 'Paper'}</legend>
                            <div className="pz-seg" role="radiogroup">
                                {f.finishes.map((x) => {
                                    const extra = sel.format === 'CANVAS' && x.code === 'STRETCHED' && price && price.size ? Number(price.size.extra) : 0;
                                    return (
                                        <label key={x.code} className={sel.finish === x.code ? 'is-on' : ''}>
                                            <input type="radio" name="buy-finish" value={x.code} checked={sel.finish === x.code} onChange={() => update({ finish: x.code })} />
                                            {x.label}{extra > 0 && <small> +{inr(extra)}</small>}
                                        </label>
                                    );
                                })}
                            </div>
                        </fieldset>
                    )}
                    <div className="pz-option pz-option--row">
                        {sel.format === 'CANVAS' && edition.size > 0
                            ? <p className="pz-edition"><i className="fa fa-hashtag" aria-hidden="true"></i> Edition of {edition.size} · <b>{edition.left} left</b></p>
                            : <span></span>}
                        {f.maxQty > 1 && <Qty value={sel.qty} max={f.maxQty} onChange={(q) => update({ qty: q })} />}
                    </div>
                </div>
            )}

            {mine && sel.format === 'ORIGINAL' && (
                <p className="pz-buy__hold" role="status"><i className="fa fa-lock" aria-hidden="true"></i> Held only for you · <b>{mmss(mineLeft)}</b>
                    <button type="button" className="pz-link pz-link--muted" onClick={release} disabled={busy}>Release</button></p>
            )}

            <div className="pz-buy__total">
                <div>
                    <p className="pz-buy__label">{FORMAT_INFO[sel.format] ? FORMAT_INFO[sel.format].name : ''}{price && price.width && sel.format !== 'ORIGINAL' ? ` · ${num(price.width)} × ${num(price.height)} in` : ''}{sel.qty > 1 ? ` · ${sel.qty} pcs` : ''}</p>
                    <p className="pz-buy__price" data-testid="buy-price">{price ? inr(price.total) : '—'}</p>
                </div>
                {sel.format === 'ORIGINAL' && Number(p.discount_percentage) > 0 && <s className="pz-muted">{inr(p.base_price)}</s>}
            </div>

            <div className="pz-buy__ctas">
                <button type="button" className="pz-btn pz-btn--brand pz-btn--lg pz-btn--block" data-testid="buy-continue" onClick={() => go(false)} disabled={busy || !canGo}>
                    {busy ? <i className="fa fa-circle-o-notch fa-spin" aria-hidden="true"></i> : <i className="fa fa-lock" aria-hidden="true"></i>}
                    {' '}Buy now
                </button>
                <button type="button" className="pz-btn pz-btn--ink pz-btn--lg pz-btn--block" data-testid="add-to-cart" onClick={() => addToCart(false)} disabled={busy || !canGo}>
                    <i className="fa fa-shopping-bag" aria-hidden="true"></i> Add to cart
                </button>
            </div>
            {added && <p className="pz-added" role="status"><i className="fa fa-check" aria-hidden="true"></i> {added} <a href="/cart">View cart</a></p>}
            {sel.format === 'ORIGINAL' && !sold && !preview && <OfferButton terms={terms} onOpen={() => { if (customer) setOfferOpen(true); else window.location.assign(`/login?return_to=${encodeURIComponent(`/marketplace/${p.slug}?offer=1#buy`)}&intent=buy`); }} />}
            {offerOpen && terms && terms.open && !terms.mine && customer && (
                <OfferDialog painting={p} terms={terms} onClose={() => { setOfferOpen(false); reloadTerms(); }} onSent={() => reloadTerms()} />
            )}
            <p className="pz-fine pz-center">
                {!customer ? 'Log in or join to buy: your choice is kept and you come straight back.'
                    : sel.format === 'ORIGINAL' && !mine ? 'Buying holds the original for you for 15 minutes. No payment yet.'
                    : sel.format === 'DIGITAL' ? 'Download link by e-mail right after payment.' : 'Next: choose a frame and see it on a wall, then pay.'}
            </p>
            {err && <p className="pz-error" role="alert">{err}</p>}
            {conflict && (
                <div className="pz-conflict" role="alertdialog" aria-label="You're already holding an original">
                    <p><b>You're already holding “{conflict.heldTitle}”.</b> You can hold one original at a time. Switch your hold to this piece?</p>
                    <div>
                        <button type="button" className="pz-btn pz-btn--ink" onClick={() => (conflict.cart ? addToCart(true) : go(true))}>Switch to this piece</button>
                        <button type="button" className="pz-btn pz-btn--line" onClick={() => setConflict(null)}>Keep my other piece</button>
                    </div>
                </div>
            )}

            {!preview && <TalkToArtist painting={p} contact={contact} conversation={conversation} customer={customer} context={context} />}
        </div>
    );
}
