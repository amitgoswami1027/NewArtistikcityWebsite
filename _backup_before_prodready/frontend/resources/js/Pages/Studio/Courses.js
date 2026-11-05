import React, { useMemo, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import StudioLayout from '@/Components/Studio/StudioLayout';

const FILTERS = [
    { id: 'all', label: 'All' },
    { id: 'course', label: 'Courses' },
    { id: 'workshop', label: 'Workshops' },
    { id: 'active', label: 'In progress' },
    { id: 'done', label: 'Completed' },
];

const statusOf = (c) => {
    const pct = Number(c.progress || 0);
    return pct >= 100 ? 'done' : pct > 0 ? 'active' : 'new';
};

/** My studio > My courses: all enrolled courses and workshops; each opens inside My studio. */
export default function Courses() {
    const { courses = [] } = usePage().props;
    const initial = typeof window !== 'undefined' ? new URLSearchParams(window.location.search).get('type') : null;
    const [filter, setFilter] = useState(FILTERS.some((f) => f.id === initial) ? initial : 'all');
    const [query, setQuery] = useState('');

    const counts = useMemo(() => ({
        all: courses.length,
        course: courses.filter((c) => c.type === 'course').length,
        workshop: courses.filter((c) => c.type === 'workshop').length,
        active: courses.filter((c) => statusOf(c) === 'active').length,
        done: courses.filter((c) => statusOf(c) === 'done').length,
    }), [courses]);

    const shown = courses.filter((c) => {
        if (filter === 'course' || filter === 'workshop') { if (c.type !== filter) return false; }
        if (filter === 'active' && statusOf(c) !== 'active') return false;
        if (filter === 'done' && statusOf(c) !== 'done') return false;
        const q = query.trim().toLowerCase();
        return !q || [c.title, c.sub_title, c.medium].filter(Boolean).join(' ').toLowerCase().includes(q);
    });

    return (
        <StudioLayout title="My courses" subtitle="Your live courses and workshops. Open one to continue learning, see its schedule and submit work."
                      active="/user/my-courses">
            {courses.length === 0 ? (
                <div className="rounded-2xl border-2 border-dashed border-gray-300 p-12 text-center">
                    <p className="text-2xl font-black text-ink">You haven't joined a course yet</p>
                    <p className="mt-2 text-gray-600">Choose a course or workshop to unlock your lessons, assignments and portfolio.</p>
                    <a href="/courses?type=all" className="mt-6 inline-flex h-12 items-center px-6 rounded-full bg-brand text-white font-bold hover:bg-brand-dark">Choose a course</a>
                </div>
            ) : (
                <>
                    <div className="flex flex-col md:flex-row md:items-center gap-4 justify-between">
                        <div className="flex flex-wrap gap-2" role="group" aria-label="Filter courses">
                            {FILTERS.map((f) => (
                                <button key={f.id} type="button" onClick={() => setFilter(f.id)} aria-pressed={filter === f.id}
                                        className={`h-10 px-4 rounded-full text-sm font-bold border ${filter === f.id ? 'bg-ink text-white border-ink' : 'bg-white text-ink border-gray-300 hover:border-ink'}`}>
                                    {f.label} <span className={filter === f.id ? 'text-gray-300' : 'text-gray-500'}>{counts[f.id]}</span>
                                </button>
                            ))}
                        </div>
                        <label className="relative block st-search">
                            <span className="sr-only">Search my courses</span>
                            <i className="fa fa-search absolute left-4 top-1/2 -translate-y-1/2 text-gray-400" aria-hidden="true"></i>
                            <input type="search" value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search courses"
                                   className="w-full h-11 pl-10 pr-4 rounded-full border border-gray-300 focus:outline-none st-search__input" />
                        </label>
                    </div>

                    {shown.length === 0 ? (
                        <p className="mt-10 text-center text-gray-600">Nothing matches this filter. <button type="button" className="font-bold underline" onClick={() => { setFilter('all'); setQuery(''); }}>Show all</button></p>
                    ) : (
                        <div className="mt-8 grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-6">
                            {shown.map((c) => {
                                const pct = Number(c.progress || 0);
                                const st = statusOf(c);
                                return (
                                    <article key={c.course_id} className="rounded-2xl border border-gray-200 bg-white overflow-hidden hover:shadow-lg transition flex flex-col">
                                        <a href={`/user/course/${c.order_id}`} className="relative block h-40 overflow-hidden" style={{ background: 'linear-gradient(135deg,#111,#3b0a24 60%,#e5156b)' }} tabIndex={-1} aria-hidden="true">
                                            {c.photo && <img src={`/storage/uploads/courses/${c.course_id}/${c.photo}`} alt="" className="absolute inset-0 w-full h-full object-cover" onError={(ev) => { ev.currentTarget.style.display = 'none'; }} />}
                                            <span className={`absolute top-3 left-3 rounded-full px-3 py-1 text-xs font-bold ${st === 'done' ? 'bg-green-600 text-white' : st === 'active' ? 'bg-white text-ink' : 'bg-brand text-white'}`}>
                                                {st === 'done' ? 'Completed' : st === 'active' ? 'In progress' : 'Not started'}
                                            </span>
                                        </a>
                                        <div className="p-5 flex-1 flex flex-col">
                                            <p className="font-mono text-xs uppercase tracking-widest text-gray-500">
                                                {c.type === 'workshop' ? 'Workshop' : 'Course'}{c.medium ? ` · ${c.medium}` : ''}{c.duration ? ` · ${c.duration} weeks` : ''}
                                            </p>
                                            <h3 className="mt-1 text-lg font-extrabold text-ink"><a href={`/user/course/${c.order_id}`} className="st-link">{c.title}</a></h3>
                                            {c.sub_title && <p className="mt-1 text-sm text-gray-600 st-clamp2">{c.sub_title}</p>}
                                            <div className="mt-4" aria-label={`Progress ${pct}%`}>
                                                <div className="flex justify-between text-sm"><span className="text-gray-600">{c.completed_lessons}/{c.total_lessons} lessons</span><span className="font-bold text-ink">{pct}%</span></div>
                                                <div className="mt-1.5 h-2 rounded-full bg-gray-100 overflow-hidden"><div className="h-full bg-brand" style={{ width: `${pct}%` }}></div></div>
                                            </div>
                                            <div className="mt-5 grid grid-cols-2 gap-2 mt-auto pt-5">
                                                <a href={`/user/course/${c.order_id}/resume`} className="inline-flex justify-center items-center h-11 rounded-full bg-ink text-white text-sm font-bold hover:bg-gray-800">
                                                    {st === 'done' ? 'Revisit lessons' : st === 'active' ? 'Continue' : 'Start learning'}
                                                </a>
                                                <a href={`/user/course/${c.order_id}`} className="inline-flex justify-center items-center h-11 rounded-full border border-gray-300 text-sm font-bold hover:border-ink">
                                                    <i className="fa fa-list-ul mr-2" aria-hidden="true"></i>Course overview
                                                </a>
                                            </div>
                                            {c.schedule_pdf && (
                                                <a href={`/storage/uploads/courses/schedule/${c.course_id}/${c.schedule_pdf}`} target="_blank" rel="noopener noreferrer"
                                                   className="mt-3 text-center text-sm font-semibold text-gray-500 hover:text-ink"><i className="fa fa-file-pdf-o mr-1" aria-hidden="true"></i>Download schedule (PDF)</a>
                                            )}
                                        </div>
                                    </article>
                                );
                            })}
                        </div>
                    )}
                </>
            )}
        </StudioLayout>
    );
}
