import React, { useEffect, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import axios from 'axios';
import { CheckoutShell } from '@/Components/Commission/CommissionLayout';
import OrderSummary from '@/Components/Commission/OrderSummary';
import TextureCanvas from '@/Components/Commission/TextureCanvas';
import { useCommission, isDigital, quote, usd, inr } from '@/Components/Commission/CommissionStore';
import useStepGuard, { go } from '@/Components/Commission/useStepGuard';

const METHODS = [
    { id: 'RAZORPAY_UPI', group: 'India', title: 'UPI & NetBanking', blurb: 'Google Pay, PhonePe, Paytm, BHIM or any bank. Powered by Razorpay.', icon: 'fa-mobile' },
    { id: 'CREDIT_CARD', group: 'India', title: 'Credit / debit card', blurb: 'Visa, Mastercard, RuPay and more, entered on Razorpay’s secure form.', icon: 'fa-credit-card' },
    { id: 'PAYPAL', group: 'International', title: 'PayPal', blurb: 'Pay in USD with your PayPal balance or card.', icon: 'fa-paypal' },
];

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

function Field({ id, label, error, children, className = '' }) {
    return (
        <div className={className}>
            <label htmlFor={id} className="block text-sm font-bold text-ink">{label}</label>
            <div className="mt-1.5">{children}</div>
            {error && <p id={`${id}-error`} className="mt-1 text-xs font-semibold text-red-600">{error}</p>}
        </div>
    );
}

const inputCls = (err) => `w-full h-11 rounded-lg text-sm ${err ? 'border-red-400 focus:border-red-500 focus:ring-red-500' : 'border-gray-300 focus:border-brand focus:ring-brand'}`;

function CheckoutForm() {
    const { pricing, gateways = {}, customer } = usePage().props;
    const state = useStepGuard(6);
    const { update, reset } = useCommission();
    const digital = isDigital(state);
    const q = quote(state, pricing);
    const currency = state.paymentMethod === 'PAYPAL' ? 'USD' : 'INR';
    const [errors, setErrors] = useState({});
    const [busy, setBusy] = useState(false);
    const [notice, setNotice] = useState(null);
    const [testOrder, setTestOrder] = useState(null);

    useEffect(() => {
        if (customer && !state.contact.name && !state.contact.email) {
            update({ contact: { ...state.contact, name: customer.name || '', email: customer.email || '', phone: customer.phone || '' } });
        }
        const params = new URLSearchParams(window.location.search);
        if (params.get('payment') === 'failed') setNotice({ type: 'error', text: 'Your payment was not completed. No money was taken. Please try again.' });
        if (params.get('cancelled')) setNotice({ type: 'info', text: 'You cancelled the PayPal payment. Choose a method to try again.' });
    }, []);

    const setContact = (k, v) => update((s) => ({ contact: { ...s.contact, [k]: v } }));
    const setShipping = (k, v) => update((s) => ({ shipping: { ...s.shipping, [k]: v } }));

    const validate = () => {
        const e = {};
        const c = state.contact, s = state.shipping;
        if (!c.name || c.name.trim().length < 2) e.name = 'Enter your full name.';
        if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(c.email || '')) e.email = 'Enter a valid email address.';
        if (c.phone && !/^[0-9+()\-\s]{6,20}$/.test(c.phone)) e.phone = 'Enter a valid phone number.';
        if (!digital) {
            if (!s.line1.trim()) e.line1 = 'Enter your street address.';
            if (!s.city.trim()) e.city = 'Enter your city.';
            if (!s.postalCode.trim()) e.postalCode = 'Enter your postal code.';
            if (!s.country.trim()) e.country = 'Enter your country.';
        }
        setErrors(e);
        return Object.keys(e).length === 0;
    };

    const finish = (redirect) => { reset(); window.location.href = redirect; };

    const confirm = (payload) => axios.post('/api/commissions/confirm', payload)
        .then((r) => finish(r.data.redirect))
        .catch((err) => {
            setBusy(false);
            setNotice({ type: 'error', text: (err.response && err.response.data && err.response.data.message) || 'We could not confirm your payment.' });
        });

    const pay = (e) => {
        e.preventDefault();
        setNotice(null);
        if (!validate()) { setNotice({ type: 'error', text: 'Please fix the highlighted fields.' }); return; }
        setBusy(true);
        const payload = {
            theme: state.theme, fulfillmentType: state.fulfillmentType, size: state.size, orientation: state.orientation,
            frameMaterial: state.frameMaterial, hasMatting: state.hasMatting, photoUrl: state.photoUrl, instructions: state.instructions,
            contact: state.contact, shipping: digital ? null : state.shipping, paymentMethod: state.paymentMethod,
        };
        axios.post('/api/commissions/initialize-checkout', payload).then(async (r) => {
            const d = r.data;
            if (d.provider === 'test') {
                setTestOrder(d);
                setBusy(false);
            } else if (d.provider === 'paypal') {
                window.location.href = d.approveUrl;
            } else if (d.provider === 'razorpay') {
                const Razorpay = await loadRazorpay();
                const rzp = new Razorpay({
                    key: d.key, amount: d.amount, currency: 'INR', order_id: d.razorpayOrderId,
                    name: 'ArtistikCity', description: `Custom commission ${d.orderId}`, image: '/favicon.png',
                    prefill: { ...d.prefill, method: d.method }, theme: { color: '#e5156b' },
                    handler: (resp) => confirm({ orderId: d.orderId, provider: 'razorpay', ...resp }),
                    modal: { ondismiss: () => { setBusy(false); setNotice({ type: 'info', text: 'Payment window closed. Your order is saved; you can try again.' }); } },
                });
                rzp.on('payment.failed', (resp) => {
                    setBusy(false);
                    setNotice({ type: 'error', text: (resp.error && resp.error.description) || 'The payment failed. Please try again.' });
                });
                rzp.open();
            }
        }).catch((err) => {
            setBusy(false);
            const data = err.response && err.response.data;
            setNotice({ type: 'error', text: (data && data.message) || 'Something went wrong. Please try again.' });
        });
    };

    const payLabel = q ? `🔒 Securely Confirm and Pay Total: ${currency === 'INR' ? inr(q.totalInr) : usd(q.totalUsd)}` : 'Securely Confirm and Pay';
    const unavailable = (m) => !gateways.testMode && (m === 'PAYPAL' ? !gateways.paypal : !gateways.razorpay);

    return (
        <form onSubmit={pay} noValidate className="grid grid-cols-1 lg:grid-cols-2 gap-8 items-start">
            {/* ---------- Column A: logistics ---------- */}
            <div className="space-y-6">
                <button type="button" onClick={() => go('/commission/preview')} className="text-sm font-bold text-gray-600 hover:text-ink">{'⬅'} Back to review</button>
                <section className="rounded-2xl border border-gray-200 bg-white p-6 shadow-sm">
                    <h2 className="text-2xl font-extrabold tracking-tight text-ink">Contact information</h2>
                    <div className="mt-4 grid grid-cols-1 sm:grid-cols-2 gap-4">
                        <Field id="name" label="Full name" error={errors.name} className="sm:col-span-2">
                            <input id="name" autoComplete="name" value={state.contact.name} onChange={(e) => setContact('name', e.target.value)} className={inputCls(errors.name)} aria-invalid={!!errors.name} />
                        </Field>
                        <Field id="email" label="Email" error={errors.email}>
                            <input id="email" type="email" autoComplete="email" value={state.contact.email} onChange={(e) => setContact('email', e.target.value)} className={inputCls(errors.email)} aria-invalid={!!errors.email} />
                        </Field>
                        <Field id="phone" label="Phone number" error={errors.phone}>
                            <input id="phone" type="tel" autoComplete="tel" value={state.contact.phone} onChange={(e) => setContact('phone', e.target.value)} className={inputCls(errors.phone)} placeholder="+91 98765 43210" />
                        </Field>
                    </div>
                    <p className="mt-3 text-xs text-gray-500">We'll email your digital proof and order updates here.</p>
                </section>

                {!digital ? (
                    <section className="rounded-2xl border border-gray-200 bg-white p-6 shadow-sm">
                        <h2 className="text-2xl font-extrabold tracking-tight text-ink">Shipping address</h2>
                        <div className="mt-4 grid grid-cols-1 sm:grid-cols-2 gap-4">
                            <Field id="line1" label="Street address" error={errors.line1} className="sm:col-span-2">
                                <input id="line1" autoComplete="address-line1" value={state.shipping.line1} onChange={(e) => setShipping('line1', e.target.value)} className={inputCls(errors.line1)} />
                            </Field>
                            <Field id="city" label="City" error={errors.city}>
                                <input id="city" autoComplete="address-level2" value={state.shipping.city} onChange={(e) => setShipping('city', e.target.value)} className={inputCls(errors.city)} />
                            </Field>
                            <Field id="postalCode" label="Postal code" error={errors.postalCode}>
                                <input id="postalCode" autoComplete="postal-code" value={state.shipping.postalCode} onChange={(e) => setShipping('postalCode', e.target.value)} className={inputCls(errors.postalCode)} />
                            </Field>
                            <Field id="country" label="Country" error={errors.country} className="sm:col-span-2">
                                <input id="country" autoComplete="country-name" value={state.shipping.country} onChange={(e) => setShipping('country', e.target.value)} className={inputCls(errors.country)} />
                            </Field>
                        </div>
                    </section>
                ) : (
                    <section className="rounded-2xl border border-blue-200 bg-blue-50 p-5 text-sm text-blue-900">
                        <i className="fa fa-cloud-download mr-2" aria-hidden="true"></i>Digital delivery: no shipping address needed. Your 300 DPI file arrives by email download link.
                    </section>
                )}

                <section className="rounded-2xl border border-gray-200 bg-white p-6 shadow-sm">
                    <div className="flex gap-4">
                        <div className="w-24 flex-none rounded-lg overflow-hidden bg-gray-100">
                            <TextureCanvas src={state.photoUrl} theme={state.theme} intensity={state.intensity} maxSize={300} />
                        </div>
                        <div className="flex-1"><OrderSummary state={state} q={q} currency={currency} compact /></div>
                    </div>
                </section>
            </div>

            {/* ---------- Column B: payment ---------- */}
            <div className="lg:sticky lg:top-6 space-y-6">
                <section className="rounded-2xl border border-gray-200 bg-white p-6 shadow-sm">
                    <h2 className="text-2xl font-extrabold tracking-tight text-ink">Payment method</h2>
                    {['India', 'International'].map((group) => (
                        <fieldset key={group} className="mt-5">
                            <legend className="text-xs font-bold tracking-widest uppercase text-gray-500">{group === 'India' ? 'India · Razorpay (INR)' : 'International · PayPal (USD)'}</legend>
                            <div className="mt-2 space-y-3">
                                {METHODS.filter((m) => m.group === group).map((m) => {
                                    const checked = state.paymentMethod === m.id;
                                    const off = unavailable(m.id);
                                    return (
                                        <div key={m.id} className={`rounded-xl border-2 ${checked ? 'border-brand' : 'border-gray-200'} ${off ? 'opacity-60' : ''}`}>
                                            <label className="flex items-start gap-3 p-4 cursor-pointer focus-within:ring-2 focus-within:ring-offset-2 rounded-xl">
                                                <input type="radio" name="payment" value={m.id} checked={checked} disabled={off}
                                                       onChange={() => update({ paymentMethod: m.id })} className="mt-1 h-5 w-5 text-brand border-gray-300 focus:ring-brand" />
                                                <span className="flex-1">
                                                    <span className="block font-bold text-ink"><i className={`fa ${m.icon} w-5 text-gray-500`} aria-hidden="true"></i> {m.title}</span>
                                                    <span className="block text-sm text-gray-600">{off ? 'Not available right now.' : m.blurb}</span>
                                                </span>
                                            </label>

                                            {checked && m.id === 'CREDIT_CARD' && (
                                                <div className="mx-4 mb-4 rounded-lg border border-dashed border-gray-300 bg-gray-50 p-4" aria-label="Secure card form placeholder">
                                                    <p className="text-xs font-bold text-gray-500 uppercase tracking-widest"><i className="fa fa-lock mr-1" aria-hidden="true"></i> Secure card fields · Razorpay</p>
                                                    <div className="mt-3 grid grid-cols-2 gap-3" aria-hidden="true">
                                                        <div className="col-span-2 h-10 rounded-md bg-white border border-gray-200 flex items-center px-3 text-sm text-gray-400">Card number</div>
                                                        <div className="h-10 rounded-md bg-white border border-gray-200 flex items-center px-3 text-sm text-gray-400">MM / YY</div>
                                                        <div className="h-10 rounded-md bg-white border border-gray-200 flex items-center px-3 text-sm text-gray-400">CVV</div>
                                                    </div>
                                                    <p className="mt-3 text-xs text-gray-500">You'll enter your card in Razorpay's encrypted window after clicking pay. ArtistikCity never sees or stores your card number.</p>
                                                </div>
                                            )}
                                            {checked && m.id === 'RAZORPAY_UPI' && (
                                                <div className="mx-4 mb-4 rounded-lg border border-dashed border-gray-300 bg-gray-50 p-4 text-xs text-gray-600">
                                                    <p className="font-bold text-gray-500 uppercase tracking-widest"><i className="fa fa-qrcode mr-1" aria-hidden="true"></i> Razorpay checkout</p>
                                                    <p className="mt-2">Pay with a UPI ID, QR code or NetBanking in the Razorpay window.</p>
                                                </div>
                                            )}
                                            {checked && m.id === 'PAYPAL' && (
                                                <div className="mx-4 mb-4">
                                                    <div className="h-11 rounded-md flex items-center justify-center font-extrabold italic text-lg" style={{ background: '#ffc439', color: '#003087' }} aria-hidden="true">
                                                        Pay<span style={{ color: '#009cde' }}>Pal</span>
                                                    </div>
                                                    <p className="mt-2 text-xs text-gray-500">You'll be taken to PayPal to approve the payment, then brought back here.</p>
                                                </div>
                                            )}
                                        </div>
                                    );
                                })}
                            </div>
                        </fieldset>
                    ))}
                </section>

                {notice && (
                    <p role="alert" className={`rounded-xl px-4 py-3 text-sm font-semibold ${notice.type === 'error' ? 'bg-red-50 border border-red-200 text-red-700' : 'bg-blue-50 border border-blue-200 text-blue-900'}`}>{notice.text}</p>
                )}

                {testOrder ? (
                    <div className="rounded-2xl border-2 border-dashed border-yellow-400 bg-yellow-50 p-5">
                        <p className="font-bold text-yellow-900"><i className="fa fa-flask mr-2" aria-hidden="true"></i>Test mode: order {testOrder.orderId} was created</p>
                        <p className="mt-1 text-sm text-yellow-900">No payment keys are configured, so no real money will be taken.</p>
                        <button type="button" disabled={busy} onClick={() => { setBusy(true); confirm({ orderId: testOrder.orderId, provider: 'test' }); }}
                                className="mt-4 w-full h-12 rounded-full bg-ink text-white font-bold hover:bg-gray-800 disabled:bg-gray-400">
                            Simulate a successful payment
                        </button>
                    </div>
                ) : (
                    <button type="submit" disabled={busy || !q || unavailable(state.paymentMethod)}
                            className="w-full min-h-[3.5rem] py-4 px-6 rounded-2xl bg-brand text-white text-lg font-extrabold shadow-lg hover:bg-brand-dark disabled:bg-gray-400 disabled:shadow-none focus:outline-none focus:ring-2 focus:ring-offset-2">
                        {busy ? <><i className="fa fa-spinner fa-spin mr-2" aria-hidden="true"></i>Processing…</> : `[${payLabel}]`}
                    </button>
                )}
                <p className="text-xs text-center text-gray-500">By paying you agree to our <a href="/terms-conditions" className="underline">terms</a>. A digital proof is sent for your approval before production.</p>
            </div>
        </form>
    );
}

export default function Checkout() {
    return <CheckoutShell title="Checkout"><CheckoutForm /></CheckoutShell>;
}
