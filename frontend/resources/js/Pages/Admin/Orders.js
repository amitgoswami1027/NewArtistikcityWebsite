import React, { useEffect, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import axios from 'axios';
import AdminLayout, { PageHeader, useToast, errorText } from '@/Components/Admin/AdminLayout';
import { Button, Badge, DataTable, Pagination, SearchBox, FilterSelect, Segments, Toolbar, EmptyState, Drawer, Stat, useGrid, fmtDate, money } from '@/Components/Admin/kit';
import { Field } from '@/Components/Admin/form';

const STATUS = {
    0: { key: 'pending', label: 'Pending', tone: 'amber' },
    1: { key: 'paid', label: 'Paid', tone: 'green' },
    2: { key: 'failed', label: 'Failed', tone: 'red' },
    3: { key: 'refunded', label: 'Refunded', tone: 'blue' },
};
const statusOf = (s) => STATUS[Number(s)] || STATUS[0];

/** Course orders: tracking table with filters, order details drawer, status changes and certificates. */
export default function Orders() {
    const { stats = {}, courses = [] } = usePage().props;
    const g = useGrid('grid', ['stats']);
    const [toastNode, toast] = useToast();
    const [openId, setOpenId] = useState(null);

    useEffect(() => {
        const id = new URLSearchParams(window.location.search).get('order');
        if (id) setOpenId(Number(id));
    }, []);

    const columns = [
        { key: 'order', label: 'Order', render: (r) => (
            <span className="adm-cell__text"><button type="button" className="adm-linkbtn" onClick={() => setOpenId(r.id)}>{r.order_number || `#${r.id}`}</button><small>{fmtDate(r.created_at, true)}</small></span>
        ) },
        { key: 'student', label: 'Student', sort: 'student', render: (r) => (
            <span className="adm-cell__text"><b>{r.student || r.billing_name || 'Guest'}</b><small>{r.student_email || r.billing_email}</small></span>
        ) },
        { key: 'course', label: 'Course', sort: 'course', hide: 'md', render: (r) => (r.course ? <span>{r.course}{Number(r.course_type_id) === 2 && <> <Badge>Workshop</Badge></>}</span> : <span className="adm-muted">Deleted course</span>) },
        { key: 'amount', label: 'Amount', sort: 'amount', align: 'right', render: (r) => <b>{money(r.price, r.price_type)}</b> },
        { key: 'method', label: 'Method', hide: 'lg', render: (r) => r.payment_option || <span className="adm-muted">—</span> },
        { key: 'status', label: 'Status', render: (r) => { const s = statusOf(r.payment_status); return <Badge tone={s.tone} dot>{s.label}</Badge>; } },
        { key: 'cert', label: 'Certificate', hide: 'lg', render: (r) => (Number(r.issue_certificate) === 1 ? <Badge tone="green">Issued</Badge> : <span className="adm-muted">—</span>) },
        { key: 'open', label: <span className="sr-only">Details</span>, align: 'right', render: (r) => <Button size="sm" onClick={() => setOpenId(r.id)} aria-label={`Open order ${r.order_number || r.id}`}>Details</Button> },
    ];
    const anyFilter = g.meta.q || Object.keys(g.meta.filters || {}).length > 0;

    return (
        <AdminLayout title="Course orders">
            <PageHeader title="Course orders" subtitle="Every course and workshop purchase. Open an order to see the payment, change its status or issue the certificate."
                actions={<Button as="a" icon="fa-download" href={g.exportUrl('/admin/orders/export')}>Export CSV</Button>} />
            <div className="adm-stats">
                <Stat icon="fa-inr" label="Revenue (INR, all time)" value={money(stats.revenueInr, 'inr')} />
                <Stat icon="fa-usd" label="Revenue (USD, all time)" value={money(stats.revenueUsd, 'usd')} />
                <Stat icon="fa-calendar" label={`This month · ${stats.monthOrders || 0} paid orders`} value={money(stats.monthInr, 'inr')} />
                <Stat icon="fa-certificate" label="Paid, certificate not issued" value={stats.certificatesDue || 0} />
            </div>
            <Toolbar end={<Segments label="Status" value={g.filter('status')} onChange={(v) => g.go({ status: v })}
                options={[{ value: '', label: 'All', count: stats.all }, { value: 'paid', label: 'Paid', count: stats.paid }, { value: 'pending', label: 'Pending', count: stats.pending }, { value: 'failed', label: 'Failed', count: stats.failed }, { value: 'refunded', label: 'Refunded', count: stats.refunded }]} />}>
                <SearchBox value={g.meta.q} onSearch={g.search} placeholder="Order no., student, e-mail or payment id" />
            </Toolbar>
            <Toolbar>
                <FilterSelect label="Course" value={g.filter('course')} onChange={(v) => g.go({ course: v })} options={courses.map((c) => ({ value: String(c.value), label: c.label }))} />
                <FilterSelect label="Method" value={g.filter('method')} onChange={(v) => g.go({ method: v })} options={[{ value: 'razorpay', label: 'Razorpay' }, { value: 'paypal', label: 'PayPal' }, { value: 'manual', label: 'Manual' }, { value: 'free', label: 'Free' }]} />
                <FilterSelect label="Currency" value={g.filter('currency')} onChange={(v) => g.go({ currency: v })} options={[{ value: 'inr', label: 'INR' }, { value: 'usd', label: 'USD' }]} />
                <label className="adm-filter"><span>From</span><input type="date" value={g.filter('from')} onChange={(e) => g.go({ from: e.target.value })} aria-label="From date" /></label>
                <label className="adm-filter"><span>To</span><input type="date" value={g.filter('to')} onChange={(e) => g.go({ to: e.target.value })} aria-label="To date" /></label>
                {anyFilter && <Button variant="ghost" size="sm" icon="fa-times" onClick={() => g.go({ q: '', status: '', course: '', method: '', currency: '', from: '', to: '' })}>Clear all</Button>}
            </Toolbar>
            <DataTable columns={columns} rows={g.rows} meta={g.meta} loading={g.loading} onSort={g.sortBy} caption="Orders" onRowClick={(r) => setOpenId(r.id)}
                empty={anyFilter ? <EmptyState icon="fa-search" title="No orders match these filters" action={<Button onClick={() => g.go({ q: '', status: '', course: '', method: '', currency: '', from: '', to: '' })}>Clear filters</Button>} />
                    : <EmptyState icon="fa-shopping-cart" title="No orders yet">Orders appear here as soon as a student starts checkout.</EmptyState>} />
            <Pagination meta={g.meta} onPage={g.setPage} onPer={g.setPer} />
            {openId && <OrderDrawer id={openId} onClose={() => setOpenId(null)} toast={toast} onChanged={() => g.reload()} />}
            {toastNode}
        </AdminLayout>
    );
}

function OrderDrawer({ id, onClose, toast, onChanged }) {
    const [data, setData] = useState(null);
    const [error, setError] = useState(null);
    const [change, setChange] = useState(null); // target status key
    const [note, setNote] = useState('');
    const [busy, setBusy] = useState(false);
    const [fieldErr, setFieldErr] = useState(null);
    useEffect(() => {
        setData(null); setError(null);
        axios.get(`/admin/api/orders/${id}`).then((r) => setData(r.data)).catch((e) => setError(errorText(e)));
    }, [id]);

    const o = data && data.order;
    const s = o ? statusOf(o.payment_status) : null;
    const submit = () => {
        setBusy(true); setFieldErr(null);
        axios.post(`/admin/api/orders/${id}/status`, { status: change, note }).then((r) => {
            setData(r.data); setChange(null); setNote(''); toast(r.data.message); onChanged();
        }).catch((e) => {
            if (e.response && e.response.status === 422) setFieldErr(e.response.data.message);
            else toast(errorText(e), 'error');
        }).finally(() => setBusy(false));
    };
    const issue = () => {
        setBusy(true);
        axios.post(`/admin/api/orders/${id}/certificate`).then((r) => { setData(r.data); toast(r.data.message); onChanged(); })
            .catch((e) => toast(errorText(e), 'error')).finally(() => setBusy(false));
    };
    const actions = o ? Object.values(STATUS).filter((x) => x.key !== s.key) : [];
    const verb = { paid: 'Mark as paid', pending: 'Mark as pending', failed: 'Mark as failed', refunded: 'Mark as refunded' };

    return (
        <Drawer open size="lg" title={o ? `Order ${o.order_number || `#${o.id}`}` : 'Order'} subtitle={o ? `Placed ${fmtDate(o.created_at, true)}` : undefined} onClose={onClose}
            footer={o && <>
                {Number(o.payment_status) === 1 && Number(o.issue_certificate) !== 1 && <Button variant="dark" icon="fa-certificate" busy={busy && !change} onClick={issue}>Issue certificate</Button>}
                <Button onClick={onClose}>Close</Button>
            </>}>
            {error && <div className="adm-alert is-error" role="alert"><i className="fa fa-exclamation-circle" aria-hidden="true"></i>{error}</div>}
            {!data && !error && <div className="adm-stack" aria-busy="true">{[0, 1, 2, 3, 4].map((i) => <span key={i} className="adm-skel" style={{ height: 16, width: `${90 - i * 12}%` }}></span>)}</div>}
            {o && (
                <div className="adm-stack">
                    <div className="adm-inline"><Badge tone={s.tone} dot>{s.label}</Badge><b className="adm-amount">{money(o.price, o.price_type)}</b><span className="adm-muted">{o.payment_option || 'No payment yet'}</span></div>

                    <h3 className="adm-sub">Change status</h3>
                    <div className="adm-inline">
                        {actions.map((a) => <Button key={a.key} size="sm" variant={change === a.key ? 'dark' : 'line'} onClick={() => { setChange(a.key); setFieldErr(null); }}>{verb[a.key]}</Button>)}
                    </div>
                    {change && (
                        <div className="adm-task is-edit">
                            {change === 'paid' && <p className="adm-muted">Use this for payments received outside the website (for example a bank transfer). The student gets access to the course straight away.</p>}
                            {change === 'refunded' && <p className="adm-muted">Record a refund made in Razorpay or PayPal. The student loses access to the course.</p>}
                            <Field label={change === 'paid' || change === 'refunded' ? 'Note (required)' : 'Note'} name="note" error={fieldErr}>
                                <textarea id="f-note" className="adm-input" rows={2} value={note} onChange={(e) => setNote(e.target.value)} placeholder={change === 'paid' ? 'e.g. NEFT ref 1234, received 24 Oct' : 'Reason or reference'} />
                            </Field>
                            <div className="adm-inline">
                                <Button variant={change === 'refunded' || change === 'failed' ? 'danger' : 'brand'} size="sm" busy={busy} onClick={submit} icon="fa-check">{verb[change]}</Button>
                                <Button size="sm" onClick={() => setChange(null)}>Cancel</Button>
                            </div>
                        </div>
                    )}

                    <h3 className="adm-sub">Student</h3>
                    {data.student ? (
                        <dl className="adm-dl">
                            <dt>Name</dt><dd>{data.student.name}</dd>
                            <dt>E-mail</dt><dd><a href={`mailto:${data.student.email}`}>{data.student.email}</a></dd>
                            {data.student.phone_number && <><dt>Phone</dt><dd>{data.student.phone_number}</dd></>}
                            <dt>Joined</dt><dd>{fmtDate(data.student.created_at)}</dd>
                        </dl>
                    ) : <p className="adm-muted">No student account linked.</p>}

                    <h3 className="adm-sub">Course</h3>
                    {data.course ? (
                        <dl className="adm-dl">
                            <dt>{Number(data.course.course_type_id) === 2 ? 'Workshop' : 'Course'}</dt>
                            <dd><a href={`/admin/${Number(data.course.course_type_id) === 2 ? 'workshop' : 'courses'}/edit/${data.course.id}`}>{data.course.title}</a></dd>
                            <dt>Starts</dt><dd>{fmtDate(data.course.course_start_date)}</dd>
                            <dt>Progress</dt><dd>{data.enrollment ? `${data.enrollment.status} · ${data.enrollment.completed} lessons done · ${data.enrollment.progress_percentage}%` : 'Not enrolled'}</dd>
                            <dt>Certificate</dt><dd>{data.certificate ? <a href={data.certificate.certificate_path} target="_blank" rel="noopener noreferrer">{data.certificate.certificate_number}</a> : 'Not issued'}</dd>
                        </dl>
                    ) : <p className="adm-muted">This course was deleted.</p>}

                    <h3 className="adm-sub">Billing</h3>
                    <dl className="adm-dl">
                        <dt>Name</dt><dd>{o.billing_name || '—'}</dd>
                        <dt>E-mail</dt><dd>{o.billing_email || '—'}</dd>
                        <dt>Address</dt><dd>{[o.billing_address, o.billing_city, o.billing_state, o.billing_zipcode, o.billing_country].filter(Boolean).join(', ') || '—'}</dd>
                    </dl>

                    <h3 className="adm-sub">Payment</h3>
                    <dl className="adm-dl">
                        <dt>Gateway order</dt><dd>{o.gateway_ref || '—'}</dd>
                        <dt>Payment id</dt><dd>{o.payment_id || '—'}</dd>
                        <dt>Last update</dt><dd>{fmtDate(o.updated_at, true)}</dd>
                    </dl>
                    {o.payment_details && typeof o.payment_details === 'object' && (
                        <details className="adm-details"><summary>Gateway response</summary><pre>{JSON.stringify(o.payment_details, null, 2)}</pre></details>
                    )}

                    {data.history && data.history.length > 0 && (
                        <>
                            <h3 className="adm-sub">History</h3>
                            <ol className="adm-timeline">
                                {data.history.map((h, i) => (
                                    <li key={i}><b>{h.action}</b>{h.details && <span> — {h.details}</span>}<small>{h.admin_name || 'System'} · {fmtDate(h.created_at, true)}</small></li>
                                ))}
                            </ol>
                        </>
                    )}
                </div>
            )}
        </Drawer>
    );
}
