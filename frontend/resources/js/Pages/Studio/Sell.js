import React, { useEffect, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import { Inertia } from '@inertiajs/inertia';
import axios from 'axios';
import StudioLayout from '@/Components/Studio/StudioLayout';
import { inr, shortDate } from '@/Components/Studio/Journey';
import OfferCard from '@/Components/Shop/OfferCard';
import { errorText } from '@/Components/Shop/shop';

/*
 * My studio > Sell your art (/dashboard/sell), step 7 of the journey. Certified students list the approved work from
 * that course in the marketplace (staff review each listing), follow their listings, see their earnings from the
 * ledger and keep their payout details (encrypted on the server).
 */

export const LISTING_STATUS = {
    DRAFT: { label: 'Draft', tone: 'grey', icon: 'fa-pencil', text: 'Private. Submit it when you are ready.' },
    CHANGES: { label: 'Changes requested', tone: 'amber', icon: 'fa-exclamation-circle', text: 'Our team asked for a change before it can go live.' },
    PENDING_REVIEW: { label: 'In review', tone: 'blue', icon: 'fa-clock-o', text: 'Our team is checking it before it goes live.' },
    AVAILABLE: { label: 'For sale', tone: 'green', icon: 'fa-globe', text: 'Live in the marketplace.' },
    RESERVED: { label: 'Reserved', tone: 'purple', icon: 'fa-lock', text: 'A collector is paying for it right now.' },
    SOLD: { label: 'Sold', tone: 'brand', icon: 'fa-trophy', text: 'Sold. Your share is in Earnings.' },
    ARCHIVED: { label: 'Archived', tone: 'grey', icon: 'fa-archive', text: 'Off the marketplace.' },
};
export const statusOf = (l) => (l.changes_requested ? LISTING_STATUS.CHANGES : LISTING_STATUS[l.stock_status] || LISTING_STATUS.DRAFT);

function Pill({ s }) {
    return <span className={`sl-pill is-${s.tone}`}><i className={`fa ${s.icon}`} aria-hidden="true"></i>{s.label}</span>;
}

function Terms({ terms }) {
    const items = [
        ['fa-percent', `You keep ${terms.artistPercent}%`, `of every sale (${terms.commissionPercent}% covers the listing, checkout and payments)`],
        ['fa-tag', 'You set the price', `from ${inr(terms.minPrice)}; we check every listing before it goes live`],
        ['fa-clock-o', `Payable ${terms.payoutDays} days after a sale`, 'then paid in our weekly payout run'],
        ['fa-shield', 'Secure checkout', 'collectors pay us; we hold the money until it is yours'],
    ];
    return (
        <ul className="sl-terms" aria-label="How selling works">
            {items.map(([icon, t, d]) => (
                <li key={t}><i className={`fa ${icon}`} aria-hidden="true"></i><span><b>{t}</b>{d}</span></li>
            ))}
        </ul>
    );
}

/* ---------------------------------------------------------- listings */

function Listings({ listings, onToast }) {
    const [busy, setBusy] = useState(null);
    const withdraw = (l) => {
        if (!window.confirm(`Take “${l.title}” off the marketplace? It becomes a private draft; you can submit it again later.`)) return;
        setBusy(l.id);
        axios.post(`/api/studio/sell/listings/${l.id}/withdraw`).then((r) => { onToast(r.data.message); Inertia.reload({ preserveScroll: true }); })
            .catch((e) => onToast((e.response && e.response.data && e.response.data.message) || 'Something went wrong.', true))
            .finally(() => setBusy(null));
    };
    if (listings.length === 0) return <p className="sl-empty">No listings yet. Pick an approved piece above to start.</p>;
    return (
        <ul className="sl-listings">
            {listings.map((l) => {
                const s = statusOf(l);
                const editable = ['DRAFT', 'PENDING_REVIEW', 'AVAILABLE'].includes(l.stock_status);
                return (
                    <li key={l.id} className="sl-listing">
                        <span className="sl-listing__img">{l.image_url ? <img src={l.image_url} alt="" /> : <i className="fa fa-picture-o" aria-hidden="true"></i>}</span>
                        <div className="sl-listing__main">
                            <div className="sl-listing__top">
                                <h3>{l.title}</h3>
                                <Pill s={s} />
                            </div>
                            <p className="sl-listing__meta">
                                <b>{inr(l.final_price)}</b> · you earn <b>{inr(l.you_earn)}</b>
                                {l.stock_status === 'SOLD' && l.sold_at ? ` · sold ${shortDate(l.sold_at)}` : l.published_at && l.stock_status === 'AVAILABLE' ? ` · live since ${shortDate(l.published_at)}` : ` · updated ${shortDate(l.updated_at)}`}
                            </p>
                            {l.changes_requested ? (
                                <p className="sl-note"><i className="fa fa-comment-o" aria-hidden="true"></i> <b>From our team:</b> {l.review_notes}</p>
                            ) : <p className="sl-listing__hint">{s.text}</p>}
                            <OfferSwitch l={l} onToast={onToast} />
                        </div>
                        <div className="sl-listing__actions">
                            {editable && <a href={`/dashboard/sell/listings/${l.id}`} className="sl-btn sl-btn--line">{l.changes_requested ? 'Make changes' : 'Edit'}</a>}
                            {l.stock_status !== 'ARCHIVED' && <a href={`/marketplace/${l.slug}`} className="sl-btn sl-btn--ghost" target="_blank" rel="noopener noreferrer">{['AVAILABLE', 'RESERVED', 'SOLD'].includes(l.stock_status) ? 'View' : 'Preview'} <i className="fa fa-external-link" aria-hidden="true"></i></a>}
                            {['PENDING_REVIEW', 'AVAILABLE'].includes(l.stock_status) && <button type="button" className="sl-btn sl-btn--ghost" disabled={busy === l.id} onClick={() => withdraw(l)}>Withdraw</button>}
                        </div>
                    </li>
                );
            })}
        </ul>
    );
}

/* ---------------------------------------------------------- offers */

/** "Accept offers" on one live listing, with the lowest offer (percent of the price; empty = the site default). */
function OfferSwitch({ l, onToast }) {
    const [on, setOn] = useState(!!Number(l.offers_enabled));
    const [pct, setPct] = useState(l.offer_min_pct || '');
    const [busy, setBusy] = useState(false);
    const save = (enabled, minPct) => {
        setBusy(true);
        axios.post(`/api/studio/sell/listings/${l.id}/offers`, { enabled, minPct: minPct === '' ? null : Number(minPct) })
            .then((r) => { setOn(!!r.data.enabled); onToast(r.data.message); })
            .catch((e) => onToast(errorText(e), true))
            .finally(() => setBusy(false));
    };
    if (!['AVAILABLE', 'RESERVED', 'PENDING_REVIEW', 'DRAFT'].includes(l.stock_status)) return null;
    return (
        <div className="sl-offersw">
            <label className="sl-switch">
                <input type="checkbox" checked={on} disabled={busy} onChange={(e) => { setOn(e.target.checked); save(e.target.checked, pct); }} data-testid={`offers-toggle-${l.id}`} />
                <span>Accept offers</span>
            </label>
            {on && (
                <label className="sl-offersw__pct">
                    <span>from</span>
                    <input type="number" min="10" max="99" placeholder="70" value={pct} onChange={(e) => setPct(e.target.value)} onBlur={() => save(true, pct)} aria-label="Lowest offer, percent of the price" />
                    <span>% of the price</span>
                </label>
            )}
        </div>
    );
}

function Offers({ onToast }) {
    const [data, setData] = useState(null);
    useEffect(() => {
        axios.get('/api/studio/sell/offers').then((r) => setData(r.data)).catch(() => setData({ offers: [] }));
    }, []);
    if (!data) return <p className="sl-empty">Loading offers…</p>;
    const replace = (o) => setData((d) => ({ ...d, offers: d.offers.map((x) => (x.id === o.id ? o : x)) }));
    if (!data.offers.length) return <p className="sl-empty">No offers yet. Switch on “Accept offers” on a live listing to let collectors suggest a price.</p>;
    return <div className="of-list">{data.offers.map((o) => <OfferCard key={o.id} offer={o} role="SELLER" endpoint="/api/studio/sell/offers" onChange={(x) => { replace(x); onToast(null); }} serverNow={data.serverNow} />)}</div>;
}

/* ---------------------------------------------------------- earnings */

function Earnings({ e, terms }) {
    const n = (v) => Number(v || 0);
    const payableNow = Math.max(0, n(e.balance) - n(e.holding));
    return (
        <div>
            <div className="sl-stats">
                <div><span>Earned</span><b>{inr(e.earned)}</b><small>your share of every sale</small></div>
                <div><span>To be paid</span><b>{inr(payableNow)}</b><small>in the next weekly payout</small></div>
                <div><span>In holding</span><b>{inr(e.holding)}</b><small>{`payable ${terms.payoutDays} days after the sale`}</small></div>
                <div><span>Paid out</span><b>{inr(e.paid)}</b><small>sent to your payout account</small></div>
            </div>
            {(e.sales || []).length > 0 && (
                <ul className="sl-sales" aria-label="Latest sales">
                    {e.sales.map((s, i) => (
                        <li key={i}><span>{String(s.memo || 'Sale').replace(/^Original: /, '')}</span><small>{shortDate(s.created_at)}{s.available_at ? ` · payable ${shortDate(s.available_at)}` : ''}</small><b>{inr(s.amount)}</b></li>
                    ))}
                </ul>
            )}
        </div>
    );
}

/* ----------------------------------------------------- payout details */

const METHODS = [['UPI', 'UPI', 'fa-mobile'], ['BANK', 'Bank account (India)', 'fa-university'], ['PAYPAL', 'PayPal (outside India)', 'fa-paypal']];

function PayoutForm({ payout, canStore, onToast }) {
    const [editing, setEditing] = useState(!payout);
    const [v, setV] = useState({ method: (payout && payout.method) || 'UPI', holderName: (payout && payout.holder_name) || '', upiId: '', accountNumber: '', accountNumberConfirm: '', ifsc: '', paypalEmail: '' });
    const [errors, setErrors] = useState({});
    const [busy, setBusy] = useState(false);
    const [reauth, setReauth] = useState(false);
    const set = (k, x) => { setV((p) => ({ ...p, [k]: x })); setErrors((e) => ({ ...e, [k]: null })); };
    const field = (k, label, props = {}) => (
        <label className="sl-field" htmlFor={`pay-${k}`}>
            <span>{label}</span>
            <input id={`pay-${k}`} value={v[k]} onChange={(e) => set(k, e.target.value)} aria-invalid={errors[k] ? 'true' : undefined} autoComplete="off" {...props} />
            {errors[k] && <em role="alert">{errors[k]}</em>}
        </label>
    );
    const save = (ev) => {
        ev.preventDefault();
        setBusy(true); setReauth(false);
        axios.post('/api/studio/sell/payout', v).then((r) => { onToast(r.data.message); setEditing(false); Inertia.reload({ only: ['payout'], preserveScroll: true }); })
            .catch((e) => {
                const d = (e.response && e.response.data) || {};
                if (d.reauth) setReauth(true);
                else if (d.errors) { setErrors(d.errors); onToast(d.message, true); }
                else onToast(d.message || 'Something went wrong.', true);
            }).finally(() => setBusy(false));
    };
    const signInAgain = () => axios.post('/auth/reauthenticate?return_to=%2Fdashboard%2Fsell').then((r) => window.location.assign(r.data.redirect));

    if (!canStore) return <p className="sl-note">Payout details can't be saved right now. Please try again later.</p>;
    if (!editing && payout) {
        return (
            <div className="sl-payout">
                <i className="fa fa-check-circle" aria-hidden="true"></i>
                <div><b>{payout.masked}</b><small>{payout.holder_name} · changed {shortDate(payout.updated_at)}</small></div>
                <button type="button" className="sl-btn sl-btn--line" onClick={() => setEditing(true)}>Change</button>
            </div>
        );
    }
    return (
        <form className="sl-form" onSubmit={save} noValidate>
            {reauth && (
                <div className="sl-alert" role="alert">For your security, sign in again before changing where your earnings are paid.{' '}
                    <button type="button" className="sl-linkbtn" onClick={signInAgain}>Sign in again</button></div>
            )}
            <div className="sl-seg" role="radiogroup" aria-label="How do you want to be paid?">
                {METHODS.map(([id, label, icon]) => (
                    <button key={id} type="button" role="radio" aria-checked={v.method === id} className={v.method === id ? 'is-on' : ''} onClick={() => set('method', id)}>
                        <i className={`fa ${icon}`} aria-hidden="true"></i> {label}
                    </button>
                ))}
            </div>
            {field('holderName', 'Account holder name', { autoComplete: 'name' })}
            {v.method === 'UPI' && field('upiId', 'UPI ID', { placeholder: 'yourname@okhdfcbank', inputMode: 'email' })}
            {v.method === 'BANK' && (
                <div className="sl-grid2">
                    {field('accountNumber', 'Account number', { inputMode: 'numeric' })}
                    {field('accountNumberConfirm', 'Account number again', { inputMode: 'numeric', onPaste: (e) => e.preventDefault() })}
                    {field('ifsc', 'IFSC code', { placeholder: 'HDFC0001234', style: { textTransform: 'uppercase' } })}
                </div>
            )}
            {v.method === 'PAYPAL' && field('paypalEmail', 'PayPal e-mail', { type: 'email', inputMode: 'email' })}
            <p className="sl-small"><i className="fa fa-lock" aria-hidden="true"></i> Stored encrypted. Only our finance team can see the full details, and only when paying you.</p>
            <div className="sl-actions">
                <button type="submit" className="sl-btn sl-btn--brand" disabled={busy}>{busy ? 'Saving…' : 'Save payout details'}</button>
                {payout && <button type="button" className="sl-btn sl-btn--ghost" onClick={() => setEditing(false)}>Cancel</button>}
            </div>
        </form>
    );
}

/* -------------------------------------------------------------- page */

export default function Sell() {
    const { certified, certifiedCourses = [], ready = [], locked = [], listings = [], earnings = {}, payout, canStorePayout, terms = {}, restriction, flash } = usePage().props;
    const [toast, setToast] = useState(flash && flash.message ? { text: flash.message } : null);
    const say = (text, error = false) => setToast(text ? { text, error } : null);
    // the message from the listing form after it saved
    useEffect(() => {
        try {
            const m = window.sessionStorage.getItem('ac.sell.flash');
            if (m) { window.sessionStorage.removeItem('ac.sell.flash'); setToast({ text: m }); }
        } catch (e) { /* private mode */ }
    }, []);

    return (
        <StudioLayout title="Sell your art" active="/dashboard/sell" stage={7}
            subtitle={`Turn your certified work into income. You set the price and keep ${terms.artistPercent}% of every sale.`}>
            {toast && <div className={`sl-toast ${toast.error ? 'is-error' : ''}`} role={toast.error ? 'alert' : 'status'}>{toast.text}<button type="button" onClick={() => setToast(null)} aria-label="Dismiss">×</button></div>}
            {restriction && <div className="sl-alert" role="alert"><i className="fa fa-ban" aria-hidden="true"></i> {restriction}</div>}

            {!certified ? (
                <section className="sl-locked">
                    <span className="sl-locked__icon" aria-hidden="true"><i className="fa fa-lock"></i></span>
                    <div>
                        <p className="sl-eyebrow">Step 7 · unlocks with your first certificate</p>
                        <h2>Earn a certificate to start selling</h2>
                        <p>Finish every lesson of a course and get an assignment approved to earn its certificate. Then the approved work you made for that course can go on sale here.</p>
                        <div className="sl-actions">
                            <a href="/dashboard/certificates" className="sl-btn sl-btn--brand">See what's left to certify</a>
                            <a href="/how-it-works#step-sell" className="sl-btn sl-btn--line">How selling works</a>
                        </div>
                    </div>
                </section>
            ) : (
                <p className="sl-certified"><i className="fa fa-certificate" aria-hidden="true"></i> Certified in {certifiedCourses.map((c) => c.title).join(', ')}</p>
            )}

            <Terms terms={terms} />

            <section className="sl-section" aria-labelledby="sl-ready">
                <div className="sl-head"><h2 id="sl-ready">Ready to sell</h2><p>Approved work from the courses you are certified in.</p></div>
                {ready.length === 0 ? (
                    <p className="sl-empty">{certified ? 'Everything approved is already listed. Submit new work in a certified course to sell more.' : 'Nothing yet: your approved work appears here once its course is certified.'}</p>
                ) : (
                    <ul className="sl-cards">
                        {ready.map((s) => (
                            <li key={s.id} className="sl-card">
                                <img src={s.file_url} alt="" />
                                <div className="sl-card__body">
                                    <h3>{s.title}</h3>
                                    <p>{s.course_title}</p>
                                    <a href={`/dashboard/sell/new?submission=${s.id}`} className={`sl-btn sl-btn--brand ${restriction ? 'is-disabled' : ''}`} aria-disabled={restriction ? 'true' : undefined}
                                       onClick={(e) => { if (restriction) e.preventDefault(); }}>List for sale</a>
                                </div>
                            </li>
                        ))}
                    </ul>
                )}
                {locked.length > 0 && (
                    <details className="sl-lockedlist">
                        <summary>{locked.length} approved piece{locked.length === 1 ? '' : 's'} waiting for a course certificate</summary>
                        <ul>{locked.map((s) => <li key={s.id}><img src={s.file_url} alt="" /><span><b>{s.title}</b>{s.course_title}</span></li>)}</ul>
                    </details>
                )}
            </section>

            <section className="sl-section" aria-labelledby="sl-listings">
                <div className="sl-head"><h2 id="sl-listings">Your listings</h2><p>Drafts, listings in review, and what is live or sold.</p></div>
                <Listings listings={listings} onToast={say} />
            </section>

            <section className="sl-section" id="offers" aria-labelledby="sl-offers">
                <div className="sl-head"><h2 id="sl-offers">Offers</h2><p>Prices collectors suggested for your paintings. Accept, counter or decline within the time shown.</p></div>
                <Offers onToast={say} />
            </section>

            <div className="sl-two">
                <section className="sl-section" aria-labelledby="sl-earnings">
                    <div className="sl-head"><h2 id="sl-earnings">Earnings</h2><p>Your share of each sale, from our payout ledger.</p></div>
                    <Earnings e={earnings} terms={terms} />
                </section>
                <section className="sl-section" id="payout" aria-labelledby="sl-payout">
                    <div className="sl-head"><h2 id="sl-payout">Payout details</h2><p>Where we send your earnings.</p></div>
                    <PayoutForm payout={payout} canStore={canStorePayout} onToast={say} />
                </section>
            </div>
        </StudioLayout>
    );
}
