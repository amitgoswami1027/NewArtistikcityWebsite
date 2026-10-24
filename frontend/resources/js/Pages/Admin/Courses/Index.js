import React, { useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import { Inertia } from '@inertiajs/inertia';
import axios from 'axios';
import AdminLayout, { PageHeader, useToast, errorText } from '@/Components/Admin/AdminLayout';
import { Button, Badge, DataTable, Pagination, SearchBox, FilterSelect, Segments, Toolbar, EmptyState, useGrid, useConfirm, fmtDate, money } from '@/Components/Admin/kit';

/** Courses (/admin/courses) and workshops (/admin/workshop): one list with search, filters, sorting and paging. */
export default function CoursesIndex() {
    const { kind, mediums = [], counts = {} } = usePage().props;
    const g = useGrid('grid', ['counts']);
    const [toastNode, toast] = useToast();
    const [dialog, confirm] = useConfirm();
    const [busyId, setBusyId] = useState(null);
    const base = kind === 'workshop' ? '/admin/workshop' : '/admin/courses';
    const noun = kind === 'workshop' ? 'workshop' : 'course';
    const Noun = kind === 'workshop' ? 'Workshop' : 'Course';

    const setStatus = (row, live) => {
        setBusyId(row.id);
        axios.post(`/admin/api/courses/${row.id}/status`, { status: live })
            .then((r) => { toast(r.data.message); g.reload(); })
            .catch((e) => toast(errorText(e), 'error')).finally(() => setBusyId(null));
    };
    const duplicate = async (row) => {
        if (!(await confirm({ title: `Duplicate “${row.title}”?`, body: `A hidden copy with the same details, price and curriculum is created, starting in two weeks. You can then change the dates and publish it.`, confirmLabel: 'Duplicate' }))) return;
        setBusyId(row.id);
        axios.post(`/admin/api/courses/${row.id}/duplicate`).then((r) => { toast(r.data.message); Inertia.visit(r.data.editUrl); })
            .catch((e) => { toast(errorText(e), 'error'); setBusyId(null); });
    };
    const remove = async (row) => {
        if (!(await confirm({ title: `Delete “${row.title}”?`, body: `This removes the ${noun}, its photos, modules, lessons and tasks for good. ${Number(row.enrolled) > 0 ? '' : 'Nobody has paid for it yet.'}`, confirmLabel: 'Delete', danger: true }))) return;
        setBusyId(row.id);
        axios.delete(`/admin/api/courses/${row.id}`).then((r) => { toast(r.data.message); g.reload(); })
            .catch((e) => toast(errorText(e), 'error')).finally(() => setBusyId(null));
    };

    const today = new Date().toISOString().slice(0, 10);
    const columns = [
        { key: 'title', label: Noun, sort: 'title', render: (r) => (
            <div className="adm-cell">
                {r.image ? <img src={r.image} alt="" /> : <span className="adm-thumb"><i className="fa fa-picture-o" aria-hidden="true"></i></span>}
                <span className="adm-cell__text">
                    <a href={`${base}/edit/${r.id}`}><b>{r.title}</b></a>
                    <small>{[r.medium, r.instructor && `by ${r.instructor}`].filter(Boolean).join(' · ') || r.slug}</small>
                </span>
            </div>
        ) },
        { key: 'start', label: 'Starts', sort: 'start', hide: 'md', render: (r) => (
            <span>{fmtDate(r.course_start_date)}{r.course_start_date && String(r.course_start_date).slice(0, 10) < today && <><br /><small className="adm-muted">Finished</small></>}</span>
        ) },
        { key: 'price', label: 'Price', sort: 'price', hide: 'lg', render: (r) => (Number(r.is_free_course) === 1 ? <Badge tone="blue">Free</Badge> : <span>{money(r.price_inr, 'inr')}<br /><small className="adm-muted">{money(r.price_usd, 'usd')}</small></span>) },
        { key: 'curriculum', label: 'Curriculum', hide: 'lg', render: (r) => <span className="adm-muted">{r.modules} modules · {r.lessons} lessons</span> },
        { key: 'enrolled', label: 'Students', sort: 'enrolled', align: 'right', render: (r) => <b>{r.enrolled}</b> },
        { key: 'status', label: 'Status', render: (r) => (Number(r.status) === 1 ? <Badge tone="green" dot>Live</Badge> : <Badge tone="grey" dot>Hidden</Badge>) },
        { key: 'actions', label: <span className="sr-only">Actions</span>, align: 'right', render: (r) => (
            <div className="adm-rowactions">
                <Button as="a" size="sm" href={`${base}/edit/${r.id}`} icon="fa-pencil">Edit</Button>
                <Button size="sm" variant="ghost" icon={Number(r.status) === 1 ? 'fa-eye-slash' : 'fa-eye'} busy={busyId === r.id} onClick={() => setStatus(r, Number(r.status) !== 1)}
                    title={Number(r.status) === 1 ? 'Hide from the site' : 'Publish'} aria-label={Number(r.status) === 1 ? `Hide ${r.title}` : `Publish ${r.title}`}></Button>
                <Button size="sm" variant="ghost" icon="fa-files-o" onClick={() => duplicate(r)} title="Duplicate" aria-label={`Duplicate ${r.title}`}></Button>
                <Button as="a" size="sm" variant="ghost" icon="fa-external-link" href={`/course/${r.slug}`} target="_blank" rel="noopener noreferrer" title="View on site" aria-label={`View ${r.title} on the site`}></Button>
                <Button size="sm" variant="ghost" icon="fa-trash-o" onClick={() => remove(r)} title="Delete" aria-label={`Delete ${r.title}`}></Button>
            </div>
        ) },
    ];

    return (
        <AdminLayout title={kind === 'workshop' ? 'Workshops' : 'Courses'}>
            <PageHeader title={kind === 'workshop' ? 'Workshops' : 'Courses'}
                subtitle={kind === 'workshop' ? 'Short, hands-on sessions. Create one, add its lessons and publish it when it is ready.' : 'Multi-week live courses: details, pricing, media and the curriculum students follow.'}
                actions={<Button as="a" variant="brand" icon="fa-plus" href={`${base}/create`}>New {noun}</Button>} />

            <Toolbar end={<Segments label="When" value={g.filter('when')} onChange={(v) => g.go({ when: v })}
                options={[{ value: '', label: 'All', count: counts.all }, { value: 'upcoming', label: 'Upcoming', count: counts.upcoming }, { value: 'past', label: 'Finished', count: counts.past }]} />}>
                <SearchBox value={g.meta.q} onSearch={g.search} placeholder={`Search ${noun}s by title`} />
                <FilterSelect label="Status" value={g.filter('status')} onChange={(v) => g.go({ status: v })} options={[{ value: 'live', label: 'Live' }, { value: 'hidden', label: `Hidden (${counts.hidden || 0})` }]} />
                <FilterSelect label="Medium" value={g.filter('medium')} onChange={(v) => g.go({ medium: v })} options={mediums.map((m) => ({ value: String(m.value), label: m.label }))} />
            </Toolbar>

            <DataTable columns={columns} rows={g.rows} meta={g.meta} loading={g.loading} onSort={g.sortBy} caption={`${Noun}s`}
                empty={g.meta.q || Object.keys(g.meta.filters || {}).length
                    ? <EmptyState icon="fa-search" title={`No ${noun}s match`} action={<Button onClick={() => g.go({ q: '', status: '', medium: '', when: '' })}>Clear filters</Button>}>Try a different search or filter.</EmptyState>
                    : <EmptyState icon="fa-graduation-cap" title={`No ${noun}s yet`} action={<Button as="a" variant="brand" icon="fa-plus" href={`${base}/create`}>Create the first {noun}</Button>}>Add the details, price and lessons; publish when it is ready.</EmptyState>} />
            <Pagination meta={g.meta} onPage={g.setPage} onPer={g.setPer} />
            {dialog}{toastNode}
        </AdminLayout>
    );
}
