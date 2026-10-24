import React, { useMemo, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import axios from 'axios';
import AdminLayout, { PageHeader, useToast, errorText } from '@/Components/Admin/AdminLayout';
import { Button, Badge, DataTable, Pagination, SearchBox, Toolbar, EmptyState, Drawer, useConfirm } from '@/Components/Admin/kit';
import { useForm, Field, TextField, Toggle, SlugField, DropZone } from '@/Components/Admin/form';

/** Small lookup lists (mediums, genres, skills, course types / blog categories, tags) with client-side search, sort and paging. */
export default function Taxonomies() {
    const { group, tabs = [], type, items: initial = [] } = usePage().props;
    const [items, setItems] = useState(initial);
    const [q, setQ] = useState('');
    const [sort, setSort] = useState({ key: 'name', dir: 'asc' });
    const [page, setPage] = useState(1);
    const [per, setPer] = useState(20);
    const [editing, setEditing] = useState(null);
    const [toastNode, toast] = useToast();
    const [dialog, confirm] = useConfirm();
    const base = group === 'blog' ? '/admin/blog/taxonomy' : '/admin/catalog';
    const title = group === 'blog' ? 'Blog categories & tags' : 'Mediums, genres & skills';

    const filtered = useMemo(() => {
        const t = q.trim().toLowerCase();
        const rows = items.filter((i) => !t || i.name.toLowerCase().includes(t) || i.slug.includes(t));
        const val = (r) => (sort.key === 'usage' ? Number(r.usage) : String(r[sort.key] || '').toLowerCase());
        return [...rows].sort((a, b) => (val(a) < val(b) ? -1 : val(a) > val(b) ? 1 : 0) * (sort.dir === 'asc' ? 1 : -1));
    }, [items, q, sort]);
    const pages = Math.max(1, Math.ceil(filtered.length / per));
    const current = Math.min(page, pages);
    const rows = filtered.slice((current - 1) * per, current * per);
    const meta = { page: current, pages, total: filtered.length, perPage: per, sort: sort.key, dir: sort.dir };

    const remove = async (r) => {
        const body = r.usage > 0 && type.detachable ? `It is removed from ${r.usage} post${r.usage === 1 ? '' : 's'}.` : 'This cannot be undone.';
        if (!(await confirm({ title: `Delete “${r.name}”?`, body, confirmLabel: 'Delete', danger: true }))) return;
        axios.delete(`/admin/api/taxonomy/${type.key}/${r.id}`).then((x) => { setItems(x.data.items); toast(x.data.message); })
            .catch((e) => toast(errorText(e), 'error'));
    };
    const usageLabel = (n) => (group === 'blog' ? `${n} post${n === 1 ? '' : 's'}` : `${n} course${n === 1 ? '' : 's'}`);

    const columns = [
        { key: 'name', label: 'Name', sort: 'name', render: (r) => (
            <div className="adm-cell">
                {type.hasPhoto && (r.photo_url ? <img src={r.photo_url} alt="" /> : <span className="adm-thumb"><i className="fa fa-picture-o" aria-hidden="true"></i></span>)}
                <span className="adm-cell__text"><button type="button" className="adm-linkbtn" onClick={() => setEditing(r)}>{r.name}</button><small>{r.slug}</small></span>
            </div>
        ) },
        { key: 'usage', label: 'Used by', sort: 'usage', render: (r) => (r.usage > 0 ? usageLabel(r.usage) : <span className="adm-muted">Not used</span>) },
        { key: 'status', label: 'Status', render: (r) => (r.status ? <Badge tone="green" dot>On</Badge> : <Badge dot>Off</Badge>) },
        { key: 'actions', label: <span className="sr-only">Actions</span>, align: 'right', render: (r) => (
            <div className="adm-rowactions">
                <Button size="sm" icon="fa-pencil" onClick={() => setEditing(r)}>Edit</Button>
                {!(type.locked || []).includes(r.id) && <Button size="sm" variant="ghost" icon="fa-trash-o" onClick={() => remove(r)} aria-label={`Delete ${r.name}`} title="Delete" />}
            </div>
        ) },
    ];

    return (
        <AdminLayout title={title}>
            <PageHeader title={title} subtitle={type.hint}
                actions={<Button variant="brand" icon="fa-plus" onClick={() => setEditing({})}>Add {type.label}</Button>} />
            <nav className="adm-tabs" aria-label="Lists">
                {tabs.map((t) => (
                    <a key={t.key} href={`${base}?type=${t.key}`} className={t.key === type.key ? 'is-on' : ''} aria-current={t.key === type.key ? 'page' : undefined}>{t.label} <Badge>{t.count}</Badge></a>
                ))}
            </nav>
            <Toolbar>
                <SearchBox value={q} onSearch={(x) => { setQ(x); setPage(1); }} placeholder={`Search ${type.plural.toLowerCase()}`} delay={150} />
            </Toolbar>
            <DataTable columns={columns} rows={rows} meta={meta} onSort={(k) => setSort((s) => ({ key: k, dir: s.key === k && s.dir === 'asc' ? 'desc' : 'asc' }))} caption={type.plural}
                empty={<EmptyState icon="fa-tags" title={q ? 'Nothing matches' : `No ${type.plural.toLowerCase()} yet`} action={!q && <Button variant="brand" icon="fa-plus" onClick={() => setEditing({})}>Add the first one</Button>} />} />
            <Pagination meta={meta} onPage={setPage} onPer={(n) => { setPer(n); setPage(1); }} />
            {editing && <TermDrawer type={type} item={editing.id ? editing : null} onClose={() => setEditing(null)} toast={toast} onSaved={(r) => { setItems(r.data.items); toast(r.data.message); setEditing(null); }} />}
            {dialog}{toastNode}
        </AdminLayout>
    );
}

function TermDrawer({ type, item, onClose, onSaved, toast }) {
    const form = useForm({ name: item ? item.name : '', slug: item ? item.slug : '', status: item ? item.status : true }, { name: ['required', 'max:100'], slug: ['slug'] });
    const [photo, setPhoto] = useState([]);
    const save = () => {
        const fd = new FormData();
        if (item) fd.append('id', item.id);
        fd.append('name', form.values.name);
        fd.append('slug', form.values.slug);
        fd.append('status', form.values.status ? '1' : '0');
        if (photo[0]) fd.append('photo', photo[0]);
        form.submit(() => axios.post(`/admin/api/taxonomy/${type.key}`, fd)).then(onSaved)
            .catch((e) => toast(e.response && e.response.status === 422 ? e.response.data.message : errorText(e), 'error'));
    };
    return (
        <Drawer open size="sm" title={item ? `Edit ${type.label}` : `New ${type.label}`} onClose={onClose}
            footer={<><Button onClick={onClose}>Cancel</Button><Button variant="brand" icon="fa-check" busy={form.busy} onClick={save}>{item ? 'Save' : 'Add'}</Button></>}>
            <form className="adm-stack" onSubmit={(e) => { e.preventDefault(); save(); }} noValidate>
                <TextField form={form} name="name" label="Name" required max={100} />
                <SlugField form={form} source="name" label="URL name" />
                {type.hasPhoto && (
                    <Field label="Photo" name="photo" optional>
                        <DropZone name="photo" value={photo} onChange={setPhoto} error={form.error('photo')} existing={item && item.photo_url ? [{ id: 'p', url: item.photo_url, name: item.name }] : []} />
                    </Field>
                )}
                <Toggle name="status" checked={form.values.status} onChange={(x) => form.set('status', x)} label="Shown on the site" />
            </form>
        </Drawer>
    );
}
