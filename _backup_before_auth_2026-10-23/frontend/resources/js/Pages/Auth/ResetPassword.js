import React, { useEffect } from 'react';
import { useForm } from '@inertiajs/inertia-react';
import AuthLayout from '@/Layouts/AuthLayout';
import { Field, FormErrors, StrengthMeter } from '@/Components/Auth/AuthFields';

export default function ResetPassword({ token, email }) {
    const { data, setData, post, processing, errors, reset } = useForm({ token, email, password: '', password_confirmation: '' });
    useEffect(() => () => reset('password', 'password_confirmation'), []);
    const change = (e) => setData(e.target.name, e.target.value);
    const submit = (e) => { e.preventDefault(); post(route('password.update')); };

    return (
        <AuthLayout title="Choose a new password">
            <h1>Choose a new password</h1>
            <p className="dm-lede">Use at least 8 characters. Mixing letters, numbers and symbols makes it stronger.</p>
            <FormErrors errors={errors.token ? { token: errors.token } : {}} />
            <form onSubmit={submit} noValidate>
                <Field label="Email" name="email" type="email" value={data.email} onChange={change} error={errors.email} autoComplete="username" />
                <Field label="New password" name="password" type="password" value={data.password} onChange={change} error={errors.password} autoComplete="new-password" autoFocus>
                    <StrengthMeter password={data.password} />
                </Field>
                <Field label="Repeat new password" name="password_confirmation" type="password" value={data.password_confirmation}
                       onChange={change} error={errors.password_confirmation} autoComplete="new-password" />
                <button type="submit" className="dm-btn dm-btn--brand dm-btn--block dm-btn--lg" disabled={processing}>{processing ? 'Saving…' : 'Save password'}</button>
            </form>
        </AuthLayout>
    );
}
