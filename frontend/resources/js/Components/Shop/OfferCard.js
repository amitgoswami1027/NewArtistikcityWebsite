import React, { useState } from 'react';
import axios from 'axios';
import { inr, errorText } from '@/Components/Shop/shop';
import { useCountdown, mmss } from '@/Components/Marketplace/shared';

/*
 * One offer and its conversation, with the actions the viewer may take now (the server sends them in `can`):
 *   buyer   accept / reoffer / decline a counter-offer, withdraw, pay an accepted offer
 *   seller  accept / counter / decline an offer, withdraw a counter-offer (staff can also cancel an agreed price)
 * Used by Account > Your offers, My studio > Sell your art and Admin > Marketplace > Offers.
 */

export const OFFER_STATUS = {
    OPEN: { label: 'Waiting for the artist', sellerLabel: 'New offer', tone: 'is-blue' },
    COUNTERED: { label: 'Counter-offer', sellerLabel: 'Countered: waiting for the buyer', tone: 'is-amber' },
    ACCEPTED: { label: 'Accepted: pay to make it yours', sellerLabel: 'Accepted: waiting for payment', tone: 'is-green' },
    PAID: { label: 'Paid', tone: 'is-green' },
    DECLINED: { label: 'Declined', tone: 'is-grey' },
    WITHDRAWN: { label: 'Withdrawn', tone: 'is-grey' },
    EXPIRED: { label: 'Expired', tone: 'is-grey' },
    CLOSED: { label: 'Closed', tone: 'is-grey' },
};

const EVENT = {
    OFFER: 'offered',
    COUNTER: 'countered with',
    ACCEPT: 'accepted',
    DECLINE: 'declined',
    WITHDRAW: 'withdrew',
    EXPIRE: 'expired',
    CLOSE: 'closed',
    CANCEL: 'cancelled the agreed price',
    PAID: 'paid',
};

function Timer({ until, serverNow, prefix }) {
    const left = useCountdown(until, serverNow);
    if (!until || left <= 0) return null;
    return <span className={`of-timer ${left < 3600 * 6 ? 'is-urgent' : ''}`}><i className="fa fa-clock-o" aria-hidden="true"></i> {prefix} {mmss(left)}</span>;
}

