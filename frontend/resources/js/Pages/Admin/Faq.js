import React, { useMemo, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import axios from 'axios';
import AdminLayout, { PageHeader, useToast, errorText } from '@/Components/Admin/AdminLayout';
import { Button, Badge, Card, SearchBox, Toolbar, EmptyState, Drawer, useConfirm } from '@/Components/Admin/kit';
import { useForm, Field, TextField, Toggle, RichText } from '@/Components/Admin/form';

/** FAQ shown on /faq: questions grouped by category, ordered by hand. */
export default function Faq() {
    const { faqs: initial = [] } = usePage().props;
    const [faqs, setFaqs] = useState(initial);
    const [q, setQ] = useState('');
    const [editing, setEditing] = useState(null);
    const [busy, setBusy] = useState(null);
    const [toastNode, toast] = useToast();
    const [dialog, confirm] = useConfirm();
    const categories = useMemo(() => Array.from(new Set(faqs.map((f) => f.category).filter(Boolean))), [faqs]);
    const shown = faqs.filter((f) => !q || `${f.question} ${f.category || ''} ${f.answer || ''}`.toLowerCase().includes(q.toLowerCase()));

    const move = (f, direction) => {
        setBusy(f.id);
        axios.post(`/admin/api/faqs/${f.id}/move`, { direction }).then((r) => setFaqs(r.data.faqs)).catch((e) => toast(errorText(e), 'error')).finally(() => setBusy(null));
    };
    const remove = async (f) => {
        if (!(await confirm({ title: 'Delete this question?', body: f.question, confirmLabel: 'Delete', danger: true }))) return;
        axios.delete(`/admin/api/faqs/${f.id}`).then((r) => { setFaqs(r.data.faqs); toast(r.data.message); }).catch((e) => toast(errorText(e), 'error'));
    };

    return (
        <AdminLayout title="FAQ">
            <PageHeader title="FAQ" subtitle="Questions and answers on the public FAQ page, in this order."
                actions={<>
                    <Button as="a" icon="fa-external-link" href="/faq" target="_blank" rel="noopener noreferrer">View page</Button>
                    <Button variant="brand" icon="fa-plus" onClick={() => setEditing({})}>Add question</Button>
                </>} />
            <Toolbar><SearchBox value={q} onSearch={setQ} placeholder="Search questions" delay={150} /></Toolbar>
            {shown.length === 0 ? (
                <Card><EmptyState icon="fa-question-circle-o" title={q ? 'No questions match' : 'No questions yet'} action={!q && <Button variant="brand" icon="fa-plus" onClick={() => setEditing({})}>Add the first question</Button>}>
                    Answer what students ask most: payments, class times, materials, certificates.
                </EmptyState></Card>
            ) : (
                <ol className="adm-faq">
                    {shown.map((f) => {
                        const i = faqs.findIndex((x) => x.id === f.id);
                        return (
                            <li key={f.id} className="adm-faq__item">
                                <span className="adm-mod__num">{i + 1}</span>
                                <button type="button" className="adm-faq__q" onClick={() => setEditing(f)}>
                                    <b>{f.question}</b>
                                    <small>{f.category || 'No category'}{Number(f.status) !== 1 && ' · hidden'}</small>
                                </button>
                                {Number(f.status) !== 1 && <Badge>Hidden</Badge>}
                                <div className="adm-rowactions">
                                    <Button size="sm" variant="ghost" icon="fa-arrow-up" disabled={!!q || i === 0} busy={busy === f.id} onClick={() => move(f, 'up')} aria-label="Move up" title="Move up" />
                                    <Button size="sm" variant="ghost" icon="fa-arrow-down" disabled={!!q || i === faqs.length - 1} onClick={() => move(f, 'down')} aria-label="Move down" title="Move down" />
                                    <Button size="sm" variant="ghost" icon="fa-pencil" onClick={() => setEditing(f)} aria-label={`Edit: ${f.question}`} title="Edit" />
                                    <Button size="sm" variant="ghost" icon="fa-trash-o" onClick={() => remove(f)} aria-label={`Delete: ${f.question}`} title="Delete" />
                                </div>
                            </li>
                        );
                    })}
                </ol>
            )}
            {editing && <FaqDrawer item={editing.id ? editing : null} categories={categories} onClose={() => setEditing(null)} toast={toast}
                onSaved={(r) => { setFaqs(r.data.faqs); toast(r.data.message); setEditing(null); }} />}
            {dialog}{toastNode}
        </AdminLayout>
    );
}

function FaqDrawer({ item, categories, onClose, onSaved, toast }) {
    const form = useForm({ question: item ? item.question : '', answer: item ? item.answer || '' : '', category: item ? item.category || '' : '', status: item ? Number(item.status) === 1 : true },
        { question: ['required', 'max:500'], answer: ['required'], category: ['max:100'] });
    const save = () => form.submit(() => axios.post('/admin/api/faqs', { ...form.values, id: item ? item.id : undefined })).then(onSaved)
        .catch((e) => { if (!e.invalid) toast(e.response && e.response.status === 422 ? e.response.data.message : errorText(e), 'error'); });
    return (
        <Drawer open size="lg" title={item ? 'Edit question' : 'New question'} onClose={onClose}
            footer={<><Button onClick={onClose}>Cancel</Button><Button variant="brand" icon="fa-check" busy={form.busy} onClick={save}>{item ? 'Save' : 'Add question'}</Button></>}>
            <form className="adm-stack" onSubmit={(e) => { e.preventDefault(); save(); }} noValidate>
                <TextField form={form} name="question" label="Question" required max={500} />
                <Field label="Answer" name="answer" required error={form.error('answer')}>
                    <RichText name="answer" value={form.values.answer} onChange={(x) => form.set('answer', x)} minHeight={180} error={form.error('answer')} />
                </Field>
                <TextField form={form} name="category" label="Category" max={100} hint={categories.length ? `Existing: ${categories.join(', ')}` : 'e.g. Payments, Classes, Certificates'} optional
                    inputProps={{ list: 'faq-cats' }} />
                <datalist id="faq-cats">{categories.map((c) => <option key={c} value={c} />)}</datalist>
                <Toggle name="status" checked={form.values.status} onChange={(x) => form.set('status', x)} label="Shown on the FAQ page" />
            </form>
        </Drawer>
    );
}
