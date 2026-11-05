import React, { useMemo, useState } from 'react';
import { Head, usePage } from '@inertiajs/inertia-react';
import Authenticated from '@/Layouts/Authenticated';

const KINDS = { course: 'Course', workshop: 'Workshop', original: 'Original artwork', commission: 'Commission' };
const TYPE_FILTERS = [
    { id: 'all', label: 'All orders' },
    { id: 'course', label: 'Courses' },
    { id: 'workshop', label: 'Workshops' },
    { id: 'original', label: 'Original artworks' },
    { id: 'commission', label: 'Commissions' },
];

const money = (a, c) => {
    const n = Number(a);
    if (a == null || isNaN(n)) return '';
    const cur = String(c || 'INR').toUpperCase();
    return (cur === 'USD' ? '$' : '₹') + n.toLocaleString(cur === 'USD' ? 'en-US' : 'en-IN', { maximumFractionDigits: 2 });
};
const toDate = (v) => (v ? new Date(String(v).replace(' ', 'T')) : null);
const niceDate = (v) => {
    const d = toDate(v);
    return d && !isNaN(d) ? d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : '';
};
const tone = (status) => {
    const s = String(status || '').toLowerCase();
    if (/(fail|cancel|refund)/.test(s)) return 'bad';
    if (/(pending|queued|production|proof|packed)/.test(s)) return 'wait';
    return 'ok';
};

