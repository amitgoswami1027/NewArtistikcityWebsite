import React, { useState } from 'react';
import axios from 'axios';
import { errorText } from '@/Components/Admin/AdminLayout';
import { Button, Badge, Drawer, EmptyState, Card } from '@/Components/Admin/kit';
import { useForm, Field, TextField, Toggle, DropZone, RichText, uploadImage } from '@/Components/Admin/form';

/** Modules → lessons → practice tasks, plus course projects. Every change saves straight away. */
export default function Curriculum({ courseId, initial, toast, confirm, noun = 'course', onChange }) {
    const [data, setData] = useState(initial || { modules: [], projects: [] });
    const [panel, setPanel] = useState(null); // {type: 'module'|'lesson'|'project', item, moduleId}
    const [open, setOpen] = useState(() => new Set((initial && initial.modules[0]) ? [initial.modules[0].id] : []));
    const [busy, setBusy] = useState(null);
    const api = `/admin/api/courses/${courseId}`;
    const apply = (r) => { setData(r.data.curriculum); if (onChange) onChange(r.data.curriculum); toast(r.data.message); return r; };
    const fail = (e) => toast(errorText(e), 'error');
    const toggle = (id) => setOpen((s) => { const n = new Set(s); n.has(id) ? n.delete(id) : n.add(id); return n; });

    const move = (kind, id, direction) => {
        setBusy(`${kind}${id}`);
        axios.post(`${api}/${kind}s/${id}/move`, { direction }).then(apply).catch(fail).finally(() => setBusy(null));
    };
    const remove = async (kind, item) => {
        const body = kind === 'module' ? `Its ${item.lessons.length} lesson(s), their tasks and students' progress on them are removed too.`
            : kind === 'lesson' ? 'Its practice tasks and students’ progress on this lesson are removed too.' : 'This cannot be undone.';
        if (!(await confirm({ title: `Delete “${item.title}”?`, body, confirmLabel: 'Delete', danger: true }))) return;
        axios.delete(`${api}/${kind}s/${item.id}`).then(apply).catch(fail);
    };
    const lessons = data.modules.reduce((n, m) => n + m.lessons.length, 0);

    return (
        <div className="adm-curr">
            <div className="adm-toolbar">
                <div className="adm-toolbar__main">
                    <span className="adm-muted"><b>{data.modules.length}</b> module{data.modules.length === 1 ? '' : 's'} · <b>{lessons}</b> lesson{lessons === 1 ? '' : 's'} · <b>{data.projects.length}</b> project{data.projects.length === 1 ? '' : 's'}</span>
                </div>
                <div className="adm-toolbar__end">
                    <Button icon="fa-plus" onClick={() => setPanel({ type: 'module', item: null })}>Add module</Button>
                    <Button variant="dark" icon="fa-plus" disabled={!data.modules.length} onClick={() => setPanel({ type: 'lesson', item: null, moduleId: data.modules[data.modules.length - 1].id })}
                        title={data.modules.length ? undefined : 'Add a module first'}>Add lesson</Button>
                </div>
            </div>

            {data.modules.length === 0 && (
                <Card><EmptyState icon="fa-list-ol" title="No modules yet" action={<Button variant="brand" icon="fa-plus" onClick={() => setPanel({ type: 'module', item: null })}>Add the first module</Button>}>
                    Group the {noun} into modules (for example “Week 1: Materials”), then add lessons to each module.
                </EmptyState></Card>
            )}

            <ol className="adm-mods">
                {data.modules.map((m, mi) => {
                    const isOpen = open.has(m.id);
                    return (
                        <li key={m.id} className={`adm-mod ${isOpen ? 'is-open' : ''}`}>
                            <div className="adm-mod__head">
                                <button type="button" className="adm-mod__toggle" onClick={() => toggle(m.id)} aria-expanded={isOpen} aria-controls={`mod-${m.id}`}>
                                    <i className={`fa fa-angle-${isOpen ? 'down' : 'right'}`} aria-hidden="true"></i>
                                    <span className="adm-mod__num">{mi + 1}</span>
                                    <span className="adm-mod__title"><b>{m.title}</b><small>{m.lessons.length} lesson{m.lessons.length === 1 ? '' : 's'}{m.duration ? ` · ${m.duration}` : ''}</small></span>
                                </button>
                                {!m.status && <Badge>Hidden</Badge>}
                                <div className="adm-rowactions">
                                    <Button size="sm" variant="ghost" icon="fa-arrow-up" disabled={mi === 0} busy={busy === `module${m.id}`} onClick={() => move('module', m.id, 'up')} aria-label={`Move ${m.title} up`} title="Move up" />
                                    <Button size="sm" variant="ghost" icon="fa-arrow-down" disabled={mi === data.modules.length - 1} onClick={() => move('module', m.id, 'down')} aria-label={`Move ${m.title} down`} title="Move down" />
                                    <Button size="sm" variant="ghost" icon="fa-pencil" onClick={() => setPanel({ type: 'module', item: m })} aria-label={`Edit ${m.title}`} title="Edit module" />
                                    <Button size="sm" variant="ghost" icon="fa-trash-o" onClick={() => remove('module', m)} aria-label={`Delete ${m.title}`} title="Delete module" />
                                </div>
                            </div>
                            {isOpen && (
                                <ul className="adm-lessons" id={`mod-${m.id}`}>
                                    {m.lessons.map((l, li) => (
                                        <li key={l.id} className="adm-lesson">
                                            <span className="adm-lesson__num">{li + 1}</span>
                                            <button type="button" className="adm-lesson__title" onClick={() => setPanel({ type: 'lesson', item: l, moduleId: m.id })}>
                                                <b>{l.title}</b>
                                                <small>
                                                    {l.tasks.length > 0 && <span><i className="fa fa-pencil-square-o" aria-hidden="true"></i> {l.tasks.length} task{l.tasks.length === 1 ? '' : 's'}</span>}
                                                    {l.pdf && <span><i className="fa fa-file-pdf-o" aria-hidden="true"></i> notes</span>}
                                                    {!l.description && <span className="adm-warn">No lesson notes yet</span>}
                                                </small>
                                            </button>
                                            {!l.status && <Badge>Hidden</Badge>}
                                            <div className="adm-rowactions">
                                                <Button size="sm" variant="ghost" icon="fa-arrow-up" disabled={li === 0} onClick={() => move('lesson', l.id, 'up')} aria-label={`Move ${l.title} up`} title="Move up" />
                                                <Button size="sm" variant="ghost" icon="fa-arrow-down" disabled={li === m.lessons.length - 1} onClick={() => move('lesson', l.id, 'down')} aria-label={`Move ${l.title} down`} title="Move down" />
                                                <Button size="sm" variant="ghost" icon="fa-trash-o" onClick={() => remove('lesson', l)} aria-label={`Delete ${l.title}`} title="Delete lesson" />
                                            </div>
                                        </li>
                                    ))}
                                    <li className="adm-lesson is-add">
                                        <button type="button" className="adm-linkbtn" onClick={() => setPanel({ type: 'lesson', item: null, moduleId: m.id })}><i className="fa fa-plus" aria-hidden="true"></i> Add a lesson to this module</button>
                                    </li>
                                </ul>
                            )}
                        </li>
                    );
                })}
            </ol>

            <Card title="Course projects" subtitle="Bigger pieces students submit for feedback and their certificate."
                actions={<Button size="sm" icon="fa-plus" onClick={() => setPanel({ type: 'project', item: null })}>Add project</Button>}>
                {data.projects.length === 0 ? <p className="adm-muted">No projects yet.</p> : (
                    <ul className="adm-plain">
                        {data.projects.map((p) => (
                            <li key={p.id} className="adm-lesson">
                                <i className="fa fa-paint-brush adm-lesson__num" aria-hidden="true"></i>
                                <button type="button" className="adm-lesson__title" onClick={() => setPanel({ type: 'project', item: p })}><b>{p.title}</b></button>
                                {!p.status && <Badge>Hidden</Badge>}
                                <Button size="sm" variant="ghost" icon="fa-trash-o" onClick={() => remove('project', p)} aria-label={`Delete ${p.title}`} title="Delete project" />
                            </li>
                        ))}
                    </ul>
                )}
            </Card>

            {panel && panel.type === 'module' && <ModulePanel api={api} item={panel.item} onClose={() => setPanel(null)} toast={toast}
                onSaved={(r) => { const before = data.modules.map((m) => m.id); apply(r); r.data.curriculum.modules.forEach((m) => { if (!before.includes(m.id)) setOpen((s) => new Set([...s, m.id])); }); setPanel(null); }} />}
            {panel && panel.type === 'lesson' && (
                <LessonPanel api={api} item={panel.item} moduleId={panel.moduleId} modules={data.modules} toast={toast} confirm={confirm}
                    onClose={() => setPanel(null)} onSaved={(r, close, moduleId) => { apply(r); if (moduleId) setOpen((s) => new Set([...s, Number(moduleId)])); if (close) setPanel(null); else { const l = findLesson(r.data.curriculum, panel.item ? panel.item.id : null); if (l) setPanel({ ...panel, item: l }); } }} />
            )}
            {panel && panel.type === 'project' && <ProjectPanel api={api} item={panel.item} onClose={() => setPanel(null)} onSaved={(r) => { apply(r); setPanel(null); }} toast={toast} />}
        </div>
    );
}

