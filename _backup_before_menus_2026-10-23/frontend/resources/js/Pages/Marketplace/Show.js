import React, { useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import axios from 'axios';
import SiteLayout from '@/Components/Site/SiteLayout';
import ZoomViewer from '@/Components/Marketplace/ZoomViewer';
import RoomView from '@/Components/Marketplace/RoomView';
import ArtCard from '@/Components/Marketplace/ArtCard';
import { Price, TrustBadges, StatusChip, HoldBanner, useCountdown, mmss, dims, dimsCm, num, errorText, Spinner, inr } from '@/Components/Marketplace/shared';

function Spec({ label, value, hint }) {
    return (
        <div className="py-4 border-b border-gray-100 grid grid-cols-3 gap-4">
            <dt className="text-sm text-gray-500">{label}</dt>
            <dd className="col-span-2 text-sm font-semibold text-ink">{value}{hint && <span className="block font-normal text-gray-500">{hint}</span>}</dd>
        </div>
    );
}

function BuyBox({ p, hold, serverNow, preview }) {
    const [busy, setBusy] = useState(false);
    const [err, setErr] = useState(null);
    const [conflict, setConflict] = useState(null);
    const mineLeft = useCountdown(hold && hold.mine ? hold.expiresAt : null, serverNow);
    const otherLeft = useCountdown(p.stock_status === 'RESERVED' && !(hold && hold.mine) ? p.reserved_until : null, serverNow);
    const mine = hold && hold.mine && mineLeft > 0;
    const heldByOther = p.stock_status === 'RESERVED' && !mine && otherLeft > 0;
    const sold = p.stock_status === 'SOLD';

    const reserve = (switchHold = false) => {
        setBusy(true); setErr(null);
        axios.post(`/api/v1/marketplace/paintings/${p.id}/reserve`, { switch: switchHold })
            .then((r) => { window.location.href = r.data.checkoutUrl; })
            .catch((e) => {
                setBusy(false);
                const d = e.response && e.response.data;
                if (d && d.code === 'HOLD_LIMIT') setConflict(d);
                else if (d && (d.code === 'HELD_BY_OTHER' || d.code === 'JUST_TAKEN' || d.code === 'SOLD')) { setErr(d.message); setTimeout(() => window.location.reload(), 2200); }
                else setErr(errorText(e));
            });
    };
    const release = () => {
        setBusy(true);
        axios.delete(`/api/v1/marketplace/reservations/${p.id}`).finally(() => window.location.reload());
    };

    return (
        <div className="rounded-2xl border border-gray-200 bg-white p-6 sm:p-7 shadow-sm">
            <div className="flex items-center justify-between gap-3">
                <p className="font-mono text-xs uppercase tracking-widest text-gray-500">{p.source === 'STUDENT' ? 'Student original' : 'Studio original'} · {p.year_created}</p>
                <StatusChip status={heldByOther || mine ? 'RESERVED' : sold ? 'SOLD' : p.stock_status} />
            </div>
            <h1 className="mt-3 text-3xl sm:text-4xl font-black tracking-tight text-ink leading-tight">{p.title}</h1>
            <p className="mt-1 text-lg text-gray-600">by <span className="font-bold text-ink">{p.artist_name}</span></p>
            <p className="mt-3 text-sm text-gray-600">{p.medium} on {String(p.surface).toLowerCase()} · {dims(p)} <span className="text-gray-400">({dimsCm(p)})</span></p>
            <div className="mt-4"><TrustBadges p={p} /></div>

            <div className="mt-6 pt-6 border-t border-gray-100">
                {sold ? (
                    <>
                        <p className="text-2xl font-black text-ink">This original has found its collector.</p>
                        <p className="mt-2 text-gray-600">Love the style? Commission something similar, made for you.</p>
                        <a href="/commission/step-1" className="mt-5 flex items-center justify-center h-14 rounded-full bg-ink text-white text-lg font-bold hover:bg-gray-800">Commission a similar piece</a>
                    </>
                ) : (
                    <>
                        <Price p={p} size="lg" />
                        {Number(p.discount_percentage) > 0 && <p className="mt-1 text-sm font-semibold text-green-700">You save {inr(p.base_price - p.final_price)}</p>}
                        <p className="mt-1 text-xs text-gray-500">Price in Indian rupees, taxes included. International collectors pay the USD equivalent via PayPal.</p>

                        {preview && <p className="mt-4 rounded-xl bg-blue-50 border border-blue-200 p-3 text-sm text-blue-900"><i className="fa fa-eye mr-1" aria-hidden="true"></i>Preview · this listing is <strong>{preview.replace('_', ' ').toLowerCase()}</strong> and not visible to collectors.</p>}

                        {mine ? (
                            <div className="mt-5">
                                <div className="rounded-xl bg-ink text-white p-4 flex items-center gap-3" role="status">
                                    <i className="fa fa-lock text-pink-300 text-xl" aria-hidden="true"></i>
                                    <p className="text-sm">Held exclusively for you for <span className="font-mono font-bold text-lg">{mmss(mineLeft)}</span></p>
                                </div>
                                <a href={`/marketplace/${p.slug}/checkout`} className="mt-3 flex items-center justify-center h-14 rounded-full bg-brand text-white text-lg font-bold hover:bg-brand-dark">Continue to checkout</a>
                                <button type="button" onClick={release} disabled={busy} className="mt-2 w-full h-11 rounded-full text-sm font-bold text-gray-600 hover:text-ink">Release my hold</button>
                            </div>
                        ) : heldByOther ? (
                            <div className="mt-5 rounded-xl border border-yellow-200 bg-yellow-50 p-4 text-sm text-yellow-900" role="status">
                                <p className="font-bold"><i className="fa fa-hourglass-half mr-1" aria-hidden="true"></i>Another collector is checking out.</p>
                                <p className="mt-1">If they don't complete payment, it returns to the gallery in <span className="font-mono font-bold">{mmss(otherLeft)}</span>. Refresh then to reserve it.</p>
                            </div>
                        ) : !preview && (p.stock_status === 'AVAILABLE' || p.stock_status === 'RESERVED') ? (
                            <div className="mt-5">
                                <button type="button" onClick={() => reserve(false)} disabled={busy}
                                        className="w-full flex items-center justify-center gap-2 h-14 rounded-full bg-brand text-white text-lg font-bold hover:bg-brand-dark disabled:opacity-60">
                                    {busy ? <Spinner /> : <i className="fa fa-lock" aria-hidden="true"></i>} Reserve this original
                                </button>
                                <p className="mt-3 text-center text-xs text-gray-500">Reserving holds it only for you for 15 minutes while you check out. No payment is taken yet.</p>
                            </div>
                        ) : null}
                        {err && <p className="mt-3 rounded-xl bg-red-50 border border-red-200 p-3 text-sm font-semibold text-red-700" role="alert">{err}</p>}
                        {conflict && (
                            <div className="mt-3 rounded-xl border border-gray-300 p-4 text-sm" role="alertdialog" aria-label="You're already holding a piece">
                                <p className="font-bold text-ink">You're already holding “{conflict.heldTitle}”.</p>
                                <p className="mt-1 text-gray-600">You can hold one original at a time. Switch your hold to this piece?</p>
                                <div className="mt-3 flex flex-wrap gap-2">
                                    <button type="button" onClick={() => { setConflict(null); reserve(true); }} className="h-10 px-4 rounded-full bg-ink text-white font-bold">Switch to this piece</button>
                                    <a href={`/marketplace/${conflict.heldSlug}/checkout`} className="inline-flex items-center h-10 px-4 rounded-full border border-gray-300 font-bold hover:border-ink">Go to my held piece</a>
                                </div>
                            </div>
                        )}
                    </>
                )}
            </div>

            <ul className="mt-6 pt-6 border-t border-gray-100 space-y-3 text-sm text-gray-700">
                <li className="flex gap-3"><i className="fa fa-shield text-brand mt-0.5 w-4" aria-hidden="true"></i>Secure payment: UPI, cards and NetBanking (Razorpay) or PayPal</li>
                <li className="flex gap-3"><i className="fa fa-truck text-brand mt-0.5 w-4" aria-hidden="true"></i>Insured, tracked shipping · India 5–8 days · worldwide 10–15 days</li>
                <li className="flex gap-3"><i className="fa fa-comments-o text-brand mt-0.5 w-4" aria-hidden="true"></i>Questions? <a href="/contact" className="font-bold underline">Ask the studio</a> before you buy</li>
            </ul>
        </div>
    );
}

export default function MarketplaceShow() {
    const { painting: p, hold, holds = [], related = [], serverNow, preview, notice } = usePage().props;
    const [tab, setTab] = useState('room');

    return (
        <SiteLayout title={`${p.title} by ${p.artist_name}`}>
            <div className="tw">
                <div className="ac-wide pt-6">
                    <nav aria-label="Breadcrumb" className="text-sm text-gray-500">
                        <a href="/" className="hover:text-ink">Home</a><span className="mx-2">/</span>
                        <a href="/marketplace" className="hover:text-ink">Marketplace</a><span className="mx-2">/</span>
                        <a href={`/marketplace?source=${p.source === 'STUDENT' ? 'student' : 'studio'}`} className="hover:text-ink">{p.source === 'STUDENT' ? 'Student originals' : 'Studio originals'}</a><span className="mx-2">/</span>
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
                        <div className="lg:sticky lg:top-24"><BuyBox p={p} hold={hold} serverNow={serverNow} preview={preview} /></div>
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
                        {tab === 'room' && <div className="max-w-4xl"><RoomView painting={p} /></div>}
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

                {related.length > 0 && (
                    <section className="ac-wide py-16">
                        <div className="flex items-end justify-between gap-4 mb-8">
                            <h2 className="text-3xl font-black tracking-tight text-ink">You may also love</h2>
                            <a href="/marketplace" className="font-bold text-ink hover:text-brand">All originals <i className="fa fa-arrow-right text-sm" aria-hidden="true"></i></a>
                        </div>
                        <div className="mk-masonry">{related.map((r) => <ArtCard key={r.id} p={r} serverNow={serverNow} />)}</div>
                    </section>
                )}
            </div>
            <div className="tw"><HoldBanner holds={holds} serverNow={serverNow} exceptSlug={p.slug} /></div>
        </SiteLayout>
    );
}
