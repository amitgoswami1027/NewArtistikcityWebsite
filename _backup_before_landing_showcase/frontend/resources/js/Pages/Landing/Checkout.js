import React, { useEffect, useRef, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import SiteLayout from '@/Components/Site/SiteLayout';
import { authPost } from '@/Components/Auth/api';
import axios from 'axios';

/*
 * Checkout of a landing page (/course/{slug}/enroll). The server decides the price, the coupon and which payment options
 * this page offers (props.choices, in the admin's order); this page only collects details, the chosen option and opens
 * the provider. Verification reuses the course checkout endpoints. /enroll/done shows success, pending or failure.
 */

const LOGO_TEXT = { upi: 'UPI', gpay: 'GPay', phonepe: 'PhonePe', paytm: 'Paytm', visa: 'VISA', mastercard: 'Mastercard', rupay: 'RuPay', paypal: 'PayPal' };
const ICON = { upi: 'fa-mobile', card: 'fa-credit-card', netbanking: 'fa-university', wallet: 'fa-google-wallet', emi: 'fa-calendar', paypal: 'fa-paypal' };

function loadRazorpay() {
    return new Promise((resolve, reject) => {
        if (window.Razorpay) return resolve(window.Razorpay);
        const s = document.createElement('script');
        s.src = 'https://checkout.razorpay.com/v1/checkout.js';
        s.onload = () => resolve(window.Razorpay);
        s.onerror = () => reject(new Error('Could not load the payment window. Check your connection and try again.'));
        document.body.appendChild(s);
    });
}

const track = (pageId, event, extra = {}) => {
    try { axios.post('/landing/e', { page: pageId, event, ...extra }).catch(() => {}); } catch (e) { /* never blocks */ }
};

function Summary({ course, quote, showCoupon, coupon, setCoupon, applyCoupon, couponMsg, busy, open, setOpen }) {
    return (
        <aside className={`lc-sum ${open ? 'is-open' : ''}`} aria-label="Order summary">
            <button type="button" className="lc-sum__toggle" onClick={() => setOpen(!open)} aria-expanded={open}>
                <span>Order summary</span><b>{quote.totalLabel}</b><i className={`fa fa-angle-${open ? 'up' : 'down'}`} aria-hidden="true"></i>
            </button>
            <div className="lc-sum__body">
                <div className="lc-sum__course">
                    {course.thumb && <img src={course.thumb} alt="" width="72" height="72" />}
                    <div>
                        <p className="lc-sum__title">{course.title}</p>
                        <p className="lc-sum__by">{course.kind}{course.artist ? ` · with ${course.artist}` : ''}</p>
                        {course.starts && <p className="lc-sum__by">Starts {course.starts}{course.duration ? ` · ${course.duration}` : ''}</p>}
                    </div>
                </div>
                <dl className="lc-lines">
                    {quote.listLabel && <div><dt>Course price</dt><dd><s>{quote.listLabel}</s></dd></div>}
                    {quote.offerLabel && <div className="is-good"><dt>{quote.offerLabel}</dt><dd>{quote.saving ? `−${quote.saving}` : ''}</dd></div>}
                    {quote.coupon && Number(quote.discount) > 0 && <div className="is-good"><dt>Coupon {quote.coupon}</dt><dd>−₹{Number(quote.discount).toLocaleString('en-IN')}</dd></div>}
                    {quote.taxMode === 'ADDED' && Number(quote.tax) > 0 && <div><dt>{quote.taxLabel}</dt><dd>₹{Number(quote.tax).toLocaleString('en-IN')}</dd></div>}
                    <div className="lc-lines__total"><dt>Total</dt><dd>{quote.totalLabel}</dd></div>
                </dl>
                {quote.taxMode !== 'ADDED' && Number(quote.tax) > 0 && <p className="lc-fine">Includes {quote.taxLabel}.</p>}
                {quote.offerEndsLabel && <p className="lc-fine">Offer price until {quote.offerEndsLabel} (IST).</p>}
                {showCoupon && quote.couponsEnabled && (
                    <form className="lc-coupon" onSubmit={(e) => { e.preventDefault(); applyCoupon(); }}>
                        <label htmlFor="lc-coupon" className="sr-only">Coupon code</label>
                        <input id="lc-coupon" value={coupon} onChange={(e) => setCoupon(e.target.value.toUpperCase().replace(/\s/g, ''))} placeholder="Coupon code"
                               autoComplete="off" maxLength={40} aria-describedby="lc-coupon-msg" data-testid="lc-coupon" />
                        <button type="submit" disabled={busy || !coupon} data-testid="lc-coupon-apply">Apply</button>
                        {couponMsg && <p id="lc-coupon-msg" className={`lc-coupon__msg ${couponMsg.ok ? 'is-ok' : 'is-err'}`} role={couponMsg.ok ? 'status' : 'alert'}>{couponMsg.text}</p>}
                    </form>
                )}
                <ul className="lc-trust">
                    <li><i className="fa fa-lock" aria-hidden="true"></i> Encrypted payment by our payment partners. We never see your card or UPI PIN.</li>
                    <li><i className="fa fa-envelope-o" aria-hidden="true"></i> Receipt and joining details by e-mail.</li>
                </ul>
            </div>
        </aside>
    );
}

function Result({ result, checkout, page, pollUrl }) {
    const [r, setR] = useState(result);
    const [tries, setTries] = useState(0);
    useEffect(() => {
        if (r.state !== 'pending' && r.state !== 'open') return undefined;
        if (tries > 30) return undefined;
        const t = setTimeout(() => {
            axios.get(pollUrl).then((x) => { setR(x.data); setTries((n) => n + 1); }).catch(() => setTries((n) => n + 1));
        }, 4000);
        return () => clearTimeout(t);
    }, [r, tries]); // eslint-disable-line react-hooks/exhaustive-deps
    const ok = r.state === 'success';
    const next = checkout.successRedirect || r.classroom;
    return (
        <div className={`lc-result is-${r.state}`} data-testid="lc-result" data-state={r.state} role="status" aria-live="polite">
            <span className="lc-result__icon" aria-hidden="true">
                <i className={`fa ${ok ? 'fa-check' : r.state === 'failed' ? 'fa-times' : r.state === 'pending' || r.state === 'open' ? 'fa-hourglass-half' : 'fa-info'}`}></i>
            </span>
            {ok && !r.duplicate && !r.soldout && (
                <>
                    <h1>You're in!</h1>
                    <p>{checkout.successMessage || 'Your payment is confirmed and your seat is booked. We have e-mailed your receipt and joining details.'}</p>
                    <p className="lc-fine">Order {r.number} · {r.amount}</p>
                    <div className="lc-result__ctas">
                        {next && <a className="lc-btn" href={next}>{checkout.successRedirect ? 'Continue' : 'Go to your course'}</a>}
                        <a className="lc-btn lc-btn--line" href="/dashboard">Open my studio</a>
                    </div>
                </>
            )}
            {ok && r.duplicate && (<><h1>You were already enrolled</h1><p>This payment will be refunded in full. Nothing else to do.</p><a className="lc-btn" href="/dashboard">Open my studio</a></>)}
            {r.state === 'success' && r.soldout && (<><h1>The course filled up</h1><p>Someone took the last seat while you were paying. Your payment will be refunded in full.</p></>)}
            {r.state === 'refund' && (<><h1>A refund is on its way</h1><p>{r.soldout ? 'The course filled up while you were paying.' : 'This payment is being refunded.'} You'll get an e-mail when it's done.</p></>)}
            {(r.state === 'pending' || r.state === 'open') && (
                <>
                    <h1>Confirming your payment…</h1>
                    <p>{checkout.pendingMessage || 'Some banks take a minute to confirm. You can keep this page open; we will also e-mail you as soon as it is confirmed.'}</p>
                    {tries > 30 && <p className="lc-fine">Still waiting. If money left your account, it will either be confirmed or refunded automatically. Order {r.number}.</p>}
                </>
            )}
            {r.state === 'failed' && (
                <>
                    <h1>The payment didn't go through</h1>
                    <p>{checkout.failureMessage || 'No money was taken. You can try again, perhaps with another payment method.'}</p>
                    <a className="lc-btn" href={`/course/${page.slug}/enroll`}>Try again</a>
                </>
            )}
            {r.state === 'refunded' && (<><h1>This order was refunded</h1><p>Order {r.number}.</p></>)}
        </div>
    );
}

export default function Checkout() {
    const { page, course, quote: initialQuote, choices, checkout, simulate, testMode, user, address, enrolled, notice, result } = usePage().props;
    const [quote, setQuote] = useState(initialQuote);
    const [form, setForm] = useState({
        name: user.name || '', phone: user.phone || '', address: (address && address.address) || '', city: (address && address.city) || '',
        state: (address && address.state) || '', zipcode: (address && address.zipcode) || '', country: (address && address.country) || 'India',
        terms: false, privacy: false,
    });
    const [errors, setErrors] = useState({});
    const [method, setMethod] = useState(choices.length ? choices[0].key : null);
    const [coupon, setCoupon] = useState('');
    const [couponMsg, setCouponMsg] = useState(null);
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState(notice === 'failed' ? { tone: 'error', text: 'The payment did not complete. No money was taken; please try again.' }
        : notice === 'cancelled' ? { tone: 'info', text: 'Payment cancelled. No money was taken.' } : null);
    const [test, setTest] = useState(null);
    const [orderId, setOrderId] = useState(null);
    const [sumOpen, setSumOpen] = useState(false);
    const methodRef = useRef(null);

    const set = (k) => (e) => setForm({ ...form, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value });
    const pick = (k) => { setMethod(k); track(page.id, 'METHOD', { method: k }); };

    const applyCoupon = async () => {
        setBusy(true);
        try {
            const r = await authPost(`/landing/checkout/${page.id}/quote`, { coupon });
            setQuote(r.quote);
            setCouponMsg(r.couponError ? { ok: false, text: r.couponError } : { ok: true, text: `Code ${coupon} applied.` });
        } catch (err) {
            setCouponMsg({ ok: false, text: err.message });
        }
        setBusy(false);
    };

    const pay = async (e, forceMethod) => {
        if (e) e.preventDefault();
        setBusy(true);
        setMessage(null);
        setErrors({});
        let oid = orderId;
        try {
            const o = await authPost(`/landing/checkout/${page.id}/order`, { ...form, coupon: quote.coupon || '' });
            oid = o.orderId;
            setOrderId(oid);
            setQuote(o.quote);
        } catch (err) {
            if (err.data && err.data.enrolled) { window.location.reload(); return; }
            setErrors(err.fieldErrors || {});
            setMessage({ tone: 'error', text: err.message });
            setBusy(false);
            const first = Object.keys(err.fieldErrors || {})[0];
            if (first) { const el = document.getElementById(`lc-${first}`); if (el) el.focus(); }
            return;
        }
        const done = (redirect) => {
            const flags = /duplicate=1/.test(redirect || '') ? '&duplicate=1' : /soldout=1/.test(redirect || '') ? '&soldout=1' : '';
            window.location.assign(`/course/${page.slug}/enroll/done?order_id=${oid}${flags}`);
        };
        try {
            const r = await authPost(`/landing/checkout/${page.id}/pay`, { orderId: oid, method: quote.free ? 'free' : (forceMethod || method) });
            if (r.gateway === 'done') { window.location.assign(r.redirect); return; }
            if (r.gateway === 'paypal') { window.location.assign(r.approveUrl); return; }
            if (r.gateway === 'test') { setTest({ ...r, orderId: oid }); setBusy(false); return; }
            const Razorpay = await loadRazorpay();
            const rzp = new Razorpay({
                key: r.key, amount: r.amount, currency: r.currency, order_id: r.razorpayOrderId, name: r.name, description: r.description,
                prefill: r.prefill || {}, theme: { color: '#9c0d48' }, config: r.config,
                handler: async (resp) => {
                    setBusy(true);
                    try {
                        const v = await authPost('/checkout/razorpay/verify', { orderId: oid, ...resp });
                        done(v.redirect);
                    } catch (err) {
                        setMessage({ tone: 'error', text: err.message });
                        setBusy(false);
                    }
                },
                modal: {
                    ondismiss: () => {
                        authPost(`/landing/checkout/${page.id}/failed`, { orderId: oid, reason: 'closed' }).catch(() => {});
                        setBusy(false);
                        setMessage({ tone: 'info', text: 'Payment window closed. No money was taken; you can try again any time.' });
                    },
                },
            });
            rzp.on('payment.failed', (resp) => {
                authPost(`/landing/checkout/${page.id}/failed`, { orderId: oid, reason: 'declined' }).catch(() => {});
                setMessage({ tone: 'error', text: (resp && resp.error && resp.error.description) || 'The payment failed. No money was taken. Please try again or choose another method.' });
            });
            rzp.open();
        } catch (err) {
            if (err.data && err.data.enrolled) { window.location.reload(); return; }
            if (err.data && err.data.repriced && err.data.quote) setQuote(err.data.quote);
            setMessage({ tone: 'error', text: err.message });
            setBusy(false);
        }
    };

    const completeTest = async () => {
        setBusy(true);
        try {
            const r = await authPost('/checkout/test-pay', { orderId: test.orderId });
            const flags = /duplicate=1/.test(r.redirect || '') ? '&duplicate=1' : /soldout=1/.test(r.redirect || '') ? '&soldout=1' : '';
            window.location.assign(`/course/${page.slug}/enroll/done?order_id=${test.orderId}${flags}`);
        } catch (err) {
            setMessage({ tone: 'error', text: err.message });
            setBusy(false);
        }
    };

    if (result) {
        return (
            <SiteLayout title="Your enrolment">
                <div className="lc lc--result">
                    <Result result={result} checkout={checkout} page={page} pollUrl={`/landing/checkout/order/${result.orderId}/status`} />
                </div>
            </SiteLayout>
        );
    }

    const choice = choices.find((c) => c.key === method);
    const canPay = quote.free || (method && choice) || simulate;
    return (
        <SiteLayout title={`Enrol · ${course.title}`}>
            <div className="lc" data-testid="lc-checkout">
                <nav className="lc-crumbs" aria-label="Breadcrumb"><a href={`/course/${page.slug}`}><i className="fa fa-angle-left" aria-hidden="true"></i> Back to the course</a></nav>
                <h1 className="lc-h1">Enrol in {course.title}</h1>
                {enrolled ? (
                    <div className="lc-note is-ok" role="status">You're already enrolled in this course. <a href={enrolled.classroom}>Go to your course</a></div>
                ) : (
                    <div className="lc-grid">
                        <Summary course={course} quote={quote} showCoupon={checkout.showCoupon} coupon={coupon} setCoupon={setCoupon} applyCoupon={applyCoupon}
                                 couponMsg={couponMsg} busy={busy} open={sumOpen} setOpen={setSumOpen} />
                        <form className="lc-main" onSubmit={pay} noValidate>
                            {message && <div className={`lc-note is-${message.tone}`} role={message.tone === 'error' ? 'alert' : 'status'}>{message.text}</div>}
                            <section className="lc-card" aria-labelledby="lc-s1">
                                <h2 id="lc-s1"><span>1</span> Your details</h2>
                                <div className="lc-fields">
                                    <label className="lc-field" htmlFor="lc-name"><span>Full name</span>
                                        <input id="lc-name" value={form.name} onChange={set('name')} autoComplete="name" aria-invalid={!!errors.name} aria-describedby={errors.name ? 'lc-name-e' : undefined} required />
                                        {errors.name && <small id="lc-name-e" className="lc-err">{errors.name}</small>}
                                    </label>
                                    <label className="lc-field" htmlFor="lc-email"><span>E-mail</span>
                                        <input id="lc-email" value={user.email} readOnly aria-readonly="true" />
                                        <small className="lc-hint">Your receipt goes here.</small>
                                    </label>
                                    <label className="lc-field" htmlFor="lc-phone"><span>Phone {checkout.requirePhone ? '' : <em>(optional)</em>}</span>
                                        <input id="lc-phone" value={form.phone} onChange={set('phone')} autoComplete="tel" inputMode="tel" placeholder="+91 98765 43210"
                                               aria-invalid={!!errors.phone} aria-describedby={errors.phone ? 'lc-phone-e' : undefined} required={checkout.requirePhone} />
                                        {errors.phone && <small id="lc-phone-e" className="lc-err">{errors.phone}</small>}
                                    </label>
                                    {checkout.requireAddress && (
                                        <>
                                            <label className="lc-field lc-field--wide" htmlFor="lc-address"><span>Address</span>
                                                <input id="lc-address" value={form.address} onChange={set('address')} autoComplete="street-address" aria-invalid={!!errors.address} required />
                                                {errors.address && <small className="lc-err">{errors.address}</small>}
                                            </label>
                                            <label className="lc-field" htmlFor="lc-city"><span>City</span>
                                                <input id="lc-city" value={form.city} onChange={set('city')} autoComplete="address-level2" aria-invalid={!!errors.city} required />
                                                {errors.city && <small className="lc-err">{errors.city}</small>}
                                            </label>
                                            <label className="lc-field" htmlFor="lc-zipcode"><span>PIN / postal code</span>
                                                <input id="lc-zipcode" value={form.zipcode} onChange={set('zipcode')} autoComplete="postal-code" aria-invalid={!!errors.zipcode} required />
                                                {errors.zipcode && <small className="lc-err">{errors.zipcode}</small>}
                                            </label>
                                            <label className="lc-field" htmlFor="lc-state"><span>State <em>(optional)</em></span>
                                                <input id="lc-state" value={form.state} onChange={set('state')} autoComplete="address-level1" />
                                            </label>
                                            <label className="lc-field" htmlFor="lc-country"><span>Country</span>
                                                <input id="lc-country" value={form.country} onChange={set('country')} autoComplete="country-name" />
                                            </label>
                                        </>
                                    )}
                                </div>
                            </section>

                            {!quote.free && (
                                <section className="lc-card" aria-labelledby="lc-s2">
                                    <h2 id="lc-s2"><span>2</span> How would you like to pay?</h2>
                                    {choices.length === 0 && !simulate && <p className="lc-note is-error">Online payment isn't available for this course right now. Please contact us and we'll help you enrol.</p>}
                                    <div className="lc-methods" role="radiogroup" aria-label="Payment method" ref={methodRef}>
                                        {choices.map((c) => (
                                            <label key={c.key} className={`lc-method ${method === c.key ? 'is-on' : ''}`} data-method={c.key}>
                                                <input type="radio" name="method" value={c.key} checked={method === c.key} onChange={() => pick(c.key)} />
                                                <i className={`fa ${ICON[c.key] || 'fa-credit-card'} lc-method__icon`} aria-hidden="true"></i>
                                                <span className="lc-method__text"><b>{c.label}</b><small>{c.hint}</small></span>
                                                <span className="lc-method__end">
                                                    <span className="lc-logos" aria-hidden="true">{c.logos.slice(0, 4).map((l) => <span key={l} className={`lc-logo lc-logo--${l}`}>{LOGO_TEXT[l] || l}</span>)}</span>
                                                    <b className="lc-method__amt">{c.amount}</b>
                                                </span>
                                            </label>
                                        ))}
                                    </div>
                                    {testMode && <p className="lc-fine"><i className="fa fa-flask" aria-hidden="true"></i> Test mode: no real payment is taken on this server.</p>}
                                </section>
                            )}

                            <section className="lc-card lc-card--pay" aria-label="Confirm">
                                {checkout.requireTerms && (
                                    <label className="lc-check" htmlFor="lc-terms">
                                        <input id="lc-terms" type="checkbox" checked={form.terms} onChange={set('terms')} aria-invalid={!!errors.terms} data-testid="lc-terms" />
                                        <span>I accept the <a href="/terms-conditions" target="_blank" rel="noopener">terms of use</a>{checkout.requirePrivacy ? '' : <> and the <a href="/privacy-policy" target="_blank" rel="noopener">privacy policy</a></>}.</span>
                                    </label>
                                )}
                                {errors.terms && <small className="lc-err">{errors.terms}</small>}
                                {checkout.requirePrivacy && (
                                    <label className="lc-check" htmlFor="lc-privacy">
                                        <input id="lc-privacy" type="checkbox" checked={form.privacy} onChange={set('privacy')} aria-invalid={!!errors.privacy} data-testid="lc-privacy" />
                                        <span>I agree that ArtistikCity uses my details to run this course, as set out in the <a href="/privacy-policy" target="_blank" rel="noopener">privacy policy</a>.</span>
                                    </label>
                                )}
                                {errors.privacy && <small className="lc-err">{errors.privacy}</small>}
                                {test ? (
                                    <div className="lc-test" data-testid="lc-test">
                                        <p><b>Test payment</b> · {test.simulated ? 'You are a test buyer: this payment is simulated and marked as a test order.' : 'No gateway keys on this server: complete a simulated payment.'}</p>
                                        <div className="lc-row">
                                            <button type="button" className="lc-btn" onClick={completeTest} disabled={busy} data-testid="lc-test-complete">Complete test payment</button>
                                            <button type="button" className="lc-btn lc-btn--line" onClick={() => { setTest(null); authPost(`/landing/checkout/${page.id}/failed`, { orderId: test.orderId, reason: 'declined' }).catch(() => {}); setMessage({ tone: 'error', text: 'The payment failed. No money was taken.' }); }}>Make it fail</button>
                                        </div>
                                    </div>
                                ) : (
                                    <div className="lc-paybar">
                                        <button type="submit" className="lc-btn lc-btn--pay" disabled={busy || !canPay} data-testid="lc-pay">
                                            {busy ? <i className="fa fa-circle-o-notch fa-spin" aria-hidden="true"></i> : <i className="fa fa-lock" aria-hidden="true"></i>}
                                            {quote.free ? ' Enrol for free' : ` Pay ${choice ? choice.amount : quote.totalLabel}`}
                                        </button>
                                        {simulate && !quote.free && (
                                            <button type="button" className="lc-btn lc-btn--line" disabled={busy} onClick={() => pay(null, 'test')} data-testid="lc-simulate">Simulate payment (test buyer)</button>
                                        )}
                                    </div>
                                )}
                                <p className="lc-fine lc-center">You'll confirm the payment on {choice && choice.currency === 'USD' ? "PayPal's" : 'the payment partner\'s'} secure page.</p>
                            </section>
                        </form>
                    </div>
                )}
            </div>
        </SiteLayout>
    );
}
