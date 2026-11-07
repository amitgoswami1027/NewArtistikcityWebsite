import React, { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { useCart } from '@/Components/Shop/CartStore';
import { inr, errorText } from '@/Components/Shop/shop';
import { useCountdown, mmss } from '@/Components/Marketplace/shared';

/** "Held for 12:31" for an original in the cart, ticking. */
export function HoldTicker({ expiresAt, serverNow }) {
    const left = useCountdown(expiresAt, serverNow);
    if (!expiresAt) return null;
    return (
        <span className={`pz-hold ${left <= 120 ? 'is-urgent' : ''}`}>
            <i className="fa fa-lock" aria-hidden="true"></i>{left > 0 ? <>Held for you · <b>{mmss(left)}</b></> : 'Hold ended'}
        </span>
    );
}

export function Qty({ value, max, onChange, disabled, label = 'Quantity' }) {
    return (
        <span className="pz-qty" role="group" aria-label={label}>
            <button type="button" onClick={() => onChange(value - 1)} disabled={disabled || value <= 1} aria-label="One less"><i className="fa fa-minus" aria-hidden="true"></i></button>
            <output aria-live="polite">{value}</output>
            <button type="button" onClick={() => onChange(value + 1)} disabled={disabled || value >= max} aria-label="One more"><i className="fa fa-plus" aria-hidden="true"></i></button>
        </span>
    );
}

/** One cart line (drawer and checkout summary share it). */
export function CartLine({ line, serverNow, compact = false, onQty, onRemove, onReserve, busy }) {
    const bad = line.status !== 'ok';
    return (
        <li className={`pz-line ${bad ? 'is-bad' : ''}`}>
            <a href={`/marketplace/${line.slug}`} className="pz-line__img" tabIndex={-1} aria-hidden="true">
                {line.image ? <img src={line.image} alt="" /> : <span></span>}
            </a>
            <div className="pz-line__body">
                <a href={`/marketplace/${line.slug}`} className="pz-line__title">{line.title}</a>
                <p className="pz-line__by">by {line.artist}</p>
                <p className="pz-line__variant">{line.label}</p>
                {line.format === 'ORIGINAL' && line.status === 'ok' && <HoldTicker expiresAt={line.holdExpiresAt} serverNow={serverNow} />}
                {bad && <p className="pz-line__warn" role="alert"><i className="fa fa-exclamation-circle" aria-hidden="true"></i>{line.message}</p>}
                {!compact && (
                    <div className="pz-line__actions">
                        {line.maxQty > 1 && !bad && <Qty value={line.qty} max={line.maxQty} onChange={(q) => onQty(line, q)} disabled={busy} label={`Quantity of ${line.title}`} />}
                        {line.status === 'hold_expired' && onReserve && <button type="button" className="pz-link" onClick={() => onReserve(line)} disabled={busy}>Reserve again</button>}
                        <button type="button" className="pz-link pz-link--muted" onClick={() => onRemove(line)} disabled={busy}>Remove</button>
                    </div>
                )}
                {compact && line.qty > 1 && <p className="pz-line__variant">Qty {line.qty} × {inr(line.unitPrice)}</p>}
            </div>
            <div className="pz-line__price">
                {bad ? <span className="pz-muted">—</span> : <b>{inr(line.lineTotal)}</b>}
                {Number(line.discount) > 0 && <small>−{inr(line.discount)}</small>}
            </div>
        </li>
    );
}

export function Totals({ totals, showShippingNote = true }) {
    if (!totals) return null;
    return (
        <dl className="pz-totals">
            <div><dt>Subtotal</dt><dd>{inr(totals.subtotal)}</dd></div>
            {Number(totals.discount) > 0 && <div className="is-green"><dt>Coupon {totals.coupon && totals.coupon.code}</dt><dd>−{inr(totals.discount)}</dd></div>}
            {totals.needsShipping && (
                <div><dt>Shipping{showShippingNote && totals.hasPrints ? <small> ({totals.country === 'India' ? 'India' : 'worldwide'})</small> : null}</dt>
                    <dd>{Number(totals.shipping) > 0 ? inr(totals.shipping) : <span className="is-green">Free</span>}</dd></div>
            )}
            <div className="is-total"><dt>Total</dt><dd>{inr(totals.total)}</dd></div>
        </dl>
    );
}

/** Slide-out mini cart, opened from the header bag icon or after "Add to cart". */
export default function CartDrawer() {
    const cart = useCart();
    const panel = useRef(null);
    const [notice, setNotice] = useState(null);
    const { data } = cart;

    useEffect(() => {
        if (!cart.isOpen) return undefined;
        const onKey = (e) => { if (e.key === 'Escape') cart.close(); };
        document.addEventListener('keydown', onKey);
        document.body.classList.add('pz-noscroll');
        const t = setTimeout(() => { if (panel.current) panel.current.focus(); }, 20);
        return () => { document.removeEventListener('keydown', onKey); document.body.classList.remove('pz-noscroll'); clearTimeout(t); };
    }, [cart.isOpen]); // eslint-disable-line react-hooks/exhaustive-deps

    if (!cart.isOpen) return null;
    const lines = data ? data.lines : [];
    const reserve = (l) => cart.add({ paintingId: l.paintingId, format: 'ORIGINAL', frame: l.frame, qty: 1 }, { openDrawer: false })
        .then(() => setNotice('It’s held for you again for 15 minutes.')).catch((e) => setNotice(errorText(e)));

    const node = (
        <div className="pz-overlay" onMouseDown={(e) => { if (e.target === e.currentTarget) cart.close(); }}>
            <aside className="pz-drawer" role="dialog" aria-modal="true" aria-labelledby="pz-drawer-title" tabIndex={-1} ref={panel}>
                <header className="pz-drawer__head">
                    <h2 id="pz-drawer-title">Your cart {data && data.count > 0 && <span>{data.count}</span>}</h2>
                    <button type="button" className="pz-iconbtn" onClick={cart.close} aria-label="Close cart"><i className="fa fa-times" aria-hidden="true"></i></button>
                </header>
                <div className="pz-drawer__body" aria-busy={cart.busy ? 'true' : 'false'}>
                    {!data && <div className="pz-skeleton" aria-label="Loading your cart"><span></span><span></span><span></span></div>}
                    {data && lines.length === 0 && (
                        <div className="pz-empty">
                            <div className="pz-empty__art" aria-hidden="true"><i className="fa fa-shopping-bag"></i></div>
                            <h3>Your cart is empty</h3>
                            <p>Originals, numbered canvases, posters and downloads you add will wait for you here.</p>
                            <a href="/marketplace" className="pz-btn pz-btn--ink">Browse the marketplace</a>
                        </div>
                    )}
                    {(cart.error || notice) && <p className={`pz-note ${cart.error ? 'is-error' : ''}`} role="status">{cart.error || notice}</p>}
                    {lines.length > 0 && (
                        <ul className="pz-lines">
                            {lines.map((l) => (
                                <CartLine key={l.id} line={l} serverNow={cart.serverNow} busy={cart.busy}
                                          onQty={(line, q) => cart.setQty(line.id, q).catch(() => {})}
                                          onRemove={(line) => cart.remove(line.id).catch(() => {})} onReserve={reserve} />
                            ))}
                        </ul>
                    )}
                </div>
                {data && lines.length > 0 && (
                    <footer className="pz-drawer__foot">
                        {data.totals.coupon && data.totals.coupon.error && <p className="pz-note">Code {data.totals.coupon.code}: {data.totals.coupon.error}</p>}
                        <Totals totals={data.totals} />
                        {data.totals.hasPrints && <p className="pz-fine">Shipping shown for delivery in India; worldwide rates apply at checkout.</p>}
                        <a href="/purchase/checkout" className={`pz-btn pz-btn--brand pz-btn--block ${data.totals.blocked ? 'is-disabled' : ''}`} aria-disabled={data.totals.blocked ? 'true' : undefined}
                           onClick={(e) => { if (data.totals.blocked) { e.preventDefault(); setNotice('Remove or fix the items marked in red first.'); } }}>
                            <i className="fa fa-lock" aria-hidden="true"></i> Checkout · {inr(data.totals.total)}
                        </a>
                        <button type="button" className="pz-btn pz-btn--ghost pz-btn--block" onClick={cart.close}>Keep browsing</button>
                    </footer>
                )}
            </aside>
        </div>
    );
    // rendered at the end of <body> so no sticky / filtered ancestor can clip the fixed overlay
    return typeof document !== 'undefined' ? createPortal(<div className="ac">{node}</div>, document.body) : node;
}

function CartButtonInner() {
    const cart = useCart();
    return (
        <button type="button" className="pz-cartbtn" onClick={cart.open} aria-label={`Cart, ${cart.count} item${cart.count === 1 ? '' : 's'}`}>
            <i className="fa fa-shopping-bag" aria-hidden="true"></i>
            {cart.count > 0 && <span className="pz-cartbtn__count">{cart.count > 99 ? '99+' : cart.count}</span>}
        </button>
    );
}

/** Header bag icon; it also owns the slide-out drawer (every page with the site header gets both). */
export function CartButton() {
    return (
        <>
            <CartButtonInner />
            <CartDrawer />
        </>
    );
}
