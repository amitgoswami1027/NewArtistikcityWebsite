import React, { useEffect, useMemo, useState } from 'react';
import { Head, usePage } from '@inertiajs/inertia-react';
import Authenticated from '@/Layouts/Authenticated';

/**
 * My account > Your orders. Every order the student has placed, from every order system (courses and workshops,
 * marketplace cart orders, older single-original orders, commissions), as UnifiedOrder records from
 * UnifiedOrderService, newest first. Filtering happens here for instant feedback; the same rules exist on the server
 * for GET /api/v1/user/orders?type=&q= (UnifiedOrderService.matchesType / matchesQuery).
 */

export const TYPE_FILTERS = [
    { id: 'all', label: 'All orders' },
    { id: 'course', label: 'Courses' },
    { id: 'workshop', label: 'Workshops' },
    { id: 'marketplace', label: 'Art shop' },
    { id: 'original', label: 'Original paintings' },
    { id: 'print', label: 'Prints' },
    { id: 'download', label: 'Digital downloads' },
    { id: 'commission', label: 'Commissions' },
];

const STATUS_FILTERS = [
    { id: 'all', label: 'Any status' },
    { id: 'pending', label: 'Payment pending' },
    { id: 'processing', label: 'Being prepared' },
    { id: 'shipped', label: 'Shipped' },
    { id: 'completed', label: 'Completed' },
    { id: 'failed', label: 'Failed' },
    { id: 'refunded', label: 'Refunded' },
];

export const matchesType = (o, type) => {
    switch (type) {
        case 'all': return true;
        case 'course': return o.category === 'Digital Course';
        case 'workshop': return o.category === 'Workshop';
        case 'marketplace': return o.orderType === 'marketplace';
        case 'original': return o.category === 'Original Painting';
        case 'print': return o.category === 'Canvas Print' || o.category === 'Poster Print';
        case 'download': return o.category === 'Digital Download';
        case 'commission': return o.orderType === 'commission';
        default: return false;
    }
};

export const matchesQuery = (o, q) => {
    const words = String(q || '').trim().toLowerCase().split(/\s+/).filter(Boolean);
    if (!words.length) return true;
    const hay = [o.orderId, o.title, o.subtitle, o.variant, o.category, o.statusLabel].filter(Boolean).join(' ').toLowerCase();
    return words.every((w) => hay.includes(w));
};

const BADGE = {
    'Digital Course': { icon: 'fa-play-circle', tone: 'course' },
    Workshop: { icon: 'fa-users', tone: 'course' },
    'Original Painting': { icon: 'fa-paint-brush', tone: 'original' },
    'Canvas Print': { icon: 'fa-picture-o', tone: 'print' },
    'Poster Print': { icon: 'fa-file-image-o', tone: 'print' },
    'Digital Download': { icon: 'fa-download', tone: 'digital' },
    Commission: { icon: 'fa-magic', tone: 'commission' },
};

const STATUS_TONE = { pending: 'wait', processing: 'wait', shipped: 'info', completed: 'ok', failed: 'bad', refunded: 'bad' };

const money = (a, c) => {
    const n = Number(a);
    if (a == null || isNaN(n)) return '';
    const cur = String(c || 'INR').toUpperCase();
    return (cur === 'USD' ? '$' : '₹') + n.toLocaleString(cur === 'USD' ? 'en-US' : 'en-IN', { maximumFractionDigits: 2 });
};
const niceDate = (v) => {
    const d = v ? new Date(v) : null;
    return d && !isNaN(d) ? d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : '';
};

/** The one thing to do next for this order, then the secondary actions. */
function actionsFor(o) {
    const a = [];
    if (o.accessUrl) a.push({ href: o.accessUrl, label: 'Start learning', icon: 'fa-play', primary: true, id: 'learn' });
    if (o.downloadUrl) a.push({ href: o.downloadUrl, label: 'Download', icon: 'fa-download', primary: true, id: 'download' });
    if (o.trackingUrl) a.push({ href: o.trackingUrl, label: 'Track delivery', icon: 'fa-truck', primary: true, external: true, id: 'track' });
    if (o.certificateUrl) a.push({ href: o.certificateUrl, label: 'View certificate of authenticity', icon: 'fa-certificate', id: 'coa', external: true });
    if (o.status === 'pending' && o.orderType === 'course' && o.detailUrl) a.push({ href: o.detailUrl, label: 'Complete payment', icon: 'fa-credit-card', primary: true, id: 'pay' });
    else if (o.detailUrl) a.push({ href: o.detailUrl, label: 'View order', icon: 'fa-file-text-o', id: 'detail' });
    return a;
}

