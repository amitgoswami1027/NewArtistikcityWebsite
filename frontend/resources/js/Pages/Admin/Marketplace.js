import React, { useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import axios from 'axios';
import AdminLayout, { PageHeader, useToast, errorText } from '@/Components/Admin/AdminLayout';
import { inr, shortDate } from '@/Components/Studio/Journey';

export default function Marketplace() {
    const { listings: initial = [] } = usePage().props;
    const [items, setItems] = useState(initial);
    const [filter, setFilter] = useState('listed');
    const [toastNode, toast] = useToast();
    const shown = items.filter((l) => filter === 'all' || (filter === 'listed' ? l.is_listed_for_sale : !l.is_listed_for_sale));
    const listedValue = items.filter((l) => l.is_listed_for_sale).reduce((a, l) => a + Number(l.sale_price || 0) * Number(l.inventory_count || 0), 0);

    const unlist = (l) => axios.post(`/admin/console/api/marketplace/${l.id}/unlist`)
        .then(() => { setItems(items.map((x) => (x.id === l.id ? { ...x, is_listed_for_sale: false } : x))); toast('Listing removed from the shop'); })
        .catch((e) => toast(errorText(e), 'error'));

    return (
        <AdminLayout title="Marketplace">
            <PageHeader title="Student marketplace" subtitle="Approved student work and its selling status. Remove a listing if it breaks the shop guidelines."
                        actions={<a href="/student-shop" target="_blank" rel="noopener" className="inline-flex items-center h-11 px-5 rounded-full border border-gray-300 bg-white font-bold hover:border-ink">Open public shop</a>} />
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-6">
                <div className="rounded-2xl bg-white border border-gray-200 p-5"><p className="text-3xl font-black text-ink">{items.filter((l) => l.is_listed_for_sale).length}</p><p className="font-mono text-xs uppercase tracking-widest text-gray-500">Listed now</p></div>
                <div className="rounded-2xl bg-white border border-gray-200 p-5"><p className="text-3xl font-black text-ink">{items.length}</p><p className="font-mono text-xs uppercase tracking-widest text-gray-500">Approved in portfolios</p></div>
                <div className="rounded-2xl bg-white border border-gray-200 p-5"><p className="text-3xl font-black text-ink font-mono">{inr(listedValue)}</p><p className="font-mono text-xs uppercase tracking-widest text-gray-500">Stock value listed</p></div>
            </div>
            <div className="flex gap-2 mb-4">{[['listed', 'For sale'], ['unlisted', 'Not listed'], ['all', 'All']].map(([k, l]) => <button key={k} type="button" onClick={() => setFilter(k)} className={`h-10 px-4 rounded-full text-sm font-bold border ${filter === k ? 'bg-ink text-white border-ink' : 'bg-white border-gray-300'}`}>{l}</button>)}</div>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-4 gap-5">
                {shown.map((l) => (
                    <article key={l.id} className="rounded-2xl bg-white border border-gray-200 overflow-hidden">
                        <div className="relative bg-gray-100" style={{ aspectRatio: '4 / 3' }}><img src={l.file_url} alt={l.title} className="absolute inset-0 w-full h-full object-cover" /></div>
                        <div className="p-4">
                            <p className="font-bold text-ink">{l.title}</p>
                            <p className="text-sm text-gray-500">{l.artist_name} · {l.course_title}</p>
                            <div className="mt-3 flex items-center justify-between">
                                <span className="font-mono text-sm">{l.is_listed_for_sale ? <><strong className="text-ink">{inr(l.sale_price)}</strong> · {l.inventory_count} in stock</> : <span className="text-gray-500">Not for sale</span>}</span>
                                {l.is_listed_for_sale && <button type="button" onClick={() => unlist(l)} className="h-8 px-3 rounded-full border border-red-300 text-red-700 text-xs font-bold hover:bg-red-50">Unlist</button>}
                            </div>
                            <p className="mt-2 font-mono text-xs text-gray-400">Updated {shortDate(l.updated_at)}</p>
                        </div>
                    </article>
                ))}
            </div>
            {shown.length === 0 && <p className="text-gray-600">Nothing in this view yet.</p>}
            {toastNode}
        </AdminLayout>
    );
}
