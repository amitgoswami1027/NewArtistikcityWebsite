import React, { useRef, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import axios from 'axios';
import StudioLayout from '@/Components/Studio/StudioLayout';
import { statusStyle, shortDate } from '@/Components/Studio/Journey';

export default function Submissions() {
    const { enrollments = [], submissions: initial = [], selectedCourse } = usePage().props;
    const [items, setItems] = useState(initial);
    const [courseId, setCourseId] = useState(selectedCourse || (enrollments[0] ? String(enrollments[0].course_id) : ''));
    const [title, setTitle] = useState('');
    const [description, setDescription] = useState('');
    const [file, setFile] = useState(null);
    const [preview, setPreview] = useState(null);
    const [progress, setProgress] = useState(null);
    const [message, setMessage] = useState(null);
    const inputRef = useRef(null);

    const pick = (f) => {
        setMessage(null);
        if (!f) return;
        if (!/^image\/(jpeg|png)$/.test(f.type)) { setMessage({ type: 'error', text: 'Please choose a JPG or PNG image.' }); return; }
        if (f.size > 15 * 1024 * 1024) { setMessage({ type: 'error', text: 'That image is larger than 15 MB.' }); return; }
        setFile(f);
        setPreview(URL.createObjectURL(f));
    };

    const submit = (e) => {
        e.preventDefault();
        setMessage(null);
        if (!courseId) return setMessage({ type: 'error', text: 'Choose the course this assignment is for.' });
        if (title.trim().length < 2) return setMessage({ type: 'error', text: 'Give your artwork a title.' });
        if (!file) return setMessage({ type: 'error', text: 'Attach a photo of your artwork.' });
        const form = new FormData();
        form.append('courseId', courseId);
        form.append('title', title.trim());
        form.append('description', description.trim());
        form.append('file', file);
        setProgress(0);
        axios.post('/api/submissions/upload', form, { onUploadProgress: (ev) => ev.total && setProgress(Math.round((ev.loaded / ev.total) * 100)) })
            .then((r) => {
                const course = enrollments.find((x) => String(x.course_id) === String(courseId));
                setItems([{ id: r.data.id, title: title.trim(), description, file_url: r.data.fileUrl, admin_status: 'Pending Review', course_title: course ? course.title : '', created_at: new Date().toISOString() }, ...items]);
                setTitle(''); setDescription(''); setFile(null); setPreview(null);
                setMessage({ type: 'ok', text: 'Submitted! Your reviewer will get back to you with feedback soon.' });
            })
            .catch((err) => setMessage({ type: 'error', text: (err.response && err.response.data && err.response.data.message) || 'Upload failed. Please try again.' }))
            .finally(() => setProgress(null));
    };

    return (
        <StudioLayout title="Submissions" subtitle="Upload your assignment milestones for review. Approved pieces go straight into your portfolio." active="/dashboard/submissions" stage={5}>
            <div className="grid grid-cols-1 lg:grid-cols-5 gap-8">
                <form onSubmit={submit} className="lg:col-span-2 rounded-2xl border border-gray-200 bg-white p-6 space-y-5 self-start" noValidate>
                    <h2 className="text-2xl font-black tracking-tight text-ink">New submission</h2>
                    {enrollments.length === 0 ? (
                        <p className="rounded-xl bg-yellow-50 border border-yellow-200 p-4 text-yellow-900">Enroll in a course to submit assignments. <a href="/courses?type=all" className="font-bold underline">Browse courses</a></p>
                    ) : (
                        <>
                            <div>
                                <label htmlFor="course" className="block text-sm font-bold text-ink">Course</label>
                                <select id="course" value={courseId} onChange={(e) => setCourseId(e.target.value)} className="mt-1.5 w-full h-11 rounded-lg border-gray-300 focus:border-brand focus:ring-brand">
                                    {enrollments.map((e) => <option key={e.course_id} value={e.course_id}>{e.title}</option>)}
                                </select>
                            </div>
                            <div>
                                <label htmlFor="title" className="block text-sm font-bold text-ink">Artwork title</label>
                                <input id="title" value={title} maxLength="200" onChange={(e) => setTitle(e.target.value)} placeholder="e.g. Monsoon over the Western Ghats" className="mt-1.5 w-full h-11 rounded-lg border-gray-300 focus:border-brand focus:ring-brand" />
                            </div>
                            <div>
                                <label htmlFor="desc" className="block text-sm font-bold text-ink">Notes for your reviewer <span className="font-normal text-gray-500">(optional)</span></label>
                                <textarea id="desc" rows="4" maxLength="4000" value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Materials used, what you tried, where you'd like feedback…" className="mt-1.5 w-full rounded-lg border-gray-300 focus:border-brand focus:ring-brand" />
                            </div>
                            <div>
                                <span className="block text-sm font-bold text-ink">Artwork photo</span>
                                <button type="button" onClick={() => inputRef.current && inputRef.current.click()}
                                        onDragOver={(e) => e.preventDefault()} onDrop={(e) => { e.preventDefault(); pick(e.dataTransfer.files[0]); }}
                                        className="mt-1.5 w-full rounded-xl border-2 border-dashed border-gray-300 hover:border-brand bg-gray-50 p-4 text-center">
                                    {preview ? <img src={preview} alt="Selected artwork" className="mx-auto max-h-56 rounded-lg" /> : (
                                        <span className="block py-6 text-gray-600"><i className="fa fa-cloud-upload text-3xl text-brand" aria-hidden="true"></i><span className="block mt-2 font-bold text-ink">Drop a photo or click to browse</span><span className="block text-sm">JPG or PNG, up to 15 MB</span></span>
                                    )}
                                </button>
                                <input ref={inputRef} type="file" accept="image/jpeg,image/png" className="sr-only" aria-label="Artwork photo" onChange={(e) => pick(e.target.files[0])} />
                            </div>
                            {progress !== null && <div className="h-2 rounded-full bg-gray-200 overflow-hidden" role="progressbar" aria-valuenow={progress}><div className="h-full bg-brand" style={{ width: `${progress}%` }}></div></div>}
                            {message && <p role="alert" className={`rounded-lg px-4 py-3 text-sm font-semibold ${message.type === 'ok' ? 'bg-green-50 border border-green-200 text-green-800' : 'bg-red-50 border border-red-200 text-red-700'}`}>{message.text}</p>}
                            <button type="submit" disabled={progress !== null} className="w-full h-12 rounded-full bg-brand text-white font-bold hover:bg-brand-dark disabled:bg-gray-400">Submit for review</button>
                        </>
                    )}
                </form>

                <section className="lg:col-span-3">
                    <h2 className="text-2xl font-black tracking-tight text-ink">Review history</h2>
                    {items.length === 0 ? <p className="mt-4 text-gray-600">No submissions yet. Your first milestone will appear here.</p> : (
                        <ul className="mt-4 space-y-4">
                            {items.map((s) => {
                                const st = statusStyle(s.admin_status);
                                return (
                                    <li key={s.id} className="flex gap-4 rounded-2xl border border-gray-200 bg-white p-4">
                                        <img src={s.file_url} alt={s.title} className="w-28 h-28 rounded-xl object-cover bg-gray-100 flex-none" />
                                        <div className="flex-1 min-w-0">
                                            <div className="flex flex-wrap items-center justify-between gap-2">
                                                <p className="text-lg font-extrabold text-ink">{s.title}</p>
                                                <span className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-bold ${st.chip}`}><i className={`fa ${st.icon}`} aria-hidden="true"></i>{st.label}</span>
                                            </div>
                                            <p className="text-sm text-gray-500">{s.course_title} · <span className="font-mono">{shortDate(s.created_at)}</span></p>
                                            {s.reviewer_feedback && (
                                                <blockquote className={`mt-3 rounded-lg border-l-4 px-3 py-2 text-sm ${s.admin_status === 'Rejected' ? 'border-red-400 bg-red-50 text-red-900' : 'border-green-500 bg-green-50 text-green-900'}`}>
                                                    <span className="font-bold">{s.reviewer_name || 'Reviewer'}:</span> {s.reviewer_feedback}
                                                </blockquote>
                                            )}
                                        </div>
                                    </li>
                                );
                            })}
                        </ul>
                    )}
                </section>
            </div>
        </StudioLayout>
    );
}