const findLesson = (cur, id) => {
    let found = null;
    cur.modules.forEach((m) => m.lessons.forEach((l) => { if (l.id === id) found = l; }));
    return found;
};

const reject = (toast) => (e) => toast(e.response && e.response.status === 422 ? e.response.data.message : errorText(e), 'error');

function ModulePanel({ api, item, onClose, onSaved, toast }) {
    const form = useForm({ title: item ? item.title : '', duration: item ? item.duration : '', status: item ? item.status : true }, { title: ['required', 'max:255'] });
    const save = () => form.submit(() => axios.post(`${api}/modules`, { ...form.values, id: item ? item.id : undefined })).then(onSaved).catch(reject(toast));
    return (
        <Drawer open title={item ? 'Edit module' : 'New module'} size="sm" onClose={onClose}
            footer={<><Button onClick={onClose}>Cancel</Button><Button variant="brand" busy={form.busy} onClick={save} icon="fa-check">{item ? 'Save module' : 'Add module'}</Button></>}>
            <form className="adm-stack" onSubmit={(e) => { e.preventDefault(); save(); }} noValidate>
                <TextField form={form} name="title" label="Module title" required max={255} placeholder="e.g. Week 1: Materials and first washes" />
                <TextField form={form} name="duration" label="Duration" placeholder="e.g. 1 week" optional />
                <Toggle name="status" checked={form.values.status} onChange={(x) => form.set('status', x)} label="Visible to students" />
            </form>
        </Drawer>
    );
}

