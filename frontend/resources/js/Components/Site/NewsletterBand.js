import React, { useState } from 'react';
import axios from 'axios';

export default function NewsletterBand() {
    const [email, setEmail] = useState('');
    const [state, setState] = useState(null);
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState('');
    const [website, setWebsite] = useState(''); // honeypot: people never see or fill it

    const submit = (e) => {
        e.preventDefault();
        setBusy(true);
        setMessage('');
        axios.post(route('newsletter.save'), { email, website })
            .then((r) => {
                if (r.data && r.data.message === 'success') { setState('ok'); return; }
                const err = r.data && r.data.error && r.data.error.email;
                setMessage(err ? err[0] : ''); setState('err');
            })
            .catch((e) => {
                const st = e && e.response && e.response.status;
                setMessage(st === 429 ? 'Too many sign-ups from here. Please try again in a minute.'
                    : st === 419 ? 'Your session expired. Reload the page and try again.' : '');
                setState('err');
            })
            .finally(() => setBusy(false));
    };

    return (
        <section className="ac-section--tight">
            <div className="ac-container">
                <div className="ac-cta">
                    <div>
                        <h2>Get a fresh art prompt every week</h2>
                        <p>Techniques, free mini-lessons and first access to new batches. No spam, unsubscribe any time.</p>
                    </div>
                    <div>
                        <form onSubmit={submit}>
                            <input type="text" name="website" value={website} onChange={(e) => setWebsite(e.target.value)} tabIndex={-1} autoComplete="off" aria-hidden="true" className="ac-hp" />
                            <input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@example.com" aria-label="Email address" />
                            <button className="ac-btn ac-btn--dark" disabled={busy}>{busy ? 'Sending…' : 'Subscribe'}</button>
                        </form>
                        {state === 'ok' && <p style={{ marginTop: 12 }} role="status"><i className="fa fa-check-circle"></i> You're on the list. Watch your inbox.</p>}
                        {state === 'err' && <p style={{ marginTop: 12 }} role="alert">{message || 'Something went wrong. Please try again.'}</p>}
                    </div>
                </div>
            </div>
        </section>
    );
}
