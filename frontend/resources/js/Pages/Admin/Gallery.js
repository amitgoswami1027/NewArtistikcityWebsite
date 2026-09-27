import React, { useRef, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import axios from 'axios';
import AdminLayout, { PageHeader, useToast, errorText } from '@/Components/Admin/AdminLayout';

export default function Gallery() {
    const { homeArtworks = [], studentArtworks = [], approvedSubmissions = [] } = usePage().props;
    const [tab, setTab] = useState('home');
    const [home, setHome] = useState(homeArtworks);
    const [student, setStudent] = useState(studentArtworks);
    const [caption, setCaption] = useState('');
    const [busy, setBusy] = useState(false);
    const [toastNode, toast] = useToast();
    const inputRef = useRef(null);

    const upload = (file) => {
        if (!file) return;
        const fd = new FormData();
        fd.append('photo', file);
        fd.append('comments', caption);
        setBusy(true);
        axios.post('/admin/console/api/gallery', fd).then((r) => { setHome([r.data, ...home]); setCaption(''); toast('Added to the home gallery'); })
            .catch((e) => toast(errorText(e), 'error')).finally(() => setBusy(false));
    };
    const promote = (s) => axios.post(`/admin/console/api/gallery/from-submission/${s.id}`)
        .then((r) => { setHome([r.data, ...home]); toast(`“${s.title}” is now featured`); setTab('home'); })
        .catch((e) => toast(errorText(e), 'error'));
    const setStatus = (kind, a, published) => axios.post(`/admin/console/api/gallery/${kind}/${a.id}/status`, { published })
        .then(() => { const fn = (list) => list.map((x) => (x.id === a.id ? { ...x, status: published ? 1 : 0 } : x)); kind === 'home' ? setHome(fn) : setStudent(fn); toast(published ? 'Published' : 'Hidden'); })
        .catch((e) => toast(errorText(e), 'error'));
    const remove = (kind, a) => {
        if (!window.confirm('Delete this artwork from the gallery?')) return;
        axios.delete(`/admin/console/api/gallery/${kind}/${a.id}`).then(() => { const fn = (list) => list.filter((x) => x.id !== a.id); kind === 'home' ? setHome(fn) : setStudent(fn); toast('Deleted'); })
            .catch((e) => toast(errorText(e), 'error'));
    };

    const Tile = ({ kind, a, src, caption: cap }) => (
        <figure className={`group rounded-xl overflow-hidden bg-white border ${String(a.status) === '1' ? 'border-gray-200' : 'border-dashed border-gray-300'}`}>
            <div className="relative bg-gray-100" style={{ aspectRatio: '1 / 1' }}>
                <img src={src} alt={cap || 'Artwork'} className={`absolute inset-0 w-full h-full object-cover ${String(a.status) === '1' ? '' : 'opacity-50 filter grayscale'}`} loading="lazy" />
                <span className={`absolute top-2 left-2 rounded-full px-2 py-0.5 text-xs font-bold ${String(a.status) === '1' ? 'bg-green-600 text-white' : 'bg-white text-gray-700'}`}>{String(a.status) === '1' ? 'Live' : 'Hidden'}</span>
            </div>
            <figcaption className="p-3">
                <p className="text-sm text-gray-700 truncate" title={cap}>{cap || '—'}</p>
                <div className="mt-2 flex gap-2">
                    <button type="button" onClick={() => setStatus(kind, a, String(a.status) !== '1')} className="h-8 px-3 rounded-full border border-gray-300 text-xs font-bold hover:border-ink">{String(a.status) === '1' ? 'Hide' : 'Publish'}</button>
                    <button type="button" onClick={() => remove(kind, a)} className="ml-auto h-8 px-3 rounded-full text-xs font-bold text-red-600 hover:bg-red-50" aria-label="Delete"><i className="fa fa-trash-o" aria-hidden="true"></i></button>
                </div>
            </figcaption>
        </figure>
    );

    return (
        <AdminLayout title="Gallery">
            <PageHeader title="Gallery" subtitle="Curate the artwork shown on the home page and student gallery. Feature approved student submissions in one click." />
            <div className="flex gap-2 mb-6 border-b border-gray-200">
                {[['home', `Home gallery (${home.length})`], ['student', `Student artworks (${student.length})`], ['feature', `Feature a submission (${approvedSubmissions.length})`]].map(([k, l]) => (
                    <button key={k} type="button" onClick={() => setTab(k)} className={`px-4 py-3 font-bold border-b-2 -mb-px ${tab === k ? 'border-ink text-ink' : 'border-transparent text-gray-500'}`}>{l}</button>
                ))}
            </div>

            {tab === 'home' && (
                <>
                    <div className="mb-6 rounded-2xl border-2 border-dashed border-gray-300 bg-white p-5 flex flex-col md:flex-row gap-4 items-center"
                         onDragOver={(e) => e.preventDefault()} onDrop={(e) => { e.preventDefault(); upload(e.dataTransfer.files[0]); }}>
                        <i className="fa fa-cloud-upload text-3xl text-brand" aria-hidden="true"></i>
                        <input value={caption} onChange={(e) => setCaption(e.target.value)} placeholder="Caption (e.g. Student landscape, watercolour)" className="flex-1 w-full h-11 rounded-lg border-gray-300" aria-label="Caption" />
                        <button type="button" disabled={busy} onClick={() => inputRef.current && inputRef.current.click()} className="h-11 px-5 rounded-full bg-ink text-white font-bold disabled:bg-gray-400">{busy ? 'Uploading…' : 'Upload image'}</button>
                        <input ref={inputRef} type="file" accept="image/jpeg,image/png" className="sr-only" onChange={(e) => upload(e.target.files[0])} aria-label="Upload gallery image" />
                    </div>
                    <div className="grid grid-cols-2 md:grid-cols-4 xl:grid-cols-6 gap-4">
                        {home.map((a) => <Tile key={a.id} kind="home" a={a} src={`/storage/uploads/home-artworks/${a.id}/${a.photo_name}`} caption={a.comments} />)}
                    </div>
                </>
            )}
            {tab === 'student' && (
                <div className="grid grid-cols-2 md:grid-cols-4 xl:grid-cols-6 gap-4">
                    {student.map((a) => <Tile key={a.id} kind="student" a={a} src={`/storage/uploads/artworks/${a.id}/${a.photo_name}`} caption={`${a.student_name || 'Student'} · ${a.comments || ''}`} />)}
                    {student.length === 0 && <p className="text-gray-600">No student artworks yet.</p>}
                </div>
            )}
            {tab === 'feature' && (
                <div className="grid grid-cols-2 md:grid-cols-4 xl:grid-cols-6 gap-4">
                    {approvedSubmissions.map((s) => (
                        <figure key={s.id} className="rounded-xl overflow-hidden bg-white border border-gray-200">
                            <div className="relative bg-gray-100" style={{ aspectRatio: '1 / 1' }}><img src={s.file_url} alt={s.title} className="absolute inset-0 w-full h-full object-cover" /></div>
                            <figcaption className="p-3"><p className="text-sm font-bold text-ink truncate">{s.title}</p><p className="text-xs text-gray-500 truncate">{s.student_name}</p>
                                <button type="button" onClick={() => promote(s)} className="mt-2 w-full h-8 rounded-full bg-brand text-white text-xs font-bold hover:bg-brand-dark">Feature on home page</button></figcaption>
                        </figure>
                    ))}
                    {approvedSubmissions.length === 0 && <p className="text-gray-600">Approved submissions appear here, ready to feature.</p>}
                </div>
            )}
            {toastNode}
        </AdminLayout>
    );
}
