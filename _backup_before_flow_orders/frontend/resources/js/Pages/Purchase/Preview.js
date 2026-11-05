import React, { useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import { Inertia } from '@inertiajs/inertia';
import axios from 'axios';
import PurchaseLayout from '@/Components/Shop/PurchaseLayout';
import ArtistHub, { useConversation } from '@/Components/Shop/ArtistHub';
import { useCart } from '@/Components/Shop/CartStore';
import { Totals } from '@/Components/Shop/CartDrawer';
import { FORMAT_INFO, inr, num, errorText } from '@/Components/Shop/shop';

/** Step 3: what they picked, the cost estimate (with an artist coupon), and "Meet the artist" before checkout. */
export default function Preview() {
    const { painting: p, draft, estimate: initial, contact, conversation: conv0, customer, hold, serverNow } = usePage().props;
    const cart = useCart();
    const conversation = useConversation(p.id, conv0);
    const [estimate, setEstimate] = useState(initial);
    const [country, setCountry] = useState('India');
    const [code, setCode] = useState('');
    const [codeMsg, setCodeMsg] = useState(null);
    const [busy, setBusy] = useState(null);
    const [err, setErr] = useState(null);
    const line = estimate.line;
    const t = estimate.totals;
    const skipRoom = draft.format === 'DIGITAL';
    const context = line ? `${line.label} · ${inr(line.lineTotal)}` : FORMAT_INFO[draft.format].name;

    const reestimate = (c = country) => axios.post('/api/v1/purchase/estimate', { country: c }).then((r) => setEstimate(r.data)).catch(() => {});
    const pickCountry = (c) => { setCountry(c); reestimate(c); };
    const apply = (e) => {
        e.preventDefault();
        if (!code.trim()) return;
        setBusy('coupon'); setCodeMsg(null);
        cart.applyCoupon(code.trim(), country).then(() => reestimate()).then(() => { setCode(''); })
            .catch((x) => setCodeMsg({ error: true, text: errorText(x) })).finally(() => setBusy(null));
    };
    const removeCode = () => { setBusy('coupon'); cart.removeCoupon().then(() => reestimate()).finally(() => setBusy(null)); };

    const add = (thenCheckout) => {
        setBusy(thenCheckout ? 'checkout' : 'add'); setErr(null);
        cart.add({ paintingId: p.id, format: draft.format, size: draft.size, finish: draft.finish, frame: draft.frame, qty: draft.qty }, { openDrawer: !thenCheckout })
            .then(() => { if (thenCheckout) Inertia.visit('/purchase/checkout'); else { setBusy(null); reestimate(); } })
            .catch((x) => { setBusy(null); setErr(errorText(x)); });
    };

    return (
        <PurchaseLayout painting={p} step={3} title="Review & meet the artist" hold={hold} serverNow={serverNow} skipRoom={skipRoom}>
            <div className="pz-review">
                <div className="pz-review__main">
                    <section className="pz-card">
                        <header className="pz-card__head"><h2>Your selection</h2>
                            <a href={skipRoom ? '/purchase/step-1' : '/purchase/step-2'} className="pz-link"><i className="fa fa-pencil" aria-hidden="true"></i> Change</a></header>
                        {line && (
                            <div className="pz-pick">
                                <div className={`pz-pick__img pz-pick__img--${String(draft.frame || 'NONE').toLowerCase()}`}>{p.image_url && <img src={p.image_url} alt={p.title} />}</div>
                                <div className="pz-pick__body">
                                    <h3>{p.title}</h3>
                                    <p className="pz-muted">by {p.artist_name}</p>
                                    <p>{line.label}</p>
                                    {line.qty > 1 && <p className="pz-muted">{line.qty} × {inr(line.unitPrice)}</p>}
                                    {line.status !== 'ok' && <p className="pz-error">{line.message}</p>}
                                    <ul className="pz-ticks pz-ticks--sm">
                                        {draft.format === 'ORIGINAL' && <li>One of one{p.has_certificate ? ', with certificate of authenticity' : ''}</li>}
                                        {draft.format === 'CANVAS' && <li>Numbered limited edition, signed on the back</li>}
                                        {draft.format === 'POSTER' && <li>Archival inks, rolled in a sturdy tube</li>}
                                        {draft.format === 'DIGITAL' && <li>Download link by e-mail straight after payment</li>}
                                        {draft.format !== 'DIGITAL' && <li>Ships in {draft.format === 'ORIGINAL' ? '2–3' : '4–6'} working days, tracked</li>}
                                    </ul>
                                </div>
                                <p className="pz-pick__price">{inr(line.lineTotal)}</p>
                            </div>
                        )}
                    </section>

                    <section className="pz-card">
                        <header className="pz-card__head"><h2>Cost estimate</h2>
                            {estimate.inCart > 0 && <span className="pz-muted">Includes {estimate.inCart} item{estimate.inCart > 1 ? 's' : ''} already in your cart</span>}</header>
                        {t.hasPrints && (
                            <div className="pz-seg pz-seg--sm" role="radiogroup" aria-label="Delivery to">
                                {['India', 'Outside India'].map((c) => (
                                    <label key={c} className={country === c ? 'is-on' : ''}><input type="radio" name="country" checked={country === c} onChange={() => pickCountry(c)} />{c}</label>
                                ))}
                            </div>
                        )}
                        <Totals totals={t} showShippingNote={false} />
                        <form className="pz-coupon" onSubmit={apply}>
                            <label htmlFor="pz-code" className="pz-label">Artist coupon code</label>
                            {t.coupon ? (
                                <div className={`pz-coupon__on ${t.coupon.error ? 'is-warn' : ''}`}>
                                    <i className="fa fa-tag" aria-hidden="true"></i>
                                    <span><b>{t.coupon.code}</b> {t.coupon.error ? `· ${t.coupon.error}` : t.coupon.description ? `· ${t.coupon.description}` : 'applied'}</span>
                                    <button type="button" className="pz-link" onClick={removeCode} disabled={busy === 'coupon'}>Remove</button>
                                </div>
                            ) : (
                                <div className="pz-coupon__row">
                                    <input id="pz-code" type="text" value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} placeholder="e.g. WELCOME10" autoComplete="off" maxLength={40} />
                                    <button type="submit" className="pz-btn pz-btn--ink" disabled={busy === 'coupon' || !code.trim()}>{busy === 'coupon' ? <i className="fa fa-circle-o-notch fa-spin" aria-hidden="true"></i> : 'Apply'}</button>
                                </div>
                            )}
                            {codeMsg && <p className={codeMsg.error ? 'pz-error' : 'pz-ok'} role="status">{codeMsg.text}</p>}
                        </form>
                        {err && <p className="pz-error" role="alert">{err}</p>}
                        <div className="pz-cta">
                            {estimate.added ? (
                                <>
                                    <p className="pz-ok"><i className="fa fa-check-circle" aria-hidden="true"></i> This is in your cart.</p>
                                    <a href="/purchase/checkout" className="pz-btn pz-btn--brand pz-btn--lg pz-btn--block"><i className="fa fa-lock" aria-hidden="true"></i> Go to checkout</a>
                                    <a href="/marketplace" className="pz-btn pz-btn--line pz-btn--block">Keep browsing</a>
                                </>
                            ) : (
                                <>
                                    <button type="button" className="pz-btn pz-btn--brand pz-btn--lg pz-btn--block" onClick={() => add(true)} disabled={!!busy || (line && line.status !== 'ok')}>
                                        {busy === 'checkout' ? <i className="fa fa-circle-o-notch fa-spin" aria-hidden="true"></i> : <i className="fa fa-lock" aria-hidden="true"></i>} Add to cart & checkout
                                    </button>
                                    <button type="button" className="pz-btn pz-btn--line pz-btn--block" onClick={() => add(false)} disabled={!!busy || (line && line.status !== 'ok')}>
                                        {busy === 'add' ? <i className="fa fa-circle-o-notch fa-spin" aria-hidden="true"></i> : <i className="fa fa-shopping-bag" aria-hidden="true"></i>} Add to cart, keep browsing
                                    </button>
                                </>
                            )}
                        </div>
                        <ul className="pz-trust">
                            <li><i className="fa fa-shield" aria-hidden="true"></i>UPI, cards, NetBanking or PayPal</li>
                            <li><i className="fa fa-truck" aria-hidden="true"></i>Insured, tracked delivery</li>
                            <li><i className="fa fa-refresh" aria-hidden="true"></i>Prints replaced free if damaged</li>
                        </ul>
                    </section>
                </div>
                <aside className="pz-review__aside">
                    <ArtistHub painting={p} contact={contact} conversation={conversation} customer={customer} context={context} />
                    <p className="pz-fine pz-center">{num(p.height_inches)} × {num(p.width_inches)} in original · {p.medium}</p>
                </aside>
            </div>
        </PurchaseLayout>
    );
}