/** My account > Your orders: every order in one place, with search and filters. */
export default function Orders(props) {
    const { orders = [] } = usePage().props;
    const [type, setType] = useState('all');
    const [status, setStatus] = useState('all');
    const [period, setPeriod] = useState('all');
    const [query, setQuery] = useState('');
    const [open, setOpen] = useState(null);

    const statuses = useMemo(() => Array.from(new Set(orders.map((o) => o.status).filter(Boolean))).sort(), [orders]);
    const years = useMemo(() => Array.from(new Set(orders.map((o) => { const d = toDate(o.date); return d && !isNaN(d) ? d.getFullYear() : null; })
        .filter(Boolean))).sort((a, b) => b - a), [orders]);
    const counts = useMemo(() => {
        const c = { all: orders.length };
        orders.forEach((o) => { c[o.kind] = (c[o.kind] || 0) + 1; });
        return c;
    }, [orders]);

    const shown = orders.filter((o) => {
        if (type !== 'all' && o.kind !== type) return false;
        if (status !== 'all' && o.status !== status) return false;
        if (period !== 'all') {
            const d = toDate(o.date);
            if (!d || isNaN(d)) return false;
            if (period === '30' || period === '180') {
                if ((Date.now() - d.getTime()) / 86400000 > Number(period)) return false;
            } else if (String(d.getFullYear()) !== period) return false;
        }
        const q = query.trim().toLowerCase();
        return !q || [o.number, o.title, o.subtitle].filter(Boolean).join(' ').toLowerCase().includes(q);
    });
    const filtered = type !== 'all' || status !== 'all' || period !== 'all' || query.trim() !== '';
    const reset = () => { setType('all'); setStatus('all'); setPeriod('all'); setQuery(''); };

    return (
        <Authenticated auth={props.auth} errors={props.errors}>
            <Head title="Your orders" />
            {orders.length === 0 ? (
                <div className="wl-empty">
                    <i className="fa fa-shopping-bag" aria-hidden="true"></i>
                    <h3 style={{ marginTop: 12 }}>No orders yet</h3>
                    <p className="dm-muted">When you enroll in a course, buy an original or commission artwork, it will appear here.</p>
                    <p style={{ marginTop: 16, display: 'flex', gap: 10, justifyContent: 'center', flexWrap: 'wrap' }}>
                        <a href="/courses?type=all" className="dm-btn dm-btn--brand">Browse courses</a>
                        <a href="/marketplace" className="dm-btn dm-btn--line">Visit the marketplace</a>
                    </p>
                </div>
            ) : (
                <>
                    <div className="ord-filters" role="search" aria-label="Filter orders">
                        <div className="ord-chips" role="group" aria-label="Order type">
                            {TYPE_FILTERS.filter((f) => f.id === 'all' || counts[f.id]).map((f) => (
                                <button key={f.id} type="button" aria-pressed={type === f.id} className={`ord-chip ${type === f.id ? 'is-on' : ''}`}
                                        onClick={() => setType(f.id)}>
                                    {f.label} <span>{counts[f.id] || 0}</span>
                                </button>
                            ))}
                        </div>
                        <div className="ord-controls">
                            <label className="ord-search">
                                <span className="sr-only">Search orders</span>
                                <i className="fa fa-search" aria-hidden="true"></i>
                                <input type="search" className="ac-input" value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Order number or title" />
                            </label>
                            <label>
                                <span className="sr-only">Status</span>
                                <select className="ac-input" value={status} onChange={(e) => setStatus(e.target.value)}>
                                    <option value="all">Any status</option>
                                    {statuses.map((s) => <option key={s} value={s}>{s}</option>)}
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
                        {shown.length === orders.length ? `${orders.length} order${orders.length === 1 ? '' : 's'}` : `${shown.length} of ${orders.length} orders`}
                        {filtered && <> · <button type="button" className="dm-linkbtn" onClick={reset}>Clear filters</button></>}
                    </p>

                    {shown.length === 0 ? (
                        <p className="wl-empty" style={{ padding: 32 }}>No orders match these filters.</p>
                    ) : (
                        <ul className="ord-list">
                            {shown.map((o) => (
                                <li key={o.key} className="ord-item">
                                    <div className="ord-item__main">
                                        <span className="ord-item__img" aria-hidden="true" style={o.image ? { backgroundImage: `url("${o.image}")` } : undefined}>
                                            {!o.image && <i className={`fa ${o.kind === 'commission' ? 'fa-paint-brush' : 'fa-picture-o'}`}></i>}
                                        </span>
                                        <div className="ord-item__body">
                                            <span className="wl-item__kind">{KINDS[o.kind]}</span>
                                            <h4>{o.title}</h4>
                                            {o.subtitle && <div className="wl-item__meta">{o.subtitle}</div>}
                                            <div className="wl-item__meta">Order {o.number} · {niceDate(o.date)}</div>
                                        </div>
                                        <div className="ord-item__side">
                                            <span className="wl-item__price">{money(o.amount, o.currency)}</span>
                                            <span className={`ord-status ord-status--${tone(o.status)}`}>{o.status}</span>
                                        </div>
                                    </div>
                                    <div className="ord-item__actions">
                                        {o.link && <a href={o.link} className="dm-btn dm-btn--brand">{o.linkLabel}</a>}
                                        <button type="button" className="dm-btn dm-btn--line" aria-expanded={open === o.key} onClick={() => setOpen(open === o.key ? null : o.key)}>
                                            {open === o.key ? 'Hide details' : 'Order details'}
                                        </button>
                                    </div>
                                    {open === o.key && (
                                        <dl className="ord-details">
                                            <div><dt>Order number</dt><dd>{o.number}</dd></div>
                                            <div><dt>Ordered on</dt><dd>{niceDate(o.date)}</dd></div>
                                            <div><dt>Amount</dt><dd>{money(o.amount, o.currency)}</dd></div>
                                            {o.payment && <div><dt>Payment</dt><dd>{o.payment}</dd></div>}
                                            <div><dt>Status</dt><dd>{o.status}</dd></div>
                                            {o.tracking && <div><dt>Tracking</dt><dd>{o.tracking}</dd></div>}
                                            {o.billing && <div className="ord-details__wide"><dt>{o.kind === 'original' ? 'Ship to' : 'Billed to'}</dt><dd>{o.billing}</dd></div>}
                                        </dl>
                                    )}
                                </li>
                            ))}
                        </ul>
                    )}
                </>
            )}
        </Authenticated>
    );
}
