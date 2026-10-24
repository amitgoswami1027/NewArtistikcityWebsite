import React, { useEffect, useState } from 'react';
import axios from 'axios';
import AdminLayout, { PageHeader, useToast, errorText } from '@/Components/Admin/AdminLayout';
import { Button, Badge, DataTable, Pagination, SearchBox, FilterSelect, Toolbar, EmptyState, Drawer, useGrid, useConfirm, fmtDate } from '@/Components/Admin/kit';
import { useForm, Field, TextField, Toggle, SlugField, DropZone, RichText, uploadImage } from '@/Components/Admin/form';

/** Free lessons offered to new students (/admin/free-courses). */
export default function FreeCourses() {
    const g = useGrid('grid');
    const [toastNode, toast] = useToast();
    const [dialog, confirm] = useConfirm();
    const [editing, setEditing] = useState(null); // {} for new, row for edit

    useEffect(() => {
        const q = new URLSearchParams(window.location.search);
        if (q.get('new')) setEditing({});
        const id = q.get('edit');
        if (id) { const row = g.rows.find((r) => String(r.id) === id); if (row) setEditing(row); }
    }, []); // eslint-disable-line react-hooks/exhaustive-deps

    const remove = async (r) => {
        if (!(await confirm({ title: `Delete “${r.title}”?`, body: 'Students will no longer see this free lesson.', confirmLabel: 'Delete', danger: true }))) return;
        axios.delete(`/admin/api/free-courses/${r.id}`).then((x) => { toast(x.data.message); g.reload(); }).catch((e) => toast(errorText(e), 'error'));
    };

    const columns = [
        { key: 'title', label: 'Free lesson', sort: 'title', render: (r) => (
            <div className="adm-cell">
                {r.photo_url ? <img src={r.photo_url} alt="" /> : <span className="adm-thumb"><i className="fa fa-gift" aria-hidden="true"></i></span>}
                <span className="adm-cell__text"><button type="button" className="adm-linkbtn" onClick={() => setEditing(r)}>{r.title}</button><small>{r.slug}</small></span>
            </div>
        ) },
        { key: 'videos', label: 'Videos', render: (r) => `${r.videos}` },
        { key: 'updated', label: 'Updated', sort: 'updated', hide: 'md', render: (r) => fmtDate(r.updated_at) },
        { key: 'status', label: 'Status', render: (r) => (Number(r.status) === 1 ? <Badge tone="green" dot>Live</Badge> : <Badge dot>Hidden</Badge>) },
        { key: 'actions', label: <span className="sr-only">Actions</span>, align: 'right', render: (r) => (
            <div className="adm-rowactions">
                <Button size="sm" icon="fa-pencil" onClick={() => setEditing(r)}>Edit</Button>
                <Button size="sm" variant="ghost" icon="fa-trash-o" onClick={() => remove(r)} aria-label={`Delete ${r.title}`} title="Delete" />
            </div>
        ) },
    ];

    return (
        <AdminLayout title="Free lessons">
            <PageHeader title="Free lessons" subtitle="Short video lessons new students can watch before they buy a course."
                actions={<Button variant="brand" icon="fa-plus" onClick={() => setEditing({})}>New free lesson</Button>} />
            <Toolbar>
                <SearchBox value={g.meta.q} onSearch={g.search} placeholder="Search free lessons" />
                <FilterSelect label="Status" value={g.filter('status')} onChange={(v) => g.go({ status: v })} options={[{ value: 'live', label: 'Live' }, { value: 'hidden', label: 'Hidden' }]} />
            </Toolbar>
            <DataTable columns={columns} rows={g.rows} meta={g.meta} loading={g.loading} onSort={g.sortBy} caption="Free lessons"
                empty={<EmptyState icon="fa-gift" title="No free lessons" action={<Button variant="brand" icon="fa-plus" onClick={() => setEditing({})}>Add one</Button>}>Free lessons help students try a medium before they enrol.</EmptyState>} />
            <Pagination meta={g.meta} onPage={g.setPage} onPer={g.setPer} />
            {editing && <FreeCourseDrawer item={editing.id ? editing : null} onClose={() => setEditing(null)} toast={toast} onSaved={() => { setEditing(null); g.reload(); }} />}
            {dialog}{toastNode}
        </AdminLayout>
    );
}

function FreeCourseDrawer({ item, onClose, onSaved, toast }) {
    const form = useForm({
        id: item ? item.id : '', title: item ? item.title : '', slug: item ? item.slug : '', description: item ? item.description || '' : '',
        status: item ? Number(item.status) === 1 : true, videos: item ? item.video_list : [],
    }, { title: ['required', 'max:255'], slug: ['slug'] });
    const [photo, setPhoto] = useState([]);
    const v = form.values;
    const save = () => {
        const fd = new FormData();
        fd.append('data', JSON.stringify(v));
        if (photo[0]) fd.append('photo', photo[0]);
        form.submit(() => axios.post('/admin/api/free-courses', fd)).then((r) => { toast(r.data.message); onSaved(); })
            .catch((e) => toast(e.response && e.response.status === 422 ? e.response.data.message : errorText(e), 'error'));
    };
    const setVideo = (i, k, val) => form.set('videos', v.videos.map((x, j) => (j === i ? { ...x, [k]: val } : x)));
    return (
        <Drawer open size="lg" title={item ? 'Edit free lesson' : 'New free lesson'} onClose={onClose}
            footer={<><Button onClick={onClose}>Cancel</Button><Button variant="brand" icon="fa-check" busy={form.busy} onClick={save}>{item ? 'Save' : 'Create'}</Button></>}>
            <form className="adm-stack" onSubmit={(e) => { e.preventDefault(); save(); }} noValidate>
                <TextField form={form} name="title" label="Title" required max={255} />
                <SlugField form={form} source="title" onCheck={(s) => axios.get('/admin/api/slug', { params: { table: 'free_courses', slug: s, except: item ? item.id : 0 } }).then((r) => r.data)} />
                <Field label="Description" name="description" optional>
                    <RichText name="description" value={v.description} onChange={(x) => form.set('description', x)} minHeight={160} onUpload={uploadImage} />
                </Field>
                <Field label="Cover photo" name="photo" optional>
                    <DropZone name="photo" value={photo} onChange={setPhoto} error={form.error('photo')} existing={item && item.photo_url ? [{ id: 'p', url: item.photo_url, name: item.photo }] : []} />
                </Field>
                <Field label="Videos" name="videos" error={form.error('videos')} hint="YouTube or other video links, in the order students should watch them." optional>
                    <div className="adm-stack">
                        {v.videos.map((x, i) => (
                            <div key={i} className="adm-list__row">
                                <input className="adm-input" placeholder="Video title" aria-label={`Video ${i + 1} title`} value={x.title} onChange={(e) => setVideo(i, 'title', e.target.value)} />
                                <input className="adm-input" placeholder="https://…" aria-label={`Video ${i + 1} link`} value={x.url} onChange={(e) => setVideo(i, 'url', e.target.value)} />
                                <button type="button" onClick={() => form.set('videos', v.videos.filter((_, j) => j !== i))} aria-label={`Remove video ${i + 1}`}><i className="fa fa-trash-o" aria-hidden="true"></i></button>
                            </div>
                        ))}
                        <div><Button size="sm" icon="fa-plus" onClick={() => form.set('videos', [...v.videos, { title: '', url: '' }])}>Add a video</Button></div>
                    </div>
                </Field>
                <Toggle name="status" checked={v.status} onChange={(x) => form.set('status', x)} label="Visible to students" />
            </form>
        </Drawer>
    );
}
