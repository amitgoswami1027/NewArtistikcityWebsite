import React, { useMemo, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import axios from 'axios';
import StudioLayout from '@/Components/Studio/StudioLayout';
import { statusStyle } from '@/Components/Studio/Journey';

const strip = (h) => String(h || '').replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim();
const embed = (url) => {
    const m = String(url || '').match(/(?:youtu\.be\/|youtube\.com\/(?:watch\?v=|embed\/|shorts\/))([\w-]{6,})/);
    return m ? `https://www.youtube-nocookie.com/embed/${m[1]}?rel=0` : null;
};

export default function Classroom() {
    const { course, enrolled, modules = [], completed: initialDone = [], progress: initialProgress = 0, videos = [], submissions = [] } = usePage().props;
    const lessons = useMemo(() => modules.flatMap((m, mi) => (m.lessons || []).map((l, li) => ({ ...l, unit: mi + 1, n: li + 1, moduleTitle: m.module_title }))), [modules]);
    const [done, setDone] = useState(new Set(initialDone.map(String)));
    const [progress, setProgress] = useState(Number(initialProgress || 0));
    const firstOpen = lessons.find((l) => !done.has(String(l.id))) || lessons[0];
    const [activeId, setActiveId] = useState(firstOpen ? firstOpen.id : null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState(null);
    const active = lessons.find((l) => l.id === activeId);
    const idx = lessons.findIndex((l) => l.id === activeId);
    const video = videos[idx] || videos[0];
    const src = video ? embed(video.video_url) : null;

    if (!enrolled) {
        return (
            <StudioLayout title={course.title} active="/dashboard">
                <div className="rounded-2xl border-2 border-dashed border-gray-300 p-12 text-center">
                    <p className="text-2xl font-black text-ink">This classroom is for enrolled students</p>
                    <p className="mt-2 text-gray-600">Enroll in the course to unlock lessons, assignments and your portfolio.</p>
                    <a href={`/course/${course.slug}`} className="mt-6 inline-flex h-12 items-center px-6 rounded-full bg-brand text-white font-bold">View course</a>
                </div>
            </StudioLayout>
        );
    }

    const complete = () => {
        if (!active) return;
        setBusy(true);
        setError(null);
        axios.post(`/api/classroom/${course.id}/lessons/${active.id}/complete`).then((r) => {
            setProgress(r.data.progress);
            setDone(new Set((r.data.completed || []).map(String)));
            const nextLesson = lessons[idx + 1];
            if (nextLesson) setActiveId(nextLesson.id);
        }).catch((e) => setError((e.response && e.response.data && e.response.data.message) || 'Could not save your progress.'))
            .finally(() => setBusy(false));
    };

    const approved = submissions.some((x) => x.admin_status === 'Approved');
    const pending = submissions.some((x) => x.admin_status === 'Pending Review');
    const finished = Number(progress) >= 100;
    const stageNow = !enrolled ? 3 : approved ? (finished ? 7 : 6) : finished || pending ? 5 : 4;
    return (
        <StudioLayout title={course.title} subtitle={course.teacher_name ? `With ${course.teacher_name}` : null} active="/dashboard" stage={stageNow}
                      actions={<div className="tw"><div className="w-64"><div className="flex justify-between text-sm font-bold"><span>Course progress</span><span>{progress}%</span></div><div className="mt-1.5 h-2.5 rounded-full bg-gray-200 overflow-hidden"><div className="h-full bg-brand transition-all" style={{ width: `${progress}%` }}></div></div></div></div>}>
            <div className="grid grid-cols-12 gap-8">
                {/* ---------- left: viewport + lesson ---------- */}
                <div className="col-span-12 lg:col-span-8">
                    <div className="relative rounded-2xl overflow-hidden bg-ink" style={{ aspectRatio: '16 / 9' }}>
                        {src ? (
                            <iframe title={video.video_title || active?.lesson_title} src={src} className="absolute inset-0 w-full h-full" allow="encrypted-media; picture-in-picture" allowFullScreen></iframe>
                        ) : (
                            <div className="absolute inset-0 flex flex-col items-center justify-center text-center text-white p-8"
                                 style={{ background: course.photo ? `linear-gradient(rgba(17,17,17,.65),rgba(17,17,17,.85)), url(/storage/uploads/courses/${course.id}/${course.photo}) center/cover` : undefined }}>
                                <span className="w-20 h-20 rounded-full bg-white bg-opacity-90 text-ink flex items-center justify-center text-3xl pl-1"><i className="fa fa-play" aria-hidden="true"></i></span>
                                <p className="mt-4 font-mono text-xs tracking-widest uppercase text-gray-300">Unit {active?.unit} · Lesson {active?.n}</p>
                                <p className="mt-1 text-2xl font-black tracking-tight">{active?.lesson_title || 'Lesson'}</p>
                                <p className="mt-2 text-sm text-gray-300">The lesson recording appears here after the live session.</p>
                            </div>
                        )}
                    </div>

                    {active && (
                        <article className="mt-6">
                            <p className="font-mono text-xs tracking-widest uppercase text-gray-500">Unit {active.unit} · {active.moduleTitle}</p>
                            <h2 className="mt-1 text-3xl font-black tracking-tight text-ink">{active.lesson_title}</h2>
                            <div className="mt-4 ac-rich text-lg" dangerouslySetInnerHTML={{ __html: active.lesson_description || '<p>Follow along with the demonstration and practise the exercise.</p>' }}></div>
                            <div className="mt-6 flex flex-wrap gap-3">
                                {done.has(String(active.id)) ? (
                                    <span className="inline-flex items-center h-12 px-6 rounded-full bg-green-100 text-green-800 font-bold"><i className="fa fa-check mr-2" aria-hidden="true"></i>Lesson completed</span>
                                ) : (
                                    <button type="button" onClick={complete} disabled={busy} className="inline-flex items-center h-12 px-6 rounded-full bg-ink text-white font-bold hover:bg-gray-800 disabled:bg-gray-400">
                                        {busy ? 'Saving…' : 'Mark lesson complete'}
                                    </button>
                                )}
                                {active.lesson_pdf && <a href={`/storage/uploads/lessons/${active.lesson_pdf}`} className="inline-flex items-center h-12 px-6 rounded-full border border-gray-300 font-bold hover:border-ink"><i className="fa fa-file-pdf-o mr-2" aria-hidden="true"></i>Study material</a>}
                            </div>
                            {error && <p role="alert" className="mt-3 text-sm font-semibold text-red-600">{error}</p>}
                        </article>
                    )}

                    {/* ---------- next step (adapts to where the student is) ---------- */}
                    {approved && finished ? (
                        <section className="mt-10 rounded-2xl p-8 text-white relative overflow-hidden" style={{ background: 'linear-gradient(120deg,#111,#3b0a24 60%,#e5156b)' }}>
                            <p className="font-mono text-xs tracking-widest uppercase text-pink-200">Course complete · Step 07</p>
                            <h3 className="mt-2 text-3xl font-black tracking-tight text-white">You've earned your certificate</h3>
                            <p className="mt-2 text-lg text-gray-200 max-w-xl">Every lesson is done and your milestone is approved. Download your ArtistikCity certificate and a printable portfolio of this course.</p>
                            <div className="mt-6 flex flex-wrap gap-3">
                                <a href="/dashboard/certificates" className="inline-flex items-center h-12 px-7 rounded-full bg-brand text-white font-black hover:bg-brand-dark"><i className="fa fa-certificate mr-2" aria-hidden="true"></i>Get my certificate</a>
                                <a href="/dashboard/portfolio" className="inline-flex items-center h-12 px-7 rounded-full border border-white border-opacity-40 text-white font-bold hover:bg-white hover:text-ink">Sell from my portfolio</a>
                            </div>
                        </section>
                    ) : approved ? (
                        <section className="mt-10 rounded-2xl border border-green-200 bg-green-50 p-8">
                            <p className="font-mono text-xs tracking-widest uppercase text-green-800">Milestone approved</p>
                            <h3 className="mt-2 text-2xl font-black tracking-tight text-ink">Great work. Finish the remaining lessons to earn your certificate.</h3>
                            <p className="mt-2 text-gray-700">{lessons.length - done.size} lesson{lessons.length - done.size === 1 ? '' : 's'} to go. Your approved piece is already in your portfolio.</p>
                            <a href="/dashboard/portfolio" className="mt-5 inline-flex items-center h-11 px-6 rounded-full bg-ink text-white font-bold">Open portfolio</a>
                        </section>
                    ) : pending ? (
                        <section className="mt-10 rounded-2xl border border-yellow-200 bg-yellow-50 p-8">
                            <p className="font-mono text-xs tracking-widest uppercase text-yellow-800">Milestone · in review</p>
                            <h3 className="mt-2 text-2xl font-black tracking-tight text-ink">Your assignment is with your instructor</h3>
                            <p className="mt-2 text-gray-700">“{submissions.find((x) => x.admin_status === 'Pending Review').title}” is waiting for review. You'll see their notes in Submissions. Keep going with the lessons meanwhile.</p>
                            <a href={`/dashboard/submissions?course=${course.id}`} className="mt-5 inline-flex items-center h-11 px-6 rounded-full bg-ink text-white font-bold">View submissions</a>
                        </section>
                    ) : (
                        <section className="mt-10 rounded-2xl p-8 text-white relative overflow-hidden" style={{ background: 'linear-gradient(120deg,#e5156b,#b80f55 55%,#111)' }}>
                            <p className="font-mono text-xs tracking-widest uppercase text-pink-100">Milestone · Step 05</p>
                            <h3 className="mt-2 text-3xl font-black tracking-tight text-white">{finished ? 'All lessons done. Submit your milestone' : 'Ready to pass this module milestone?'}</h3>
                            <p className="mt-2 text-lg text-pink-50 max-w-xl">Upload your assignment for a personal review. Approved work goes straight into your portfolio, and you can sell it.</p>
                            <a href={`/dashboard/submissions?course=${course.id}`} className="mt-6 inline-flex items-center h-12 px-7 rounded-full bg-white text-ink font-black hover:bg-gray-100">Submit assignment <i className="fa fa-arrow-right ml-2" aria-hidden="true"></i></a>
                            {submissions.length > 0 && (
                                <p className="mt-4 text-sm text-pink-50">Your latest: <strong>{submissions[0].title}</strong> · {statusStyle(submissions[0].admin_status).label}</p>
                            )}
                        </section>
                    )}
                </div>

                {/* ---------- right: sticky syllabus ---------- */}
                <aside className="col-span-12 lg:col-span-4">
                    <div className="lg:sticky lg:top-24 rounded-2xl border border-gray-200 bg-white">
                        <div className="p-5 border-b border-gray-100">
                            <h2 className="text-xl font-black tracking-tight text-ink">Syllabus</h2>
                            <p className="font-mono text-xs uppercase tracking-widest text-gray-500">{done.size}/{lessons.length} lessons complete</p>
                        </div>
                        <div className="max-h-[70vh] overflow-y-auto">
                            {modules.length === 0 && <p className="p-5 text-gray-600">The syllabus will appear here when the batch starts.</p>}
                            {modules.map((m, mi) => (
                                <div key={m.id} className="border-b border-gray-100 last:border-b-0">
                                    <p className="px-5 pt-4 pb-2 font-mono text-xs uppercase tracking-widest text-gray-500">Unit {mi + 1} · {m.module_title}</p>
                                    <ol>
                                        {(m.lessons || []).map((l, li) => {
                                            const isDone = done.has(String(l.id));
                                            const isActive = l.id === activeId;
                                            return (
                                                <li key={l.id}>
                                                    <button type="button" onClick={() => setActiveId(l.id)} aria-current={isActive ? 'true' : undefined}
                                                            className={`w-full text-left flex items-start gap-3 px-5 py-3 ${isActive ? 'bg-brand-soft' : 'hover:bg-gray-50'}`}>
                                                        <span className={`mt-0.5 flex-none w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold ${isDone ? 'bg-green-600 text-white' : isActive ? 'bg-brand text-white' : 'border border-gray-300 text-gray-500'}`}>
                                                            {isDone ? <i className="fa fa-check" aria-hidden="true"></i> : li + 1}
                                                        </span>
                                                        <span className="flex-1 min-w-0">
                                                            <span className={`block font-bold ${isActive ? 'text-ink' : 'text-gray-800'}`}>{l.lesson_title}</span>
                                                            <span className="block text-sm text-gray-500 truncate">{strip(l.lesson_description).slice(0, 60)}</span>
                                                        </span>
                                                    </button>
                                                </li>
                                            );
                                        })}
                                    </ol>
                                </div>
                            ))}
                        </div>
                    </div>
                </aside>
            </div>
        </StudioLayout>
    );
}
