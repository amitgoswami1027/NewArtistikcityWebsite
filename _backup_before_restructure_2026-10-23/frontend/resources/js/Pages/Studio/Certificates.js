import React, { useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import StudioLayout from '@/Components/Studio/StudioLayout';
import { shortDate } from '@/Components/Studio/Journey';

function Requirement({ ok, label, detail, action }) {
    return (
        <li className="flex items-start gap-3 py-2">
            <span className={`mt-0.5 flex-none w-6 h-6 rounded-full flex items-center justify-center text-xs ${ok ? 'bg-green-600 text-white' : 'border-2 border-gray-300 text-gray-400'}`}>
                {ok ? <i className="fa fa-check" aria-hidden="true"></i> : null}
            </span>
            <span className="flex-1">
                <span className={`block font-bold ${ok ? 'text-ink' : 'text-gray-800'}`}>{label}</span>
                {detail && <span className="block text-sm text-gray-500">{detail}</span>}
            </span>
            {!ok && action}
        </li>
    );
}

function CertificateCard({ c, verifyBase }) {
    const total = Number(c.total_lessons || 0);
    const done = Number(c.completed_lessons || 0);
    const pct = total === 0 ? 100 : Math.round((done / total) * 100);
    const cert = c.certificate;
    const earned = c.eligible || cert;
    const download = `/dashboard/certificates/${c.course_id}/download`;
    const issued = cert ? new Date(String(cert.issued_at).replace(' ', 'T')) : new Date();
    const linkedIn = cert ? 'https://www.linkedin.com/profile/add?startTask=CERTIFICATION_NAME'
        + `&name=${encodeURIComponent(c.title)}&organizationName=ArtistikCity&issueYear=${issued.getFullYear()}&issueMonth=${issued.getMonth() + 1}`
        + `&certUrl=${encodeURIComponent(verifyBase + cert.certificate_no)}&certId=${encodeURIComponent(cert.certificate_no)}` : null;

    return (
        <article className={`rounded-2xl border bg-white overflow-hidden ${earned ? 'border-ink' : 'border-gray-200'}`}>
            <div className="flex flex-col sm:flex-row">
                <div className="sm:w-48 h-36 sm:h-auto bg-gray-100 bg-cover bg-center flex-none" style={{ backgroundImage: c.photo ? `url(/storage/uploads/courses/${c.course_id}/${c.photo})` : undefined }}></div>
                <div className="flex-1 p-6">
                    <div className="flex flex-wrap items-start justify-between gap-3">
                        <div>
                            <p className="font-mono text-xs uppercase tracking-widest text-gray-500">{String(c.course_type_id) === '2' ? 'Workshop' : 'Course'}</p>
                            <h3 className="text-xl font-black tracking-tight text-ink">{c.title}</h3>
                        </div>
                        {cert ? <span className="rounded-full bg-ink text-white px-3 py-1 text-xs font-bold"><i className="fa fa-certificate mr-1" aria-hidden="true"></i>Certified</span>
                            : earned ? <span className="rounded-full bg-green-100 text-green-800 px-3 py-1 text-xs font-bold">Ready to claim</span>
                                : <span className="rounded-full bg-gray-100 text-gray-700 px-3 py-1 text-xs font-bold">In progress</span>}
                    </div>

                    <ul className="mt-3 divide-y divide-gray-100" aria-label="Certificate requirements">
                        <Requirement ok={c.lessons_ok} label={total === 0 ? 'Lessons' : `Complete all lessons (${done}/${total})`}
                                     detail={total === 0 ? 'No lessons published for this course' : null}
                                     action={<a href={`/dashboard/classroom/${c.course_id}`} className="text-sm font-bold underline whitespace-nowrap">Continue</a>} />
                        <Requirement ok={c.milestone_ok} label="Get one assignment approved"
                                     detail={c.milestone_ok ? `${c.approved_works} approved` : Number(c.pending_works) > 0 ? `${c.pending_works} waiting for review` : 'No approved work yet'}
                                     action={<a href={`/dashboard/submissions?course=${c.course_id}`} className="text-sm font-bold underline whitespace-nowrap">Submit</a>} />
                    </ul>
                    {total > 0 && !c.lessons_ok && (
                        <div className="mt-2 h-1.5 rounded-full bg-gray-100 overflow-hidden" aria-hidden="true"><div className="h-full bg-brand" style={{ width: `${pct}%` }}></div></div>
                    )}

                    {earned ? (
                        <div className="mt-5 flex flex-wrap items-center gap-2">
                            <a href={download} className="inline-flex items-center h-11 px-5 rounded-full bg-brand text-white font-bold hover:bg-brand-dark"><i className="fa fa-download mr-2" aria-hidden="true"></i>{cert ? 'Download certificate' : 'Claim & download certificate'}</a>
                            <a href={`${download}?inline=1`} target="_blank" rel="noopener" className="inline-flex items-center h-11 px-5 rounded-full border border-gray-300 font-bold hover:border-ink"><i className="fa fa-print mr-2" aria-hidden="true"></i>View &amp; print</a>
                            {cert && <a href={`/certificates/verify/${cert.certificate_no}`} target="_blank" rel="noopener" className="inline-flex items-center h-11 px-4 rounded-full font-bold text-gray-700 hover:text-ink"><i className="fa fa-shield mr-2" aria-hidden="true"></i>Verify</a>}
                            {linkedIn && <a href={linkedIn} target="_blank" rel="noopener" className="inline-flex items-center h-11 px-4 rounded-full font-bold text-gray-700 hover:text-ink"><i className="fa fa-linkedin-square mr-2" aria-hidden="true"></i>Add to LinkedIn</a>}
                            {cert && <p className="w-full mt-1 font-mono text-xs text-gray-500">No. {cert.certificate_no} · issued {shortDate(cert.issued_at)}</p>}
                        </div>
                    ) : (
                        <p className="mt-4 text-sm text-gray-500"><i className="fa fa-lock mr-2" aria-hidden="true"></i>Your certificate unlocks when both steps are done.</p>
                    )}
                </div>
            </div>
        </article>
    );
}

function PortfolioBuilder({ credentials, stats }) {
    const [approvedOnly, setApprovedOnly] = useState(true);
    const [curriculum, setCurriculum] = useState(true);
    const [comments, setComments] = useState(true);
    const [courses, setCourses] = useState(credentials.map((c) => String(c.course_id)));
    const toggleCourse = (id) => setCourses((list) => (list.includes(id) ? list.filter((x) => x !== id) : [...list, id]));
    const qs = new URLSearchParams({ approvedOnly: approvedOnly ? '1' : '0', curriculum: curriculum ? '1' : '0', comments: comments ? '1' : '0', courses: courses.join(',') }).toString();
    const href = `/dashboard/portfolio/report?${qs}`;
    const none = credentials.length > 0 && courses.length === 0;

    const Toggle = ({ checked, onChange, label, hint }) => (
        <label className="flex items-start gap-3 py-2 cursor-pointer">
            <input type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} className="mt-1 h-5 w-5 rounded text-brand border-gray-300 focus:ring-brand" />
            <span><span className="block font-bold text-ink">{label}</span>{hint && <span className="block text-sm text-gray-500">{hint}</span>}</span>
        </label>
    );

    return (
        <section className="rounded-2xl border border-gray-200 bg-white overflow-hidden">
            <div className="grid grid-cols-1 lg:grid-cols-5">
                <div className="lg:col-span-2 bg-ink text-white p-8 relative overflow-hidden">
                    <div className="absolute inset-0 opacity-40" style={{ background: 'radial-gradient(circle at 90% 10%, #e5156b 0, transparent 55%)' }} aria-hidden="true"></div>
                    <div className="relative">
                        <p className="font-mono text-xs tracking-widest uppercase text-pink-300">PDF report</p>
                        <h2 className="mt-2 text-3xl font-black tracking-tight text-white">Portfolio report</h2>
                        <p className="mt-2 text-gray-300">A detailed, print-ready portfolio: cover page, every course with its curriculum and your progress, each artwork with instructor comments, and your certificate numbers.</p>
                        <div className="mt-6 grid grid-cols-3 gap-3">
                            {[[credentials.length, 'Courses'], [stats.lessons, 'Lessons'], [approvedOnly ? stats.approved : stats.total, 'Artworks']].map(([v, l]) => (
                                <div key={l} className="border-t border-gray-600 pt-2"><p className="text-2xl font-black">{v}</p><p className="font-mono text-xs uppercase tracking-widest text-gray-400">{l}</p></div>
                            ))}
                        </div>
                    </div>
                </div>
                <div className="lg:col-span-3 p-8">
                    <h3 className="text-lg font-black tracking-tight text-ink">What to include</h3>
                    <div className="mt-2 grid grid-cols-1 md:grid-cols-2 gap-x-6">
                        <Toggle checked={curriculum} onChange={setCurriculum} label="Course curriculum" hint="Units and lessons, with the ones you completed ticked" />
                        <Toggle checked={comments} onChange={setComments} label="Instructor comments" hint="Reviewer notes printed under each artwork" />
                        <Toggle checked={approvedOnly} onChange={setApprovedOnly} label="Approved artworks only" hint="Untick to include work in review or needing changes" />
                    </div>
                    {credentials.length > 1 && (
                        <fieldset className="mt-4">
                            <legend className="font-bold text-ink">Courses</legend>
                            <div className="mt-2 flex flex-wrap gap-2">
                                {credentials.map((c) => {
                                    const on = courses.includes(String(c.course_id));
                                    return <button key={c.course_id} type="button" aria-pressed={on} onClick={() => toggleCourse(String(c.course_id))}
                                                   className={`h-9 px-4 rounded-full text-sm font-bold border ${on ? 'bg-ink text-white border-ink' : 'bg-white text-gray-600 border-gray-300'}`}>{on && <i className="fa fa-check mr-1" aria-hidden="true"></i>}{c.title}</button>;
                                })}
                            </div>
                        </fieldset>
                    )}
                    <div className="mt-6 flex flex-wrap gap-3">
                        <a href={none ? undefined : href} aria-disabled={none} className={`inline-flex items-center h-12 px-6 rounded-full font-bold ${none ? 'bg-gray-300 text-white pointer-events-none' : 'bg-brand text-white hover:bg-brand-dark'}`}>
                            <i className="fa fa-file-pdf-o mr-2" aria-hidden="true"></i>Download portfolio PDF
                        </a>
                        <a href={none ? undefined : `${href}&inline=1`} target="_blank" rel="noopener" className={`inline-flex items-center h-12 px-6 rounded-full border font-bold ${none ? 'border-gray-200 text-gray-300 pointer-events-none' : 'border-gray-300 hover:border-ink'}`}>
                            <i className="fa fa-print mr-2" aria-hidden="true"></i>Open to print
                        </a>
                    </div>
                    <p className="mt-3 text-xs text-gray-500">Generated fresh each time, so it always reflects your latest reviews and certificates.</p>
                </div>
            </div>
        </section>
    );
}

