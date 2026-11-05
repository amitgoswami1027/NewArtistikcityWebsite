import React, { useEffect, useState } from 'react';
import axios from 'axios';
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

const isPhone = () => {
    try {
        return window.matchMedia('(max-width: 767px)').matches || /iPhone|iPad|iPod|Android/i.test(navigator.userAgent || '');
    } catch (e) { return false; }
};
const kb = (n) => (Number(n) >= 1048576 ? `${(Number(n) / 1048576).toFixed(1)} MB` : `${Math.max(1, Math.round(Number(n || 0) / 1024))} KB`);

/** Opens a stored document through a fresh short-lived link (presigned S3 URL in production). */
function openDocument(id, inline) {
    // open the tab first, in the click, so phones don't block it as a pop-up; then point it at the link
    const w = inline ? window.open('', '_blank') : null;
    return axios.get(`/api/v1/studio/documents/${id}/link${inline ? '?inline=1' : ''}`)
        .then((r) => { if (w) w.location.href = r.data.url; else window.location.href = r.data.url; })
        .catch(() => { if (w) w.close(); window.alert('This report is no longer available. Make a new one.'); });
}

/**
 * Portfolio report: choose what to include, make the PDF (rendered on the server, stored privately in S3), then read
 * it right here (desktop) or open it in the phone's own PDF viewer. Earlier reports stay listed for 10 versions.
 */
