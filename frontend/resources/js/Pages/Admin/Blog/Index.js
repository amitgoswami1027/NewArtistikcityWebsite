import React from 'react';
import { usePage } from '@inertiajs/inertia-react';
import axios from 'axios';
import AdminLayout, { PageHeader, useToast, errorText } from '@/Components/Admin/AdminLayout';
import { Button, Badge, DataTable, Pagination, SearchBox, FilterSelect, Segments, Toolbar, EmptyState, useGrid, useConfirm, fmtDate } from '@/Components/Admin/kit';

/** Blog posts (/admin/blog/post): drafts and published posts with search, filters and quick publish. */
export default function BlogIndex() {
    const { counts = {}, categories = [] } = usePage().props;
    const g = useGrid('grid', ['counts']);
    const [toastNode, toast] = useToast();
    const [dialog, confirm] = useConfirm();

    const setStatus = (r, publish) => axios.post(`/admin/api/posts/${r.id}/status`, { status: publish })
        .then((x) => { toast(x.data.message); g.reload(); }).catch((e) => toast(errorText(e), 'error'));
    const remove = async (r) => {
        if (!(await confirm({ title: `Delete “${r.title}”?`, body: 'The post and its link are removed for good.', confirmLabel: 'Delete', danger: true }))) return;
        axios.delete(`/admin/api/posts/${r.id}`).then((x) => { toast(x.data.message); g.reload(); }).catch((e) => toast(errorText(e), 'error'));
    };

    const columns = [
        { key: 'title', label: 'Post', sort: 'title', render: (r) => (
            <div className="adm-cell">
                {r.image_url ? <img src={r.image_url} alt="" /> : <span className="adm-thumb"><i className="fa fa-file-text-o" aria-hidden="true"></i></span>}
                <span className="adm-cell__text">
                    <a href={`/admin/blog/post/${r.id}/edit`}><b>{r.title}</b></a>
                    <small>{r.categories || 'No category'}{Number(r.featured) === 1 ? ' · Featured' : ''}</small>
                </span>
            </div>
        ) },
        { key: 'author', label: 'Author', hide: 'lg', render: (r) => r.author || <span className="adm-muted">—</span> },
        { key: 'date', label: 'Date', sort: 'date', hide: 'md', render: (r) => fmtDate(r.created_at) },
        { key: 'updated', label: 'Updated', sort: 'updated', hide: 'lg', render: (r) => fmtDate(r.updated_at, true) },
        { key: 'status', label: 'Status', render: (r) => (Number(r.status) === 1 ? <Badge tone="green" dot>Published</Badge> : <Badge tone="amber" dot>Draft</Badge>) },
        { key: 'actions', label: <span className="sr-only">Actions</span>, align: 'right', render: (r) => (
            <div className="adm-rowactions">
                <Button as="a" size="sm" icon="fa-pencil" href={`/admin/blog/post/${r.id}/edit`}>Edit</Button>
                {Number(r.status) === 1
                    ? <Button size="sm" variant="ghost" icon="fa-eye-slash" onClick={() => setStatus(r, false)} aria-label={`Unpublish ${r.title}`} title="Move to drafts" />
                    : <Button size="sm" variant="ghost" icon="fa-globe" onClick={() => setStatus(r, true)} aria-label={`Publish ${r.title}`} title="Publish" />}
                <Button as="a" size="sm" variant="ghost" icon="fa-external-link" href={`/blog/post/${r.slug}`} target="_blank" rel="noopener noreferrer" aria-label={`View ${r.title}`} title={Number(r.status) === 1 ? 'View on site' : 'Preview'} />
                <Button size="sm" variant="ghost" icon="fa-trash-o" onClick={() => remove(r)} aria-label={`Delete ${r.title}`} title="Delete" />
            </div>
        ) },
    ];

    return (
        <AdminLayout title="Blog posts">
            <PageHeader title="Blog posts" subtitle="Write, preview and publish Studio articles. Drafts stay private until you publish them."
                actions={<>
                    <Button as="a" icon="fa-bookmark-o" href="/admin/blog/taxonomy">Categories & tags</Button>
                    <Button as="a" variant="brand" icon="fa-plus" href="/admin/blog/post/create">New post</Button>
                </>} />
            <Toolbar end={<Segments label="Status" value={g.filter('status')} onChange={(v) => g.go({ status: v })}
                options={[{ value: '', label: 'All', count: counts.all }, { value: 'published', label: 'Published', count: counts.published }, { value: 'draft', label: 'Drafts', count: counts.draft }]} />}>
                <SearchBox value={g.meta.q} onSearch={g.search} placeholder="Search posts" />
                <FilterSelect label="Category" value={g.filter('category')} onChange={(v) => g.go({ category: v })} options={categories.map((c) => ({ value: String(c.value), label: c.label }))} />
                <FilterSelect label="Featured" value={g.filter('featured')} onChange={(v) => g.go({ featured: v })} options={[{ value: '1', label: 'Featured only' }]} allLabel="Any" />
            </Toolbar>
            <DataTable columns={columns} rows={g.rows} meta={g.meta} loading={g.loading} onSort={g.sortBy} caption="Blog posts"
                empty={<EmptyState icon="fa-pencil-square-o" title={g.meta.q ? 'No posts match' : 'No posts yet'} action={<Button as="a" variant="brand" icon="fa-plus" href="/admin/blog/post/create">Write a post</Button>} />} />
            <Pagination meta={g.meta} onPage={g.setPage} onPer={g.setPer} />
            {dialog}{toastNode}
        </AdminLayout>
    );
}