export default function OfferCard({ offer: o, role, endpoint, onChange, serverNow }) {
    const [mode, setMode] = useState(null);          // counter | reoffer | decline
    const [amount, setAmount] = useState('');
    const [note, setNote] = useState('');
    const [busy, setBusy] = useState(false);
    const [err, setErr] = useState(null);
    const [msg, setMsg] = useState(null);
    const st = OFFER_STATUS[o.status] || { label: o.status, tone: 'is-grey' };
    const buyer = role === 'BUYER';
    const can = o.can || [];

    const post = (action, extra = {}) => {
        setBusy(true); setErr(null); setMsg(null);
        return axios.post(`${endpoint}/${o.id}`, { action, ...extra })
            .then((r) => { setMode(null); setAmount(''); setNote(''); setMsg(r.data.message); if (onChange) onChange(r.data.offer); })
            .catch((e) => setErr(errorText(e)))
            .finally(() => setBusy(false));
    };
    const pay = () => {
        setBusy(true); setErr(null);
        axios.post(`/api/v1/offers/${o.id}/pay`).then((r) => window.location.assign(r.data.redirect))
            .catch((e) => { setBusy(false); setErr(errorText(e)); });
    };
    const submitAmount = (e) => {
        e.preventDefault();
        const v = Number(String(amount).replace(/[^\d]/g, ''));
        if (!v) { setErr('Enter an amount.'); return; }
        post(mode, { amount: v, note });
    };

    const latest = o.status === 'ACCEPTED' || o.status === 'PAID' ? o.agreed : o.status === 'COUNTERED' ? o.counter : o.amount;
    return (
        <article className={`of-card ${o.unread ? 'is-unread' : ''}`} data-testid="offer-card" data-status={o.status} data-offer-id={o.id}>
            <div className="of-card__head">
                <a href={`/marketplace/${o.slug}`} className="of-card__img" tabIndex={-1} aria-hidden="true">{o.image ? <img src={o.image} alt="" loading="lazy" /> : null}</a>
                <div className="of-card__main">
                    <p className="of-card__title"><a href={`/marketplace/${o.slug}`}>{o.title}</a></p>
                    <p className="of-card__sub">
                        {buyer ? `by ${o.artist}` : <>from <b>{o.buyer}</b>{o.buyerEmail ? ` · ${o.buyerEmail}` : ''}</>}
                        {o.student ? ' · student painting' : ''} · listed at {inr(o.listPrice)}
                    </p>
                    <p className="of-card__status">
                        <span className={`of-badge ${st.tone}`}>{(!buyer && st.sellerLabel) || st.label}</span>
                        <b className="of-card__amount">{inr(latest)}</b>
                        {o.status === 'OPEN' || o.status === 'COUNTERED' ? <Timer until={o.expiresAt} serverNow={serverNow} prefix="answer within" /> : null}
                        {o.status === 'ACCEPTED' && <Timer until={o.payBy} serverNow={serverNow} prefix={buyer ? 'pay within' : 'buyer pays within'} />}
                        {o.closedReason && <span className="of-card__why">{o.closedReason}</span>}
                    </p>
                </div>
            </div>

            <ol className="of-thread" aria-label="History">
                {(o.events || []).map((e, i) => (
                    <li key={i} className={`of-ev of-ev--${String(e.actor).toLowerCase()}`}>
                        <span className="of-ev__who">{e.actor === 'BUYER' ? (buyer ? 'You' : o.buyer) : e.actor === 'SYSTEM' ? 'ArtistikCity' : (!buyer && e.actor === role ? 'You' : (e.who || 'The artist'))}</span>
                        {' '}{EVENT[e.kind] || String(e.kind).toLowerCase()}{e.amount ? <> <b>{inr(e.amount)}</b></> : ''}
                        <time>{new Date(e.at).toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })}</time>
                        {e.note && <q>{e.note}</q>}
                    </li>
                ))}
            </ol>

            {(msg || err) && <p className={`of-msg ${err ? 'is-error' : ''}`} role={err ? 'alert' : 'status'}>{err || msg}</p>}

            {mode === 'counter' || mode === 'reoffer' ? (
                <form className="of-form" onSubmit={submitAmount}>
                    <label>
                        <span>{mode === 'counter' ? 'Your counter-offer' : 'Your new offer'}</span>
                        <span className="pz-money"><span aria-hidden="true">₹</span>
                            <input inputMode="numeric" value={amount} onChange={(e) => setAmount(e.target.value.replace(/[^\d]/g, '').slice(0, 9))} aria-label="Amount in rupees" data-testid="offer-form-amount" autoFocus /></span>
                        <small>{mode === 'counter' ? `More than ${inr(o.amount)}, less than ${inr(o.currentPrice || o.listPrice)}.` : `More than ${inr(o.amount)}, less than ${inr(o.counter)}.`}</small>
                    </label>
                    <label><span>Note <small>(optional)</small></span><input value={note} maxLength={500} onChange={(e) => setNote(e.target.value)} /></label>
                    <div className="of-actions">
                        <button type="submit" className="pz-btn pz-btn--ink" disabled={busy} data-testid="offer-form-send">{mode === 'counter' ? 'Send counter-offer' : 'Send new offer'}</button>
                        <button type="button" className="pz-btn pz-btn--line" onClick={() => setMode(null)}>Cancel</button>
                    </div>
                </form>
            ) : mode === 'decline' ? (
                <form className="of-form" onSubmit={(e) => { e.preventDefault(); post('decline', { note }); }}>
                    <label><span>Reason <small>(optional, shared with the {buyer ? 'artist' : 'buyer'})</small></span>
                        <input value={note} maxLength={500} onChange={(e) => setNote(e.target.value)} autoFocus /></label>
                    <div className="of-actions">
                        <button type="submit" className="pz-btn pz-btn--ink" disabled={busy} data-testid="offer-form-decline">Decline</button>
                        <button type="button" className="pz-btn pz-btn--line" onClick={() => setMode(null)}>Cancel</button>
                    </div>
                </form>
            ) : can.length > 0 && (
                <div className="of-actions">
                    {can.includes('pay') && <button type="button" className="pz-btn pz-btn--brand" onClick={pay} disabled={busy} data-testid="offer-pay"><i className="fa fa-lock" aria-hidden="true"></i> Pay {inr(o.agreed)}</button>}
                    {can.includes('accept') && (
                        <button type="button" className="pz-btn pz-btn--brand" onClick={() => post('accept')} disabled={busy} data-testid="offer-accept">
                            Accept {inr(buyer ? o.counter : o.amount)}</button>
                    )}
                    {can.includes('counter') && <button type="button" className="pz-btn pz-btn--line" onClick={() => setMode('counter')} disabled={busy} data-testid="offer-counter">Counter</button>}
                    {can.includes('reoffer') && <button type="button" className="pz-btn pz-btn--line" onClick={() => setMode('reoffer')} disabled={busy} data-testid="offer-reoffer">Offer again</button>}
                    {can.includes('decline') && <button type="button" className="pz-btn pz-btn--ghost" onClick={() => setMode('decline')} disabled={busy} data-testid="offer-decline">Decline</button>}
                    {can.includes('withdraw') && <button type="button" className="pz-btn pz-btn--ghost" onClick={() => post('withdraw')} disabled={busy} data-testid="offer-withdraw">{buyer ? 'Withdraw offer' : 'Withdraw counter-offer'}</button>}
                    {can.includes('cancel') && <button type="button" className="pz-btn pz-btn--ghost" onClick={() => post('cancel')} disabled={busy} data-testid="offer-cancel">Cancel agreed price</button>}
                    {buyer && o.status === 'COUNTERED' && !can.includes('reoffer') && <span className="pz-fine">That was the last round: accept or decline.</span>}
                </div>
            )}
            {o.status === 'PAID' && o.orderId && buyer && <p className="pz-fine"><a href={`/purchase/orders/${o.orderId}`}>See order {o.orderId}</a></p>}
        </article>
    );
}