function PortfolioBuilder({ credentials, stats, reports: initialReports }) {
    const [approvedOnly, setApprovedOnly] = useState(true);
    const [curriculum, setCurriculum] = useState(true);
    const [comments, setComments] = useState(true);
    const [courses, setCourses] = useState(credentials.map((c) => String(c.course_id)));
    const [busy, setBusy] = useState(false);
    const [err, setErr] = useState(null);
    const [made, setMade] = useState(null);        // {document, viewUrl, downloadUrl}
    const [reports, setReports] = useState(initialReports || []);
    const [phone, setPhone] = useState(false);
    useEffect(() => { setPhone(isPhone()); }, []);
    const toggleCourse = (id) => setCourses((list) => (list.includes(id) ? list.filter((x) => x !== id) : [...list, id]));
    const none = credentials.length > 0 && courses.length === 0;

    const create = () => {
        setBusy(true); setErr(null);
        axios.post('/api/v1/studio/portfolio', { approvedOnly, curriculum, comments, courses: courses.map(Number) })
            .then((r) => { setMade(r.data); setReports((xs) => [r.data.document, ...xs.filter((x) => x.id !== r.data.document.id)].slice(0, 10)); })
            .catch((e) => setErr((e.response && e.response.data && e.response.data.message) || 'We could not make your report. Please try again.'))
            .finally(() => setBusy(false));
    };

    const Toggle = ({ checked, onChange, label, hint }) => (
        <label className="pf-toggle">
            <input type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} />
            <span><b>{label}</b>{hint && <small>{hint}</small>}</span>
        </label>
    );

    return (
        <section className="pf" aria-labelledby="pf-title">
            <div className="pf__grid">
                <div className="pf__intro">
                    <p className="pf__eyebrow">PDF report</p>
                    <h2 id="pf-title">Portfolio report</h2>
                    <p>A print-ready portfolio: a cover with your details, every course with its curriculum and your progress, each artwork with instructor comments, and your certificate numbers.</p>
                    <div className="pf__stats">
                        {[[credentials.length, 'Courses'], [stats.lessons, 'Lessons'], [approvedOnly ? stats.approved : stats.total, 'Artworks']].map(([v, l]) => (
                            <div key={l}><b>{v}</b><span>{l}</span></div>
                        ))}
                    </div>
                </div>
                <div className="pf__form">
                    <h3>What to include</h3>
                    <div className="pf__toggles">
                        <Toggle checked={curriculum} onChange={setCurriculum} label="Course curriculum" hint="Units and lessons, with the ones you completed ticked" />
                        <Toggle checked={comments} onChange={setComments} label="Instructor comments" hint="Reviewer notes printed under each artwork" />
                        <Toggle checked={approvedOnly} onChange={setApprovedOnly} label="Approved artworks only" hint="Untick to include work in review or needing changes" />
                    </div>
                    {credentials.length > 1 && (
                        <fieldset className="pf__courses">
                            <legend>Courses</legend>
                            <div>
                                {credentials.map((c) => {
                                    const on = courses.includes(String(c.course_id));
                                    return <button key={c.course_id} type="button" aria-pressed={on} onClick={() => toggleCourse(String(c.course_id))}
                                                   className={on ? 'is-on' : ''}>{on && <i className="fa fa-check" aria-hidden="true"></i>} {c.title}</button>;
                                })}
                            </div>
                        </fieldset>
                    )}
                    <div className="pf__actions">
                        <button type="button" className="pf__btn pf__btn--primary" onClick={create} disabled={busy || none} data-testid="portfolio-create">
                            {busy ? <i className="fa fa-circle-o-notch fa-spin" aria-hidden="true"></i> : <i className="fa fa-file-pdf-o" aria-hidden="true"></i>}
                            {busy ? ' Making your PDF…' : ' Create portfolio PDF'}
                        </button>
                    </div>
                    {err && <p className="pf__error" role="alert">{err}</p>}
                    <p className="pf__fine">Made fresh each time, so it always shows your latest reviews and certificates. Stored privately; links last 10 minutes.</p>
                </div>
            </div>

            {made && (
                <div className="pf__viewer" data-testid="portfolio-viewer">
                    <div className="pf__viewerbar">
                        <span><i className="fa fa-check-circle" aria-hidden="true"></i> <b>{made.document.fileName}</b> · {kb(made.document.sizeBytes)}</span>
                        <span className="pf__viewerlinks">
                            <a href={made.viewUrl} target="_blank" rel="noopener noreferrer" className="pf__btn"><i className="fa fa-external-link" aria-hidden="true"></i> Open</a>
                            <a href={made.downloadUrl} className="pf__btn pf__btn--primary" data-testid="portfolio-download"><i className="fa fa-download" aria-hidden="true"></i> Download</a>
                        </span>
                    </div>
                    {phone ? (
                        <p className="pf__phone">Your PDF is ready. Tap <b>Open</b> to read it in your phone's PDF viewer, or <b>Download</b> to save it.</p>
                    ) : (
                        <iframe title="Your portfolio report" src={made.viewUrl} className="pf__frame" />
                    )}
                </div>
            )}

            {reports.length > 0 && (
                <div className="pf__history">
                    <h3>Your reports</h3>
                    <ul>
                        {reports.map((d) => (
                            <li key={d.id}>
                                <span><i className="fa fa-file-pdf-o" aria-hidden="true"></i> {shortDate(d.createdAt)} · {kb(d.sizeBytes)}</span>
                                <span>
                                    <button type="button" className="pf__link" onClick={() => openDocument(d.id, true)}>Open</button>
                                    <button type="button" className="pf__link" onClick={() => openDocument(d.id, false)}>Download</button>
                                </span>
                            </li>
                        ))}
                    </ul>
                </div>
            )}
        </section>
    );
}

export default function Certificates() {
    const { credentials = [], stats = {}, stage = 4, verifyBase = '', reports = [] } = usePage().props;
    const earned = credentials.filter((c) => c.eligible || c.certificate).length;
    return (
        <StudioLayout title="Certificates & reports" subtitle="Earn a verifiable certificate for each course and download a printable portfolio of your work." active="/dashboard/certificates">
            <PortfolioBuilder credentials={credentials} stats={stats} reports={reports} />

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
                        <p className="mt-2 text-gray-600">Choose a course to start working towards your first certificate.</p>
                        <a href="/courses?type=all" className="mt-6 inline-flex h-12 items-center px-6 rounded-full bg-brand text-white font-bold">Browse courses</a>
                    </div>
                ) : (
                    <div className="mt-6 space-y-5">{credentials.map((c) => <CertificateCard key={c.id} c={c} verifyBase={verifyBase} />)}</div>
                )}
            </section>
        </StudioLayout>
    );
}
