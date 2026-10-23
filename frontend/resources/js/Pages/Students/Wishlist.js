import React, { useState } from 'react';
import { Head } from '@inertiajs/inertia-react';
import Authenticated from '@/Layouts/Authenticated';
import StudentNavigation from '@/Components/Students/StudentNavigation';
import SaveButton from '@/Components/Wishlist/SaveButton';

const money = (amount, currency) => {
    const n = Number(amount);
    if (!amount || isNaN(n) || n <= 0) return null;
    const symbol = currency === 'USD' ? '$' : '₹';
    return symbol + n.toLocaleString(currency === 'USD' ? 'en-US' : 'en-IN', { maximumFractionDigits: 0 });
};

const STATUS = { RESERVED: 'Reserved by a collector right now', SOLD: 'Sold' };

/** Courses, workshops and originals the student saved for later. */
export default function Wishlist(props) {
    const [items, setItems] = useState(props.items || []);
    const remove = (item) => setItems((list) => list.filter((x) => !(x.type === item.type && x.id === item.id)));

    return (
        <Authenticated auth={props.auth} errors={props.errors}>
            <Head title="Your wish list" />
            <div className="container-fluid">
                <div className="row">
                    <StudentNavigation />
                    <main className="col-md-10 px-0 ms-sm-auto rightsidebar">
                        <div className="p30">
                            {items.length === 0 ? (
                                <div className="wl-empty">
                                    <i className="fa fa-heart-o" aria-hidden="true"></i>
                                    <h3 style={{ marginTop: 12 }}>Your wish list is empty</h3>
                                    <p className="dm-muted">Tap the heart on a course, workshop or original artwork to save it here for later.</p>
                                    <p style={{ marginTop: 16, display: 'flex', gap: 10, justifyContent: 'center', flexWrap: 'wrap' }}>
                                        <a href="/courses?type=all" className="dm-btn dm-btn--brand">Browse courses</a>
                                        <a href="/marketplace" className="dm-btn dm-btn--line">Visit the marketplace</a>
                                    </p>
                                </div>
                            ) : (
                                <>
                                    <p className="dm-muted" style={{ marginBottom: 16 }}>
                                        {items.length} saved {items.length === 1 ? 'item' : 'items'}. Originals are one of a kind, so they can sell while they wait here.
                                    </p>
                                    <ul className="wl-list">
                                        {items.map((it) => {
                                            const price = it.type === 'course' ? money(it.price_inr, 'INR') : money(it.price, it.currency || 'INR');
                                            const meta = [it.medium, it.detail, it.start_date ? `Starts ${it.start_date}` : null].filter(Boolean).join(' · ');
                                            return (
                                                <li key={`${it.type}-${it.id}`} className="wl-item">
                                                    <a href={it.url} className="wl-item__img" aria-hidden="true" tabIndex={-1}
                                                       style={it.image ? { backgroundImage: `url("${it.image}")` } : undefined}></a>
                                                    <div className="wl-item__body">
                                                        <span className="wl-item__kind">{it.kind}</span>
                                                        <h4><a href={it.url}>{it.title}</a></h4>
                                                        {it.subtitle && <div className="wl-item__meta">{it.subtitle}</div>}
                                                        {meta && <div className="wl-item__meta">{meta}</div>}
                                                        {STATUS[it.status] && <span className="sec-badge" style={{ marginTop: 6 }}>{STATUS[it.status]}</span>}
                                                    </div>
                                                    <div className="wl-item__side">
                                                        {price && <span className="wl-item__price">{price}</span>}
                                                        <div style={{ display: 'flex', gap: 8 }}>
                                                            <a href={it.url} className="dm-btn dm-btn--brand">{it.type === 'course' ? 'View & enroll' : it.status === 'SOLD' ? 'View' : 'View & buy'}</a>
                                                            <SaveButton type={it.type} id={it.id} saved iconOnly onChange={(s) => { if (!s) remove(it); }} />
                                                        </div>
                                                    </div>
                                                </li>
                                            );
                                        })}
                                    </ul>
                                </>
                            )}
                        </div>
                    </main>
                </div>
            </div>
        </Authenticated>
    );
}
