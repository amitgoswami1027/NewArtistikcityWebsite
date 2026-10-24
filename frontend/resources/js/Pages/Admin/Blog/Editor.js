import React, { useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import { Inertia } from '@inertiajs/inertia';
import axios from 'axios';
import AdminLayout, { PageHeader, useToast, errorText } from '@/Components/Admin/AdminLayout';
import { Button, Badge, Card, fmtDate } from '@/Components/Admin/kit';
import { useForm, useUnsavedGuard, uploadImage, Field, TextField, Toggle, ChoiceChips, SlugField, TagInput, DropZone, RichText } from '@/Components/Admin/form';

const strip = (html) => String(html || '').replace(/<[^>]+>/g, ' ').replace(/&nbsp;/g, ' ').replace(/\s+/g, ' ').trim();

/** Blog post editor: writing on the left, publishing, media, taxonomy and SEO on the right. */
export default function BlogEditor() {
    const { post, categories = [], tags = [] } = usePage().props;
    const [saved, setSaved] = useState(post);
    const isNew = !saved;
    const [image, setImage] = useState([]);
    const [removeImage, setRemoveImage] = useState(false);
    const [toastNode, toast] = useToast();
    const initial = post ? {
        title: post.title, slug: post.slug, subtitle: post.subtitle, body: post.body, status: post.status, featured: post.featured,
        category_ids: post.category_ids, tags: post.tags, meta_title: post.meta_title, meta_description: post.meta_description,
    } : { title: '', slug: '', subtitle: '', body: '', status: false, featured: false, category_ids: [], tags: [], meta_title: '', meta_description: '' };
    const form = useForm(initial, { title: ['required', 'max:256'], slug: ['slug', 'max:100'], subtitle: ['max:500'], meta_title: ['max:255'], meta_description: ['max:500'] });
    const v = form.values;
    useUnsavedGuard(form.dirty || image.length > 0 || removeImage);

    const save = (publish) => {
        const next = { ...v, status: publish === undefined ? v.status : publish, remove_image: removeImage };
        const extra = {};
        if (next.status) {
            if (!strip(next.subtitle)) extra.subtitle = 'Add a short summary; it shows in the blog list.';
            if (!strip(next.body)) extra.body = 'Write the post before publishing it.';
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
        form.submit(() => axios.post(isNew ? '/admin/api/posts' : `/admin/api/posts/${saved.id}`, fd), { skipValidation: true }).then((r) => {
            toast(r.data.message);
            if (isNew) { Inertia.visit(r.data.editUrl, { replace: true }); return; }
            setSaved(r.data.post);
            form.reset({ ...v, status: r.data.post.status, slug: r.data.post.slug });
            setImage([]); setRemoveImage(false);
        }).catch((e) => toast(e.response && e.response.status === 422 ? e.response.data.message : errorText(e), 'error'));
    };

    const snippetTitle = v.meta_title || v.title || 'Post title';
    const snippetText = v.meta_description || v.subtitle || strip(v.body).slice(0, 160) || 'A short description of the post appears here in search results.';
    const words = strip(v.body) ? strip(v.body).split(' ').length : 0;

    return (
        <AdminLayout title={isNew ? 'New post' : saved.title} crumbs={[{ label: 'Content' }, { href: '/admin/blog/post', label: 'Blog posts' }, { label: isNew ? 'New post' : saved.title }]}>
            <PageHeader eyebrow="Blog" title={isNew ? 'New post' : saved.title || 'Untitled post'}
                subtitle={isNew ? 'Drafts are private. Publish when the post is ready.' : <>{saved.status ? <Badge tone="green" dot>Published {fmtDate(saved.created_at)}</Badge> : <Badge tone="amber" dot>Draft</Badge>} <span className="adm-muted">· last saved {fmtDate(saved.updated_at, true)}</span></>}
                actions={!isNew && <Button as="a" icon="fa-external-link" href={saved.public_url} target="_blank" rel="noopener noreferrer">{saved.status ? 'View post' : 'Preview'}</Button>} />

            <div className="adm-split">
                <div className="adm-stack">
                    <Card>
                        <div className="adm-stack">
                            <Field label="Title" name="title" required error={form.error('title')} max={256} counter={v.title.length}>
                                <input {...form.bind('title')} className="adm-input adm-input--title" placeholder="Write a clear, specific title" />
                            </Field>
                            <TextField form={form} name="subtitle" label="Summary" rows={2} max={500} hint="One or two sentences shown under the title and in the blog list." />
                            <Field label="Post" name="body" error={form.error('body')}>
                                <RichText name="body" value={v.body} onChange={(x) => form.set('body', x)} minHeight={420} onUpload={uploadImage} error={form.error('body')} placeholder="Tell the story. Use headings to split it into sections…" />
                            </Field>
                        </div>
                    </Card>
                    <Card title="Search & sharing" subtitle="How the post looks in Google and when shared. Leave empty to use the title and summary.">
                        <div className="adm-stack">
                            <div className="adm-serp" aria-label="Search result preview">
                                <span className="adm-serp__url">artistikcity.com › blog › {v.slug || 'post-url'}</span>
                                <span className="adm-serp__title">{snippetTitle.length > 60 ? `${snippetTitle.slice(0, 59)}…` : snippetTitle}</span>
                                <span className="adm-serp__text">{snippetText.length > 160 ? `${snippetText.slice(0, 159)}…` : snippetText}</span>
                            </div>
                            <TextField form={form} name="meta_title" label="SEO title" max={255} hint="Best under 60 characters." optional />
                            <TextField form={form} name="meta_description" label="Meta description" rows={3} max={500} hint="Best between 120 and 160 characters." optional />
                        </div>
                    </Card>
                </div>

                <aside className="adm-stack adm-sticky">
                    <Card title="Publish">
                        <div className="adm-stack">
                            <Toggle name="status" checked={v.status} onChange={(x) => form.set('status', x)} label={v.status ? 'Published' : 'Draft'} description={v.status ? 'Visible on the blog.' : 'Only admins can see it.'} />
                            <Toggle name="featured" checked={v.featured} onChange={(x) => form.set('featured', x)} label="Featured" description="Shown in the featured slot on the blog." />
                            <p className="adm-muted adm-small">{words} words · about {Math.max(1, Math.round(words / 200))} min read</p>
                            <div className="adm-inline">
                                {!v.status && <Button onClick={() => save(false)} busy={form.busy} icon="fa-floppy-o">Save draft</Button>}
                                <Button variant="brand" onClick={() => save(true)} busy={form.busy} icon={v.status && saved && saved.status ? 'fa-check' : 'fa-globe'}>{saved && saved.status ? 'Update post' : 'Publish'}</Button>
                            </div>
                            {(form.dirty || image.length > 0 || removeImage) && <p className="adm-small adm-warn"><i className="fa fa-circle" aria-hidden="true"></i> Unsaved changes</p>}
                        </div>
                    </Card>
                    <Card title="Cover image">
                        <DropZone name="image" value={image} onChange={setImage} error={form.error('image')} label="Drop an image or click to choose"
                            existing={saved && saved.image && !removeImage ? [{ id: 'img', ...saved.image }] : []} onRemoveExisting={() => setRemoveImage(true)} />
                    </Card>
                    <Card title="Categories">
                        {categories.length === 0 ? <p className="adm-muted">No categories yet. <a href="/admin/blog/taxonomy">Create one</a>.</p>
                            : <ChoiceChips name="category_ids" options={categories.map((c) => ({ value: String(c.value), label: c.label }))} value={v.category_ids} onChange={(x) => form.set('category_ids', x)} />}
                    </Card>
                    <Card title="Tags">
                        <TagInput name="tags" value={v.tags} onChange={(x) => form.set('tags', x)} suggestions={tags} />
                        <p className="adm-muted adm-small">Press Enter after each tag. New tags are created when you save.</p>
                    </Card>
                    <Card title="Link">
                        <SlugField form={form} source="title" prefix="/blog/post/" label="URL slug"
                            onCheck={(s) => axios.get('/admin/api/slug', { params: { table: 'posts', slug: s, except: saved ? saved.id : 0 } }).then((r) => r.data)} />
                    </Card>
                </aside>
            </div>
            {toastNode}
        </AdminLayout>
    );
}
