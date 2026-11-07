import React, { useEffect, useMemo, useRef, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import axios from 'axios';
import AdminLayout, { PageHeader, useToast, errorText } from '@/Components/Admin/AdminLayout';
import { Card, Button, Badge, DataTable, Segments, Toolbar, SearchBox, FilterSelect, EmptyState, Drawer, Stat, useConfirm, fmtDate, cx } from '@/Components/Admin/kit';
import { Field, Toggle } from '@/Components/Admin/form';
import { STATUS, inr, pct, num, has, PaymentSettings, TemplateThumb, SeriesChart } from '@/Components/Admin/Landing/parts';

/*
 * Admin > Landing Page Management. One section for everything about landing pages: overview, all pages, templates and
 * sections, payments and checkout defaults, coupons, orders and transactions, analytics, media and settings. Each tab
 * has its own address (/admin/landing/{tab}); a page itself is edited in Admin/Landing/Editor.
 */

export const TABS = [
    { key: 'overview', label: 'Overview', icon: 'fa-th-large', cap: 'landing' },
    { key: 'pages', label: 'All landing pages', icon: 'fa-files-o', cap: 'landing' },
    { key: 'templates', label: 'Templates & sections', icon: 'fa-columns', cap: 'landing' },
    { key: 'payments', label: 'Payments & checkout', icon: 'fa-credit-card', cap: 'landing.payments' },
    { key: 'coupons', label: 'Coupons & promotions', icon: 'fa-ticket', cap: 'landing.pricing' },
    { key: 'orders', label: 'Orders & transactions', icon: 'fa-shopping-cart', cap: 'landing.orders' },
    { key: 'analytics', label: 'Analytics & reports', icon: 'fa-line-chart', cap: 'landing' },
    { key: 'media', label: 'Media library', icon: 'fa-picture-o', cap: 'landing' },
    { key: 'settings', label: 'Settings', icon: 'fa-cog', cap: 'landing' },
];

export function LandingTabs({ current, caps }) {
    return (
        <nav className="lpa-tabs" aria-label="Landing Page Management">
            {TABS.filter((t) => has(caps, t.cap)).map((t) => (
                <a key={t.key} href={t.key === 'overview' ? '/admin/landing' : `/admin/landing/${t.key}`} className={cx(current === t.key && 'is-on')} aria-current={current === t.key ? 'page' : undefined}>
                    <i className={`fa ${t.icon}`} aria-hidden="true"></i>{t.label}
                </a>
            ))}
        </nav>
    );
}

const RANGES = [{ value: 'today', label: 'Today' }, { value: 'yesterday', label: 'Yesterday' }, { value: '7d', label: '7 days' }, { value: '30d', label: '30 days' },
    { value: '90d', label: '90 days' }, { value: 'custom', label: 'Custom' }];

function useReport(lookups) {
    const [f, setF] = useState({ range: '30d', from: '', to: '', page: '', course: '', artist: '', campaign: '' });
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(false);
    useEffect(() => {
        if (f.range === 'custom' && (!f.from || !f.to)) return;
        setLoading(true);
        axios.get('/admin/api/landing/overview', { params: f }).then((r) => setData(r.data)).finally(() => setLoading(false));
    }, [f]);
    const filters = (
        <div className="lpa-filters">
            <Segments value={f.range} options={RANGES} onChange={(v) => setF({ ...f, range: v })} label="Date range" />
            {f.range === 'custom' && (
                <span className="lpa-dates">
                    <label>From <input type="date" className="adm-input adm-input--sm" value={f.from} onChange={(e) => setF({ ...f, from: e.target.value })} /></label>
                    <label>To <input type="date" className="adm-input adm-input--sm" value={f.to} onChange={(e) => setF({ ...f, to: e.target.value })} /></label>
                </span>
            )}
            <FilterSelect label="Page" value={f.page} onChange={(v) => setF({ ...f, page: v })} options={lookups.pages.map((p) => ({ value: p.id, label: p.name }))} allLabel="All pages" />
            <FilterSelect label="Course" value={f.course} onChange={(v) => setF({ ...f, course: v })} options={lookups.courses.map((c) => ({ value: c.id, label: c.title }))} allLabel="All courses" />
            <FilterSelect label="Artist" value={f.artist} onChange={(v) => setF({ ...f, artist: v })} options={lookups.artists.map((a) => ({ value: a.id, label: a.name }))} allLabel="All artists" />
            <FilterSelect label="Campaign" value={f.campaign} onChange={(v) => setF({ ...f, campaign: v })} options={lookups.campaigns.map((c) => ({ value: c.campaign, label: c.campaign }))} allLabel="All campaigns" />
        </div>
    );
    return { data, loading, filters };
}

/* ------------------------------------------------------------------------------------------------ overview */

function Overview({ lookups, caps }) {
    const { data, loading, filters } = useReport(lookups);
    const k = data && data.kpis;
    const money = data && !data.moneyHidden;
    return (
        <div className="adm-stack" data-testid="lp-overview">
            {filters}
            {!data ? <Card><p className="adm-muted">Loading…</p></Card> : (
                <>
                    <div className="adm-stats lpa-stats" aria-busy={loading}>
                        <Stat label="Landing pages" value={num(Object.values(data.pages).reduce((a, b) => a + b, 0))} icon="fa-files-o" />
                        <Stat label="Published" value={num(data.pages.PUBLISHED)} icon="fa-globe" tone="green" />
                        <Stat label="Drafts" value={num(data.pages.DRAFT)} icon="fa-pencil" />
                        <Stat label="Scheduled" value={num(data.pages.SCHEDULED)} icon="fa-clock-o" tone="blue" />
                    </div>
                    <div className="adm-stats lpa-stats">
                        <Stat label="Page views" value={num(k.views)} icon="fa-eye" />
                        <Stat label="Unique visitors" value={num(k.visitors)} icon="fa-user" />
                        <Stat label="Enrolments" value={num(k.enrollments)} icon="fa-graduation-cap" tone="green" />
                        <Stat label="Conversion" value={pct(k.conversion)} icon="fa-percent" />
                        {money && <Stat label="Revenue (INR)" value={inr(k.revenueInr)} icon="fa-inr" tone="green" />}
                        {money && Number(k.revenueUsd) > 0 && <Stat label="Revenue (USD)" value={`$${num(k.revenueUsd)}`} icon="fa-usd" />}
                        <Stat label="Successful payments" value={num(k.successful)} icon="fa-check" />
                        <Stat label="Failed payments" value={num(k.failed)} icon="fa-times" tone={k.failed ? 'red' : undefined} />
                        {money && <Stat label="Average order" value={inr(k.aovInr)} icon="fa-shopping-bag" />}
                    </div>
                    <Card title="Trend" subtitle={`${data.range.from} to ${data.range.to}`}>
                        <div className="lpa-charts">
                            <SeriesChart series={data.series} keyName="visitors" label="Visitors" />
                            <SeriesChart series={data.series} keyName="enrollments" label="Enrolments" />
                            {money && <SeriesChart series={data.series} keyName="revenue" label="Revenue" format={inr} />}
                        </div>
                    </Card>
                    <div className="adm-grid2">
                        <Card title="Best-performing pages" flush>
                            <DataTable rows={data.byPage} columns={[
                                { key: 'name', label: 'Page', render: (r) => <a href={`/admin/landing/pages/${r.id}`}>{r.name}</a> },
                                { key: 'visitors', label: 'Visitors', align: 'right', render: (r) => num(r.visitors) },
                                { key: 'enrollments', label: 'Enrolled', align: 'right' },
                                { key: 'conversion', label: 'Conv.', align: 'right', render: (r) => pct(r.conversion) },
                                ...(money ? [{ key: 'revenue', label: 'Revenue', align: 'right', render: (r) => inr(r.revenue) }] : []),
                            ]} empty={<EmptyState icon="fa-files-o" title="No pages yet" />} />
                        </Card>
                        <Card title="Best-performing courses" flush>
                            <DataTable rows={data.byCourse} rowKey="id" columns={[
                                { key: 'course', label: 'Course' }, { key: 'artist', label: 'Artist' },
                                { key: 'enrollments', label: 'Enrolled', align: 'right' },
                                ...(money ? [{ key: 'revenue', label: 'Revenue', align: 'right', render: (r) => inr(r.revenue) }] : []),
                            ]} empty={<EmptyState icon="fa-graduation-cap" title="No enrolments in this period" />} />
                        </Card>
                    </div>
                    <Card title="Top campaigns" subtitle="From UTM tags, coupon campaigns and page campaigns." flush>
                        <DataTable rows={data.byCampaign} rowKey="campaign" columns={[
                            { key: 'campaign', label: 'Campaign' }, { key: 'enrollments', label: 'Enrolled', align: 'right' },
                            ...(money ? [{ key: 'revenue', label: 'Revenue', align: 'right', render: (r) => inr(r.revenue) },
                                { key: 'discounts', label: 'Discounts given', align: 'right', render: (r) => inr(r.discounts) }] : []),
                        ]} empty={<EmptyState icon="fa-bullhorn" title="No campaign sales in this period" />} />
                    </Card>
                </>
            )}
        </div>
    );
}

/* ------------------------------------------------------------------------------------------------ analytics */

const METHOD_LABEL = { upi: 'UPI', card: 'Cards', netbanking: 'Net banking', wallet: 'Wallets', emi: 'EMI', paypal: 'PayPal', free: 'Free', test: 'Test', unknown: 'Not recorded' };

function Analytics({ lookups }) {
    const { data, loading, filters } = useReport(lookups);
    const k = data && data.kpis;
    const money = data && !data.moneyHidden;
    const funnel = k ? [['Visitors', k.visitors], ['Clicked Enroll', k.ctaClicks], ['Opened checkout', k.checkouts], ['Enrolled', k.enrollments]] : [];
    const top = funnel.length ? Math.max(1, funnel[0][1]) : 1;
    return (
        <div className="adm-stack" data-testid="lp-analytics">
            {filters}
            {!data ? <Card><p className="adm-muted">Loading…</p></Card> : (
                <>
                    <Card title="Funnel" subtitle="Unique visitors at each step (bots and staff are not counted).">
                        <ol className="lpa-funnel" aria-busy={loading}>
                            {funnel.map(([l, v], i) => (
                                <li key={l}><span className="lpa-funnel__label">{l}</span>
                                    <span className="lpa-funnel__bar"><span style={{ width: `${Math.max(2, (v / top) * 100)}%` }}></span></span>
                                    <b>{num(v)}</b>{i > 0 && <small>{pct(funnel[i - 1][1] ? (v * 100) / funnel[i - 1][1] : 0)} of previous</small>}</li>
                            ))}
                        </ol>
                    </Card>
                    <div className="adm-stats lpa-stats">
                        <Stat label="Checkout started" value={num(k.checkouts)} icon="fa-shopping-cart" />
                        <Stat label="Abandoned checkouts" value={`${num(k.abandoned)} · ${pct(k.abandonRate)}`} icon="fa-sign-out" tone={k.abandonRate > 60 ? 'amber' : undefined} />
                        <Stat label="Payment success rate" value={pct(k.paymentSuccessRate)} icon="fa-check-circle" tone="green" />
                        <Stat label="Pending payments" value={num(k.pending)} icon="fa-hourglass-half" />
                        <Stat label="Refunds" value={`${num(k.refunded)}${money ? ` · ${inr(k.refundsInr)}` : ''}`} icon="fa-undo" />
                    </div>
                    <Card title="Payment methods" subtitle="Picked = buyers who chose it at checkout. Conversion = paid ÷ picked." flush>
                        <DataTable rows={data.byMethod} rowKey="method" columns={[
                            { key: 'method', label: 'Method', render: (r) => METHOD_LABEL[r.method] || r.method },
                            { key: 'picked', label: 'Picked', align: 'right' }, { key: 'started', label: 'Payment opened', align: 'right' },
                            { key: 'paid', label: 'Paid', align: 'right' }, { key: 'failed', label: 'Failed', align: 'right' },
                            { key: 'successRate', label: 'Success rate', align: 'right', render: (r) => pct(r.successRate) },
                            { key: 'conversion', label: 'Conversion', align: 'right', render: (r) => pct(r.conversion) },
                            ...(money ? [{ key: 'revenue', label: 'Revenue', align: 'right', render: (r) => inr(r.revenue) }] : []),
                        ]} empty={<EmptyState icon="fa-credit-card" title="No payments in this period" />} />
                    </Card>
                    <div className="adm-grid2">
                        <Card title="Payment providers" flush>
                            <DataTable rows={data.byProvider} rowKey="provider" columns={[
                                { key: 'provider', label: 'Provider' }, { key: 'paid', label: 'Paid', align: 'right' }, { key: 'failed', label: 'Failed', align: 'right' },
                                { key: 'pending', label: 'Pending', align: 'right' }, { key: 'successRate', label: 'Success', align: 'right', render: (r) => pct(r.successRate) },
                            ]} empty={<EmptyState icon="fa-plug" title="No payments yet" />} />
                        </Card>
                        <Card title="Devices" flush>
                            <DataTable rows={data.byDevice} rowKey="device" columns={[
                                { key: 'device', label: 'Device' }, { key: 'visitors', label: 'Visitors', align: 'right' }, { key: 'buyers', label: 'Bought', align: 'right' },
                                { key: 'c', label: 'Conversion', align: 'right', render: (r) => pct(r.visitors ? (r.buyers * 100) / r.visitors : 0) },
                            ]} empty={<EmptyState icon="fa-mobile" title="No visits yet" />} />
                        </Card>
                    </div>
                </>
            )}
        </div>
    );
}

/* -------------------------------------------------------------------------------------------------- pages */

function Pages({ caps, toast }) {
    const [q, setQ] = useState({ q: '', status: '', sort: 'updated', dir: 'desc', page: 1 });
    const [data, setData] = useState({ rows: [], meta: {}, counts: {} });
    const [loading, setLoading] = useState(true);
    const [dialog, confirm] = useConfirm();
    const load = () => { setLoading(true); axios.get('/admin/api/landing/pages', { params: q }).then((r) => setData(r.data)).finally(() => setLoading(false)); };
    useEffect(load, [q]); // eslint-disable-line react-hooks/exhaustive-deps
    const act = async (r, action) => {
        if (action === 'delete') {
            if (!(await confirm({ title: `Delete "${r.name}"?`, body: 'This cannot be undone. Pages people bought through can only be archived.', confirmLabel: 'Delete', danger: true }))) return;
            axios.delete(`/admin/api/landing/pages/${r.id}`).then((x) => { toast(x.data.message); load(); }).catch((e) => toast(errorText(e), 'error'));
            return;
        }
        if (action === 'unpublish' && !(await confirm({ title: `Unpublish "${r.name}"?`, body: r.kind === 'SITE' ? `The original page comes back at ${r.publicUrl}.` : r.takesOverCourse ? 'The regular course page comes back at this address.' : 'The address stops working until you publish again.', confirmLabel: 'Unpublish' }))) return;
        axios.post(`/admin/api/landing/pages/${r.id}/${action}`).then((x) => {
            toast(x.data.message);
            if (action === 'duplicate') window.location.assign(`/admin/landing/pages/${x.data.id}`); else load();
        }).catch((e) => toast(errorText(e), 'error'));
    };
    const pub = has(caps, 'landing.publish');
    const c = data.counts || {};
    return (
        <Card flush>
            <Toolbar end={pub && <Button as="a" href="/admin/landing/pages/new" variant="brand" icon="fa-plus" data-testid="lp-create">Create landing page</Button>}>
                <SearchBox value={q.q} onSearch={(v) => setQ({ ...q, q: v, page: 1 })} placeholder="Search name, address, course, campaign" label="Search landing pages" />
                <Segments value={q.status} onChange={(v) => setQ({ ...q, status: v, page: 1 })} label="Status" options={[
                    { value: '', label: 'Active' }, { value: 'PUBLISHED', label: 'Published', count: c.PUBLISHED || 0 }, { value: 'DRAFT', label: 'Drafts', count: c.DRAFT || 0 },
                    { value: 'SCHEDULED', label: 'Scheduled', count: c.SCHEDULED || 0 }, { value: 'ARCHIVED', label: 'Archived', count: c.ARCHIVED || 0 }]} />
            </Toolbar>
            <DataTable loading={loading} rows={data.rows} meta={{ sort: q.sort, dir: q.dir }} onSort={(sort) => setQ({ ...q, sort, dir: q.sort === sort && q.dir === 'desc' ? 'asc' : 'desc' })}
                       caption="Landing pages" columns={[
                { key: 'name', label: 'Landing page', sort: 'name', render: (r) => (
                    <div className="adm-cell"><a href={`/admin/landing/pages/${r.id}`} className="adm-cell__text"><b>{r.name}</b></a>
                        <small className="adm-muted">{r.publicUrl || `/course/${r.slug}`}{r.campaign ? ` · ${r.campaign}` : ''}</small></div>) },
                { key: 'course', label: 'Course · artist', sort: 'course', hide: 'md', render: (r) => <span>{r.course}<br /><small className="adm-muted">{r.artist}</small></span> },
                { key: 'template', label: 'Template', hide: 'lg' },
                { key: 'status', label: 'Status', sort: 'status', render: (r) => (
                    <span><Badge tone={STATUS[r.status].tone} dot>{STATUS[r.status].label}</Badge>{r.status === 'SCHEDULED' && <><br /><small className="adm-muted">{fmtDate(r.publish_at, true)}</small></>}</span>) },
                { key: 'updated_at', label: 'Updated', sort: 'updated', hide: 'md', render: (r) => <span>{fmtDate(r.updated_at)}<br /><small className="adm-muted">Created {fmtDate(r.created_at)}</small></span> },
                { key: 'published_at', label: 'Published', sort: 'published', hide: 'lg', render: (r) => (r.published_at ? fmtDate(r.published_at) : '—') },
                { key: 'views', label: 'Views', align: 'right', render: (r) => num(r.views) },
                { key: 'enrollments', label: 'Enrolled', align: 'right', render: (r) => <span>{num(r.enrollments)}<br /><small className="adm-muted">{pct(r.conversion)}</small></span> },
                { key: 'revenue', label: 'Revenue', align: 'right', hide: 'md', render: (r) => (r.revenue === null ? '—' : inr(r.revenue)) },
                { key: 'actions', label: 'Actions', render: (r) => (
                    <div className="adm-rowactions">
                        <Button as="a" size="sm" href={`/admin/landing/pages/${r.id}`} icon="fa-pencil">Edit</Button>
                        <Button as="a" size="sm" variant="ghost" href={`/admin/landing/preview/${r.id}`} target="_blank" rel="noopener" icon="fa-eye" aria-label={`Preview ${r.name}`} />
                        {pub && <PageMenu r={r} act={act} />}
                    </div>) },
            ]} empty={<EmptyState icon="fa-files-o" title="No landing pages here" action={pub && <Button as="a" href="/admin/landing/pages/new" variant="brand" icon="fa-plus">Create the first one</Button>}>
                A landing page sells one course with its own design, price, payment methods and analytics.</EmptyState>} />
            {data.meta.pages > 1 && (
                <div className="adm-pager"><span className="adm-pager__info">Page {data.meta.page} of {data.meta.pages} · {data.meta.total} pages</span>
                    <div className="adm-pager__pages">
                        <Button size="sm" disabled={data.meta.page <= 1} onClick={() => setQ({ ...q, page: data.meta.page - 1 })}>Previous</Button>
                        <Button size="sm" disabled={data.meta.page >= data.meta.pages} onClick={() => setQ({ ...q, page: data.meta.page + 1 })}>Next</Button>
                    </div></div>
            )}
            {dialog}
        </Card>
    );
}

function PageMenu({ r, act }) {
    const [open, setOpen] = useState(false);
    const ref = useRef(null);
    useEffect(() => {
        if (!open) return undefined;
        const out = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
        document.addEventListener('mousedown', out);
        return () => document.removeEventListener('mousedown', out);
    }, [open]);
    const items = [
        r.status !== 'PUBLISHED' && r.status !== 'ARCHIVED' && ['publish', 'Publish now', 'fa-globe'],
        r.status === 'PUBLISHED' && ['unpublish', 'Unpublish', 'fa-eye-slash'],
        ['duplicate', 'Duplicate', 'fa-clone'],
        r.status !== 'ARCHIVED' && r.status !== 'PUBLISHED' && ['archive', 'Archive', 'fa-archive'],
        r.status === 'ARCHIVED' && ['restore', 'Restore', 'fa-undo'],
        r.status !== 'PUBLISHED' && ['delete', 'Delete', 'fa-trash-o'],
    ].filter(Boolean);
    return (
        <div className="lpa-menu" ref={ref}>
            <Button size="sm" variant="ghost" icon="fa-ellipsis-h" aria-label={`More actions for ${r.name}`} aria-expanded={open} onClick={() => setOpen(!open)} />
            {open && (
                <ul className="lpa-menu__list" role="menu">
                    {items.map(([a, l, i]) => <li key={a} role="none"><button type="button" role="menuitem" className={a === 'delete' ? 'is-danger' : ''} onClick={() => { setOpen(false); act(r, a); }}><i className={`fa ${i}`} aria-hidden="true"></i>{l}</button></li>)}
                    <li role="none"><a role="menuitem" href={`/admin/landing/pages/${r.id}/publish`}><i className="fa fa-clock-o" aria-hidden="true"></i>Schedule…</a></li>
                    {r.status === 'PUBLISHED' && <li role="none"><a role="menuitem" href={r.publicUrl || `/course/${r.slug}`} target="_blank" rel="noopener"><i className="fa fa-external-link" aria-hidden="true"></i>Open live page</a></li>}
                </ul>
            )}
        </div>
    );
}

/* --------------------------------------------------------------------------------------------- templates */

function Templates({ catalog, caps }) {
    return (
        <div className="adm-stack" data-testid="lp-templates">
            <Card title="Template library" subtitle="A template is a starting point: design, hero layout and section order. Pages keep their own copy, so changing pages never changes a template, and switching template never loses text.">
                <ul className="lpa-templates">
                    {catalog.templates.map((t) => (
                        <li key={t.key} className="lpa-template">
                            <TemplateThumb t={t} />
                            <h3>{t.name}</h3>
                            <p>{t.description}</p>
                            <p className="adm-small adm-muted">{t.sections.length} sections · hero: {t.hero}</p>
                            {has(caps, 'landing.publish') && <Button as="a" size="sm" href={`/admin/landing/pages/new?template=${t.key}`} icon="fa-plus">Use this template</Button>}
                        </li>
                    ))}
                </ul>
            </Card>
            <Card title="Section library" subtitle="Every page is built from these sections. Each can be switched on or off, reordered, given a layout and a background, and hidden on phones." flush>
                <DataTable rows={catalog.sections} rowKey="key" columns={[
                    { key: 'label', label: 'Section', render: (s) => <b>{s.label}</b> },
                    { key: 'description', label: 'What it shows' },
                    { key: 'layouts', label: 'Layouts', render: (s) => s.layouts.join(', ') },
                    { key: 'single', label: 'Per page', render: (s) => (s.single ? 'Once' : 'Several') },
                ]} />
            </Card>
        </div>
    );
}

/* ---------------------------------------------------------------------------------------------- payments */

function Payments({ caps, toast }) {
    const [data, setData] = useState(null);
    const load = () => axios.get('/admin/api/landing/payments').then((r) => setData(r.data)).catch((e) => toast(errorText(e), 'error'));
    useEffect(() => { load(); }, []); // eslint-disable-line react-hooks/exhaustive-deps
    if (!data) return <Card><p className="adm-muted">Loading…</p></Card>;
    return (
        <div className="adm-stack" data-testid="lp-payments">
            <Card title="Payment settings of every page" subtitle="Each page either follows the global defaults below or has its own settings. Open a page to change it." flush>
                <DataTable rows={data.pages} columns={[
                    { key: 'name', label: 'Page', render: (r) => <a href={`/admin/landing/pages/${r.id}/payments`}>{r.name}</a> },
                    { key: 'status', label: 'Status', render: (r) => <Badge tone={STATUS[r.status].tone}>{STATUS[r.status].label}</Badge> },
                    { key: 'mode', label: 'Settings', render: (r) => (r.mode === 'CUSTOM' ? <Badge tone="pink">Custom</Badge> : <Badge>Global defaults</Badge>) },
                    { key: 'choices', label: 'Buyers see', render: (r) => (r.choices.length ? r.choices.join(' · ') : <span className="adm-danger">Nothing: no way to pay</span>) },
                    { key: 'edit', label: '', render: (r) => <Button as="a" size="sm" href={`/admin/landing/pages/${r.id}/payments`} icon="fa-pencil">Payment settings</Button> },
                ]} empty={<EmptyState icon="fa-credit-card" title="No pages yet" />} />
            </Card>
            <Card title="Global defaults" subtitle="Used by pages set to 'Use the global defaults' and as the starting point for new custom settings.">
                <PaymentSettings value={data.defaults} global toast={toast} canEdit={has(caps, 'landing.payments')}
                                 onSave={(body) => axios.put('/admin/api/landing/payments/defaults', body).then((r) => { toast(r.data.message); load(); return r.data.defaults; })} />
            </Card>
        </div>
    );
}

/* ----------------------------------------------------------------------------------------------- coupons */

const EMPTY_COUPON = { code: '', description: '', kind: 'PERCENT', value: '', campaign: '', allPages: false, pages: [], courses: [], minAmount: '', maxUses: '', perUserLimit: '1',
    firstTimeOnly: false, startsAt: '', expiresAt: '', active: true };

function Coupons({ lookups, toast }) {
    const [list, setList] = useState(null);
    const [edit, setEdit] = useState(null);
    const [errors, setErrors] = useState({});
    const [busy, setBusy] = useState(false);
    const [dialog, confirm] = useConfirm();
    const load = () => axios.get('/admin/api/landing/coupons').then((r) => setList(r.data.coupons)).catch((e) => toast(errorText(e), 'error'));
    useEffect(() => { load(); }, []); // eslint-disable-line react-hooks/exhaustive-deps
    const open = (c) => {
        setErrors({});
        setEdit(c ? { id: c.id, code: c.code, description: c.description || '', kind: c.kind, value: String(c.value), campaign: c.campaign || '', allPages: !!Number(c.all_pages),
            pages: c.pages, courses: c.courses, minAmount: c.min_amount || '', maxUses: c.max_uses || '', perUserLimit: c.per_user_limit || '', firstTimeOnly: !!Number(c.first_time_only),
            startsAt: (c.starts_at || '').replace(' ', 'T').slice(0, 16), expiresAt: (c.expires_at || '').replace(' ', 'T').slice(0, 16), active: !!Number(c.is_active), used: c.used_count } : { ...EMPTY_COUPON });
    };
    const save = () => {
        setBusy(true);
        const req = edit.id ? axios.put(`/admin/api/landing/coupons/${edit.id}`, edit) : axios.post('/admin/api/landing/coupons', edit);
        req.then((r) => { toast(r.data.message); setList(r.data.coupons); setEdit(null); })
            .catch((e) => { setErrors((e.response && e.response.data && e.response.data.errors) || {}); toast(errorText(e), 'error'); })
            .finally(() => setBusy(false));
    };
    const remove = async (c) => {
        if (!(await confirm({ title: `Remove ${c.code}?`, body: 'A code that has been used is switched off instead, so past orders keep their history.', confirmLabel: 'Remove', danger: true }))) return;
        axios.delete(`/admin/api/landing/coupons/${c.id}`).then((r) => { toast(r.data.message); setList(r.data.coupons); }).catch((e) => toast(errorText(e), 'error'));
    };
    const now = new Date();
    const state = (c) => (!Number(c.is_active) ? ['Off', 'grey'] : c.expires_at && new Date(c.expires_at.replace(' ', 'T')) < now ? ['Expired', 'amber']
        : c.starts_at && new Date(c.starts_at.replace(' ', 'T')) > now ? ['Starts later', 'blue'] : c.max_uses && c.used_count >= c.max_uses ? ['Used up', 'amber'] : ['Active', 'green']);
    const target = (c) => (Number(c.all_pages) ? 'All landing pages' : [...c.pages.map((id) => (lookups.pages.find((p) => p.id === id) || {}).name),
        ...c.courses.map((id) => `Course: ${(lookups.courses.find((x) => x.id === id) || {}).title}`)].filter(Boolean).join(', '));
    const set = (k) => (e) => setEdit({ ...edit, [k]: e && e.target ? (e.target.type === 'checkbox' ? e.target.checked : e.target.value) : e });
    return (
        <Card flush>
            <Toolbar end={<Button variant="brand" icon="fa-plus" onClick={() => open(null)} data-testid="coupon-new">New coupon</Button>}>
                <p className="adm-muted adm-small">Checked on the server every time: dates, pages / courses, minimum amount, total uses, uses per buyer and first-course-only. A use counts only once the payment is verified.</p>
            </Toolbar>
            <DataTable loading={!list} rows={list || []} columns={[
                { key: 'code', label: 'Code', render: (c) => <span><b className="adm-mono">{c.code}</b><br /><small className="adm-muted">{c.description}</small></span> },
                { key: 'value', label: 'Discount', render: (c) => (c.kind === 'PERCENT' ? `${Number(c.value)}%` : inr(c.value)) },
                { key: 'target', label: 'Applies to', render: target },
                { key: 'campaign', label: 'Campaign', hide: 'md' },
                { key: 'used', label: 'Used', align: 'right', render: (c) => `${c.used_count}${c.max_uses ? ` / ${c.max_uses}` : ''}` },
                { key: 'discount_total', label: 'Given', align: 'right', hide: 'md', render: (c) => inr(c.discount_total) },
                { key: 'expires_at', label: 'Valid until', hide: 'md', render: (c) => (c.expires_at ? fmtDate(c.expires_at, true) : 'No end') },
                { key: 'state', label: 'Status', render: (c) => { const [l, t] = state(c); return <Badge tone={t} dot>{l}</Badge>; } },
                { key: 'actions', label: '', render: (c) => <div className="adm-rowactions"><Button size="sm" icon="fa-pencil" onClick={() => open(c)}>Edit</Button><Button size="sm" variant="ghost" icon="fa-trash-o" aria-label={`Remove ${c.code}`} onClick={() => remove(c)} /></div> },
            ]} empty={<EmptyState icon="fa-ticket" title="No coupons yet" action={<Button variant="brand" icon="fa-plus" onClick={() => open(null)}>New coupon</Button>} />} />
            <Drawer open={!!edit} onClose={() => setEdit(null)} title={edit && edit.id ? `Edit ${edit.code}` : 'New coupon'} size="lg"
                    footer={<><Button onClick={() => setEdit(null)}>Cancel</Button><Button variant="brand" icon="fa-check" busy={busy} onClick={save} data-testid="coupon-save">Save coupon</Button></>}>
                {edit && (
                    <div className="adm-form">
                        <div className="adm-grid2">
                            <Field label="Code" name="code" error={errors.code} required hint="Letters, numbers, - and _. Buyers type it at checkout.">
                                <input id="f-code" className="adm-input adm-mono" value={edit.code} onChange={(e) => setEdit({ ...edit, code: e.target.value.toUpperCase().replace(/\s/g, '') })} maxLength={40} />
                            </Field>
                            <Field label="Description" name="description" optional><input id="f-description" className="adm-input" value={edit.description} onChange={set('description')} maxLength={255} /></Field>
                        </div>
                        <div className="adm-grid3">
                            <Field label="Type" name="kind" error={errors.kind}>
                                <select id="f-kind" className="adm-input" value={edit.kind} onChange={set('kind')}><option value="PERCENT">Percentage</option><option value="FLAT">Fixed amount (₹)</option></select>
                            </Field>
                            <Field label={edit.kind === 'PERCENT' ? 'Discount %' : 'Discount ₹'} name="value" error={errors.value} required>
                                <input id="f-value" className="adm-input" inputMode="decimal" value={edit.value} onChange={set('value')} />
                            </Field>
                            <Field label="Campaign" name="campaign" optional hint="Groups sales in Analytics."><input id="f-campaign" className="adm-input" value={edit.campaign} onChange={set('campaign')} list="lp-campaigns" /></Field>
                            <datalist id="lp-campaigns">{lookups.campaigns.map((c) => <option key={c.campaign} value={c.campaign} />)}</datalist>
                        </div>
                        <Field label="Where it works" name="pages" error={errors.pages}>
                            <Toggle checked={edit.allPages} onChange={(v) => setEdit({ ...edit, allPages: v })} label="All landing pages" name="allPages" />
                            {!edit.allPages && (
                                <div className="adm-grid2">
                                    <div><p className="adm-field__label">Landing pages</p><div className="lpa-checks">{lookups.pages.map((p) => (
                                        <label key={p.id}><input type="checkbox" checked={edit.pages.includes(p.id)} onChange={(e) => setEdit({ ...edit, pages: e.target.checked ? [...edit.pages, p.id] : edit.pages.filter((x) => x !== p.id) })} /> {p.name}</label>))}</div></div>
                                    <div><p className="adm-field__label">Or every page of a course</p><div className="lpa-checks">{lookups.courses.map((c) => (
                                        <label key={c.id}><input type="checkbox" checked={edit.courses.includes(c.id)} onChange={(e) => setEdit({ ...edit, courses: e.target.checked ? [...edit.courses, c.id] : edit.courses.filter((x) => x !== c.id) })} /> {c.title}</label>))}</div></div>
                                </div>
                            )}
                        </Field>
                        <div className="adm-grid3">
                            <Field label="Minimum order ₹" name="minAmount" optional error={errors.minAmount}><input id="f-minAmount" className="adm-input" inputMode="decimal" value={edit.minAmount} onChange={set('minAmount')} /></Field>
                            <Field label="Total uses" name="maxUses" optional error={errors.maxUses} hint="Empty = no limit"><input id="f-maxUses" className="adm-input" inputMode="numeric" value={edit.maxUses} onChange={set('maxUses')} /></Field>
                            <Field label="Uses per buyer" name="perUserLimit" optional error={errors.perUserLimit}><input id="f-perUserLimit" className="adm-input" inputMode="numeric" value={edit.perUserLimit} onChange={set('perUserLimit')} /></Field>
                        </div>
                        <div className="adm-grid2">
                            <Field label="Starts" name="startsAt" optional hint="IST. Empty = now."><input id="f-startsAt" type="datetime-local" className="adm-input" value={edit.startsAt} onChange={set('startsAt')} /></Field>
                            <Field label="Ends" name="expiresAt" optional error={errors.expiresAt} hint="IST. A real end date; buyers see it."><input id="f-expiresAt" type="datetime-local" className="adm-input" value={edit.expiresAt} onChange={set('expiresAt')} /></Field>
                        </div>
                        <Toggle checked={edit.firstTimeOnly} onChange={(v) => setEdit({ ...edit, firstTimeOnly: v })} label="First course only" description="Only for buyers who have never paid for a course." name="firstTimeOnly" />
                        <Toggle checked={edit.active} onChange={(v) => setEdit({ ...edit, active: v })} label="Active" name="active" />
                    </div>
                )}
            </Drawer>
            {dialog}
        </Card>
    );
}

/* ------------------------------------------------------------------------------------------------- orders */

const OSTATUS = { created: ['Created', 'grey'], pending: ['Pending', 'amber'], successful: ['Successful', 'green'], failed: ['Failed', 'red'], refunded: ['Refunded', 'blue'], refund_due: ['Refund due', 'amber'] };

function Orders({ lookups, toast }) {
    const [f, setF] = useState({ q: '', page: '', course: '', artist: '', provider: '', method: '', status: '', range: '90d', from: '', to: '', page_no: 1 });
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(false);
    const [open, setOpen] = useState(null);
    const [note, setNote] = useState('');
    const [sure, setSure] = useState(false);
    const [busy, setBusy] = useState(false);
    const load = () => { setLoading(true); axios.get('/admin/api/landing/orders', { params: f }).then((r) => setData(r.data)).catch((e) => toast(errorText(e), 'error')).finally(() => setLoading(false)); };
    useEffect(load, [f]); // eslint-disable-line react-hooks/exhaustive-deps
    const show = (r) => { setNote(''); setSure(false); axios.get(`/admin/api/landing/orders/${r.id}`).then((x) => setOpen(x.data)).catch((e) => toast(errorText(e), 'error')); };
    const refund = () => {
        setBusy(true);
        axios.post(`/admin/api/landing/orders/${open.id}/refund`, { note }).then((r) => { toast(r.data.message); setOpen(null); load(); })
            .catch((e) => toast(errorText(e), 'error')).finally(() => setBusy(false));
    };
    const set = (k) => (v) => setF({ ...f, [k]: v, page_no: 1 });
    return (
        <Card flush>
            <div className="lpa-filters lpa-filters--pad">
                <SearchBox value={f.q} onSearch={set('q')} placeholder="Order, name, e-mail, transaction, coupon" label="Search orders" />
                <Segments value={f.range} options={RANGES.filter((r) => r.value !== 'today' && r.value !== 'yesterday')} onChange={set('range')} label="Date range" />
                {f.range === 'custom' && (
                    <span className="lpa-dates">
                        <label>From <input type="date" className="adm-input adm-input--sm" value={f.from} onChange={(e) => set('from')(e.target.value)} /></label>
                        <label>To <input type="date" className="adm-input adm-input--sm" value={f.to} onChange={(e) => set('to')(e.target.value)} /></label>
                    </span>
                )}
                <FilterSelect label="Status" value={f.status} onChange={set('status')} allLabel="Any status" options={Object.keys(OSTATUS).map((k) => ({ value: k, label: OSTATUS[k][0] }))} />
                <FilterSelect label="Page" value={f.page} onChange={set('page')} allLabel="All pages" options={lookups.pages.map((p) => ({ value: p.id, label: p.name }))} />
                <FilterSelect label="Course" value={f.course} onChange={set('course')} allLabel="All courses" options={lookups.courses.map((c) => ({ value: c.id, label: c.title }))} />
                <FilterSelect label="Artist" value={f.artist} onChange={set('artist')} allLabel="All artists" options={lookups.artists.map((a) => ({ value: a.id, label: a.name }))} />
                <FilterSelect label="Provider" value={f.provider} onChange={set('provider')} allLabel="All providers" options={['razorpay', 'paypal', 'test', 'free'].map((p) => ({ value: p, label: p }))} />
                <FilterSelect label="Method" value={f.method} onChange={set('method')} allLabel="All methods" options={Object.keys(METHOD_LABEL).filter((k) => k !== 'unknown').map((m) => ({ value: m, label: METHOD_LABEL[m] }))} />
            </div>
            <DataTable loading={loading || !data} rows={data ? data.rows : []} onRowClick={show} caption="Landing page orders" columns={[
                { key: 'order_number', label: 'Order', render: (r) => <span><b>{r.order_number}</b>{Number(r.is_test) ? <> <Badge>Test</Badge></> : null}<br /><small className="adm-muted">{fmtDate(r.created_at, true)}</small></span> },
                { key: 'billing_name', label: 'Customer', render: (r) => <span>{r.billing_name}<br /><small className="adm-muted">{r.billing_email}</small></span> },
                { key: 'course', label: 'Course · page', hide: 'md', render: (r) => <span>{r.course}<br /><small className="adm-muted">{r.page}</small></span> },
                { key: 'artist', label: 'Artist', hide: 'lg' },
                { key: 'price', label: 'Amount', align: 'right', render: (r) => <span>{r.currency === 'USD' ? `$${r.price}` : inr(r.price)}{r.coupon_code && <><br /><small className="adm-muted">{r.coupon_code}</small></>}</span> },
                { key: 'payment_method', label: 'Method · gateway', hide: 'md', render: (r) => <span>{METHOD_LABEL[r.payment_method] || r.payment_method || '—'}<br /><small className="adm-muted">{r.payment_provider || '—'}</small></span> },
                { key: 'payment_id', label: 'Transaction', hide: 'lg', render: (r) => <span className="adm-mono adm-small">{r.payment_id || r.gateway_ref || '—'}</span> },
                { key: 'status', label: 'Payment', render: (r) => <Badge tone={OSTATUS[r.status][1]} dot>{OSTATUS[r.status][0]}</Badge> },
                { key: 'enrollment', label: 'Enrolment', hide: 'md' },
            ]} empty={<EmptyState icon="fa-shopping-cart" title="No orders match">Orders placed through landing pages appear here.</EmptyState>} />
            {data && data.meta.pages > 1 && (
                <div className="adm-pager"><span className="adm-pager__info">Page {data.meta.page} of {data.meta.pages} · {data.meta.total} orders</span>
                    <div className="adm-pager__pages">
                        <Button size="sm" disabled={data.meta.page <= 1} onClick={() => setF({ ...f, page_no: data.meta.page - 1 })}>Previous</Button>
                        <Button size="sm" disabled={data.meta.page >= data.meta.pages} onClick={() => setF({ ...f, page_no: data.meta.page + 1 })}>Next</Button>
                    </div></div>
            )}
            <Drawer open={!!open} onClose={() => setOpen(null)} title={open ? `Order ${open.order_number}` : ''} subtitle={open ? `${open.course} · ${open.page}` : ''} size="lg">
                {open && (
                    <div className="adm-stack" data-testid="order-drawer">
                        <dl className="adm-dl">
                            <dt>Status</dt><dd><Badge tone={OSTATUS[open.status][1]} dot>{OSTATUS[open.status][0]}</Badge> · {open.enrollment}</dd>
                            <dt>Amount</dt><dd>{open.currency === 'USD' ? `$${open.price}` : inr(open.price)}{open.list_amount && Number(open.list_amount) !== Number(open.price) ? ` (list ${inr(open.list_amount)})` : ''}</dd>
                            {open.coupon_code && <><dt>Coupon</dt><dd>{open.coupon_code} · −{inr(open.discount_amount)}</dd></>}
                            {Number(open.tax_amount) > 0 && <><dt>Tax in amount</dt><dd>{inr(open.tax_amount)}</dd></>}
                            <dt>Method</dt><dd>{METHOD_LABEL[open.payment_method] || open.payment_method || '—'} via {open.payment_provider || '—'}</dd>
                            <dt>Transaction</dt><dd className="adm-mono">{open.payment_id || '—'}{open.gateway_ref ? ` · gateway order ${open.gateway_ref}` : ''}</dd>
                            <dt>Customer</dt><dd>{open.billing.name} · {open.billing.email}{open.billing.phone ? ` · ${open.billing.phone}` : ''}</dd>
                            {(open.utm_source || open.utm_campaign) && <><dt>Came from</dt><dd>{[open.utm_source, open.utm_campaign].filter(Boolean).join(' · ')}</dd></>}
                            <dt>Created</dt><dd>{fmtDate(open.created_at, true)}</dd>
                            {open.refund && <><dt>Refund</dt><dd>{open.refund.status} · {open.refund.gateway_refund_id || open.refund.reason}</dd></>}
                        </dl>
                        {open.events.length > 0 && <Card title="Checkout steps"><ul className="adm-timeline">{open.events.map((e, i) => <li key={i}>{e.event.replace('_', ' ').toLowerCase()} {e.method ? `· ${METHOD_LABEL[e.method] || e.method}` : ''} <small className="adm-muted">{fmtDate(e.created_at, true)}</small></li>)}</ul></Card>}
                        <p className="adm-muted adm-small">Successful payments can't be edited. Money only goes back through a refund, which is logged.</p>
                        {data.canRefund && (open.status === 'successful' || open.status === 'refund_due') && (
                            <Card title="Refund this order" subtitle="Sends the full amount back through the gateway now.">
                                <Field label="Why" name="note" required><textarea id="f-note" className="adm-input" rows={2} value={note} onChange={(e) => setNote(e.target.value)} /></Field>
                                <label className="adm-check"><input type="checkbox" checked={sure} onChange={(e) => setSure(e.target.checked)} /> I confirm: refund {open.currency === 'USD' ? `$${open.price}` : inr(open.price)} to {open.billing.name}.</label>
                                <Button variant="danger" icon="fa-undo" busy={busy} disabled={!sure || !note.trim()} onClick={refund} data-testid="order-refund">Refund</Button>
                            </Card>
                        )}
                    </div>
                )}
            </Drawer>
        </Card>
    );
}

/* -------------------------------------------------------------------------------------------------- media */

function Media({ caps, toast }) {
    const [data, setData] = useState(null);
    const [busy, setBusy] = useState(false);
    const [dialog, confirm] = useConfirm();
    const file = useRef(null);
    const load = () => axios.get('/admin/api/landing/media').then((r) => setData(r.data)).catch((e) => toast(errorText(e), 'error'));
    useEffect(() => { load(); }, []); // eslint-disable-line react-hooks/exhaustive-deps
    const upload = (files) => {
        const list = Array.from(files || []);
        if (!list.length) return;
        setBusy(true);
        Promise.all(list.map((f) => { const fd = new FormData(); fd.append('file', f); return axios.post('/admin/api/landing/media', fd).then(() => null).catch((e) => `${f.name}: ${errorText(e)}`); }))
            .then((errs) => { const bad = errs.filter(Boolean); if (bad.length) toast(bad.join(' · '), 'error'); else toast(`${list.length} uploaded.`); load(); })
            .finally(() => setBusy(false));
    };
    const alt = (m, v) => axios.put(`/admin/api/landing/media/${m.id}`, { alt: v }).then(() => toast('Saved.')).catch((e) => toast(errorText(e), 'error'));
    const remove = async (m) => {
        if (!(await confirm({ title: 'Remove this image from the library?', body: 'Images a page still uses cannot be removed.', confirmLabel: 'Remove', danger: true }))) return;
        axios.delete(`/admin/api/landing/media/${m.id}`).then((r) => { toast(r.data.message); load(); }).catch((e) => toast(errorText(e), 'error'));
    };
    const edit = has(caps, 'landing.content');
    return (
        <Card title="Media library" subtitle="Artwork, course images, artist photos and banners for landing pages. Pages get resized copies automatically (320 to 1600 px) for fast loading."
              actions={edit && <><input ref={file} type="file" multiple accept="image/jpeg,image/png,image/webp" hidden onChange={(e) => upload(e.target.files)} />
                  <Button variant="brand" icon="fa-upload" busy={busy} onClick={() => file.current.click()}>Upload images</Button></>}>
            {!data ? <p className="adm-muted">Loading…</p> : (
                <>
                    {data.media.length === 0 && <EmptyState icon="fa-picture-o" title="No uploads yet">Upload artwork once and use it on any page. Course images and artist portraits below can be used too.</EmptyState>}
                    <ul className="lpa-media lpa-media--lib">
                        {data.media.map((m) => (
                            <li key={m.id}>
                                <img src={m.thumb} alt={m.alt || ''} loading="lazy" />
                                <small className="adm-muted">{m.width}×{m.height} · {Math.round((m.bytes || 0) / 1024)} KB</small>
                                {edit ? <input className="adm-input adm-input--sm" defaultValue={m.alt || ''} placeholder="Describe the image (alt text)" aria-label="Alt text" onBlur={(e) => { if (e.target.value !== (m.alt || '')) alt(m, e.target.value); }} />
                                    : <span>{m.alt}</span>}
                                <span className="lpa-media__row"><a href={m.url} target="_blank" rel="noopener" className="adm-small">Open</a>{edit && <button type="button" className="adm-linkbtn adm-danger" onClick={() => remove(m)}>Remove</button>}</span>
                            </li>
                        ))}
                    </ul>
                    <h3 className="lpa-h3">Already on the site</h3>
                    <ul className="lpa-media">{data.site.map((m) => <li key={m.url}><img src={m.thumb} alt="" loading="lazy" /><span>{m.label}</span></li>)}</ul>
                </>
            )}
            {dialog}
        </Card>
    );
}

/* ----------------------------------------------------------------------------------------------- settings */

function Settings({ catalog, caps, toast }) {
    const [s, setS] = useState(null);
    const [canEdit, setCanEdit] = useState(false);
    const [errors, setErrors] = useState({});
    const [busy, setBusy] = useState(false);
    useEffect(() => { axios.get('/admin/api/landing/settings').then((r) => { setS(r.data.settings); setCanEdit(r.data.canEdit); }); }, []);
    if (!s) return <Card><p className="adm-muted">Loading…</p></Card>;
    const co = s.checkout || {};
    const save = () => {
        setBusy(true);
        axios.put('/admin/api/landing/settings', s).then((r) => { toast(r.data.message); setS(r.data.settings); setErrors({}); })
            .catch((e) => { setErrors((e.response && e.response.data && e.response.data.errors) || {}); toast(errorText(e), 'error'); }).finally(() => setBusy(false));
    };
    return (
        <div className="adm-stack">
            <Card title="Defaults for new pages" subtitle="Applied when a page is created; every page can change them afterwards.">
                <fieldset disabled={!canEdit} className="adm-form">
                    <div className="adm-grid2">
                        <Field label="Default template" name="template">
                            <select id="f-template" className="adm-input" value={s.template} onChange={(e) => setS({ ...s, template: e.target.value })}>{catalog.templates.map((t) => <option key={t.key} value={t.key}>{t.name}</option>)}</select>
                        </Field>
                        <Field label="WhatsApp number for questions" name="whatsapp" optional error={errors.whatsapp} hint="Shown as a chat button. With country code.">
                            <input id="f-whatsapp" className="adm-input" value={s.whatsapp || ''} onChange={(e) => setS({ ...s, whatsapp: e.target.value })} placeholder="+919876543210" />
                        </Field>
                        <Field label="Course access" name="accessDuration" optional error={errors.accessDuration}><input id="f-accessDuration" className="adm-input" value={s.accessDuration || ''} onChange={(e) => setS({ ...s, accessDuration: e.target.value })} placeholder="Recordings for 12 months" /></Field>
                        <Field label="Tax" name="taxRate" optional error={errors.taxRate}>
                            <div className="adm-inline"><input id="f-taxRate" className="adm-input adm-input--sm" inputMode="decimal" value={s.taxRate || ''} onChange={(e) => setS({ ...s, taxRate: e.target.value })} placeholder="18" /> %
                                <select className="adm-input adm-input--sm" value={s.taxMode} onChange={(e) => setS({ ...s, taxMode: e.target.value })} aria-label="Tax mode"><option value="INCLUDED">included in the price</option><option value="ADDED">added at checkout</option></select></div>
                        </Field>
                    </div>
                    <Field label="Refund policy" name="refundPolicy" optional error={errors.refundPolicy}><textarea id="f-refundPolicy" className="adm-input" rows={3} value={s.refundPolicy || ''} onChange={(e) => setS({ ...s, refundPolicy: e.target.value })} /></Field>
                    <p className="adm-field__label">Checkout</p>
                    <div className="adm-grid2">
                        {[['requirePhone', 'Ask for a phone number'], ['requireAddress', 'Ask for a billing address'], ['requireTerms', 'Require accepting the terms'], ['requirePrivacy', 'Require privacy consent'],
                            ['showCoupon', 'Show the coupon field'], ['showSummary', 'Show the order summary']].map(([k, l]) => (
                            <Toggle key={k} checked={!!co[k]} onChange={(v) => setS({ ...s, checkout: { ...co, [k]: v } })} label={l} name={k} />
                        ))}
                    </div>
                    {canEdit && <div className="lpa-savebar"><span></span><Button variant="brand" icon="fa-check" busy={busy} onClick={save}>Save defaults</Button></div>}
                </fieldset>
            </Card>
            <Card title="Who can do what" subtitle="Landing page permissions are checked on the server for every action. Super admins grant them per person under Team access below.">
                <table className="lpa-roles">
                    <thead><tr><th scope="col">Permission</th><th scope="col">Lets someone</th><th scope="col">Given to</th></tr></thead>
                    <tbody>
                        {[['landing', 'Open this section, see pages and analytics', 'Super admin, instructors (own courses), moderators, content / marketing / finance grants'],
                            ['landing.content', 'Edit content, sections, design, media, SEO and tags', 'Super admin, instructors (own courses), moderators, content and marketing grants'],
                            ['landing.publish', 'Create, publish, schedule, roll back, archive, delete', 'Super admin, publisher grant'],
                            ['landing.pricing', 'Prices, offers and coupons', 'Super admin, marketing grant'],
                            ['landing.payments', 'Payment providers, methods and checkout', 'Super admin'],
                            ['landing.orders', 'See orders and transactions', 'Super admin, finance grant'],
                            ['landing.refunds', 'Refund an order through the gateway', 'Super admin, finance grant']].map(([c, d, w]) => (
                            <tr key={c}><td><code>{c}</code>{has(caps, c) && <> <Badge tone="green">You</Badge></>}</td><td>{d}</td><td>{w}</td></tr>
                        ))}
                    </tbody>
                </table>
            </Card>
            <TeamAccess toast={toast} />
        </div>
    );
}

const CAP_LABEL = { landing: 'Open the section', 'landing.content': 'Content', 'landing.publish': 'Publish', 'landing.pricing': 'Pricing & coupons',
    'landing.payments': 'Payments & checkout', 'landing.orders': 'Orders', 'landing.refunds': 'Refunds' };
const ROLE_LABEL = { content: 'Content manager', marketing: 'Marketing manager', finance: 'Finance / payments', publisher: 'Publisher' };

/** Settings > Team access: landing permissions per person, on top of what their persona gives. Super admins change them. */
function TeamAccess({ toast }) {
    const [data, setData] = useState(null);
    const [edit, setEdit] = useState(null);       // {id, name, caps:Set}
    const [busy, setBusy] = useState(false);
    const load = () => axios.get('/admin/api/landing/team').then((r) => setData(r.data)).catch((e) => toast(errorText(e), 'error'));
    useEffect(() => { load(); }, []); // eslint-disable-line react-hooks/exhaustive-deps
    if (!data) return <Card><p className="adm-muted">Loading…</p></Card>;
    const open = (r) => setEdit({ id: r.id, name: r.name, persona: r.fromPersona, caps: new Set(r.granted) });
    const flip = (c) => { const n = new Set(edit.caps); if (n.has(c)) n.delete(c); else n.add(c); setEdit({ ...edit, caps: n }); };
    const applyRole = (k) => setEdit({ ...edit, caps: new Set(data.roles[k]) });
    const save = () => {
        setBusy(true);
        axios.put(`/admin/api/landing/team/${edit.id}`, { capabilities: [...edit.caps] }).then((r) => { toast(r.data.message); setEdit(null); load(); })
            .catch((e) => toast(errorText(e), 'error')).finally(() => setBusy(false));
    };
    return (
        <Card title="Team access" subtitle={data.canEdit ? 'Give someone landing permissions on top of their persona: pick a ready-made role or tick single permissions. Instructors stay limited to their own courses.'
            : 'Who has which landing page permissions. Only a super admin can change them.'} flush>
            <DataTable rows={data.team} columns={[
                { key: 'name', label: 'Person', render: (r) => <span><b>{r.name}</b><br /><span className="adm-muted adm-small">{r.email}</span></span> },
                { key: 'persona', label: 'Persona', render: (r) => <span>{r.personaLabel}{r.ownCoursesOnly && <><br /><span className="adm-muted adm-small">own courses only</span></>}</span> },
                { key: 'effective', label: 'Landing permissions', render: (r) => (r.effective.length === 0 ? <span className="adm-muted">None</span> : (
                    <span className="lpa-capchips">{r.effective.map((c) => <span key={c} className={cx('lpa-chip', r.granted.includes(c) && !r.fromPersona.includes(c) && 'is-on')} title={r.granted.includes(c) && !r.fromPersona.includes(c) ? 'Granted personally' : 'From the persona'}>{CAP_LABEL[c] || c}</span>)}</span>)) },
                { key: 'a', label: '', render: (r) => (data.canEdit && r.editable ? <Button size="sm" icon="fa-key" onClick={() => open(r)} data-testid={`team-edit-${r.id}`}>Change</Button> : null) },
            ]} empty={<EmptyState icon="fa-users" title="No staff yet" />} />
            <Drawer open={!!edit} onClose={() => setEdit(null)} title={edit ? `Landing permissions: ${edit.name}` : ''} subtitle="Green chips in the list are granted personally; the rest come from the persona."
                    footer={<><Button onClick={() => setEdit(null)}>Cancel</Button><Button variant="brand" icon="fa-check" busy={busy} onClick={save} data-testid="team-save">Save permissions</Button></>}>
                {edit && (
                    <div className="adm-stack">
                        <div><p className="adm-field__label">Ready-made roles</p>
                            <div className="lpa-actions">{Object.keys(data.roles).map((k) => <Button key={k} size="sm" onClick={() => applyRole(k)}>{ROLE_LABEL[k] || k}</Button>)}
                                <Button size="sm" variant="ghost" onClick={() => setEdit({ ...edit, caps: new Set() })}>Remove all</Button></div></div>
                        <div className="lpa-toggles">
                            {data.grantable.map((c) => (
                                <Toggle key={c} name={`cap-${c}`} label={CAP_LABEL[c] || c} checked={edit.caps.has(c) || edit.persona.includes(c)} disabled={edit.persona.includes(c)}
                                        description={edit.persona.includes(c) ? 'Already given by the persona' : c} onChange={() => flip(c)} />
                            ))}
                        </div>
                    </div>
                )}
            </Drawer>
        </Card>
    );
}

/* ---------------------------------------------------------------------------------------------------- page */

export default function LandingIndex() {
    const { tab, caps, lookups, catalog } = usePage().props;
    const [toastNode, toast] = useToast();
    const current = TABS.find((t) => t.key === tab) || TABS[0];
    const allowed = has(caps, current.cap);
    const SUB = {
        overview: 'Every artist course landing page in one place: create, design, price, take payments, publish and measure.',
        pages: 'Search, filter and manage every landing page.',
        templates: 'Starting points for new pages, and the sections pages are built from.',
        payments: 'Which payment providers and methods each page offers, and the global defaults.',
        coupons: 'Codes for pages, courses and campaigns.',
        orders: 'Orders placed through landing pages, with payment details and refunds.',
        analytics: 'Funnel, payment methods, providers and devices.',
        media: 'Images for landing pages.',
        settings: 'Defaults for new pages and who can do what.',
    };
    return (
        <AdminLayout title={`Landing pages · ${current.label}`} crumbs={[{ label: 'Landing pages', href: '/admin/landing' }, { label: current.label }]} wide>
            <PageHeader eyebrow="Landing Page Management" title={current.label} subtitle={SUB[current.key]}
                        actions={has(caps, 'landing.publish') && <Button as="a" href="/admin/landing/pages/new" variant="brand" icon="fa-plus">Create landing page</Button>} />
            <LandingTabs current={current.key} caps={caps} />
            {!allowed ? <Card><EmptyState icon="fa-lock" title="You don't have access to this part">Ask a super admin to grant it in People & roles.</EmptyState></Card> : (
                <>
                    {current.key === 'overview' && <Overview lookups={lookups} caps={caps} />}
                    {current.key === 'pages' && <Pages caps={caps} toast={toast} />}
                    {current.key === 'templates' && <Templates catalog={catalog} caps={caps} />}
                    {current.key === 'payments' && <Payments caps={caps} toast={toast} />}
                    {current.key === 'coupons' && <Coupons lookups={lookups} toast={toast} />}
                    {current.key === 'orders' && <Orders lookups={lookups} toast={toast} />}
                    {current.key === 'analytics' && <Analytics lookups={lookups} />}
                    {current.key === 'media' && <Media caps={caps} toast={toast} />}
                    {current.key === 'settings' && <Settings catalog={catalog} caps={caps} toast={toast} />}
                </>
            )}
            {toastNode}
        </AdminLayout>
    );
}
