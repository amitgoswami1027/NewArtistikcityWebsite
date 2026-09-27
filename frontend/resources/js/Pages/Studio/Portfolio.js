import React, { useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import axios from 'axios';
import StudioLayout from '@/Components/Studio/StudioLayout';
import { statusStyle, shortDate, inr } from '@/Components/Studio/Journey';

const LISTING = {
    DRAFT: { label: 'Draft · not live', chip: 'bg-gray-100 text-gray-700 border-gray-200', icon: 'fa-pencil' },
    PENDING_REVIEW: { label: 'In moderator review', chip: 'bg-blue-50 text-blue-800 border-blue-200', icon: 'fa-hourglass-half' },
    AVAILABLE: { label: 'Live in the marketplace', chip: 'bg-green-50 text-green-800 border-green-200', icon: 'fa-check-circle' },
    RESERVED: { label: 'A collector is checking out', chip: 'bg-yellow-50 text-yellow-800 border-yellow-200', icon: 'fa-lock' },
    SOLD: { label: 'Sold', chip: 'bg-ink text-white border-ink', icon: 'fa-trophy' },
};

function SellPanel({ item, listing, onWithdrawn }) {
    const approved = item.admin_status === 'Approved';
    const [busy, setBusy] = useState(false);
    const [msg, setMsg] = useState(null);
    if (!approved) return null;
    if (!listing) {
        return (
            <div className="mt-4 pt-4 border-t border-gray-100">
                <a href={`/dashboard/portfolio/sell/${item.id}`} className="flex items-center justify-center gap-2 h-11 rounded-full bg-ink text-white font-bold hover:bg-gray-800">
                    <i className="fa fa-tag" aria-hidden="true"></i>Sell as a 1-of-1 original
                </a>
                <p className="mt-2 text-xs text-gray-500 text-center">Set the story, size and price. A moderator reviews it before it goes live.</p>
            </div>
        );
    }
    const st = LISTING[listing.stock_status] || LISTING.DRAFT;
    const rejected = listing.stock_status === 'DRAFT' && listing.review_notes && listing.review_notes !== 'Approved.';
    const withdraw = () => {
        setBusy(true);
        axios.post(`/api/v1/marketplace/student/paintings/${listing.id}/withdraw`).then((r) => { setMsg(r.data.message); onWithdrawn(listing.id); })
            .catch((e) => setMsg((e.response && e.response.data && e.response.data.message) || 'Could not withdraw.')).finally(() => setBusy(false));
    };
    return (
        <div className="mt-4 pt-4 border-t border-gray-100">
            <div className="flex items-center justify-between gap-2">
                <span className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-bold ${st.chip}`}><i className={`fa ${st.icon}`} aria-hidden="true"></i>{st.label}</span>
                <span className="font-mono font-bold text-ink">{inr(listing.final_price)}</span>
            </div>
            {rejected && <p className="mt-3 rounded-xl border border-red-200 bg-red-50 p-3 text-sm text-red-900"><strong>Moderator notes:</strong> {listing.review_notes}</p>}
            <div className="mt-3 flex flex-wrap gap-2">
                {['DRAFT', 'PENDING_REVIEW', 'AVAILABLE'].includes(listing.stock_status) && (
                    <a href={`/dashboard/portfolio/sell/${item.id}`} className="inline-flex items-center h-10 px-4 rounded-full bg-ink text-white text-sm font-bold">{listing.stock_status === 'DRAFT' ? (rejected ? 'Fix & resubmit' : 'Finish listing') : 'Edit listing'}</a>
                )}
                {['AVAILABLE', 'RESERVED', 'SOLD'].includes(listing.stock_status) && <a href={`/marketplace/${listing.slug}`} className="inline-flex items-center h-10 px-4 rounded-full border border-gray-300 text-sm font-bold hover:border-ink">View in marketplace</a>}
                {['AVAILABLE', 'PENDING_REVIEW'].includes(listing.stock_status) && <button type="button" onClick={withdraw} disabled={busy} className="inline-flex items-center h-10 px-4 rounded-full text-sm font-bold text-gray-600 hover:text-red-600">Withdraw</button>}
            </div>
            {msg && <p className="mt-2 text-sm font-semibold text-gray-700" role="status">{msg}</p>}
        </div>
    );
}

function ArtworkCard({ item, listing, onWithdrawn }) {
    const st = statusStyle(item.admin_status);
    const forSale = listing && ['AVAILABLE', 'RESERVED'].includes(listing.stock_status);
    return (
        <article className="rounded-2xl border border-gray-200 bg-white overflow-hidden flex flex-col">
            <div className="relative bg-gray-100" style={{ aspectRatio: '4 / 3' }}>
                <img src={item.file_url} alt={item.title} className={`absolute inset-0 w-full h-full object-cover ${item.admin_status === 'Pending Review' ? 'opacity-70' : ''}`} />
                <span className={`absolute top-3 left-3 inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-bold ${st.chip}`}><i className={`fa ${st.icon}`} aria-hidden="true"></i>{st.label}</span>
                {forSale && <span className="absolute top-3 right-3 rounded-full bg-ink text-white px-3 py-1 text-xs font-bold">For sale · {inr(listing.final_price)}</span>}
                {listing && listing.stock_status === 'SOLD' && <span className="absolute top-3 right-3 rounded-full bg-brand text-white px-3 py-1 text-xs font-bold">Sold</span>}
            </div>
            <div className="p-5 flex-1 flex flex-col">
                <h3 className="text-lg font-extrabold text-ink">{item.title}</h3>
                <p className="text-sm text-gray-500">{item.course_title} · <span className="font-mono">{shortDate(item.created_at)}</span></p>

                {item.admin_status === 'Pending Review' && (
                    <div className="mt-4 rounded-xl border border-yellow-200 bg-yellow-50 p-3 text-sm text-yellow-900 flex gap-2" role="status">
                        <span className="relative flex h-3 w-3 mt-1 flex-none"><span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-yellow-400 opacity-75"></span><span className="relative inline-flex rounded-full h-3 w-3 bg-yellow-500"></span></span>
                        <span><strong>Locked while in review.</strong> Selling unlocks once a reviewer approves this piece.</span>
                    </div>
                )}
                {item.admin_status === 'Rejected' && (
                    <div className="mt-4 rounded-xl border border-red-200 bg-red-50 p-3 text-sm text-red-900">
                        <strong>Changes requested:</strong> {item.reviewer_feedback || 'See your reviewer’s notes.'}
                        <a href="/dashboard/submissions" className="block mt-2 font-bold underline">Submit a new version</a>
                    </div>
                )}
                <div className="flex-1"></div>
                <SellPanel item={item} listing={listing} onWithdrawn={onWithdrawn} />
            </div>
        </article>
    );
}

export default function Portfolio() {
    const { submissions = [], listings = [] } = usePage().props;
    const [items] = useState(submissions);
    const [listed, setListed] = useState(listings);
    const bySub = {};
    listed.forEach((l) => { bySub[String(l.submission_id)] = l; });
    const saleOf = (s) => bySub[String(s.id)];
    const onWithdrawn = (id) => setListed((ls) => ls.map((l) => (l.id === id ? { ...l, stock_status: 'DRAFT' } : l)));
    const [filter, setFilter] = useState('all');
    const shown = items.filter((s) => filter === 'all' || (filter === 'sale' ? !!saleOf(s) : s.admin_status === filter));
    const counts = {
        all: items.length,
        Approved: items.filter((s) => s.admin_status === 'Approved').length,
        'Pending Review': items.filter((s) => s.admin_status === 'Pending Review').length,
        sale: items.filter((s) => !!saleOf(s)).length,
    };

    return (
        <StudioLayout title="Portfolio & shop" subtitle="Your curated body of work. Approved pieces can be sold as 1-of-1 originals in the ArtistikCity Marketplace; pieces in review stay locked until they're approved." active="/dashboard/portfolio" stage={6}
                      actions={<a href="/marketplace?source=student" className="dm-btn dm-btn--line">Student originals in the marketplace</a>}>
            <div className="flex flex-wrap gap-2" role="tablist">
                {[['all', 'All work'], ['Approved', 'Approved'], ['Pending Review', 'In review'], ['sale', 'Marketplace listings']].map(([k, label]) => (
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
                    {shown.map((s) => <ArtworkCard key={s.id} item={s} listing={saleOf(s)} onWithdrawn={onWithdrawn} />)}
                </div>
            )}
        </StudioLayout>
    );
}
