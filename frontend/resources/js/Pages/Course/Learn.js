import React, { useEffect, useMemo, useState } from 'react';
import { Link, usePage } from '@inertiajs/inertia-react';
import axios from 'axios';
import CourseShell, { ProgressBar, lessonUrl } from '@/Components/Course/CourseShell';
import VideoPlayer, { videoSource } from '@/Components/Video/VideoPlayer';

const SIDEBAR_KEY = 'ac.cl.sidebar';
const readPref = () => { try { return window.localStorage.getItem(SIDEBAR_KEY) !== 'closed'; } catch (e) { return true; } };
const writePref = (open) => { try { window.localStorage.setItem(SIDEBAR_KEY, open ? 'open' : 'closed'); } catch (e) { /* private mode */ } };


/** Sidebar: every module with its lessons; the current lesson is highlighted. */
function Syllabus({ syllabus, current, done, onPick }) {
    const [open, setOpen] = useState(() => new Set(syllabus.map((m) => String(m.id))));
    const toggle = (id) => setOpen((s) => { const n = new Set(s); n.has(id) ? n.delete(id) : n.add(id); return n; });
    return (
        <nav className="cl-nav" aria-label="Course lessons">
            {syllabus.map((m, mi) => {
                const id = String(m.id);
                const isOpen = open.has(id);
                const count = m.lessons.filter((l) => done.has(String(l.id))).length;
                return (
                    <div key={id} className="cl-nav__module">
                        <button type="button" className="cl-nav__head" aria-expanded={isOpen} onClick={() => toggle(id)}>
                            <span><small>Module {mi + 1}</small><b>{m.title}</b></span>
                            <span className="cl-nav__count">{count}/{m.lessons.length}</span>
                            <i className={`fa fa-angle-${isOpen ? 'up' : 'down'}`} aria-hidden="true"></i>
                        </button>
                        {isOpen && (
                            <ul>
                                {m.lessons.length === 0 && <li className="cl-nav__empty">Lessons coming soon</li>}
                                {m.lessons.map((l) => {
                                    const isDone = done.has(String(l.id));
                                    const isCur = current && String(current.id) === String(l.id);
                                    return (
                                        <li key={l.id}>
                                            <Link href={lessonUrl(m.id, l.id)} onClick={onPick} aria-current={isCur ? 'page' : undefined}
                                                className={`cl-nav__lesson ${isCur ? 'is-current' : ''} ${isDone ? 'is-done' : ''}`}>
                                                <span className="cl-nav__dot" aria-hidden="true">{isDone ? <i className="fa fa-check"></i> : l.number}</span>
                                                <span className="cl-nav__title">{l.title}</span>
                                                {isDone && <span className="sr-only">(completed)</span>}
                                            </Link>
                                        </li>
                                    );
                                })}
                            </ul>
                        )}
                    </div>
                );
            })}
        </nav>
    );
}

