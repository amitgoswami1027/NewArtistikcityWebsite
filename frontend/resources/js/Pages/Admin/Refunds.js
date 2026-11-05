import React, { useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import { Inertia } from '@inertiajs/inertia';
import axios from 'axios';
import AdminLayout, { PageHeader, useToast, errorText } from '@/Components/Admin/AdminLayout';
import { Button, Badge, DataTable, Pagination, SearchBox, FilterSelect, Segments, Toolbar, EmptyState, Drawer, Stat, useGrid, fmtDate, money } from '@/Components/Admin/kit';

/*
 * Admin > Sales > Refunds. Payments that must go back to the buyer (a course paid twice, an original sold to someone
 * else first, prints that couldn't be supplied, a cancelled live course) wait here. Nothing is refunded automatically:
 * staff open one, tick "confirm" and press Refund, which calls Razorpay / PayPal. The Reconciliation tab lists
 * payments the scheduled gateway check matched after the browser and the webhook missed them.
 */

const STATUS = {
    PENDING: { label: 'Waiting', tone: 'amber' },
    PROCESSING: { label: 'Sending', tone: 'blue' },
    REFUNDED: { label: 'Refunded', tone: 'green' },
    FAILED: { label: 'Failed', tone: 'red' },
    DISMISSED: { label: 'Dismissed', tone: 'grey' },
};
const REASON = {
    DUPLICATE_PAYMENT: 'Paid twice',
    SOLD_TO_OTHER: 'Sold to someone else',
    NOT_SUPPLIED: "Couldn't be supplied",
    COURSE_CANCELLED: 'Course cancelled',
    OTHER: 'Other',
};
const SOURCE = { COURSE: 'Course', MARKETPLACE: 'Original', SHOP: 'Shop' };
const orderLink = (r) => (r.source === 'COURSE' ? `/admin/orders?order=${r.source_ref}` : `/admin/dashboard/marketplace?order=${encodeURIComponent(r.source_ref)}`);

export default function Refunds() {
    const { stats = {}, reconciliations = [] } = usePage().props;
    const g = useGrid('grid', ['stats']);
    const [toastNode, toast] = useToast();
    const [tab, setTab] = useState(() => (typeof window !== 'undefined' && new URLSearchParams(window.location.search).get('tab') === 'reconcile' ? 'reconcile' : 'queue'));
    const [open, setOpen] = useState(null);
    const [checking, setChecking] = useState(false);

    const check = () => {
        setChecking(true);
        axios.post('/admin/api/payments/reconcile').then((r) => {
            const d = r.data;
            toast(d.notes && d.notes.length ? d.notes.join(' · ') : `Checked ${d.checked} open orders: nothing new.`);
            Inertia.reload({ only: ['grid', 'stats', 'reconciliations'], preserveScroll: true });
        }).catch((e) => toast(errorText(e), 'error')).finally(() => setChecking(false));
    };

    const columns = [
        { key: 'date', label: 'Flagged', sort: 'date', render: (r) => <span className="adm-cell__text"><b>{fmtDate(r.created_at, true)}</b><small>#{r.id}</small></span> },
        { key: 'order', label: 'Order', render: (r) => (
            <span className="adm-cell__text"><span><Badge>{SOURCE[r.source] || r.source}</Badge> <a href={orderLink(r)}>{r.source_ref}</a></span><small>{r.buyer_email || '—'}</small></span>
        ) },
        { key: 'reason', label: 'Why', render: (r) => <span className="adm-cell__text"><b>{REASON[r.reason] || r.reason}</b>{r.detail && <small>{r.detail}</small>}</span> },
        { key: 'amount', label: 'Amount', sort: 'amount', align: 'right', render: (r) => <b>{money(r.amount, String(r.currency).toLowerCase())}</b> },
        { key: 'gateway', label: 'Gateway', hide: 'md', render: (r) => <span className="adm-cell__text"><span>{r.gateway}</span><small className="adm-mono">{r.payment_ref}</small></span> },
        { key: 'status', label: 'Status', render: (r) => { const s = STATUS[r.status] || { label: r.status }; return <Badge tone={s.tone} dot>{s.label}</Badge>; } },
        { key: 'open', label: <span className="sr-only">Decide</span>, align: 'right', render: (r) => (
            <Button size="sm" variant={r.status === 'PENDING' || r.status === 'FAILED' ? 'dark' : 'line'} onClick={() => setOpen(r)}>{r.status === 'PENDING' || r.status === 'FAILED' ? 'Decide' : 'Details'}</Button>
        ) },
    ];
    const anyFilter = g.meta.q || Object.keys(g.meta.filters || {}).length > 0;

    return (
        <AdminLayout title="Refunds">
            <PageHeader eyebrow="Sales" title="Refunds"
                subtitle="Payments that must go back to the buyer. Nothing is refunded until you confirm it here; the refund is then sent through Razorpay or PayPal."
                actions={<Button icon="fa-refresh" busy={checking} onClick={check}>Check gateways now</Button>} />
            <div className="adm-stats">
                <Stat icon="fa-hourglass-half" label="Waiting for a decision" value={stats.open || 0} tone={stats.open ? 'amber' : undefined} />
                <Stat icon="fa-inr" label="Open amount (INR)" value={money(stats.openInr, 'inr')} />
                <Stat icon="fa-usd" label="Open amount (USD)" value={money(stats.openUsd, 'usd')} />
                <Stat icon="fa-check" label="Refunded (30 days)" value={stats.refunded30 || 0} />
            </div>
            <div className="adm-tabs" role="tablist" aria-label="Refunds views">
                <button type="button" role="tab" aria-selected={tab === 'queue'} className={tab === 'queue' ? 'is-on' : ''} onClick={() => setTab('queue')}>Queue {stats.open ? <Badge tone="amber">{stats.open}</Badge> : null}</button>
                <button type="button" role="tab" aria-selected={tab === 'reconcile'} className={tab === 'reconcile' ? 'is-on' : ''} onClick={() => setTab('reconcile')}>Reconciliation log</button>
            </div>
            {tab === 'queue' ? (
                <>
                    <Toolbar end={<Segments label="Status" value={g.filter('status')} onChange={(v) => g.go({ status: v })}
                        options={[{ value: '', label: 'All' }, { value: 'open', label: 'To decide', count: stats.open }, { value: 'FAILED', label: 'Failed', count: stats.failed }, { value: 'REFUNDED', label: 'Refunded' }, { value: 'DISMISSED', label: 'Dismissed' }]} />}>
                        <SearchBox value={g.meta.q} onSearch={g.search} placeholder="Order, payment id or e-mail" />
                        <FilterSelect label="Kind" value={g.filter('source')} onChange={(v) => g.go({ source: v })} options={Object.entries(SOURCE).map(([value, label]) => ({ value, label }))} />
                    </Toolbar>
                    <DataTable columns={columns} rows={g.rows} meta={g.meta} loading={g.loading} onSort={g.sortBy} caption="Refunds" onRowClick={(r) => setOpen(r)}
                        empty={anyFilter ? <EmptyState icon="fa-search" title="No refunds match these filters" action={<Button onClick={() => g.go({ q: '', status: '', source: '' })}>Clear filters</Button>} />
                            : <EmptyState icon="fa-check-circle" title="No refunds waiting">When a payment has to go back, it appears here.</EmptyState>} />
                    <Pagination meta={g.meta} onPage={g.setPage} onPer={g.setPer} />
                </>
            ) : <Reconciliations rows={reconciliations} />}
            {open && <RefundDrawer refund={open} onClose={() => setOpen(null)} toast={toast} onDone={() => { g.reload(); setOpen(null); }} />}
            {toastNode}
        </AdminLayout>
    );
}

function Reconciliations({ rows }) {
    const tone = { PAID: 'green', SOLD: 'green', CAPTURED: 'green', REFUND_REQUIRED: 'amber', PARTIAL: 'amber', AMOUNT_MISMATCH: 'red', ERROR: 'red' };
    if (!rows.length) {
        return <EmptyState icon="fa-exchange" title="Nothing reconciled yet">Every few minutes the site asks Razorpay and PayPal about orders still waiting for a confirmation. Matches show up here.</EmptyState>;
    }
    return (
        <div className="adm-dense">
            <DataTable caption="Reconciliation log" rows={rows} columns={[
                { key: 'time', label: 'When', render: (r) => fmtDate(r.created_at, true) },
                { key: 'order', label: 'Order', render: (r) => <span className="adm-cell__text"><b>{SOURCE[r.source] || r.source} {r.source_ref}</b><small>{r.gateway} {r.payment_ref || ''}</small></span> },
                { key: 'outcome', label: 'Outcome', render: (r) => <Badge tone={tone[r.outcome] || 'grey'}>{r.outcome}</Badge> },
                { key: 'late', label: 'Late by', align: 'right', render: (r) => (r.minutes_late ? `${r.minutes_late} min` : '—') },
                { key: 'detail', label: 'Detail', hide: 'md', render: (r) => r.detail || '—' },
            ]} />
        </div>
    );
}

function RefundDrawer({ refund, onClose, toast, onDone }) {
    const [r, setR] = useState(refund);
    const [confirm, setConfirm] = useState(false);
    const [note, setNote] = useState('');
    const [busy, setBusy] = useState(null);
    const [err, setErr] = useState(null);
    const open = r.status === 'PENDING' || r.status === 'FAILED';
    const act = (kind) => {
        setBusy(kind); setErr(null);
        axios.post(`/admin/api/payments/refunds/${r.id}/${kind}`, { confirm, note }).then((x) => {
            toast(x.data.message); setR(x.data.refund); onDone();
        }).catch((e) => {
            const d = e.response && e.response.data;
            setErr(d && d.errors ? Object.values(d.errors)[0] : errorText(e));
            if (d && d.refund) setR(d.refund);
        }).finally(() => setBusy(null));
    };
    const s = STATUS[r.status] || { label: r.status };
    return (
        <Drawer open title={`Refund #${r.id}`} subtitle={`${SOURCE[r.source] || r.source} order ${r.source_ref}`} onClose={onClose}
            footer={<Button onClick={onClose}>Close</Button>}>
            <div className="adm-stack">
                <div className="adm-inline"><Badge tone={s.tone} dot>{s.label}</Badge><b className="adm-amount">{money(r.amount, String(r.currency).toLowerCase())}</b><span className="adm-muted">{r.gateway}</span></div>
                <dl className="adm-dl">
                    <dt>Why</dt><dd>{REASON[r.reason] || r.reason}{r.detail && <> — {r.detail}</>}</dd>
                    <dt>Buyer</dt><dd>{r.buyer_email || '—'}</dd>
                    <dt>Order</dt><dd><a href={orderLink(r)}>{r.source_ref}</a></dd>
                    <dt>Payment</dt><dd className="adm-mono">{r.payment_ref}</dd>
                    <dt>Flagged</dt><dd>{fmtDate(r.created_at, true)}</dd>
                    {r.decided_at && <><dt>Decided</dt><dd>{fmtDate(r.decided_at, true)}{r.decided_by_name && ` by ${r.decided_by_name}`}</dd></>}
                    {r.gateway_refund_id && <><dt>Refund id</dt><dd className="adm-mono">{r.gateway_refund_id}</dd></>}
                    {r.note && <><dt>Note</dt><dd>{r.note}</dd></>}
                    {r.error && <><dt>Last error</dt><dd className="adm-small">{r.error}</dd></>}
                </dl>
                {open && (
                    <div className="adm-task is-edit">
                        <h3 className="adm-sub">Decide</h3>
                        <label className="adm-check" htmlFor="rf-confirm">
                            <input id="rf-confirm" type="checkbox" checked={confirm} onChange={(e) => setConfirm(e.target.checked)} />
                            Send {money(r.amount, String(r.currency).toLowerCase())} back to the buyer through {r.gateway === 'PAYPAL' ? 'PayPal' : r.gateway === 'RAZORPAY' ? 'Razorpay' : r.gateway.toLowerCase()}
                        </label>
                        <label className="adm-filter" htmlFor="rf-note" style={{ display: 'block' }}><span>Note (required to dismiss)</span></label>
                        <textarea id="rf-note" className="adm-input" rows={2} value={note} onChange={(e) => setNote(e.target.value)} placeholder="e.g. Buyer asked for a refund instead of a swap" />
                        {err && <div className="adm-alert is-error" role="alert"><i className="fa fa-exclamation-circle" aria-hidden="true"></i>{err}</div>}
                        <div className="adm-inline">
                            <Button variant="danger" icon="fa-undo" disabled={!confirm} busy={busy === 'refund'} onClick={() => act('refund')}>Refund now</Button>
                            <Button variant="line" busy={busy === 'dismiss'} onClick={() => act('dismiss')}>No refund</Button>
                        </div>
                    </div>
                )}
            </div>
        </Drawer>
    );
}
