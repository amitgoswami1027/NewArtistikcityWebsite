import React, { useEffect, useRef, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import { Inertia } from '@inertiajs/inertia';
import axios from 'axios';
import AdminLayout, { PageHeader, useToast, errorText } from '@/Components/Admin/AdminLayout';
import { Button, Badge, Card, DataTable, Pagination, SearchBox, Segments, Toolbar, EmptyState, Drawer, Stat, useGrid, useConfirm, fmtDate } from '@/Components/Admin/kit';
import { useForm, Field, TextField, RichText, uploadImage } from '@/Components/Admin/form';

const CAMPAIGN_TONE = { draft: 'grey', sending: 'amber', sent: 'green', failed: 'red' };
const CAMPAIGN_LABEL = { draft: 'Draft', sending: 'Sending…', sent: 'Sent', failed: 'Failed' };

/** Newsletter: subscribers and e-mail campaigns (/admin/newsletter). */
export default function Newsletter() {
    const { counts = {}, campaigns = [], adminEmail } = usePage().props;
    const [tab, setTab] = useState(() => (typeof window !== 'undefined' && new URLSearchParams(window.location.search).get('tab') === 'campaigns' ? 'campaigns' : 'subscribers'));
    const [toastNode, toast] = useToast();
    const [dialog, confirm] = useConfirm();
    const switchTab = (t) => {
        setTab(t);
        const url = new URL(window.location.href);
        if (t === 'campaigns') url.searchParams.set('tab', 'campaigns'); else url.searchParams.delete('tab');
        window.history.replaceState(window.history.state, '', url.toString());
    };
    return (
        <AdminLayout title="Newsletter">
            <PageHeader title="Newsletter" subtitle="People who signed up in the site footer, and the e-mails you send them." />
            <div className="adm-stats">
                <Stat icon="fa-users" label="Subscribed" value={counts.active || 0} />
                <Stat icon="fa-line-chart" label="New in the last 30 days" value={counts.last30 || 0} />
                <Stat icon="fa-user-times" label="Unsubscribed" value={counts.unsubscribed || 0} />
                <Stat icon="fa-paper-plane-o" label="Newsletters sent" value={campaigns.filter((c) => c.status === 'sent').length} />
            </div>
            <div className="adm-tabs" role="tablist">
                <button type="button" role="tab" aria-selected={tab === 'subscribers'} className={tab === 'subscribers' ? 'is-on' : ''} onClick={() => switchTab('subscribers')}>Subscribers <Badge>{counts.active || 0}</Badge></button>
                <button type="button" role="tab" aria-selected={tab === 'campaigns'} className={tab === 'campaigns' ? 'is-on' : ''} onClick={() => switchTab('campaigns')}>Newsletters <Badge>{campaigns.length}</Badge></button>
            </div>
            {tab === 'subscribers' ? <Subscribers toast={toast} confirm={confirm} /> : <Campaigns campaigns={campaigns} toast={toast} confirm={confirm} adminEmail={adminEmail} active={counts.active || 0} />}
            {dialog}{toastNode}
        </AdminLayout>
    );
}

function Subscribers({ toast, confirm }) {
    const g = useGrid('grid', ['counts']);
    const [selected, setSelected] = useState([]);
    const [adding, setAdding] = useState(false);
    const form = useForm({ emails: '' }, { emails: ['required'] });
    const add = () => form.submit(() => axios.post('/admin/api/newsletter/subscribers', form.values)).then((r) => {
        toast(r.data.message); setAdding(false); form.reset({ emails: '' }); g.reload();
    }).catch((e) => { if (!(e.response && e.response.status === 422)) toast(errorText(e), 'error'); });
    const toggle = (r) => axios.post(`/admin/api/newsletter/subscribers/${r.id}/status`, { subscribed: !!r.unsubscribed_at })
        .then((x) => { toast(x.data.message); g.reload(); }).catch((e) => toast(errorText(e), 'error'));
    const removeSelected = async () => {
        if (!(await confirm({ title: `Delete ${selected.length} subscriber${selected.length === 1 ? '' : 's'}?`, body: 'They are removed from the list completely. To stop e-mails but keep the record, unsubscribe them instead.', confirmLabel: 'Delete', danger: true }))) return;
        axios.post('/admin/api/newsletter/subscribers/delete', { ids: selected }).then((r) => { toast(r.data.message); setSelected([]); g.reload(); })
            .catch((e) => toast(errorText(e), 'error'));
    };
    const columns = [
        { key: 'email', label: 'E-mail', sort: 'email', render: (r) => <span className="adm-cell__text"><b>{r.email}</b>{r.student && <small>Student: {r.student}</small>}</span> },
        { key: 'source', label: 'Source', hide: 'md', render: (r) => (r.source === 'admin' ? 'Added by admin' : r.source === 'footer' ? 'Site footer' : <span className="adm-muted">—</span>) },
        { key: 'date', label: 'Subscribed', sort: 'date', render: (r) => fmtDate(r.created_at) },
        { key: 'state', label: 'Status', render: (r) => (r.unsubscribed_at ? <Badge>Unsubscribed {fmtDate(r.unsubscribed_at)}</Badge> : <Badge tone="green" dot>Subscribed</Badge>) },
        { key: 'actions', label: <span className="sr-only">Actions</span>, align: 'right', render: (r) => (
            <Button size="sm" variant="ghost" onClick={() => toggle(r)}>{r.unsubscribed_at ? 'Resubscribe' : 'Unsubscribe'}</Button>
        ) },
    ];
    return (
        <>
            <Toolbar end={<>
                <Button as="a" icon="fa-download" href={g.exportUrl('/admin/newsletter/export')}>Export CSV</Button>
                <Button variant="brand" icon="fa-plus" onClick={() => setAdding(true)}>Add subscribers</Button>
            </>}>
                <SearchBox value={g.meta.q} onSearch={g.search} placeholder="Search e-mail addresses" />
                <Segments label="Status" value={g.filter('state')} onChange={(v) => g.go({ state: v })}
                    options={[{ value: '', label: 'All' }, { value: 'active', label: 'Subscribed' }, { value: 'unsubscribed', label: 'Unsubscribed' }]} />
            </Toolbar>
            {selected.length > 0 && (
                <div className="adm-bulk" role="region" aria-label="Bulk actions">
                    <span>{selected.length} selected</span>
                    <Button size="sm" variant="danger" icon="fa-trash-o" onClick={removeSelected}>Delete</Button>
                    <Button size="sm" variant="line" onClick={() => setSelected([])}>Clear</Button>
                </div>
            )}
            <DataTable columns={columns} rows={g.rows} meta={g.meta} loading={g.loading} onSort={g.sortBy} caption="Subscribers" selected={selected} onSelect={setSelected}
                empty={<EmptyState icon="fa-envelope-o" title={g.meta.q ? 'No matching subscribers' : 'No subscribers yet'}>{g.meta.q ? 'Try another search.' : 'Visitors can subscribe in the site footer, or add addresses yourself.'}</EmptyState>} />
            <Pagination meta={g.meta} onPage={g.setPage} onPer={g.setPer} />
            {adding && (
                <Drawer open size="sm" title="Add subscribers" subtitle="Only add people who agreed to receive the newsletter." onClose={() => setAdding(false)}
                    footer={<><Button onClick={() => setAdding(false)}>Cancel</Button><Button variant="brand" icon="fa-check" busy={form.busy} onClick={add}>Add</Button></>}>
                    <TextField form={form} name="emails" label="E-mail addresses" rows={8} required hint="One per line, or separated by commas. Addresses already on the list are skipped." placeholder={'asha@example.com\nrahul@example.com'} />
                </Drawer>
            )}
        </>
    );
}

function Campaigns({ campaigns, toast, confirm, adminEmail, active }) {
    const [editing, setEditing] = useState(null);
    const sending = campaigns.some((c) => c.status === 'sending');
    useEffect(() => {
        if (!sending) return undefined;
        const t = setInterval(() => Inertia.reload({ only: ['campaigns', 'counts'], preserveScroll: true }), 3000);
        return () => clearInterval(t);
    }, [sending]);
    const reload = () => Inertia.reload({ only: ['campaigns', 'counts'], preserveScroll: true });
    const remove = async (c) => {
        if (!(await confirm({ title: `Delete “${c.subject}”?`, confirmLabel: 'Delete', danger: true }))) return;
        axios.delete(`/admin/api/newsletter/campaigns/${c.id}`).then((r) => { toast(r.data.message); reload(); }).catch((e) => toast(errorText(e), 'error'));
    };
    const duplicate = (c) => axios.post(`/admin/api/newsletter/campaigns/${c.id}/duplicate`).then((r) => { toast(r.data.message); reload(); setEditing(r.data.campaign); })
        .catch((e) => toast(errorText(e), 'error'));
    return (
        <>
            <Toolbar end={<Button variant="brand" icon="fa-pencil" onClick={() => setEditing({})}>Write a newsletter</Button>}>
                <span className="adm-muted">Sent to <b>{active}</b> subscribed address{active === 1 ? '' : 'es'}. Every e-mail has an unsubscribe link.</span>
            </Toolbar>
            {campaigns.length === 0 ? (
                <Card><EmptyState icon="fa-paper-plane-o" title="No newsletters yet" action={<Button variant="brand" icon="fa-pencil" onClick={() => setEditing({})}>Write the first one</Button>}>
                    Share new courses, workshops, student work and studio news.
                </EmptyState></Card>
            ) : (
                <ul className="adm-campaigns">
                    {campaigns.map((c) => (
                        <li key={c.id} className="adm-campaign">
                            <div className="adm-campaign__main">
                                <Badge tone={CAMPAIGN_TONE[c.status]} dot>{CAMPAIGN_LABEL[c.status] || c.status}</Badge>
                                <h3>{c.subject}</h3>
                                <p className="adm-muted">
                                    {c.status === 'sent' ? `Sent ${fmtDate(c.sent_at, true)} to ${c.sent_count} of ${c.recipients}${c.failed_count ? ` · ${c.failed_count} failed` : ''}`
                                        : c.status === 'sending' ? `${c.sent_count + c.failed_count} of ${c.recipients} sent so far…`
                                            : `Last edited ${fmtDate(c.updated_at, true)}${c.author ? ` by ${c.author}` : ''}`}
                                </p>
                                {c.status === 'sending' && <span className="adm-progress"><span style={{ width: `${c.recipients ? Math.round(((c.sent_count + c.failed_count) * 100) / c.recipients) : 0}%` }}></span></span>}
                            </div>
                            <div className="adm-rowactions">
                                {(c.status === 'draft' || c.status === 'failed') ? <Button size="sm" icon="fa-pencil" onClick={() => setEditing(c)}>Edit</Button>
                                    : <Button size="sm" icon="fa-eye" onClick={() => setEditing(c)}>View</Button>}
                                <Button size="sm" variant="ghost" icon="fa-files-o" onClick={() => duplicate(c)} aria-label={`Duplicate ${c.subject}`} title="Duplicate" />
                                {c.status !== 'sending' && <Button size="sm" variant="ghost" icon="fa-trash-o" onClick={() => remove(c)} aria-label={`Delete ${c.subject}`} title="Delete" />}
                            </div>
                        </li>
                    ))}
                </ul>
            )}
            {editing && <Composer item={editing.id ? editing : null} onClose={() => setEditing(null)} toast={toast} confirm={confirm} adminEmail={adminEmail} active={active} onChanged={reload} />}
        </>
    );
}

function Composer({ item, onClose, toast, confirm, adminEmail, active, onChanged }) {
    const [current, setCurrent] = useState(item);
    const readOnly = current && current.status !== 'draft' && current.status !== 'failed';
    const form = useForm({ subject: item ? item.subject : '', preheader: item ? item.preheader || '' : '', body: item ? item.body || '' : '' },
        { subject: ['required', 'max:255'], preheader: ['max:255'], body: ['required'] });
    const [view, setView] = useState(readOnly ? 'preview' : 'edit');
    const [previewHtml, setPreviewHtml] = useState('');
    const [testTo, setTestTo] = useState(adminEmail || '');
    const [busy, setBusy] = useState(null);
    const frame = useRef(null);

    useEffect(() => {
        if (view !== 'preview') return;
        axios.post('/admin/api/newsletter/preview', form.values, { responseType: 'text' }).then((r) => setPreviewHtml(r.data)).catch((e) => toast(errorText(e), 'error'));
    }, [view]); // eslint-disable-line react-hooks/exhaustive-deps

    const saveDraft = () => form.submit(() => axios.post('/admin/api/newsletter/campaigns', { ...form.values, id: current ? current.id : undefined }))
        .then((r) => { setCurrent(r.data.campaign); toast(r.data.message); onChanged(); return r.data.campaign; })
        .catch((e) => { if (!e.invalid) toast(e.response && e.response.status === 422 ? e.response.data.message : errorText(e), 'error'); throw e; });
    const ensureSaved = () => (form.dirty || !current ? saveDraft() : Promise.resolve(current));
    const sendTest = () => {
        setBusy('test');
        ensureSaved().then((c) => axios.post(`/admin/api/newsletter/campaigns/${c.id}/test`, { email: testTo }))
            .then((r) => toast(r.data.message)).catch((e) => { if (e && e.response) toast(errorText(e), 'error'); }).finally(() => setBusy(null));
    };
    const sendAll = () => {
        ensureSaved().then(async (c) => {
            if (!(await confirm({ title: `Send “${c.subject}” now?`, body: `It goes to ${active} subscribed address${active === 1 ? '' : 'es'} and can't be recalled. Send yourself a test first if you haven't.`, confirmLabel: `Send to ${active}` }))) return;
            setBusy('send');
            axios.post(`/admin/api/newsletter/campaigns/${c.id}/send`).then((r) => { toast(r.data.message); onChanged(); onClose(); })
                .catch((e) => toast(errorText(e), 'error')).finally(() => setBusy(null));
        }).catch(() => {});
    };

    return (
        <Drawer open size="lg" title={readOnly ? current.subject : current ? 'Edit newsletter' : 'New newsletter'} subtitle={readOnly ? `Sent ${fmtDate(current.sent_at, true)}` : 'Write, preview, send yourself a test, then send it to everyone.'} onClose={onClose}
            footer={readOnly ? <Button onClick={onClose}>Close</Button> : <>
                <Button onClick={() => saveDraft().catch(() => {})} busy={form.busy && !busy} icon="fa-floppy-o">Save draft</Button>
                <Button variant="brand" icon="fa-paper-plane" busy={busy === 'send'} onClick={sendAll} disabled={!active}>Send to {active}</Button>
            </>}>
            {!readOnly && (
                <div className="adm-rte__tabs adm-composer-tabs" role="tablist">
                    <button type="button" role="tab" aria-selected={view === 'edit'} className={view === 'edit' ? 'is-on' : ''} onClick={() => setView('edit')}>Write</button>
                    <button type="button" role="tab" aria-selected={view === 'preview'} className={view === 'preview' ? 'is-on' : ''} onClick={() => setView('preview')}>E-mail preview</button>
                </div>
            )}
            {view === 'edit' && !readOnly && (
                <form className="adm-stack" onSubmit={(e) => { e.preventDefault(); saveDraft().catch(() => {}); }} noValidate>
                    <TextField form={form} name="subject" label="Subject" required max={255} placeholder="e.g. New: Weekend watercolour florals workshop" />
                    <TextField form={form} name="preheader" label="Preview text" max={255} hint="Shown after the subject in most inboxes." optional />
                    <Field label="Message" name="body" required error={form.error('body')}>
                        <RichText name="body" value={form.values.body} onChange={(x) => form.set('body', x)} minHeight={320} onUpload={uploadImage} error={form.error('body')} />
                    </Field>
                    <div className="adm-task is-edit">
                        <Field label="Send a test to" name="test">
                            <div className="adm-inline">
                                <input id="f-test" className="adm-input adm-grow" type="email" value={testTo} onChange={(e) => setTestTo(e.target.value)} />
                                <Button icon="fa-paper-plane-o" busy={busy === 'test'} onClick={sendTest}>Send test</Button>
                            </div>
                        </Field>
                    </div>
                </form>
            )}
            {view === 'preview' && (
                previewHtml ? <iframe ref={frame} title="E-mail preview" className="adm-mailframe" sandbox="" srcDoc={previewHtml}></iframe>
                    : <div className="adm-stack" aria-busy="true"><span className="adm-skel" style={{ height: 300 }}></span></div>
            )}
        </Drawer>
    );
}
