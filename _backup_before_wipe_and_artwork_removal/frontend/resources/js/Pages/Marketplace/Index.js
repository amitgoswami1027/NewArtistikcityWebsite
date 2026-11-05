import React, { useMemo, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import SiteLayout from '@/Components/Site/SiteLayout';
import ArtCard from '@/Components/Marketplace/ArtCard';
import { HoldBanner, inr } from '@/Components/Marketplace/shared';

const SIZES = [['small', 'Small', 'up to 16 in'], ['medium', 'Medium', '16–36 in'], ['large', 'Large', 'over 36 in']];
const ORIENT = [['portrait', 'Portrait'], ['landscape', 'Landscape'], ['square', 'Square']];
const PRICES = [['0-10000', 'Under ₹10,000'], ['10000-25000', '₹10,000–25,000'], ['25000-50000', '₹25,000–50,000'], ['50000-', 'Over ₹50,000']];

function Chip({ on, onClick, children }) {
    return (
        <button type="button" onClick={onClick} aria-pressed={on}
                className={`h-10 px-4 rounded-full text-sm font-bold border whitespace-nowrap transition ${on ? 'bg-ink text-white border-ink' : 'bg-white text-gray-700 border-gray-300 hover:border-ink'}`}>
            {children}
        </button>
    );
}

export default function MarketplaceIndex() {
    const { paintings = [], mediums = [], holds = [], serverNow, initialSource, stats = {} } = usePage().props;
    const [source, setSource] = useState(initialSource === 'student' ? 'STUDENT' : initialSource === 'studio' ? 'STUDIO' : 'ALL');
    const [medium, setMedium] = useState('ALL');
    const [size, setSize] = useState(null);
    const [orient, setOrient] = useState(null);
    const [price, setPrice] = useState(null);
    const [availableOnly, setAvailableOnly] = useState(false);
    const [sort, setSort] = useState('curated');
    const [q, setQ] = useState('');
    const [filtersOpen, setFiltersOpen] = useState(false);

    const shown = useMemo(() => {
        const needle = q.trim().toLowerCase();
        let list = paintings.filter((p) => {
            if (source !== 'ALL' && p.source !== source) return false;
            if (medium !== 'ALL' && p.medium !== medium) return false;
            if (size && p.size_class !== size) return false;
            if (orient && p.orientation !== orient) return false;
            if (availableOnly && p.stock_status !== 'AVAILABLE') return false;
            if (price) {
                const [lo, hi] = price.split('-').map((x) => (x === '' ? null : Number(x)));
                const v = Number(p.final_price);
                if (lo !== null && v < lo) return false;
                if (hi !== null && v >= hi) return false;
            }
            if (needle && !`${p.title} ${p.artist_name} ${p.medium} ${p.subject || ''} ${p.style_tags || ''}`.toLowerCase().includes(needle)) return false;
            return true;
        });
        const rank = { AVAILABLE: 0, RESERVED: 1, SOLD: 2 };
        if (sort === 'price-asc') list = [...list].sort((a, b) => rank[a.stock_status] - rank[b.stock_status] || a.final_price - b.final_price);
        if (sort === 'price-desc') list = [...list].sort((a, b) => rank[a.stock_status] - rank[b.stock_status] || b.final_price - a.final_price);
        if (sort === 'newest') list = [...list].sort((a, b) => (b.published_at || 0) - (a.published_at || 0));
        if (sort === 'size') list = [...list].sort((a, b) => b.height_inches * b.width_inches - a.height_inches * a.width_inches);
        return list;
    }, [paintings, source, medium, size, orient, price, availableOnly, sort, q]);

    const active = [source !== 'ALL', medium !== 'ALL', size, orient, price, availableOnly, q].filter(Boolean).length;
    const clear = () => { setSource('ALL'); setMedium('ALL'); setSize(null); setOrient(null); setPrice(null); setAvailableOnly(false); setQ(''); };
    const featured = paintings.find((p) => p.is_featured && p.stock_status === 'AVAILABLE') || paintings.find((p) => p.stock_status === 'AVAILABLE');

    return (
        <SiteLayout title="Original art marketplace">
            <div className="tw">
                {/* ---------- hero ---------- */}
                <section className="relative overflow-hidden bg-ink text-white">
                    <div className="absolute inset-0 opacity-30" aria-hidden="true" style={{ background: 'radial-gradient(circle at 80% 30%, #e5156b 0, transparent 40%), radial-gradient(circle at 5% 100%, #3b5bdb 0, transparent 35%)' }}></div>
                    <div className="ac-wide relative py-16 sm:py-20 grid grid-cols-1 lg:grid-cols-5 gap-10 items-center">
                        <div className="lg:col-span-3">
                            <nav aria-label="Breadcrumb" className="text-sm text-gray-400"><a href="/" className="hover:text-white">Home</a> <span className="mx-2">/</span> <span className="text-white">Marketplace</span></nav>
                            <p className="mt-6 font-mono text-xs tracking-widest uppercase text-pink-300">The ArtistikCity Marketplace</p>
                            <h1 className="mt-3 text-5xl sm:text-6xl font-black tracking-tight leading-none text-white">Original art.<br /><span className="text-brand">Only one of each.</span></h1>
                            <p className="mt-6 max-w-xl text-lg text-gray-300">Paintings and drawings by our studio artists and reviewed student originals. Every piece is unique, signed, and ships with a certificate of authenticity.</p>
                            <dl className="mt-8 flex flex-wrap gap-8">
                                <div><dt className="text-xs uppercase tracking-widest text-gray-400">Available now</dt><dd className="text-3xl font-black">{stats.available || 0}</dd></div>
                                <div><dt className="text-xs uppercase tracking-widest text-gray-400">Artists</dt><dd className="text-3xl font-black">{stats.artists || 0}</dd></div>
                                <div><dt className="text-xs uppercase tracking-widest text-gray-400">Found a home</dt><dd className="text-3xl font-black">{stats.sold || 0}</dd></div>
                            </dl>
                        </div>
                        {featured && (
                            <a href={`/marketplace/${featured.slug}`} className="lg:col-span-2 group block">
                                <div className="rounded-2xl bg-white bg-opacity-5 p-6 border border-white border-opacity-10">
                                    <img src={featured.image_url} alt={featured.title} className="mk-art w-full h-auto rounded max-h-80 object-contain transform group-hover:scale-105 transition duration-500" />
                                    <div className="mt-4 flex items-end justify-between gap-3">
                                        <div className="min-w-0"><p className="text-xs uppercase tracking-widest text-pink-300">Featured</p><p className="font-bold truncate">{featured.title}</p><p className="text-sm text-gray-400 truncate">{featured.artist_name}</p></div>
                                        <p className="text-xl font-black whitespace-nowrap">{inr(featured.final_price)}</p>
                                    </div>
                                </div>
                            </a>
                        )}
                    </div>
                </section>

                {/* ---------- trust strip ---------- */}
                <section className="border-b border-gray-200 bg-gray-50">
                    <ul className="ac-wide py-4 grid grid-cols-2 lg:grid-cols-4 gap-4 text-sm text-gray-700">
                        {[['fa-diamond', '1-of-1 originals, never prints'], ['fa-certificate', 'Signed certificate of authenticity'], ['fa-lock', '15-minute exclusive hold at checkout'], ['fa-truck', 'Insured, tracked delivery worldwide']].map(([i, t]) => (
                            <li key={t} className="flex items-center gap-2"><i className={`fa ${i} text-brand`} aria-hidden="true"></i>{t}</li>
                        ))}
                    </ul>
                </section>

                {/* ---------- filters ---------- */}
                <section className="ac-wide pt-10">
                    <div className="flex flex-wrap items-center gap-3">
                        <div className="inline-flex rounded-full border border-gray-300 p-1 bg-white" role="tablist" aria-label="Artist">
                            {[['ALL', 'All originals'], ['STUDIO', 'Studio artists'], ['STUDENT', 'Student originals']].map(([k, l]) => (
                                <button key={k} type="button" role="tab" aria-selected={source === k} onClick={() => setSource(k)}
                                        className={`h-9 px-4 rounded-full text-sm font-bold ${source === k ? 'bg-ink text-white' : 'text-gray-600 hover:text-ink'}`}>{l}</button>
                            ))}
                        </div>
                        <div className="relative flex-1 min-w-[220px] max-w-sm">
                            <i className="fa fa-search absolute left-4 top-1/2 transform -translate-y-1/2 text-gray-400" aria-hidden="true"></i>
                            <input type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search title, artist, subject"
                                   className="w-full h-11 pl-10 pr-4 rounded-full border border-gray-300 bg-white text-sm focus:border-brand focus:ring-brand" aria-label="Search the marketplace" />
                        </div>
                        <button type="button" onClick={() => setFiltersOpen(!filtersOpen)} aria-expanded={filtersOpen}
                                className="inline-flex items-center gap-2 h-11 px-5 rounded-full border border-gray-300 bg-white text-sm font-bold hover:border-ink">
                            <i className="fa fa-sliders" aria-hidden="true"></i>Filters{active ? <span className="rounded-full bg-brand text-white text-xs px-2">{active}</span> : null}
                        </button>
                        <label className="ml-auto flex items-center gap-2 text-sm text-gray-600">
                            <span className="hidden sm:inline">Sort</span>
                            <select value={sort} onChange={(e) => setSort(e.target.value)} className="h-11 rounded-full border border-gray-300 bg-white pl-4 pr-8 text-sm font-bold focus:border-brand focus:ring-brand" aria-label="Sort">
                                <option value="curated">Curated</option>
                                <option value="newest">Newest</option>
                                <option value="price-asc">Price: low to high</option>
                                <option value="price-desc">Price: high to low</option>
                                <option value="size">Largest first</option>
                            </select>
                        </label>
                    </div>

                    <div className="mt-4 flex gap-2 overflow-x-auto pb-2" aria-label="Medium">
                        <Chip on={medium === 'ALL'} onClick={() => setMedium('ALL')}>All mediums</Chip>
                        {mediums.map((m) => <Chip key={m} on={medium === m} onClick={() => setMedium(medium === m ? 'ALL' : m)}>{m}</Chip>)}
                    </div>

                    {filtersOpen && (
                        <div className="mt-4 rounded-2xl border border-gray-200 bg-white p-6 grid grid-cols-1 md:grid-cols-4 gap-6">
                            <fieldset>
                                <legend className="font-mono text-xs uppercase tracking-widest text-gray-500">Size</legend>
                                <div className="mt-3 flex flex-col gap-2">
                                    {SIZES.map(([k, l, hint]) => (
                                        <label key={k} className="flex items-center gap-3 cursor-pointer text-sm"><input type="radio" name="size" checked={size === k} onChange={() => setSize(k)} className="text-brand focus:ring-brand" /><span className="font-bold text-ink">{l}</span><span className="text-gray-500">{hint}</span></label>
                                    ))}
                                    {size && <button type="button" className="text-left text-sm font-bold text-brand" onClick={() => setSize(null)}>Any size</button>}
                                </div>
                            </fieldset>
                            <fieldset>
                                <legend className="font-mono text-xs uppercase tracking-widest text-gray-500">Orientation</legend>
                                <div className="mt-3 flex flex-col gap-2">
                                    {ORIENT.map(([k, l]) => (
                                        <label key={k} className="flex items-center gap-3 cursor-pointer text-sm"><input type="radio" name="orient" checked={orient === k} onChange={() => setOrient(k)} className="text-brand focus:ring-brand" /><span className="font-bold text-ink">{l}</span></label>
                                    ))}
                                    {orient && <button type="button" className="text-left text-sm font-bold text-brand" onClick={() => setOrient(null)}>Any orientation</button>}
                                </div>
                            </fieldset>
                            <fieldset>
                                <legend className="font-mono text-xs uppercase tracking-widest text-gray-500">Price</legend>
                                <div className="mt-3 flex flex-col gap-2">
                                    {PRICES.map(([k, l]) => (
                                        <label key={k} className="flex items-center gap-3 cursor-pointer text-sm"><input type="radio" name="price" checked={price === k} onChange={() => setPrice(k)} className="text-brand focus:ring-brand" /><span className="font-bold text-ink">{l}</span></label>
                                    ))}
                                    {price && <button type="button" className="text-left text-sm font-bold text-brand" onClick={() => setPrice(null)}>Any price</button>}
                                </div>
                            </fieldset>
                            <div className="flex flex-col gap-4">
                                <label className="flex items-center gap-3 cursor-pointer text-sm font-bold text-ink">
                                    <input type="checkbox" checked={availableOnly} onChange={(e) => setAvailableOnly(e.target.checked)} className="h-5 w-5 rounded text-brand focus:ring-brand" />Available to buy now
                                </label>
                                {active > 0 && <button type="button" onClick={clear} className="self-start h-10 px-4 rounded-full border border-gray-300 text-sm font-bold hover:border-ink">Clear all filters</button>}
                            </div>
                        </div>
                    )}

                    <p className="mt-6 text-sm text-gray-500" aria-live="polite">{shown.length} {shown.length === 1 ? 'artwork' : 'artworks'}{active ? ' match your filters' : ''}</p>
                </section>

                {/* ---------- masonry gallery ---------- */}
                <section className="ac-wide pt-6 pb-24">
                    {shown.length ? (
                        <div className="mk-masonry">
                            {shown.map((p) => <ArtCard key={p.id} p={p} serverNow={serverNow} />)}
                        </div>
                    ) : (
                        <div className="rounded-2xl border-2 border-dashed border-gray-300 p-16 text-center">
                            <p className="text-2xl font-black text-ink">Nothing matches just yet</p>
                            <p className="mt-2 text-gray-600">Try another medium or price range, or commission a painting made just for you.</p>
                            <div className="mt-6 flex flex-wrap justify-center gap-3">
                                {active > 0 && <button type="button" onClick={clear} className="h-12 px-6 rounded-full bg-ink text-white font-bold">Clear filters</button>}
                                <a href="/commission/step-1" className="inline-flex items-center h-12 px-6 rounded-full border border-gray-300 font-bold hover:border-ink">Commission a painting</a>
                            </div>
                        </div>
                    )}
                </section>

                {/* ---------- sell / commission ---------- */}
                <section className="bg-gray-50 border-t border-gray-200">
                    <div className="ac-wide py-16 grid grid-cols-1 md:grid-cols-2 gap-6">
                        <div className="rounded-2xl bg-white border border-gray-200 p-8">
                            <i className="fa fa-paint-brush text-2xl text-brand" aria-hidden="true"></i>
                            <h2 className="mt-3 text-2xl font-black tracking-tight text-ink">Want something made for your wall?</h2>
                            <p className="mt-2 text-gray-600">Commission a custom painting in your size, subject and frame.</p>
                            <a href="/commission/step-1" className="mt-5 inline-flex items-center h-11 px-5 rounded-full bg-ink text-white font-bold hover:bg-gray-800">Start a commission</a>
                        </div>
                        <div className="rounded-2xl bg-white border border-gray-200 p-8">
                            <i className="fa fa-graduation-cap text-2xl text-brand" aria-hidden="true"></i>
                            <h2 className="mt-3 text-2xl font-black tracking-tight text-ink">Are you an ArtistikCity student?</h2>
                            <p className="mt-2 text-gray-600">Once a reviewer approves your coursework, you can list it here as a 1-of-1 original.</p>
                            <a href="/dashboard/portfolio" className="mt-5 inline-flex items-center h-11 px-5 rounded-full border border-gray-300 font-bold hover:border-ink">Sell from my portfolio</a>
                        </div>
                    </div>
                </section>
            </div>
            <div className="tw"><HoldBanner holds={holds} serverNow={serverNow} /></div>
        </SiteLayout>
    );
}
