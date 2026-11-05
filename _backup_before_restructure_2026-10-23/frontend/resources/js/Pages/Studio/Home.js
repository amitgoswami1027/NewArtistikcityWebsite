import React from 'react';
import { usePage } from '@inertiajs/inertia-react';
import StudioLayout from '@/Components/Studio/StudioLayout';
import { JOURNEY, statusStyle, shortDate } from '@/Components/Studio/Journey';

export default function Home() {
    const { enrollments = [], submissions = [], stage = 3, auth, certificatesReady = 0 } = usePage().props;
    const name = auth && auth.user ? auth.user.name.split(' ')[0] : 'artist';
    const next = JOURNEY[Math.min(stage, JOURNEY.length) - 1];
    const approved = submissions.filter((s) => s.admin_status === 'Approved').length;
    const pending = submissions.filter((s) => s.admin_status === 'Pending Review').length;
    const listed = submissions.filter((s) => s.is_listed_for_sale).length;

    return (
        <StudioLayout title="My studio" subtitle={`Welcome back, ${name}. Here's where you are on your creative journey.`} active="/dashboard" stage={stage}>
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                <div className="lg:col-span-2 rounded-2xl bg-ink text-white p-8 relative overflow-hidden">
                    <div className="absolute inset-0 opacity-40" style={{ background: 'radial-gradient(circle at 90% 10%, #e5156b 0, transparent 50%)' }} aria-hidden="true"></div>
                    <div className="relative">
                        <p className="font-mono text-xs tracking-widest uppercase text-pink-300">Next step · {next.n}</p>
                        <h2 className="mt-2 text-3xl font-black tracking-tight text-white">{next.title}</h2>
                        <p className="mt-2 text-lg text-gray-300 max-w-xl">{next.text}</p>
                        <a href={next.href} className="mt-6 inline-flex items-center h-12 px-6 rounded-full bg-brand text-white font-bold hover:bg-brand-dark">{next.cta} <i className="fa fa-arrow-right ml-2" aria-hidden="true"></i></a>
                    </div>
                </div>
                <div className="grid grid-cols-2 gap-4">
                    {[
                        ['Courses', enrollments.length, 'fa-graduation-cap'],
                        ['In review', pending, 'fa-clock-o'],
                        ['Approved', approved, 'fa-check-circle'],
                        ['For sale', listed, 'fa-tag'],
                    ].map(([label, value, icon]) => (
                        <div key={label} className="rounded-2xl border border-gray-200 bg-white p-5">
                            <i className={`fa ${icon} text-brand`} aria-hidden="true"></i>
                            <p className="mt-3 text-3xl font-black tracking-tight text-ink">{value}</p>
                            <p className="font-mono text-xs uppercase tracking-widest text-gray-500">{label}</p>
                        </div>
                    ))}
                </div>
            </div>

            <section className="mt-6 rounded-2xl border border-gray-200 bg-white p-5 flex flex-col md:flex-row md:items-center gap-4">
                <span className="w-12 h-12 flex-none rounded-full bg-brand-soft text-brand flex items-center justify-center text-xl"><i className="fa fa-certificate" aria-hidden="true"></i></span>
                <div className="flex-1">
                    <p className="font-extrabold text-ink">{certificatesReady > 0 ? `${certificatesReady} certificate${certificatesReady > 1 ? 's' : ''} ready to download` : 'Certificates & portfolio report'}</p>
                    <p className="text-sm text-gray-600">{certificatesReady > 0 ? 'Download your ArtistikCity certificates and a printable PDF portfolio of your work.' : 'Track what\u2019s left to earn each course certificate, and download your PDF portfolio any time.'}</p>
                </div>
                <a href="/dashboard/certificates" className="inline-flex items-center justify-center h-11 px-5 rounded-full bg-ink text-white font-bold hover:bg-gray-800">Open certificates &amp; reports</a>
            </section>

            <section className="mt-12">
                <div className="flex items-end justify-between gap-4">
                    <h2 className="text-2xl font-black tracking-tight text-ink">My classrooms</h2>
                    <a href="/courses?type=all" className="font-bold underline">Find another course</a>
                </div>
                {enrollments.length === 0 ? (
                    <div className="mt-6 rounded-2xl border-2 border-dashed border-gray-300 p-10 text-center">
                        <p className="text-xl font-extrabold text-ink">You haven't joined a course yet</p>
                        <p className="mt-2 text-gray-600">Choose a track to unlock your classroom, assignments and portfolio.</p>
                        <a href="/courses?type=all" className="mt-6 inline-flex h-12 items-center px-6 rounded-full bg-brand text-white font-bold">Choose a track</a>
                    </div>
                ) : (
                    <div className="mt-6 grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-6">
                        {enrollments.map((e) => {
                            const pct = Number(e.progress_percentage || 0);
                            return (
                                <article key={e.id} className="rounded-2xl border border-gray-200 bg-white overflow-hidden hover:shadow-lg transition">
                                    <div className="relative h-40 overflow-hidden" style={{ background: 'linear-gradient(135deg,#111,#3b0a24 60%,#e5156b)' }}>
                                        {e.photo && <img src={`/storage/uploads/courses/${e.course_id}/${e.photo}`} alt="" className="absolute inset-0 w-full h-full object-cover" onError={(ev) => { ev.currentTarget.style.display = 'none'; }} />}
                                        <span className={`absolute top-3 left-3 rounded-full px-3 py-1 text-xs font-bold ${pct >= 100 ? 'bg-green-600 text-white' : pct > 0 ? 'bg-white text-ink' : 'bg-brand text-white'}`}>{pct >= 100 ? 'Completed' : pct > 0 ? 'In progress' : 'Not started'}</span>
                                    </div>
                                    <div className="p-5">
                                        <p className="font-mono text-xs uppercase tracking-widest text-gray-500">{String(e.course_type_id) === '2' ? 'Workshop' : 'Course'}{Number(e.submissions) > 0 ? ` · ${e.submissions} submission${Number(e.submissions) === 1 ? '' : 's'}` : ''}</p>
                                        <h3 className="mt-1 text-lg font-extrabold text-ink">{e.title}</h3>
                                        <div className="mt-4" aria-label={`Progress ${pct}%`}>
                                            <div className="flex justify-between text-sm"><span className="text-gray-600">{e.completed_lessons}/{e.total_lessons} lessons</span><span className="font-bold text-ink">{pct}%</span></div>
                                            <div className="mt-1.5 h-2 rounded-full bg-gray-100 overflow-hidden"><div className="h-full bg-brand" style={{ width: `${pct}%` }}></div></div>
                                        </div>
                                        <div className="mt-5 grid grid-cols-2 gap-2">
                                            <a href={`/dashboard/classroom/${e.course_id}`} className="inline-flex justify-center items-center h-11 rounded-full bg-ink text-white text-sm font-bold hover:bg-gray-800">{pct >= 100 ? 'Revisit lessons' : pct > 0 ? 'Continue' : 'Start learning'}</a>
                                            {pct >= 100 && Number(certificatesReady) > 0
                                                ? <a href="/dashboard/certificates" className="inline-flex justify-center items-center h-11 rounded-full bg-brand text-white text-sm font-bold hover:bg-brand-dark"><i className="fa fa-certificate mr-2" aria-hidden="true"></i>Certificate</a>
                                                : <a href={`/dashboard/submissions?course=${e.course_id}`} className="inline-flex justify-center items-center h-11 rounded-full border border-gray-300 text-sm font-bold hover:border-ink"><i className="fa fa-upload mr-2" aria-hidden="true"></i>Submit work</a>}
                                        </div>
                                        <a href="/user/my-courses" className="mt-3 block text-center text-sm font-semibold text-gray-500 hover:text-ink">Schedule, modules & tasks</a>
                                    </div>
                                </article>
                            );
                        })}
                    </div>
                )}
            </section>

            {submissions.length > 0 && (
                <section className="mt-12">
                    <div className="flex items-end justify-between gap-4">
                        <h2 className="text-2xl font-black tracking-tight text-ink">Latest submissions</h2>
                        <a href="/dashboard/portfolio" className="font-bold underline">Open portfolio</a>
                    </div>
                    <ul className="mt-6 divide-y divide-gray-100 rounded-2xl border border-gray-200 bg-white">
                        {submissions.slice(0, 5).map((s) => {
                            const st = statusStyle(s.admin_status);
                            return (
                                <li key={s.id} className="flex items-center gap-4 p-4">
                                    <img src={s.file_url} alt="" className="w-14 h-14 rounded-lg object-cover bg-gray-100" />
                                    <div className="flex-1 min-w-0">
                                        <p className="font-bold text-ink truncate">{s.title}</p>
                                        <p className="text-sm text-gray-500 truncate">{s.course_title} · <span className="font-mono">{shortDate(s.created_at)}</span></p>
                                    </div>
                                    <span className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-bold ${st.chip}`}><i className={`fa ${st.icon}`} aria-hidden="true"></i>{st.label}</span>
                                </li>
                            );
                        })}
                    </ul>
                </section>
            )}
        </StudioLayout>
    );
}
