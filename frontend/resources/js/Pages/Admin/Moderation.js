import React, { useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import { Inertia } from '@inertiajs/inertia';
import axios from 'axios';
import AdminLayout, { PageHeader, useToast, errorText } from '@/Components/Admin/AdminLayout';
import { Button, Badge, DataTable, Pagination, FilterSelect, Segments, Toolbar, EmptyState, Drawer, useGrid, fmtDate } from '@/Components/Admin/kit';

/*
 * Admin > Community > Reports & bans. The moderation queue (one row per reported item, reason codes, select many
 * to hide or dismiss at once) and account restrictions (suspend sign-in, posting or selling, for a time or until
 * lifted).
 */
const REASON = { SPAM: 'Spam', OFFENSIVE: 'Offensive', COPYRIGHT: 'Copyright', MISLEADING: 'Misleading', PERSONAL_INFO: 'Personal info', OTHER: 'Other' };
const TYPE = { PAINTING: 'Listing', ARTWORK: 'Gallery artwork', REVIEW: 'Course review', SUBMISSION: 'Submission', USER: 'Account' };
const KIND = { BAN: 'Suspended (no sign-in)', POSTING: 'No posting', SELLING: 'No selling' };
const keyOf = (r) => `${r.target_type}:${r.target_id}`;

export default function Moderation() {
    const { restrictions = [], canBan, counts = {} } = usePage().props;
    const g = useGrid('grid', ['counts']);
    const [toastNode, toast] = useToast();
    const [tab, setTab] = useState('queue');
    const [selected, setSelected] = useState([]);
    const [open, setOpen] = useState(null);
    const [note, setNote] = useState('');
    const [busy, setBusy] = useState(null);
    const [ban, setBan] = useState({ email: '', kind: 'BAN', reason: '', until: '' });
    const [err, setErr] = useState({});

    const rows = g.rows.map((r) => ({ ...r, id: keyOf(r) }));
    const decide = (action, items) => {
        setBusy(action);
        axios.post('/admin/api/moderation/decide', { action, note, items: items.map((k) => { const [type, id] = k.split(':'); return { type, id: Number(id) }; }) })
            .then((r) => { toast(r.data.message); setSelected([]); setNote(''); setOpen(null); g.reload(); })
            .catch((e) => toast(errorText(e), 'error')).finally(() => setBusy(null));
    };
    const restrict = () => {
        setBusy('ban'); setErr({});
        axios.post('/admin/api/moderation/restrictions', ban).then((r) => { toast(r.data.message); setBan({ email: '', kind: 'BAN', reason: '', until: '' }); Inertia.reload({ only: ['restrictions'] }); })
            .catch((e) => { const d = e.response && e.response.data; if (d && d.errors) setErr(d.errors); else toast(errorText(e), 'error'); })
            .finally(() => setBusy(null));
    };
    const lift = (id) => axios.post(`/admin/api/moderation/restrictions/${id}/lift`).then((r) => { toast(r.data.message); Inertia.reload({ only: ['restrictions'] }); }).catch((e) => toast(errorText(e), 'error'));
    const status = g.filter('status') || 'OPEN';

    const columns = [
        { key: 'item', label: 'Reported item', render: (r) => (
            <span className="adm-cell__text"><span><Badge>{TYPE[r.target_type] || r.target_type}</Badge> <b>{(r.target && r.target.label) || `#${r.target_id}`}</b></span>
                <small>{r.target && r.target.owner ? `by ${r.target.owner}` : ''}{r.target && r.target.url ? <> · <a href={r.target.url} target="_blank" rel="noopener noreferrer">open</a></> : null}</small></span>
        ) },
        { key: 'reasons', label: 'Reasons', render: (r) => <span className="adm-inline">{String(r.reasons || '').split(',').map((x) => <Badge key={x} tone={x === 'COPYRIGHT' || x === 'OFFENSIVE' ? 'red' : 'amber'}>{REASON[x] || x}</Badge>)}</span> },
        { key: 'reports', label: 'Reports', sort: 'reports', align: 'right', render: (r) => <b>{r.reports}</b> },
        { key: 'last', label: 'Latest', sort: 'last', hide: 'md', render: (r) => fmtDate(r.last_at, true) },
        { key: 'open', label: <span className="sr-only">Review</span>, align: 'right', render: (r) => <Button size="sm" onClick={() => setOpen(r)}>Review</Button> },
    ];

    return (
        <AdminLayout title="Reports & bans">
            <PageHeader eyebrow="Community" title="Reports & bans" subtitle="What visitors reported, grouped per item, and accounts you have restricted. Hiding an item closes all of its reports." />
            <div className="adm-tabs" role="tablist" aria-label="Moderation views">
                <button type="button" role="tab" aria-selected={tab === 'queue'} className={tab === 'queue' ? 'is-on' : ''} onClick={() => setTab('queue')}>Reports {counts.open ? <Badge tone="amber">{counts.open}</Badge> : null}</button>
                <button type="button" role="tab" aria-selected={tab === 'bans'} className={tab === 'bans' ? 'is-on' : ''} onClick={() => setTab('bans')}>Restricted accounts ({restrictions.length})</button>
            </div>
            {tab === 'queue' ? (
                <>
                    <Toolbar end={<Segments label="Status" value={status} onChange={(v) => g.go({ status: v })} options={[{ value: 'OPEN', label: 'Open' }, { value: 'ACTIONED', label: 'Hidden' }, { value: 'DISMISSED', label: 'Dismissed' }]} />}>
                        <FilterSelect label="Kind" value={g.filter('type')} onChange={(v) => g.go({ type: v })} options={Object.entries(TYPE).map(([value, label]) => ({ value, label }))} />
                        <FilterSelect label="Reason" value={g.filter('reason')} onChange={(v) => g.go({ reason: v })} options={Object.entries(REASON).map(([value, label]) => ({ value, label }))} />
                    </Toolbar>
                    {selected.length > 0 && status === 'OPEN' && (
                        <div className="adm-bulkbar" role="region" aria-label="Bulk actions">
                            <b>{selected.length} selected</b>
                            <input className="adm-input" value={note} onChange={(e) => setNote(e.target.value)} placeholder="Note for the log (optional)" aria-label="Moderation note" />
                            <Button variant="danger" icon="fa-eye-slash" busy={busy === 'hide'} onClick={() => decide('hide', selected)}>Hide items</Button>
                            <Button busy={busy === 'dismiss'} onClick={() => decide('dismiss', selected)}>Dismiss reports</Button>
                        </div>
                    )}
                    <DataTable columns={columns} rows={rows} meta={g.meta} loading={g.loading} onSort={g.sortBy} caption="Reported items"
                        selected={status === 'OPEN' ? selected : undefined} onSelect={status === 'OPEN' ? setSelected : undefined}
                        empty={<EmptyState icon="fa-flag-o" title={status === 'OPEN' ? 'Nothing to review' : 'Nothing here'}>Reports from the Report buttons on listings and reviews appear here.</EmptyState>} />
                    <Pagination meta={g.meta} onPage={g.setPage} onPer={g.setPer} />
                </>
            ) : (
                <>
                    {canBan && (
                        <div className="adm-card adm-pad">
                            <h3 className="adm-sub">Restrict an account</h3>
                            <div className="adm-inline">
                                <label className="adm-filter"><span>Student e-mail</span><input type="email" value={ban.email} onChange={(e) => setBan({ ...ban, email: e.target.value })} aria-label="Student e-mail" /></label>
                                <FilterSelect label="Restriction" value={ban.kind} allLabel="Choose" onChange={(v) => setBan({ ...ban, kind: v })} options={Object.entries(KIND).map(([value, label]) => ({ value, label }))} />
                                <label className="adm-filter"><span>Until (empty = until lifted)</span><input type="datetime-local" value={ban.until} onChange={(e) => setBan({ ...ban, until: e.target.value })} aria-label="Restricted until" /></label>
                            </div>
                            <label className="adm-label" htmlFor="mod-reason">Reason (staff only)</label>
                            <input id="mod-reason" className="adm-input" value={ban.reason} onChange={(e) => setBan({ ...ban, reason: e.target.value })} aria-invalid={!!err.reason} />
                            {Object.values(err).length > 0 && <p className="adm-error" role="alert">{Object.values(err)[0]}</p>}
                            <div className="adm-inline" style={{ marginTop: 10 }}><Button variant="danger" icon="fa-ban" busy={busy === 'ban'} onClick={restrict}>Restrict</Button></div>
                        </div>
                    )}
                    <DataTable caption="Restricted accounts" rows={restrictions} columns={[
                        { key: 'who', label: 'Student', render: (x) => <span className="adm-cell__text"><b>{x.name}</b><small>{x.email}</small></span> },
                        { key: 'kind', label: 'Restriction', render: (x) => <Badge tone={x.kind === 'BAN' ? 'red' : 'amber'} dot>{KIND[x.kind]}</Badge> },
                        { key: 'until', label: 'Until', render: (x) => (x.until ? fmtDate(x.until, true) : 'Lifted by staff') },
                        { key: 'reason', label: 'Reason', hide: 'md', render: (x) => <span className="adm-cell__text"><span>{x.reason}</span><small>{x.by_name} · {fmtDate(x.created_at, true)}</small></span> },
                        { key: 'act', label: <span className="sr-only">Lift</span>, align: 'right', render: (x) => (canBan ? <Button size="sm" onClick={() => lift(x.id)}>Lift</Button> : null) },
                    ]} empty={<EmptyState icon="fa-check" title="No restricted accounts" />} />
                </>
            )}
            {open && <ReportDrawer item={open} onClose={() => setOpen(null)} onDecide={(a) => decide(a, [keyOf(open)])} busy={busy} note={note} setNote={setNote} />}
            {toastNode}
        </AdminLayout>
    );
}

function ReportDrawer({ item, onClose, onDecide, busy, note, setNote }) {
    const [d, setD] = useState(null);
    React.useEffect(() => { axios.get('/admin/api/moderation/reports', { params: { type: item.target_type, id: item.target_id } }).then((r) => setD(r.data)); }, [item]);
    const openReports = d ? d.reports.filter((r) => r.status === 'OPEN').length : 0;
    return (
        <Drawer open size="lg" title={`${TYPE[item.target_type] || item.target_type} #${item.target_id}`} subtitle={item.target && item.target.label} onClose={onClose}
            footer={openReports > 0 ? <>
                <Button variant="danger" icon="fa-eye-slash" busy={busy === 'hide'} onClick={() => onDecide('hide')}>Hide item</Button>
                <Button busy={busy === 'dismiss'} onClick={() => onDecide('dismiss')}>Dismiss</Button>
            </> : <Button onClick={onClose}>Close</Button>}>
            {!d ? <span className="adm-skel" style={{ height: 16, width: '60%' }}></span> : (
                <div className="adm-stack">
                    {openReports > 0 && <><label className="adm-label" htmlFor="rd-note">Note for the log</label><input id="rd-note" className="adm-input" value={note} onChange={(e) => setNote(e.target.value)} /></>}
                    <ol className="adm-timeline">
                        {d.reports.map((r) => (
                            <li key={r.id}><b>{REASON[r.reason_code] || r.reason_code}</b> <Badge tone={r.status === 'OPEN' ? 'amber' : 'grey'}>{r.status}</Badge>{r.detail && <span> — {r.detail}</span>}
                                <small>{r.reporter || 'Visitor'} · {fmtDate(r.created_at, true)}{r.note ? ` · staff: ${r.note}` : ''}</small></li>
                        ))}
                    </ol>
                </div>
            )}
        </Drawer>
    );
}
