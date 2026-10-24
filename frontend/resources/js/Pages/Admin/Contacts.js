import React, { useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import axios from 'axios';
import AdminLayout, { PageHeader, useToast, errorText } from '@/Components/Admin/AdminLayout';
import { Button, Badge, DataTable, Pagination, SearchBox, Segments, Toolbar, EmptyState, Drawer, useGrid, useConfirm, fmtDate } from '@/Components/Admin/kit';

const STATE = { 1: ['New', 'pink'], 2: ['Read', 'grey'], 3: ['Archived', 'blue'] };

/** Contact form messages (/admin/contacts): read, reply by e-mail, archive, delete. */
export default function Contacts() {
    const { counts = {} } = usePage().props;
    const g = useGrid('grid', ['counts']);
    const [open, setOpen] = useState(null);
    const [selected, setSelected] = useState([]);
    const [toastNode, toast] = useToast();
    const [dialog, confirm] = useConfirm();

    const setStatus = (r, status, quiet) => axios.post(`/admin/api/contacts/${r.id}/status`, { status })
        .then((x) => { if (!quiet) toast(x.data.message); g.reload(); }).catch((e) => toast(errorText(e), 'error'));
    const view = (r) => { setOpen(r); if (Number(r.status) === 1) setStatus(r, 'read', true); };
    const remove = async (ids) => {
        if (!(await confirm({ title: `Delete ${ids.length === 1 ? 'this message' : `${ids.length} messages`}?`, confirmLabel: 'Delete', danger: true }))) return;
        axios.post('/admin/api/contacts/delete', { ids }).then((x) => { toast(x.data.message); setSelected([]); setOpen(null); g.reload(); })
            .catch((e) => toast(errorText(e), 'error'));
    };

    const columns = [
        { key: 'name', label: 'From', sort: 'name', render: (r) => (
            <span className="adm-cell__text"><button type="button" className="adm-linkbtn" onClick={() => view(r)}>{Number(r.status) === 1 ? <b>{r.name || r.email}</b> : r.name || r.email}</button><small>{r.email}</small></span>
        ) },
        { key: 'message', label: 'Message', render: (r) => <span className="adm-clip">{r.message}</span> },
        { key: 'date', label: 'Received', sort: 'date', hide: 'md', render: (r) => fmtDate(r.created_at, true) },
        { key: 'status', label: 'Status', render: (r) => { const [l, t] = STATE[Number(r.status)] || STATE[2]; return <Badge tone={t} dot>{l}</Badge>; } },
    ];

    return (
        <AdminLayout title="Contact messages">
            <PageHeader title="Contact messages" subtitle="Everything sent through the contact form. Reply by e-mail, then archive the message."
                actions={<Button as="a" icon="fa-download" href={g.exportUrl('/admin/contacts/export')}>Export CSV</Button>} />
            <Toolbar end={<Segments label="Status" value={g.filter('status')} onChange={(v) => g.go({ status: v })}
                options={[{ value: '', label: 'All', count: counts.all }, { value: 'new', label: 'New', count: counts.new }, { value: 'read', label: 'Read', count: counts.read }, { value: 'archived', label: 'Archived', count: counts.archived }]} />}>
                <SearchBox value={g.meta.q} onSearch={g.search} placeholder="Search name, e-mail or message" />
            </Toolbar>
            {selected.length > 0 && (
                <div className="adm-bulk" role="region" aria-label="Bulk actions">
                    <span>{selected.length} selected</span>
                    <Button size="sm" variant="danger" icon="fa-trash-o" onClick={() => remove(selected)}>Delete</Button>
                    <Button size="sm" variant="line" onClick={() => setSelected([])}>Clear</Button>
                </div>
            )}
            <DataTable columns={columns} rows={g.rows} meta={g.meta} loading={g.loading} onSort={g.sortBy} caption="Contact messages" onRowClick={view} selected={selected} onSelect={setSelected}
                empty={<EmptyState icon="fa-inbox" title={g.meta.q || g.filter('status') ? 'No messages match' : 'No messages yet'}>Messages from the contact page land here.</EmptyState>} />
            <Pagination meta={g.meta} onPage={g.setPage} onPer={g.setPer} />
            {open && (
                <Drawer open title={open.name || open.email} subtitle={`Received ${fmtDate(open.created_at, true)}`} onClose={() => setOpen(null)}
                    footer={<>
                        <Button variant="ghost" icon="fa-trash-o" onClick={() => remove([open.id])}>Delete</Button>
                        {Number(open.status) !== 3 ? <Button icon="fa-archive" onClick={() => { setStatus(open, 'archived'); setOpen(null); }}>Archive</Button>
                            : <Button icon="fa-undo" onClick={() => { setStatus(open, 'new'); setOpen(null); }}>Move to inbox</Button>}
                        <Button as="a" variant="brand" icon="fa-reply" href={`mailto:${open.email}?subject=${encodeURIComponent('Re: your message to ArtistikCity')}`}>Reply by e-mail</Button>
                    </>}>
                    <dl className="adm-dl">
                        <dt>E-mail</dt><dd><a href={`mailto:${open.email}`}>{open.email}</a></dd>
                        <dt>Phone</dt><dd>{open.mobile_no ? <a href={`tel:${open.mobile_no}`}>{open.mobile_no}</a> : '—'}</dd>
                    </dl>
                    <h3 className="adm-sub">Message</h3>
                    <p className="adm-message">{open.message}</p>
                </Drawer>
            )}
            {dialog}{toastNode}
        </AdminLayout>
    );
}
