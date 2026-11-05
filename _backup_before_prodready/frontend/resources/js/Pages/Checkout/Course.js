import React, { useMemo, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import SiteLayout from '@/Components/Site/SiteLayout';
import { authPost } from '@/Components/Auth/api';

const COUNTRIES = ['India', 'United States', 'United Kingdom', 'Canada', 'Australia', 'United Arab Emirates', 'Singapore', 'Saudi Arabia',
    'Qatar', 'Kuwait', 'Oman', 'Bahrain', 'Germany', 'France', 'Netherlands', 'Ireland', 'New Zealand', 'Malaysia', 'Nepal', 'Sri Lanka',
    'Bangladesh', 'South Africa', 'Other'];

const fmt = (amount, currency) => {
    const n = Number(amount || 0);
    return currency === 'USD'
        ? '$' + n.toLocaleString('en-US', { minimumFractionDigits: n % 1 ? 2 : 0, maximumFractionDigits: 2 })
        : '₹' + n.toLocaleString('en-IN', { minimumFractionDigits: n % 1 ? 2 : 0, maximumFractionDigits: 2 });
};

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

function Step({ n, title, state, children, aside }) {
    return (
        <section className={`co-step is-${state}`} aria-labelledby={`co-step-${n}`}>
            <header className="co-step__head">
                <span className="co-step__num" aria-hidden="true">{state === 'done' ? <i className="fa fa-check"></i> : n}</span>
                <h2 id={`co-step-${n}`}>{title}</h2>
                {aside}
            </header>
            {children && <div className="co-step__body">{children}</div>}
        </section>
    );
}

function Field({ id, label, error, children, wide, optional }) {
    return (
        <div className={`co-field ${wide ? 'co-field--wide' : ''}`}>
            <label htmlFor={id}>{label}{optional && <span className="dm-muted"> (optional)</span>}</label>
            {children}
            {error && <p className="ac-field__error" id={`${id}-err`}>{error}</p>}
        </div>
    );
}

/** One-page course checkout: 1 account, 2 billing details, 3 payment, with the order summary alongside. */
export default function CheckoutCourse() {
    const { course, prices, methods = {}, auth, addresses = [], order, enrolled, returnTo, providers = {}, notice } = usePage().props;
    const user = auth && auth.user;
    const primary = addresses.find((a) => String(a.primary) === '1' || a.primary === true) || addresses[0];
    const initial = {
        billing_name: (order && order.billing_name) || (primary && primary.address_name) || (user && user.name) || '',
        billing_address: (order && order.billing_address) || (primary && primary.address) || '',
        billing_city: (order && order.billing_city) || (primary && primary.city) || '',
        billing_state: (order && order.billing_state) || (primary && primary.state) || '',
        billing_zipcode: (order && order.billing_zipcode) || (primary && primary.zipcode) || '',
        billing_country: (order && order.billing_country) || (primary && primary.country) || 'India',
    };
    const [form, setForm] = useState(initial);
    const [saveAddress, setSaveAddress] = useState(addresses.length === 0);
    const [errors, setErrors] = useState({});
    const [orderId, setOrderId] = useState(order && order.billing_address ? order.id : null);
    const [editing, setEditing] = useState(!(order && order.billing_address));
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState(notice === 'cancelled' ? { tone: 'info', text: 'You cancelled the PayPal payment. No money was taken - choose a method to try again.' }
        : notice === 'failed' ? { tone: 'error', text: 'Your payment was not completed. No money was taken. Please try again.' } : null);
    const india = /^india$/i.test(String(form.billing_country || '').trim());
    const [method, setMethod] = useState(methods.free ? 'free' : india || !methods.paypal ? 'razorpay' : 'paypal');
    const [test, setTest] = useState(null);

    const currency = method === 'paypal' ? 'USD' : 'INR';
    const total = method === 'paypal' ? prices.usd : prices.inr;
    const signIn = (path) => `${path}?return_to=${encodeURIComponent(returnTo)}`;
    const step = !user ? 1 : editing || !orderId ? 2 : 3;

    const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });
    const pickAddress = (a) => {
        setForm({ billing_name: a.address_name || form.billing_name, billing_address: a.address || '', billing_city: a.city || '',
            billing_state: a.state || '', billing_zipcode: a.zipcode || '', billing_country: a.country || 'India' });
        setSaveAddress(false);
    };

    const saveBilling = async (e) => {
        e.preventDefault();
        setBusy(true);
        setErrors({});
        setMessage(null);
        try {
            const r = await authPost('/checkout/billing', { ...form, save_address: saveAddress });
            setOrderId(r.orderId);
            setEditing(false);
            setMethod(methods.free ? 'free' : /^india$/i.test(form.billing_country.trim()) || !methods.paypal ? 'razorpay' : 'paypal');
        } catch (err) {
            if (err.data && err.data.enrolled) window.location.reload();
            setErrors(err.fieldErrors || {});
            setMessage({ tone: 'error', text: err.message });
        }
        setBusy(false);
    };

    const pay = async () => {
        setBusy(true);
        setMessage(null);
        try {
            const r = await authPost('/checkout/pay', { orderId, method });
            if (r.gateway === 'done') { window.location.assign(r.redirect); return; }
            if (r.gateway === 'paypal') { window.location.assign(r.approveUrl); return; }
            if (r.gateway === 'test') { setTest(r); setBusy(false); return; }
            const Razorpay = await loadRazorpay();
            const rzp = new Razorpay({
                key: r.key, amount: r.amount, currency: r.currency, order_id: r.razorpayOrderId, name: r.name, description: r.description,
                prefill: r.prefill || {}, theme: { color: '#e5156b' },
                handler: async (resp) => {
                    setBusy(true);
                    try {
                        const v = await authPost('/checkout/razorpay/verify', { orderId, ...resp });
                        window.location.assign(v.redirect);
                    } catch (err) {
                        setMessage({ tone: 'error', text: err.message });
                        setBusy(false);
                    }
                },
                modal: { ondismiss: () => { setBusy(false); setMessage({ tone: 'info', text: 'Payment window closed. No money was taken - you can try again any time.' }); } },
            });
            rzp.on('payment.failed', (resp) => {
                setMessage({ tone: 'error', text: (resp && resp.error && resp.error.description) || 'The payment failed. No money was taken. Please try again.' });
            });
            rzp.open();
        } catch (err) {
            if (err.data && err.data.enrolled) { window.location.reload(); return; }
            setMessage({ tone: 'error', text: err.message });
            setBusy(false);
        }
    };

    const completeTest = async () => {
        setBusy(true);
        try {
            const r = await authPost('/checkout/test-pay', { orderId });
            window.location.assign(r.redirect);
        } catch (err) {
            setMessage({ tone: 'error', text: err.message });
            setBusy(false);
        }
    };

    const facts = useMemo(() => [
        course.start_date && ['fa-calendar', `Starts ${course.start_date}`],
        course.duration && ['fa-clock-o', `${course.duration} weeks${course.hours_per_week ? ` · ${course.hours_per_week} hrs/week` : ''}`],
        course.sessions && ['fa-video-camera', `${course.sessions} live sessions`],
        course.age_group && ['fa-user', `Age ${course.age_group}`],
    ].filter(Boolean), [course]);

    return (
        <SiteLayout title="Checkout">
            <div className="ac-wide co">
                <ul className="dm-crumbs">
                    <li><a href="/">Home</a></li>
                    <li><a href="/courses?type=all">Courses</a></li>
                    <li><a href={`/course/${course.slug}`}>{course.title}</a></li>
                    <li>Checkout</li>
                </ul>
                <div className="co-head">
                    <div>
                        <span className="ac-eyebrow">Enroll</span>
                        <h1>Checkout</h1>
                    </div>
                    <span className="co-secure"><i className="fa fa-lock" aria-hidden="true"></i> Secure checkout</span>
                </div>

                <div className="co-grid">
                    <div className="co-main">
                        <a href="#co-summary" className="co-mini" aria-label="Order summary">
                            <span className="co-course__img" style={course.image ? { backgroundImage: `url("${course.image}")` } : undefined} aria-hidden="true"></span>
                            <span className="co-mini__title">{course.title}</span>
                            <b>{methods.free ? 'Free' : fmt(total, currency)}</b>
                        </a>
                        {enrolled ? (
                            <div className="co-card co-enrolled" role="status">
                                <i className="fa fa-check-circle" aria-hidden="true"></i>
                                <div>
                                    <h2>You're already enrolled</h2>
                                    <p>This {course.kind.toLowerCase()} is in your studio. Pick up right where you left off.</p>
                                </div>
                                <a href={enrolled.classroom} className="dm-btn dm-btn--brand dm-btn--lg">Go to course</a>
                            </div>
                        ) : (
                            <>
                                {message && <div className={`ac-alert ${message.tone === 'error' ? 'ac-alert--error' : 'co-alert--info'}`} role="alert">{message.text}</div>}

                                <Step n={1} title="Your account" state={user ? 'done' : 'active'}
                                      aside={user ? <span className="co-step__meta">{user.name}{user.email ? ` · ${user.email}` : ''}</span> : null}>
                                    {!user && (
                                        <div className="co-signin">
                                            <p>Sign in or create a free account. Your lessons, progress and certificate are saved to it.</p>
                                            <div className="co-signin__social">
                                                {providers.google && <a className="co-social" href={signIn('/social-login/google')}><img src="/assets/images/google-icon.png" alt="" /> Continue with Google</a>}
                                                {providers.facebook && <a className="co-social co-social--fb" href={signIn('/social-login/facebook')}><i className="fa fa-facebook-official" aria-hidden="true"></i> Continue with Facebook</a>}
                                            </div>
                                            <div className="co-signin__main">
                                                <a className="dm-btn dm-btn--brand dm-btn--lg" href={signIn('/join')}>Create free account</a>
                                                <a className="dm-btn dm-btn--line dm-btn--lg" href={signIn('/login')}>Log in</a>
                                            </div>
                                            <p className="dm-muted co-small">You can also log in with a code sent to your phone or a link sent by e-mail.</p>
                                        </div>
                                    )}
                                </Step>

                                <Step n={2} title="Billing details" state={step === 2 ? 'active' : step > 2 ? 'done' : 'todo'}
                                      aside={step > 2 ? <button type="button" className="dm-linkbtn" onClick={() => setEditing(true)}>Edit</button> : null}>
                                    {step === 2 && (
                                        <form onSubmit={saveBilling} noValidate>
                                            {addresses.length > 0 && (
                                                <div className="co-saved" role="group" aria-label="Saved addresses">
                                                    {addresses.map((a) => {
                                                        const on = a.address === form.billing_address && a.zipcode === form.billing_zipcode;
                                                        return (
                                                            <button type="button" key={a.id} className={`co-saved__item ${on ? 'is-on' : ''}`} aria-pressed={on} onClick={() => pickAddress(a)}>
                                                                <b>{a.address_name}</b>
                                                                <span>{[a.address, a.city, a.zipcode, a.country].filter(Boolean).join(', ')}</span>
                                                            </button>
                                                        );
                                                    })}
                                                </div>
                                            )}
                                            <div className="co-fields">
                                                <Field id="co-name" label="Full name" error={errors.billing_name} wide>
                                                    <input id="co-name" className={`ac-input ${errors.billing_name ? 'is-invalid' : ''}`} autoComplete="name" value={form.billing_name} onChange={set('billing_name')} />
                                                </Field>
                                                <Field id="co-address" label="Street address" error={errors.billing_address} wide>
                                                    <input id="co-address" className={`ac-input ${errors.billing_address ? 'is-invalid' : ''}`} autoComplete="street-address" value={form.billing_address} onChange={set('billing_address')} placeholder="House no., street, area" />
                                                </Field>
                                                <Field id="co-city" label="City" error={errors.billing_city}>
                                                    <input id="co-city" className={`ac-input ${errors.billing_city ? 'is-invalid' : ''}`} autoComplete="address-level2" value={form.billing_city} onChange={set('billing_city')} />
                                                </Field>
                                                <Field id="co-state" label="State / region" optional>
                                                    <input id="co-state" className="ac-input" autoComplete="address-level1" value={form.billing_state} onChange={set('billing_state')} />
                                                </Field>
                                                <Field id="co-zip" label="PIN / postal code" error={errors.billing_zipcode}>
                                                    <input id="co-zip" className={`ac-input ${errors.billing_zipcode ? 'is-invalid' : ''}`} autoComplete="postal-code" value={form.billing_zipcode} onChange={set('billing_zipcode')} />
                                                </Field>
                                                <Field id="co-country" label="Country" error={errors.billing_country}>
                                                    <select id="co-country" className="ac-input" autoComplete="country-name" value={form.billing_country} onChange={set('billing_country')}>
                                                        {!COUNTRIES.includes(form.billing_country) && form.billing_country && <option value={form.billing_country}>{form.billing_country}</option>}
                                                        {COUNTRIES.map((c) => <option key={c} value={c}>{c}</option>)}
                                                    </select>
                                                </Field>
                                            </div>
                                            <label className="ac-check co-save">
                                                <input type="checkbox" checked={saveAddress} onChange={(e) => setSaveAddress(e.target.checked)} /> Save this address to my account
                                            </label>
                                            <button type="submit" className="dm-btn dm-btn--brand dm-btn--lg co-next" disabled={busy}>
                                                {busy ? 'Saving…' : 'Continue to payment'} <i className="fa fa-arrow-right" aria-hidden="true"></i>
                                            </button>
                                        </form>
                                    )}
                                    {step > 2 && (
                                        <p className="co-recap">{[form.billing_name, form.billing_address, form.billing_city, form.billing_state, form.billing_zipcode, form.billing_country].filter(Boolean).join(', ')}</p>
                                    )}
                                </Step>

                                <Step n={3} title="Payment" state={step === 3 ? 'active' : 'todo'}>
                                    {step === 3 && (
                                        <div>
                                            {methods.free ? (
                                                <p>This {course.kind.toLowerCase()} is free - no payment needed.</p>
                                            ) : (
                                                <div className="co-methods" role="radiogroup" aria-label="Payment method">
                                                    {methods.razorpay && (
                                                        <label className={`co-method ${method === 'razorpay' ? 'is-on' : ''}`}>
                                                            <input type="radio" name="method" value="razorpay" checked={method === 'razorpay'} onChange={() => setMethod('razorpay')} />
                                                            <span className="co-method__icon" aria-hidden="true"><i className="fa fa-mobile"></i></span>
                                                            <span className="co-method__text">
                                                                <b>UPI, cards &amp; NetBanking</b>
                                                                <small>Google Pay, PhonePe, Paytm, Visa, Mastercard, RuPay · secured by Razorpay</small>
                                                            </span>
                                                            <span className="co-method__price">{fmt(prices.inr, 'INR')}</span>
                                                        </label>
                                                    )}
                                                    {methods.paypal && (
                                                        <label className={`co-method ${method === 'paypal' ? 'is-on' : ''}`}>
                                                            <input type="radio" name="method" value="paypal" checked={method === 'paypal'} onChange={() => setMethod('paypal')} />
                                                            <span className="co-method__icon" aria-hidden="true"><i className="fa fa-paypal"></i></span>
                                                            <span className="co-method__text">
                                                                <b>PayPal</b>
                                                                <small>Pay in US dollars with your PayPal balance or card · best outside India</small>
                                                            </span>
                                                            <span className="co-method__price">{fmt(prices.usd, 'USD')}</span>
                                                        </label>
                                                    )}
                                                    {!methods.razorpay && !methods.paypal && (
                                                        <p className="ac-alert ac-alert--error">Online payments aren't available right now. Please <a href="/contact" style={{ textDecoration: 'underline' }}>contact us</a> to enroll.</p>
                                                    )}
                                                </div>
                                            )}

                                            {methods.testMode && !methods.free && (
                                                <p className="co-test"><i className="fa fa-flask" aria-hidden="true"></i> Test mode: payment keys are not set, so no real money is charged.</p>
                                            )}

                                            {test ? (
                                                <div className="co-testpay" role="dialog" aria-label="Test payment">
                                                    <p><b>Simulated payment of {fmt(test.amount, test.currency)}</b></p>
                                                    <p className="dm-muted co-small">This only happens while PAYMENT_TEST_MODE is on and no gateway keys are configured.</p>
                                                    <div className="co-row">
                                                        <button type="button" className="dm-btn dm-btn--brand" onClick={completeTest} disabled={busy}>{busy ? 'Completing…' : 'Complete test payment'}</button>
                                                        <button type="button" className="dm-btn dm-btn--line" onClick={() => setTest(null)}>Cancel</button>
                                                    </div>
                                                </div>
                                            ) : (
                                                <button type="button" className="dm-btn dm-btn--brand dm-btn--lg co-pay" onClick={pay}
                                                        disabled={busy || (!methods.free && !methods.razorpay && !methods.paypal)}>
                                                    <i className="fa fa-lock" aria-hidden="true"></i>
                                                    {busy ? 'Opening secure payment…' : methods.free ? 'Enroll for free' : `Pay ${fmt(total, currency)}`}
                                                </button>
                                            )}
                                            <p className="dm-muted co-small co-center">
                                                By continuing you agree to our <a href="/terms-conditions">Terms</a> and <a href="/privacy-policy">Privacy policy</a>.
                                            </p>
                                        </div>
                                    )}
                                </Step>
                            </>
                        )}
                    </div>

                    <aside className="co-summary" id="co-summary" aria-label="Order summary">
                        <div className="co-card">
                            <h2 className="co-summary__title">Order summary</h2>
                            <a href={`/course/${course.slug}`} className="co-course">
                                <span className="co-course__img" style={course.image ? { backgroundImage: `url("${course.image}")` } : undefined} aria-hidden="true"></span>
                                <span>
                                    <span className="wl-item__kind">{course.kind}{course.medium ? ` · ${course.medium}` : ''}</span>
                                    <b>{course.title}</b>
                                    {course.teacher && <small>with {course.teacher}</small>}
                                </span>
                            </a>
                            {facts.length > 0 && (
                                <ul className="co-facts">
                                    {facts.map(([icon, text]) => <li key={text}><i className={`fa ${icon}`} aria-hidden="true"></i>{text}</li>)}
                                </ul>
                            )}
                            <ul className="co-includes">
                                <li><i className="fa fa-check" aria-hidden="true"></i>Live classes with personal feedback</li>
                                {course.certificate && <li><i className="fa fa-check" aria-hidden="true"></i>Verifiable certificate on completion</li>}
                                {course.materials && <li><i className="fa fa-check" aria-hidden="true"></i>Study materials to download</li>}
                                <li><i className="fa fa-check" aria-hidden="true"></i>Instant access to your classroom after payment</li>
                            </ul>
                            <dl className="co-total">
                                <div><dt>Course fee</dt><dd>{methods.free ? 'Free' : fmt(total, currency)}</dd></div>
                                <div><dt>Taxes</dt><dd>Included</dd></div>
                                <div className="co-total__sum"><dt>Total</dt><dd>{methods.free ? 'Free' : fmt(total, currency)}</dd></div>
                            </dl>
                            {!methods.free && methods.paypal && methods.razorpay && (
                                <p className="dm-muted co-small">Paying from outside India? Choose PayPal to pay {fmt(prices.usd, 'USD')}.</p>
                            )}
                        </div>
                        <ul className="co-trust">
                            <li><i className="fa fa-shield" aria-hidden="true"></i><span>Payments are encrypted and handled by Razorpay or PayPal - we never see your card details.</span></li>
                            <li><i className="fa fa-envelope-o" aria-hidden="true"></i><span>You'll get an e-mail receipt and a link to your classroom.</span></li>
                            <li><i className="fa fa-comments-o" aria-hidden="true"></i><span>Questions before you pay? <a href="/contact">Ask the studio</a>.</span></li>
                        </ul>
                    </aside>
                </div>
            </div>
        </SiteLayout>
    );
}
