import React, { useEffect } from 'react';
import { useForm } from '@inertiajs/inertia-react';
import AuthLayout from '@/Layouts/AuthLayout';
import { Field, SocialButtons } from '@/Components/Auth/AuthFields';

export default function Login({ status, canResetPassword }) {
    const { data, setData, post, processing, errors, reset } = useForm({ email: '', password: '', remember: true });

    useEffect(() => () => reset('password'), []);

    const change = (e) => setData(e.target.name, e.target.type === 'checkbox' ? e.target.checked : e.target.value);
    const submit = (e) => { e.preventDefault(); post(route('login'), { onFinish: () => reset('password') }); };
    const joinHref = typeof window !== 'undefined' && window.location.search.includes('return_to') ? route('join') + window.location.search : route('join');

    return (
        <AuthLayout title="Log in">
            <h1>Log in</h1>
            <p className="dm-lede">Welcome back. Pick up your courses right where you left off.</p>

            {status && <div className="ac-alert ac-alert--ok">{status}</div>}

            <SocialButtons />
            <div className="dm-or">or log in with your email</div>

            <form onSubmit={submit} noValidate>
                <Field label="Email" name="email" type="email" value={data.email} onChange={change}
                       error={errors.email} autoComplete="username" placeholder="you@example.com" autoFocus />
                <Field label="Password" name="password" type="password" value={data.password} onChange={change}
                       error={errors.password} autoComplete="current-password" placeholder="Your password" />
                <div className="dm-auth__row">
                    <label className="ac-check" style={{ fontSize: 14 }}>
                        <input type="checkbox" name="remember" checked={!!data.remember} onChange={change} /> Remember me
                    </label>
                    {canResetPassword && <a href={route('password.request')}>Forgot your password?</a>}
                </div>
                <button type="submit" className="dm-btn dm-btn--brand dm-btn--block dm-btn--lg" disabled={processing}>
                    {processing ? 'Logging in…' : 'Log in'}
                </button>
            </form>

            <div className="dm-auth__switch">Don't have an account? <a href={joinHref}>Join for free</a></div>
        </AuthLayout>
    );
}
