import React, { useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import axios from 'axios';
import AdminLayout, { PageHeader, useToast, errorText } from '@/Components/Admin/AdminLayout';
import { shortDate } from '@/Components/Studio/Journey';

const PERSONA_HELP = {
    admin: 'Everything: people, revenue, catalog, settings.',
    teacher: 'Reviews submissions and sees students of their own courses.',
    moderator: 'Moderates submissions, marketplace, gallery, testimonials and commissions.',
};

export default function People() {
    const { admin, students: initialStudents = [], staff: initialStaff = [], personas = [] } = usePage().props;
    const canManage = (admin.capabilities || []).includes('people.manage');
    const [tab, setTab] = useState('students');
    const [students, setStudents] = useState(initialStudents);
    const [staff, setStaff] = useState(initialStaff);
    const [q, setQ] = useState('');
    const [segment, setSegment] = useState('all');
    const [toastNode, toast] = useToast();

    const segments = {
        all: () => true,
        learning: (s) => Number(s.enrollments) > 0,
        submitted: (s) => Number(s.submissions) > 0,
        selling: (s) => Number(s.listed) > 0,
        inactive: (s) => String(s.status) === '0',
    };
    const shownStudents = students.filter((s) => segments[segment](s) && (!q || `${s.name} ${s.email} ${s.location || ''}`.toLowerCase().includes(q.toLowerCase())));
    const shownStaff = staff.filter((s) => !q || `${s.name} ${s.email}`.toLowerCase().includes(q.toLowerCase()));

    const toggleStudent = (s) => {
        const active = String(s.status) !== '1';
        axios.post(`/admin/console/api/students/${s.id}/status`, { active })
            .then(() => { setStudents(students.map((x) => (x.id === s.id ? { ...x, status: active ? 1 : 0 } : x))); toast(`${s.name} ${active ? 'reactivated' : 'deactivated'}`); })
            .catch((e) => toast(errorText(e), 'error'));
    };
    const setPersona = (s, persona) => {
        axios.post(`/admin/console/api/staff/${s.id}/persona`, { persona })
            .then(() => { setStaff(staff.map((x) => (x.id === s.id ? { ...x, admin_type: persona } : x))); toast(`${s.name} is now ${personas.find((p) => p.id === persona).label}`); })
            .catch((e) => toast(errorText(e), 'error'));
    };
    const exportCsv = () => {
        const rows = [['Name', 'Email', 'Phone', 'Location', 'Enrollments', 'Submissions', 'Listed', 'Status', 'Joined'],
            ...shownStudents.map((s) => [s.name, s.email, s.phone || '', s.location || '', s.enrollments, s.submissions, s.listed, String(s.status) === '1' ? 'Active' : 'Inactive', String(s.created_at).slice(0, 10)])];
        const csv = rows.map((r) => r.map((v) => `"${String(v).replace(/"/g, '""')}"`).join(',')).join('\n');
        const a = document.createElement('a');
        a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }));
        a.download = 'artistikcity-students.csv';
        a.click();
    };

    return (
        <AdminLayout title="People & roles">
            <PageHeader title="People & roles" subtitle={admin.persona === 'teacher' ? 'Students enrolled in your courses.' : 'Students, instructors, moderators and admins, with what each persona can do.'}
                        actions={<>
                            <input type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search name or email" className="h-11 w-64 rounded-full border-gray-300 px-4 focus:border-brand focus:ring-brand" aria-label="Search people" />
                            {tab === 'students' && <button type="button" onClick={exportCsv} className="h-11 px-5 rounded-full border border-gray-300 bg-white font-bold hover:border-ink"><i className="fa fa-download mr-2" aria-hidden="true"></i>Export CSV</button>}
                            {canManage && <a href="/admin/teacher/create" className="h-11 px-5 inline-flex items-center rounded-full bg-ink text-white font-bold hover:bg-gray-800">Invite staff</a>}
                        </>} />

            {staff.length > 0 && (
                <div className="flex gap-2 mb-6 border-b border-gray-200">
                    {[['students', `Students (${students.length})`], ['staff', `Staff (${staff.length})`]].map(([k, l]) => (
                        <button key={k} type="button" onClick={() => setTab(k)} className={`px-4 py-3 font-bold border-b-2 -mb-px ${tab === k ? 'border-ink text-ink' : 'border-transparent text-gray-500'}`}>{l}</button>
                    ))}
                </div>
            )}

            {tab === 'students' ? (
                <>
                    <div className="flex flex-wrap gap-2 mb-4">
                        {[['all', 'Everyone'], ['learning', 'Learning'], ['submitted', 'Submitted work'], ['selling', 'Selling'], ['inactive', 'Deactivated']].map(([k, l]) => (
                            <button key={k} type="button" onClick={() => setSegment(k)} className={`h-9 px-4 rounded-full text-sm font-bold border ${segment === k ? 'bg-ink text-white border-ink' : 'bg-white border-gray-300'}`}>{l} <span className="font-mono opacity-70">{students.filter(segments[k]).length}</span></button>
                        ))}
                    </div>
                    <div className="rounded-2xl bg-white border border-gray-200 overflow-x-auto">
                        <table className="w-full text-left text-sm">
                            <thead className="bg-gray-50"><tr className="font-mono text-xs uppercase tracking-widest text-gray-500"><th className="p-3">Student</th><th>Location</th><th className="text-center">Courses</th><th className="text-center">Submissions</th><th className="text-center">For sale</th><th>Joined</th><th>Status</th>{canManage && <th className="p-3"></th>}</tr></thead>
                            <tbody>{shownStudents.map((s) => (
                                <tr key={s.id} className="border-t border-gray-100">
                                    <td className="p-3"><div className="flex items-center gap-3"><span className="w-9 h-9 rounded-full bg-brand-soft text-brand font-black flex items-center justify-center">{String(s.name).charAt(0)}</span><div><p className="font-semibold text-ink">{s.name}</p><p className="text-xs text-gray-500">{s.email}{s.provider ? ` · ${s.provider}` : ''}</p></div></div></td>
                                    <td className="text-gray-600">{s.location || '—'}</td>
                                    <td className="text-center font-mono">{s.enrollments}</td>
                                    <td className="text-center font-mono">{s.submissions}</td>
                                    <td className="text-center font-mono">{s.listed}</td>
                                    <td className="font-mono text-xs">{shortDate(s.created_at)}</td>
                                    <td><span className={`rounded-full px-2.5 py-1 text-xs font-bold ${String(s.status) === '1' ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-600'}`}>{String(s.status) === '1' ? 'Active' : 'Inactive'}</span></td>
                                    {canManage && <td className="p-3 text-right whitespace-nowrap">
                                        <a href={`/admin/students/details/${s.id}`} className="text-sm font-bold underline mr-3">Details</a>
                                        <button type="button" onClick={() => toggleStudent(s)} className="text-sm font-bold text-gray-600 hover:text-ink">{String(s.status) === '1' ? 'Deactivate' : 'Reactivate'}</button>
                                    </td>}
                                </tr>
                            ))}</tbody>
                        </table>
                        {shownStudents.length === 0 && <p className="p-6 text-gray-600">No students match.</p>}
                    </div>
                </>
            ) : (
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                    <div className="lg:col-span-2 rounded-2xl bg-white border border-gray-200 overflow-x-auto">
                        <table className="w-full text-left text-sm">
                            <thead className="bg-gray-50"><tr className="font-mono text-xs uppercase tracking-widest text-gray-500"><th className="p-3">Name</th><th>Role</th><th>Joined</th><th className="p-3"></th></tr></thead>
                            <tbody>{shownStaff.map((s) => (
                                <tr key={s.id} className="border-t border-gray-100">
                                    <td className="p-3"><p className="font-semibold text-ink">{s.name}{String(s.id) === String(admin.id) && <span className="ml-2 font-mono text-xs text-brand">you</span>}</p><p className="text-xs text-gray-500">{s.email}</p></td>
                                    <td>
                                        {canManage && String(s.id) !== String(admin.id) ? (
                                            <select value={s.admin_type} onChange={(e) => setPersona(s, e.target.value)} className="h-9 rounded-lg border-gray-300 text-sm" aria-label={`Role for ${s.name}`}>
                                                {personas.map((p) => <option key={p.id} value={p.id}>{p.label}</option>)}
                                            </select>
                                        ) : <span className="font-semibold">{(personas.find((p) => p.id === s.admin_type) || {}).label}</span>}
                                    </td>
                                    <td className="font-mono text-xs">{shortDate(s.created_at)}</td>
                                    <td className="p-3 text-right">{s.admin_type === 'teacher' && canManage && <a href={`/admin/teacher/edit/${s.id}`} className="text-sm font-bold underline">Edit profile</a>}</td>
                                </tr>
                            ))}</tbody>
                        </table>
                    </div>
                    <aside className="rounded-2xl bg-white border border-gray-200 p-6 self-start">
                        <h2 className="text-lg font-black tracking-tight text-ink">What each role can do</h2>
                        <ul className="mt-4 space-y-4">
                            {personas.map((p) => <li key={p.id}><p className="font-bold text-ink">{p.label}</p><p className="text-sm text-gray-600">{PERSONA_HELP[p.id]}</p></li>)}
                            <li><p className="font-bold text-ink">Student</p><p className="text-sm text-gray-600">Learns in the classroom, submits milestones and curates a portfolio.</p></li>
                        </ul>
                    </aside>
                </div>
            )}
            {toastNode}
        </AdminLayout>
    );
}