export default function Certificates() {
    const { credentials = [], stats = {}, stage = 4, verifyBase = '' } = usePage().props;
    const earned = credentials.filter((c) => c.eligible || c.certificate).length;
    return (
        <StudioLayout title="Certificates & reports" subtitle="Earn a verifiable certificate for each course and download a printable portfolio of your work." active="/dashboard/certificates" stage={stage}>
            <PortfolioBuilder credentials={credentials} stats={stats} />

            <section className="mt-12">
                <div className="flex flex-wrap items-end justify-between gap-4">
                    <div>
                        <h2 className="text-2xl font-black tracking-tight text-ink">Course certificates</h2>
                        <p className="text-gray-600">{earned} of {credentials.length} earned · complete all lessons and get one assignment approved to unlock each one.</p>
                    </div>
                </div>
                {credentials.length === 0 ? (
                    <div className="mt-6 rounded-2xl border-2 border-dashed border-gray-300 p-10 text-center">
                        <p className="text-xl font-extrabold text-ink">No courses yet</p>
                        <p className="mt-2 text-gray-600">Choose a track to start working towards your first certificate.</p>
                        <a href="/courses?type=all" className="mt-6 inline-flex h-12 items-center px-6 rounded-full bg-brand text-white font-bold">Browse courses</a>
                    </div>
                ) : (
                    <div className="mt-6 space-y-5">{credentials.map((c) => <CertificateCard key={c.id} c={c} verifyBase={verifyBase} />)}</div>
                )}
            </section>
        </StudioLayout>
    );
}
