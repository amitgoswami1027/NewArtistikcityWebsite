import React, { useEffect, useRef, useState } from 'react';
import axios from 'axios';
import { errorText } from '@/Components/Shop/shop';

/*
 * "Meet the artist": direct links (WhatsApp, Instagram DM, e-mail), an in-app chat window and an
 * "Ask the artist a question" form. Messages are stored on the server (artist_threads) and e-mailed to the
 * artist side; replies come back here (the chat polls while it is open) and by e-mail.
 */

const when = (ms) => (ms ? new Date(ms).toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : '');

function initials(name) {
    return String(name || 'A').split(/\s+/).filter(Boolean).slice(0, 2).map((w) => w[0].toUpperCase()).join('');
}

export function useConversation(paintingId, initial) {
    const [conv, setConv] = useState(initial || { thread: null, messages: [] });
    const reload = () => axios.get(`/api/v1/artist-hub/${paintingId}`).then((r) => { setConv(r.data); return r.data; }).catch(() => null);
    const send = (payload) => axios.post(`/api/v1/artist-hub/${paintingId}/messages`, payload).then((r) => { setConv(r.data); return r.data; });
    return { conv, reload, send };
}

function Messages({ messages, artist }) {
    const end = useRef(null);
    useEffect(() => { if (end.current && end.current.scrollIntoView) end.current.scrollIntoView({ block: 'end' }); }, [messages.length]);
    if (!messages.length) {
        return <p className="pz-chat__empty">Say hello. {artist} or the studio usually replies within a day, here and by e-mail.</p>;
    }
    return (
        <ol className="pz-chat__list">
            {messages.map((m) => (
                <li key={m.id} className={m.sender === 'BUYER' ? 'is-me' : 'is-artist'}>
                    <p>{m.body}</p>
                    <small>{m.sender === 'BUYER' ? 'You' : m.by} · {when(m.at)}</small>
                </li>
            ))}
            <li ref={end} aria-hidden="true" className="pz-chat__end"></li>
        </ol>
    );
}

/** Floating chat window. */
export function ChatWindow({ painting, conversation, customer, context, onClose }) {
    const { conv, reload, send } = conversation;
    const [text, setText] = useState('');
    const [name, setName] = useState((customer && customer.name) || '');
    const [email, setEmail] = useState((customer && customer.email) || '');
    const [busy, setBusy] = useState(false);
    const [err, setErr] = useState(null);
    const needIdentity = !conv.thread && !(customer && customer.name && customer.email);

    useEffect(() => {
        reload();
        const t = setInterval(reload, 10000);
        const onKey = (e) => { if (e.key === 'Escape') onClose(); };
        window.addEventListener('keydown', onKey);
        return () => { clearInterval(t); window.removeEventListener('keydown', onKey); };
    }, []); // eslint-disable-line react-hooks/exhaustive-deps

    const submit = (e) => {
        e.preventDefault();
        if (text.trim().length < 2) { setErr('Write your message first.'); return; }
        setBusy(true); setErr(null);
        send({ body: text, name, email, context }).then(() => setText('')).catch((x) => setErr(errorText(x))).finally(() => setBusy(false));
    };

    return (
        <section className="pz-chat" role="dialog" aria-label={`Chat with ${painting.artist_name}`}>
            <header className="pz-chat__head">
                <span className="pz-avatar" aria-hidden="true">{initials(painting.artist_name)}</span>
                <div><b>{painting.artist_name}</b><small>About “{painting.title}”</small></div>
                <button type="button" className="pz-iconbtn" onClick={onClose} aria-label="Close chat"><i className="fa fa-times" aria-hidden="true"></i></button>
            </header>
            <div className="pz-chat__body" aria-live="polite"><Messages messages={conv.messages || []} artist={painting.artist_name} /></div>
            <form className="pz-chat__form" onSubmit={submit}>
                {needIdentity && (
                    <div className="pz-chat__who">
                        <input type="text" value={name} onChange={(e) => setName(e.target.value)} placeholder="Your name" aria-label="Your name" autoComplete="name" />
                        <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="E-mail for replies" aria-label="Your e-mail" autoComplete="email" />
                    </div>
                )}
                <div className="pz-chat__send">
                    <textarea rows="2" value={text} onChange={(e) => setText(e.target.value)} placeholder="Type a message…" aria-label="Message" maxLength={2000}
                              onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) submit(e); }} />
                    <button type="submit" disabled={busy} aria-label="Send"><i className={`fa ${busy ? 'fa-circle-o-notch fa-spin' : 'fa-paper-plane'}`} aria-hidden="true"></i></button>
                </div>
                {err && <p className="pz-error" role="alert">{err}</p>}
            </form>
        </section>
    );
}

/**
 * @param painting     {id, title, slug, artist_name}
 * @param contact      {whatsapp, instagram, email, own}
 * @param conversation from useConversation()
 * @param context      what the visitor is looking at, e.g. "Limited edition canvas · 24 × 18 in · ₹5,999"
 */
