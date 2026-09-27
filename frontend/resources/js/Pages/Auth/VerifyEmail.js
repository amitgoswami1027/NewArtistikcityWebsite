import React from 'react';
import { useForm, InertiaLink } from '@inertiajs/inertia-react';
import AuthLayout from '@/Layouts/AuthLayout';

export default function VerifyEmail({ status }) {
    const { post, processing } = useForm();
    const submit = (e) => { e.preventDefault(); post(route('verification.send')); };
    return (
        <AuthLayout title="Verify your email">
            <div style={{ width: 64, height: 64, borderRadius: 16, background: 'var(--ac-brand-soft)', color: 'var(--ac-brand)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 28, margin: '0 auto 16px' }}>
                <i className="fa fa-envelope-o"></i>
            </div>
            <h1>Check your inbox</h1>
            <p className="dm-lede">We sent you a verification link. Click it to activate your account. Can't find it? Check your spam folder or send a new one.</p>
            {status === 'verification-link-sent' && <div className="ac-alert ac-alert--ok">A new verification link is on its way.</div>}
            <form onSubmit={submit}>
                <button type="submit" className="dm-btn dm-btn--brand dm-btn--block dm-btn--lg" disabled={processing}>Resend verification email</button>
            </form>
            <div className="dm-auth__switch">
                Wrong account? <InertiaLink href={route('logout')} method="post" as="button" style={{ background: 'none', border: 0, fontWeight: 800, color: 'var(--ac-brand)', cursor: 'pointer', font: 'inherit' }}>Log out</InertiaLink>
            </div>
        </AuthLayout>
    );
}
