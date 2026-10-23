import React from 'react';
import { useForm } from '@inertiajs/inertia-react';
import AuthLayout from '@/Layouts/AuthLayout';

/**
 * Landing page of the e-mailed sign-in link. Signing in needs one more click, so mail scanners and
 * link previews (which "open" links automatically) can't use up the single-use link.
 */
export default function MagicLink({ token, email, valid }) {
    const { post, processing } = useForm({ token });
    const submit = (e) => { e.preventDefault(); post('/auth/magic-link/consume'); };

    if (!valid) {
        return (
            <AuthLayout title="Link expired">
                <h1>This link has expired</h1>
                <p className="dm-lede">Sign-in links work once and only for 15 minutes. Request a fresh one and use it right away.</p>
                <a href={route('login')} className="dm-btn dm-btn--brand dm-btn--block dm-btn--lg">Back to log in</a>
            </AuthLayout>
        );
    }
    return (
        <AuthLayout title="Sign in">
            <h1>Sign in to ArtistikCity</h1>
            <p className="dm-lede">Continue as <b>{email}</b></p>
            <form onSubmit={submit}>
                <button type="submit" className="dm-btn dm-btn--brand dm-btn--block dm-btn--lg" disabled={processing} autoFocus>
                    {processing ? 'Signing you in…' : 'Continue'}
                </button>
            </form>
            <p className="dm-legal">Not you? Just close this page - nothing happens without the click.</p>
        </AuthLayout>
    );
}
