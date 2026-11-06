import React, { useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import { Inertia } from '@inertiajs/inertia';
import axios from 'axios';
import AdminLayout, { PageHeader, useToast, errorText } from '@/Components/Admin/AdminLayout';
import { Button, Badge, DataTable, Pagination, SearchBox, FilterSelect, Toolbar, EmptyState, Drawer, Stat, useGrid, fmtDate, money } from '@/Components/Admin/kit';

/*
 * Admin > Sales > Payouts & ledger. The escrow ledger (append-only) as a finance report, artist balances, weekly
 * payout batches (pay by bank transfer, then mark the batch paid with the reference), disputes and every entry.
 */
const inr = (v) => money(v, 'inr');
const TYPE_TONE = { SALE: 'green', COMMISSION: 'blue', ARTIST_SHARE: 'pink', REFUND: 'red', PAYOUT: 'amber', DISPUTE_LOSS: 'red', ADJUSTMENT: 'grey' };

export default function Finance() {
    const { totals = {}, monthly = [], balances = [], batches = [], disputes = [], rules = {} } = usePage().props;
    const g = useGrid('grid');
    const [toastNode, toast] = useToast();
    const [tab, setTab] = useState(() => (typeof window !== 'undefined' && new URLSearchParams(window.location.search).get('tab')) || 'report');
    const [batch, setBatch] = useState(null);
    const [payee, setPayee] = useState(null);
    const [busy, setBusy] = useState(null);
    const [dispute, setDispute] = useState({ source: 'MARKETPLACE', ref: '', reason: '' });
    const reload = () => Inertia.reload({ preserveScroll: true });
    const call = (key, url, body, after) => {
        setBusy(key);
        return axios.post(url, body || {}).then((r) => { toast(r.data.message); if (after) after(r.data); reload(); })
            .catch((e) => { const d = e.response && e.response.data; toast(d && d.errors ? Object.values(d.errors)[0] : errorText(e), 'error'); })
            .finally(() => setBusy(null));
    };
    const draft = batches.find((b) => b.status === 'DRAFT');
    const openDisputes = disputes.filter((d) => d.status === 'OPEN').length;
    const TABS = [['report', 'Report'], ['artists', `Artists (${balances.length})`], ['payouts', 'Payouts'], ['disputes', `Disputes${openDisputes ? ` (${openDisputes})` : ''}`], ['ledger', 'Ledger']];

    return (
        <AdminLayout title="Payouts & ledger">
            <PageHeader eyebrow="Sales" title="Payouts & ledger"
                subtitle={`Buyers' money is held in escrow. Each sale is split: ${rules.commissionPercent}% commission to ArtistikCity, the rest to the artist, payable ${rules.payoutAfterDays} days after the sale. Payouts go out in weekly batches.`}
                actions={<Button as="a" icon="fa-download" href={g.exportUrl('/admin/finance/export')}>Export ledger</Button>} />
            {totals.balanced === false && <div className="adm-alert is-error" role="alert"><i className="fa fa-exclamation-triangle" aria-hidden="true"></i>The ledger does not balance (escrow ≠ commission + artist shares). Check the latest entries.</div>}
            <div className="adm-stats">
                <Stat icon="fa-lock" label="Held in escrow (all time, net)" value={inr(Number(totals.escrow || 0) - Number(totals.paid_out || 0))} />
                <Stat icon="fa-percent" label="Commission earned" value={inr(totals.platform)} />
                <Stat icon="fa-user" label="Owed to artists" value={inr(totals.owed)} />
                <Stat icon="fa-paper-plane" label="Paid out" value={inr(totals.paid_out)} />
            </div>
            <div className="adm-tabs" role="tablist" aria-label="Finance views">
                {TABS.map(([k, l]) => <button key={k} type="button" role="tab" aria-selected={tab === k} className={tab === k ? 'is-on' : ''} onClick={() => setTab(k)}>{l}</button>)}
            </div>

            {tab === 'report' && (
                monthly.length === 0 ? <EmptyState icon="fa-bar-chart" title="No sales in the ledger yet">Paid orders appear here within 10 minutes.</EmptyState> : (
                    <DataTable caption="Finance report by month" rows={monthly} rowKey="month" columns={[
                        { key: 'month', label: 'Month', render: (r) => <b>{r.month}</b> },
                        { key: 'gross', label: 'Gross sales', align: 'right', render: (r) => inr(r.gross) },
                        { key: 'refunds', label: 'Refunds', align: 'right', render: (r) => (Number(r.refunds) ? <span className="adm-neg">−{inr(r.refunds)}</span> : '—') },
                        { key: 'commission', label: 'Commission (net)', align: 'right', render: (r) => inr(r.commission) },
                        { key: 'artist', label: 'Artist share (net)', align: 'right', render: (r) => inr(r.artist_share) },
                        { key: 'payouts', label: 'Paid out', align: 'right', render: (r) => inr(r.payouts) },
                    ]} />
                )
            )}

            {tab === 'artists' && (
                balances.length === 0 ? <EmptyState icon="fa-users" title="No artist earnings yet">Sales of student (and, if enabled, studio) artworks create artist balances.</EmptyState> : (
                    <DataTable caption="Artist balances" rows={balances} rowKey="payee_id" columns={[
                        { key: 'who', label: 'Artist', render: (r) => <span className="adm-cell__text"><b>{r.payee_name || `#${r.payee_id}`}</b><small>{r.payee_type === 'USER' ? 'Student artist' : 'Studio artist'} · id {r.payee_id}</small></span> },
                        { key: 'earned', label: 'Earned', align: 'right', render: (r) => inr(r.earned) },
                        { key: 'reversed', label: 'Refunds', align: 'right', render: (r) => (Number(r.reversed) ? <span className="adm-neg">{inr(r.reversed)}</span> : '—') },
                        { key: 'paid', label: 'Paid', align: 'right', render: (r) => inr(r.paid) },
                        { key: 'holding', label: 'In holding period', align: 'right', render: (r) => (Number(r.holding) ? inr(r.holding) : '—') },
                        { key: 'disputed', label: 'On hold (dispute)', align: 'right', render: (r) => (Number(r.disputed) ? <Badge tone="red">{inr(r.disputed)}</Badge> : '—') },
                        { key: 'balance', label: 'Balance', align: 'right', render: (r) => <b>{inr(r.balance)}</b> },
                        { key: 'pay', label: <span className="sr-only">Payout details</span>, align: 'right', render: (r) => (r.payee_type === 'USER'
                            ? <Button size="sm" icon="fa-university" onClick={() => setPayee(r.payee_id)}>Payout details</Button> : null) },
                    ]} />
                )
            )}

            {tab === 'payouts' && (
                <>
                    <div className="adm-inline" style={{ marginBottom: 12 }}>
                        <Button variant="dark" icon="fa-plus" busy={busy === 'batch'} disabled={!!draft} onClick={() => call('batch', '/admin/api/finance/batches')}>Create batch now</Button>
                        <span className="adm-muted">{draft ? `Draft #${draft.id} is open: pay it, then mark it paid.` : 'A draft batch is created automatically every Monday morning.'}</span>
                    </div>
                    <DataTable caption="Payout batches" rows={batches} onRowClick={(b) => setBatch(b.id)} columns={[
                        { key: 'id', label: 'Batch', render: (b) => <span className="adm-cell__text"><b>#{b.id}</b><small>up to {fmtDate(b.period_end, true)}</small></span> },
                        { key: 'status', label: 'Status', render: (b) => <Badge tone={b.status === 'PAID' ? 'green' : b.status === 'DRAFT' ? 'amber' : 'grey'} dot>{b.status === 'DRAFT' ? 'To pay' : b.status === 'PAID' ? 'Paid' : 'Void'}</Badge> },
                        { key: 'lines', label: 'Artists', align: 'right', render: (b) => b.line_count },
                        { key: 'total', label: 'Total', align: 'right', render: (b) => <b>{inr(b.total)}</b> },
                        { key: 'ref', label: 'Reference', hide: 'md', render: (b) => (b.reference ? <span className="adm-cell__text"><span>{b.reference}</span><small>{b.paid_by_name} · {fmtDate(b.paid_at, true)}</small></span> : '—') },
                        { key: 'open', label: <span className="sr-only">Open</span>, align: 'right', render: (b) => <Button size="sm" onClick={() => setBatch(b.id)}>Open</Button> },
                    ]} empty={<EmptyState icon="fa-paper-plane" title="No payout batches yet" />} />
                </>
            )}

            {tab === 'disputes' && (
                <>
                    <div className="adm-card adm-pad">
                        <h3 className="adm-sub">Open a dispute by hand</h3>
                        <p className="adm-muted">Razorpay disputes open automatically (payment.dispute.* webhook). Use this for a chargeback reported by e-mail or by PayPal. While open, the artist's share of the order is held out of payouts.</p>
                        <div className="adm-inline">
                            <FilterSelect label="Order kind" value={dispute.source} allLabel="Choose" onChange={(v) => setDispute({ ...dispute, source: v })} options={[{ value: 'MARKETPLACE', label: 'Original' }, { value: 'SHOP', label: 'Shop' }, { value: 'COURSE', label: 'Course' }]} />
                            <label className="adm-filter"><span>Order</span><input value={dispute.ref} onChange={(e) => setDispute({ ...dispute, ref: e.target.value })} aria-label="Order number" /></label>
                            <input className="adm-input" style={{ flex: 1, minWidth: 200 }} value={dispute.reason} onChange={(e) => setDispute({ ...dispute, reason: e.target.value })} placeholder="Reason" aria-label="Dispute reason" />
                            <Button busy={busy === 'dispute'} onClick={() => call('dispute', '/admin/api/finance/disputes', dispute, () => setDispute({ ...dispute, ref: '', reason: '' }))}>Open dispute</Button>
                        </div>
                    </div>
                    <DataTable caption="Disputes" rows={disputes} columns={[
                        { key: 'when', label: 'Opened', render: (d) => <span className="adm-cell__text"><b>{fmtDate(d.created_at, true)}</b><small>{d.gateway_dispute_id || 'manual'}</small></span> },
                        { key: 'order', label: 'Order', render: (d) => `${d.source} ${d.source_ref}` },
                        { key: 'reason', label: 'Reason', render: (d) => d.reason || '—' },
                        { key: 'status', label: 'Status', render: (d) => <Badge tone={d.status === 'OPEN' ? 'red' : d.status === 'WON' ? 'green' : 'grey'} dot>{d.status}</Badge> },
                        { key: 'act', label: <span className="sr-only">Close</span>, align: 'right', render: (d) => (d.status === 'OPEN' ? <span className="adm-inline">
                            <Button size="sm" busy={busy === `w${d.id}`} onClick={() => call(`w${d.id}`, `/admin/api/finance/disputes/${d.id}/close`, { outcome: 'WON' })}>Won</Button>
                            <Button size="sm" variant="danger" busy={busy === `l${d.id}`} onClick={() => call(`l${d.id}`, `/admin/api/finance/disputes/${d.id}/close`, { outcome: 'LOST' })}>Lost</Button>
                        </span> : (d.closed_at ? fmtDate(d.closed_at, true) : null)) },
                    ]} empty={<EmptyState icon="fa-gavel" title="No disputes" />} />
                </>
            )}

            {tab === 'ledger' && (
                <>
                    <Toolbar>
                        <SearchBox value={g.meta.q} onSearch={g.search} placeholder="Order, artist or memo" />
                        <FilterSelect label="Account" value={g.filter('account')} onChange={(v) => g.go({ account: v })} options={['ESCROW', 'PLATFORM', 'ARTIST'].map((v) => ({ value: v, label: v[0] + v.slice(1).toLowerCase() }))} />
                        <FilterSelect label="Type" value={g.filter('type')} onChange={(v) => g.go({ type: v })} options={Object.keys(TYPE_TONE).map((v) => ({ value: v, label: v.replace('_', ' ').toLowerCase() }))} />
                        <FilterSelect label="Source" value={g.filter('source')} onChange={(v) => g.go({ source: v })} options={['MARKETPLACE', 'SHOP', 'COURSE', 'PAYOUT'].map((v) => ({ value: v, label: v[0] + v.slice(1).toLowerCase() }))} />
                    </Toolbar>
                    <div className="adm-dense">
                        <DataTable caption="Ledger entries" rows={g.rows} meta={g.meta} loading={g.loading} onSort={g.sortBy} columns={[
                            { key: 'date', label: 'Time', sort: 'date', render: (e) => fmtDate(e.created_at, true) },
                            { key: 'type', label: 'Entry', render: (e) => <span className="adm-cell__text"><span><Badge tone={TYPE_TONE[e.entry_type]}>{e.entry_type.replace('_', ' ')}</Badge> {e.account}</span><small>{e.memo}</small></span> },
                            { key: 'order', label: 'Order', render: (e) => `${e.source} ${e.source_ref}` },
                            { key: 'payee', label: 'Artist', hide: 'md', render: (e) => e.payee_name || (e.payee_id ? `#${e.payee_id}` : '—') },
                            { key: 'available', label: 'Payable from', hide: 'lg', render: (e) => (e.available_at ? fmtDate(e.available_at) : '—') },
                            { key: 'amount', label: 'Amount', sort: 'amount', align: 'right', render: (e) => <b className={Number(e.amount) < 0 ? 'adm-neg' : ''}>{inr(e.amount)}</b> },
                        ]} empty={<EmptyState icon="fa-book" title="No ledger entries yet" />} />
                    </div>
                    <Pagination meta={g.meta} onPage={g.setPage} onPer={g.setPer} />
                </>
            )}
            {batch && <BatchDrawer id={batch} onClose={() => setBatch(null)} call={call} busy={busy} onPayee={setPayee} />}
            {payee && <PayoutDetailsDrawer userId={payee} onClose={() => setPayee(null)} />}
            {toastNode}
        </AdminLayout>
    );
}

/** Where to send a student artist's money (saved by them in My studio > Sell your art). Opening it is logged. */
function PayoutDetailsDrawer({ userId, onClose }) {
    const [d, setD] = useState(null);
    const [err, setErr] = useState(null);
    React.useEffect(() => {
        axios.get(`/admin/api/finance/payees/${userId}/payout-details`).then((r) => setD(r.data)).catch((e) => setErr(errorText(e)));
    }, [userId]);
    const x = d && d.details;
    const rows = !x || x.unreadable ? [] : [
        ['Method', { UPI: 'UPI', BANK: 'Bank transfer (India)', PAYPAL: 'PayPal' }[x.method] || x.method],
        ['Account holder', x.holderName],
        ...(x.method === 'UPI' ? [['UPI ID', x.upiId]] : []),
        ...(x.method === 'BANK' ? [['Account number', x.accountNumber], ['IFSC', x.ifsc]] : []),
        ...(x.method === 'PAYPAL' ? [['PayPal e-mail', x.paypalEmail]] : []),
        ['Last changed', fmtDate(x.updated_at, true)],
    ];
    return (
        <Drawer open title="Payout details" subtitle={d ? `${d.student.name} · ${d.student.email}` : ''} onClose={onClose} footer={<Button onClick={onClose}>Close</Button>}>
            {err ? <p className="adm-field__error" role="alert">{err}</p> : !d ? <span className="adm-skel" style={{ height: 16, width: '60%' }}></span> : !x ? (
                <EmptyState icon="fa-university" title="No payout details yet">The student hasn't added where to be paid. Ask them to add it in My studio › Sell your art ({d.student.email}{d.student.phone ? `, ${d.student.phone}` : ''}).</EmptyState>
            ) : x.unreadable ? (
                <p className="adm-field__error" role="alert">These details can't be decrypted with the server's current key. Ask the student to enter them again.</p>
            ) : (
                <div className="adm-stack">
                    <dl className="adm-dl">{rows.map(([k, v]) => <React.Fragment key={k}><dt>{k}</dt><dd><b>{v}</b></dd></React.Fragment>)}</dl>
                    <p className="adm-muted adm-small"><i className="fa fa-shield" aria-hidden="true"></i> Viewing these details is recorded in the security log. Check the name matches the student before you transfer.</p>
                </div>
            )}
        </Drawer>
    );
}

function BatchDrawer({ id, onClose, call, busy, onPayee }) {
    const [d, setD] = useState(null);
    const [ref, setRef] = useState('');
    React.useEffect(() => { axios.get(`/admin/api/finance/batches/${id}`).then((r) => setD(r.data)); }, [id]);
    if (!d) return <Drawer open title={`Batch #${id}`} onClose={onClose}><span className="adm-skel" style={{ height: 16, width: '60%' }}></span></Drawer>;
    const b = d.batch;
    const draft = b.status === 'DRAFT';
    return (
        <Drawer open size="lg" title={`Payout batch #${b.id}`} subtitle={`${b.line_count} artist(s) · ${inr(b.total)}`} onClose={onClose} footer={<Button onClick={onClose}>Close</Button>}>
            <div className="adm-stack">
                <DataTable caption="Payout lines" rows={d.lines} columns={[
                    { key: 'who', label: 'Artist', render: (l) => <span className="adm-cell__text"><b>{l.payee_name || `#${l.payee_id}`}</b><small>{l.payee_email || '—'}</small></span> },
                    { key: 'entries', label: 'Entries', align: 'right', render: (l) => l.entries },
                    { key: 'amount', label: 'Amount', align: 'right', render: (l) => <b>{inr(l.amount)}</b> },
                    { key: 'pay', label: <span className="sr-only">Payout details</span>, align: 'right', render: (l) => (l.payee_type === 'USER'
                        ? <Button size="sm" variant="ghost" icon="fa-university" onClick={() => onPayee(l.payee_id)} aria-label={`Payout details of ${l.payee_name || l.payee_id}`}>Pay to</Button> : null) },
                ]} />
                {draft ? (
                    <div className="adm-task is-edit">
                        <p className="adm-muted">Transfer each amount to the artist, then record the reference. Lines are re-checked when you confirm: a dispute opened since the batch was made lowers or removes that artist's line.</p>
                        <label className="adm-filter" style={{ display: 'flex' }}><span>Bank / batch reference</span><input value={ref} onChange={(e) => setRef(e.target.value)} aria-label="Bank reference" /></label>
                        <div className="adm-inline">
                            <Button variant="brand" icon="fa-check" disabled={!ref.trim()} busy={busy === 'paid'} onClick={() => call('paid', `/admin/api/finance/batches/${b.id}/paid`, { reference: ref }, onClose)}>Mark paid</Button>
                            <Button variant="ghost" busy={busy === 'void'} onClick={() => call('void', `/admin/api/finance/batches/${b.id}/void`, {}, onClose)}>Void draft</Button>
                        </div>
                    </div>
                ) : <p className="adm-muted">{b.status === 'PAID' ? `Paid ${fmtDate(b.paid_at, true)} · ${b.reference}` : 'Voided.'}</p>}
            </div>
        </Drawer>
    );
}
