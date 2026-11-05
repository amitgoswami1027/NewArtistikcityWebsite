import React from 'react';
import { usePage } from '@inertiajs/inertia-react';
import SiteLayout from '@/Components/Site/SiteLayout';
import { inr, usd, num } from '@/Components/Marketplace/shared';

const FLOW = [['PAID', 'Payment confirmed', 'fa-check'], ['PACKED', 'Packed with care', 'fa-cube'], ['SHIPPED', 'On its way', 'fa-truck'], ['DELIVERED', 'Delivered', 'fa-home']];

export default function MarketplaceConfirmation() {
    const { order: o } = usePage().props;
    const refund = o.status === 'REFUND_REQUIRED' || o.status === 'REFUNDED';
    const idx = FLOW.findIndex(([k]) => k === o.status);
    const money = o.currency === 'INR' ? inr(o.amount) : usd(o.amount);
    const when = o.paid_at ? new Date(o.paid_at).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' }) : '';

    return (
        <SiteLayout title={`Order ${o.order_id}`}>
            <div className="tw">
                <section className="ac-wide py-12 max-w-5xl">
                    {refund ? (
                        <div className="rounded-2xl border border-yellow-200 bg-yellow-50 p-8">
                            <p className="font-mono text-xs uppercase tracking-widest text-yellow-800">Order {o.order_id}</p>
                            <h1 className="mt-2 text-3xl font-black tracking-tight text-ink">{o.status === 'REFUNDED' ? 'Your refund has been issued' : 'We\'re refunding your payment'}</h1>
                            <p className="mt-3 text-lg text-gray-700">Your payment of {money} reached us after the 15-minute hold ended, and “{o.title}” had already gone to another collector. {o.status === 'REFUNDED' ? 'The full amount has been returned to your original payment method.' : 'You\'ll receive the full amount back on your original payment method within 5–7 working days.'} We're sorry for the disappointment.</p>
                            <a href="/marketplace" className="mt-6 inline-flex items-center h-12 px-6 rounded-full bg-ink text-white font-bold">Explore other originals</a>
                        </div>
                    ) : (
                        <div className="text-center">
                            <span className="inline-flex w-16 h-16 rounded-full bg-green-100 text-green-700 items-center justify-center text-3xl"><i className="fa fa-check" aria-hidden="true"></i></span>
                            <p className="mt-5 font-mono text-xs uppercase tracking-widest text-brand">Order {o.order_id}</p>
                            <h1 className="mt-2 text-4xl sm:text-5xl font-black tracking-tight text-ink">It's yours.</h1>
                            <p className="mt-3 text-lg text-gray-600">Thank you, {o.buyer_name}. “{o.title}” by {o.artist_name} now belongs to you. A receipt is on its way to <strong>{o.buyer_email}</strong>.</p>
                        </div>
                    )}

                    {!refund && (
                        <ol className="mt-12 grid grid-cols-4 gap-2" aria-label="Delivery progress">
                            {FLOW.map(([k, label, icon], i) => {
                                const done = i <= idx;
                                return (
                                    <li key={k} className="text-center">
                                        <div className="relative flex items-center justify-center">
                                            {i > 0 && <span className={`absolute right-1/2 w-full h-1 ${i <= idx ? 'bg-ink' : 'bg-gray-200'}`} style={{ top: '50%', transform: 'translateY(-50%)' }} aria-hidden="true"></span>}
                                            <span className={`relative z-10 w-11 h-11 rounded-full flex items-center justify-center ${done ? 'bg-ink text-white' : 'bg-white border-2 border-gray-200 text-gray-400'}`}><i className={`fa ${icon}`} aria-hidden="true"></i></span>
                                        </div>
                                        <p className={`mt-2 text-xs sm:text-sm font-bold ${done ? 'text-ink' : 'text-gray-400'}`}>{label}</p>
                                    </li>
                                );
                            })}
                        </ol>
                    )}

                    <div className="mt-12 grid grid-cols-1 md:grid-cols-5 gap-8 rounded-2xl border border-gray-200 bg-white p-6 sm:p-8">
                        <div className="md:col-span-2 bg-gray-50 rounded-xl p-6 flex items-center justify-center"><img src={o.image_url} alt={o.title} className="mk-art max-h-64 w-auto" /></div>
                        <div className="md:col-span-3">
                            <h2 className="text-2xl font-extrabold text-ink">{o.title}</h2>
                            <p className="text-gray-600">{o.artist_name} · {o.medium} on {String(o.surface || '').toLowerCase()} · {num(o.height_inches)} × {num(o.width_inches)} in</p>
                            <dl className="mt-6 grid grid-cols-2 gap-x-6 gap-y-4 text-sm">
                                <div><dt className="text-gray-500">Paid</dt><dd className="font-bold text-ink">{money}{o.currency !== 'INR' && <span className="font-normal text-gray-500"> (≈ {inr(o.price_inr)})</span>}</dd></div>
                                <div><dt className="text-gray-500">Payment</dt><dd className="font-bold text-ink">{o.gateway === 'PAYPAL' ? 'PayPal' : o.gateway === 'RAZORPAY' ? 'Razorpay' : 'Test payment'}{when && <span className="block font-normal text-gray-500">{when}</span>}</dd></div>
                                <div className="col-span-2"><dt className="text-gray-500">Shipping to</dt><dd className="font-semibold text-ink">{o.buyer_name}, {o.address_line1}{o.address_line2 ? `, ${o.address_line2}` : ''}, {o.city}{o.state ? `, ${o.state}` : ''} {o.postal_code}, {o.country}</dd></div>
                                {o.tracking_number && <div className="col-span-2"><dt className="text-gray-500">Tracking</dt><dd className="font-mono font-bold text-ink">{o.courier} · {o.tracking_number}</dd></div>}
                            </dl>
                            {o.has_certificate && <p className="mt-6 rounded-xl bg-gray-50 p-4 text-sm text-gray-700"><i className="fa fa-certificate text-brand mr-2" aria-hidden="true"></i>A signed certificate of authenticity travels with your artwork.</p>}
                        </div>
                    </div>

                    <div className="mt-10 flex flex-wrap justify-center gap-3">
                        <a href="/marketplace" className="inline-flex items-center h-12 px-6 rounded-full bg-ink text-white font-bold hover:bg-gray-800">Keep exploring</a>
                        <button type="button" onClick={() => window.print()} className="inline-flex items-center h-12 px-6 rounded-full border border-gray-300 font-bold hover:border-ink"><i className="fa fa-print mr-2" aria-hidden="true"></i>Print receipt</button>
                    </div>
                </section>
            </div>
        </SiteLayout>
    );
}
