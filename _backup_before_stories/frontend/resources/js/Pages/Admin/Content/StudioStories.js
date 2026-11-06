import React, { useMemo, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import { Inertia } from '@inertiajs/inertia';
import axios from 'axios';
import AdminLayout, { PageHeader, useToast, errorText } from '@/Components/Admin/AdminLayout';
import { Button, Badge, DataTable, Pagination, SearchBox, FilterSelect, Segments, Toolbar, EmptyState, useGrid, useConfirm, fmtDate, cx } from '@/Components/Admin/kit';
import { useForm, useUnsavedGuard, uploadImage, Field, TextField, Toggle, ChoiceChips, SlugField, TagInput, DropZone, RichText } from '@/Components/Admin/form';

/*
 * Studio Stories CMS (/admin/content/stories): an analytics dashboard over the stories table, and the editor
 * (/admin/content/stories/new, /admin/content/stories/{id}): a centred writing sheet with a sidebar for the workflow,
 * author, tags, categories, clean URL, cover and SEO.
 */

export const WORKFLOW = {
    draft: { tone: 'grey', label: 'Draft', icon: 'fa-pencil' },
    review: { tone: 'blue', label: 'In review', icon: 'fa-eye' },
    scheduled: { tone: 'amber', label: 'Scheduled', icon: 'fa-clock-o' },
    published: { tone: 'green', label: 'Published', icon: 'fa-globe' },
    archived: { tone: 'pink', label: 'Archived', icon: 'fa-archive' },
};
const PHASES = ['draft', 'review', 'scheduled', 'published', 'archived'];

const strip = (html) => String(html || '').replace(/<[^>]+>/g, ' ').replace(/&nbsp;/g, ' ').replace(/\s+/g, ' ').trim();
const num = (n) => Number(n || 0).toLocaleString('en-IN');
const change = (now, before) => {
    const a = Number(now || 0); const b = Number(before || 0);
    if (!b) return a ? { tone: 'up', text: 'new' } : null;
    const pct = Math.round(((a - b) / b) * 100);
    return { tone: pct > 0 ? 'up' : pct < 0 ? 'down' : 'flat', text: `${pct > 0 ? '+' : ''}${pct}%` };
};

export function StatusBadge({ workflow }) {
    const w = WORKFLOW[workflow] || WORKFLOW.draft;
    return <Badge tone={w.tone} dot>{w.label}</Badge>;
}

/* --------------------------------------------------------------- dashboard */

function Sparkline({ series = [] }) {
    const max = Math.max(1, ...series.map((d) => Number(d.views) || 0));
    const w = 300; const h = 56;
    const step = series.length > 1 ? w / (series.length - 1) : w;
    const pts = series.map((d, i) => `${(i * step).toFixed(1)},${(h - ((Number(d.views) || 0) / max) * (h - 6) - 3).toFixed(1)}`).join(' ');
    const total = series.reduce((s, d) => s + (Number(d.views) || 0), 0);
    return (
        <svg className="st-spark" viewBox={`0 0 ${w} ${h}`} preserveAspectRatio="none" role="img" aria-label={`Daily views over the last ${series.length} days, ${num(total)} in total`}>
            {series.length > 1 && <polygon points={`0,${h} ${pts} ${w},${h}`} className="st-spark__area" />}
            {series.length > 1 && <polyline points={pts} className="st-spark__line" />}
        </svg>
    );
}

function Kpi({ label, value, sub, delta, icon, children }) {
    return (
        <div className="st-kpi">
            <span className="st-kpi__label"><i className={`fa ${icon}`} aria-hidden="true"></i> {label}</span>
            <b className="st-kpi__value">{value}</b>
            <span className="st-kpi__sub">
                {delta && <span className={cx('st-delta', `is-${delta.tone}`)}>{delta.tone === 'up' ? '▲' : delta.tone === 'down' ? '▼' : '•'} {delta.text}</span>} {sub}
            </span>
            {children}
        </div>
    );
}

function Dashboard() {
    const { counts = {}, analytics = {}, categories = [], authors = [] } = usePage().props;
    const g = useGrid('grid', ['counts', 'analytics']);
    const [toastNode, toast] = useToast();
    const [dialog, confirm] = useConfirm();
    const [busyId, setBusyId] = useState(null);

    const move = (r, workflow) => {
        setBusyId(r.id);
        axios.post(`/admin/api/content/stories/${r.id}/workflow`, { workflow })
            .then((x) => { toast(x.data.message); g.reload(); })
            .catch((e) => toast(errorText(e), 'error'))
            .finally(() => setBusyId(null));
    };
    const remove = async (r) => {
        if (!(await confirm({ title: `Delete “${r.title}”?`, body: 'The story, its link and its view history are removed for good. Archive it instead to keep it.', confirmLabel: 'Delete', danger: true }))) return;
        axios.delete(`/admin/api/content/stories/${r.id}`).then((x) => { toast(x.data.message); g.reload(); }).catch((e) => toast(errorText(e), 'error'));
    };

    const a = analytics || {};
    const columns = [
        { key: 'title', label: 'Story', sort: 'title', render: (r) => (
            <div className="adm-cell">
                {r.image_url ? <img src={r.image_url} alt="" /> : <span className="adm-thumb"><i className="fa fa-file-text-o" aria-hidden="true"></i></span>}
                <span className="adm-cell__text">
                    <a href={`/admin/content/stories/${r.id}`}><b>{r.title}</b></a>
                    <small>{r.tags || 'No tags'}{Number(r.featured) === 1 ? ' · Featured' : ''}{Number(r.read_minutes) > 0 ? ` · ${r.read_minutes} min read` : ''}</small>
                </span>
            </div>
        ) },
        { key: 'author', label: 'Author', hide: 'lg', render: (r) => r.author || <span className="adm-muted">—</span> },
        { key: 'views', label: 'Views', sort: 'views', align: 'right', hide: 'md', render: (r) => (
            <span className="st-views"><b>{num(r.views)}</b><small>{num(r.views_30d)} in 30 days</small></span>
        ) },
        { key: 'updated', label: 'Updated', sort: 'updated', hide: 'lg', render: (r) => fmtDate(r.updated_at, true) },
        { key: 'status', label: 'Status', render: (r) => (
            <span className="st-status">
                <StatusBadge workflow={r.workflow} />
                {r.workflow === 'scheduled' && r.publish_at && <small>{fmtDate(r.publish_at, true)}</small>}
                {r.workflow === 'published' && r.published_at && <small>{fmtDate(r.published_at)}</small>}
            </span>
        ) },
        { key: 'actions', label: <span className="sr-only">Actions</span>, align: 'right', render: (r) => (
            <div className="adm-rowactions st-quick" aria-busy={busyId === r.id}>
                {r.workflow !== 'draft' && <button type="button" className="st-chip" onClick={() => move(r, 'draft')} disabled={busyId === r.id} aria-label={`Move ${r.title} to drafts`}><i className="fa fa-pencil" aria-hidden="true"></i> Draft</button>}
                {r.workflow !== 'published' && <button type="button" className="st-chip is-green" onClick={() => move(r, 'published')} disabled={busyId === r.id} aria-label={`Publish ${r.title}`}><i className="fa fa-globe" aria-hidden="true"></i> Publish</button>}
                {r.workflow !== 'archived' && <button type="button" className="st-chip is-pink" onClick={() => move(r, 'archived')} disabled={busyId === r.id} aria-label={`Archive ${r.title}`}><i className="fa fa-archive" aria-hidden="true"></i> Archive</button>}
                <Button as="a" size="sm" variant="ghost" icon="fa-pencil-square-o" href={`/admin/content/stories/${r.id}`} aria-label={`Edit ${r.title}`} title="Edit" />
                <Button as="a" size="sm" variant="ghost" icon="fa-external-link" href={r.public_url} target="_blank" rel="noopener noreferrer" aria-label={`View ${r.title}`} title={r.workflow === 'published' ? 'View on site' : 'Preview'} />
                <Button size="sm" variant="ghost" icon="fa-trash-o" onClick={() => remove(r)} aria-label={`Delete ${r.title}`} title="Delete" />
            </div>
        ) },
    ];

    const vDelta = change(a.views30, a.views30Previous);
    const velDelta = change(a.velocity, a.velocityPrevious);

    return (
        <AdminLayout title="Studio Stories" crumbs={[{ label: 'Content' }, { label: 'Studio Stories' }]}>
            <PageHeader eyebrow="Content" title="Studio Stories" subtitle="Write, review, schedule and publish the stories on /studio-stories. Only published stories are public."
                actions={<>
                    <Button as="a" icon="fa-bookmark-o" href="/admin/blog/taxonomy">Categories & tags</Button>
                    <Button as="a" variant="brand" icon="fa-plus" href="/admin/content/stories/new">New story</Button>
                </>} />

            <section className="st-kpis" aria-label="Content analytics">
                <Kpi label="Views · 30 days" icon="fa-eye" value={num(a.views30)} delta={vDelta} sub={`vs previous 30 days · ${num(a.viewsTotal)} all time`}>
                    <Sparkline series={a.series || []} />
                </Kpi>
                <Kpi label="Engagement velocity" icon="fa-bolt" value={<>{a.velocity || 0}<small> views/day</small></>} delta={velDelta} sub="last 7 days vs the 7 before" />
                <Kpi label="Average read time" icon="fa-clock-o" value={<>{a.avgReadMinutes || 0}<small> min</small></>} sub={`across ${num(a.published)} published ${Number(a.published) === 1 ? 'story' : 'stories'}`} />
                <div className="st-kpi st-top">
                    <span className="st-kpi__label"><i className="fa fa-trophy" aria-hidden="true"></i> Top stories · 30 days</span>
                    {(a.top || []).length === 0 ? <p className="adm-muted adm-small">Views appear here once published stories are read.</p> : (
                        <ol>
                            {a.top.map((t) => (
                                <li key={t.id}><a href={`/admin/content/stories/${t.id}`}>{t.title}</a><b>{num(t.views)}</b></li>
                            ))}
                        </ol>
                    )}
                </div>
            </section>

            <Toolbar end={<Segments label="Status" value={g.filter('status')} onChange={(x) => g.go({ status: x })}
                options={[{ value: '', label: 'All', count: counts.all }, ...PHASES.map((p) => ({ value: p, label: WORKFLOW[p].label, count: counts[p] }))]} />}>
                <SearchBox value={g.meta.q} onSearch={g.search} placeholder="Search title, summary or URL" label="Search stories" />
                <FilterSelect label="Author" value={g.filter('author')} onChange={(x) => g.go({ author: x })} options={authors.map((o) => ({ value: String(o.value), label: o.label }))} />
                <FilterSelect label="Category" value={g.filter('category')} onChange={(x) => g.go({ category: x })} options={categories.map((c) => ({ value: String(c.value), label: c.label }))} />
                <FilterSelect label="Featured" value={g.filter('featured')} onChange={(x) => g.go({ featured: x })} options={[{ value: '1', label: 'Featured only' }]} allLabel="Any" />
            </Toolbar>
            <DataTable columns={columns} rows={g.rows} meta={g.meta} loading={g.loading} onSort={g.sortBy} caption="Studio stories"
                empty={<EmptyState icon="fa-pencil-square-o" title={g.meta.q || g.filter('status') ? 'No stories match' : 'No stories yet'}
                    action={<Button as="a" variant="brand" icon="fa-plus" href="/admin/content/stories/new">Write a story</Button>} />} />
            <Pagination meta={g.meta} onPage={g.setPage} onPer={g.setPer} />
            {dialog}{toastNode}
        </AdminLayout>
    );
}

/* ------------------------------------------------------------------ editor */

const toLocalInput = (s) => (s ? String(s).replace(' ', 'T').slice(0, 16) : '');

function Editor() {
    const { post, categories = [], tags = [], authors = [], me } = usePage().props;
    const [saved, setSaved] = useState(post);
    const isNew = !saved;
    const [image, setImage] = useState([]);
    const [removeImage, setRemoveImage] = useState(false);
    const [focusMode, setFocusMode] = useState(false);
    const [toastNode, toast] = useToast();
    const initial = post ? {
        title: post.title, slug: post.slug, subtitle: post.subtitle, body: post.body, workflow: post.workflow, featured: post.featured,
        category_ids: post.category_ids, tags: post.tags, meta_title: post.meta_title, meta_description: post.meta_description,
        author_id: String(post.author_id || ''), publish_at: toLocalInput(post.publish_at),
    } : { title: '', slug: '', subtitle: '', body: '', workflow: 'draft', featured: false, category_ids: [], tags: [], meta_title: '', meta_description: '', author_id: me ? String(me) : '', publish_at: '' };
    const form = useForm(initial, { title: ['required', 'max:256'], slug: ['slug', 'max:100'], subtitle: ['max:500'], meta_title: ['max:255'], meta_description: ['max:500'] });
    const v = form.values;
    useUnsavedGuard(form.dirty || image.length > 0 || removeImage);
    const words = useMemo(() => (strip(v.body) ? strip(v.body).split(' ').length : 0), [v.body]);
    const minutes = words ? Math.max(1, Math.ceil(words / 200)) : 0;
    const savedFlow = saved ? saved.workflow : 'draft';

    const save = (workflow) => {
        const next = { ...v, workflow: workflow || v.workflow, remove_image: removeImage };
        const extra = {};
        if (next.workflow === 'published' || next.workflow === 'scheduled') {
            if (!strip(next.subtitle)) extra.subtitle = 'Add a short summary; it shows in the story list.';
            if (!strip(next.body)) extra.body = 'Write the story before publishing it.';
        }
        if (next.workflow === 'scheduled') {
            if (!next.publish_at) extra.publish_at = 'Pick the date and time to publish.';
            else if (new Date(next.publish_at).getTime() < Date.now() - 60000) extra.publish_at = 'Pick a time in the future, or publish now.';
        }
        const ok = form.validate();
        if (!ok || Object.keys(extra).length) {
            form.setErrors((prev) => ({ ...prev, ...extra }));
            toast('Please fix the highlighted fields.', 'error');
            return;
        }
        const fd = new FormData();
        fd.append('data', JSON.stringify(next));
        if (image[0]) fd.append('image', image[0]);
        form.submit(() => axios.post(isNew ? '/admin/api/content/stories' : `/admin/api/content/stories/${saved.id}`, fd), { skipValidation: true }).then((r) => {
            toast(r.data.message);
            if (isNew) { Inertia.visit(r.data.editUrl, { replace: true }); return; }
            const p = r.data.post;
            setSaved(p);
            form.reset({ ...v, workflow: p.workflow, slug: p.slug, publish_at: toLocalInput(p.publish_at) });
            setImage([]); setRemoveImage(false);
        }).catch((e) => toast(e.response && e.response.status === 422 ? e.response.data.message : errorText(e), 'error'));
    };

    const primary = {
        draft: ['Save draft', 'fa-floppy-o'],
        review: ['Send for review', 'fa-paper-plane'],
        scheduled: ['Schedule', 'fa-clock-o'],
        published: [savedFlow === 'published' ? 'Update live story' : 'Publish now', 'fa-globe'],
        archived: ['Archive', 'fa-archive'],
    }[v.workflow] || ['Save', 'fa-check'];

    const snippetTitle = v.meta_title || v.title || 'Story title';
    const snippetText = v.meta_description || v.subtitle || strip(v.body).slice(0, 160) || 'A short description of the story appears here in search results.';
    const title = isNew ? 'New story' : saved.title || 'Untitled story';

    return (
        <AdminLayout title={title} wide crumbs={[{ label: 'Content' }, { href: '/admin/content/stories', label: 'Studio Stories' }, { label: title }]}>
            <div className="st-editbar">
                <a href="/admin/content/stories" className="st-back"><i className="fa fa-arrow-left" aria-hidden="true"></i> All stories</a>
                <span className="st-editbar__state">
                    <StatusBadge workflow={savedFlow} />
                    {!isNew && <span className="adm-muted adm-small">saved {fmtDate(saved.updated_at, true)}</span>}
                    {(form.dirty || image.length > 0 || removeImage) && <span className="adm-small adm-warn"><i className="fa fa-circle" aria-hidden="true"></i> Unsaved</span>}
                </span>
                <span className="st-editbar__actions">
                    <Button size="sm" variant="ghost" icon={focusMode ? 'fa-compress' : 'fa-expand'} onClick={() => setFocusMode((f) => !f)} aria-pressed={focusMode}>{focusMode ? 'Show sidebar' : 'Focus'}</Button>
                    {!isNew && <Button as="a" size="sm" icon="fa-external-link" href={saved.public_url} target="_blank" rel="noopener noreferrer">{savedFlow === 'published' ? 'View' : 'Preview'}</Button>}
                    <Button size="sm" variant="brand" busy={form.busy} icon={primary[1]} onClick={() => save()}>{primary[0]}</Button>
                </span>
            </div>

            <div className={cx('st-workspace', focusMode && 'is-focus')}>
                <article className="st-sheet" aria-label="Story">
                    <label htmlFor="f-title" className="sr-only">Title</label>
                    <textarea id="f-title" rows={1} className={cx('st-sheet__title', form.error('title') && 'has-error')} value={v.title} maxLength={256}
                        placeholder="Story title" onChange={(e) => form.set('title', e.target.value.replace(/\n/g, ' '))}
                        onInput={(e) => { e.target.style.height = 'auto'; e.target.style.height = `${e.target.scrollHeight}px`; }}
                        aria-invalid={form.error('title') ? 'true' : undefined} />
                    {form.error('title') && <p className="adm-field__error" role="alert">{form.error('title')}</p>}
                    <label htmlFor="f-subtitle" className="sr-only">Summary</label>
                    <textarea id="f-subtitle" rows={2} className={cx('st-sheet__dek', form.error('subtitle') && 'has-error')} value={v.subtitle} maxLength={500}
                        placeholder="A one- or two-sentence summary shown in the story list" onChange={(e) => form.set('subtitle', e.target.value)}
                        aria-invalid={form.error('subtitle') ? 'true' : undefined} />
                    {form.error('subtitle') && <p className="adm-field__error" role="alert">{form.error('subtitle')}</p>}
                    <div className="st-sheet__body">
                        <RichText name="body" value={v.body} onChange={(x) => form.set('body', x)} minHeight={520} onUpload={uploadImage} error={form.error('body')}
                            placeholder="Tell the story. Use headings to split it into sections, and add photos of the work in progress…" />
                        {form.error('body') && <p className="adm-field__error" role="alert">{form.error('body')}</p>}
                    </div>
                </article>

                <aside className="st-side" aria-label="Story settings">
                    <section className="st-panel">
                        <h2 className="st-panel__h">Workflow</h2>
                        <ol className="st-phases" role="radiogroup" aria-label="Workflow phase">
                            {PHASES.map((p) => {
                                const w = WORKFLOW[p];
                                const on = v.workflow === p;
                                return (
                                    <li key={p}>
                                        <button type="button" role="radio" aria-checked={on} className={cx('st-phase', on && 'is-on', savedFlow === p && 'is-saved')} onClick={() => form.set('workflow', p)}>
                                            <i className={`fa ${w.icon}`} aria-hidden="true"></i>
                                            <span><b>{w.label}</b>{savedFlow === p && <small>current</small>}</span>
                                        </button>
                                    </li>
                                );
                            })}
                        </ol>
                        {v.workflow === 'scheduled' && (
                            <Field label="Publish on" name="publish_at" error={form.error('publish_at')} hint="Goes live within a minute of this time (server time).">
                                <input id="f-publish_at" type="datetime-local" className="adm-input" value={v.publish_at} onChange={(e) => form.set('publish_at', e.target.value)} aria-invalid={form.error('publish_at') ? 'true' : undefined} />
                            </Field>
                        )}
                        <p className="adm-muted adm-small">
                            {v.workflow === 'draft' && 'Private. Only admins can open it.'}
                            {v.workflow === 'review' && 'Private, flagged for an editor to read before it goes live.'}
                            {v.workflow === 'scheduled' && 'Private until the time above, then published automatically.'}
                            {v.workflow === 'published' && 'Public on /studio-stories and in search.'}
                            {v.workflow === 'archived' && 'Taken off the site; kept here with its views.'}
                        </p>
                        <Toggle name="featured" checked={v.featured} onChange={(x) => form.set('featured', x)} label="Featured" description="Shown in the featured slot." />
                        <Button variant="brand" busy={form.busy} icon={primary[1]} onClick={() => save()} className="st-block">{primary[0]}</Button>
                    </section>

                    <section className="st-panel">
                        <h2 className="st-panel__h">Author</h2>
                        <label htmlFor="f-author_id" className="sr-only">Author</label>
                        <select id="f-author_id" className="adm-input" value={v.author_id} onChange={(e) => form.set('author_id', e.target.value)} aria-invalid={form.error('author_id') ? 'true' : undefined}>
                            <option value="">Me</option>
                            {authors.map((a) => <option key={a.value} value={String(a.value)}>{a.label}</option>)}
                        </select>
                        {form.error('author_id') && <p className="adm-field__error" role="alert">{form.error('author_id')}</p>}
                    </section>

                    <section className="st-panel">
                        <h2 className="st-panel__h">Clean URL</h2>
                        <SlugField form={form} source="title" prefix="/blog/post/" label="URL slug"
                            onCheck={(s) => axios.get('/admin/api/slug', { params: { table: 'posts', slug: s, except: saved ? saved.id : 0 } }).then((r) => r.data)} />
                    </section>

                    <section className="st-panel">
                        <h2 className="st-panel__h">Tags</h2>
                        <TagInput name="tags" value={v.tags} onChange={(x) => form.set('tags', x)} suggestions={tags} />
                        <p className="adm-muted adm-small">Press Enter after each tag. New tags are created when you save.</p>
                    </section>

                    <section className="st-panel">
                        <h2 className="st-panel__h">Categories</h2>
                        {categories.length === 0 ? <p className="adm-muted">No categories yet. <a href="/admin/blog/taxonomy">Create one</a>.</p>
                            : <ChoiceChips name="category_ids" options={categories.map((c) => ({ value: String(c.value), label: c.label }))} value={v.category_ids} onChange={(x) => form.set('category_ids', x)} />}
                    </section>

                    <section className="st-panel">
                        <h2 className="st-panel__h">Cover image</h2>
                        <DropZone name="image" value={image} onChange={setImage} error={form.error('image')} label="Drop an image or click to choose"
                            existing={saved && saved.image && !removeImage ? [{ id: 'img', ...saved.image }] : []} onRemoveExisting={() => setRemoveImage(true)} />
                    </section>

                    <section className="st-panel">
                        <h2 className="st-panel__h">Search & sharing</h2>
                        <div className="adm-serp" aria-label="Search result preview">
                            <span className="adm-serp__url">artistikcity.com › blog › {v.slug || 'story-url'}</span>
                            <span className="adm-serp__title">{snippetTitle.length > 60 ? `${snippetTitle.slice(0, 59)}…` : snippetTitle}</span>
                            <span className="adm-serp__text">{snippetText.length > 160 ? `${snippetText.slice(0, 159)}…` : snippetText}</span>
                        </div>
                        <TextField form={form} name="meta_title" label="SEO title" max={255} hint="Best under 60 characters." optional />
                        <TextField form={form} name="meta_description" label="Meta description" rows={3} max={500} hint="Best between 120 and 160 characters." optional />
                    </section>

                    {!isNew && (
                        <section className="st-panel st-panel--stats">
                            <h2 className="st-panel__h">Performance</h2>
                            <dl>
                                <div><dt>Views</dt><dd>{num(saved.views)}</dd></div>
                                <div><dt>Read time</dt><dd>{saved.read_minutes || 0} min</dd></div>
                                <div><dt>Published</dt><dd>{saved.published_at ? fmtDate(saved.published_at) : '—'}</dd></div>
                            </dl>
                        </section>
                    )}
                </aside>
            </div>
            {toastNode}
        </AdminLayout>
    );
}

export default function StudioStories() {
    const { mode } = usePage().props;
    return mode === 'edit' ? <Editor /> : <Dashboard />;
}
