import React, { useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import axios from 'axios';
import AdminLayout, { PageHeader, useToast, errorText } from '@/Components/Admin/AdminLayout';

const EMPTY = { id: null, name: '', title: '', description: '', status: 1 };

export default function Testimonials() {
    const { testimonials: initial = [] } = usePage().props;
    const [items, setItems] = useState(initial);
    const [form, setForm] = useState(null);
    const [photo, setPhoto] = useState(null);
    const [busy, setBusy] = useState(false);
    const [toastNode, toast] = useToast();

    const save = (e) => {
        e.preventDefault();
        setBusy(true);
        const fd = new FormData();
        if (form.id) fd.append('id', form.id);
        fd.append('name', form.name);
        fd.append('title', form.title || '');
        fd.append('description', form.description);
        fd.append('status', String(form.status));
        if (photo) fd.append('photo', photo);
        axios.post('/admin/console/api/testimonials', fd).then((r) => {
            setItems(form.id ? items.map((x) => (x.id === r.data.id ? r.data : x)) : [r.data, ...items]);
            setForm(null); setPhoto(null);
            toast('Testimonial saved');
        }).catch((err) => toast(errorText(err), 'error')).finally(() => setBusy(false));
    };
    const quickToggle = (t) => {
        const fd = new FormData();
        ['id', 'name', 'title', 'description'].forEach((k) => fd.append(k, t[k] || ''));
        fd.append('status', String(t.status) === '1' ? '0' : '1');
        axios.post('/admin/console/api/testimonials', fd).then((r) => { setItems(items.map((x) => (x.id === t.id ? r.data : x))); toast(String(r.data.status) === '1' ? 'Now showing on the site' : 'Hidden from the site'); })
            .catch((err) => toast(errorText(err), 'error'));
    };
    const remove = (t) => {
        if (!window.confirm(`Delete the testimonial from ${t.name}?`)) return;
        axios.delete(`/admin/console/api/testimonials/${t.id}`).then(() => { setItems(items.filter((x) => x.id !== t.id)); toast('Deleted'); }).catch((err) => toast(errorText(err), 'error'));
    };

    return (
        <AdminLayout title="Testimonials">
            <PageHeader title="Testimonials" subtitle="Student voices shown on the home page. Published testimonials appear in the “What our students say” section."
                        actions={<button type="button" onClick={() => { setForm(EMPTY); setPhoto(null); }} className="h-11 px-5 rounded-full bg-brand text-white font-bold hover:bg-brand-dark"><i className="fa fa-plus mr-2" aria-hidden="true"></i>New testimonial</button>} />
            <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-5">
                {items.map((t) => (
                    <article key={t.id} className={`rounded-2xl bg-white border p-6 flex flex-col ${String(t.status) === '1' ? 'border-gray-200' : 'border-dashed border-gray-300 opacity-75'}`}>
                        <div className="flex items-center gap-3">
                            {t.photo ? <img src={`/storage/uploads/testimonials/${t.id}/${t.photo}`} alt="" className="w-12 h-12 rounded-full object-cover" /> : <span className="w-12 h-12 rounded-full bg-brand-soft text-brand font-black flex items-center justify-center">{String(t.name).charAt(0)}</span>}
                            <div className="flex-1"><p className="font-bold text-ink">{t.name}</p><p className="text-sm text-gray-500">{t.title}</p></div>
                            <span className={`rounded-full px-2.5 py-1 text-xs font-bold ${String(t.status) === '1' ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-600'}`}>{String(t.status) === '1' ? 'Published' : 'Hidden'}</span>
                        </div>
                        <p className="mt-4 text-gray-700 flex-1">“{t.description}”</p>
                        <div className="mt-5 flex gap-2">
                            <button type="button" onClick={() => { setForm({ ...t }); setPhoto(null); }} className="h-9 px-4 rounded-full border border-gray-300 text-sm font-bold hover:border-ink">Edit</button>
                            <button type="button" onClick={() => quickToggle(t)} className="h-9 px-4 rounded-full border border-gray-300 text-sm font-bold hover:border-ink">{String(t.status) === '1' ? 'Hide' : 'Publish'}</button>
                            <button type="button" onClick={() => remove(t)} className="ml-auto h-9 px-3 rounded-full text-sm font-bold text-red-600 hover:bg-red-50" aria-label={`Delete ${t.name}`}><i className="fa fa-trash-o" aria-hidden="true"></i></button>
                        </div>
                    </article>
                ))}
            </div>
            {items.length === 0 && <p className="text-gray-600">No testimonials yet.</p>}

            {form && (
                <div className="fixed inset-0 z-40 bg-black bg-opacity-50 flex justify-end" role="dialog" aria-modal="true" aria-label="Testimonial editor">
                    <form onSubmit={save} className="w-full max-w-lg bg-white h-full overflow-y-auto p-8 space-y-5">
                        <div className="flex justify-between items-center"><h2 className="text-2xl font-black tracking-tight text-ink">{form.id ? 'Edit testimonial' : 'New testimonial'}</h2><button type="button" onClick={() => setForm(null)} aria-label="Close"><i className="fa fa-times text-xl" aria-hidden="true"></i></button></div>
                        <label className="block"><span className="text-sm font-bold text-ink">Student name</span><input required value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} className="mt-1.5 w-full h-11 rounded-lg border-gray-300" /></label>
                        <label className="block"><span className="text-sm font-bold text-ink">Title / course</span><input value={form.title || ''} onChange={(e) => setForm({ ...form, title: e.target.value })} placeholder="Adult learner, Watercolour course" className="mt-1.5 w-full h-11 rounded-lg border-gray-300" /></label>
                        <label className="block"><span className="text-sm font-bold text-ink">Testimonial</span><textarea required rows="6" value={form.description || ''} onChange={(e) => setForm({ ...form, description: e.target.value })} className="mt-1.5 w-full rounded-lg border-gray-300" /><span className="text-xs text-gray-500">{(form.description || '').length} characters · 150–300 reads best</span></label>
                        <label className="block"><span className="text-sm font-bold text-ink">Photo</span><input type="file" accept="image/jpeg,image/png" onChange={(e) => setPhoto(e.target.files[0])} className="mt-1.5 block w-full text-sm" /></label>
                        <label className="flex items-center gap-3"><input type="checkbox" checked={String(form.status) === '1'} onChange={(e) => setForm({ ...form, status: e.target.checked ? 1 : 0 })} className="h-5 w-5 rounded text-brand" /><span className="font-bold text-ink">Publish on the website</span></label>
                        <div className="pt-4 border-t border-gray-100 flex gap-3"><button disabled={busy} className="h-12 px-6 rounded-full bg-brand text-white font-bold disabled:bg-gray-400">{busy ? 'Saving…' : 'Save'}</button><button type="button" onClick={() => setForm(null)} className="h-12 px-6 rounded-full border border-gray-300 font-bold">Cancel</button></div>
                    </form>
                </div>
            )}
            {toastNode}
        </AdminLayout>
    );
}
