import React, { useState } from 'react';
import { authPost } from '@/Components/Auth/api';

/** "From your course team": messages, new dates and cancellations sent from Admin > course > Students. */
export default function Notices({ notices = [] }) {
    const [items, setItems] = useState(notices);
    const [open, setOpen] = useState(() => (notices.find((n) => !n.read_at) || {}).id || null);
    if (!items.length) return null;
    const read = (n) => {
        setOpen(open === n.id ? null : n.id);
        if (n.read_at) return;
        setItems(items.map((x) => (x.id === n.id ? { ...x, read_at: 'now' } : x)));
        authPost(`/dashboard/notices/${n.id}/read`).catch(() => { /* stays unread on the server; harmless */ });
    };
    const unread = items.filter((n) => !n.read_at).length;
    return (
        <section className="st-notices" aria-labelledby="st-notices-h">
            <h2 id="st-notices-h">From your course team {unread > 0 && <span className="st-notices__count">{unread} new</span>}</h2>
            <ul>
                {items.map((n) => (
                    <li key={n.id} className={`st-notice${n.read_at ? '' : ' is-new'} is-${String(n.kind).toLowerCase()}`}>
                        <button type="button" aria-expanded={open === n.id} onClick={() => read(n)}>
                            <span className="st-notice__dot" aria-hidden="true"></span>
                            <span className="st-notice__text"><b>{n.subject}</b><small>{n.course_title} · {String(n.created_at).slice(0, 10)}</small></span>
                            <i className={`fa fa-chevron-${open === n.id ? 'up' : 'down'}`} aria-hidden="true"></i>
                        </button>
                        {open === n.id && <div className="st-notice__body">{String(n.body).split(/\n\s*\n/).map((p, i) => <p key={i}>{p}</p>)}</div>}
                    </li>
                ))}
            </ul>
        </section>
    );
}
