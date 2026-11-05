import React from 'react';
import { usePage } from '@inertiajs/inertia-react';
import SiteLayout from '@/Components/Site/SiteLayout';

const fmt = (amount, currency) => {
    const n = Number(amount || 0);
    if (!n) return 'Free';
    return currency === 'USD' ? '$' + n.toLocaleString('en-US', { maximumFractionDigits: 2 }) : '₹' + n.toLocaleString('en-IN', { maximumFractionDigits: 2 });
};
const niceDate = (v) => {
    const d = v ? new Date(String(v).replace(' ', 'T')) : null;
    return d && !isNaN(d) ? d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : '';
};

/** After payment: confirmation with next steps, or a friendly failure with a retry. */
export default function CheckoutResult() {
    const { paid, order, course, duplicate, soldOut } = usePage().props;
    return (
        <SiteLayout title={paid ? 'You are enrolled' : 'Payment not completed'}>
            <div className="ac-wide co co-result">
                <div className={`co-card co-result__card ${paid ? 'is-ok' : 'is-bad'}`}>
                    <span className="co-result__icon" aria-hidden="true"><i className={`fa ${paid ? 'fa-check' : 'fa-times'}`}></i></span>
                    {soldOut ? (
                        <>
                            <span className="ac-eyebrow">Course full</span>
                            <h1>The last seat went while you were paying</h1>
                            <p className="co-result__lede" role="status">We're sorry: <b>{course && course.title}</b> filled up before your payment arrived. We have queued a full refund, which our team confirms within 2 working days. Join the waitlist on the course page and you'll be first to hear when a seat opens.</p>
                        </>
                    ) : paid && duplicate ? (
                        <>
                            <span className="ac-eyebrow">Already enrolled</span>
                            <h1>You already have this course</h1>
                            <p className="co-result__lede" role="status">Your new payment for <b>{course && course.title}</b> went through, but you were already enrolled. We have queued a full refund of that extra payment; our team confirms it within 2 working days and you'll get an e-mail when it's sent.</p>
                        </>
                    ) : paid ? (
                        <>
                            <span className="ac-eyebrow">Payment successful</span>
                            <h1>You're enrolled!</h1>
                            <p className="co-result__lede">Welcome to <b>{course && course.title}</b>. Your classroom is ready in My studio{order.email ? <>, and a receipt is on its way to <b>{order.email}</b></> : null}.</p>
                        </>
                    ) : (
                        <>
                            <span className="ac-eyebrow">Payment not completed</span>
                            <h1>Your payment didn't go through</h1>
                            <p className="co-result__lede">No money was taken. You can try again with the same or a different payment method.</p>
                        </>
                    )}

                    {course && (
                        <div className="co-course co-result__course">
                            <span className="co-course__img" style={course.image ? { backgroundImage: `url("${course.image}")` } : undefined} aria-hidden="true"></span>
                            <span>
                                <span className="wl-item__kind">{course.kind}{course.medium ? ` · ${course.medium}` : ''}</span>
                                <b>{course.title}</b>
                                {course.start_date && <small>Starts {course.start_date}</small>}
                            </span>
                        </div>
                    )}

                    <dl className="co-total co-result__facts">
                        <div><dt>Order number</dt><dd>{order.number}</dd></div>
                        <div><dt>Amount</dt><dd>{fmt(order.amount, order.currency)}</dd></div>
                        {order.method && <div><dt>Paid with</dt><dd>{order.method}</dd></div>}
                        <div><dt>Date</dt><dd>{niceDate(order.date)}</dd></div>
                    </dl>

                    <div className="co-row co-result__actions">
                        {paid ? (
                            <>
                                <a href={`/dashboard/classroom/${course ? course.id : ''}`} className="dm-btn dm-btn--brand dm-btn--lg">Start learning</a>
                                <a href="/dashboard" className="dm-btn dm-btn--line dm-btn--lg">Go to My studio</a>
                            </>
                        ) : (
                            <>
                                <a href={course ? `/checkout?course=${course.slug}` : '/checkout'} className="dm-btn dm-btn--brand dm-btn--lg">Try again</a>
                                <a href="/contact" className="dm-btn dm-btn--line dm-btn--lg">Contact us</a>
                            </>
                        )}
                    </div>
                    {paid && <p className="dm-muted co-small">Find this order any time under <a href="/user/orders">My account › Your orders</a>.</p>}
                </div>
            </div>
        </SiteLayout>
    );
}
