import React, { useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import CourseShell, { ProgressBar, lessonUrl } from '@/Components/Course/CourseShell';

/** Course overview: header, progress, one clear "Start / Resume" action and the syllabus. */
export default function Overview() {
    const { course, syllabus = [], progress, resume, projects = [], certificate } = usePage().props;
    const resumeModule = resume ? resume.moduleId : (syllabus[0] && syllabus[0].id);
    const [open, setOpen] = useState(() => new Set([String(resumeModule)]));
    const toggle = (id) => setOpen((s) => { const n = new Set(s); const k = String(id); n.has(k) ? n.delete(k) : n.add(k); return n; });
    const allDone = progress.total > 0 && progress.done === progress.total;
    const cta = !resume ? null : allDone ? 'Review the course' : resume.started ? `Resume: ${resume.title}` : 'Start learning';
    const facts = [
        course.start_date && ['fa-calendar', `Starts ${course.start_date}`],
        course.duration && ['fa-clock-o', `${course.duration} weeks${course.hours_per_week ? ` · ${course.hours_per_week} hrs/week` : ''}`],
        course.sessions && ['fa-video-camera', `${course.sessions} live sessions`],
        syllabus.length > 0 && ['fa-list-ul', `${syllabus.length} module${syllabus.length === 1 ? '' : 's'} · ${progress.total} lesson${progress.total === 1 ? '' : 's'}`],
    ].filter(Boolean);

    return (
        <CourseShell course={course} back={{ href: '/user/my-courses', label: 'My courses' }}>
            <main className="ac-wide cl-overview">
                <section className="cl-hero">
                    <div className="cl-hero__img" style={course.image ? { backgroundImage: `url("${course.image}")` } : undefined} aria-hidden="true"></div>
                    <div className="cl-hero__body">
                        <span className="ac-eyebrow">{course.kind}{course.medium ? ` · ${course.medium}` : ''}</span>
                        <h1>{course.title}</h1>
                        {course.sub_title && <p className="cl-hero__lede">{course.sub_title}</p>}
                        {course.teacher && <p className="cl-hero__teacher"><i className="fa fa-user" aria-hidden="true"></i> with {course.teacher}</p>}
                        {facts.length > 0 && (
                            <ul className="cl-facts">{facts.map(([i, t]) => <li key={t}><i className={`fa ${i}`} aria-hidden="true"></i>{t}</li>)}</ul>
                        )}
                        <div className="cl-hero__progress">
                            <div className="cl-hero__progress-row">
                                <b>{allDone ? 'Course completed' : progress.done > 0 ? 'Your progress' : 'Not started yet'}</b>
                                <span>{progress.done} of {progress.total} lessons · {progress.percent}%</span>
                            </div>
                            <ProgressBar percent={progress.percent} />
                        </div>
                        <div className="cl-hero__actions">
                            {resume ? (
                                <a href={lessonUrl(resume.moduleId, resume.lessonId)} className="dm-btn dm-btn--brand dm-btn--lg">
                                    <i className="fa fa-play" aria-hidden="true"></i> {cta}
                                </a>
                            ) : (
                                <span className="dm-muted">Lessons for this course will appear here soon.</span>
                            )}
                            {course.schedule_pdf && (
                                <a href={course.schedule_pdf} target="_blank" rel="noopener noreferrer" className="dm-btn dm-btn--line dm-btn--lg">
                                    <i className="fa fa-file-pdf-o" aria-hidden="true"></i> Class schedule
                                </a>
                            )}
                        </div>
                    </div>
                </section>

                <div className="cl-cols">
                    <section className="cl-syllabus" id="syllabus" aria-labelledby="syllabus-title">
                        <h2 id="syllabus-title">Syllabus</h2>
                        {syllabus.length === 0 && <p className="dm-muted">The syllabus will be published before the first class.</p>}
                        <ol className="cl-modules">
                            {syllabus.map((m, mi) => {
                                const isOpen = open.has(String(m.id));
                                const doneCount = m.lessons.filter((l) => l.done).length;
                                return (
                                    <li key={m.id} className={`cl-module ${isOpen ? 'is-open' : ''}`}>
                                        <button type="button" className="cl-module__head" aria-expanded={isOpen} aria-controls={`mod-${m.id}`} onClick={() => toggle(m.id)}>
                                            <span className={`cl-module__num ${m.done ? 'is-done' : ''}`} aria-hidden="true">{m.done ? <i className="fa fa-check"></i> : mi + 1}</span>
                                            <span className="cl-module__title">
                                                <b>Module {mi + 1}: {m.title}</b>
                                                <small>{m.lessons.length} lesson{m.lessons.length === 1 ? '' : 's'}{m.duration ? ` · ${m.duration}` : ''} · {doneCount}/{m.lessons.length} done</small>
                                            </span>
                                            <i className={`fa fa-angle-${isOpen ? 'up' : 'down'} cl-module__chev`} aria-hidden="true"></i>
                                        </button>
                                        {isOpen && (
                                            <ul className="cl-lessons" id={`mod-${m.id}`}>
                                                {m.lessons.length === 0 && <li className="cl-lesson cl-lesson--empty">Lessons coming soon</li>}
                                                {m.lessons.map((l) => (
                                                    <li key={l.id}>
                                                        <a href={lessonUrl(m.id, l.id)} className={`cl-lesson ${l.done ? 'is-done' : ''} ${resume && resume.lessonId === l.id ? 'is-next' : ''}`}>
                                                            <span className="cl-lesson__icon" aria-hidden="true">{l.done ? <i className="fa fa-check"></i> : l.number}</span>
                                                            <span className="cl-lesson__title">{l.title}</span>
                                                            {Number(l.tasks) > 0 && <span className="cl-chip">{l.tasks} task{Number(l.tasks) === 1 ? '' : 's'}</span>}
                                                            {resume && resume.lessonId === l.id && !allDone && <span className="cl-chip cl-chip--brand">Up next</span>}
                                                            <span className="sr-only">{l.done ? '(completed)' : ''}</span>
                                                        </a>
                                                    </li>
                                                ))}
                                            </ul>
                                        )}
                                    </li>
                                );
                            })}
                        </ol>

                        {projects.length > 0 && (
                            <div className="cl-projects">
                                <h3>Course project{projects.length > 1 ? 's' : ''}</h3>
                                {projects.map((p) => (
                                    <div key={p.id} className="cl-project">
                                        <i className="fa fa-paint-brush" aria-hidden="true"></i>
                                        <div>
                                            <b>{p.project_title}</b>
                                            {p.project_description && <p dangerouslySetInnerHTML={{ __html: String(p.project_description).replace(/<script[\s\S]*?<\/script>/gi, '') }}></p>}
                                        </div>
                                    </div>
                                ))}
                                <a href={`/dashboard/submissions?course=${course.id}`} className="dm-btn dm-btn--line">Submit your work</a>
                            </div>
                        )}
                    </section>

                    <aside className="cl-side">
                        <div className="cl-card">
                            <h3><i className="fa fa-certificate" aria-hidden="true"></i> Certificate</h3>
                            {certificate.issued ? (
                                <>
                                    <p>Your certificate for this course is ready.</p>
                                    <a href={certificate.download} className="dm-btn dm-btn--brand dm-btn--block">Download certificate</a>
                                </>
                            ) : (
                                <>
                                    <p>Complete every lesson and get your submission approved to earn your verifiable certificate.</p>
                                    <a href="/dashboard/certificates" className="dm-btn dm-btn--line dm-btn--block">Check my progress</a>
                                </>
                            )}
                        </div>
                        <div className="cl-card">
                            <h3><i className="fa fa-upload" aria-hidden="true"></i> Share your work</h3>
                            <p>Upload your assignment for personal feedback from {course.teacher || 'your instructor'}.</p>
                            <a href={`/dashboard/submissions?course=${course.id}`} className="dm-btn dm-btn--line dm-btn--block">Submit work</a>
                        </div>
                        <div className="cl-card cl-card--soft">
                            <h3><i className="fa fa-question-circle-o" aria-hidden="true"></i> Need help?</h3>
                            <p>See answers to common questions or write to the studio.</p>
                            <a href="/user/help" className="dm-linkbtn">Help &amp; support</a>
                        </div>
                    </aside>
                </div>
            </main>
        </CourseShell>
    );
}
