import React, { useEffect, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import axios from 'axios';
import AdminLayout, { PageHeader, useToast, errorText } from '@/Components/Admin/AdminLayout';
import { Button, Badge, DataTable, Pagination, SearchBox, FilterSelect, Toolbar, EmptyState, Drawer, useGrid, useConfirm } from '@/Components/Admin/kit';
import { useForm, Field, TextField, SelectField, SlugField, DropZone, RichText, Row2 } from '@/Components/Admin/form';

/** Staff accounts and the public instructor profiles shown on /teacher/{slug}. */
export default function Instructors() {
    const { roles = [], admin = {} } = usePage().props;
    const g = useGrid('grid');
    const [editing, setEditing] = useState(null);
    const [toastNode, toast] = useToast();
    const [dialog, confirm] = useConfirm();

    useEffect(() => {
        const id = new URLSearchParams(window.location.search).get('edit');
        if (id) { const row = g.rows.find((r) => String(r.id) === id); if (row) setEditing(row); }
    }, []); // eslint-disable-line react-hooks/exhaustive-deps

    const remove = async (r) => {
        if (!(await confirm({ title: `Delete ${r.name}'s account?`, body: 'They can no longer sign in to the admin, and their public profile disappears.', confirmLabel: 'Delete account', danger: true }))) return;
        axios.delete(`/admin/api/instructors/${r.id}`).then((x) => { toast(x.data.message); g.reload(); }).catch((e) => toast(errorText(e), 'error'));
    };

    const columns = [
        { key: 'name', label: 'Name', sort: 'name', render: (r) => (
            <div className="adm-cell">
                {r.photo_url ? <img src={r.photo_url} alt="" className="is-round" /> : <span className="adm-thumb is-round">{(r.name || '?').charAt(0)}</span>}
                <span className="adm-cell__text"><button type="button" className="adm-linkbtn" onClick={() => setEditing(r)}>{r.name}</button><small>{r.designation || r.email}</small></span>
            </div>
        ) },
        { key: 'email', label: 'Sign-in e-mail', hide: 'md', render: (r) => r.email },
        { key: 'role', label: 'Role', render: (r) => <Badge tone={r.admin_type === 'admin' ? 'pink' : r.admin_type === 'moderator' ? 'blue' : 'grey'}>{r.role_label}</Badge> },
        { key: 'courses', label: 'Courses', sort: 'courses', align: 'right', render: (r) => `${r.live_courses} live / ${r.courses}` },
        { key: 'actions', label: <span className="sr-only">Actions</span>, align: 'right', render: (r) => (
            <div className="adm-rowactions">
                <Button size="sm" icon="fa-pencil" onClick={() => setEditing(r)}>Edit</Button>
                {r.public_url && <Button as="a" size="sm" variant="ghost" icon="fa-external-link" href={r.public_url} target="_blank" rel="noopener noreferrer" aria-label={`View ${r.name}'s profile`} title="Public profile" />}
                {String(r.id) !== String(admin.id) && <Button size="sm" variant="ghost" icon="fa-trash-o" onClick={() => remove(r)} aria-label={`Delete ${r.name}`} title="Delete account" />}
            </div>
        ) },
    ];

    return (
        <AdminLayout title="Instructor profiles">
            <PageHeader title="Instructor profiles" subtitle="Admin accounts and the public profiles students see on course pages. Roles decide what each person can do in the admin."
                actions={<Button variant="brand" icon="fa-user-plus" onClick={() => setEditing({})}>Add a person</Button>} />
            <Toolbar>
                <SearchBox value={g.meta.q} onSearch={g.search} placeholder="Search by name or e-mail" />
                <FilterSelect label="Role" value={g.filter('role')} onChange={(v) => g.go({ role: v })} options={roles} />
            </Toolbar>
            <DataTable columns={columns} rows={g.rows} meta={g.meta} loading={g.loading} onSort={g.sortBy} caption="Staff" empty={<EmptyState icon="fa-users" title="Nobody matches" />} />
            <Pagination meta={g.meta} onPage={g.setPage} onPer={g.setPer} />
            {editing && <PersonDrawer item={editing.id ? editing : null} roles={roles} onClose={() => setEditing(null)} toast={toast} onSaved={(m) => { toast(m); setEditing(null); g.reload(); }} />}
            {dialog}{toastNode}
        </AdminLayout>
    );
}

function PersonDrawer({ item, roles, onClose, onSaved, toast }) {
    const form = useForm({
        name: item ? item.name : '', email: item ? item.email : '', slug: item ? item.slug || '' : '', admin_type: item ? item.admin_type : 'teacher',
        designation: item ? item.designation || '' : '', profile_title: item ? item.profile_title || '' : '', location: item ? item.location || '' : '',
        profile_description: item ? item.profile_description || '' : '', password: '',
    }, { name: ['required', 'max:100'], email: ['required', 'email'], slug: ['slug'], password: item ? ['minlen:8'] : ['required', 'minlen:8'] });
    const [photo, setPhoto] = useState([]);
    const save = () => {
        const fd = new FormData();
        if (item) fd.append('id', item.id);
        Object.entries(form.values).forEach(([k, v]) => fd.append(k, v || ''));
        if (photo[0]) fd.append('photo', photo[0]);
        form.submit(() => axios.post('/admin/api/instructors', fd)).then((r) => onSaved(r.data.message))
            .catch((e) => { if (!e.invalid) toast(e.response && e.response.status === 422 ? e.response.data.message : errorText(e), 'error'); });
    };
    return (
        <Drawer open size="lg" title={item ? item.name : 'Add a person'} subtitle={item ? 'Account and public profile' : 'They sign in at /admin/login with this e-mail and password.'} onClose={onClose}
            footer={<><Button onClick={onClose}>Cancel</Button><Button variant="brand" icon="fa-check" busy={form.busy} onClick={save}>{item ? 'Save' : 'Create account'}</Button></>}>
            <form className="adm-stack" onSubmit={(e) => { e.preventDefault(); save(); }} noValidate>
                <h3 className="adm-sub">Account</h3>
                <Row2>
                    <TextField form={form} name="name" label="Name" required max={100} />
                    <TextField form={form} name="email" label="Sign-in e-mail" type="email" required />
                </Row2>
                <Row2>
                    <SelectField form={form} name="admin_type" label="Role" options={roles} placeholder="Choose a role" required />
                    <TextField form={form} name="password" label={item ? 'New password' : 'First password'} type="password" required={!item}
                        hint={item ? 'Leave empty to keep the current password.' : 'At least 8 characters. Share it securely.'} inputProps={{ autoComplete: 'new-password' }} />
                </Row2>
                <h3 className="adm-sub">Public profile</h3>
                <Row2>
                    <TextField form={form} name="designation" label="Designation" placeholder="e.g. Founder & Lead Instructor" optional />
                    <TextField form={form} name="profile_title" label="Headline" placeholder="e.g. Artist, Illustrator & Art Educator" optional />
                </Row2>
                <TextField form={form} name="location" label="Location" optional />
                <SlugField form={form} source="name" prefix="/teacher/" label="Profile URL" />
                <Field label="About" name="profile_description" optional>
                    <RichText name="profile_description" value={form.values.profile_description} onChange={(x) => form.set('profile_description', x)} minHeight={180} />
                </Field>
                <Field label="Photo" name="photo" optional>
                    <DropZone name="photo" value={photo} onChange={setPhoto} error={form.error('photo')} existing={item && item.photo_url ? [{ id: 'p', url: item.photo_url, name: item.name }] : []} />
                </Field>
            </form>
        </Drawer>
    );
}
