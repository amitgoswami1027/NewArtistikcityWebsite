import React, { useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import axios from 'axios';
import StudioLayout from '@/Components/Studio/StudioLayout';
import { statusStyle, shortDate, inr } from '@/Components/Studio/Journey';

function ArtworkCard({ item, onSaved }) {
    const st = statusStyle(item.admin_status);
    const approved = item.admin_status === 'Approved';
    const [listed, setListed] = useState(!!item.is_listed_for_sale);
    const [price, setPrice] = useState(item.sale_price ? String(Number(item.sale_price)) : '');
    const [stock, setStock] = useState(item.inventory_count ? String(item.inventory_count) : '1');
    const [state, setState] = useState(null);

    const save = (nextListed = listed) => {
        setState({ busy: true });
        axios.put('/api/portfolio/marketplace/toggle', {
            submissionId: item.id, isListedForSale: nextListed,
            salePrice: price === '' ? null : Number(price), inventoryCount: stock === '' ? 1 : Number(stock),
        }).then((r) => { setState({ ok: nextListed ? 'Listed in the student shop' : 'Saved (not for sale)' }); onSaved(r.data); })
            .catch((e) => { setState({ error: (e.response && e.response.data && e.response.data.message) || 'Could not save.' }); if (nextListed) setListed(false); });
    };

    return (
        <article className={`rounded-2xl border bg-white overflow-hidden flex flex-col ${approved ? 'border-gray-200' : 'border-gray-200'}`}>
            <div className="relative bg-gray-100" style={{ aspectRatio: '4 / 3' }}>
                <img src={item.file_url} alt={item.title} className={`absolute inset-0 w-full h-full object-cover ${item.admin_status === 'Pending Review' ? 'opacity-70' : ''}`} />
                <span className={`absolute top-3 left-3 inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-bold ${st.chip}`}><i className={`fa ${st.icon}`} aria-hidden="true"></i>{st.label}</span>
                {listed && approved && <span className="absolute top-3 right-3 rounded-full bg-ink text-white px-3 py-1 text-xs font-bold">For sale · {inr(price)}</span>}
            </div>
            <div className="p-5 flex-1 flex flex-col">
                <h3 className="text-lg font-extrabold text-ink">{item.title}</h3>
                <p className="text-sm text-gray-500">{item.course_title} · <span className="font-mono">{shortDate(item.created_at)}</span></p>

                {item.admin_status === 'Pending Review' && (
                    <div className="mt-4 rounded-xl border border-yellow-200 bg-yellow-50 p-3 text-sm text-yellow-900 flex gap-2" role="status">
                        <span className="relative flex h-3 w-3 mt-1 flex-none"><span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-yellow-400 opacity-75"></span><span className="relative inline-flex rounded-full h-3 w-3 bg-yellow-500"></span></span>
                        <span><strong>Locked while in review.</strong> Selling options unlock once a reviewer approves this piece.</span>
                    </div>
                )}
                {item.admin_status === 'Rejected' && (
                    <div className="mt-4 rounded-xl border border-red-200 bg-red-50 p-3 text-sm text-red-900">
                        <strong>Changes requested:</strong> {item.reviewer_feedback || 'See your reviewer’s notes.'}
                        <a href="/dashboard/submissions" className="block mt-2 font-bold underline">Submit a new version</a>
                    </div>
                )}

                <fieldset disabled={!approved} className="mt-4 pt-4 border-t border-gray-100 disabled:opacity-50">
                    <label className="flex items-center gap-3 cursor-pointer">
                        <input type="checkbox" checked={listed && approved} onChange={(e) => { setListed(e.target.checked); if (!e.target.checked) save(false); }}
                               className="h-5 w-5 rounded text-brand border-gray-300 focus:ring-brand" />
                        <span className="font-bold text-ink">List for sale in the student shop</span>
                    </label>
                    {listed && approved && (
                        <div className="mt-4 grid grid-cols-2 gap-3">
                            <label className="block">
                                <span className="font-mono text-xs uppercase tracking-widest text-gray-500">Price (INR)</span>
                                <div className="mt-1 relative">
                                    <span className="absolute left-3 top-1/2 -translate-y-1/2 transform text-gray-500">₹</span>
                                    <input type="number" min="100" step="50" inputMode="decimal" value={price} onChange={(e) => setPrice(e.target.value)}
                                           className="w-full h-11 pl-7 rounded-lg border-gray-300 font-mono focus:border-brand focus:ring-brand" aria-label="Sale price in rupees" />
                                </div>
                            </label>
                            <label className="block">
                                <span className="font-mono text-xs uppercase tracking-widest text-gray-500">In stock</span>
                                <input type="number" min="1" max="1000" value={stock} onChange={(e) => setStock(e.target.value)}
                                       className="mt-1 w-full h-11 rounded-lg border-gray-300 font-mono focus:border-brand focus:ring-brand" aria-label="Inventory count" />
                            </label>
                            <p className="col-span-2 text-xs text-gray-500">{price ? <>Live price: <span className="font-mono font-bold text-ink">{inr(price)}</span> · buyers enquire through ArtistikCity.</> : 'Set a price of at least ₹100.'}</p>
                            <button type="button" onClick={() => save(true)} disabled={state && state.busy} className="col-span-2 h-11 rounded-full bg-ink text-white font-bold hover:bg-gray-800 disabled:bg-gray-400">
                                {state && state.busy ? 'Saving…' : 'Save listing'}
                            </button>
                        </div>
                    )}
                </fieldset>
                {state && state.ok && <p className="mt-2 text-sm font-semibold text-green-700" role="status">{state.ok}</p>}
                {state && state.error && <p className="mt-2 text-sm font-semibold text-red-600" role="alert">{state.error}</p>}
            </div>
        </article>
    );
}

export default function Portfolio() {
    const { submissions = [] } = usePage().props;
    const [items, setItems] = useState(submissions);
    const [filter, setFilter] = useState('all');
    const shown = items.filter((s) => filter === 'all' || (filter === 'sale' ? s.is_listed_for_sale : s.admin_status === filter));
    const counts = {
        all: items.length,
        Approved: items.filter((s) => s.admin_status === 'Approved').length,
        'Pending Review': items.filter((s) => s.admin_status === 'Pending Review').length,
        sale: items.filter((s) => s.is_listed_for_sale).length,
    };
    const onSaved = (updated) => updated && setItems((list) => list.map((x) => (x.id === updated.id ? { ...x, ...updated } : x)));

    return (
        <StudioLayout title="Portfolio & shop" subtitle="Your curated body of work. Approved pieces can be listed for sale; pieces in review stay locked until they're approved." active="/dashboard/portfolio" stage={6}
                      actions={<a href="/student-shop" className="dm-btn dm-btn--line">View the student shop</a>}>
            <div className="flex flex-wrap gap-2" role="tablist">
                {[['all', 'All work'], ['Approved', 'Approved'], ['Pending Review', 'In review'], ['sale', 'For sale']].map(([k, label]) => (
                    <button key={k} type="button" role="tab" aria-selected={filter === k} onClick={() => setFilter(k)}
                            className={`h-10 px-4 rounded-full text-sm font-bold border ${filter === k ? 'bg-ink text-white border-ink' : 'bg-white text-gray-700 border-gray-300 hover:border-ink'}`}>
                        {label} <span className="font-mono opacity-70">{counts[k]}</span>
                    </button>
                ))}
            </div>
            {shown.length === 0 ? (
                <div className="mt-8 rounded-2xl border-2 border-dashed border-gray-300 p-12 text-center">
                    <p className="text-xl font-extrabold text-ink">Nothing here yet</p>
                    <p className="mt-2 text-gray-600">Submit an assignment to start building your portfolio.</p>
                    <a href="/dashboard/submissions" className="mt-6 inline-flex h-12 items-center px-6 rounded-full bg-brand text-white font-bold">Submit work</a>
                </div>
            ) : (
                <div className="mt-8 grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-6">
                    {shown.map((s) => <ArtworkCard key={s.id} item={s} onSaved={onSaved} />)}
                </div>
            )}
        </StudioLayout>
    );
}
