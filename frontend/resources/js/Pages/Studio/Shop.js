import React, { useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import SiteLayout from '@/Components/Site/SiteLayout';
import { inr } from '@/Components/Studio/Journey';

export default function Shop() {
    const { listings = [] } = usePage().props;
    const [open, setOpen] = useState(null);
    return (
        <SiteLayout title="Student shop">
            <div className="ac-wide">
                <ul className="dm-crumbs"><li><a href="/">Home</a></li><li>Student shop</li></ul>
                <div className="dm-listhead" style={{ paddingTop: 8 }}>
                    <span className="ac-eyebrow">Exhibit &amp; sell</span>
                    <h1>Student shop</h1>
                    <p>Original artwork by ArtistikCity students. Every piece has been reviewed by an instructor before it goes on sale.</p>
                </div>
            </div>
            <div className="tw">
                <div className="ac-wide py-10">
                    {listings.length === 0 ? (
                        <div className="rounded-2xl border-2 border-dashed border-gray-300 p-12 text-center">
                            <p className="text-xl font-extrabold text-ink">The first pieces are on their way</p>
                            <p className="mt-2 text-gray-600">Approved student work appears here once students list it for sale.</p>
                            <a href="/how-it-works" className="mt-6 inline-flex h-12 items-center px-6 rounded-full bg-ink text-white font-bold">How it works</a>
                        </div>
                    ) : (
                        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
                            {listings.map((l) => (
                                <article key={l.id} className="group">
                                    <button type="button" onClick={() => setOpen(l)} className="block w-full rounded-xl overflow-hidden bg-gray-100 relative" style={{ aspectRatio: '4 / 5' }}>
                                        <img src={l.file_url} alt={l.title} className="absolute inset-0 w-full h-full object-cover transform transition-transform duration-500 group-hover:scale-105" loading="lazy" />
                                    </button>
                                    <h2 className="mt-3 text-lg font-extrabold text-ink">{l.title}</h2>
                                    <p className="text-sm text-gray-500">by {l.artist_name} · {l.course_title}</p>
                                    <p className="mt-1 font-mono font-bold text-ink">{inr(l.sale_price)} <span className="text-xs font-normal text-gray-500">· {l.inventory_count} available</span></p>
                                </article>
                            ))}
                        </div>
                    )}
                </div>

                {open && (
                    <div className="fixed inset-0 z-50 bg-black bg-opacity-70 flex items-center justify-center p-4" role="dialog" aria-modal="true" aria-label={open.title} onClick={() => setOpen(null)}>
                        <div className="bg-white rounded-2xl max-w-4xl w-full grid grid-cols-1 md:grid-cols-2 overflow-hidden" onClick={(e) => e.stopPropagation()}>
                            <img src={open.file_url} alt={open.title} className="w-full h-full object-cover max-h-[70vh]" />
                            <div className="p-8 flex flex-col">
                                <button type="button" onClick={() => setOpen(null)} className="self-end text-gray-500 hover:text-ink" aria-label="Close"><i className="fa fa-times text-xl" aria-hidden="true"></i></button>
                                <p className="font-mono text-xs uppercase tracking-widest text-brand">Original student artwork</p>
                                <h2 className="mt-2 text-3xl font-black tracking-tight text-ink">{open.title}</h2>
                                <p className="mt-1 text-gray-600">by {open.artist_name} · made in “{open.course_title}”</p>
                                {open.description && <p className="mt-4 text-gray-700">{open.description}</p>}
                                <p className="mt-6 text-3xl font-black text-ink font-mono">{inr(open.sale_price)}</p>
                                <a href={`/contact?subject=${encodeURIComponent('Buy artwork: ' + open.title + ' (#' + open.submission_id + ')')}`}
                                   className="mt-6 inline-flex justify-center items-center h-12 rounded-full bg-brand text-white font-bold hover:bg-brand-dark">Enquire to buy</a>
                                <p className="mt-3 text-xs text-gray-500">ArtistikCity connects you with the artist and handles payment and delivery.</p>
                            </div>
                        </div>
                    </div>
                )}
            </div>
        </SiteLayout>
    );
}
