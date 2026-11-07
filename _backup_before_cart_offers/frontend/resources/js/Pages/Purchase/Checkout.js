import React, { useEffect, useState } from 'react';
import { Head, usePage } from '@inertiajs/inertia-react';
import axios from 'axios';
import { CartProvider, useCart } from '@/Components/Shop/CartStore';
import { CartLine, Totals } from '@/Components/Shop/CartDrawer';
import { PurchaseStepper } from '@/Components/Shop/PurchaseLayout';
import { inr, errorText, loadRazorpay } from '@/Components/Shop/shop';

const COUNTRIES = ['India', 'United States', 'United Kingdom', 'Canada', 'Australia', 'United Arab Emirates', 'Singapore', 'Germany', 'France', 'Netherlands',
    'Italy', 'Spain', 'Switzerland', 'Sweden', 'Ireland', 'New Zealand', 'Japan', 'Hong Kong', 'Malaysia', 'Saudi Arabia', 'Qatar', 'South Africa', 'Other'];

function Field({ id, label, error, hint, children, className = '', valid = false }) {
    return (
        <div className={`pz-field ${className} ${error ? 'has-error' : ''} ${valid ? 'is-valid' : ''}`}>
            <label htmlFor={id}>{label}</label>
            <div className="pz-field__control">
                {React.cloneElement(children, { 'aria-invalid': error ? 'true' : undefined, 'aria-describedby': error ? `${id}-err` : undefined })}
                {valid && <i className="fa fa-check pz-field__ok" aria-hidden="true"></i>}
            </div>
            {hint && !error && <small>{hint}</small>}
            {error && <small className="pz-field__err" id={`${id}-err`} role="alert">{error}</small>}
        </div>
    );
}

