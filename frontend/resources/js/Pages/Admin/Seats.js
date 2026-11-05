import React, { useEffect, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import { Inertia } from '@inertiajs/inertia';
import axios from 'axios';
import AdminLayout, { PageHeader, useToast, errorText } from '@/Components/Admin/AdminLayout';
import { Button, Badge, DataTable, EmptyState, Drawer, FilterSelect, fmtDate } from '@/Components/Admin/kit';

/*
 * Admin > Learning > Seats & waitlist. Live courses and workshops with a seat cap: how full each one is, who waits,
 * invitations sent (each keeps a seat for 24 hours) and priority links staff send by hand.
 */
export default function Seats() {
    const { courses = [], uncapped = [] } = usePage().props;
    const [toastNode, toast] = useToast();
    const [open, setOpen] = useState(null);
    const [adding, setAdding] = useState('');
    const reload = () => Inertia.reload({ only: ['courses', 'uncapped'], preserveScroll: true });

    const columns = [
        { key: 'title', label: 'Course', render: (r) => <span className="adm-cell__text"><b>{r.title}</b><small>Starts {fmtDate(r.course_start_date)}</small></span> },
        { key: 'use', label: 'Seats', render: (r) => (
            <span className="adm-cell__text">
                <span className="adm-seatbar" role="img" aria-label={`${r.taken} of ${r.seat_limit} seats taken`}><span style={{ width: `${Math.min(100, (r.taken / Math.max(1, r.seat_limit)) * 100)}%` }}></span></span>
                <small>{r.taken} of {r.seat_limit} taken · {r.left} free</small>
            </span>
        ) },
        { key: 'state', label: 'State', render: (r) => (r.left === 0 ? <Badge tone="red" dot>Sold out</Badge> : r.left <= 5 ? <Badge tone="amber" dot>{r.left} left</Badge> : <Badge tone="green" dot>Open</Badge>) },
        { key: 'waiting', label: 'Waitlist', align: 'right', render: (r) => (r.waiting ? <b>{r.waiting}</b> : <span className="adm-muted">—</span>) },
        { key: 'open', label: <span className="sr-only">Manage</span>, align: 'right', render: (r) => <Button size="sm" onClick={() => setOpen(r.id)}>Manage</Button> },
    ];

    const addCap = () => {
        if (!adding) return;
        axios.post(`/admin/api/seats/${adding}/limit`, { limit: 20 }).then((r) => { toast(`${r.data.message} Set to 20 - change it under Manage.`); setAdding(''); reload(); })
            .catch((e) => toast(errorText(e), 'error'));
    };

    return (
        <AdminLayout title="Seats & waitlist">
            <PageHeader eyebrow="Learning" title="Seats & waitlist"
                subtitle="Cap the number of students in a live course or workshop. When it's full, students join a waitlist; the first in line gets a seat kept for 24 hours as soon as one frees up." />
            <div className="adm-inline" style={{ marginBottom: 16 }}>
                <FilterSelect label="Add a seat limit to" value={adding} onChange={setAdding} allLabel="Choose a course" options={uncapped.map((c) => ({ value: String(c.value), label: c.label }))} />
                <Button icon="fa-plus" disabled={!adding} onClick={addCap}>Add limit</Button>
            </div>
            <DataTable columns={columns} rows={courses} caption="Courses with a seat limit" onRowClick={(r) => setOpen(r.id)}
                empty={<EmptyState icon="fa-users" title="No course has a seat limit">Recorded courses don't need one. Add a limit to a live course or workshop above.</EmptyState>} />
            {open && <SeatDrawer id={open} onClose={() => setOpen(null)} toast={toast} onChanged={reload} />}
            {toastNode}
        </AdminLayout>
    );
}

function SeatDrawer({ id, onClose, toast, onChanged }) {
    const [d, setD] = useState(null);
    const [limit, setLimit] = useState('');
    const [p, setP] = useState({ email: '', hours: '48', holdsSeat: true, note: '' });
    const [err, setErr] = useState({});
    const [busy, setBusy] = useState(null);
    const [link, setLink] = useState(null);
    const load = () => axios.get(`/admin/api/seats/${id}`).then((r) => { setD(r.data); setLimit(r.data.course.seat_limit == null ? '' : String(r.data.course.seat_limit)); });
    useEffect(() => { load().catch((e) => toast(errorText(e), 'error')); }, [id]);
    const call = (key, url, body) => {
        setBusy(key); setErr({});
        return axios.post(url, body).then((r) => { toast(r.data.message); if (r.data.link) setLink(r.data.link); onChanged(); return load(); })
            .catch((e) => { const x = e.response && e.response.data; if (x && x.errors) setErr(x.errors); else toast(errorText(e), 'error'); })
            .finally(() => setBusy(null));
    };
    if (!d) return <Drawer open title="Seats" onClose={onClose}><div className="adm-stack" aria-busy="true"><span className="adm-skel" style={{ height: 16, width: '70%' }}></span></div></Drawer>;
    const s = d.seats;
    return (
        <Drawer open size="lg" title={d.course.title} subtitle={s.limit == null ? 'No seat limit' : `${s.taken} of ${s.limit} seats taken`} onClose={onClose} footer={<Button onClick={onClose}>Close</Button>}>
            <div className="adm-stack">
                <h3 className="adm-sub">Seat limit</h3>
                <div className="adm-inline">
                    <label className="adm-filter"><span>Seats</span><input type="number" min="1" value={limit} onChange={(e) => setLimit(e.target.value)} aria-label="Seat limit" placeholder="No limit" /></label>
                    <Button size="sm" busy={busy === 'limit'} onClick={() => call('limit', `/admin/api/seats/${id}/limit`, { limit })}>Save</Button>
                    {d.course.seat_limit != null && <Button size="sm" variant="ghost" onClick={() => call('limit', `/admin/api/seats/${id}/limit`, { limit: '' })}>Remove limit</Button>}
                </div>
                {err.limit && <p className="adm-error" role="alert">{err.limit}</p>}

                <div className="adm-inline adm-inline--between">
                    <h3 className="adm-sub">Waitlist ({d.waitlist.length})</h3>
                    <Button size="sm" icon="fa-envelope-o" busy={busy === 'invite'} disabled={!d.waitlist.some((w) => w.status === 'WAITING')} onClick={() => call('invite', `/admin/api/seats/${id}/invite-next`, {})}>Invite next</Button>
                </div>
                {d.waitlist.length === 0 ? <p className="adm-muted">Nobody is waiting.</p> : (
                    <ol className="adm-timeline">
                        {d.waitlist.map((w) => <li key={w.id}><b>{w.name || w.email}</b> <Badge tone={w.status === 'INVITED' ? 'blue' : 'amber'}>{w.status === 'INVITED' ? 'Invited' : 'Waiting'}</Badge><small>{w.email} · since {fmtDate(w.created_at, true)}</small></li>)}
                    </ol>
                )}

                <h3 className="adm-sub">Send a priority link</h3>
                <p className="adm-muted">The person gets an e-mail with a checkout link that works only for their e-mail address. With "keep a seat" on, one seat is set aside for them until the link expires - even if the course is full by then.</p>
                <div className="adm-inline">
                    <label className="adm-filter"><span>E-mail</span><input type="email" value={p.email} onChange={(e) => setP({ ...p, email: e.target.value })} aria-label="E-mail for the priority link" /></label>
                    <label className="adm-filter"><span>Valid (hours)</span><input type="number" min="1" max="720" value={p.hours} onChange={(e) => setP({ ...p, hours: e.target.value })} aria-label="Hours the link stays valid" /></label>
                    <label className="adm-check"><input type="checkbox" checked={p.holdsSeat} onChange={(e) => setP({ ...p, holdsSeat: e.target.checked })} /> Keep a seat</label>
                    <Button size="sm" variant="dark" icon="fa-paper-plane" busy={busy === 'priority'} onClick={() => call('priority', `/admin/api/seats/${id}/priority`, p).then(() => setP({ ...p, email: '', note: '' }))}>Send link</Button>
                </div>
                {(err.email || err.hours) && <p className="adm-error" role="alert">{err.email || err.hours}</p>}
                {link && (
                    <div className="adm-alert is-info" role="status">
                        <i className="fa fa-link" aria-hidden="true"></i>
                        <span style={{ flex: 1, minWidth: 0, overflowWrap: 'anywhere' }}>Link (shown once, also e-mailed): <code>{link}</code></span>
                        <Button size="sm" onClick={() => { try { navigator.clipboard.writeText(link); toast('Link copied.'); } catch (e) { /* select it by hand */ } }}>Copy</Button>
                    </div>
                )}

                <h3 className="adm-sub">Links sent</h3>
                {d.grants.length === 0 ? <p className="adm-muted">None yet.</p> : (
                    <DataTable caption="Priority and waitlist links" rows={d.grants} columns={[
                        { key: 'who', label: 'For', render: (g) => <span className="adm-cell__text"><b>{g.email}</b><small>{g.kind === 'WAITLIST' ? 'Waitlist invitation' : 'Priority link'}{Number(g.holds_seat) ? ' · keeps a seat' : ''}</small></span> },
                        { key: 'state', label: 'State', render: (g) => g.used_at ? <Badge tone="green">Used</Badge> : g.revoked_at ? <Badge>Revoked</Badge> : new Date(String(g.expires_at).replace(' ', 'T')) < new Date() ? <Badge>Expired</Badge> : <Badge tone="blue" dot>Open until {fmtDate(g.expires_at, true)}</Badge> },
                        { key: 'act', label: <span className="sr-only">Revoke</span>, align: 'right', render: (g) => (!g.used_at && !g.revoked_at && new Date(String(g.expires_at).replace(' ', 'T')) > new Date()
                            ? <Button size="sm" variant="ghost" onClick={() => call('revoke', `/admin/api/seats/grants/${g.id}/revoke`, {})}>Revoke</Button> : null) },
                    ]} />
                )}

                <h3 className="adm-sub">Seats in use ({d.holds.length})</h3>
                {d.holds.length === 0 ? <p className="adm-muted">No seat in use yet.</p> : (
                    <ul className="adm-small">{d.holds.map((h, i) => <li key={i}>{h.email} — {h.status === 'PAID' ? 'paid' : `paying, kept until ${fmtDate(h.expires_at, true)}`}</li>)}</ul>
                )}
            </div>
        </Drawer>
    );
}
