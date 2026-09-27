import React from 'react';
import { useForm } from '@inertiajs/inertia-react';
import AuthLayout from '@/Layouts/AuthLayout';
import { Field } from '@/Components/Auth/AuthFields';

export default function ForgotPassword({ status }) {
    const { data, setData, post, processing, errors } = useForm({ email: '' });
    const submit = (e) => { e.preventDefault(); post(route('password.email')); };

    return (
        <AuthLayout title="Reset your password">
            <h1>Forgot your password?</h1>
            <p className="dm-lede">Enter the email you joined with and we'll send you a link to choose a new one.</p>
            {status && <div className="ac-alert ac-alert--ok">{status}</div>}
            <form onSubmit={submit} noValidate>
                <Field label="Email" name="email" type="email" value={data.email}
                       onChange={(e) => setData('email', e.target.value)} error={errors.email}
                       autoComplete="email" placeholder="you@example.com" autoFocus />
                <button type="submit" className="dm-btn dm-btn--brand dm-btn--block dm-btn--lg" disabled={processing}>
                    {processing ? 'Sending…' : 'Send reset link'}
                </button>
            </form>
            <div className="dm-auth__switch">Remembered it? <a href={route('login')}>Back to log in</a></div>
        </AuthLayout>
    );
}
