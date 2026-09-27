import React, { useState } from 'react';
import axios from 'axios';

export default function NewsletterBand() {
    const [email, setEmail] = useState('');
    const [state, setState] = useState(null);
    const [busy, setBusy] = useState(false);

    const submit = (e) => {
        e.preventDefault();
        setBusy(true);
        axios.post(route('newsletter.save'), { email })
            .then((r) => setState(r.data && r.data.message === 'success' ? 'ok' : 'err'))
            .catch(() => setState('err'))
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
                            <input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@example.com" aria-label="Email address" />
                            <button className="ac-btn ac-btn--dark" disabled={busy}>{busy ? 'Sending…' : 'Subscribe'}</button>
                        </form>
                        {state === 'ok' && <p style={{ marginTop: 12 }}><i className="fa fa-check-circle"></i> You're on the list. Watch your inbox.</p>}
                        {state === 'err' && <p style={{ marginTop: 12 }}>Something went wrong. Please try again.</p>}
                    </div>
                </div>
            </div>
        </section>
    );
}