function readUrl(fallback) {
    try {
        const p = new URLSearchParams(window.location.search);
        return { type: p.get('type') || fallback.type || 'all', q: p.get('q') || fallback.q || '' };
    } catch (e) {
        return { type: fallback.type || 'all', q: fallback.q || '' };
    }
}

export default function Orders(props) {
    const { orders = [], filter = {} } = usePage().props;
    const initial = useMemo(() => readUrl(filter), []); // eslint-disable-line react-hooks/exhaustive-deps
    const [type, setType] = useState(TYPE_FILTERS.some((t) => t.id === initial.type) ? initial.type : 'all');
    const [status, setStatus] = useState('all');
    const [period, setPeriod] = useState('all');
    const [query, setQuery] = useState(initial.q);
    const [open, setOpen] = useState(null);

    // keep the filter in the address bar so a refresh or a shared link shows the same list
    useEffect(() => {
        try {
            const p = new URLSearchParams();
            if (type !== 'all') p.set('type', type);
            if (query.trim()) p.set('q', query.trim());
            const s = p.toString();
            window.history.replaceState(window.history.state, '', window.location.pathname + (s ? `?${s}` : ''));
        } catch (e) { /* not important */ }
    }, [type, query]);

    const counts = useMemo(() => {
        const c = {};
        TYPE_FILTERS.forEach((t) => { c[t.id] = orders.filter((o) => matchesType(o, t.id)).length; });
        return c;
    }, [orders]);

    const years = useMemo(() => Array.from(new Set(orders.map((o) => { const d = new Date(o.purchaseDate); return isNaN(d) ? null : d.getFullYear(); })
        .filter(Boolean))).sort((a, b) => b - a), [orders]);
    const inPeriod = (o) => {
        if (period === 'all') return true;
        const d = new Date(o.purchaseDate);
        if (isNaN(d)) return false;
        if (period === '30' || period === '180') return (Date.now() - d.getTime()) / 86400000 <= Number(period);
        return String(d.getFullYear()) === period;
    };
    const shown = orders.filter((o) => matchesType(o, type) && (status === 'all' || o.status === status) && inPeriod(o) && matchesQuery(o, query));
    const filtered = type !== 'all' || status !== 'all' || period !== 'all' || query.trim() !== '';
    const reset = () => { setType('all'); setStatus('all'); setPeriod('all'); setQuery(''); };

    return (
        <Authenticated auth={props.auth} errors={props.errors}>
            <Head title="Your orders" />
            {orders.length === 0 ? (
                <div className="wl-empty" data-testid="orders-empty">
                    <i className="fa fa-shopping-bag" aria-hidden="true"></i>
                    <h3 style={{ marginTop: 12 }}>No orders yet</h3>
                    <p className="dm-muted">Courses, workshops, artworks, prints and commissions you buy will all appear here.</p>
                    <p style={{ marginTop: 16, display: 'flex', gap: 10, justifyContent: 'center', flexWrap: 'wrap' }}>
                        <a href="/courses?type=all" className="dm-btn dm-btn--brand">Browse courses</a>
                        <a href="/marketplace" className="dm-btn dm-btn--line">Visit the marketplace</a>
                    </p>
                </div>
            ) : (
                <>
                    <div className="ord-filters" role="search" aria-label="Filter orders">
                        <div className="ord-chips" role="group" aria-label="Order type">
                            {TYPE_FILTERS.filter((f) => f.id === 'all' || counts[f.id] || type === f.id).map((f) => (
                                <button key={f.id} type="button" aria-pressed={type === f.id} data-type={f.id}
                                        className={`ord-chip ${type === f.id ? 'is-on' : ''}`} onClick={() => setType(f.id)}>
                                    {f.label} <span>{counts[f.id] || 0}</span>
                                </button>
                            ))}
                        </div>
                        <div className="ord-controls">
                            <label className="ord-search">
                                <span className="sr-only">Search orders</span>
                                <i className="fa fa-search" aria-hidden="true"></i>
                                <input type="search" className="ac-input" value={query} onChange={(e) => setQuery(e.target.value)}
                                       placeholder="Search by order number, title, artist or size" />
                            </label>
                            <label>
                                <span className="sr-only">Status</span>
                                <select className="ac-input" value={status} onChange={(e) => setStatus(e.target.value)}>
                                    {STATUS_FILTERS.map((s) => <option key={s.id} value={s.id}>{s.label}</option>)}
                                </select>
                            </label>
                            <label>
                                <span className="sr-only">Date</span>
                                <select className="ac-input" value={period} onChange={(e) => setPeriod(e.target.value)}>
                                    <option value="all">All time</option>
                                    <option value="30">Last 30 days</option>
                                    <option value="180">Last 6 months</option>
                                    {years.map((y) => <option key={y} value={String(y)}>{y}</option>)}
                                </select>
                            </label>
                        </div>
                    </div>

                    <p className="dm-muted ord-summary" aria-live="polite">
                        {shown.length === orders.length ? `${orders.length} order${orders.length === 1 ? '' : 's'}, newest first` : `${shown.length} of ${orders.length} orders`}
                        {filtered && <> · <button type="button" className="dm-linkbtn" onClick={reset}>Clear filters</button></>}
                    </p>

                    {shown.length === 0 ? (
                        <p className="wl-empty" style={{ padding: 32 }}>No orders match these filters.</p>
                    ) : (
                        <ul className="ord-list">
                            {shown.map((o) => {
                                const badge = BADGE[o.category] || { icon: 'fa-shopping-bag', tone: 'print' };
                                const actions = actionsFor(o);
                                return (
                                    <li key={o.key} className="ord-item" data-order={o.orderId} data-category={o.category}>
                                        <div className="ord-item__main">
                                            <span className="ord-item__img" aria-hidden="true" style={o.imageUrl ? { backgroundImage: `url("${o.imageUrl}")` } : undefined}>
                                                {!o.imageUrl && <i className={`fa ${badge.icon}`}></i>}
                                            </span>
                                            <div className="ord-item__body">
                                                <span className={`ord-badge ord-badge--${badge.tone}`}>
                                                    <i className={`fa ${badge.icon}`} aria-hidden="true"></i> {o.category}
                                                </span>
                                                <h4>{o.title}</h4>
                                                {(o.subtitle || o.variant) && (
                                                    <div className="wl-item__meta">{[o.subtitle, o.variant].filter(Boolean).join(' · ')}
                                                        {o.quantity > 1 && ` · Qty ${o.quantity}`}</div>
                                                )}
                                                <div className="wl-item__meta">Order {o.orderId} · {niceDate(o.purchaseDate)}</div>
                                            </div>
                                            <div className="ord-item__side">
                                                <span className="wl-item__price">{money(o.amount, o.currency)}</span>
                                                <span className={`ord-status ord-status--${STATUS_TONE[o.status] || 'wait'}`} data-status={o.status}>
                                                    {o.statusLabel || o.status}
                                                </span>
                                            </div>
                                        </div>
                                        <div className="ord-item__actions">
                                            {actions.map((a) => (
                                                <a key={a.id} href={a.href} data-action={a.id}
                                                   className={`dm-btn ${a.primary ? 'dm-btn--brand' : 'dm-btn--line'}`}
                                                   {...(a.external ? { target: '_blank', rel: 'noopener noreferrer' } : {})}>
                                                    <i className={`fa ${a.icon}`} aria-hidden="true"></i> {a.label}
                                                </a>
                                            ))}
                                            <button type="button" className="dm-btn dm-btn--line" aria-expanded={open === o.key} onClick={() => setOpen(open === o.key ? null : o.key)}>
                                                {open === o.key ? 'Hide details' : 'Order details'}
                                            </button>
                                        </div>
                                        {open === o.key && (
                                            <dl className="ord-details">
                                                <div><dt>Order number</dt><dd>{o.orderId}</dd></div>
                                                <div><dt>Ordered on</dt><dd>{niceDate(o.purchaseDate)}</dd></div>
                                                <div><dt>Amount</dt><dd>{money(o.amount, o.currency)}</dd></div>
                                                <div><dt>Type</dt><dd>{o.category}</dd></div>
                                                <div><dt>Status</dt><dd>{o.statusLabel || o.status}</dd></div>
                                                {o.editionNo && <div><dt>Edition</dt><dd>No. {o.editionNo}{o.quantity > 1 ? `–${o.editionNo + o.quantity - 1}` : ''}</dd></div>}
                                                {o.paymentMethod && <div><dt>Payment</dt><dd>{o.paymentMethod}</dd></div>}
                                                {o.trackingNumber && <div><dt>Tracking number</dt><dd>{o.trackingNumber}</dd></div>}
                                                {o.address && <div className="ord-details__wide"><dt>{o.addressLabel}</dt><dd>{o.address}</dd></div>}
                                            </dl>
                                        )}
                                    </li>
                                );
                            })}
                        </ul>
                    )}
                </>
            )}
        </Authenticated>
    );
}
