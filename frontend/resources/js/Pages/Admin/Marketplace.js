import React, { useMemo, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import axios from 'axios';
import AdminLayout, { PageHeader, useToast, errorText } from '@/Components/Admin/AdminLayout';
import { StatusChip, inr, usd, dims, useCountdown, mmss, num } from '@/Components/Marketplace/shared';

/*
 * Marketplace console. What each persona sees:
 *   Super admin  - everything: list/publish, review queue, live holds, fulfilment, refunds.
 *   Moderator    - review queue, unpublish/feature, live holds, fulfilment.
 *   Instructor   - their own listings (edits go to review) and a read-only view of their sales.
 */

const ORDER_LABEL = {
    PAID: ['Paid · to pack', 'bg-brand-soft text-brand-dark border-pink-200'], PACKED: ['Packed', 'bg-blue-50 text-blue-800 border-blue-200'],
    SHIPPED: ['Shipped', 'bg-indigo-50 text-indigo-800 border-indigo-200'], DELIVERED: ['Delivered', 'bg-green-50 text-green-800 border-green-200'],
    REFUND_REQUIRED: ['Refund required', 'bg-red-50 text-red-700 border-red-200'], REFUNDED: ['Refunded', 'bg-gray-100 text-gray-600 border-gray-200'],
    CANCELLED: ['Cancelled', 'bg-gray-100 text-gray-500 border-gray-200'], EXPIRED: ['Expired', 'bg-gray-100 text-gray-500 border-gray-200'],
};
const NEXT_LABEL = { PACKED: 'Mark packed', SHIPPED: 'Mark shipped', DELIVERED: 'Mark delivered', REFUNDED: 'Record refund' };
const when = (ms) => (ms ? new Date(ms).toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—');

function Kpi({ label, value, icon, tone = 'text-ink', onClick }) {
    const Tag = onClick ? 'button' : 'div';
    return (
        <Tag type={onClick ? 'button' : undefined} onClick={onClick} className={`text-left rounded-2xl bg-white border border-gray-200 p-5 ${onClick ? 'hover:border-ink transition' : ''}`}>
            <div className="flex items-center justify-between"><p className="font-mono text-xs uppercase tracking-widest text-gray-500">{label}</p><i className={`fa ${icon} text-gray-300`} aria-hidden="true"></i></div>
            <p className={`mt-2 text-3xl font-black ${tone}`}>{value}</p>
        </Tag>
    );
}

function HoldRow({ h, serverNow, onRelease }) {
    const left = useCountdown(h.expires_at, serverNow);
    return (
        <tr className="border-t border-gray-100">
            <td className="py-3 pr-4"><div className="flex items-center gap-3"><img src={h.image_url} alt="" className="w-12 h-12 rounded-lg object-cover" /><div><p className="font-bold text-ink">{h.title}</p><p className="text-xs text-gray-500">{h.artist_name} · {inr(h.final_price)}</p></div></div></td>
            <td className="py-3 pr-4 text-sm">{h.guest ? <span className="text-gray-500">Guest collector</span> : <><p className="font-semibold text-ink">{h.user_name}</p><p className="text-xs text-gray-500">{h.user_email}</p></>}</td>
            <td className="py-3 pr-4 text-sm">{Number(h.payment_started) > 0 ? <span className="inline-flex items-center gap-1 text-green-700 font-bold"><i className="fa fa-credit-card" aria-hidden="true"></i>Paying now</span> : <span className="text-gray-500">On checkout page</span>}</td>
            <td className="py-3 pr-4 font-mono font-bold text-ink">{left > 0 ? mmss(left) : 'ending…'}</td>
            <td className="py-3 text-right"><button type="button" onClick={() => onRelease(h)} className="h-9 px-4 rounded-full border border-gray-300 text-sm font-bold hover:border-red-500 hover:text-red-600">Release</button></td>
        </tr>
    );
}

function ReviewCard({ l, onDecide }) {
    const [notes, setNotes] = useState('');
    const [busy, setBusy] = useState(false);
    const go = (decision) => { setBusy(true); onDecide(l, decision, notes).finally(() => setBusy(false)); };
    return (
        <article className="rounded-2xl border border-gray-200 bg-white overflow-hidden grid grid-cols-1 md:grid-cols-5">
            <div className="md:col-span-2 bg-gray-50 p-5 flex items-center justify-center"><img src={l.image_url} alt={l.title} className="mk-art max-h-64 w-auto" /></div>
            <div className="md:col-span-3 p-6 flex flex-col">
                <div className="flex items-start justify-between gap-3">
                    <div><h3 className="text-xl font-extrabold text-ink">{l.title}</h3><p className="text-sm text-gray-500">{l.artist_name} · {l.source === 'STUDENT' ? 'Student original' : 'Studio original'}</p></div>
                    <a href={`/marketplace/${l.slug}`} target="_blank" rel="noopener" className="text-sm font-bold text-brand whitespace-nowrap">Preview page <i className="fa fa-external-link" aria-hidden="true"></i></a>
                </div>
                <dl className="mt-4 grid grid-cols-2 gap-3 text-sm">
                    <div><dt className="text-gray-500">Medium</dt><dd className="font-semibold text-ink">{l.medium} on {String(l.surface).toLowerCase()}</dd></div>
                    <div><dt className="text-gray-500">Size</dt><dd className="font-semibold text-ink">{dims(l)}</dd></div>
                    <div><dt className="text-gray-500">Price</dt><dd className="font-semibold text-ink">{inr(l.final_price)}{Number(l.discount_percentage) > 0 && <span className="text-gray-400"> ({num(l.discount_percentage, 0)}% off {inr(l.base_price)})</span>}</dd></div>
                    <div><dt className="text-gray-500">Photos</dt><dd className={`font-semibold ${Number(l.image_count) < 2 ? 'text-yellow-700' : 'text-ink'}`}>{l.image_count}{Number(l.image_count) < 2 && ' · consider asking for detail shots'}</dd></div>
                </dl>
                <ul className="mt-4 text-xs text-gray-500 grid grid-cols-1 sm:grid-cols-2 gap-1">
                    {['Photo is sharp, square-on and colour-true', 'Title & story are the artist\'s own', 'Dimensions look plausible for the photo', 'Price is in a sensible range'].map((c) => <li key={c}><i className="fa fa-check-square-o mr-1" aria-hidden="true"></i>{c}</li>)}
                </ul>
                <label className="mt-4 block text-sm font-bold text-ink" htmlFor={`n${l.id}`}>Notes to the artist</label>
                <textarea id={`n${l.id}`} rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} className="mt-1 w-full rounded-xl border border-gray-300 bg-white px-3 py-2 text-sm focus:border-brand focus:ring-brand" placeholder="Required when requesting changes" />
                <div className="mt-4 flex flex-wrap gap-2">
                    <button type="button" disabled={busy} onClick={() => go('approve')} className="h-11 px-5 rounded-full bg-green-600 text-white font-bold hover:bg-green-700 disabled:opacity-60"><i className="fa fa-check mr-2" aria-hidden="true"></i>Approve & publish</button>
                    <button type="button" disabled={busy || notes.trim().length < 5} onClick={() => go('reject')} className="h-11 px-5 rounded-full border border-gray-300 font-bold hover:border-red-500 hover:text-red-600 disabled:opacity-40">Request changes</button>
                </div>
            </div>
        </article>
    );
}

function OrderRow({ o, flow, canFulfil, onAdvance }) {
    const [open, setOpen] = useState(false);
    const [courier, setCourier] = useState(o.courier || '');
    const [tracking, setTracking] = useState(o.tracking_number || '');
    const [notes, setNotes] = useState('');
    const [relist, setRelist] = useState(true);
    const [busy, setBusy] = useState(null);
    const [label, tone] = ORDER_LABEL[o.status] || [o.status, 'bg-gray-100 text-gray-600 border-gray-200'];
    const next = flow[o.status] || [];
    const act = (to) => { setBusy(to); onAdvance(o, { status: to, courier, tracking, notes, relist }).then((ok) => { if (ok) setOpen(false); }).finally(() => setBusy(null)); };
    return (
        <>
            <tr className="border-t border-gray-100 align-top">
                <td className="py-3 pr-4"><p className="font-mono text-xs font-bold text-ink">{o.order_id}</p><p className="text-xs text-gray-500">{when(o.paid_at || o.created_at)}</p></td>
                <td className="py-3 pr-4"><div className="flex items-center gap-3"><img src={o.image_url} alt="" className="w-10 h-10 rounded object-cover" /><div><p className="font-bold text-ink">{o.title}</p><p className="text-xs text-gray-500">{o.artist_name}</p></div></div></td>
                <td className="py-3 pr-4 text-sm">{canFulfil ? <p className="font-semibold text-ink">{o.buyer_name}</p> : <p className="font-semibold text-ink">A collector</p>}<p className="text-xs text-gray-500">{o.city}, {o.country}</p></td>
                <td className="py-3 pr-4 text-sm font-mono font-bold text-ink">{o.currency === 'INR' ? inr(o.amount) : usd(o.amount)}<p className="font-sans font-normal text-xs text-gray-500">{o.gateway === 'TEST' ? 'Test' : o.gateway === 'PAYPAL' ? 'PayPal' : 'Razorpay'}</p></td>
                <td className="py-3 pr-4"><span className={`inline-flex rounded-full border px-2.5 py-0.5 text-xs font-bold ${tone}`}>{label}</span>{o.tracking_number && <p className="mt-1 text-xs text-gray-500 font-mono">{o.courier} {o.tracking_number}</p>}</td>
                <td className="py-3 text-right">{canFulfil && next.length > 0 ? <button type="button" onClick={() => setOpen(!open)} className="h-9 px-4 rounded-full bg-ink text-white text-sm font-bold">{open ? 'Close' : 'Update'}</button> : canFulfil ? <a href={`mailto:${o.buyer_email}`} className="text-sm font-bold text-gray-500 hover:text-ink">Email buyer</a> : null}</td>
            </tr>
            {open && (
                <tr className="bg-gray-50"><td colSpan={6} className="p-5">
                    <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                        <div className="md:col-span-2 text-sm"><p className="font-bold text-ink">Ship to</p><p className="text-gray-700">{o.buyer_name}, {o.address_line1}{o.address_line2 ? `, ${o.address_line2}` : ''}, {o.city}{o.state ? `, ${o.state}` : ''} {o.postal_code}, {o.country}</p><p className="text-gray-500">{o.buyer_email}{o.buyer_phone ? ` · ${o.buyer_phone}` : ''}</p>{o.staff_notes && <p className="mt-2 text-gray-500">Notes: {o.staff_notes}</p>}</div>
                        {next.includes('SHIPPED') && <><input value={courier} onChange={(e) => setCourier(e.target.value)} placeholder="Courier (e.g. Blue Dart)" className="h-11 rounded-xl border border-gray-300 bg-white px-3 text-sm" aria-label="Courier" /><input value={tracking} onChange={(e) => setTracking(e.target.value)} placeholder="Tracking number" className="h-11 rounded-xl border border-gray-300 bg-white px-3 text-sm font-mono" aria-label="Tracking number" /></>}
                        <input value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="Internal note (optional)" className="md:col-span-2 h-11 rounded-xl border border-gray-300 bg-white px-3 text-sm" aria-label="Internal note" />
                        {next.includes('REFUNDED') && o.status !== 'REFUND_REQUIRED' && <label className="md:col-span-2 flex items-center gap-2 text-sm"><input type="checkbox" checked={relist} onChange={(e) => setRelist(e.target.checked)} className="rounded text-brand" />After refunding, put the painting back on sale (otherwise archive it)</label>}
                    </div>
                    <div className="mt-4 flex flex-wrap gap-2">
                        {next.map((to) => <button key={to} type="button" disabled={!!busy} onClick={() => act(to)} className={`h-10 px-4 rounded-full text-sm font-bold disabled:opacity-60 ${to === 'REFUNDED' ? 'border border-red-300 text-red-700 hover:bg-red-50' : 'bg-ink text-white'}`}>{busy === to ? '…' : NEXT_LABEL[to]}</button>)}
                    </div>
                </td></tr>
            )}
        </>
    );
}

export default function Marketplace() {
    const { admin = {}, listings: initial = [], kpis = {}, orders: initialOrders = [], holds: initialHolds, orderFlow = {}, serverNow, tab: initialTab } = usePage().props;
    const caps = admin.capabilities || [];
    const publisher = caps.includes('marketplace.publish');
    const editor = caps.includes('marketplace.edit');
    const canCreate = editor || caps.includes('marketplace.own');
    const canFulfil = caps.includes('marketplace.orders');
    const [items, setItems] = useState(initial);
    const [orders, setOrders] = useState(initialOrders);
    const [holds, setHolds] = useState(initialHolds || []);
    const review = items.filter((l) => l.raw_status === 'PENDING_REVIEW');
    const tabs = [
        publisher && ['review', 'Review queue', review.length],
        ['listings', editor || publisher ? 'All listings' : 'My listings', items.length],
        publisher && ['holds', 'Live holds', holds.length],
        ['orders', canFulfil ? 'Orders & fulfilment' : 'My sales', orders.length],
    ].filter(Boolean);
    const [tab, setTab] = useState(initialTab && tabs.some((t) => t[0] === initialTab) ? initialTab : (publisher && review.length ? 'review' : 'listings'));
    const [status, setStatus] = useState('ALL');
    const [q, setQ] = useState('');
    const [toastNode, toast] = useToast();

    const shown = useMemo(() => items.filter((l) => (status === 'ALL' || l.raw_status === status)
        && (!q || `${l.title} ${l.artist_name} ${l.medium}`.toLowerCase().includes(q.toLowerCase()))), [items, status, q]);
    const count = (s) => items.filter((l) => l.raw_status === s).length;
    const patch = (id, changes) => setItems((xs) => xs.map((x) => (x.id === id ? { ...x, ...changes } : x)));

    const decide = (l, decision, notes) => axios.post(`/admin/console/api/marketplace/paintings/${l.id}/review`, { decision, notes })
        .then((r) => { patch(l.id, { raw_status: r.data.status, stock_status: r.data.status, review_notes: notes }); toast(decision === 'approve' ? `“${l.title}” is live` : 'Sent back to the artist with your notes'); })
        .catch((e) => toast(errorText(e), 'error'));
    const setListing = (l, to) => axios.post(`/admin/console/api/marketplace/paintings/${l.id}/status`, { status: to })
        .then(() => { patch(l.id, { raw_status: to, stock_status: to }); toast({ AVAILABLE: 'Published', DRAFT: 'Moved to drafts (off the marketplace)', ARCHIVED: 'Archived' }[to]); })
        .catch((e) => toast(errorText(e), 'error'));
    const feature = (l) => axios.post(`/admin/console/api/marketplace/paintings/${l.id}/feature`, { featured: !l.is_featured })
        .then((r) => { patch(l.id, { is_featured: r.data.featured }); toast(r.data.featured ? 'Featured in the marketplace hero' : 'No longer featured'); })
        .catch((e) => toast(errorText(e), 'error'));
    const release = (h) => axios.post(`/admin/console/api/marketplace/paintings/${h.painting_id}/release`)
        .then(() => { setHolds((xs) => xs.filter((x) => x.id !== h.id)); patch(h.painting_id, { raw_status: 'AVAILABLE', stock_status: 'AVAILABLE' }); toast('Hold released, the piece is back on sale'); })
        .catch((e) => toast(errorText(e), 'error'));
    const advance = (o, body) => axios.post(`/admin/console/api/marketplace/orders/${o.order_id}`, body)
        .then((r) => { setOrders((xs) => xs.map((x) => (x.order_id === o.order_id ? { ...x, ...r.data } : x))); toast(`Order ${o.order_id}: ${(ORDER_LABEL[r.data.status] || [r.data.status])[0]}`); return true; })
        .catch((e) => { toast(errorText(e), 'error'); return false; });

    const canEditRow = (l) => (editor || l.mine) && ['DRAFT', 'PENDING_REVIEW', 'AVAILABLE', 'ARCHIVED'].includes(l.raw_status);

    return (
        <AdminLayout title="Marketplace">
            <PageHeader title="Art marketplace"
                        subtitle={editor ? 'Every 1-of-1 original: list, review, watch live holds and ship sold work.' : publisher ? 'Review new originals, keep the gallery tidy and get sold work to collectors.' : 'List your originals and follow their review and sales.'}
                        actions={<>
                            <a href="/marketplace" target="_blank" rel="noopener" className="inline-flex items-center h-11 px-5 rounded-full border border-gray-300 bg-white font-bold hover:border-ink"><i className="fa fa-external-link mr-2" aria-hidden="true"></i>Open marketplace</a>
                            {canCreate && <a href="/admin/dashboard/marketplace/new" className="inline-flex items-center h-11 px-5 rounded-full bg-brand text-white font-bold hover:bg-brand-dark"><i className="fa fa-plus mr-2" aria-hidden="true"></i>List an original</a>}
                        </>} />

            <div className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-6 gap-4 mb-8">
                <Kpi label="Live" value={kpis.live || 0} icon="fa-eye" onClick={() => { setTab('listings'); setStatus('AVAILABLE'); }} />
                <Kpi label="On hold" value={kpis.held || 0} icon="fa-lock" tone="text-yellow-600" onClick={publisher ? () => setTab('holds') : undefined} />
                <Kpi label="In review" value={kpis.review || 0} icon="fa-hourglass-half" tone="text-blue-700" onClick={() => { if (publisher) setTab('review'); else { setTab('listings'); setStatus('PENDING_REVIEW'); } }} />
                <Kpi label="To ship" value={kpis.toShip || 0} icon="fa-truck" tone={kpis.toShip ? 'text-brand' : 'text-ink'} onClick={() => setTab('orders')} />
                <Kpi label="Sales (INR)" value={inr(kpis.revenueInr)} icon="fa-line-chart" />
                <Kpi label="Live inventory" value={inr(kpis.inventoryValueInr)} icon="fa-diamond" />
            </div>
            {Number(kpis.refunds) > 0 && canFulfil && (
                <div className="mb-6 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800 flex items-center justify-between gap-3" role="alert">
                    <span><i className="fa fa-exclamation-triangle mr-2" aria-hidden="true"></i><strong>{kpis.refunds} payment{kpis.refunds > 1 ? 's' : ''} arrived after the piece was sold.</strong> Refund in the gateway dashboard, then record it here.</span>
                    <button type="button" onClick={() => setTab('orders')} className="font-bold underline whitespace-nowrap">Open orders</button>
                </div>
            )}

            <div className="flex gap-2 border-b border-gray-200 mb-6 overflow-x-auto" role="tablist">
                {tabs.map(([k, l, n]) => (
                    <button key={k} type="button" role="tab" aria-selected={tab === k} onClick={() => setTab(k)}
                            className={`whitespace-nowrap px-4 py-3 text-base font-bold border-b-2 -mb-px ${tab === k ? 'border-ink text-ink' : 'border-transparent text-gray-500 hover:text-ink'}`}>
                        {l} <span className={`ml-1 rounded-full px-2 py-0.5 text-xs ${k === 'review' && n ? 'bg-brand text-white' : 'bg-gray-100 text-gray-600'}`}>{n}</span>
                    </button>
                ))}
            </div>

            {tab === 'review' && (
                review.length ? <div className="space-y-5">{review.map((l) => <ReviewCard key={l.id} l={l} onDecide={decide} />)}</div>
                    : <div className="rounded-2xl border-2 border-dashed border-gray-300 p-12 text-center"><p className="text-xl font-extrabold text-ink">All caught up</p><p className="mt-1 text-gray-600">New originals from instructors and students appear here for review.</p></div>
            )}

            {tab === 'listings' && (
                <div>
                    <div className="flex flex-wrap items-center gap-2 mb-4">
                        {[['ALL', 'All', items.length], ['AVAILABLE', 'Live', count('AVAILABLE')], ['RESERVED', 'On hold', count('RESERVED')], ['PENDING_REVIEW', 'In review', count('PENDING_REVIEW')], ['DRAFT', 'Drafts', count('DRAFT')], ['SOLD', 'Sold', count('SOLD')], ['ARCHIVED', 'Archived', count('ARCHIVED')]].map(([k, l, n]) => (
                            <button key={k} type="button" onClick={() => setStatus(k)} className={`h-9 px-3 rounded-full text-sm font-bold border ${status === k ? 'bg-ink text-white border-ink' : 'bg-white border-gray-300 hover:border-ink'}`}>{l} <span className="opacity-60">{n}</span></button>
                        ))}
                        <input type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search listings" className="ml-auto h-10 w-64 rounded-full border border-gray-300 bg-white px-4 text-sm" aria-label="Search listings" />
                    </div>
                    <div className="rounded-2xl border border-gray-200 bg-white overflow-x-auto">
                        <table className="w-full text-left">
                            <thead><tr className="text-xs font-mono uppercase tracking-widest text-gray-500"><th className="p-4">Artwork</th><th className="p-4">Status</th><th className="p-4">Price</th><th className="p-4 hidden lg:table-cell">Updated</th><th className="p-4 text-right">Actions</th></tr></thead>
                            <tbody>
                                {shown.map((l) => (
                                    <tr key={l.id} className="border-t border-gray-100 align-middle">
                                        <td className="p-4"><div className="flex items-center gap-3">
                                            {l.image_url ? <img src={l.image_url} alt="" className="w-14 h-14 rounded-lg object-cover flex-none" /> : <span className="w-14 h-14 rounded-lg bg-gray-100 flex-none"></span>}
                                            <div className="min-w-0"><p className="font-bold text-ink truncate">{l.title} {l.is_featured && <i className="fa fa-star text-yellow-500 text-xs" title="Featured" aria-label="Featured"></i>}</p><p className="text-xs text-gray-500 truncate">{l.artist_name} · {l.medium} · {dims(l)}{l.source === 'STUDENT' ? ' · student' : ''}</p>
                                                {l.raw_status === 'DRAFT' && l.review_notes && l.review_notes !== 'Approved.' && <p className="text-xs text-red-600 truncate max-w-xs" title={l.review_notes}>Changes requested: {l.review_notes}</p>}</div>
                                        </div></td>
                                        <td className="p-4"><StatusChip status={l.raw_status} />{l.order_id && <p className="mt-1 text-xs font-mono text-gray-500">{l.order_id}</p>}</td>
                                        <td className="p-4 font-mono text-sm font-bold text-ink">{inr(l.final_price)}{Number(l.discount_percentage) > 0 && <p className="font-sans font-normal text-xs text-gray-400"><span className="line-through">{inr(l.base_price)}</span> −{num(l.discount_percentage, 0)}%</p>}</td>
                                        <td className="p-4 text-sm text-gray-500 hidden lg:table-cell">{when(l.updated_at)}</td>
                                        <td className="p-4">
                                            <div className="flex flex-wrap justify-end gap-1.5">
                                                {canEditRow(l) && <a href={`/admin/dashboard/marketplace/${l.id}/edit`} className="h-9 px-3 inline-flex items-center rounded-full border border-gray-300 text-sm font-bold hover:border-ink">Edit</a>}
                                                <a href={`/marketplace/${l.slug}`} target="_blank" rel="noopener" className="h-9 w-9 inline-flex items-center justify-center rounded-full border border-gray-300 hover:border-ink" title="View page" aria-label="View page"><i className="fa fa-eye" aria-hidden="true"></i></a>
                                                {publisher && ['DRAFT', 'ARCHIVED'].includes(l.raw_status) && <button type="button" onClick={() => setListing(l, 'AVAILABLE')} className="h-9 px-3 rounded-full bg-ink text-white text-sm font-bold">Publish</button>}
                                                {publisher && l.raw_status === 'PENDING_REVIEW' && <button type="button" onClick={() => setTab('review')} className="h-9 px-3 rounded-full bg-blue-600 text-white text-sm font-bold">Review</button>}
                                                {(publisher || l.mine) && ['AVAILABLE', 'PENDING_REVIEW'].includes(l.raw_status) && <button type="button" onClick={() => setListing(l, 'DRAFT')} className="h-9 px-3 rounded-full border border-gray-300 text-sm font-bold hover:border-ink">Unpublish</button>}
                                                {(publisher || l.mine) && ['DRAFT', 'AVAILABLE'].includes(l.raw_status) && <button type="button" onClick={() => setListing(l, 'ARCHIVED')} className="h-9 w-9 rounded-full border border-gray-300 hover:border-ink" title="Archive" aria-label="Archive"><i className="fa fa-archive" aria-hidden="true"></i></button>}
                                                {publisher && l.raw_status === 'AVAILABLE' && <button type="button" onClick={() => feature(l)} className={`h-9 w-9 rounded-full border ${l.is_featured ? 'border-yellow-400 text-yellow-500' : 'border-gray-300 text-gray-400'} hover:border-ink`} title={l.is_featured ? 'Unfeature' : 'Feature'} aria-label="Toggle featured"><i className="fa fa-star" aria-hidden="true"></i></button>}
                                                {publisher && l.raw_status === 'RESERVED' && <button type="button" onClick={() => setTab('holds')} className="h-9 px-3 rounded-full border border-yellow-400 text-sm font-bold text-yellow-700">See hold</button>}
                                            </div>
                                        </td>
                                    </tr>
                                ))}
                                {shown.length === 0 && <tr><td colSpan={5} className="p-12 text-center text-gray-500">No listings here. {canCreate && <a href="/admin/dashboard/marketplace/new" className="font-bold text-brand">List an original</a>}</td></tr>}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}

            {tab === 'holds' && (
                <div className="rounded-2xl border border-gray-200 bg-white p-4 overflow-x-auto">
                    <p className="px-2 pb-3 text-sm text-gray-600">Each hold lasts 15 minutes (topped up by 5 once payment starts). Expired holds are released automatically every minute. Release one manually only if a collector asks, or a hold is clearly stuck.</p>
                    {holds.length ? (
                        <table className="w-full text-left">
                            <thead><tr className="text-xs font-mono uppercase tracking-widest text-gray-500"><th className="py-2 pr-4">Artwork</th><th className="py-2 pr-4">Collector</th><th className="py-2 pr-4">Stage</th><th className="py-2 pr-4">Time left</th><th></th></tr></thead>
                            <tbody>{holds.map((h) => <HoldRow key={h.id} h={h} serverNow={serverNow} onRelease={release} />)}</tbody>
                        </table>
                    ) : <p className="p-10 text-center text-gray-500">No one is holding a piece right now.</p>}
                </div>
            )}

            {tab === 'orders' && (
                <div>
                    {canFulfil && (
                        <div className="grid grid-cols-2 md:grid-cols-5 gap-3 mb-4">
                            {['PAID', 'PACKED', 'SHIPPED', 'DELIVERED', 'REFUND_REQUIRED'].map((s) => (
                                <div key={s} className="rounded-xl border border-gray-200 bg-white p-4"><p className="text-2xl font-black text-ink">{orders.filter((o) => o.status === s).length}</p><p className="text-xs font-bold text-gray-500">{ORDER_LABEL[s][0]}</p></div>
                            ))}
                        </div>
                    )}
                    <div className="rounded-2xl border border-gray-200 bg-white p-4 overflow-x-auto">
                        {orders.length ? (
                            <table className="w-full text-left">
                                <thead><tr className="text-xs font-mono uppercase tracking-widest text-gray-500"><th className="py-2 pr-4">Order</th><th className="py-2 pr-4">Artwork</th><th className="py-2 pr-4">Collector</th><th className="py-2 pr-4">Amount</th><th className="py-2 pr-4">Status</th><th></th></tr></thead>
                                <tbody>{orders.map((o) => <OrderRow key={o.order_id} o={o} flow={orderFlow} canFulfil={canFulfil} onAdvance={advance} />)}</tbody>
                            </table>
                        ) : <p className="p-10 text-center text-gray-500">No sales yet.</p>}
                    </div>
                </div>
            )}
            {toastNode}
        </AdminLayout>
    );
}