function LessonPanel({ api, item, moduleId, modules, onClose, onSaved, toast, confirm }) {
    const form = useForm({ title: item ? item.title : '', module_id: String(moduleId || ''), description: item ? item.description : '', status: item ? item.status : true },
        { title: ['required', 'max:255'], module_id: ['required'] });
    const [pdf, setPdf] = useState([]);
    const [removePdf, setRemovePdf] = useState(false);
    const save = (close = true) => {
        const fd = new FormData();
        if (item) fd.append('id', item.id);
        fd.append('title', form.values.title);
        fd.append('module_id', form.values.module_id);
        fd.append('description', form.values.description || '');
        fd.append('status', form.values.status ? '1' : '0');
        if (pdf[0]) fd.append('pdf', pdf[0]);
        if (removePdf) fd.append('remove_pdf', '1');
        return form.submit(() => axios.post(`${api}/lessons`, fd)).then((r) => { setPdf([]); setRemovePdf(false); onSaved(r, close, form.values.module_id); }).catch(reject(toast));
    };
    return (
        <Drawer open size="lg" title={item ? 'Edit lesson' : 'New lesson'} subtitle={item ? undefined : 'Students see lessons in the order of this list.'} onClose={onClose}
            footer={<><Button onClick={onClose}>Cancel</Button><Button variant="brand" busy={form.busy} onClick={() => save(true)} icon="fa-check">{item ? 'Save lesson' : 'Add lesson'}</Button></>}>
            <form className="adm-stack" onSubmit={(e) => { e.preventDefault(); save(true); }} noValidate>
                <TextField form={form} name="title" label="Lesson title" required max={255} placeholder="e.g. Flat and graded washes" />
                <Field label="Module" name="module_id" required error={form.error('module_id')}>
                    <select className="adm-input" {...form.bind('module_id')}>
                        {modules.map((m, i) => <option key={m.id} value={String(m.id)}>{i + 1}. {m.title}</option>)}
                    </select>
                </Field>
                <Field label="Lesson notes" name="description" hint="Shown in the student's lesson view under the video." optional>
                    <RichText name="description" value={form.values.description} onChange={(x) => form.set('description', x)} minHeight={220} onUpload={uploadImage} />
                </Field>
                <Field label="Notes PDF" name="pdf" optional>
                    <DropZone name="pdf" kind="pdf" value={pdf} onChange={setPdf} error={form.error('pdf')}
                        existing={item && item.pdf && !removePdf ? [{ id: 'pdf', ...item.pdf }] : []} onRemoveExisting={() => setRemovePdf(true)} />
                </Field>
                <Toggle name="status" checked={form.values.status} onChange={(x) => form.set('status', x)} label="Visible to students" />
            </form>
            {item && <Tasks api={api} lesson={item} toast={toast} confirm={confirm} onChanged={(r) => onSaved(r, false)} />}
            {!item && <p className="adm-muted adm-gap">Save the lesson to add practice tasks to it.</p>}
        </Drawer>
    );
}

