import React, { useEffect, useMemo, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import axios from 'axios';
import AdminLayout, { PageHeader, useToast, errorText } from '@/Components/Admin/AdminLayout';
import { Button, Badge, DataTable, Pagination, SearchBox, FilterSelect, Segments, Toolbar, EmptyState, Drawer, Stat, useGrid } from '@/Components/Admin/kit';

/*
 * Admin > Setup > Security log: the append-only, hash-chained audit stream (audit_logs).
 * Dense table (ISO time, who, action type, resource, IP / location), quick filters (user, action types, time window,
 * custom range), a drawer with the side-by-side JSON diff of an UPDATE, CSV export and the integrity check.
 */

export const TYPES = [
    { value: 'CREATE', tone: 'green', icon: 'fa-plus' },
    { value: 'UPDATE', tone: 'blue', icon: 'fa-pencil' },
    { value: 'DELETE', tone: 'red', icon: 'fa-trash' },
    { value: 'AUTH', tone: 'purple', icon: 'fa-key' },
    { value: 'EXPORT', tone: 'amber', icon: 'fa-download' },
    { value: 'DENIED', tone: 'red', icon: 'fa-ban' },
];
const toneOf = (t) => (TYPES.find((x) => x.value === t) || { tone: 'grey' }).tone;
export const iso = (ts) => (ts ? String(ts).replace(' ', 'T') : '');
const EMPTY = { q: '', actor: '', role: '', types: '', outcome: '', resource: '', window: '', from: '', to: '' };

/** Rows for the side-by-side diff: every key of both versions, marked added / removed / changed / same. */
export function diffRows(before, after) {
    const b = before || {};
    const a = after || {};
    const keys = Array.from(new Set([...Object.keys(b), ...Object.keys(a)])).sort();
    const show = (v) => (v === undefined ? undefined : v === null ? 'null' : typeof v === 'object' ? JSON.stringify(v) : String(v));
    return keys.map((k) => {
        const l = show(b[k]);
        const r = show(a[k]);
        const kind = l === undefined ? 'added' : r === undefined ? 'removed' : l === r ? 'same' : 'changed';
        return { key: k, before: l, after: r, kind };
    });
}

const parse = (s) => { if (!s) return null; try { return JSON.parse(s); } catch (e) { return { value: s }; } };

export default function SecurityAudit() {
    const { stats = {}, resources = [], canExport } = usePage().props;
    const g = useGrid('grid', ['stats']);
    const [toastNode, toast] = useToast();
    const [openId, setOpenId] = useState(null);
    const [actor, setActor] = useState(g.filter('actor'));
    const [verifying, setVerifying] = useState(false);
    const [integrity, setIntegrity] = useState(null);
    useEffect(() => setActor(g.filter('actor')), [g.meta.filters && g.meta.filters.actor]);

    const picked = useMemo(() => (g.filter('types') ? g.filter('types').split(',') : []), [g.meta.filters && g.meta.filters.types]);
    const toggleType = (t) => {
        const next = picked.includes(t) ? picked.filter((x) => x !== t) : [...picked, t];
        g.go({ types: next.join(',') });
    };
    const anyFilter = g.meta.q || Object.keys(g.meta.filters || {}).length > 0;
    const clear = () => { setActor(''); g.go(EMPTY); };

    const verify = () => {
        setVerifying(true);
        axios.post('/admin/api/security/audit/verify').then((r) => {
            setIntegrity(r.data);
            toast(r.data.ok ? `Chain intact: ${r.data.checked} entries checked.` : r.data.problem, r.data.ok ? 'ok' : 'error');
            g.reload();
        }).catch((e) => toast(errorText(e), 'error')).finally(() => setVerifying(false));
    };

    const columns = [
        { key: 'time', label: 'Timestamp (ISO)', sort: 'time', render: (r) => <time className="adm-mono" dateTime={iso(r.occurred_at)}>{iso(r.occurred_at)}</time> },
        { key: 'actor', label: 'User', sort: 'actor', render: (r) => (
            <span className="adm-cell__text">
                <b>{r.actor_email || (r.actor_type === 'system' ? 'System' : r.actor_type === 'anonymous' ? 'Anonymous' : `#${r.actor_id}`)}</b>
                <small>{[r.actor_id ? `ID ${r.actor_id}` : null, r.actor_role].filter(Boolean).join(' · ') || r.actor_type}</small>
            </span>
        ) },
        { key: 'type', label: 'Action', sort: 'type', render: (r) => (
            <span className="adm-cell__text"><span><Badge tone={toneOf(r.action_type)}>{r.action_type}</Badge>{r.outcome === 'FAIL' && <> <Badge tone="red" dot>Failed</Badge></>}</span><small>{r.action}</small></span>
        ) },
        { key: 'resource', label: 'Target resource', sort: 'resource', render: (r) => (
            <span className="adm-cell__text"><b className="adm-mono">{r.resource_type || '—'}{r.resource_id ? ` #${r.resource_id}` : ''}</b>{r.summary && <small title={r.summary}>{r.summary.length > 80 ? `${r.summary.slice(0, 80)}…` : r.summary}</small>}</span>
        ) },
        { key: 'ip', label: 'IP / location', hide: 'md', render: (r) => (
            <span className="adm-cell__text"><span className="adm-mono">{r.ip || '—'}</span><small>{[r.city, r.country].filter(Boolean).join(', ') || '—'}</small></span>
        ) },
        { key: 'open', label: <span className="sr-only">Details</span>, align: 'right', render: (r) => (
            <Button size="sm" variant={Number(r.has_diff) ? 'dark' : 'line'} onClick={() => setOpenId(r.id)} aria-label={`Open entry ${r.id}`}>{Number(r.has_diff) ? 'Diff' : 'Details'}</Button>
        ) },
    ];

    return (
        <AdminLayout title="Security log">
            <PageHeader eyebrow="Setup" title="Security log"
                subtitle="Every staff change, sign-in, export and refused request, in one tamper-evident stream. Entries can't be edited or deleted."
                actions={<>
                    <Button icon="fa-check-circle-o" busy={verifying} onClick={verify}>Verify integrity</Button>
                    {canExport && <Button as="a" icon="fa-download" href={g.exportUrl('/admin/security/audit/export')}>Export CSV</Button>}
                </>} />
            {integrity && (
                <div className={`adm-alert ${integrity.ok ? 'is-ok' : 'is-error'}`} role="status">
                    <i className={`fa ${integrity.ok ? 'fa-check-circle' : 'fa-exclamation-triangle'}`} aria-hidden="true"></i>
                    {integrity.ok ? `Hash chain intact: all ${integrity.checked} entries match.` : <>{integrity.problem} <button type="button" className="adm-linkbtn" onClick={() => setOpenId(integrity.brokenAt)}>Open entry {integrity.brokenAt}</button></>}
                </div>
            )}
            <div className="adm-stats">
                <Stat icon="fa-pencil" label="Changes (24 h)" value={(stats.CREATE || 0) + (stats.UPDATE || 0) + (stats.DELETE || 0)} />
                <Stat icon="fa-key" label="Failed sign-ins (24 h)" value={stats.failedSignIns || 0} tone={stats.failedSignIns ? 'amber' : undefined} />
                <Stat icon="fa-ban" label="Refused requests (24 h)" value={stats.DENIED || 0} tone={stats.DENIED ? 'red' : undefined} />
                <Stat icon="fa-user-times" label="Sessions revoked (24 h)" value={stats.revoked || 0} />
            </div>

            <section className="adm-auditfilters" aria-label="Audit filters">
                <Toolbar end={<Segments label="Time window" value={g.filter('window')} onChange={(v) => g.go({ window: v, from: '', to: '' })}
                    options={[{ value: '', label: 'All time' }, { value: '1h', label: '1 h' }, { value: '24h', label: '24 h' }, { value: '7d', label: '7 days' }, { value: '30d', label: '30 days' }]} />}>
                    <SearchBox value={g.meta.q} onSearch={g.search} placeholder="Action, summary, IP, path or resource id" />
                </Toolbar>
                <div className="adm-typechips" role="group" aria-label="Action types">
                    {TYPES.map((t) => (
                        <button key={t.value} type="button" className={`adm-typechip is-${t.tone}${picked.includes(t.value) ? ' is-on' : ''}`} aria-pressed={picked.includes(t.value)} onClick={() => toggleType(t.value)}>
                            <i className={`fa ${t.icon}`} aria-hidden="true"></i>{t.value}
                        </button>
                    ))}
                </div>
                <Toolbar>
                    <form className="adm-filter" onSubmit={(e) => { e.preventDefault(); g.go({ actor: actor.trim() }); }}>
                        <span>User</span>
                        <input type="search" value={actor} onChange={(e) => setActor(e.target.value)} onBlur={() => actor.trim() !== g.filter('actor') && g.go({ actor: actor.trim() })}
                            placeholder="ID or e-mail" aria-label="User ID or e-mail" />
                    </form>
                    <FilterSelect label="Role" value={g.filter('role')} onChange={(v) => g.go({ role: v })}
                        options={['admin', 'teacher', 'moderator', 'student', 'system', 'anonymous'].map((v) => ({ value: v, label: v[0].toUpperCase() + v.slice(1) }))} />
                    <FilterSelect label="Outcome" value={g.filter('outcome')} onChange={(v) => g.go({ outcome: v })}
                        options={[{ value: 'OK', label: 'OK' }, { value: 'FAIL', label: 'Failed' }, { value: 'DENIED', label: 'Denied' }]} />
                    <FilterSelect label="Resource" value={g.filter('resource')} onChange={(v) => g.go({ resource: v })} options={resources.map((r) => ({ value: r.value, label: r.label }))} />
                    <label className="adm-filter"><span>From</span><input type="datetime-local" value={g.filter('from')} onChange={(e) => g.go({ from: e.target.value, window: '' })} aria-label="From time" /></label>
                    <label className="adm-filter"><span>To</span><input type="datetime-local" value={g.filter('to')} onChange={(e) => g.go({ to: e.target.value, window: '' })} aria-label="To time" /></label>
                    {anyFilter && <Button variant="ghost" size="sm" icon="fa-times" onClick={clear}>Clear all</Button>}
                </Toolbar>
            </section>

            <div className="adm-dense">
                <DataTable columns={columns} rows={g.rows} meta={g.meta} loading={g.loading} onSort={g.sortBy} caption="Security log entries" onRowClick={(r) => setOpenId(r.id)}
                    empty={anyFilter ? <EmptyState icon="fa-search" title="No entries match these filters" action={<Button onClick={clear}>Clear filters</Button>} />
                        : <EmptyState icon="fa-shield" title="Nothing logged yet">Entries appear as soon as staff sign in or change something.</EmptyState>} />
            </div>
            <Pagination meta={g.meta} onPage={g.setPage} onPer={g.setPer} />
            {openId && <EntryDrawer id={openId} onClose={() => setOpenId(null)} onActor={(v) => { setOpenId(null); setActor(String(v)); g.go({ actor: String(v) }); }} />}
            {toastNode}
        </AdminLayout>
    );
}

function EntryDrawer({ id, onClose, onActor }) {
    const [e, setE] = useState(null);
    const [error, setError] = useState(null);
    const [onlyChanged, setOnlyChanged] = useState(true);
    useEffect(() => {
        setE(null); setError(null);
        axios.get(`/admin/api/security/audit/${id}`).then((r) => setE(r.data.entry)).catch((x) => setError(errorText(x)));
    }, [id]);
    const rows = useMemo(() => (e ? diffRows(parse(e.before_json), parse(e.after_json)) : []), [e]);
    const shown = onlyChanged ? rows.filter((r) => r.kind !== 'same') : rows;
    const hasDiff = e && (e.before_json || e.after_json);

    return (
        <Drawer open size="lg" title={e ? `${e.action_type} · ${e.action}` : 'Entry'} subtitle={e ? iso(e.occurred_at) : undefined} onClose={onClose}
            footer={<Button onClick={onClose}>Close</Button>}>
            {error && <div className="adm-alert is-error" role="alert"><i className="fa fa-exclamation-circle" aria-hidden="true"></i>{error}</div>}
            {!e && !error && <div className="adm-stack" aria-busy="true">{[0, 1, 2, 3].map((i) => <span key={i} className="adm-skel" style={{ height: 16, width: `${90 - i * 12}%` }}></span>)}</div>}
            {e && (
                <div className="adm-stack">
                    <dl className="adm-dl">
                        <dt>Entry</dt><dd className="adm-mono">#{e.id} · <Badge tone={toneOf(e.action_type)}>{e.action_type}</Badge> {e.outcome}</dd>
                        <dt>User</dt><dd>{e.actor_email || e.actor_type}{e.actor_id && <> · <button type="button" className="adm-linkbtn" onClick={() => onActor(e.actor_id)}>ID {e.actor_id}: show all</button></>}{e.actor_role && ` · ${e.actor_role}`}</dd>
                        <dt>Resource</dt><dd className="adm-mono">{e.resource_type || '—'}{e.resource_id ? ` #${e.resource_id}` : ''}</dd>
                        {e.summary && <><dt>Summary</dt><dd>{e.summary}</dd></>}
                        <dt>Request</dt><dd className="adm-mono">{[e.http_method, e.path].filter(Boolean).join(' ') || '—'}</dd>
                        <dt>IP / location</dt><dd><span className="adm-mono">{e.ip || '—'}</span>{(e.city || e.country) && ` · ${[e.city, e.country].filter(Boolean).join(', ')}`}</dd>
                        <dt>Device</dt><dd className="adm-small">{e.user_agent || '—'}</dd>
                        <dt>Row hash</dt><dd className="adm-mono adm-small" title={`previous: ${e.prev_hash}`}>{e.row_hash}</dd>
                    </dl>
                    {hasDiff && (
                        <>
                            <div className="adm-inline adm-inline--between">
                                <h3 className="adm-sub">Changes</h3>
                                <label className="adm-check"><input type="checkbox" checked={onlyChanged} onChange={(x) => setOnlyChanged(x.target.checked)} /> Only changed fields</label>
                            </div>
                            <div className="adm-jsondiff" role="table" aria-label="Before and after">
                                <div className="adm-jsondiff__row is-head" role="row">
                                    <span role="columnheader">Field</span><span role="columnheader">Before</span><span role="columnheader">After</span>
                                </div>
                                {shown.length === 0 && <div className="adm-jsondiff__row" role="row"><span role="cell" className="adm-muted">No field changed.</span><span role="cell"></span><span role="cell"></span></div>}
                                {shown.map((r) => (
                                    <div key={r.key} className={`adm-jsondiff__row is-${r.kind}`} role="row">
                                        <span role="cell" className="adm-mono">{r.key}</span>
                                        <span role="cell" className="adm-jsondiff__val is-before">{r.before === undefined ? <i>(none)</i> : r.before}</span>
                                        <span role="cell" className="adm-jsondiff__val is-after">{r.after === undefined ? <i>(none)</i> : r.after}</span>
                                    </div>
                                ))}
                            </div>
                        </>
                    )}
                </div>
            )}
        </Drawer>
    );
}
