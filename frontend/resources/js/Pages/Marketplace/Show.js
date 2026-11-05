import React, { useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import SiteLayout from '@/Components/Site/SiteLayout';
import ZoomViewer from '@/Components/Marketplace/ZoomViewer';
import ArtCard from '@/Components/Marketplace/ArtCard';
import SaveButton from '@/Components/Wishlist/SaveButton';
import ArtistHub, { useConversation } from '@/Components/Shop/ArtistHub';
import BuyPanel, { startSelection } from '@/Components/Shop/BuyPanel';
import RoomStage, { RoomSwitcher, usePreloadRooms } from '@/Components/Shop/RoomStage';
import { FORMAT_INFO, priceOf, variantLine, inr } from '@/Components/Shop/shop';
import { TrustBadges, StatusChip, HoldBanner, dims, dimsCm, num } from '@/Components/Marketplace/shared';

function Spec({ label, value, hint }) {
    return (
        <div className="py-4 border-b border-gray-100 grid grid-cols-3 gap-4">
            <dt className="text-sm text-gray-500">{label}</dt>
            <dd className="col-span-2 text-sm font-semibold text-ink">{value}{hint && <span className="block font-normal text-gray-500">{hint}</span>}</dd>
        </div>
    );
}

function BuyBox({ p, hold, serverNow, preview, offer, sel, setSel, contact, conversation, customer }) {
    const { saved } = usePage().props;
    const sold = p.stock_status === 'SOLD';
    const anything = offer && offer.formats.some((f) => f.available && (f.format !== 'ORIGINAL' || !sold));
    return (
        <div className="rounded-2xl border border-gray-200 bg-white p-6 sm:p-7 shadow-sm">
            <div className="flex items-center justify-between gap-3">
                <p className="font-mono text-xs uppercase tracking-widest text-gray-500">{p.source === 'STUDENT' ? 'Student original' : 'Studio original'} · {p.year_created}</p>
                <StatusChip status={hold && hold.mine ? 'RESERVED' : p.stock_status} />
            </div>
            <h1 className="mt-3 text-3xl sm:text-4xl font-black tracking-tight text-ink leading-tight">{p.title}</h1>
            <p className="mt-1 text-lg text-gray-600">by <span className="font-bold text-ink">{p.artist_name}</span></p>
            <p className="mt-3 text-sm text-gray-600">{p.medium} on {String(p.surface).toLowerCase()} · {dims(p)} <span className="text-gray-400">({dimsCm(p)})</span></p>
            <div className="mt-4"><TrustBadges p={p} /></div>

            {preview && <p className="mt-4 rounded-xl bg-blue-50 border border-blue-200 p-3 text-sm text-blue-900"><i className="fa fa-eye mr-1" aria-hidden="true"></i>Preview · this listing is <strong>{preview.replace('_', ' ').toLowerCase()}</strong> and not visible to collectors.</p>}

            <div className="mt-6 pt-6 border-t border-gray-100">
                {offer && anything ? (
                    <BuyPanel p={p} offer={offer} sel={sel} setSel={setSel} hold={hold} serverNow={serverNow} preview={preview}
                              contact={contact} conversation={conversation} customer={customer} />
                ) : (
                    <>
                        <p className="text-2xl font-black text-ink">This original has found its collector.</p>
                        <p className="mt-2 text-gray-600">Love the style? Commission something similar, made for you.</p>
                        <a href="/commission/step-1" className="mt-5 flex items-center justify-center h-14 rounded-full bg-ink text-white text-lg font-bold hover:bg-gray-800">Commission a similar piece</a>
                    </>
                )}
            </div>

            {!preview && <div className="mt-3"><SaveButton type="painting" id={p.id} saved={saved} className="dm-heart--block" /></div>}

            <ul className="mt-6 pt-6 border-t border-gray-100 space-y-3 text-sm text-gray-700">
                <li className="flex gap-3"><i className="fa fa-shield text-brand mt-0.5 w-4" aria-hidden="true"></i>Secure payment: UPI, cards and NetBanking (Razorpay) or PayPal</li>
                <li className="flex gap-3"><i className="fa fa-truck text-brand mt-0.5 w-4" aria-hidden="true"></i>Insured, tracked shipping · India 5–8 days · worldwide 10–15 days</li>
            </ul>
        </div>
    );
}

/** "View in a room" tab: the current choice (format and size) on the walls of the photographed rooms. */
function RoomTab({ p, offer, sel }) {
    const [room, setRoom] = useState('living');
    usePreloadRooms();
    const price = offer ? priceOf(offer, sel) : null;
    const w = price && price.width ? price.width : p.width_inches;
    const h = price && price.height ? price.height : p.height_inches;
    return (
        <div className="max-w-5xl">
            <RoomStage room={room} image={p.images && p.images[0] ? (p.images[0].url || p.images[0]) : p.image_url} title={p.title}
                       widthIn={w} heightIn={h} frame={sel.frame || 'NONE'} surface={sel.format === 'POSTER' ? 'paper' : 'canvas'} />
            <RoomSwitcher value={room} onChange={setRoom} />
            <p className="mt-3 text-sm text-gray-500">To scale: {num(w)} × {num(h)} in{sel.format && FORMAT_INFO[sel.format] ? ` · ${FORMAT_INFO[sel.format].name.toLowerCase()}` : ''}. Frames and more rooms on the next step.</p>
        </div>
    );
}

export default function MarketplaceShow() {
    const { painting: p, hold, holds = [], related = [], serverNow, preview, notice, offer, draft, contact, conversation: conv0, customer, openChat } = usePage().props;
    const [tab, setTab] = useState('room');
    const conversation = useConversation(p.id, conv0);
    const originalOk = p.stock_status === 'AVAILABLE' || !!(hold && hold.mine);
    const [sel, setSel] = useState(() => (offer ? startSelection(offer, draft, originalOk) : { format: 'ORIGINAL', frame: 'NONE', qty: 1 }));
    const selPrice = offer ? priceOf(offer, sel) : null;
    const context = selPrice ? `${variantLine(sel, selPrice)} · ${inr(selPrice.total)}` : null;

    return (
        <SiteLayout title={`${p.title} by ${p.artist_name}`}>
            <div className="tw">
                <div className="ac-wide pt-6">
                    <nav aria-label="Breadcrumb" className="text-sm text-gray-500">
                        <a href="/" className="hover:text-ink">Home</a><span className="mx-2">/</span>
                        <a href="/marketplace" className="hover:text-ink">Marketplace</a><span className="mx-2">/</span>
                        <span className="text-ink font-semibold">{p.title}</span>
                    </nav>
                    {notice === 'expired' && (
                        <div className="mt-4 rounded-xl border border-yellow-200 bg-yellow-50 p-4 text-sm text-yellow-900" role="alert">
                            <strong>There's no active hold on this piece for you.</strong> Holds last 15 minutes. If it's still available, reserve it again below.
                        </div>
                    )}
                </div>

                <section className="ac-wide py-8 grid grid-cols-1 lg:grid-cols-12 gap-10">
                    <div className="lg:col-span-7">
                        <ZoomViewer images={p.images} title={p.title} />
                    </div>
                    <aside className="lg:col-span-5">
                        <div className="lg:sticky lg:top-24"><BuyBox p={p} hold={hold} serverNow={serverNow} preview={preview} offer={offer} sel={sel} setSel={setSel}
                                                                   contact={contact} conversation={conversation} customer={customer} /></div>
                    </aside>
                </section>

                {/* ---------- see it at home / specs ---------- */}
                <section className="ac-wide pb-8">
                    <div className="flex gap-2 border-b border-gray-200 overflow-x-auto" role="tablist">
                        {[['room', 'View in a room', 'fa-home'], ['specs', 'Specifications', 'fa-th-list'], ['shipping', 'Shipping & care', 'fa-truck']].map(([k, l, i]) => (
                            <button key={k} type="button" role="tab" aria-selected={tab === k} onClick={() => setTab(k)}
                                    className={`whitespace-nowrap px-4 py-3 text-base font-bold border-b-2 -mb-px ${tab === k ? 'border-ink text-ink' : 'border-transparent text-gray-500 hover:text-ink'}`}>
                                <i className={`fa ${i} mr-2`} aria-hidden="true"></i>{l}
                            </button>
                        ))}
                    </div>
                    <div className="pt-8">
                        {tab === 'room' && <RoomTab p={p} offer={offer} sel={sel} />}
                        {tab === 'specs' && (
                            <dl className="max-w-3xl">
                                <Spec label="Medium" value={p.medium} />
                                <Spec label="Surface" value={p.surface} />
                                <Spec label="Dimensions" value={`${dims(p)} · ${dimsCm(p)}`} hint={Number(p.depth_inches) > 0 ? `Depth ${num(p.depth_inches, 2)} in (${num(p.depth_cm)} cm)` : null} />
                                <Spec label="Framing" value={p.framing} />
                                <Spec label="Year" value={p.year_created} />
                                {p.subject && <Spec label="Subject" value={p.subject} />}
                                {p.style_tags && <Spec label="Style" value={p.style_tags} />}
                                <Spec label="Signature" value={p.is_signed ? 'Hand-signed by the artist' : 'Unsigned'} />
                                <Spec label="Authenticity" value={p.has_certificate ? 'Certificate of authenticity included' : 'Not included'} />
                                <Spec label="Edition" value="1 of 1 · original artwork, not a print" />
                                {p.weight_kg && <Spec label="Artwork weight" value={`${num(p.weight_kg, 2)} kg`} />}
                            </dl>
                        )}
                        {tab === 'shipping' && p.shipping && (
                            <div className="max-w-3xl grid grid-cols-1 sm:grid-cols-2 gap-6">
                                <div className="rounded-2xl border border-gray-200 p-6"><h3 className="text-lg font-extrabold text-ink"><i className="fa fa-map-marker text-brand mr-2" aria-hidden="true"></i>Within India</h3><p className="mt-2 text-gray-600">{p.shipping.india}</p></div>
                                <div className="rounded-2xl border border-gray-200 p-6"><h3 className="text-lg font-extrabold text-ink"><i className="fa fa-globe text-brand mr-2" aria-hidden="true"></i>International</h3><p className="mt-2 text-gray-600">{p.shipping.international}</p></div>
                                <div className="rounded-2xl border border-gray-200 p-6 sm:col-span-2">
                                    <h3 className="text-lg font-extrabold text-ink"><i className="fa fa-cube text-brand mr-2" aria-hidden="true"></i>Packing & freight compliance</h3>
                                    <p className="mt-2 text-gray-600">{p.shipping.compliance}</p>
                                    <p className="mt-3 text-sm text-gray-500 font-mono">Packed ≈ {p.shipping.packed_size_in} in · {p.shipping.packed_weight_kg} kg</p>
                                </div>
                            </div>
                        )}
                    </div>
                </section>

                {/* ---------- story ---------- */}
                <section className="bg-gray-50 border-y border-gray-200 mt-8">
                    <div className="ac-wide py-16 grid grid-cols-1 lg:grid-cols-12 gap-10">
                        <div className="lg:col-span-4">
                            <p className="font-mono text-xs uppercase tracking-widest text-brand">The story</p>
                            <h2 className="mt-2 text-3xl font-black tracking-tight text-ink">Inspiration & creative notes</h2>
                            <p className="mt-4 text-gray-600">In {p.artist_name}'s own words.</p>
                        </div>
                        <div className="lg:col-span-8">
                            <p className="text-xl leading-relaxed text-gray-800">{p.description}</p>
                            {p.artist_notes && (
                                <blockquote className="mt-8 border-l-4 border-brand pl-6">
                                    <p className="text-2xl leading-relaxed font-semibold text-ink" style={{ fontFamily: 'Georgia, serif' }}>“{p.artist_notes}”</p>
                                    <footer className="mt-3 text-sm text-gray-500">— {p.artist_name}</footer>
                                </blockquote>
                            )}
                        </div>
                    </div>
                </section>

                {!preview && contact && (
                    <section className="ac-wide py-16" id="meet-the-artist">
                        <div className="pz-hubwrap">
                            <ArtistHub painting={p} contact={contact} conversation={conversation} customer={customer} autoOpenChat={!!openChat} context={context} />
                        </div>
                    </section>
                )}

                {related.length > 0 && (
                    <section className="ac-wide py-16">
                        <div className="flex items-end justify-between gap-4 mb-8">
                            <h2 className="text-3xl font-black tracking-tight text-ink">You may also love</h2>
                            <a href="/marketplace" className="font-bold text-ink hover:text-brand">All originals <i className="fa fa-arrow-right text-sm" aria-hidden="true"></i></a>
                        </div>
                        <div className="mk-grid mk-grid--related">{related.map((r) => <ArtCard key={r.id} p={r} serverNow={serverNow} />)}</div>
                    </section>
                )}
            </div>
            <div className="tw"><HoldBanner holds={holds} serverNow={serverNow} exceptSlug={p.slug} /></div>
        </SiteLayout>
    );
}