function Tasks({ api, lesson, toast, confirm, onChanged }) {
    const [edit, setEdit] = useState(null);
    const form = useForm({ title: '', description: '', status: true }, { title: ['required', 'max:255'] });
    const start = (t) => { setEdit(t ? t.id : 'new'); form.reset({ title: t ? t.title : '', description: t ? t.description : '', status: t ? t.status : true }); };
    const save = () => form.submit(() => axios.post(`${api}/tasks`, { ...form.values, lesson_id: lesson.id, id: edit === 'new' ? undefined : edit }))
        .then((r) => { setEdit(null); onChanged(r); }).catch(reject(toast));
    const remove = async (t) => {
        if (!(await confirm({ title: `Delete the task “${t.title}”?`, confirmLabel: 'Delete', danger: true }))) return;
        axios.delete(`${api}/tasks/${t.id}`).then(onChanged).catch(reject(toast));
    };
    return (
        <div className="adm-tasks">
            <h3 className="adm-sub">Practice tasks</h3>
            {lesson.tasks.length === 0 && edit === null && <p className="adm-muted">No tasks. Tasks ask students to practise and send their work for feedback.</p>}
            <ul className="adm-plain">
                {lesson.tasks.map((t) => (edit === t.id ? null : (
                    <li key={t.id} className="adm-task">
                        <div><b>{t.title}</b>{!t.status && <> <Badge>Hidden</Badge></>}{t.description && <p>{t.description}</p>}</div>
                        <div className="adm-rowactions">
                            <Button size="sm" variant="ghost" icon="fa-pencil" onClick={() => start(t)} aria-label={`Edit task ${t.title}`} title="Edit" />
                            <Button size="sm" variant="ghost" icon="fa-trash-o" onClick={() => remove(t)} aria-label={`Delete task ${t.title}`} title="Delete" />
                        </div>
                    </li>
                )))}
            </ul>
            {edit !== null ? (
                <div className="adm-task is-edit">
                    <TextField form={form} name="title" label="Task" required max={255} placeholder="e.g. Paint three wash swatches" />
                    <TextField form={form} name="description" label="Instructions" rows={3} optional />
                    <Toggle name="task-status" checked={form.values.status} onChange={(x) => form.set('status', x)} label="Visible to students" />
                    <div className="adm-inline">
                        <Button variant="dark" size="sm" busy={form.busy} onClick={save} icon="fa-check">{edit === 'new' ? 'Add task' : 'Save task'}</Button>
                        <Button size="sm" onClick={() => setEdit(null)}>Cancel</Button>
                    </div>
                </div>
            ) : <Button size="sm" icon="fa-plus" onClick={() => start(null)}>Add a task</Button>}
        </div>
    );
}

function ProjectPanel({ api, item, onClose, onSaved, toast }) {
    const form = useForm({ title: item ? item.title : '', description: item ? item.description : '', status: item ? item.status : true }, { title: ['required', 'max:255'] });
    const save = () => form.submit(() => axios.post(`${api}/projects`, { ...form.values, id: item ? item.id : undefined })).then(onSaved).catch(reject(toast));
    return (
        <Drawer open size="lg" title={item ? 'Edit project' : 'New project'} onClose={onClose}
            footer={<><Button onClick={onClose}>Cancel</Button><Button variant="brand" busy={form.busy} onClick={save} icon="fa-check">{item ? 'Save project' : 'Add project'}</Button></>}>
            <form className="adm-stack" onSubmit={(e) => { e.preventDefault(); save(); }} noValidate>
                <TextField form={form} name="title" label="Project title" required max={255} placeholder="e.g. Final landscape" />
                <Field label="Brief" name="description" optional>
                    <RichText name="description" value={form.values.description} onChange={(x) => form.set('description', x)} minHeight={200} />
                </Field>
                <Toggle name="status" checked={form.values.status} onChange={(x) => form.set('status', x)} label="Visible to students" />
            </form>
        </Drawer>
    );
}
