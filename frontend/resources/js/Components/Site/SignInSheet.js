import React, { useEffect, useRef, useState } from 'react';
import axios from 'axios';
import { Inertia } from '@inertiajs/inertia';

/*
 * Signed out in the middle of something (finding 14: a session that ended while the checkout was open). Instead of
 * throwing the visitor onto the login page, a sheet opens over the page: the form they were filling in, their cart
 * and any hold stay as they are. After signing in again they press the same button once more.
 * Opened by the axios interceptor below on any 401 from the site's own JSON endpoints.
 */
const SKIP = /^\/(login|register|join|auth\/|admin\/)/;

if (typeof window !== 'undefined' && !window.__acSite401) {
    window.__acSite401 = true;
    axios.interceptors.response.use((r) => r, (e) => {
        try {
            const url = new URL(e.config && e.config.url ? e.config.url : '', window.location.origin);
            if (e.response && e.response.status === 401 && url.origin === window.location.origin && !SKIP.test(url.pathname)
                && !SKIP.test(window.location.pathname)
                // a purchase that needs an account goes to the full log-in page instead (it names what they're buying)
                && !(e.response.data && e.response.data.code === 'SIGN_IN')) {
                window.dispatchEvent(new CustomEvent('ac:reauth'));
            }
        } catch (x) { /* never block the original error */ }
        return Promise.reject(e);
    });
}

export default function SignInSheet() {
    const [open, setOpen] = useState(false);
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [busy, setBusy] = useState(false);
    const [err, setErr] = useState(null);
    const [done, setDone] = useState(false);
    const first = useRef(null);
    useEffect(() => {
        const on = () => { setOpen(true); setDone(false); setErr(null); };
        window.addEventListener('ac:reauth', on);
        return () => window.removeEventListener('ac:reauth', on);
    }, []);
    useEffect(() => {
        if (!open) return undefined;
        if (first.current) first.current.focus();
        const esc = (e) => { if (e.key === 'Escape') setOpen(false); };
        document.addEventListener('keydown', esc);
        return () => document.removeEventListener('keydown', esc);
    }, [open]);
    if (!open) return null;
    const submit = async (e) => {
        e.preventDefault();
        setBusy(true); setErr(null);
        try {
            await axios.post('/login', { email, password, remember: true }, { headers: { Accept: 'application/json' } });
            setDone(true);
            Inertia.reload({ only: ['auth', 'nav'], preserveState: true, preserveScroll: true });
        } catch (x) {
            const d = x.response && x.response.data;
            setErr((d && d.errors && Object.values(d.errors)[0] && [].concat(Object.values(d.errors)[0])[0]) || (d && d.message) || 'That did not work. Please try again.');
        }
        setBusy(false);
    };
    const back = typeof window === 'undefined' ? '/' : window.location.pathname + window.location.search;
    return (
        <div className="ac-sheet__backdrop">
            <form className="ac-sheet" role="dialog" aria-modal="true" aria-labelledby="ac-sheet-h" onSubmit={submit}>
                <h2 id="ac-sheet-h">{done ? "You're signed in again" : 'Please sign in again'}</h2>
                {done ? (
                    <>
                        <p role="status">Everything on this page is as you left it. Press the button you were on once more to continue.</p>
                        <button type="button" className="dm-btn dm-btn--brand dm-btn--block" onClick={() => setOpen(false)}>Continue</button>
                    </>
                ) : (
                    <>
                        <p>Your session ended. Your cart, any piece you're holding and what you typed here are kept.</p>
                        <label htmlFor="ac-sheet-email">E-mail</label>
                        <input id="ac-sheet-email" ref={first} type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
                        <label htmlFor="ac-sheet-pw">Password</label>
                        <input id="ac-sheet-pw" type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} />
                        {err && <p className="ac-sheet__err" role="alert">{err}</p>}
                        <button type="submit" className="dm-btn dm-btn--brand dm-btn--block" disabled={busy}>{busy ? 'Signing in…' : 'Sign in and stay here'}</button>
                        <p className="ac-sheet__alt">No password? <a href={`/login?return_to=${encodeURIComponent(back)}`}>Sign in another way</a> (you come back here).</p>
                    </>
                )}
            </form>
        </div>
    );
}
