import React from 'react';
import { usePage } from '@inertiajs/inertia-react';
import AdminLayout, { PageHeader } from '@/Components/Admin/AdminLayout';
import RevenueChart from '@/Components/Admin/RevenueChart';
import { shortDate } from '@/Components/Studio/Journey';

const STAGES = [
    ['signedUp', 'Joined'], ['enrolled', 'Enrolled'], ['learning', 'Learning'], ['submitted', 'Submitted'], ['approved', 'Approved'], ['selling', 'Selling'],
];

export default function Overview() {
    const { admin, kpi = {}, revenue, pipeline = {}, reviewQueue = [], recentStudents = [], recentCommissions } = usePage().props;
    const caps = admin.capabilities || [];
    const stages = STAGES.filter(([k]) => pipeline[k] !== null && pipeline[k] !== undefined);
    const maxStage = Math.max(1, ...stages.map(([k]) => Number(pipeline[k] || 0)));
    const hour = new Date().getHours();
    const greet = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';

    const cards = [
        ['students', admin.persona === 'teacher' ? 'My students' : 'Students', 'fa-users', '/admin/dashboard/people', 'people'],
        ['activeEnrollments', 'Active enrollments', 'fa-graduation-cap', null, null],
        ['pendingReviews', 'Pending reviews', 'fa-check-square-o', '/admin/dashboard/submissions', 'submissions'],
        ['listedArtworks', 'Artworks for sale', 'fa-tags', '/admin/dashboard/marketplace', 'marketplace'],
        ['openCommissions', 'Open commissions', 'fa-paint-brush', '/admin/dashboard/commissions', 'commissions'],
        ['courses', admin.persona === 'teacher' ? 'My live courses' : 'Live courses', 'fa-book', '/admin/courses', 'catalog'],
    ].filter(([, , , , cap]) => !cap || caps.includes(cap));

    return (
        <AdminLayout title="Overview">
            <PageHeader title={`${greet}, ${admin.name.split(' ')[0]}`} subtitle={`You're signed in as ${admin.personaLabel}. Here's what needs your attention today.`}
                        actions={caps.includes('submissions') && Number(kpi.pendingReviews) > 0 ? <a href="/admin/dashboard/submissions" className="inline-flex items-center h-11 px-5 rounded-full bg-brand text-white font-bold hover:bg-brand-dark">Review {kpi.pendingReviews} submission{kpi.pendingReviews > 1 ? 's' : ''}</a> : null} />

            <div className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-6 gap-4">
                {cards.map(([k, label, icon, href]) => {
                    const Tag = href ? 'a' : 'div';
                    return (
                        <Tag key={k} href={href || undefined} className={`rounded-2xl bg-white border border-gray-200 p-5 ${href ? 'hover:border-ink hover:shadow-md transition' : ''}`}>
                            <i className={`fa ${icon} text-brand`} aria-hidden="true"></i>
                            <p className="mt-3 text-3xl font-black tracking-tight text-ink">{Number(kpi[k] || 0).toLocaleString('en-IN')}</p>
                            <p className="font-mono text-xs uppercase tracking-widest text-gray-500">{label}</p>
                        </Tag>
                    );
                })}
            </div>

            <div className="mt-6 grid grid-cols-1 xl:grid-cols-3 gap-6">
                {revenue && (
                    <section className="xl:col-span-2 rounded-2xl bg-white border border-gray-200 p-6">
                        <div className="flex items-baseline justify-between gap-4">
                            <h2 className="text-xl font-black tracking-tight text-ink">Revenue · last 12 months</h2>
                            <p className="font-mono text-sm text-gray-500">₹{revenue.reduce((a, d) => a + d.coursesInr + d.commissionsInr, 0).toLocaleString('en-IN')} total</p>
                        </div>
                        <div className="mt-4"><RevenueChart data={revenue} /></div>
                    </section>
                )}
                <section className={`${revenue ? '' : 'xl:col-span-3'} rounded-2xl bg-white border border-gray-200 p-6`}>
                    <h2 className="text-xl font-black tracking-tight text-ink">Creative lifecycle</h2>
                    <p className="text-sm text-gray-500">Students at each stage of learn → submit → sell.</p>
                    <ol className="mt-5 space-y-3">
                        {stages.map(([k, label], i) => {
                            const v = Number(pipeline[k] || 0);
                            return (
                                <li key={k}>
                                    <div className="flex justify-between text-sm"><span className="font-semibold text-gray-800"><span className="font-mono text-gray-400 mr-2">0{i + 1}</span>{label}</span><span className="font-mono font-bold text-ink">{v}</span></div>
                                    <div className="mt-1 h-2 rounded-full bg-gray-100 overflow-hidden"><div className="h-full rounded-full bg-ink" style={{ width: `${(v / maxStage) * 100}%` }}></div></div>
                                </li>
                            );
                        })}
                    </ol>
                </section>
            </div>

            <div className="mt-6 grid grid-cols-1 xl:grid-cols-3 gap-6">
                {caps.includes('submissions') && (
                    <section className="xl:col-span-2 rounded-2xl bg-white border border-gray-200 p-6">
                        <div className="flex justify-between items-baseline"><h2 className="text-xl font-black tracking-tight text-ink">Waiting for review</h2><a href="/admin/dashboard/submissions" className="text-sm font-bold underline">Open desk</a></div>
                        {reviewQueue.length === 0 ? <p className="mt-4 text-gray-600"><i className="fa fa-check-circle text-green-600 mr-2" aria-hidden="true"></i>All caught up. No submissions are waiting.</p> : (
                            <ul className="mt-4 divide-y divide-gray-100">
                                {reviewQueue.map((s) => (
                                    <li key={s.id} className="py-3 flex items-center gap-4">
                                        <img src={s.file_url} alt="" className="w-14 h-14 rounded-lg object-cover bg-gray-100" />
                                        <div className="flex-1 min-w-0"><p className="font-bold text-ink truncate">{s.title}</p><p className="text-sm text-gray-500 truncate">{s.student_name} · {s.course_title}</p></div>
                                        <span className="font-mono text-xs text-gray-500">{shortDate(s.created_at)}</span>
                                        <a href={`/admin/dashboard/submissions?focus=${s.id}`} className="h-9 px-4 inline-flex items-center rounded-full border border-gray-300 text-sm font-bold hover:border-ink">Review</a>
                                    </li>
                                ))}
                            </ul>
                        )}
                    </section>
                )}
                <section className="rounded-2xl bg-white border border-gray-200 p-6">
                    <h2 className="text-xl font-black tracking-tight text-ink">{admin.persona === 'teacher' ? 'My newest students' : 'New students'}</h2>
                    <ul className="mt-4 space-y-3">
                        {recentStudents.map((u) => (
                            <li key={u.id} className="flex items-center gap-3">
                                <span className="w-9 h-9 rounded-full bg-brand-soft text-brand font-black flex items-center justify-center">{String(u.name).charAt(0)}</span>
                                <div className="flex-1 min-w-0"><p className="font-semibold text-ink truncate">{u.name}</p><p className="text-xs text-gray-500 truncate">{u.email}</p></div>
                                <span className="font-mono text-xs text-gray-400">{shortDate(u.created_at)}</span>
                            </li>
                        ))}
                        {recentStudents.length === 0 && <li className="text-gray-500">No students yet.</li>}
                    </ul>
                </section>
            </div>

            {recentCommissions && (
                <section className="mt-6 rounded-2xl bg-white border border-gray-200 p-6">
                    <div className="flex justify-between items-baseline"><h2 className="text-xl font-black tracking-tight text-ink">Latest commissions</h2><a href="/admin/dashboard/commissions" className="text-sm font-bold underline">Manage</a></div>
                    {recentCommissions.length === 0 ? <p className="mt-4 text-gray-600">No paid commissions yet.</p> : (
                        <div className="mt-4 overflow-x-auto">
                            <table className="w-full text-left text-sm">
                                <thead><tr className="font-mono text-xs uppercase tracking-widest text-gray-500"><th className="py-2">Order</th><th>Customer</th><th>Theme</th><th>Status</th><th className="text-right">Total</th></tr></thead>
                                <tbody>{recentCommissions.map((o) => (
                                    <tr key={o.order_id} className="border-t border-gray-100"><td className="py-3 font-mono">{o.order_id}</td><td>{o.customer_name}</td><td>{o.artwork_theme}</td><td><span className="rounded-full bg-gray-100 px-2.5 py-1 text-xs font-bold">{String(o.order_status).replace('_', ' ')}</span></td><td className="text-right font-mono">{o.currency === 'INR' ? '₹' : '$'}{Number(o.total_price).toLocaleString('en-IN')}</td></tr>
                                ))}</tbody>
                            </table>
                        </div>
                    )}
                </section>
            )}
        </AdminLayout>
    );
}
