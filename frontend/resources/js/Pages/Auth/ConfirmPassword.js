import React, { useEffect } from 'react';
import { useForm } from '@inertiajs/inertia-react';
import AuthLayout from '@/Layouts/AuthLayout';
import { Field } from '@/Components/Auth/AuthFields';

export default function ConfirmPassword() {
    const { data, setData, post, processing, errors, reset } = useForm({ password: '' });
    useEffect(() => () => reset('password'), []);
    const submit = (e) => { e.preventDefault(); post(route('password.confirm')); };
    return (
        <AuthLayout title="Confirm password">
            <h1>Confirm it's you</h1>
            <p className="dm-lede">This is a secure area. Please enter your password to continue.</p>
            <form onSubmit={submit} noValidate>
                <Field label="Password" name="password" type="password" value={data.password}
                       onChange={(e) => setData('password', e.target.value)} error={errors.password} autoComplete="current-password" autoFocus />
                <button type="submit" className="dm-btn dm-btn--brand dm-btn--block dm-btn--lg" disabled={processing}>Confirm</button>
            </form>
        </AuthLayout>
    );
}
