import React, { useEffect, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import axios from 'axios';
import AdminLayout, { PageHeader, useToast, errorText } from '@/Components/Admin/AdminLayout';
import { Button, Badge, DataTable, Pagination, SearchBox, FilterSelect, Segments, Toolbar, EmptyState, Drawer, useGrid, fmtDate } from '@/Components/Admin/kit';
import { Field, RichText } from '@/Components/Admin/form';

const TONE = { 'Under Review': 'amber', Feedback: 'blue', Reviewed: 'grey', Completed: 'green' };

/** Practice task answers from students (/admin/tasks): read the thread, reply and set the review status. */
export default function Tasks() {
    const { statuses = [], courses = [] } = usePage().props;
    const g = useGrid('grid');
    const [open, setOpen] = useState(null);
    const [toastNode, toast] = useToast();
    const columns = [
        { key: 'student', label: 'Student', sort: 'student', render: (r) => <span className="adm-cell__text"><b>{r.student}</b><small>{r.student_email}</small></span> },
        { key: 'task', label: 'Task', render: (r) => <span className="adm-cell__text"><button type="button" className="adm-linkbtn" onClick={() => setOpen(r.id)}>{r.task_title}</button><small>{[r.course, r.lesson_title].filter(Boolean).join(' · ')}</small></span> },
        { key: 'last', label: 'Last activity', sort: 'last', hide: 'md', render: (r) => <span>{fmtDate(r.last_at, true)}<br /><small className="adm-muted">{r.last_from === 'Student' ? 'Student wrote' : 'You replied'} · {r.messages} messages</small></span> },
        { key: 'status', label: 'Status', render: (r) => <>{r.last_from === 'Student' && <Badge tone="pink" dot>Needs reply</Badge>} <Badge tone={TONE[r.status] || 'grey'}>{r.status || '—'}</Badge></> },
        { key: 'open', label: <span className="sr-only">Open</span>, align: 'right', render: (r) => <Button size="sm" onClick={() => setOpen(r.id)}>Open</Button> },
    ];
    return (
        <AdminLayout title="Student tasks">
            <PageHeader title="Student tasks" subtitle="Answers to the practice tasks inside lessons. Reply with feedback and set where each one stands." />
            <Toolbar end={<Segments label="Status" value={g.filter('status')} onChange={(v) => g.go({ status: v })}
                options={[{ value: '', label: 'All' }, { value: 'waiting', label: 'Needs reply' }, ...statuses.map((s) => ({ value: s, label: s }))]} />}>
                <SearchBox value={g.meta.q} onSearch={g.search} placeholder="Student, task or course" />
                <FilterSelect label="Course" value={g.filter('course')} onChange={(v) => g.go({ course: v })} options={courses.map((c) => ({ value: String(c.value), label: c.label }))} />
            </Toolbar>
            <DataTable columns={columns} rows={g.rows} meta={g.meta} loading={g.loading} onSort={g.sortBy} caption="Student tasks" onRowClick={(r) => setOpen(r.id)}
                empty={<EmptyState icon="fa-comments-o" title="No task answers here">When students answer a practice task, it shows up in this list.</EmptyState>} />
            <Pagination meta={g.meta} onPage={g.setPage} onPer={g.setPer} />
            {open && <ThreadDrawer id={open} statuses={statuses} onClose={() => setOpen(null)} toast={toast} onChanged={() => g.reload()} />}
            {toastNode}
        </AdminLayout>
    );
}

function ThreadDrawer({ id, statuses, onClose, toast, onChanged }) {
    const [data, setData] = useState(null);
    const [error, setError] = useState(null);
    const [reply, setReply] = useState('');
    const [status, setStatus] = useState('Feedback');
    const [busy, setBusy] = useState(false);
    const [fieldError, setFieldError] = useState(null);
    useEffect(() => {
        axios.get(`/admin/api/tasks/${id}`).then((r) => {
            setData(r.data);
            const last = [...r.data.messages].reverse().find((m) => m.from_type === 'Student');
            if (last && last.review_status && last.review_status !== 'Under Review') setStatus(last.review_status);
        }).catch((e) => setError(errorText(e)));
    }, [id]);
    const send = () => {
        setBusy(true); setFieldError(null);
        axios.post(`/admin/api/tasks/${id}/reply`, { reply, status }).then((r) => { setData(r.data); setReply(''); toast(r.data.message); onChanged(); })
            .catch((e) => { if (e.response && e.response.status === 422) setFieldError(e.response.data.message); else toast(errorText(e), 'error'); })
            .finally(() => setBusy(false));
    };
    const t = data && data.thread;
    return (
        <Drawer open size="lg" title={t ? t.task_title : 'Task'} subtitle={t ? `${t.student} · ${[t.course, t.lesson_title].filter(Boolean).join(' · ')}` : undefined} onClose={onClose}
            footer={data && <>
                <label className="adm-filter"><span>Status</span>
                    <select value={status} onChange={(e) => setStatus(e.target.value)} aria-label="Review status">{statuses.map((s) => <option key={s} value={s}>{s}</option>)}</select>
                </label>
                <Button variant="brand" icon="fa-reply" busy={busy} onClick={send}>{reply.replace(/<[^>]+>/g, '').trim() ? 'Send reply' : 'Update status'}</Button>
            </>}>
            {error && <div className="adm-alert is-error" role="alert">{error}</div>}
            {!data && !error && <span className="adm-skel" style={{ height: 120 }}></span>}
            {data && (
                <div className="adm-stack">
                    {t.task_description && <div className="adm-alert is-info"><i className="fa fa-info-circle" aria-hidden="true"></i><span><b>Task:</b> {t.task_description}</span></div>}
                    <ol className="adm-thread">
                        {data.messages.map((m) => (
                            <li key={m.id} className={m.from_type === 'Teacher' ? 'is-staff' : ''}>
                                <div className="adm-thread__head"><b>{m.from_name}</b><small>{fmtDate(m.created_at, true)}{m.review_status && m.from_type === 'Student' ? ` · ${m.review_status}` : ''}</small></div>
                                <div className="adm-prose" dangerouslySetInnerHTML={{ __html: m.reply || '' }}></div>
                                {m.photos.length > 0 && <div className="adm-thread__photos">{m.photos.map((p) => <a key={p} href={p} target="_blank" rel="noopener noreferrer"><img src={p} alt="Student work" /></a>)}</div>}
                            </li>
                        ))}
                    </ol>
                    <Field label="Your reply" name="reply" error={fieldError} optional>
                        <RichText name="reply" value={reply} onChange={setReply} minHeight={140} placeholder="What works well, and what to try next…" />
                    </Field>
                </div>
            )}
        </Drawer>
    );
}
