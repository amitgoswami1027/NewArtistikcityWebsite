import React, { useEffect, useState } from 'react';
import { Head, usePage } from '@inertiajs/inertia-react';
import axios from 'axios';
import { CartProvider, useCart } from '@/Components/Shop/CartStore';
import { CartLine, Totals } from '@/Components/Shop/CartDrawer';
import { PurchaseStepper } from '@/Components/Shop/PurchaseLayout';
import { inr, errorText, loadRazorpay } from '@/Components/Shop/shop';

const COUNTRIES = ['India', 'United States', 'United Kingdom', 'Canada', 'Australia', 'United Arab Emirates', 'Singapore', 'Germany', 'France', 'Netherlands',
    'Italy', 'Spain', 'Switzerland', 'Sweden', 'Ireland', 'New Zealand', 'Japan', 'Hong Kong', 'Malaysia', 'Saudi Arabia', 'Qatar', 'South Africa', 'Other'];

function Field({ id, label, error, hint, children, className = '' }) {
    return (
        <div className={`pz-field ${className} ${error ? 'has-error' : ''}`}>
            <label htmlFor={id}>{label}</label>
            {children}
            {hint && !error && <small>{hint}</small>}
            {error && <small className="pz-field__err" id={`${id}-err`}>{error}</small>}
        </div>
    );
}

function CouponBox({ totals, country }) {
    const cart = useCart();
    const [code, setCode] = useState('');
    const [err, setErr] = useState(null);
    const apply = (e) => {
        e.preventDefault();
        if (!code.trim()) return;
        setErr(null);
        cart.applyCoupon(code.trim(), country).then(() => { setCode(''); cart.refresh(country); }).catch((x) => setErr(errorText(x)));
    };
    if (totals.coupon) {
        return (
            <div className={`pz-coupon__on ${totals.coupon.error ? 'is-warn' : ''}`}>
                <i className="fa fa-tag" aria-hidden="true"></i>
                <span><b>{totals.coupon.code}</b> {totals.coupon.error ? `· ${totals.coupon.error}` : totals.coupon.description ? `· ${totals.coupon.description}` : 'applied'}</span>
                <button type="button" className="pz-link" onClick={() => cart.removeCoupon().then(() => cart.refresh(country))}>Remove</button>
            </div>
        );
    }
    return (
        <form className="pz-coupon" onSubmit={apply}>
            <div className="pz-coupon__row">
                <input type="text" value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} placeholder="Coupon code" aria-label="Coupon code" autoComplete="off" maxLength={40} />
                <button type="submit" className="pz-btn pz-btn--line" disabled={!code.trim() || cart.busy}>Apply</button>
            </div>
            {err && <p className="pz-error" role="alert">{err}</p>}
        </form>
    );
}

