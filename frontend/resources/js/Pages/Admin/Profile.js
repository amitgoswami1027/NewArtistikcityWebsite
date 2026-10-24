import React, { useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import axios from 'axios';
import AdminLayout, { PageHeader, useToast, errorText } from '@/Components/Admin/AdminLayout';
import { Button, Badge } from '@/Components/Admin/kit';
import { useForm, FormSection, Field, TextField, DropZone, RichText, Row2 } from '@/Components/Admin/form';

/** The signed-in admin's own profile (/admin/profile) and password (/admin/change-password). */
export default function Profile() {
    const { me, tab: initialTab } = usePage().props;
    const [tab, setTab] = useState(initialTab);
    const [toastNode, toast] = useToast();
    const go = (t) => { setTab(t); window.history.replaceState(window.history.state, '', t === 'password' ? '/admin/change-password' : '/admin/profile'); };
    return (
        <AdminLayout title={tab === 'password' ? 'Change password' : 'Your profile'} crumbs={[{ label: 'Account' }, { label: tab === 'password' ? 'Change password' : 'Your profile' }]}>
            <PageHeader title={me.name} subtitle={<><Badge tone="pink">{me.role_label}</Badge> <span className="adm-muted">{me.email}</span></>}
                actions={me.public_url && <Button as="a" icon="fa-external-link" href={me.public_url} target="_blank" rel="noopener noreferrer">Public profile</Button>} />
            <div className="adm-tabs" role="tablist">
                <button type="button" role="tab" aria-selected={tab === 'profile'} className={tab === 'profile' ? 'is-on' : ''} onClick={() => go('profile')}>Profile</button>
                <button type="button" role="tab" aria-selected={tab === 'password'} className={tab === 'password' ? 'is-on' : ''} onClick={() => go('password')}>Password</button>
            </div>
            {tab === 'profile' ? <ProfileForm me={me} toast={toast} /> : <PasswordForm toast={toast} />}
            {toastNode}
        </AdminLayout>
    );
}

function ProfileForm({ me, toast }) {
    const form = useForm({ name: me.name || '', designation: me.designation || '', profile_title: me.profile_title || '', location: me.location || '', profile_description: me.profile_description || '' },
        { name: ['required', 'max:100'] });
    const [photo, setPhoto] = useState([]);
    const save = () => {
        const fd = new FormData();
        Object.entries(form.values).forEach(([k, v]) => fd.append(k, v || ''));
        if (photo[0]) fd.append('photo', photo[0]);
        form.submit(() => axios.post('/admin/api/profile', fd)).then((r) => { toast(r.data.message); setPhoto([]); })
            .catch((e) => { if (!e.invalid) toast(e.response && e.response.status === 422 ? e.response.data.message : errorText(e), 'error'); });
    };
    return (
        <form className="adm-form" onSubmit={(e) => { e.preventDefault(); save(); }} noValidate>
            <FormSection title="About you" description="Shown on your public instructor page and next to your courses.">
                <Row2>
                    <TextField form={form} name="name" label="Name" required max={100} />
                    <TextField form={form} name="location" label="Location" optional />
                </Row2>
                <Row2>
                    <TextField form={form} name="designation" label="Designation" optional />
                    <TextField form={form} name="profile_title" label="Headline" optional />
                </Row2>
                <Field label="About" name="profile_description" optional>
                    <RichText name="profile_description" value={form.values.profile_description} onChange={(x) => form.set('profile_description', x)} minHeight={180} />
                </Field>
                <Field label="Photo" name="photo" optional>
                    <DropZone name="photo" value={photo} onChange={setPhoto} error={form.error('photo')} existing={me.photo_url ? [{ id: 'p', url: me.photo_url, name: 'Current photo' }] : []} />
                </Field>
                <div><Button variant="brand" icon="fa-check" busy={form.busy} type="submit">Save profile</Button></div>
            </FormSection>
        </form>
    );
}

function PasswordForm({ toast }) {
    const form = useForm({ current_password: '', new_password: '', confirm_password: '' },
        { current_password: ['required'], new_password: ['required', 'minlen:8'], confirm_password: ['required'] });
    const save = () => {
        if (form.values.new_password !== form.values.confirm_password) { form.setErrors({ confirm_password: "The two passwords don't match." }); return; }
        form.submit(() => axios.post('/admin/api/password', form.values)).then((r) => { toast(r.data.message); form.reset({ current_password: '', new_password: '', confirm_password: '' }); })
            .catch((e) => { if (!e.invalid) toast(e.response && e.response.status === 422 ? e.response.data.message : errorText(e), 'error'); });
    };
    return (
        <form className="adm-form" onSubmit={(e) => { e.preventDefault(); save(); }} noValidate>
            <FormSection title="Change password" description="Use at least 8 characters. A short phrase of a few words is easy to remember and hard to guess.">
                <TextField form={form} name="current_password" label="Current password" type="password" required inputProps={{ autoComplete: 'current-password' }} />
                <Row2>
                    <TextField form={form} name="new_password" label="New password" type="password" required inputProps={{ autoComplete: 'new-password' }} />
                    <TextField form={form} name="confirm_password" label="Repeat new password" type="password" required inputProps={{ autoComplete: 'new-password' }} />
                </Row2>
                <div><Button variant="brand" icon="fa-lock" busy={form.busy} type="submit">Change password</Button></div>
            </FormSection>
        </form>
    );
}
