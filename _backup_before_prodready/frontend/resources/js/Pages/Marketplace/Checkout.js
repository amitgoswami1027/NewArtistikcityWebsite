import React, { useEffect, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import axios from 'axios';
import SiteLayout from '@/Components/Site/SiteLayout';
import { useCountdown, mmss, inr, usd, dims, dimsCm, hasDiscount, pct, errorText, Spinner } from '@/Components/Marketplace/shared';

const COUNTRIES = ['India', 'United States', 'United Kingdom', 'Canada', 'Australia', 'United Arab Emirates', 'Singapore', 'Germany', 'France', 'Netherlands',
    'Italy', 'Spain', 'Switzerland', 'Sweden', 'Ireland', 'New Zealand', 'Japan', 'Hong Kong', 'Malaysia', 'Saudi Arabia', 'Qatar', 'South Africa', 'Other'];

function loadRazorpay() {
    return new Promise((resolve, reject) => {
        if (window.Razorpay) return resolve(window.Razorpay);
        const s = document.createElement('script');
        s.src = 'https://checkout.razorpay.com/v1/checkout.js';
        s.onload = () => resolve(window.Razorpay);
        s.onerror = () => reject(new Error('Could not load Razorpay'));
        document.body.appendChild(s);
    });
}

function Field({ id, label, error, hint, children, className = '' }) {
    return (
        <div className={className}>
            <label htmlFor={id} className="block text-sm font-bold text-ink">{label}</label>
            <div className="mt-1.5">{children}</div>
            {hint && !error && <p className="mt-1 text-xs text-gray-500">{hint}</p>}
            {error && <p id={`${id}-err`} className="mt-1 text-xs font-semibold text-red-600">{error}</p>}
        </div>
    );
}
const input = (err) => `w-full h-12 rounded-xl text-base border bg-white px-4 ${err ? 'border-red-400 focus:border-red-500 focus:ring-red-500' : 'border-gray-300 focus:border-brand focus:ring-brand'}`;

function HoldTimer({ left }) {
    const urgent = left <= 120;
    return (
        <div className={`sticky z-30 ${urgent ? 'bg-red-600' : 'bg-ink'} text-white transition-colors`} style={{ top: 68 }} role="timer" aria-live={urgent ? 'assertive' : 'off'}>
            <div className="ac-wide h-14 flex items-center justify-center gap-3 text-sm sm:text-base">
                <i className={`fa fa-lock ${urgent ? '' : 'text-pink-300'}`} aria-hidden="true"></i>
                <span><strong>Held exclusively for you.</strong> <span className="hidden sm:inline">This 1-of-1 original will be released in</span><span className="sm:hidden">Released in</span></span>
                <span className="font-mono font-black text-lg tabular-nums">{mmss(left)}</span>
                <span className="hidden md:inline text-gray-300">min</span>
            </div>
        </div>
    );
}

export default function MarketplaceCheckout() {
    const { painting: p, expiresAt, serverNow, usdPrice, inrPerUsd, gateways = {}, customer, payment } = usePage().props;
    const [expiry, setExpiry] = useState(expiresAt);
    const [skewNow, setSkewNow] = useState(serverNow);
    const left = useCountdown(expiry, skewNow);
    const [tab, setTab] = useState('DOMESTIC');
    const [f, setF] = useState({
        name: (customer && customer.name) || '', email: (customer && customer.email) || '', phone: (customer && customer.phone) || '',
        line1: '', line2: '', city: '', state: '', postal: '', country: 'India',
    });
    const [errors, setErrors] = useState({});
    const [busy, setBusy] = useState(false);
    const [notice, setNotice] = useState(payment === 'failed' ? { type: 'error', text: 'Your PayPal payment did not complete. No money was taken. You can try again while your hold lasts.' }
        : payment === 'cancelled' ? { type: 'info', text: 'You cancelled the PayPal payment. Your hold is still active.' } : null);
    const [testOrder, setTestOrder] = useState(null);
    const [again, setAgain] = useState(false);

    const set = (k, v) => setF((x) => ({ ...x, [k]: v }));
    const switchTab = (t) => {
        setTab(t);
        if (t === 'DOMESTIC') set('country', 'India');
        else if (f.country === 'India') set('country', 'United States');
    };
    useEffect(() => { if (f.country === 'India' && tab !== 'DOMESTIC') setTab('DOMESTIC'); if (f.country !== 'India' && tab !== 'INTERNATIONAL') setTab('INTERNATIONAL'); }, [f.country]);

    const validate = () => {
        const e = {};
        if (f.name.trim().length < 2) e.name = 'Enter your full name.';
        if (!/^[^@\s]+@[^@\s]+\.[^@\s]{2,}$/.test(f.email.trim())) e.email = 'Enter a valid email for your receipt.';
        if (tab === 'DOMESTIC' && !/^\d{10,13}$/.test(f.phone.replace(/\D/g, ''))) e.phone = 'Enter a 10-digit mobile number for delivery updates.';
        if (f.line1.trim().length < 3) e.line1 = 'Enter the street address.';
        if (f.city.trim().length < 2) e.city = 'Enter the city.';
        if (tab === 'DOMESTIC' && !/^\d{6}$/.test(f.postal.trim())) e.postal = 'Enter the 6-digit PIN code.';
        if (tab !== 'DOMESTIC' && (f.postal.trim().length < 3 || f.postal.trim().length > 12)) e.postal = 'Enter the postal code.';
        setErrors(e);
        return Object.keys(e).length === 0;
    };

    const confirm = (payload) => axios.post('/api/v1/marketplace/confirm', payload)
        .then((r) => { window.location.href = r.data.redirect; })
        .catch((e) => {
            const d = e.response && e.response.data;
            if (d && d.redirect) { window.location.href = d.redirect; return; }
            setBusy(false);
            setNotice({ type: 'error', text: errorText(e, 'We could not confirm your payment.') });
        });

    const pay = (ev) => {
        ev.preventDefault();
        setNotice(null);
        if (!validate()) { setNotice({ type: 'error', text: 'Please fix the highlighted fields.' }); window.scrollTo({ top: 0, behavior: 'smooth' }); return; }
        setBusy(true);
        axios.post('/api/v1/marketplace/checkout', { paintingId: p.id, gateway: tab === 'DOMESTIC' ? 'RAZORPAY' : 'PAYPAL', ...f })
            .then(async (r) => {
                const d = r.data;
                if (d.expiresAt) { setExpiry(d.expiresAt); setSkewNow(d.serverNow); }
                if (d.provider === 'test') { setTestOrder(d); setBusy(false); return; }
                if (d.provider === 'paypal') { window.location.href = d.approveUrl; return; }
                const Razorpay = await loadRazorpay();
                const rzp = new Razorpay({
                    key: d.key, amount: d.amountPaise, currency: 'INR', order_id: d.razorpayOrderId,
                    name: 'ArtistikCity', description: d.description, image: '/assets/images/logo.png',
                    prefill: d.prefill, theme: { color: '#e5156b' }, notes: { marketplace_order_id: d.orderId },
                    handler: (resp) => confirm({ orderId: d.orderId, provider: 'razorpay', ...resp }),
                    modal: { ondismiss: () => { setBusy(false); setNotice({ type: 'info', text: 'Payment window closed. Your hold is still active, so you can try again.' }); } },
                });
                rzp.on('payment.failed', (resp) => { setBusy(false); setNotice({ type: 'error', text: (resp.error && resp.error.description) || 'The payment failed. No money was taken.' }); });
                rzp.open();
            })
            .catch((e) => {
                setBusy(false);
                const d = e.response && e.response.data;
                if (d && d.code === 'HOLD_EXPIRED') { setExpiry(Date.now() - 1); return; }
                setNotice({ type: 'error', text: errorText(e) });
            });
    };

    const reserveAgain = () => {
        setAgain(true);
        axios.post(`/api/v1/marketplace/paintings/${p.id}/reserve`, {})
            .then((r) => { setExpiry(r.data.expiresAt); setSkewNow(r.data.serverNow); setAgain(false); setNotice({ type: 'info', text: 'Welcome back, it\'s held for you again for 15 minutes.' }); })
            .catch((e) => { setAgain(false); setNotice({ type: 'error', text: errorText(e, 'Sorry, this piece is no longer available.') }); setTimeout(() => { window.location.href = `/marketplace/${p.slug}`; }, 2500); });
    };

    const unavailable = tab === 'DOMESTIC' ? !gateways.razorpay && !gateways.testMode : !gateways.paypal && !gateways.testMode;
    const expired = left <= 0;

    return (
        <SiteLayout title={`Checkout · ${p.title}`}>
            <div className="tw">
                {!expired && <HoldTimer left={left} />}
                <div className="ac-wide pt-6">
                    <nav aria-label="Breadcrumb" className="text-sm text-gray-500">
                        <a href="/marketplace" className="hover:text-ink">Marketplace</a><span className="mx-2">/</span>
                        <a href={`/marketplace/${p.slug}`} className="hover:text-ink">{p.title}</a><span className="mx-2">/</span>
                        <span className="text-ink font-semibold">Secure checkout</span>
                    </nav>
                    <h1 className="mt-4 text-3xl sm:text-4xl font-black tracking-tight text-ink">Secure checkout</h1>
                </div>

                <form onSubmit={pay} noValidate className="ac-wide py-8 grid grid-cols-1 lg:grid-cols-12 gap-10">
                    <div className="lg:col-span-7 space-y-8">
                        {notice && (
                            <div className={`rounded-xl border p-4 text-sm font-semibold ${notice.type === 'error' ? 'bg-red-50 border-red-200 text-red-800' : 'bg-blue-50 border-blue-200 text-blue-900'}`} role="alert">{notice.text}</div>
                        )}

                        <fieldset className="rounded-2xl border border-gray-200 bg-white p-6 sm:p-8">
                            <legend className="px-2 text-lg font-extrabold text-ink">1 · Contact</legend>
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
                                <Field id="name" label="Full name" error={errors.name}><input id="name" autoComplete="name" value={f.name} onChange={(e) => set('name', e.target.value)} className={input(errors.name)} aria-invalid={!!errors.name} /></Field>
                                <Field id="email" label="Email" error={errors.email} hint="Your receipt and tracking go here."><input id="email" type="email" autoComplete="email" value={f.email} onChange={(e) => set('email', e.target.value)} className={input(errors.email)} aria-invalid={!!errors.email} /></Field>
                                <Field id="phone" label={tab === 'DOMESTIC' ? 'Mobile number' : 'Phone (optional)'} error={errors.phone} className="sm:col-span-2"><input id="phone" type="tel" autoComplete="tel" value={f.phone} onChange={(e) => set('phone', e.target.value)} className={input(errors.phone)} aria-invalid={!!errors.phone} /></Field>
                            </div>
                        </fieldset>

                        <fieldset className="rounded-2xl border border-gray-200 bg-white p-6 sm:p-8">
                            <legend className="px-2 text-lg font-extrabold text-ink">2 · Delivery address</legend>
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
                                <Field id="country" label="Country" className="sm:col-span-2">
                                    <select id="country" autoComplete="country-name" value={f.country} onChange={(e) => set('country', e.target.value)} className={input(false)}>
                                        {COUNTRIES.map((c) => <option key={c}>{c}</option>)}
                                    </select>
                                </Field>
                                <Field id="line1" label="Street address" error={errors.line1} className="sm:col-span-2"><input id="line1" autoComplete="address-line1" value={f.line1} onChange={(e) => set('line1', e.target.value)} className={input(errors.line1)} aria-invalid={!!errors.line1} /></Field>
                                <Field id="line2" label="Apartment, floor, landmark (optional)" className="sm:col-span-2"><input id="line2" autoComplete="address-line2" value={f.line2} onChange={(e) => set('line2', e.target.value)} className={input(false)} /></Field>
                                <Field id="city" label="City" error={errors.city}><input id="city" autoComplete="address-level2" value={f.city} onChange={(e) => set('city', e.target.value)} className={input(errors.city)} aria-invalid={!!errors.city} /></Field>
                                <Field id="state" label={tab === 'DOMESTIC' ? 'State' : 'State / region'}><input id="state" autoComplete="address-level1" value={f.state} onChange={(e) => set('state', e.target.value)} className={input(false)} /></Field>
                                <Field id="postal" label={tab === 'DOMESTIC' ? 'PIN code' : 'Postal code'} error={errors.postal}><input id="postal" autoComplete="postal-code" inputMode={tab === 'DOMESTIC' ? 'numeric' : 'text'} value={f.postal} onChange={(e) => set('postal', e.target.value)} className={input(errors.postal)} aria-invalid={!!errors.postal} /></Field>
                            </div>
                        </fieldset>

                        <fieldset className="rounded-2xl border border-gray-200 bg-white p-6 sm:p-8">
                            <legend className="px-2 text-lg font-extrabold text-ink">3 · Payment</legend>
                            <div className="grid grid-cols-2 rounded-xl bg-gray-100 p-1" role="tablist" aria-label="Where are you paying from?">
                                {[['DOMESTIC', 'India', 'UPI · Cards · NetBanking'], ['INTERNATIONAL', 'International', 'PayPal · USD']].map(([k, l, s]) => (
                                    <button key={k} type="button" role="tab" aria-selected={tab === k} onClick={() => switchTab(k)}
                                            className={`rounded-lg py-3 px-2 text-center ${tab === k ? 'bg-white shadow text-ink' : 'text-gray-500 hover:text-ink'}`}>
                                        <span className="block font-extrabold">{l}</span><span className="block text-xs">{s}</span>
                                    </button>
                                ))}
                            </div>
                            {tab === 'DOMESTIC' ? (
                                <div className="mt-5">
                                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                                        {[['fa-mobile', 'UPI'], ['fa-google', 'Google Pay'], ['fa-mobile', 'PhonePe'], ['fa-university', 'NetBanking']].map(([i, l]) => (
                                            <div key={l} className="rounded-xl border border-gray-200 py-3 text-center text-sm font-bold text-ink"><i className={`fa ${i} block text-xl text-brand mb-1`} aria-hidden="true"></i>{l}</div>
                                        ))}
                                    </div>
                                    <p className="mt-4 text-sm text-gray-600"><i className="fa fa-lock mr-1" aria-hidden="true"></i>You'll choose UPI, a card or your bank in Razorpay's secure window. ArtistikCity never sees your card or UPI PIN.</p>
                                </div>
                            ) : (
                                <div className="mt-5">
                                    <div className="rounded-xl border border-gray-200 p-4 flex items-center gap-4">
                                        <i className="fa fa-paypal text-3xl" style={{ color: '#003087' }} aria-hidden="true"></i>
                                        <div className="text-sm text-gray-700"><p className="font-bold text-ink">Pay {usd(usdPrice)} with PayPal</p><p>Use your PayPal balance or any major card. You'll approve the payment on PayPal and come straight back.</p></div>
                                    </div>
                                    <p className="mt-3 text-xs text-gray-500">Converted at ₹{Number(inrPerUsd).toFixed(2)} = $1. Import duties and taxes, if any, are paid on delivery.</p>
                                </div>
                            )}
                            {unavailable && <p className="mt-4 rounded-xl bg-yellow-50 border border-yellow-200 p-3 text-sm text-yellow-900">This payment option is temporarily unavailable. Please try the other tab or <a href="/contact" className="underline font-bold">contact us</a>.</p>}
                        </fieldset>

                        {testOrder ? (
                            <div className="rounded-2xl border-2 border-dashed border-brand bg-brand-soft p-6" role="region" aria-label="Test payment">
                                <p className="font-mono text-xs uppercase tracking-widest text-brand">Test mode · no real money</p>
                                <p className="mt-2 font-bold text-ink">Order {testOrder.orderId} is waiting for payment of {testOrder.currency === 'INR' ? inr(testOrder.amount) : usd(testOrder.amount)}.</p>
                                <p className="mt-1 text-sm text-gray-700">Payment keys aren't configured on this server, so you can simulate the gateway's success callback.</p>
                                <button type="button" onClick={() => { setBusy(true); confirm({ orderId: testOrder.orderId, provider: 'test' }); }} disabled={busy}
                                        className="mt-4 h-12 px-6 rounded-full bg-ink text-white font-bold disabled:opacity-60">{busy ? <Spinner /> : 'Simulate successful payment'}</button>
                            </div>
                        ) : (
                            <button type="submit" disabled={busy || expired || unavailable}
                                    className="w-full flex items-center justify-center gap-2 h-16 rounded-full bg-brand text-white text-lg font-bold hover:bg-brand-dark disabled:opacity-50">
                                {busy ? <Spinner /> : <i className="fa fa-lock" aria-hidden="true"></i>}
                                {tab === 'DOMESTIC' ? `Pay ${inr(p.final_price)} securely` : `Continue to PayPal · ${usd(usdPrice)}`}
                            </button>
                        )}
                        <p className="text-center text-xs text-gray-500">By paying you agree to our <a href="/terms-conditions" className="underline">terms</a>. Your hold covers the payment window; it's topped up for 5 minutes once you start paying.</p>
                    </div>

                    {/* ---------- summary ---------- */}
                    <aside className="lg:col-span-5">
                        <div className="lg:sticky lg:top-36 rounded-2xl border border-gray-200 bg-white overflow-hidden">
                            <div className="bg-gray-50 p-6 flex items-center justify-center"><img src={p.image_url} alt={p.title} className="mk-art max-h-72 w-auto" /></div>
                            <div className="p-6">
                                <p className="font-mono text-xs uppercase tracking-widest text-gray-500">1-of-1 original</p>
                                <h2 className="mt-1 text-xl font-extrabold text-ink">{p.title}</h2>
                                <p className="text-sm text-gray-600">{p.artist_name} · {p.medium} · {dims(p)} ({dimsCm(p)})</p>
                                <dl className="mt-5 space-y-2 text-sm">
                                    {hasDiscount(p) && <div className="flex justify-between"><dt className="text-gray-500">Original price</dt><dd className="line-through text-gray-400">{inr(p.base_price)}</dd></div>}
                                    {hasDiscount(p) && <div className="flex justify-between"><dt className="text-gray-500">Studio markdown (-{pct(p)})</dt><dd className="text-green-700 font-semibold">-{inr(p.base_price - p.final_price)}</dd></div>}
                                    <div className="flex justify-between"><dt className="text-gray-500">Insured shipping</dt><dd className="font-semibold text-ink">{tab === 'DOMESTIC' ? 'Free' : 'Included'}</dd></div>
                                    <div className="flex justify-between pt-3 border-t border-gray-100 text-lg"><dt className="font-extrabold text-ink">Total</dt><dd className="font-black text-ink">{tab === 'DOMESTIC' ? inr(p.final_price) : `${usd(usdPrice)}`}</dd></div>
                                    {tab !== 'DOMESTIC' && <div className="text-right text-xs text-gray-500">≈ {inr(p.final_price)}</div>}
                                </dl>
                                <ul className="mt-6 pt-5 border-t border-gray-100 space-y-2 text-sm text-gray-700">
                                    {p.has_certificate && <li><i className="fa fa-certificate text-brand mr-2" aria-hidden="true"></i>Certificate of authenticity included</li>}
                                    <li><i className="fa fa-cube text-brand mr-2" aria-hidden="true"></i>{p.framing}</li>
                                    <li><i className="fa fa-truck text-brand mr-2" aria-hidden="true"></i>Ships in 2–3 working days, tracked and insured</li>
                                </ul>
                            </div>
                        </div>
                    </aside>
                </form>

                {expired && (
                    <div className="fixed inset-0 z-50 bg-black bg-opacity-60 flex items-center justify-center p-4" role="alertdialog" aria-modal="true" aria-labelledby="exp-title">
                        <div className="w-full max-w-md rounded-2xl bg-white p-8 text-center shadow-2xl">
                            <span className="inline-flex w-14 h-14 rounded-full bg-yellow-100 text-yellow-700 items-center justify-center text-2xl"><i className="fa fa-hourglass-end" aria-hidden="true"></i></span>
                            <h2 id="exp-title" className="mt-4 text-2xl font-black text-ink">Your hold has ended</h2>
                            <p className="mt-2 text-gray-600">“{p.title}” went back to the gallery so other collectors can see it. If nobody has taken it, you can reserve it again right now.</p>
                            <button type="button" onClick={reserveAgain} disabled={again} className="mt-6 w-full h-12 rounded-full bg-brand text-white font-bold hover:bg-brand-dark disabled:opacity-60">{again ? <Spinner /> : 'Reserve it again'}</button>
                            <a href="/marketplace" className="mt-2 block h-12 leading-[3rem] rounded-full font-bold text-gray-600 hover:text-ink">Back to the marketplace</a>
                        </div>
                    </div>
                )}
            </div>
        </SiteLayout>
    );
}
