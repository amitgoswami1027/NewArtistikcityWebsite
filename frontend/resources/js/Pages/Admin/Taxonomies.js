import React, { useMemo, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import axios from 'axios';
import AdminLayout, { PageHeader, useToast, errorText } from '@/Components/Admin/AdminLayout';
import { Button, Badge, DataTable, Pagination, SearchBox, Toolbar, EmptyState, Drawer, useConfirm } from '@/Components/Admin/kit';
import { useForm, Field, TextField, Toggle, SlugField, DropZone } from '@/Components/Admin/form';

/**
 * Small lookup lists (mediums, genres, skills, course types / blog categories, tags) with client-side search, sort and paging.
 * The course filter lists are also a Tag & Hierarchy manager: items can be nested under a parent (shown as a tree)
 * and archived (gone from the filters and picklists, kept on courses, restorable).
 */

/** Rows in tree order (parent, then its children indented), with depth and the path of names. */
export function treeOrder(items) {
    const byParent = {};
    const ids = new Set(items.map((i) => String(i.id)));
    items.forEach((i) => { const k = i.parent_id && ids.has(String(i.parent_id)) ? String(i.parent_id) : ''; (byParent[k] = byParent[k] || []).push(i); });
    const out = [];
    const walk = (k, depth, path) => (byParent[k] || []).sort((a, b) => a.name.localeCompare(b.name)).forEach((i) => {
        out.push({ ...i, depth, path: [...path, i.name] });
        if (depth < 6) walk(String(i.id), depth + 1, [...path, i.name]);
    });
    walk('', 0, []);
    return out;
}
export default function Taxonomies() {
    const { group, tabs = [], type, items: initial = [] } = usePage().props;
    const [items, setItems] = useState(initial);
    const [q, setQ] = useState('');
    const [sort, setSort] = useState({ key: 'name', dir: 'asc' });
    const [page, setPage] = useState(1);
    const [per, setPer] = useState(20);
    const [editing, setEditing] = useState(null);
    const [showArchived, setShowArchived] = useState(false);
    const tree = !!(type && type.hierarchy);
    const [toastNode, toast] = useToast();
    const [dialog, confirm] = useConfirm();
    const base = group === 'blog' ? '/admin/blog/taxonomy' : '/admin/catalog';
    const title = group === 'blog' ? 'Blog categories & tags' : 'Mediums, genres & skills';

    const filtered = useMemo(() => {
        const t = q.trim().toLowerCase();
        const visible = items.filter((i) => showArchived || !i.archived);
        if (tree && !t && sort.key === 'name' && sort.dir === 'asc') return treeOrder(visible);
        const rows = visible.filter((i) => !t || i.name.toLowerCase().includes(t) || i.slug.includes(t));
        const val = (r) => (sort.key === 'usage' ? Number(r.usage) : String(r[sort.key] || '').toLowerCase());
        return [...rows].sort((a, b) => (val(a) < val(b) ? -1 : val(a) > val(b) ? 1 : 0) * (sort.dir === 'asc' ? 1 : -1));
    }, [items, q, sort, showArchived, tree]);
    const archive = (r, archived) => axios.post(`/admin/api/taxonomy/${type.key}/${r.id}/archive`, { archived })
        .then((x) => { setItems(x.data.items); toast(x.data.message); }).catch((e) => toast(errorText(e), 'error'));
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
                <span className="adm-cell__text" style={r.depth ? { paddingLeft: r.depth * 22 } : undefined}>
                    <button type="button" className="adm-linkbtn" onClick={() => setEditing(r)}>{r.depth ? <span className="adm-tree" aria-hidden="true">└ </span> : null}{r.name}</button>
                    <small>{r.path && r.path.length > 1 ? r.path.join(' › ') : r.slug}</small>
                </span>
            </div>
        ) },
        { key: 'usage', label: 'Used by', sort: 'usage', render: (r) => (r.usage > 0 ? usageLabel(r.usage) : <span className="adm-muted">Not used</span>) },
        { key: 'status', label: 'Status', render: (r) => (r.archived ? <Badge tone="amber" dot>Archived</Badge> : r.status ? <Badge tone="green" dot>On</Badge> : <Badge dot>Off</Badge>) },
        { key: 'actions', label: <span className="sr-only">Actions</span>, align: 'right', render: (r) => (
            <div className="adm-rowactions">
                <Button size="sm" icon="fa-pencil" onClick={() => setEditing(r)}>Edit</Button>
                {tree && (r.archived
                    ? <Button size="sm" variant="ghost" icon="fa-undo" onClick={() => archive(r, false)}>Restore</Button>
                    : <Button size="sm" variant="ghost" icon="fa-archive" onClick={() => archive(r, true)} aria-label={`Archive ${r.name}`} title="Archive: hide from filters, keep on courses" />)}
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
                {tree && <label className="adm-check"><input type="checkbox" checked={showArchived} onChange={(e) => setShowArchived(e.target.checked)} /> Show archived ({items.filter((i) => i.archived).length})</label>}
            </Toolbar>
            <DataTable columns={columns} rows={rows} meta={meta} onSort={(k) => setSort((s) => ({ key: k, dir: s.key === k && s.dir === 'asc' ? 'desc' : 'asc' }))} caption={type.plural}
                empty={<EmptyState icon="fa-tags" title={q ? 'Nothing matches' : `No ${type.plural.toLowerCase()} yet`} action={!q && <Button variant="brand" icon="fa-plus" onClick={() => setEditing({})}>Add the first one</Button>} />} />
            <Pagination meta={meta} onPage={setPage} onPer={(n) => { setPer(n); setPage(1); }} />
            {editing && <TermDrawer type={type} item={editing.id ? editing : null} onClose={() => setEditing(null)} toast={toast} onSaved={(r) => { setItems(r.data.items); toast(r.data.message); setEditing(null); }} items={items} />}
            {dialog}{toastNode}
        </AdminLayout>
    );
}

function TermDrawer({ type, item, onClose, onSaved, toast, items = [] }) {
    const form = useForm({ name: item ? item.name : '', slug: item ? item.slug : '', status: item ? item.status : true, parent_id: item && item.parent_id ? String(item.parent_id) : '' },
        { name: ['required', 'max:100'], slug: ['slug'] });
    // a parent can't be the item itself or one of its descendants
    const blocked = new Set();
    if (item) {
        blocked.add(String(item.id));
        let grew = true;
        while (grew) { grew = false; items.forEach((i) => { if (i.parent_id && blocked.has(String(i.parent_id)) && !blocked.has(String(i.id))) { blocked.add(String(i.id)); grew = true; } }); }
    }
    const parents = treeOrder(items.filter((i) => !i.archived && !blocked.has(String(i.id))));
    const [photo, setPhoto] = useState([]);
    const save = () => {
        const fd = new FormData();
        if (item) fd.append('id', item.id);
        fd.append('name', form.values.name);
        fd.append('slug', form.values.slug);
        fd.append('status', form.values.status ? '1' : '0');
        if (type.hierarchy) fd.append('parent_id', form.values.parent_id || '');
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
                {type.hierarchy && (
                    <Field label="Sits under" name="parent_id" optional hint="Nest it under a broader item (e.g. Seascape under Landscape). The filters show it as a sub-item.">
                        <select id="f-parent_id" className="adm-input" value={form.values.parent_id} onChange={(e) => form.set('parent_id', e.target.value)}>
                            <option value="">Top level</option>
                            {parents.map((p) => <option key={p.id} value={String(p.id)}>{'\u00a0\u00a0'.repeat(p.depth)}{p.name}</option>)}
                        </select>
                    </Field>
                )}
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
