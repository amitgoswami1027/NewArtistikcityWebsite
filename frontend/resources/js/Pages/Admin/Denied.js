import React from 'react';
import { usePage } from '@inertiajs/inertia-react';
import AdminLayout from '@/Components/Admin/AdminLayout';

export default function Denied() {
    const { admin } = usePage().props;
    return (
        <AdminLayout title="Not available">
            <div className="max-w-xl mx-auto text-center py-20">
                <span className="inline-flex w-16 h-16 rounded-full bg-gray-100 items-center justify-center text-2xl text-gray-500"><i className="fa fa-lock" aria-hidden="true"></i></span>
                <h1 className="mt-5 text-3xl font-black tracking-tight text-ink">This area isn't part of your role</h1>
                <p className="mt-2 text-gray-600">You're signed in as <strong>{admin.personaLabel}</strong>. Ask a super admin if you need access.</p>
                <a href="/admin/dashboard" className="mt-6 inline-flex h-12 items-center px-6 rounded-full bg-ink text-white font-bold">Back to overview</a>
            </div>
        </AdminLayout>
    );
}
