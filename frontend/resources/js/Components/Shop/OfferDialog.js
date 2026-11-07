import React, { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import axios from 'axios';
import { inr, errorText } from '@/Components/Shop/shop';

/*
 * "Make an offer" on an original. useOfferTerms() asks the server whether this piece takes offers (and the buyer's live
 * offer, if any); <OfferButton> shows "Make an offer" or "Your offer: ₹x · pending" next to Buy now; <OfferDialog> is the
 * form: amount (from the lowest offer up to just under the price), optional message, and the promise to pay within the
 * payment window if it's accepted. Answers arrive by e-mail and in Account > Your offers.
 */

export function useOfferTerms(paintingId, enabled = true) {
    const [terms, setTerms] = useState(null);
    const load = () => axios.get(`/api/v1/offers/painting/${paintingId}`).then((r) => setTerms(r.data)).catch(() => setTerms(null));
    useEffect(() => { if (enabled && paintingId) load(); }, [paintingId, enabled]); // eslint-disable-line react-hooks/exhaustive-deps
    return [terms, load];
}

const STATUS_TEXT = {
    OPEN: 'waiting for the artist',
    COUNTERED: 'the artist made a counter-offer',
    ACCEPTED: 'accepted: pay to make it yours',
};

/** "Make an offer", or the buyer's live offer with a link to Your offers. */
export function OfferButton({ terms, onOpen, className = '' }) {
    if (!terms) return null;
    if (terms.mine) {
        const m = terms.mine;
        return (
            <a href="/user/offers" className={`pz-offer-mine ${className}`} data-testid="offer-mine">
                <i className="fa fa-handshake-o" aria-hidden="true"></i>
                <span>Your offer {inr(m.status === 'ACCEPTED' ? m.agreed : m.amount)} · <b>{STATUS_TEXT[m.status] || m.status.toLowerCase()}</b></span>
                <i className="fa fa-angle-right" aria-hidden="true"></i>
            </a>
        );
    }
    if (!terms.open || terms.own) return null;
    return (
        <button type="button" className={`pz-btn pz-btn--line pz-btn--block ${className}`} onClick={onOpen} data-testid="offer-open" aria-haspopup="dialog">
            <i className="fa fa-handshake-o" aria-hidden="true"></i> Make an offer
        </button>
    );
}

export default function OfferDialog({ painting, terms, onClose, onSent }) {
    const [amount, setAmount] = useState(() => String(Math.round((Number(terms.minAmount) + Number(terms.price)) / 2 / 100) * 100));
    const [message, setMessage] = useState('');
    const [agree, setAgree] = useState(false);
    const [busy, setBusy] = useState(false);
    const [errors, setErrors] = useState({});
    const [done, setDone] = useState(null);
    const box = useRef(null);
    const first = useRef(null);

    useEffect(() => {
        const onKey = (e) => { if (e.key === 'Escape') onClose(); };
        document.addEventListener('keydown', onKey);
        document.body.classList.add('pz-noscroll');
        const t = setTimeout(() => { if (first.current) first.current.focus(); }, 30);
        return () => { document.removeEventListener('keydown', onKey); document.body.classList.remove('pz-noscroll'); clearTimeout(t); };
    }, []); // eslint-disable-line react-hooks/exhaustive-deps

    const value = Number(String(amount).replace(/[^\d]/g, ''));
    const pct = terms.price ? Math.round((value / Number(terms.price)) * 100) : 0;
    const local = !value ? 'Enter the amount you would like to pay.'
        : value < Number(terms.minAmount) ? `The artist considers offers from ${inr(terms.minAmount)}.`
        : value >= Number(terms.price) ? 'That is the full price: use Buy now instead.' : null;

    const send = (e) => {
        e.preventDefault();
        const errs = {};
        if (local) errs.amount = local;
        if (!agree) errs.agree = 'Tick the box to confirm.';
        setErrors(errs);
        if (Object.keys(errs).length) return;
        setBusy(true);
        axios.post('/api/v1/offers', { paintingId: painting.id, amount: value, message, agree })
            .then((r) => { setDone(r.data.message); if (onSent) onSent(r.data.offer); })
            .catch((err) => {
                const d = err.response && err.response.data;
                if (d && d.code === 'SIGN_IN' && d.redirect) { window.location.assign(d.redirect); return; }
                const field = (d && d.field) || (d && d.errors && Object.keys(d.errors)[0]) || 'amount';
                setErrors({ [field]: errorText(err) });
            })
            .finally(() => setBusy(false));
    };

    const node = (
        <div className="pz-overlay pz-overlay--center" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
            <div className="pz-modal" role="dialog" aria-modal="true" aria-labelledby="offer-title" ref={box}>
                <header className="pz-modal__head">
                    <h2 id="offer-title">{done ? 'Offer sent' : 'Make an offer'}</h2>
                    <button type="button" className="pz-iconbtn" onClick={onClose} aria-label="Close"><i className="fa fa-times" aria-hidden="true"></i></button>
                </header>
                {done ? (
                    <div className="pz-modal__body">
                        <p className="pz-offer-done"><i className="fa fa-check-circle" aria-hidden="true"></i> {done}</p>
                        <p className="pz-fine">The artist has {terms.expiryHours} hours to accept, counter or decline. You can follow it, or withdraw it, in Your offers.</p>
                        <div className="pz-modal__actions">
                            <a href="/user/offers" className="pz-btn pz-btn--ink">See your offers</a>
                            <button type="button" className="pz-btn pz-btn--line" onClick={onClose}>Keep browsing</button>
                        </div>
                    </div>
                ) : (
                    <form className="pz-modal__body" onSubmit={send} noValidate>
                        <div className="pz-offer-art">
                            {painting.image_url && <img src={painting.image_url} alt="" />}
                            <div>
                                <b>{painting.title}</b>
                                <span>Original by {painting.artist_name} · listed at {inr(terms.price)}</span>
                            </div>
                        </div>
                        <label className="pz-field" htmlFor="offer-amount">
                            <span className="pz-field__label">Your offer</span>
                            <span className="pz-money">
                                <span aria-hidden="true">₹</span>
                                <input id="offer-amount" ref={first} inputMode="numeric" autoComplete="off" value={amount}
                                       onChange={(e) => { setAmount(e.target.value.replace(/[^\d]/g, '').slice(0, 9)); setErrors((x) => ({ ...x, amount: null })); }}
                                       aria-invalid={errors.amount ? 'true' : undefined} aria-describedby="offer-amount-help" data-testid="offer-amount" />
                                {value > 0 && <small>{pct}% of the price</small>}
                            </span>
                            {errors.amount ? <span className="pz-field__err" id="offer-amount-help" role="alert">{errors.amount}</span>
                                : <span className="pz-field__help" id="offer-amount-help">Offers from {inr(terms.minAmount)} ({terms.minPct}% of the price). Whole rupees.</span>}
                        </label>
                        <label className="pz-field" htmlFor="offer-message">
                            <span className="pz-field__label">Message to the artist <small>(optional)</small></span>
                            <textarea id="offer-message" rows={3} maxLength={500} value={message} onChange={(e) => setMessage(e.target.value)}
                                      placeholder="Why this piece speaks to you, where it will hang…" />
                            <span className="pz-field__help">{500 - message.length} characters left</span>
                        </label>
                        <label className={`pz-check ${errors.agree ? 'has-error' : ''}`}>
                            <input type="checkbox" checked={agree} onChange={(e) => { setAgree(e.target.checked); setErrors((x) => ({ ...x, agree: null })); }} data-testid="offer-agree" />
                            <span>If my offer is accepted, I'll pay within {terms.payHours} hours. The piece is held only for me during that time.</span>
                        </label>
                        {errors.agree && <span className="pz-field__err" role="alert">{errors.agree}</span>}
                        <div className="pz-modal__actions">
                            <button type="submit" className="pz-btn pz-btn--brand" disabled={busy} data-testid="offer-send">
                                {busy ? <i className="fa fa-circle-o-notch fa-spin" aria-hidden="true"></i> : <i className="fa fa-paper-plane" aria-hidden="true"></i>} Send offer{value > 0 ? ` · ${inr(value)}` : ''}
                            </button>
                            <button type="button" className="pz-btn pz-btn--line" onClick={onClose}>Cancel</button>
                        </div>
                        <p className="pz-fine">No payment now. The artist answers within {terms.expiryHours} hours; we'll e-mail you.</p>
                    </form>
                )}
            </div>
        </div>
    );
    return typeof document !== 'undefined' ? createPortal(<div className="ac">{node}</div>, document.body) : node;
}
