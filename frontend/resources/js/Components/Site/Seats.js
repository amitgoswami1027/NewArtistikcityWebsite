import React, { useState } from 'react';
import { authPost } from '@/Components/Auth/api';

/*
 * Seat caps on live courses and workshops (backend: seats/SeatService).
 * SeatsNote: "Only 3 seats left" / "Sold out" line. WaitlistPanel: join / leave the waitlist of a full course,
 * with the student's place in the queue. Both render nothing for courses without a seat limit.
 */

export const seatsLeftLabel = (left) => (left === 1 ? 'Only 1 seat left' : `Only ${left} seats left`);

/** Card badge for catalogue rows ({seat_limit, seats_left, sold_out}); null when there's nothing to say. */
export function seatBadge(course) {
    if (!course || course.seat_limit === null || course.seat_limit === undefined) return null;
    if (course.sold_out) return { label: 'Sold out', cls: 'dm-badge--dark' };
    if (course.seats_left !== null && course.seats_left !== undefined && Number(course.seats_left) <= 5) return { label: seatsLeftLabel(Number(course.seats_left)), cls: 'dm-badge--accent' };
    return null;
}

export function SeatsNote({ seats }) {
    if (!seats || seats.limit === null || seats.limit === undefined) return null;
    if (seats.mine && seats.soldOut === false && seats.left === 0) return <p className="ac-seats is-mine" role="status"><i className="fa fa-check" aria-hidden="true"></i> Your seat is kept for you while you pay.</p>;
    if (seats.soldOut) return <p className="ac-seats is-out" role="status"><i className="fa fa-ban" aria-hidden="true"></i> Sold out · all {seats.limit} seats are taken</p>;
    if (seats.left <= 5) return <p className="ac-seats is-low" role="status"><i className="fa fa-fire" aria-hidden="true"></i> {seatsLeftLabel(seats.left)} of {seats.limit}</p>;
    return <p className="ac-seats" role="status"><i className="fa fa-users" aria-hidden="true"></i> {seats.left} of {seats.limit} seats free</p>;
}

export function WaitlistPanel({ courseId, seats: initial, signedIn, loginUrl }) {
    const [seats, setSeats] = useState(initial);
    const [busy, setBusy] = useState(false);
    const [msg, setMsg] = useState(null);
    if (!seats || !seats.soldOut) return null;
    const waiting = seats.waitlist === 'WAITING';
    const invited = seats.waitlist === 'INVITED';
    const act = async (action) => {
        if (!signedIn) { window.location.assign(loginUrl || `/login?return_to=${encodeURIComponent(window.location.pathname)}`); return; }
        setBusy(true); setMsg(null);
        try {
            const r = await authPost(`/courses/${courseId}/waitlist`, { action });
            setSeats(r.seats); setMsg({ ok: true, text: r.message });
        } catch (e) {
            if (e.data && e.data.seats) setSeats(e.data.seats);
            setMsg({ ok: false, text: e.message });
        }
        setBusy(false);
    };
    return (
        <div className="ac-waitlist" aria-live="polite">
            {invited ? (
                <p><b>A seat is waiting for you.</b> Check your e-mail for the priority link: it's kept for 24 hours.</p>
            ) : waiting ? (
                <>
                    <p><b>You're on the waitlist{seats.position ? ` · number ${seats.position} in line` : ''}.</b> We'll e-mail you the moment a seat frees up.</p>
                    <button type="button" className="dm-btn dm-btn--ghost dm-btn--block" disabled={busy} onClick={() => act('leave')}>Leave the waitlist</button>
                </>
            ) : (
                <>
                    <p>Seats free up when someone cancels. Join the waitlist and the next seat is yours first, kept for 24 hours.</p>
                    <button type="button" className="dm-btn dm-btn--brand dm-btn--block dm-btn--lg" disabled={busy} onClick={() => act('join')}>{busy ? 'Joining…' : 'Join the waitlist'}</button>
                </>
            )}
            {msg && <p className={msg.ok ? 'ac-waitlist__ok' : 'ac-waitlist__err'} role={msg.ok ? 'status' : 'alert'}>{msg.text}</p>}
        </div>
    );
}
