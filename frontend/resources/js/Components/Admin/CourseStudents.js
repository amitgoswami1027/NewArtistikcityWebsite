import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { errorText } from '@/Components/Admin/AdminLayout';
import { Button, Badge, fmtDate } from '@/Components/Admin/kit';

/*
 * Course editor > Students: message every enrolled student (studio notice + optional e-mail, with a delivery log),
 * move the start date (students told old -> new), or cancel the run (hidden, refunds queued for staff to confirm,
 * waitlist closed, students told). Backend: notices/CourseNoticeController.
 */
export default function CourseStudents({ courseId, noun = 'course', toast }) {
    const [d, setD] = useState(null);
    const [msg, setMsg] = useState({ subject: '', body: '', email: true });
    const [move, setMove] = useState({ date: '', note: '' });
    const [cancel, setCancel] = useState({ reason: '', refund: true, refundPercent: '100', confirmTitle: '' });
    const [err, setErr] = useState({});
    const [busy, setBusy] = useState(null);
    const [log, setLog] = useState({});
    const load = () => axios.get(`/admin/api/courses/${courseId}/messages`).then((r) => setD(r.data)).catch((e) => toast(errorText(e), 'error'));
    useEffect(() => { load(); }, [courseId]);
    const post = (key, url, body, after) => {
        setBusy(key); setErr({});
        axios.post(url, body).then((r) => { toast(r.data.message); if (after) after(); load(); })
            .catch((e) => { const x = e.response && e.response.data; if (x && x.errors) setErr(x.errors); else toast(errorText(e), 'error'); })
            .finally(() => setBusy(null));
    };
    const showLog = (id) => axios.get(`/admin/api/course-messages/${id}/deliveries`).then((r) => setLog({ ...log, [id]: r.data.deliveries }));
    if (!d) return <div className="adm-stack" aria-busy="true"><span className="adm-skel" style={{ height: 18, width: '60%' }}></span><span className="adm-skel" style={{ height: 120 }}></span></div>;
    const cancelled = !!d.course.cancelled_at;

    return (
        <div className="adm-stack adm-students">
            {cancelled && <div className="adm-alert is-error" role="status"><i className="fa fa-ban" aria-hidden="true"></i>This run was cancelled on {fmtDate(d.course.cancelled_at, true)}: {d.course.cancel_reason}</div>}
            <section className="adm-card adm-pad" aria-labelledby="cs-msg">
                <h2 id="cs-msg" className="adm-sub">Message the students <Badge>{d.recipients} enrolled</Badge></h2>
                <p className="adm-muted">Shown in each student's studio. Tick e-mail to send it to their inbox as well.</p>
                <label className="adm-label" htmlFor="cs-subject">Subject</label>
                <input id="cs-subject" className="adm-input" value={msg.subject} onChange={(e) => setMsg({ ...msg, subject: e.target.value })} maxLength={200} placeholder="e.g. Bring a 6B pencil to Saturday's class" aria-invalid={!!err.subject} />
                {err.subject && <p className="adm-error" role="alert">{err.subject}</p>}
                <label className="adm-label" htmlFor="cs-body">Message</label>
                <textarea id="cs-body" className="adm-input" rows={6} value={msg.body} onChange={(e) => setMsg({ ...msg, body: e.target.value })} placeholder="Plain text. Leave an empty line between paragraphs." aria-invalid={!!err.body} />
                {err.body && <p className="adm-error" role="alert">{err.body}</p>}
                <div className="adm-inline adm-inline--between">
                    <label className="adm-check"><input type="checkbox" checked={msg.email} onChange={(e) => setMsg({ ...msg, email: e.target.checked })} /> Also send by e-mail</label>
                    <Button variant="brand" icon="fa-paper-plane" busy={busy === 'msg'} disabled={!d.recipients} onClick={() => post('msg', `/admin/api/courses/${courseId}/messages`, msg, () => setMsg({ subject: '', body: '', email: true }))}>Send to {d.recipients}</Button>
                </div>
            </section>

            {!cancelled && (
                <section className="adm-card adm-pad" aria-labelledby="cs-move">
                    <h2 id="cs-move" className="adm-sub">Move the start date</h2>
                    <p className="adm-muted">Currently {d.course.course_start_date ? fmtDate(d.course.course_start_date) : 'not set'}. Every student is told the old and the new date.</p>
                    <div className="adm-inline">
                        <label className="adm-filter"><span>New date</span><input type="date" value={move.date} onChange={(e) => setMove({ ...move, date: e.target.value })} aria-label="New start date" /></label>
                        <input className="adm-input" style={{ flex: 1, minWidth: 200 }} value={move.note} onChange={(e) => setMove({ ...move, note: e.target.value })} placeholder="Why (optional, goes into the e-mail)" aria-label="Reason for the new date" />
                        <Button busy={busy === 'move'} disabled={!move.date} onClick={() => post('move', `/admin/api/courses/${courseId}/reschedule`, move, () => setMove({ date: '', note: '' }))}>Move & tell students</Button>
                    </div>
                    {err.date && <p className="adm-error" role="alert">{err.date}</p>}
                </section>
            )}

            {!cancelled && d.canCancel && (
                <section className="adm-card adm-pad adm-danger" aria-labelledby="cs-cancel">
                    <h2 id="cs-cancel" className="adm-sub">Cancel this {noun}</h2>
                    <p className="adm-muted">Hides it from the site, closes the waitlist and tells every student. With refunds on, each paid order goes to Sales › Refunds, where you confirm the refunds one by one.</p>
                    <label className="adm-label" htmlFor="cs-reason">Reason (shown to students)</label>
                    <textarea id="cs-reason" className="adm-input" rows={2} value={cancel.reason} onChange={(e) => setCancel({ ...cancel, reason: e.target.value })} aria-invalid={!!err.reason} />
                    {err.reason && <p className="adm-error" role="alert">{err.reason}</p>}
                    <div className="adm-inline">
                        <label className="adm-check"><input type="checkbox" checked={cancel.refund} onChange={(e) => setCancel({ ...cancel, refund: e.target.checked })} /> Queue a refund for every paid order:</label>
                        <label className="adm-filter"><span>% of each payment</span><input type="number" min="1" max="100" value={cancel.refundPercent} disabled={!cancel.refund} onChange={(e) => setCancel({ ...cancel, refundPercent: e.target.value })} aria-label="Refund percentage" /></label>
                    </div>
                    {err.refundPercent && <p className="adm-error" role="alert">{err.refundPercent}</p>}
                    <label className="adm-label" htmlFor="cs-confirm">Type the title to confirm: <b>{d.course.title}</b></label>
                    <input id="cs-confirm" className="adm-input" value={cancel.confirmTitle} onChange={(e) => setCancel({ ...cancel, confirmTitle: e.target.value })} aria-invalid={!!err.confirmTitle} />
                    {err.confirmTitle && <p className="adm-error" role="alert">{err.confirmTitle}</p>}
                    <Button variant="danger" icon="fa-ban" busy={busy === 'cancel'} disabled={cancel.confirmTitle !== d.course.title} onClick={() => post('cancel', `/admin/api/courses/${courseId}/cancel`, cancel)}>Cancel {noun} & notify</Button>
                </section>
            )}

            <section aria-labelledby="cs-hist">
                <h2 id="cs-hist" className="adm-sub">Sent messages</h2>
                {d.messages.length === 0 ? <p className="adm-muted">Nothing sent yet.</p> : (
                    <ol className="adm-timeline">
                        {d.messages.map((m) => (
                            <li key={m.id}>
                                <b>{m.subject}</b> <Badge tone={m.kind === 'CANCELLED' ? 'red' : m.kind === 'RESCHEDULED' ? 'blue' : 'grey'}>{m.kind === 'MESSAGE' ? 'Message' : m.kind === 'CANCELLED' ? 'Cancelled' : 'New date'}</Badge>
                                <small>{fmtDate(m.created_at, true)} · {m.admin_name || 'System'} · {m.recipients} students · {Number(m.send_email) ? `${m.emailed} e-mailed${m.failed ? `, ${m.failed} failed` : ''}` : 'studio only'} · {m.read_count} read
                                    {' '}<button type="button" className="adm-linkbtn" onClick={() => showLog(m.id)}>Delivery log</button></small>
                                {log[m.id] && (
                                    <ul className="adm-small">{log[m.id].map((x, i) => <li key={i}>{x.name || x.email} — {x.email_status === 'SENT' ? 'e-mailed' : x.email_status === 'FAILED' ? `e-mail failed${x.error ? ` (${x.error})` : ''}` : 'studio only'}{x.read_at ? ` · read ${fmtDate(x.read_at, true)}` : ' · not read yet'}</li>)}</ul>
                                )}
                            </li>
                        ))}
                    </ol>
                )}
            </section>
        </div>
    );
}