/** Learning view: collapsible syllabus sidebar + a wide content area with prev / complete / next. */
export default function Learn() {
    const { course, syllabus = [], progress: initialProgress, module, lesson, prev, next } = usePage().props;
    const initialDone = useMemo(() => new Set(syllabus.flatMap((m) => m.lessons.filter((l) => l.done).map((l) => String(l.id)))), [syllabus]);
    const [done, setDone] = useState(initialDone);
    const [progress, setProgress] = useState(initialProgress);
    const [sidebar, setSidebar] = useState(readPref);       // desktop: docked or collapsed
    const [drawer, setDrawer] = useState(false);        // phone: slide-over
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState(null);

    useEffect(() => {
        if (!drawer) return undefined;
        const onKey = (e) => { if (e.key === 'Escape') setDrawer(false); };
        document.addEventListener('keydown', onKey);
        return () => document.removeEventListener('keydown', onKey);
    }, [drawer]);

    const isDone = lesson && done.has(String(lesson.id));
    const toggleSidebar = () => {
        if (window.matchMedia && window.matchMedia('(max-width: 960px)').matches) { setDrawer((d) => !d); return; }
        setSidebar((s) => { writePref(!s); return !s; });
    };

    const complete = () => {
        if (!lesson || busy) return;
        setBusy(true);
        setError(null);
        axios.post(`/api/classroom/${course.id}/lessons/${lesson.id}/complete`).then((r) => {
            const ids = (r.data.completed || []).map(String);
            const set = new Set(ids);
            setDone(set);
            const total = initialProgress.total;
            const count = syllabus.reduce((n, m) => n + m.lessons.filter((l) => set.has(String(l.id))).length, 0);
            setProgress({ done: count, total, percent: total ? Math.round((count * 100) / total) : 0 });
        }).catch((e) => setError((e.response && e.response.data && e.response.data.message) || 'Could not save your progress. Please try again.'))
            .finally(() => setBusy(false));
    };

    const vsrc = lesson && lesson.video ? videoSource(lesson.video.url) : null;
    const src = vsrc && vsrc.kind !== 'link' ? vsrc.src : null; // this lesson's own video
    const toggleLabel = drawer || sidebar ? 'Hide lessons' : 'Show lessons';

    return (
        <CourseShell
            title={lesson ? `${lesson.title} · ${course.title}` : course.title}
            course={course}
            progress={progress}
            footer={false}
            back={{ href: `/user/course/${course.orderId}`, label: 'Course overview' }}
            actions={(
                <button type="button" className="cl-bar__toggle" onClick={toggleSidebar} aria-controls="cl-sidebar" aria-expanded={drawer || sidebar}>
                    <i className="fa fa-list-ul" aria-hidden="true"></i><span>{toggleLabel}</span>
                </button>
            )}
        >
            <div className={`cl-learn ${sidebar ? '' : 'is-collapsed'} ${drawer ? 'is-drawer' : ''}`}>
                <div className="cl-scrim" onClick={() => setDrawer(false)} aria-hidden="true"></div>
                <aside id="cl-sidebar" className="cl-sidebar" aria-label="Lessons">
                    <div className="cl-sidebar__head">
                        <b>Course content</b>
                        <span>{progress.done}/{progress.total} done</span>
                        <button type="button" className="cl-sidebar__close" onClick={() => setDrawer(false)} aria-label="Close lessons"><i className="fa fa-times" aria-hidden="true"></i></button>
                    </div>
                    <div className="cl-sidebar__progress"><ProgressBar percent={progress.percent} /></div>
                    <Syllabus syllabus={syllabus} current={lesson} done={done} onPick={() => setDrawer(false)} />
                </aside>

                <main className="cl-main" id="lesson">
                    {!lesson ? (
                        <div className="cl-empty">
                            <i className="fa fa-hourglass-half" aria-hidden="true"></i>
                            <h1>{module.title}</h1>
                            <p>The lessons for this module haven't been published yet. Check back soon.</p>
                            <a href={`/user/course/${course.orderId}`} className="dm-btn dm-btn--line">Back to the course overview</a>
                        </div>
                    ) : (
                        <article className="cl-lessonview">
                            <span className="ac-eyebrow">{module.title} · Lesson {lesson.number} of {lesson.total}</span>
                            <h1>{lesson.title}</h1>

                            <div className={`cl-media ${src ? '' : 'cl-media--ph'}`}>
                                {src ? (
                                    <VideoPlayer key={lesson.id} url={lesson.video.url} title={lesson.video.title || lesson.title} />
                                ) : lesson.video && lesson.video.url ? (
                                    <a className="cl-media__ph" href={lesson.video.url} target="_blank" rel="noopener noreferrer">
                                        <i className="fa fa-play-circle" aria-hidden="true"></i><span>Watch “{lesson.video.title}”</span>
                                    </a>
                                ) : (
                                    <div className="cl-media__ph">
                                        <i className="fa fa-video-camera" aria-hidden="true"></i>
                                        <span>This lesson is taught live. The recording appears here after the class.</span>
                                    </div>
                                )}
                            </div>

                            {lesson.description
                                ? <div className="cl-prose" dangerouslySetInnerHTML={{ __html: lesson.description }}></div>
                                : <p className="dm-muted">Notes for this lesson will be added by your instructor.</p>}

                            {(lesson.pdf || lesson.tasks.length > 0) && (
                                <div className="cl-resources">
                                    {lesson.pdf && (
                                        <a href={lesson.pdf} target="_blank" rel="noopener noreferrer" className="cl-resource">
                                            <i className="fa fa-file-pdf-o" aria-hidden="true"></i>
                                            <span><b>Lesson notes</b><small>PDF download</small></span>
                                        </a>
                                    )}
                                    {lesson.tasks.map((t) => (
                                        <a key={t.id} href={`/user/course/module/lesson/task/${t.id}`} className="cl-resource">
                                            <i className="fa fa-pencil-square-o" aria-hidden="true"></i>
                                            <span><b>{t.title}</b><small>Practice task · submit your answer</small></span>
                                        </a>
                                    ))}
                                </div>
                            )}

                            {error && <p className="cl-error" role="alert">{error}</p>}

                            <div className="cl-pager">
                                {prev ? (
                                    <Link href={lessonUrl(prev.moduleId, prev.lessonId)} className="cl-pager__link">
                                        <i className="fa fa-arrow-left" aria-hidden="true"></i>
                                        <span><small>Previous</small><b>{prev.title}</b></span>
                                    </Link>
                                ) : <span></span>}
                                {isDone ? (
                                    <span className="cl-done"><i className="fa fa-check" aria-hidden="true"></i> Completed</span>
                                ) : (
                                    <button type="button" className="dm-btn dm-btn--ink" onClick={complete} disabled={busy}>
                                        {busy ? 'Saving…' : 'Mark as complete'}
                                    </button>
                                )}
                                {next ? (
                                    <Link href={lessonUrl(next.moduleId, next.lessonId)} className="cl-pager__link cl-pager__link--next">
                                        <span><small>Next</small><b>{next.title}</b></span>
                                        <i className="fa fa-arrow-right" aria-hidden="true"></i>
                                    </Link>
                                ) : (
                                    <a href={`/user/course/${course.orderId}`} className="cl-pager__link cl-pager__link--next">
                                        <span><small>Finished?</small><b>Back to overview</b></span>
                                        <i className="fa fa-flag-checkered" aria-hidden="true"></i>
                                    </a>
                                )}
                            </div>
                        </article>
                    )}
                </main>
            </div>
        </CourseShell>
    );
}
