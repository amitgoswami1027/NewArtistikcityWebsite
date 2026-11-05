import React from 'react';
import { usePage } from '@inertiajs/inertia-react';
import SiteLayout from '@/Components/Site/SiteLayout';
import { inr } from '@/Components/Shop/shop';

const STATUS = {
    PAID: 'Paid · being prepared', PACKED: 'Packed', SHIPPED: 'Shipped', DELIVERED: 'Delivered', REFUND_REQUIRED: 'Refund in progress',
    REFUNDED: 'Refunded', PAYMENT_PENDING: 'Waiting for payment', CANCELLED: 'Cancelled', EXPIRED: 'Expired',
};

export default function ThankYou() {
    const { order: o } = usePage().props;
    const paid = ['PAID', 'PACKED', 'SHIPPED', 'DELIVERED'].includes(o.status);
    const refund = Number(o.refund_due_inr) > 0;
    return (
        <SiteLayout title={`Order ${o.order_id}`}>
            <div className="pz">
                <div className="ac-wide pz-thanks">
                    <header className="pz-thanks__head">
                        <span className={`pz-thanks__icon ${paid ? '' : 'is-warn'}`} aria-hidden="true"><i className={`fa ${paid ? 'fa-check' : 'fa-info'}`}></i></span>
                        <p className="pz-eyebrow">Order {o.order_id}</p>
                        <h1>{paid ? `Thank you, ${String(o.buyer_name).split(' ')[0]}!` : o.status === 'REFUND_REQUIRED' ? 'We’re sorry' : 'Your order'}</h1>
                        <p className="pz-lead">
                            {o.status === 'REFUND_REQUIRED' ? `Your payment arrived after the artwork had gone to another collector. We’ll refund ${inr(o.refund_due_inr)} within 5–7 working days.`
                                : paid ? (o.needs_shipping ? `We’ve emailed your receipt to ${o.buyer_email}. We’ll send the tracking number when it ships.` : `Your download is ready below, and we’ve emailed the link to ${o.buyer_email}.`)
                                : STATUS[o.status]}
                        </p>
                        {refund && o.status !== 'REFUND_REQUIRED' && <p className="pz-note">One item couldn’t be supplied when your payment arrived. We’ll refund {inr(o.refund_due_inr)} for it within 5–7 working days.</p>}
                    </header>

                    <section className="pz-card">
                        <header className="pz-card__head"><h2>What you bought</h2><span className="pz-badge">{STATUS[o.status] || o.status}</span></header>
                        <ul className="pz-lines">
                            {o.items.map((i) => (
                                <li key={i.id} className={`pz-line ${i.status === 'REFUND_REQUIRED' ? 'is-bad' : ''}`}>
                                    <a href={`/marketplace/${i.slug}`} className="pz-line__img" tabIndex={-1} aria-hidden="true">{i.image_url ? <img src={i.image_url} alt="" /> : <span></span>}</a>
                                    <div className="pz-line__body">
                                        <a href={`/marketplace/${i.slug}`} className="pz-line__title">{i.title}</a>
                                        <p className="pz-line__by">by {i.artist_name}</p>
                                        <p className="pz-line__variant">{i.variant_label}{i.quantity > 1 ? ` · qty ${i.quantity}` : ''}</p>
                                        {i.edition_no && <p className="pz-line__variant">Edition no. {i.edition_no}{i.quantity > 1 ? `–${i.edition_no + i.quantity - 1}` : ''}</p>}
                                        {i.status === 'REFUND_REQUIRED' && <p className="pz-line__warn">No longer available: refund due</p>}
                                        {i.download_url && (
                                            <a href={i.download_url} className="pz-btn pz-btn--brand pz-btn--sm"><i className="fa fa-download" aria-hidden="true"></i> Download high-resolution file</a>
                                        )}
                                    </div>
                                    <div className="pz-line__price"><b>{inr(i.line_total_inr)}</b></div>
                                </li>
                            ))}
                        </ul>
                        <dl className="pz-totals">
                            <div><dt>Subtotal</dt><dd>{inr(o.subtotal_inr)}</dd></div>
                            {Number(o.discount_inr) > 0 && <div className="is-green"><dt>Coupon {o.coupon_code}</dt><dd>−{inr(o.discount_inr)}</dd></div>}
                            {o.needs_shipping && <div><dt>Shipping</dt><dd>{Number(o.shipping_inr) > 0 ? inr(o.shipping_inr) : 'Free'}</dd></div>}
                            <div className="is-total"><dt>Paid</dt><dd>{o.currency === 'USD' ? `$${Number(o.amount).toFixed(2)}` : inr(o.total_inr)}</dd></div>
                        </dl>
                    </section>

                    {o.needs_shipping && (
                        <section className="pz-card">
                            <h2 className="pz-card__head">Delivery</h2>
                            <p>{o.buyer_name}<br />{o.address_line1}{o.address_line2 ? `, ${o.address_line2}` : ''}<br />{o.city}{o.state ? `, ${o.state}` : ''} {o.postal_code}<br />{o.country}</p>
                            {o.tracking_number && <p><b>Tracking:</b> {o.courier} {o.tracking_number}</p>}
                        </section>
                    )}
                    <div className="pz-thanks__next">
                        <a href="/marketplace" className="pz-btn pz-btn--ink">Back to the marketplace</a>
                        <a href="/commission/step-1" className="pz-btn pz-btn--line">Commission an artwork</a>
                    </div>
                </div>
            </div>
        </SiteLayout>
    );
}