function CheckoutBody() {
    const { customer, gateways = {}, payment, inrPerUsd } = usePage().props;
    const cart = useCart();
    const data = cart.data;
    const [tab, setTab] = useState('DOMESTIC');
    const [f, setF] = useState({
        name: (customer && customer.name) || '', email: (customer && customer.email) || '', phone: (customer && customer.phone) || '',
        line1: '', line2: '', city: '', state: '', postal: '', country: 'India',
    });
    const [errors, setErrors] = useState({});
    const [busy, setBusy] = useState(false);
    const [notice, setNotice] = useState(payment === 'failed' ? { type: 'error', text: 'Your PayPal payment did not complete. No money was taken. You can try again.' }
        : payment === 'cancelled' ? { type: 'info', text: 'You cancelled the PayPal payment. Your cart is still here.' } : null);
    const [testOrder, setTestOrder] = useState(null);
    const set = (k, v) => setF((x) => ({ ...x, [k]: v }));

    // shipping depends on the country
    useEffect(() => { cart.refresh(f.country); }, [f.country]); // eslint-disable-line react-hooks/exhaustive-deps
    useEffect(() => {
        if (f.country === 'India' && tab !== 'DOMESTIC') setTab('DOMESTIC');
        if (f.country !== 'India' && tab !== 'INTERNATIONAL') setTab('INTERNATIONAL');
    }, [f.country]); // eslint-disable-line react-hooks/exhaustive-deps
    const switchTab = (t) => {
        setTab(t);
        if (t === 'DOMESTIC') set('country', 'India');
        else if (f.country === 'India') set('country', 'United States');
    };

    if (!data) return <div className="pz-skeleton"><span></span><span></span></div>;
    const lines = data.lines;
    const totals = data.totals;
    if (lines.length === 0) {
        return (
            <div className="pz-empty pz-empty--page">
                <div className="pz-empty__art" aria-hidden="true"><i className="fa fa-shopping-bag"></i></div>
                <h1>Your cart is empty</h1>
                <p>Find an original, a numbered canvas or a print you love.</p>
                <a href="/marketplace" className="pz-btn pz-btn--brand">Browse the marketplace</a>
            </div>
        );
    }
    const ship = totals.needsShipping;

    const validate = () => {
        const e = {};
        if (f.name.trim().length < 2) e.name = 'Enter your full name.';
        if (!/^[^@\s]+@[^@\s]+\.[^@\s]{2,}$/.test(f.email.trim())) e.email = ship ? 'Enter a valid email for your receipt.' : 'Enter a valid email: your download link goes there.';
        if (ship) {
            if (tab === 'DOMESTIC' && !/^\d{10,13}$/.test(f.phone.replace(/\D/g, ''))) e.phone = 'Enter a 10-digit mobile number for delivery updates.';
            if (f.line1.trim().length < 3) e.line1 = 'Enter the street address.';
            if (f.city.trim().length < 2) e.city = 'Enter the city.';
            if (tab === 'DOMESTIC' && !/^\d{6}$/.test(f.postal.trim())) e.postal = 'Enter the 6-digit PIN code.';
            if (tab !== 'DOMESTIC' && (f.postal.trim().length < 3 || f.postal.trim().length > 12)) e.postal = 'Enter the postal code.';
        }
        setErrors(e);
        return Object.keys(e).length === 0;
    };

    const confirm = (payload) => axios.post('/api/v1/purchase/confirm', payload)
        .then((r) => { window.location.href = r.data.redirect; })
        .catch((x) => { setBusy(false); setNotice({ type: 'error', text: errorText(x, 'We could not confirm your payment.') }); });

    const pay = (ev) => {
        ev.preventDefault();
        setNotice(null);
        if (totals.blocked) { setNotice({ type: 'error', text: 'Remove or fix the items marked in red first.' }); return; }
        if (!validate()) { setNotice({ type: 'error', text: 'Please fix the highlighted fields.' }); window.scrollTo({ top: 0, behavior: 'smooth' }); return; }
        setBusy(true);
        axios.post('/api/v1/purchase/checkout', { gateway: tab === 'DOMESTIC' ? 'RAZORPAY' : 'PAYPAL', ...f })
            .then(async (r) => {
                const d = r.data;
                if (d.provider === 'test') { setTestOrder(d); setBusy(false); return; }
                if (d.provider === 'paypal') { window.location.href = d.approveUrl; return; }
                const Razorpay = await loadRazorpay();
                const rzp = new Razorpay({
                    key: d.key, amount: d.amountPaise, currency: 'INR', order_id: d.razorpayOrderId,
                    name: 'ArtistikCity', description: d.description, image: '/assets/images/logo.png',
                    prefill: d.prefill, theme: { color: '#e5156b' }, notes: { shop_order_id: d.orderId },
                    handler: (resp) => confirm({ orderId: d.orderId, provider: 'razorpay', ...resp }),
                    modal: { ondismiss: () => { setBusy(false); setNotice({ type: 'info', text: 'Payment window closed. Nothing was charged, so you can try again.' }); } },
                });
                rzp.on('payment.failed', (resp) => { setBusy(false); setNotice({ type: 'error', text: (resp.error && resp.error.description) || 'The payment failed. No money was taken.' }); });
                rzp.open();
            })
            .catch((x) => {
                setBusy(false);
                const d = x.response && x.response.data;
                if (d && d.errors) setErrors(d.errors);
                if (d && d.code === 'CART_CHANGED' && d.cart) cart.refresh(f.country);
                setNotice({ type: 'error', text: errorText(x) });
            });
    };

    const unavailable = tab === 'DOMESTIC' ? !gateways.razorpay && !gateways.testMode : !gateways.paypal && !gateways.testMode;
    const usd = (Number(totals.total) / Number(inrPerUsd || 84)).toFixed(2);

    return (
        <div className="pz-checkout">
            <form className="pz-checkout__form" onSubmit={pay} noValidate>
                <h1>Checkout</h1>
                {notice && <p className={`pz-note ${notice.type === 'error' ? 'is-error' : ''}`} role="alert">{notice.text}</p>}

                <section className="pz-card">
                    <h2 className="pz-card__title"><span>1</span> Contact</h2>
                    <div className="pz-grid2">
                        <Field id="co-name" label="Full name" error={errors.name}><input id="co-name" value={f.name} onChange={(e) => set('name', e.target.value)} autoComplete="name" /></Field>
                        <Field id="co-email" label="Email" error={errors.email} hint={ship ? 'For your receipt and tracking.' : 'Your download link goes here.'}>
                            <input id="co-email" type="email" value={f.email} onChange={(e) => set('email', e.target.value)} autoComplete="email" /></Field>
                        <Field id="co-phone" label={`Mobile${ship && tab === 'DOMESTIC' ? '' : ' (optional)'}`} error={errors.phone}>
                            <input id="co-phone" type="tel" value={f.phone} onChange={(e) => set('phone', e.target.value)} autoComplete="tel" /></Field>
                        <Field id="co-country" label="Country" error={errors.country}>
                            <select id="co-country" value={f.country} onChange={(e) => set('country', e.target.value)}>{COUNTRIES.map((c) => <option key={c}>{c}</option>)}</select></Field>
                    </div>
                </section>

                {ship ? (
                    <section className="pz-card">
                        <h2 className="pz-card__title"><span>2</span> Delivery address</h2>
                        <div className="pz-grid2">
                            <Field id="co-line1" label="Street address" error={errors.line1} className="pz-span2"><input id="co-line1" value={f.line1} onChange={(e) => set('line1', e.target.value)} autoComplete="address-line1" /></Field>
                            <Field id="co-line2" label="Apartment, floor (optional)" className="pz-span2"><input id="co-line2" value={f.line2} onChange={(e) => set('line2', e.target.value)} autoComplete="address-line2" /></Field>
                            <Field id="co-city" label="City" error={errors.city}><input id="co-city" value={f.city} onChange={(e) => set('city', e.target.value)} autoComplete="address-level2" /></Field>
                            <Field id="co-state" label="State (optional)"><input id="co-state" value={f.state} onChange={(e) => set('state', e.target.value)} autoComplete="address-level1" /></Field>
                            <Field id="co-postal" label={tab === 'DOMESTIC' ? 'PIN code' : 'Postal code'} error={errors.postal}><input id="co-postal" value={f.postal} onChange={(e) => set('postal', e.target.value)} autoComplete="postal-code" /></Field>
                        </div>
                    </section>
                ) : (
                    <section className="pz-card pz-card--soft">
                        <p><i className="fa fa-cloud-download" aria-hidden="true"></i> Nothing to ship: your download link arrives by email right after payment.</p>
                    </section>
                )}

                <section className="pz-card">
                    <h2 className="pz-card__title"><span>{ship ? 3 : 2}</span> Payment</h2>
                    <div className="pz-paytabs" role="tablist">
                        <button type="button" role="tab" aria-selected={tab === 'DOMESTIC'} className={tab === 'DOMESTIC' ? 'is-on' : ''} onClick={() => switchTab('DOMESTIC')}>
                            <b>India</b><span>UPI · cards · NetBanking (Razorpay)</span></button>
                        <button type="button" role="tab" aria-selected={tab === 'INTERNATIONAL'} className={tab === 'INTERNATIONAL' ? 'is-on' : ''} onClick={() => switchTab('INTERNATIONAL')}>
                            <b>International</b><span>PayPal · cards · about ${usd}</span></button>
                    </div>
                    {unavailable && <p className="pz-note is-error">This payment option isn't available right now. Please try the other one or contact us.</p>}
                    {testOrder ? (
                        <div className="pz-testpay">
                            <p><b>Test mode:</b> no payment gateway is configured, so you can simulate the payment for order <code>{testOrder.orderId}</code>.</p>
                            <button type="button" className="pz-btn pz-btn--brand pz-btn--block" onClick={() => { setBusy(true); confirm({ orderId: testOrder.orderId, provider: 'test' }); }} disabled={busy}>
                                {busy && <i className="fa fa-circle-o-notch fa-spin" aria-hidden="true"></i>} Simulate successful payment
                            </button>
                        </div>
                    ) : (
                        <button type="submit" className="pz-btn pz-btn--brand pz-btn--lg pz-btn--block" disabled={busy || unavailable || totals.blocked}>
                            {busy ? <i className="fa fa-circle-o-notch fa-spin" aria-hidden="true"></i> : <i className="fa fa-lock" aria-hidden="true"></i>}
                            {' '}Pay {tab === 'DOMESTIC' ? inr(totals.total) : `$${usd}`}
                        </button>
                    )}
                    <p className="pz-fine pz-center">Payments are processed securely by {tab === 'DOMESTIC' ? 'Razorpay' : 'PayPal'}. We never see your card details.</p>
                </section>
            </form>

            <aside className="pz-checkout__summary">
                <section className="pz-card">
                    <h2 className="pz-card__head">Order summary <span className="pz-muted">{data.count} item{data.count === 1 ? '' : 's'}</span></h2>
                    <ul className="pz-lines">
                        {lines.map((l) => (
                            <CartLine key={l.id} line={l} serverNow={cart.serverNow} busy={cart.busy}
                                      onQty={(line, q) => cart.setQty(line.id, q).then(() => cart.refresh(f.country)).catch(() => {})}
                                      onRemove={(line) => cart.remove(line.id).then(() => cart.refresh(f.country)).catch(() => {})}
                                      onReserve={(line) => cart.add({ paintingId: line.paintingId, format: 'ORIGINAL', frame: line.frame, qty: 1 }, { openDrawer: false }).then(() => cart.refresh(f.country)).catch(() => {})} />
                        ))}
                    </ul>
                    <CouponBox totals={totals} country={f.country} />
                    <Totals totals={totals} />
                    {cart.error && <p className="pz-error" role="alert">{cart.error}</p>}
                </section>
                <ul className="pz-trust">
                    <li><i className="fa fa-shield" aria-hidden="true"></i>Secure payment</li>
                    <li><i className="fa fa-certificate" aria-hidden="true"></i>Authentic artist works</li>
                    <li><i className="fa fa-comments-o" aria-hidden="true"></i>Questions? <a href="/contact">Contact us</a></li>
                </ul>
            </aside>
        </div>
    );
}

export default function PurchaseCheckout() {
    const { cart, serverNow } = usePage().props;
    return (
        <div className="ac">
            <Head title="Checkout · ArtistikCity" />
            <CartProvider initial={{ ...cart, serverNow }}>
                <div className="pz pz--shell">
                    <header className="pz-shellhead">
                        <div className="ac-wide">
                            <a href="/marketplace" aria-label="Back to the marketplace"><img src="/assets/images/logo.png" alt="ArtistikCity" /></a>
                            <span><i className="fa fa-lock" aria-hidden="true"></i> Secure checkout</span>
                        </div>
                    </header>
                    <main className="ac-wide">
                        <PurchaseStepper current={4} />
                        <CheckoutBody />
                    </main>
                </div>
            </CartProvider>
        </div>
    );
}
