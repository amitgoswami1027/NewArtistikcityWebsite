import React, { useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import axios from 'axios';
import AdminLayout, { PageHeader, useToast, errorText } from '@/Components/Admin/AdminLayout';
import { shortDate } from '@/Components/Studio/Journey';

const LABEL = { PAYMENT_PENDING: 'Awaiting payment', QUEUED: 'Queued', PROOF_SENT: 'Proof sent', IN_PRODUCTION: 'In production', SHIPPED: 'Shipped', COMPLETED: 'Completed' };

export default function Commissions() {
    const { orders: initial = [], flow = [] } = usePage().props;
    const [orders, setOrders] = useState(initial);
    const [view, setView] = useState('board');
    const [q, setQ] = useState('');
    const [toastNode, toast] = useToast();
    const shown = orders.filter((o) => !q || `${o.order_id} ${o.customer_name} ${o.customer_email}`.toLowerCase().includes(q.toLowerCase()));

    const move = (o, status) => {
        axios.post(`/admin/console/api/commissions/${o.order_id}/status`, { status })
            .then(() => { setOrders(orders.map((x) => (x.order_id === o.order_id ? { ...x, order_status: status } : x))); toast(`${o.order_id} → ${LABEL[status]}`); })
            .catch((e) => toast(errorText(e), 'error'));
    };
    const nextOf = (s) => flow[flow.indexOf(s) + 1];
    const money = (o) => `${o.currency === 'INR' ? '₹' : '$'}${Number(o.total_price).toLocaleString('en-IN')}`;

    const Card = ({ o }) => (
        <div className="rounded-xl bg-white border border-gray-200 p-4 shadow-sm">
            <div className="flex gap-3">
                <img src={o.uploaded_photo_url} alt="" className="w-14 h-14 rounded-lg object-cover bg-gray-100" />
                <div className="min-w-0">
                    <p className="font-mono text-xs text-gray-500">{o.order_id}</p>
                    <p className="font-bold text-ink truncate">{o.customer_name}</p>
                    <p className="text-xs text-gray-500">{o.artwork_theme} · {o.width_inches}×{o.height_inches}" · {String(o.fulfillment_type).replace('_', ' ').toLowerCase()}</p>
                </div>
            </div>
            <div className="mt-3 flex items-center justify-between">
                <span className="font-mono font-bold text-ink">{money(o)}</span>
                {o.order_status !== 'PAYMENT_PENDING' && nextOf(o.order_status) && (
                    <button type="button" onClick={() => move(o, nextOf(o.order_status))} className="h-8 px-3 rounded-full bg-ink text-white text-xs font-bold hover:bg-gray-800">→ {LABEL[nextOf(o.order_status)]}</button>
                )}
            </div>
            {o.special_instructions && <p className="mt-2 text-xs text-gray-600 truncate" title={o.special_instructions}>“{o.special_instructions}”</p>}
        </div>
    );

    return (
        <AdminLayout title="Commissions">
            <PageHeader title="Commission orders" subtitle="Track every custom painting from payment to delivery. Move an order along the pipeline as the work progresses."
                        actions={<>
                            <input type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search order or customer" className="h-11 w-64 rounded-full border-gray-300 px-4 focus:border-brand focus:ring-brand" aria-label="Search commissions" />
                            <div className="inline-flex rounded-full bg-white border border-gray-300 p-1">{['board', 'table'].map((v) => <button key={v} type="button" onClick={() => setView(v)} className={`h-9 px-4 rounded-full text-sm font-bold ${view === v ? 'bg-ink text-white' : 'text-gray-600'}`}>{v === 'board' ? 'Board' : 'Table'}</button>)}</div>
                        </>} />
            {view === 'board' ? (
                <div className="grid grid-flow-col auto-cols-[minmax(260px,1fr)] gap-4 overflow-x-auto pb-4">
                    {flow.map((stage) => {
                        const col = shown.filter((o) => o.order_status === stage);
                        return (
                            <section key={stage} className="rounded-2xl bg-gray-100 p-3 min-h-[200px]">
                                <h2 className="px-1 pb-3 flex justify-between font-mono text-xs uppercase tracking-widest text-gray-600"><span>{LABEL[stage]}</span><span>{col.length}</span></h2>
                                <div className="space-y-3">{col.map((o) => <Card key={o.order_id} o={o} />)}</div>
                            </section>
                        );
                    })}
                </div>
            ) : (
                <div className="rounded-2xl bg-white border border-gray-200 overflow-x-auto">
                    <table className="w-full text-left text-sm">
                        <thead className="bg-gray-50"><tr className="font-mono text-xs uppercase tracking-widest text-gray-500"><th className="p-3">Order</th><th>Customer</th><th>Artwork</th><th>Payment</th><th>Placed</th><th>Status</th><th className="text-right p-3">Total</th></tr></thead>
                        <tbody>{shown.map((o) => (
                            <tr key={o.order_id} className="border-t border-gray-100">
                                <td className="p-3 font-mono">{o.order_id}</td>
                                <td><p className="font-semibold text-ink">{o.customer_name}</p><p className="text-xs text-gray-500">{o.customer_email}</p></td>
                                <td>{o.artwork_theme} · {o.width_inches}×{o.height_inches}"</td>
                                <td className="font-mono text-xs">{o.payment_method}</td>
                                <td className="font-mono text-xs">{shortDate(o.created_at)}</td>
                                <td>
                                    <select value={o.order_status} disabled={o.order_status === 'PAYMENT_PENDING'} onChange={(e) => move(o, e.target.value)} className="h-9 rounded-lg border-gray-300 text-sm" aria-label={`Status for ${o.order_id}`}>
                                        {flow.map((s) => <option key={s} value={s} disabled={s === 'PAYMENT_PENDING'}>{LABEL[s]}</option>)}
                                    </select>
                                </td>
                                <td className="p-3 text-right font-mono font-bold">{money(o)}</td>
                            </tr>
                        ))}</tbody>
                    </table>
                    {shown.length === 0 && <p className="p-6 text-gray-600">No commission orders yet.</p>}
                </div>
            )}
            {toastNode}
        </AdminLayout>
    );
}
