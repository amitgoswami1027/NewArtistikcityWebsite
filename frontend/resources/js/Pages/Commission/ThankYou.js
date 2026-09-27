import React, { useEffect } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import SiteLayout from '@/Components/Site/SiteLayout';
import { clearCommission, THEMES, FULFILLMENTS, FRAMES, labelOf, usd, inr } from '@/Components/Commission/CommissionStore';

const TIMELINE = [
    { key: 'QUEUED', title: 'Order received', text: 'Your commission is in the artist’s queue.' },
    { key: 'PROOF_SENT', title: 'Digital proof', text: 'You’ll get a draft by email to approve or request changes.' },
    { key: 'IN_PRODUCTION', title: 'Painting in progress', text: 'Your piece is painted by hand.' },
    { key: 'SHIPPED', title: 'Delivered', text: 'Download link or tracked shipping.' },
];

export default function ThankYou() {
    const { order } = usePage().props;
    useEffect(() => { clearCommission(); }, []);
    const paid = order && order.order_status !== 'PAYMENT_PENDING';
    const idx = order ? TIMELINE.findIndex((t) => t.key === order.order_status) : -1;

    return (
        <SiteLayout title="Thank you">
            <div className="tw">
                <div className="ac-wide" style={{ maxWidth: 960, paddingTop: 64, paddingBottom: 72 }}>
                    {!order ? (
                        <div className="text-center">
                            <h1 className="text-3xl font-extrabold text-ink">We couldn't find that order</h1>
                            <p className="mt-3 text-gray-600">If you completed a payment, check your email for the confirmation or contact us.</p>
                            <a href="/commission/step-1" className="mt-6 inline-flex h-12 items-center px-6 rounded-full bg-brand text-white font-bold">Start a new commission</a>
                        </div>
                    ) : (
                        <>
                            <div className="text-center">
                                <span className={`inline-flex items-center justify-center w-16 h-16 rounded-full text-3xl ${paid ? 'bg-green-100 text-green-600' : 'bg-yellow-100 text-yellow-700'}`}>
                                    <i className={`fa ${paid ? 'fa-check' : 'fa-clock-o'}`} aria-hidden="true"></i>
                                </span>
                                <h1 className="mt-5 text-3xl sm:text-4xl font-extrabold text-ink">{paid ? 'Thank you! Your commission is booked.' : 'We’re confirming your payment'}</h1>
                                <p className="mt-3 text-gray-600">Order <strong className="text-ink">{order.order_id}</strong> · a confirmation is on its way to {order.customer_email}.</p>
                            </div>

                            <ol className="mt-10 grid grid-cols-1 sm:grid-cols-4 gap-4">
                                {TIMELINE.map((t, i) => (
                                    <li key={t.key} className={`rounded-xl border p-4 ${i <= idx ? 'border-brand bg-brand-soft' : 'border-gray-200 bg-white'}`}>
                                        <span className={`text-xs font-bold ${i <= idx ? 'text-brand' : 'text-gray-400'}`}>STEP {i + 1}</span>
                                        <p className="mt-1 font-bold text-ink">{t.title}</p>
                                        <p className="mt-1 text-xs text-gray-600">{t.text}</p>
                                    </li>
                                ))}
                            </ol>

                            <div className="mt-10 rounded-2xl border border-gray-200 bg-white p-6 flex flex-col sm:flex-row gap-6">
                                <img src={order.uploaded_photo_url} alt="Your reference photo" className="w-full sm:w-40 h-40 object-cover rounded-lg" />
                                <dl className="flex-1 grid grid-cols-2 gap-x-6 gap-y-2 text-sm">
                                    <dt className="text-gray-500">Theme</dt><dd className="font-semibold text-ink">{labelOf(THEMES, order.artwork_theme)}</dd>
                                    <dt className="text-gray-500">Delivery</dt><dd className="font-semibold text-ink">{labelOf(FULFILLMENTS, order.fulfillment_type)}</dd>
                                    <dt className="text-gray-500">Size</dt><dd className="font-semibold text-ink">{order.width_inches}" × {order.height_inches}"</dd>
                                    {order.fulfillment_type !== 'DIGITAL_ONLY' && <><dt className="text-gray-500">Frame</dt><dd className="font-semibold text-ink">{labelOf(FRAMES, order.frame_material)}{order.has_matting ? ' + mat' : ''}</dd></>}
                                    <dt className="text-gray-500">Total paid</dt><dd className="font-semibold text-ink">{order.currency === 'INR' ? inr(order.total_price) : usd(order.total_price)}</dd>
                                </dl>
                            </div>
                            <div className="mt-8 flex flex-col sm:flex-row justify-center gap-3">
                                <a href="/courses?type=all" className="inline-flex h-12 items-center justify-center px-6 rounded-full border border-gray-300 bg-white font-bold text-ink hover:border-ink">Explore art courses</a>
                                <a href="/commission/step-1" className="inline-flex h-12 items-center justify-center px-6 rounded-full bg-brand text-white font-bold hover:bg-brand-dark">Commission another piece</a>
                            </div>
                        </>
                    )}
                </div>
            </div>
        </SiteLayout>
    );
}
