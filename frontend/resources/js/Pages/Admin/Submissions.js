import React, { useEffect, useRef, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import axios from 'axios';
import AdminLayout, { PageHeader, useToast, errorText } from '@/Components/Admin/AdminLayout';
import { statusStyle, shortDate } from '@/Components/Studio/Journey';

const FILTERS = ['Pending Review', 'Approved', 'Rejected', 'all'];

function ZoomFrame({ src, alt }) {
    const [zoom, setZoom] = useState(1);
    const [origin, setOrigin] = useState('50% 50%');
    const ref = useRef(null);
    const move = (e) => {
        const r = ref.current.getBoundingClientRect();
        setOrigin(`${((e.clientX - r.left) / r.width) * 100}% ${((e.clientY - r.top) / r.height) * 100}%`);
    };
    return (
        <div>
            <div ref={ref} onMouseMove={move} onMouseLeave={() => setOrigin('50% 50%')}
                 className="relative overflow-hidden rounded-xl bg-gray-900 cursor-crosshair" style={{ aspectRatio: '4 / 3' }}>
                <img src={src} alt={alt} className="absolute inset-0 w-full h-full object-contain transition-transform duration-150" style={{ transform: `scale(${zoom})`, transformOrigin: origin }} />
                <span className="absolute left-3 bottom-3 rounded-md bg-black bg-opacity-60 text-white font-mono text-xs px-2 py-1">{Math.round(zoom * 100)}%</span>
            </div>
            <div className="mt-3 flex items-center gap-3">
                <button type="button" onClick={() => setZoom((z) => Math.max(1, z - 0.5))} className="w-9 h-9 rounded-lg border border-gray-300 hover:border-ink" aria-label="Zoom out"><i className="fa fa-search-minus" aria-hidden="true"></i></button>
                <input type="range" min="1" max="4" step="0.25" value={zoom} onChange={(e) => setZoom(Number(e.target.value))} className="flex-1" style={{ accentColor: '#e5156b' }} aria-label="Zoom" />
                <button type="button" onClick={() => setZoom((z) => Math.min(4, z + 0.5))} className="w-9 h-9 rounded-lg border border-gray-300 hover:border-ink" aria-label="Zoom in"><i className="fa fa-search-plus" aria-hidden="true"></i></button>
                <a href={src} target="_blank" rel="noopener" className="h-9 px-3 inline-flex items-center rounded-lg border border-gray-300 text-sm font-bold hover:border-ink">Full size</a>
            </div>
        </div>
    );
}

export default function Submissions() {
    const { submissions: initial = [], status, counts = {} } = usePage().props;
    const [items, setItems] = useState(initial);
    const focusId = typeof window !== 'undefined' ? new URLSearchParams(window.location.search).get('focus') : null;
    const [selectedId, setSelectedId] = useState((initial.find((s) => String(s.id) === focusId) || initial[0] || {}).id);
    const [notes, setNotes] = useState('');
    const [busy, setBusy] = useState(false);
    const [toastNode, toast] = useToast();
    const selected = items.find((s) => s.id === selectedId);

    useEffect(() => { setNotes(selected && selected.reviewer_feedback ? selected.reviewer_feedback : ''); }, [selectedId]);

    const decide = (decision) => {
        if (notes.trim().length < 3) { toast('Add review notes for the student first.', 'error'); return; }
        setBusy(true);
        axios.patch(`/api/admin/submissions/${selected.id}/review`, { status: decision, notes: notes.trim() })
            .then((r) => {
                toast(decision === 'Approved' ? 'Approved and added to the student’s portfolio' : 'Sent back with your notes');
                const updated = { ...selected, ...r.data };
                const rest = items.map((s) => (s.id === selected.id ? updated : s));
                const stillVisible = status === 'all' || status === decision ? rest : rest.filter((s) => s.id !== selected.id);
                setItems(stillVisible);
                const nextPending = stillVisible.find((s) => s.admin_status === 'Pending Review' && s.id !== selected.id);
                setSelectedId(status === 'Pending Review' ? (nextPending || {}).id : updated.id);
            })
            .catch((e) => toast(errorText(e), 'error'))
            .finally(() => setBusy(false));
    };

    return (
        <AdminLayout title="Verification desk">
            <PageHeader title="Verification desk" subtitle="Review student milestone submissions. Approved work unlocks the student's portfolio and selling options." />
            <div className="flex flex-wrap gap-2 mb-6" role="tablist">
                {FILTERS.map((f) => (
                    <a key={f} href={`/admin/dashboard/submissions?status=${encodeURIComponent(f)}`} role="tab" aria-selected={status === f}
                       className={`h-10 px-4 inline-flex items-center gap-2 rounded-full text-sm font-bold border ${status === f ? 'bg-ink text-white border-ink' : 'bg-white text-gray-700 border-gray-300 hover:border-ink'}`}>
                        {f === 'all' ? 'All' : statusStyle(f).label}{f !== 'all' && <span className="font-mono opacity-70">{counts[f] || 0}</span>}
                    </a>
                ))}
            </div>

            <div className="grid grid-cols-1 xl:grid-cols-12 gap-6">
                {/* left: queue */}
                <section className="xl:col-span-4 rounded-2xl bg-white border border-gray-200 overflow-hidden self-start">
                    <p className="px-5 py-3 border-b border-gray-100 font-mono text-xs uppercase tracking-widest text-gray-500">{items.length} in this view</p>
                    {items.length === 0 ? <p className="p-6 text-gray-600"><i className="fa fa-check-circle text-green-600 mr-2" aria-hidden="true"></i>Nothing to review here.</p> : (
                        <ul className="max-h-[70vh] overflow-y-auto divide-y divide-gray-100">
                            {items.map((s) => {
                                const st = statusStyle(s.admin_status);
                                return (
                                    <li key={s.id}>
                                        <button type="button" onClick={() => setSelectedId(s.id)} aria-current={s.id === selectedId ? 'true' : undefined}
                                                className={`w-full text-left flex gap-3 p-4 ${s.id === selectedId ? 'bg-brand-soft' : 'hover:bg-gray-50'}`}>
                                            <img src={s.file_url} alt="" className="w-16 h-16 rounded-lg object-cover bg-gray-100 flex-none" />
                                            <span className="flex-1 min-w-0">
                                                <span className="block font-bold text-ink truncate">{s.title}</span>
                                                <span className="block text-sm text-gray-600 truncate">{s.student_name}</span>
                                                <span className="mt-1 flex items-center gap-2 text-xs"><span className={`rounded-full border px-2 py-0.5 font-bold ${st.chip}`}>{st.label}</span><span className="font-mono text-gray-400">{shortDate(s.created_at)}</span></span>
                                            </span>
                                        </button>
                                    </li>
                                );
                            })}
                        </ul>
                    )}
                </section>

                {/* right: workspace */}
                <section className="xl:col-span-8 rounded-2xl bg-white border border-gray-200 p-6">
                    {!selected ? <p className="text-gray-600">Select a submission to review it.</p> : (
                        <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
                            <div className="lg:col-span-3"><ZoomFrame src={selected.file_url} alt={selected.title} /></div>
                            <div className="lg:col-span-2 flex flex-col">
                                <p className="font-mono text-xs uppercase tracking-widest text-gray-500">Submission #{selected.id}</p>
                                <h2 className="mt-1 text-2xl font-black tracking-tight text-ink">{selected.title}</h2>
                                <dl className="mt-4 grid grid-cols-3 gap-y-2 text-sm">
                                    <dt className="text-gray-500">Student</dt><dd className="col-span-2 font-semibold text-ink">{selected.student_name}<br /><span className="font-normal text-gray-500">{selected.student_email}</span></dd>
                                    <dt className="text-gray-500">Course</dt><dd className="col-span-2 font-semibold text-ink">{selected.course_title}</dd>
                                    <dt className="text-gray-500">Submitted</dt><dd className="col-span-2 font-mono">{shortDate(selected.created_at)}</dd>
                                    <dt className="text-gray-500">Status</dt><dd className="col-span-2"><span className={`rounded-full border px-2.5 py-0.5 text-xs font-bold ${statusStyle(selected.admin_status).chip}`}>{statusStyle(selected.admin_status).label}</span></dd>
                                    {selected.reviewer_name && <><dt className="text-gray-500">Reviewed by</dt><dd className="col-span-2">{selected.reviewer_name}</dd></>}
                                </dl>
                                {selected.description && <blockquote className="mt-4 rounded-lg bg-gray-50 border-l-4 border-gray-300 px-3 py-2 text-sm text-gray-700">“{selected.description}”</blockquote>}

                                <label htmlFor="notes" className="mt-5 block text-sm font-bold text-ink">Review notes <span className="font-normal text-gray-500">(required, shared with the student)</span></label>
                                <textarea id="notes" rows="6" maxLength="4000" value={notes} onChange={(e) => setNotes(e.target.value)}
                                          placeholder="What works, what to improve, and the next step…" className="mt-1.5 w-full rounded-lg border-gray-300 focus:border-brand focus:ring-brand text-sm" />
                                <div className="mt-2 flex flex-wrap gap-2">
                                    {['Beautiful value control and composition.', 'Please refine the edges and resubmit.', 'Great progress, ready for your portfolio!'].map((q) => (
                                        <button key={q} type="button" onClick={() => setNotes((n) => (n ? `${n} ${q}` : q))} className="rounded-full border border-gray-200 px-3 py-1 text-xs hover:border-ink">{q}</button>
                                    ))}
                                </div>
                                <div className="mt-6 grid grid-cols-2 gap-3">
                                    <button type="button" disabled={busy} onClick={() => decide('Rejected')} className="h-12 rounded-full border-2 border-red-300 text-red-700 font-bold hover:bg-red-50 disabled:opacity-50">[Deny Upload]</button>
                                    <button type="button" disabled={busy} onClick={() => decide('Approved')} className="h-12 rounded-full bg-green-600 text-white font-bold hover:bg-green-700 disabled:opacity-50">[Verify &amp; Approve]</button>
                                </div>
                            </div>
                        </div>
                    )}
                </section>
            </div>
            {toastNode}
        </AdminLayout>
    );
}