/** One accordion section: number, title, a one-line summary once done, the fields while open. */
function Section({ n, id, title, open, done, summary, onEdit, children }) {
    return (
        <section className={`pz-acc ${open ? 'is-open' : ''} ${done && !open ? 'is-done' : ''}`} data-section={id} aria-labelledby={`acc-${id}`}>
            <header className="pz-acc__head">
                <h2 id={`acc-${id}`} className="pz-acc__title">
                    <span className="pz-acc__num" aria-hidden="true">{done && !open ? <i className="fa fa-check"></i> : n}</span>{title}
                </h2>
                {done && !open && <button type="button" className="pz-link" onClick={onEdit} aria-label={`Edit ${title.toLowerCase()}`}>Edit</button>}
            </header>
            {done && !open && summary && <p className="pz-acc__summary">{summary}</p>}
            {open && <div className="pz-acc__body">{children}</div>}
        </section>
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

const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]{2,}$/;

/** Field rules, the same as the server's (PurchaseController.startCheckout). */
function check(k, f, { ship, domestic }) {
    const v = String(f[k] || '').trim();
    switch (k) {
        case 'name': return v.length < 2 ? 'Enter your full name.' : null;
        case 'email': return !EMAIL.test(v) ? (ship ? 'Enter a valid email for your receipt.' : 'Enter a valid email: your download link goes there.') : null;
        case 'phone':
            if (ship && domestic) return /^\d{10,13}$/.test(v.replace(/\D/g, '')) ? null : 'Enter a 10-digit mobile number for delivery updates.';
            return v && !/^\+?[\d\s()-]{6,20}$/.test(v) ? 'Enter a valid phone number, or leave it empty.' : null;
        case 'country': return v.length < 2 ? 'Choose your country.' : null;
        case 'line1': return ship && v.length < 3 ? 'Enter the street address.' : null;
        case 'city': return ship && v.length < 2 ? 'Enter the city.' : null;
        case 'postal':
            if (!ship) return null;
            if (domestic) return /^\d{6}$/.test(v) ? null : 'Enter the 6-digit PIN code.';
            return v.length < 3 || v.length > 12 ? 'Enter the postal code.' : null;
        default: return null;
    }
}
const CONTACT = ['name', 'email', 'country', 'phone'];
const DELIVERY = ['line1', 'line2', 'city', 'state', 'postal'];

function CheckoutBody() {
    const { customer, gateways = {}, payment, inrPerUsd } = usePage().props;
    const cart = useCart();
    const data = cart.data;
    const [f, setF] = useState({
        name: (customer && customer.name) || '', email: (customer && customer.email) || '', phone: (customer && customer.phone) || '',
        line1: '', line2: '', city: '', state: '', postal: '', country: 'India',
    });
    const domestic = f.country === 'India';
    const tab = domestic ? 'DOMESTIC' : 'INTERNATIONAL';
    const ship = data ? data.totals.needsShipping : true;
    const ctx = { ship, domestic };
    const [touched, setTouched] = useState({});
    const [serverErrors, setServerErrors] = useState({});
    const sectionValid = (keys) => keys.every((k) => !check(k, f, ctx));
    const [open, setOpen] = useState(() => (sectionValid(CONTACT) ? (ship ? 'delivery' : 'payment') : 'contact'));
    const [done, setDone] = useState(() => (sectionValid(CONTACT) ? { contact: true } : {}));
    const [busy, setBusy] = useState(false);
    const [notice, setNotice] = useState(payment === 'failed' ? { type: 'error', text: 'Your PayPal payment did not complete. No money was taken. You can try again.' }
        : payment === 'cancelled' ? { type: 'info', text: 'You cancelled the PayPal payment. Your order is still here: you can try again.' } : null);
    const [testOrder, setTestOrder] = useState(null);
    const [simulate, setSimulate] = useState(false);
    const [sumOpen, setSumOpen] = useState(false);
    const set = (k, v) => { setF((x) => ({ ...x, [k]: v })); if (serverErrors[k]) setServerErrors((e) => ({ ...e, [k]: null })); };
    const err = (k) => serverErrors[k] || (touched[k] ? check(k, f, ctx) : null);
    const ok = (k) => touched[k] && !check(k, f, ctx) && String(f[k] || '').trim() !== '';

    // shipping and the payment method depend on the country
    useEffect(() => { cart.refresh(f.country); }, [f.country]); // eslint-disable-line react-hooks/exhaustive-deps

    if (!data) return <div className="pz-skeleton"><span></span><span></span></div>;
    const lines = data.lines;
    const totals = data.totals;
    if (lines.length === 0) {
        return (
            <div className="pz-empty pz-empty--page">
                <div className="pz-empty__art" aria-hidden="true"><i className="fa fa-paint-brush"></i></div>
                <h1>Nothing to pay for yet</h1>
                <p>Choose an original, a numbered canvas or a print, then press Buy now.</p>
                <a href="/marketplace" className="pz-btn pz-btn--brand">Browse the marketplace</a>
            </div>
        );
    }
    const order = ship ? ['contact', 'delivery', 'payment'] : ['contact', 'payment'];
    const keysOf = (sec) => (sec === 'contact' ? CONTACT : sec === 'delivery' ? DELIVERY : []);

    /** Validate a section; if it's fine, close it and open the next one. */
    const advance = (sec) => {
        const keys = keysOf(sec);
        setTouched((t) => ({ ...t, ...Object.fromEntries(keys.map((k) => [k, true])) }));
        if (!sectionValid(keys)) {
            const first = keys.find((k) => check(k, f, ctx));
            const el = first && document.getElementById(`co-${first}`);
            if (el) el.focus();
            return false;
        }
        setDone((d) => ({ ...d, [sec]: true }));
        const next = order[order.indexOf(sec) + 1] || 'payment';
        setOpen(next);
        setTimeout(() => {
            const el = document.querySelector(`[data-section="${next}"]`);
            if (el && el.scrollIntoView) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
            const input = el && el.querySelector('input, select');
            if (input) input.focus({ preventScroll: true });
        }, 60);
        return true;
    };
    const blur = (k, sec, last) => () => {
        setTouched((t) => ({ ...t, [k]: true }));
        // finishing the last field of a complete section moves on by itself
        if (last && sectionValid(keysOf(sec))) advance(sec);
    };
    const onEnter = (sec) => (e) => { if (e.key === 'Enter' && e.target.tagName !== 'TEXTAREA') { e.preventDefault(); advance(sec); } };
    const input = (k, sec, props = {}, last = false) => (
        <input id={`co-${k}`} value={f[k]} onChange={(e) => set(k, e.target.value)} onBlur={blur(k, sec, last)} onKeyDown={onEnter(sec)} {...props} />
    );

    const confirm = (payload) => axios.post('/api/v1/purchase/confirm', payload)
        .then((r) => { window.location.href = r.data.redirect; })
        .catch((x) => { setBusy(false); setNotice({ type: 'error', text: errorText(x, 'We could not confirm your payment.') }); });

    const pay = (ev) => {
        if (ev) ev.preventDefault();
        setNotice(null);
        if (totals.blocked) { setNotice({ type: 'error', text: 'Remove or fix the items marked in red in your order summary first.' }); return; }
        for (const sec of order.filter((x) => x !== 'payment')) {
            if (!sectionValid(keysOf(sec))) {
                setTouched((t) => ({ ...t, ...Object.fromEntries(keysOf(sec).map((k) => [k, true])) }));
                setOpen(sec);
                return;
            }
        }
        setBusy(true);
        axios.post('/api/v1/purchase/checkout', { gateway: domestic ? 'RAZORPAY' : 'PAYPAL', simulate: simulate && gateways.simulate, ...f })
            .then(async (r) => {
                const d = r.data;
                if (d.provider === 'test') { setTestOrder(d); setBusy(false); return; }
                if (d.provider === 'paypal') { window.location.href = d.approveUrl; return; }
                const Razorpay = await loadRazorpay();
                const rzp = new Razorpay({
                    key: d.key, amount: d.amountPaise, currency: 'INR', order_id: d.razorpayOrderId,
                    name: 'ArtistikCity', description: d.description, image: '/assets/images/logo.png',
                    prefill: d.prefill, theme: { color: '#9c0d48' }, notes: { shop_order_id: d.orderId },
                    handler: (resp) => confirm({ orderId: d.orderId, provider: 'razorpay', ...resp }),
                    modal: { ondismiss: () => { setBusy(false); setNotice({ type: 'info', text: 'Payment window closed. Nothing was charged, so you can try again.' }); } },
                });
                rzp.on('payment.failed', (resp) => { setBusy(false); setNotice({ type: 'error', text: (resp.error && resp.error.description) || 'The payment failed. No money was taken.' }); });
                rzp.open();
            })
            .catch((x) => {
                setBusy(false);
                const d = x.response && x.response.data;
                if (d && d.code === 'SIGN_IN' && d.redirect) { window.location.assign(d.redirect); return; }
                if (d && d.errors) {
                    setServerErrors(d.errors);
                    const sec = order.find((s2) => keysOf(s2).some((k) => d.errors[k]));
                    if (sec) setOpen(sec);
                }
                if (d && d.code === 'CART_CHANGED' && d.cart) cart.refresh(f.country);
                setNotice({ type: 'error', text: errorText(x) });
            });
    };

    const unavailable = !(simulate && gateways.simulate) && (domestic ? !gateways.razorpay && !gateways.testMode : !gateways.paypal && !gateways.testMode);
    const usd = (Number(totals.total) / Number(inrPerUsd || 84)).toFixed(2);
    const contactSummary = [f.name, f.email, f.phone, f.country].filter(Boolean).join(' · ');
    const deliverySummary = [f.line1, f.line2, f.city, f.state, f.postal].filter(Boolean).join(', ');

    const summary = (
        <section className="pz-card">
            <h2 className="pz-card__head">Your order</h2>
            <ul className="pz-lines">
                {lines.map((l) => <CartLine key={l.id} line={l} serverNow={cart.serverNow} busy={cart.busy} compact />)}
            </ul>
            {lines[0] && (
                <p className="pz-fine pz-change">
                    <a href={lines[0].format === 'DIGITAL' ? `/marketplace/${lines[0].slug}#buy` : '/purchase/step-2'} className="pz-link">
                        <i className="fa fa-pencil" aria-hidden="true"></i> Change size or frame</a>
                </p>
            )}
            <CouponBox totals={totals} country={f.country} />
            <Totals totals={totals} />
            {cart.error && <p className="pz-error" role="alert">{cart.error}</p>}
        </section>
    );

    return (
        <div className="pz-checkout pz-checkout--acc">
            <button type="button" className="pz-co-mobilesum" aria-expanded={sumOpen} onClick={() => setSumOpen((v) => !v)}>
                <span><i className="fa fa-shopping-bag" aria-hidden="true"></i> {sumOpen ? 'Hide' : 'Show'} order summary <i className={`fa fa-angle-${sumOpen ? 'up' : 'down'}`} aria-hidden="true"></i></span>
                <b>{inr(totals.total)}</b>
            </button>
            <form className="pz-checkout__form" onSubmit={pay} noValidate>
                <h1>Payment</h1>
                {notice && <p className={`pz-note ${notice.type === 'error' ? 'is-error' : ''}`} role="alert">{notice.text}</p>}

                <Section n={1} id="contact" title="Contact" open={open === 'contact'} done={done.contact} summary={contactSummary} onEdit={() => setOpen('contact')}>
                    <div className="pz-grid2">
                        <Field id="co-name" label="Full name" error={err('name')} valid={ok('name')}>{input('name', 'contact', { autoComplete: 'name' })}</Field>
                        <Field id="co-email" label="Email" error={err('email')} valid={ok('email')} hint={ship ? 'For your receipt and tracking.' : 'Your download link goes here.'}>
                            {input('email', 'contact', { type: 'email', autoComplete: 'email', inputMode: 'email' })}</Field>
                        <Field id="co-country" label="Country" error={err('country')}>
                            <select id="co-country" value={f.country} onChange={(e) => set('country', e.target.value)} onBlur={blur('country', 'contact', false)} autoComplete="country-name">
                                {COUNTRIES.map((c) => <option key={c}>{c}</option>)}</select></Field>
                        <Field id="co-phone" label={`Mobile${ship && domestic ? '' : ' (optional)'}`} error={err('phone')} valid={ok('phone')} hint={ship && domestic ? 'The courier calls this number.' : null}>
                            {input('phone', 'contact', { type: 'tel', autoComplete: 'tel', inputMode: 'tel' }, true)}</Field>
                    </div>
                    <button type="button" className="pz-btn pz-btn--ink pz-acc__next" onClick={() => advance('contact')}>
                        Continue to {ship ? 'delivery' : 'payment'} <i className="fa fa-arrow-right" aria-hidden="true"></i></button>
                </Section>

                {ship ? (
                    <Section n={2} id="delivery" title="Delivery address" open={open === 'delivery'} done={done.delivery} summary={deliverySummary} onEdit={() => setOpen('delivery')}>
                        <div className="pz-grid2">
                            <Field id="co-line1" label="Street address" error={err('line1')} valid={ok('line1')} className="pz-span2">{input('line1', 'delivery', { autoComplete: 'address-line1' })}</Field>
                            <Field id="co-line2" label="Apartment, floor (optional)" className="pz-span2">{input('line2', 'delivery', { autoComplete: 'address-line2' })}</Field>
                            <Field id="co-city" label="City" error={err('city')} valid={ok('city')}>{input('city', 'delivery', { autoComplete: 'address-level2' })}</Field>
                            <Field id="co-state" label="State (optional)">{input('state', 'delivery', { autoComplete: 'address-level1' })}</Field>
                            <Field id="co-postal" label={domestic ? 'PIN code' : 'Postal code'} error={err('postal')} valid={ok('postal')}>
                                {input('postal', 'delivery', { autoComplete: 'postal-code', inputMode: domestic ? 'numeric' : 'text', maxLength: domestic ? 6 : 12 }, true)}</Field>
                        </div>
                        <p className="pz-fine"><i className="fa fa-truck" aria-hidden="true"></i> Insured and tracked. {domestic ? 'India: 5–8 working days.' : 'Worldwide: 10–15 working days.'}</p>
                        <button type="button" className="pz-btn pz-btn--ink pz-acc__next" onClick={() => advance('delivery')}>
                            Continue to payment <i className="fa fa-arrow-right" aria-hidden="true"></i></button>
                    </Section>
                ) : null}

                <Section n={ship ? 3 : 2} id="payment" title="Payment" open={open === 'payment'} done={false} onEdit={() => setOpen('payment')}>
                    {!ship && <p className="pz-note"><i className="fa fa-cloud-download" aria-hidden="true"></i> Nothing to ship: your download link arrives by email right after payment.</p>}
                    <div className="pz-paytabs" role="radiogroup" aria-label="Payment method">
                        <button type="button" role="radio" aria-checked={tab === 'DOMESTIC'} className={tab === 'DOMESTIC' ? 'is-on' : ''} onClick={() => set('country', 'India')}>
                            <b>India</b><span>UPI · cards · NetBanking (Razorpay)</span></button>
                        <button type="button" role="radio" aria-checked={tab === 'INTERNATIONAL'} className={tab === 'INTERNATIONAL' ? 'is-on' : ''}
                                onClick={() => { if (domestic) { set('country', 'United States'); setOpen('contact'); setTouched((t) => ({ ...t, country: true })); } }}>
                            <b>International</b><span>PayPal · cards · about ${usd}</span></button>
                    </div>
                    {!domestic && <p className="pz-fine">Paying from outside India? Check your country under Contact: it sets shipping and the currency.</p>}
                    {gateways.simulate && (
                        <label className={`pz-simulate ${simulate ? 'is-on' : ''}`} data-testid="simulate">
                            <input type="checkbox" checked={simulate} onChange={(e) => setSimulate(e.target.checked)} />
                            <span><b><i className="fa fa-flask" aria-hidden="true"></i> Simulate this payment</b>
                                <small>Your account is a test buyer: no money is charged and the order is marked TEST in the admin.</small></span>
                        </label>
                    )}
                    {unavailable && <p className="pz-note is-error">This payment option isn't available right now. Please try the other one or contact us.</p>}
                    {testOrder ? (
                        <div className="pz-testpay">
                            <p><b>{simulate ? 'Test buyer:' : 'Test mode:'}</b> {simulate ? 'nothing is charged' : 'no payment gateway is configured'}, so you can simulate the payment for order <code>{testOrder.orderId}</code>.</p>
                            <button type="button" className="pz-btn pz-btn--brand pz-btn--block" onClick={() => { setBusy(true); confirm({ orderId: testOrder.orderId, provider: 'test' }); }} disabled={busy}>
                                {busy && <i className="fa fa-circle-o-notch fa-spin" aria-hidden="true"></i>} Simulate successful payment
                            </button>
                        </div>
                    ) : (
                        <button type="submit" className="pz-btn pz-btn--brand pz-btn--lg pz-btn--block" disabled={busy || unavailable || totals.blocked} data-testid="pay">
                            {busy ? <i className="fa fa-circle-o-notch fa-spin" aria-hidden="true"></i> : <i className="fa fa-lock" aria-hidden="true"></i>}
                            {' '}{simulate && gateways.simulate ? 'Simulate' : 'Pay'} {domestic ? inr(totals.total) : `$${usd}`}
                        </button>
                    )}
                    <p className="pz-fine pz-center">Payments are processed securely by {domestic ? 'Razorpay' : 'PayPal'}. We never see your card details.</p>
                </Section>
            </form>

            <aside className={`pz-checkout__summary ${sumOpen ? 'is-open' : ''}`}>
                {summary}
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
                        <PurchaseStepper current={3} />
                        <CheckoutBody />
                    </main>
                </div>
            </CartProvider>
        </div>
    );
}