export default function ArtistHub({ painting, contact = {}, conversation, customer, context, autoOpenChat = false }) {
    const [chat, setChat] = useState(autoOpenChat);
    const [q, setQ] = useState('');
    const [name, setName] = useState((customer && customer.name) || '');
    const [email, setEmail] = useState((customer && customer.email) || '');
    const [busy, setBusy] = useState(false);
    const [sent, setSent] = useState(null);
    const [err, setErr] = useState(null);
    const url = typeof window !== 'undefined' ? `${window.location.origin}/marketplace/${painting.slug}` : `/marketplace/${painting.slug}`;
    const intro = `Hi ${painting.artist_name.split(' ')[0]}, I'm interested in “${painting.title}”${context ? ` (${context})` : ''}. ${url}`;
    const hasThread = conversation.conv && conversation.conv.thread;
    const unread = hasThread ? (conversation.conv.messages || []).filter((m) => m.sender === 'ARTIST').length : 0;

    const ask = (e) => {
        e.preventDefault();
        setErr(null); setSent(null);
        if (q.trim().length < 5) { setErr('Write your question for the artist.'); return; }
        setBusy(true);
        conversation.send({ body: q, name, email, context }).then((d) => { setSent(d.message || 'Sent.'); setQ(''); })
            .catch((x) => setErr(errorText(x))).finally(() => setBusy(false));
    };

    return (
        <section className="pz-hub" aria-labelledby="pz-hub-title">
            <div className="pz-hub__artist">
                <span className="pz-avatar pz-avatar--lg" aria-hidden="true">{initials(painting.artist_name)}</span>
                <div>
                    <p className="pz-eyebrow">Meet the artist</p>
                    <h2 id="pz-hub-title">{painting.artist_name}</h2>
                    <p className="pz-muted">Questions about size, colours or framing? Talk to {contact.own ? 'the artist' : 'the artist through our studio'} before you buy.</p>
                </div>
            </div>

            <div className="pz-hub__channels">
                {contact.whatsapp && (
                    <a className="pz-channel pz-channel--wa" href={`https://wa.me/${contact.whatsapp}?text=${encodeURIComponent(intro)}`} target="_blank" rel="noopener noreferrer">
                        <i className="fa fa-whatsapp" aria-hidden="true"></i><span>WhatsApp</span>
                    </a>
                )}
                {contact.instagram && (
                    <a className="pz-channel pz-channel--ig" href={`https://ig.me/m/${contact.instagram}`} target="_blank" rel="noopener noreferrer">
                        <i className="fa fa-instagram" aria-hidden="true"></i><span>Instagram DM</span>
                    </a>
                )}
                {contact.email && (
                    <a className="pz-channel pz-channel--mail" href={`mailto:${contact.email}?subject=${encodeURIComponent(`Question about “${painting.title}”`)}&body=${encodeURIComponent(intro + '\n\n')}`}>
                        <i className="fa fa-envelope-o" aria-hidden="true"></i><span>E-mail</span>
                    </a>
                )}
                <button type="button" className="pz-channel pz-channel--chat" onClick={() => setChat(true)} aria-haspopup="dialog">
                    <i className="fa fa-comments-o" aria-hidden="true"></i><span>Live chat</span>{unread > 0 && <em>{unread}</em>}
                </button>
            </div>

            <form className="pz-hub__ask" onSubmit={ask}>
                <label htmlFor="pz-ask" className="pz-label">Ask the artist a question</label>
                <textarea id="pz-ask" rows="3" value={q} onChange={(e) => setQ(e.target.value)} maxLength={2000}
                          placeholder="e.g. Is the blue as deep in person? Could the canvas come in 30 inches?" />
                {!(customer && customer.name && customer.email) && !hasThread && (
                    <div className="pz-grid2">
                        <input type="text" value={name} onChange={(e) => setName(e.target.value)} placeholder="Your name" aria-label="Your name" autoComplete="name" />
                        <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="Your e-mail" aria-label="Your e-mail" autoComplete="email" />
                    </div>
                )}
                <p className="pz-fine">We send the artist the artwork details{context ? ' and the option you picked' : ''}, so you don’t have to.</p>
                {err && <p className="pz-error" role="alert">{err}</p>}
                {sent && <p className="pz-ok" role="status"><i className="fa fa-check-circle" aria-hidden="true"></i> {sent} <button type="button" className="pz-link" onClick={() => setChat(true)}>Open chat</button></p>}
                <button type="submit" className="pz-btn pz-btn--line" disabled={busy}>{busy ? <i className="fa fa-circle-o-notch fa-spin" aria-hidden="true"></i> : <i className="fa fa-paper-plane-o" aria-hidden="true"></i>} Send question</button>
            </form>

            {chat && <ChatWindow painting={painting} conversation={conversation} customer={customer} context={context} onClose={() => setChat(false)} />}
        </section>
    );
}
