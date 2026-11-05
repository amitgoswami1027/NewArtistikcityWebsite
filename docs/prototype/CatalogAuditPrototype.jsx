/*
 * ArtistikCity - course filters, tag manager and security audit log: one-file prototype.
 * React 17/18 + Tailwind CSS, standard hooks only (useState, useMemo, useEffect), no UI libraries.
 * Paste into any React + Tailwind project (or open docs/prototype/index.html) and render <CatalogAuditPrototype />.
 *
 * Three workspaces (tabs):
 *   1. Learner      - Medium + Age pills always visible, Format / Level / Subject in a drawer, forgiving search that works
 *                     with the filters, active chips, a zero-results view with one-click fixes.
 *   2. Admin        - catalog table with fast tagging (type-ahead picklist), and the Tag & Hierarchy manager
 *                     (nest, archive / restore filter categories).
 *   3. Security log - dense table (ISO time, user id / e-mail / role, action type, target, IP / location), quick
 *                     filters (user, action types, time window), a drawer with a side-by-side JSON diff for UPDATEs.
 * The real implementation lives in Pages/Courses.js, Pages/Admin/Taxonomies.js, Components/Admin/form.js (TagPicker)
 * and Pages/Admin/SecurityAudit.js; this file shows the same behaviour on mock data.
 */
import React, { useEffect, useMemo, useState } from 'react';

// ------------------------------------------------------------------------------------------------ mock data

const MEDIUMS = [
    { id: 1, name: 'Painting', parent_id: null },
    { id: 2, name: 'Watercolour', parent_id: 1 },
    { id: 3, name: 'Acrylic', parent_id: 1 },
    { id: 4, name: 'Oil', parent_id: 1 },
    { id: 5, name: 'Drawing', parent_id: null },
    { id: 6, name: 'Pencil', parent_id: 5 },
    { id: 7, name: 'Charcoal', parent_id: 5, archived: true },
];
const AGES = ['Sub-junior · 8–12', 'Junior · 12–15', 'Adult · 16+'];
const LEVELS = [{ id: 1, name: 'Beginner' }, { id: 2, name: 'Intermediate' }, { id: 3, name: 'Advanced' }];
const GENRES_INIT = [
    { id: 1, name: 'Landscape', parent_id: null },
    { id: 2, name: 'Seascape', parent_id: 1 },
    { id: 3, name: 'Portrait', parent_id: null },
    { id: 4, name: 'Still life', parent_id: null },
    { id: 5, name: 'Florals', parent_id: 4 },
    { id: 6, name: 'Abstract', parent_id: null },
    { id: 7, name: 'Urban sketching', parent_id: null, archived: true },
];
const COURSES_INIT = [
    { id: 101, title: 'Watercolour Landscapes for Beginners', medium: 2, age: 'Junior · 12–15', format: 'course', level: 1, genres: [1, 2], price: 4999, students: 212, teacher: 'Vaishali Rao', start: '2026-12-05', seats: 20, taken: 17 },
    { id: 102, title: 'Acrylic Portrait Painting', medium: 3, age: 'Adult · 16+', format: 'course', level: 2, genres: [3], price: 7499, students: 96, teacher: 'Kabir Shah', start: '2026-12-20', seats: 12, taken: 12 },
    { id: 103, title: 'Pencil Sketching Essentials', medium: 6, age: 'Sub-junior · 8–12', format: 'course', level: 1, genres: [4], price: 2999, students: 340, teacher: 'Meera Iyer', start: '2027-01-04', seats: null, taken: 0 },
    { id: 104, title: 'Weekend Watercolour Florals Workshop', medium: 2, age: 'Sub-junior · 8–12', format: 'workshop', level: 1, genres: [5], price: 1499, students: 410, teacher: 'Vaishali Rao', start: '2026-11-25', seats: 25, taken: 9 },
    { id: 105, title: 'Oil Painting: Light on the Sea', medium: 4, age: 'Adult · 16+', format: 'course', level: 3, genres: [2], price: 8999, students: 58, teacher: 'Rohit Menon', start: '2027-02-01', seats: 10, taken: 4 },
    { id: 106, title: 'Abstract Acrylic Pours', medium: 3, age: 'Junior · 12–15', format: 'workshop', level: 2, genres: [6], price: 1999, students: 133, teacher: 'Sana Qureshi', start: '2026-12-12', seats: null, taken: 0 },
    { id: 107, title: 'Still Life in Charcoal', medium: 7, age: 'Adult · 16+', format: 'course', level: 2, genres: [4], price: 3999, students: 41, teacher: 'Kabir Shah', start: '2027-01-15', seats: 15, taken: 2 },
];
const AUDIT = [
    { id: 9041, at: '2026-11-06T09:14:03.218Z', actorId: 1, email: 'amit@artistikcity.com', role: 'admin', type: 'UPDATE', action: 'course updated', resource: 'Course #110', ip: '49.36.12.204', loc: 'Pune, IN', outcome: 'OK',
        summary: 'Admin Amit updated Course 110 details',
        before: { title: 'Watercolour Basics', price_inr: 3999, seat_limit: 20, status: 1, course_start_date: '2026-12-01' },
        after: { title: 'Watercolour Basics: Skies & Seas', price_inr: 4499, seat_limit: 24, status: 1, course_start_date: '2026-12-05' } },
    { id: 9042, at: '2026-11-06T09:21:47.006Z', actorId: 7, email: 'vaishali@artistikcity.com', role: 'teacher', type: 'DENIED', action: 'not allowed', resource: 'POST /admin/api/taxonomy/mediums', ip: '103.21.58.9', loc: 'Mumbai, IN', outcome: 'DENIED',
        summary: 'User performed unauthorized action (403 for teacher)' },
    { id: 9043, at: '2026-11-06T09:22:10.551Z', actorId: null, email: null, role: 'system', type: 'AUTH', action: 'session_revoked', resource: 'Admin #7', ip: '103.21.58.9', loc: 'Mumbai, IN', outcome: 'OK',
        summary: 'Staff session signed out after 5 refused requests in 10 minutes' },
    { id: 9044, at: '2026-11-06T10:02:31.900Z', actorId: 1, email: 'amit@artistikcity.com', role: 'admin', type: 'EXPORT', action: 'security log exported', resource: 'audit_logs', ip: '49.36.12.204', loc: 'Pune, IN', outcome: 'OK',
        summary: '1,284 rows (types=DENIED,AUTH&window=7d)' },
    { id: 9045, at: '2026-11-06T10:40:12.044Z', actorId: 3, email: 'meera@artistikcity.com', role: 'moderator', type: 'DELETE', action: 'faq deleted', resource: 'FAQ #18', ip: '122.170.4.77', loc: 'Bengaluru, IN', outcome: 'OK',
        summary: 'How do I reschedule a class?' },
    { id: 9046, at: '2026-11-06T11:05:56.312Z', actorId: 1, email: 'amit@artistikcity.com', role: 'admin', type: 'CREATE', action: 'course created', resource: 'Course #126', ip: '49.36.12.204', loc: 'Pune, IN', outcome: 'OK',
        summary: 'Ink & Wash Night Workshop', after: { title: 'Ink & Wash Night Workshop', price_inr: 1299, seat_limit: 18, status: 0 } },
    { id: 9047, at: '2026-11-06T11:31:08.777Z', actorId: 52, email: 'ravi@example.com', role: 'student', type: 'AUTH', action: 'login_failed', resource: 'auth:password', ip: '185.220.101.4', loc: 'Frankfurt, DE', outcome: 'FAIL',
        summary: 'Wrong password (3rd attempt)' },
    { id: 9048, at: '2026-11-06T12:15:40.120Z', actorId: 1, email: 'amit@artistikcity.com', role: 'admin', type: 'UPDATE', action: 'settings updated', resource: 'Settings', ip: '49.36.12.204', loc: 'Pune, IN', outcome: 'OK',
        summary: 'seo, contact', before: { 'seo.site_title': 'ArtistikCity', 'contact.phone': '+91 98200 00000' }, after: { 'seo.site_title': 'ArtistikCity · Online art school', 'contact.phone': '+91 98200 00000' } },
];

// ------------------------------------------------------------------------------------------------ helpers

const norm = (s) => String(s || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
function lev1(a, b) { // edit distance <= 1
    if (Math.abs(a.length - b.length) > 1) return false;
    let i = 0; let j = 0; let edits = 0;
    while (i < a.length && j < b.length) {
        if (a[i] === b[j]) { i++; j++; continue; }
        if (++edits > 1) return false;
        if (a.length > b.length) i++; else if (b.length > a.length) j++; else { i++; j++; }
    }
    return edits + (a.length - i) + (b.length - j) <= 1;
}
/** Every typed word must match a word (exact, prefix, inside, or one typo for 4+ letters). 0 = no match. */
function fuzzy(text, q) {
    const terms = norm(q).split(' ').filter(Boolean);
    if (!terms.length) return 1;
    const hay = norm(text); const words = hay.split(' ');
    let score = 0;
    for (const t of terms) {
        if (words.includes(t)) score += 3;
        else if (words.some((w) => w.startsWith(t))) score += 2;
        else if (hay.includes(t)) score += 1.5;
        else if (t.length >= 4 && words.some((w) => lev1(t, w.slice(0, t.length)) || lev1(t, w))) score += 1;
        else return 0;
    }
    return score;
}
const byId = (list) => Object.fromEntries(list.map((x) => [x.id, x]));
const descendantsOf = (list, id) => {
    const out = new Set([id]); let grew = true;
    while (grew) { grew = false; list.forEach((x) => { if (x.parent_id && out.has(x.parent_id) && !out.has(x.id)) { out.add(x.id); grew = true; } }); }
    return out;
};
const pathOf = (list, item) => { const m = byId(list); const p = [item.name]; let at = m[item.parent_id]; while (at) { p.unshift(at.name); at = m[at.parent_id]; } return p.join(' › '); };
const treeOrder = (list) => {
    const out = []; const walk = (pid, d) => list.filter((x) => (x.parent_id || null) === pid).sort((a, b) => a.name.localeCompare(b.name)).forEach((x) => { out.push({ ...x, depth: d }); walk(x.id, d + 1); });
    walk(null, 0); return out;
};
const inr = (n) => `₹${Number(n).toLocaleString('en-IN')}`;
const toggleIn = (set, v) => { const n = new Set(set); n.has(v) ? n.delete(v) : n.add(v); return n; };

// ------------------------------------------------------------------------------------------------ shared UI

function Badge({ tone = 'slate', children }) {
    const tones = { slate: 'bg-slate-100 text-slate-700', green: 'bg-emerald-50 text-emerald-800', blue: 'bg-blue-50 text-blue-800', red: 'bg-red-50 text-red-800',
        amber: 'bg-amber-50 text-amber-900', purple: 'bg-violet-50 text-violet-800', pink: 'bg-pink-50 text-pink-900' };
    return <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-bold ${tones[tone]}`}>{children}</span>;
}
function Drawer({ title, onClose, children, footer, wide }) {
    useEffect(() => {
        const esc = (e) => { if (e.key === 'Escape') onClose(); };
        document.addEventListener('keydown', esc);
        return () => document.removeEventListener('keydown', esc);
    }, [onClose]);
    return (
        <div className="fixed inset-0 z-50 flex justify-end bg-slate-900/40" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
            <aside role="dialog" aria-modal="true" aria-label={title} className={`flex h-full w-full flex-col bg-white shadow-2xl ${wide ? 'max-w-2xl' : 'max-w-sm'}`}>
                <header className="flex items-center justify-between border-b px-5 py-4">
                    <h2 className="text-lg font-extrabold text-slate-900">{title}</h2>
                    <button type="button" onClick={onClose} aria-label="Close" className="h-9 w-9 rounded-full bg-slate-100 text-slate-700 hover:bg-slate-200">✕</button>
                </header>
                <div className="flex-1 overflow-auto px-5 py-4">{children}</div>
                {footer && <footer className="flex items-center justify-between gap-3 border-t px-5 py-4">{footer}</footer>}
            </aside>
        </div>
    );
}
const pill = (on) => `h-9 rounded-full border px-4 text-sm font-semibold focus:outline-none focus-visible:ring-4 focus-visible:ring-slate-900/40 ${on ? 'border-slate-900 bg-slate-900 text-white' : 'border-slate-300 bg-white text-slate-800 hover:border-slate-900'}`;

// ------------------------------------------------------------------------------------------------ 1. learner

function LearnerGrid({ courses, genres }) {
    const [q, setQ] = useState('');
    const [medium, setMedium] = useState(null);
    const [ages, setAges] = useState(new Set());
    const [formats, setFormats] = useState(new Set());
    const [levels, setLevels] = useState(new Set());
    const [subjects, setSubjects] = useState(new Set());
    const [drawer, setDrawer] = useState(false);
    const mediums = MEDIUMS.filter((m) => !m.archived);
    const liveGenres = genres.filter((g) => !g.archived);
    const mById = byId(MEDIUMS); const gById = byId(genres);

    const passes = (c, skip) => {
        if (skip !== 'medium' && medium && !descendantsOf(MEDIUMS, medium).has(c.medium)) return false;
        if (skip !== 'age' && ages.size && !ages.has(c.age)) return false;
        if (skip !== 'format' && formats.size && !formats.has(c.format)) return false;
        if (skip !== 'level' && levels.size && !levels.has(c.level)) return false;
        if (skip !== 'subject' && subjects.size && ![...subjects].some((s) => c.genres.some((g) => descendantsOf(genres, s).has(g)))) return false;
        const hay = `${c.title} ${c.teacher} ${(mById[c.medium] || {}).name} ${c.genres.map((g) => (gById[g] || {}).name).join(' ')}`;
        if (skip !== 'q' && q.trim() && !fuzzy(hay, q)) return false;
        return true;
    };
    const results = useMemo(() => courses.filter((c) => passes(c))
        .map((c) => [c, fuzzy(`${c.title} ${c.teacher}`, q)]).sort((a, b) => b[1] - a[1] || b[0].students - a[0].students).map((x) => x[0]),
    [courses, q, medium, ages, formats, levels, subjects, genres]);

    const chips = [
        q.trim() && { key: 'q', kind: 'Search', label: `“${q.trim()}”`, skip: 'q', remove: () => setQ('') },
        medium && { key: 'm', kind: 'Medium', label: mById[medium].name, skip: 'medium', remove: () => setMedium(null) },
        ...[...ages].map((a) => ({ key: `a${a}`, kind: 'Age', label: a, skip: 'age', remove: () => setAges(toggleIn(ages, a)) })),
        ...[...formats].map((f) => ({ key: `f${f}`, kind: 'Format', label: f === 'course' ? 'Live courses' : 'Workshops', skip: 'format', remove: () => setFormats(toggleIn(formats, f)) })),
        ...[...levels].map((l) => ({ key: `l${l}`, kind: 'Level', label: LEVELS.find((x) => x.id === l).name, skip: 'level', remove: () => setLevels(toggleIn(levels, l)) })),
        ...[...subjects].map((s) => ({ key: `s${s}`, kind: 'Subject', label: gById[s].name, skip: 'subject', remove: () => setSubjects(toggleIn(subjects, s)) })),
    ].filter(Boolean);
    const rescue = results.length ? [] : chips.map((c) => ({ ...c, n: courses.filter((x) => passes(x, c.skip)).length })).filter((c) => c.n).sort((a, b) => b.n - a.n).slice(0, 3);
    const clear = () => { setQ(''); setMedium(null); setAges(new Set()); setFormats(new Set()); setLevels(new Set()); setSubjects(new Set()); };
    const drawerCount = formats.size + levels.size + subjects.size;
    const count = (skip, test) => courses.filter((c) => passes(c, skip) && test(c)).length;

    return (
        <section aria-label="Learner course grid">
            <nav aria-label="Mediums" className="flex flex-wrap gap-2 border-b pb-4">
                <button type="button" aria-pressed={!medium} className={pill(!medium)} onClick={() => setMedium(null)}>All</button>
                {mediums.filter((m) => !m.parent_id).map((m) => (
                    <React.Fragment key={m.id}>
                        <button type="button" aria-pressed={medium === m.id} className={pill(medium === m.id)} onClick={() => setMedium(medium === m.id ? null : m.id)}>{m.name}</button>
                        {mediums.filter((c) => c.parent_id === m.id).map((c) => (
                            <button key={c.id} type="button" aria-pressed={medium === c.id} className={`${pill(medium === c.id)} !h-8 !px-3 !text-xs`} onClick={() => setMedium(medium === c.id ? null : c.id)}>{c.name}</button>
                        ))}
                    </React.Fragment>
                ))}
            </nav>
            <div className="flex flex-wrap items-center gap-3 py-4">
                <label className="relative min-w-[220px] flex-1 sm:max-w-xs">
                    <span className="sr-only">Search courses</span>
                    <input type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search courses, techniques, teachers"
                        className="h-10 w-full rounded-full border border-slate-300 pl-4 pr-4 text-sm focus:border-slate-900 focus:outline-none focus-visible:ring-4 focus-visible:ring-slate-900/20" />
                </label>
                <div role="group" aria-label="Age group" className="flex flex-wrap items-center gap-2">
                    <span className="text-xs font-extrabold uppercase tracking-wider text-slate-600">Age</span>
                    {AGES.map((a) => <button key={a} type="button" aria-pressed={ages.has(a)} className={pill(ages.has(a))} onClick={() => setAges(toggleIn(ages, a))}>{a}</button>)}
                </div>
                <button type="button" onClick={() => setDrawer(true)} className={`inline-flex h-10 items-center gap-2 rounded-full border border-slate-900 px-4 text-sm font-bold ${drawerCount ? 'bg-pink-50' : 'bg-white'}`}>
                    ⚙ More filters {drawerCount > 0 && <span className="grid h-5 min-w-[20px] place-items-center rounded-full bg-[#9C0D48] px-1.5 text-xs text-white">{drawerCount}</span>}
                </button>
                <p className="ml-auto text-sm font-semibold text-slate-700" aria-live="polite">{results.length} {results.length === 1 ? 'course' : 'courses'}</p>
            </div>
            {chips.length > 0 && (
                <ul aria-label="Active filters" className="mb-5 flex flex-wrap items-center gap-2 border-b pb-4">
                    {chips.map((c) => (
                        <li key={c.key}><button type="button" onClick={c.remove} aria-label={`Remove ${c.kind} ${c.label}`}
                            className="inline-flex items-center gap-1.5 rounded-full border border-pink-200 bg-pink-50 px-3 py-1 text-sm font-semibold text-pink-900 hover:border-[#9C0D48]">
                            <span className="text-[10px] font-extrabold uppercase text-[#9C0D48]">{c.kind}</span>{c.label} <span aria-hidden="true">✕</span></button></li>
                    ))}
                    <li><button type="button" onClick={clear} className="text-sm font-bold underline">Clear filters</button></li>
                </ul>
            )}
            {results.length ? (
                <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
                    {results.map((c) => {
                        const left = c.seats ? c.seats - c.taken : null;
                        return (
                            <article key={c.id} className="flex min-w-0 flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white">
                                <div className="relative h-36 bg-gradient-to-br from-slate-800 via-[#5e0b30] to-[#9C0D48]">
                                    {left === 0 ? <span className="absolute left-3 top-3"><Badge>Sold out</Badge></span>
                                        : left !== null && left <= 5 ? <span className="absolute left-3 top-3"><Badge tone="amber">Only {left} seats left</Badge></span>
                                            : c.format === 'workshop' && <span className="absolute left-3 top-3"><Badge tone="pink">Workshop</Badge></span>}
                                </div>
                                <div className="flex flex-1 flex-col gap-1 p-4">
                                    <h3 className="font-extrabold leading-snug text-slate-900">{c.title}</h3>
                                    <p className="text-sm text-slate-600">{c.teacher} · {(mById[c.medium] || {}).name} · {c.age}</p>
                                    <p className="mt-auto pt-2 font-bold text-slate-900">{inr(c.price)} <span className="text-sm font-normal text-slate-600">· starts {c.start}</span></p>
                                </div>
                            </article>
                        );
                    })}
                </div>
            ) : (
                <div role="status" className="rounded-2xl border-2 border-dashed border-slate-300 px-6 py-14 text-center">
                    <h3 className="text-xl font-extrabold text-slate-900">No courses match your filters</h3>
                    {rescue.length ? (
                        <>
                            <p className="mt-1 text-slate-600">One change brings courses back:</p>
                            <div className="my-4 flex flex-wrap justify-center gap-2">
                                {rescue.map((r) => <button key={r.key} type="button" onClick={r.remove} className="rounded-full border border-pink-200 bg-pink-50 px-3 py-1.5 text-sm font-semibold text-pink-900">Remove {r.kind.toLowerCase()} {r.label} · {r.n}</button>)}
                            </div>
                        </>
                    ) : <p className="mt-1 text-slate-600">Try another word or medium.</p>}
                    <button type="button" onClick={clear} className="mt-2 rounded-full bg-slate-900 px-5 py-2.5 font-bold text-white">Clear filters</button>
                </div>
            )}
            {drawer && (
                <Drawer title="More filters" onClose={() => setDrawer(false)}
                    footer={<><button type="button" className="text-sm font-bold underline" onClick={() => { setFormats(new Set()); setLevels(new Set()); setSubjects(new Set()); }}>Reset these</button>
                        <button type="button" className="rounded-full bg-slate-900 px-5 py-2.5 font-bold text-white" onClick={() => setDrawer(false)}>Show {results.length} courses</button></>}>
                    {[['Format', [['course', 'Live courses'], ['workshop', 'Workshops']], formats, setFormats, 'format', (v) => (c) => c.format === v, 0],
                        ['Skill level', LEVELS.map((l) => [l.id, l.name]), levels, setLevels, 'level', (v) => (c) => c.level === v, 0],
                        ['Subject', treeOrder(liveGenres).map((g) => [g.id, g.name, g.depth]), subjects, setSubjects, 'subject', (v) => (c) => c.genres.some((g) => descendantsOf(genres, v).has(g))],
                    ].map(([legend, opts, sel, setSel, skip, test]) => (
                        <fieldset key={legend} className="border-b py-3 last:border-0">
                            <legend className="mb-1 font-extrabold text-slate-900">{legend}</legend>
                            {opts.map(([v, label, depth]) => (
                                <label key={v} className="flex min-h-[40px] cursor-pointer items-center gap-3 rounded-lg px-2 hover:bg-slate-50" style={{ paddingLeft: 8 + (depth || 0) * 18 }}>
                                    <input type="checkbox" className="h-4 w-4 accent-[#9C0D48]" checked={sel.has(v)} onChange={() => setSel(toggleIn(sel, v))} />
                                    <span className="flex-1">{label}</span><small className="text-slate-600">{count(skip, test(v))}</small>
                                </label>
                            ))}
                        </fieldset>
                    ))}
                </Drawer>
            )}
        </section>
    );
}

// ------------------------------------------------------------------------------------------------ 2. admin

function TagPicker({ options, value, onChange }) {
    const [q, setQ] = useState(''); const [open, setOpen] = useState(false); const [hi, setHi] = useState(0);
    const live = options.filter((o) => !o.archived && !value.includes(o.id));
    const matches = live.filter((o) => !q || norm(pathOf(options, o)).includes(norm(q))).slice(0, 8);
    const add = (o) => { if (o) { onChange([...value, o.id]); setQ(''); setHi(0); } };
    return (
        <div className="relative">
            <div className="flex min-h-[40px] flex-wrap items-center gap-1.5 rounded-lg border border-slate-300 bg-white px-2 py-1 focus-within:border-[#9C0D48]">
                {value.map((id) => { const o = options.find((x) => x.id === id); return (
                    <span key={id} className="inline-flex items-center gap-1 rounded-full bg-pink-50 py-0.5 pl-2.5 pr-1 text-xs font-semibold text-pink-900">
                        {o ? pathOf(options, o) : id}<button type="button" aria-label={`Remove ${o && o.name}`} className="h-5 w-5 rounded-full hover:bg-pink-100" onClick={() => onChange(value.filter((x) => x !== id))}>✕</button></span>); })}
                <input value={q} onChange={(e) => { setQ(e.target.value); setOpen(true); }} onFocus={() => setOpen(true)} onBlur={() => setTimeout(() => setOpen(false), 120)}
                    onKeyDown={(e) => { if (e.key === 'ArrowDown') { e.preventDefault(); setHi(Math.min(hi + 1, matches.length - 1)); } if (e.key === 'ArrowUp') setHi(Math.max(0, hi - 1)); if (e.key === 'Enter') { e.preventDefault(); add(matches[hi]); } if (e.key === 'Backspace' && !q && value.length) onChange(value.slice(0, -1)); }}
                    placeholder={value.length ? '' : 'Type, Enter to add'} aria-label="Add genre" role="combobox" aria-expanded={open} className="min-w-[110px] flex-1 border-0 p-1 text-sm outline-none" />
            </div>
            {open && matches.length > 0 && (
                <ul role="listbox" className="absolute z-20 mt-1 max-h-56 w-full overflow-auto rounded-lg border bg-white p-1 shadow-lg">
                    {matches.map((o, i) => <li key={o.id} role="option" aria-selected={i === hi} onMouseDown={(e) => { e.preventDefault(); add(o); }}
                        className={`cursor-pointer rounded-md px-2 py-1.5 text-sm ${i === hi ? 'bg-slate-100' : ''}`}>{pathOf(options, o)}</li>)}
                </ul>
            )}
        </div>
    );
}

function AdminCatalog({ courses, setCourses, genres, setGenres }) {
    const [newTag, setNewTag] = useState(''); const [parent, setParent] = useState(''); const [showArchived, setShowArchived] = useState(false);
    const usage = (id) => courses.filter((c) => c.genres.includes(id)).length;
    const rows = treeOrder(genres.filter((g) => showArchived || !g.archived));
    const addTag = () => {
        if (!newTag.trim()) return;
        setGenres([...genres, { id: Math.max(...genres.map((g) => g.id)) + 1, name: newTag.trim(), parent_id: parent ? Number(parent) : null }]);
        setNewTag(''); setParent('');
    };
    const archive = (g, on) => setGenres(genres.map((x) => (x.id === g.id ? { ...x, archived: on } : on && x.parent_id === g.id ? { ...x, parent_id: g.parent_id } : x)));
    const move = (g, pid) => setGenres(genres.map((x) => (x.id === g.id ? { ...x, parent_id: pid ? Number(pid) : null } : x)));
    return (
        <div className="grid gap-6 xl:grid-cols-[1.4fr_1fr]">
            <section aria-label="Course catalog" className="min-w-0 rounded-2xl border bg-white">
                <header className="border-b px-5 py-4"><h2 className="font-extrabold text-slate-900">Course catalog</h2><p className="text-sm text-slate-600">Fast tagging: type a few letters, Enter adds the genre; Backspace removes the last.</p></header>
                <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                        <thead><tr className="border-b bg-slate-50 text-left text-xs uppercase tracking-wider text-slate-600"><th className="px-4 py-2">Course</th><th className="px-4 py-2">Seats</th><th className="px-4 py-2 w-[46%]">Genres</th></tr></thead>
                        <tbody>
                            {courses.map((c) => (
                                <tr key={c.id} className="border-b align-top last:border-0">
                                    <td className="px-4 py-3"><b className="text-slate-900">#{c.id} {c.title}</b><div className="text-xs text-slate-600">{c.format} · {c.age}</div></td>
                                    <td className="px-4 py-3 whitespace-nowrap">{c.seats ? `${c.taken} / ${c.seats}` : '—'}</td>
                                    <td className="px-4 py-3"><TagPicker options={genres} value={c.genres} onChange={(v) => setCourses(courses.map((x) => (x.id === c.id ? { ...x, genres: v } : x)))} /></td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            </section>
            <section aria-label="Tag and hierarchy manager" className="min-w-0 rounded-2xl border bg-white">
                <header className="flex flex-wrap items-center justify-between gap-2 border-b px-5 py-4">
                    <div><h2 className="font-extrabold text-slate-900">Tag & hierarchy manager</h2><p className="text-sm text-slate-600">Genres. Nest under a parent; archive to hide from filters (kept on courses).</p></div>
                    <label className="flex items-center gap-2 text-sm font-semibold text-slate-700"><input type="checkbox" checked={showArchived} onChange={(e) => setShowArchived(e.target.checked)} /> Show archived</label>
                </header>
                <form className="flex flex-wrap gap-2 border-b px-5 py-3" onSubmit={(e) => { e.preventDefault(); addTag(); }}>
                    <input value={newTag} onChange={(e) => setNewTag(e.target.value)} placeholder="New genre" aria-label="New genre" className="h-9 flex-1 rounded-lg border px-3 text-sm" />
                    <select value={parent} onChange={(e) => setParent(e.target.value)} aria-label="Parent" className="h-9 rounded-lg border px-2 text-sm">
                        <option value="">Top level</option>{treeOrder(genres.filter((g) => !g.archived)).map((g) => <option key={g.id} value={g.id}>{'  '.repeat(g.depth)}{g.name}</option>)}
                    </select>
                    <button type="submit" className="h-9 rounded-lg bg-[#9C0D48] px-4 text-sm font-bold text-white">Add</button>
                </form>
                <ul className="divide-y">
                    {rows.map((g) => (
                        <li key={g.id} className="flex flex-wrap items-center gap-2 px-5 py-2.5" style={{ paddingLeft: 20 + g.depth * 22 }}>
                            <span className="flex-1 font-semibold text-slate-900">{g.depth > 0 && <span className="text-slate-400">└ </span>}{g.name} {g.archived && <Badge tone="amber">Archived</Badge>}
                                <span className="ml-2 text-xs font-normal text-slate-600">{usage(g.id)} courses</span></span>
                            <select value={g.parent_id || ''} onChange={(e) => move(g, e.target.value)} aria-label={`Parent of ${g.name}`} className="h-8 rounded-md border px-1 text-xs">
                                <option value="">Top level</option>{genres.filter((x) => !x.archived && !descendantsOf(genres, g.id).has(x.id)).map((x) => <option key={x.id} value={x.id}>{x.name}</option>)}
                            </select>
                            <button type="button" onClick={() => archive(g, !g.archived)} className="h-8 rounded-md border px-2.5 text-xs font-bold">{g.archived ? 'Restore' : 'Archive'}</button>
                        </li>
                    ))}
                </ul>
            </section>
        </div>
    );
}

// ------------------------------------------------------------------------------------------------ 3. security log

const TYPE_TONE = { CREATE: 'green', UPDATE: 'blue', DELETE: 'red', AUTH: 'purple', EXPORT: 'amber', DENIED: 'red' };
const NOW = new Date('2026-11-06T12:30:00Z').getTime();

function diffRows(before = {}, after = {}) {
    return Array.from(new Set([...Object.keys(before || {}), ...Object.keys(after || {})])).sort().map((k) => {
        const b = before && k in before ? JSON.stringify(before[k]) : undefined;
        const a = after && k in after ? JSON.stringify(after[k]) : undefined;
        return { k, b, a, kind: b === undefined ? 'added' : a === undefined ? 'removed' : a === b ? 'same' : 'changed' };
    });
}

function AuditViewer() {
    const [user, setUser] = useState('');
    const [types, setTypes] = useState(new Set());
    const [win, setWin] = useState('');
    const [open, setOpen] = useState(null);
    const [onlyChanged, setOnlyChanged] = useState(true);
    const rows = useMemo(() => AUDIT.filter((e) => {
        const u = user.trim().toLowerCase();
        if (u && !(String(e.actorId) === u || (e.email || '').includes(u))) return false;
        if (types.size && !types.has(e.type)) return false;
        if (win) { const ms = { '1h': 36e5, '24h': 864e5, '7d': 6048e5 }[win]; if (NOW - new Date(e.at).getTime() > ms) return false; }
        return true;
    }).sort((a, b) => b.at.localeCompare(a.at)), [user, types, win]);
    const e = open && AUDIT.find((x) => x.id === open);
    const diff = e ? diffRows(e.before, e.after) : [];
    return (
        <section aria-label="Security audit log" className="rounded-2xl border bg-white">
            <header className="flex flex-wrap items-center gap-3 border-b px-5 py-4">
                <div className="mr-auto"><h2 className="font-extrabold text-slate-900">Security log</h2><p className="text-sm text-slate-600">Append-only, hash-chained. Super admins only.</p></div>
                <label className="text-sm"><span className="sr-only">User ID or e-mail</span>
                    <input value={user} onChange={(x) => setUser(x.target.value)} placeholder="User ID or e-mail" className="h-9 w-48 rounded-lg border px-3" /></label>
                <div role="group" aria-label="Time window" className="flex overflow-hidden rounded-lg border text-sm">
                    {[['', 'All'], ['1h', '1 h'], ['24h', '24 h'], ['7d', '7 d']].map(([v, l]) => <button key={v} type="button" aria-pressed={win === v} onClick={() => setWin(v)} className={`px-3 py-1.5 font-semibold ${win === v ? 'bg-slate-900 text-white' : 'bg-white'}`}>{l}</button>)}
                </div>
            </header>
            <div role="group" aria-label="Action types" className="flex flex-wrap gap-2 border-b px-5 py-3">
                {Object.keys(TYPE_TONE).map((t) => <button key={t} type="button" aria-pressed={types.has(t)} onClick={() => setTypes(toggleIn(types, t))}
                    className={`rounded-full border px-3 py-1 text-xs font-bold tracking-wide ${types.has(t) ? 'border-slate-900 bg-slate-900 text-white' : 'bg-white text-slate-700'}`}>{t}</button>)}
                {(user || types.size || win) && <button type="button" className="text-xs font-bold underline" onClick={() => { setUser(''); setTypes(new Set()); setWin(''); }}>Clear all</button>}
            </div>
            <div className="overflow-x-auto">
                <table className="w-full text-[13px]">
                    <caption className="sr-only">Audit log entries</caption>
                    <thead><tr className="border-b bg-slate-50 text-left text-[11px] uppercase tracking-wider text-slate-600">
                        <th className="px-4 py-2">Timestamp (ISO)</th><th className="px-4 py-2">User</th><th className="px-4 py-2">Action</th><th className="px-4 py-2">Target resource</th><th className="px-4 py-2">IP / location</th><th className="px-4 py-2"><span className="sr-only">Details</span></th></tr></thead>
                    <tbody>
                        {rows.map((r) => (
                            <tr key={r.id} className="cursor-pointer border-b align-top hover:bg-slate-50" onClick={() => setOpen(r.id)}>
                                <td className="whitespace-nowrap px-4 py-2 font-mono text-[12px]">{r.at}</td>
                                <td className="px-4 py-2"><b>{r.email || (r.role === 'system' ? 'System' : 'Anonymous')}</b><div className="text-xs text-slate-600">{r.actorId ? `ID ${r.actorId} · ` : ''}{r.role}</div></td>
                                <td className="px-4 py-2"><Badge tone={TYPE_TONE[r.type]}>{r.type}</Badge> {r.outcome === 'FAIL' && <Badge tone="red">Failed</Badge>}<div className="text-xs text-slate-600">{r.action}</div></td>
                                <td className="px-4 py-2"><b className="font-mono text-[12px]">{r.resource}</b><div className="text-xs text-slate-600">{r.summary}</div></td>
                                <td className="whitespace-nowrap px-4 py-2"><span className="font-mono text-[12px]">{r.ip}</span><div className="text-xs text-slate-600">{r.loc}</div></td>
                                <td className="px-4 py-2 text-right"><button type="button" onClick={(x) => { x.stopPropagation(); setOpen(r.id); }} className={`rounded-md px-2.5 py-1 text-xs font-bold ${r.before ? 'bg-slate-900 text-white' : 'border'}`}>{r.before ? 'Diff' : 'Details'}</button></td>
                            </tr>
                        ))}
                        {!rows.length && <tr><td colSpan={6} className="px-4 py-10 text-center text-slate-600">No entries match these filters.</td></tr>}
                    </tbody>
                </table>
            </div>
            {e && (
                <Drawer wide title={`${e.type} · ${e.action}`} onClose={() => setOpen(null)}>
                    <dl className="grid grid-cols-[120px_1fr] gap-x-4 gap-y-2 text-sm">
                        <dt className="font-bold text-slate-600">When</dt><dd className="font-mono">{e.at}</dd>
                        <dt className="font-bold text-slate-600">User</dt><dd>{e.email || e.role}{e.actorId && ` · ID ${e.actorId}`} · {e.role}</dd>
                        <dt className="font-bold text-slate-600">Resource</dt><dd className="font-mono">{e.resource}</dd>
                        <dt className="font-bold text-slate-600">Summary</dt><dd>{e.summary}</dd>
                        <dt className="font-bold text-slate-600">From</dt><dd><span className="font-mono">{e.ip}</span> · {e.loc}</dd>
                    </dl>
                    {(e.before || e.after) && (
                        <>
                            <div className="mt-6 flex items-center justify-between"><h3 className="font-extrabold">Changes</h3>
                                <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={onlyChanged} onChange={(x) => setOnlyChanged(x.target.checked)} /> Only changed fields</label></div>
                            <div role="table" aria-label="Before and after" className="mt-2 overflow-hidden rounded-lg border text-[12.5px]">
                                <div role="row" className="grid grid-cols-[1fr_2fr_2fr] bg-slate-50 text-[11px] font-bold uppercase tracking-wider text-slate-600"><span role="columnheader" className="px-3 py-2">Field</span><span role="columnheader" className="px-3 py-2">Before</span><span role="columnheader" className="px-3 py-2">After</span></div>
                                {diff.filter((d) => !onlyChanged || d.kind !== 'same').map((d) => (
                                    <div role="row" key={d.k} className="grid grid-cols-[1fr_2fr_2fr] border-t">
                                        <span role="cell" className="break-all px-3 py-2 font-mono">{d.k}</span>
                                        <span role="cell" className={`break-all px-3 py-2 font-mono ${d.kind === 'changed' || d.kind === 'removed' ? 'bg-red-50 text-red-900' : ''}`}>{d.b === undefined ? <i>(none)</i> : d.b}</span>
                                        <span role="cell" className={`break-all px-3 py-2 font-mono ${d.kind === 'changed' || d.kind === 'added' ? 'bg-emerald-50 text-emerald-900' : ''}`}>{d.a === undefined ? <i>(none)</i> : d.a}</span>
                                    </div>
                                ))}
                            </div>
                        </>
                    )}
                </Drawer>
            )}
        </section>
    );
}

// ------------------------------------------------------------------------------------------------ shell

export default function CatalogAuditPrototype() {
    const [tab, setTab] = useState('learner');
    const [courses, setCourses] = useState(COURSES_INIT);
    const [genres, setGenres] = useState(GENRES_INIT);
    const tabs = [['learner', 'Learner course grid'], ['admin', 'Admin catalog & tags'], ['audit', 'Security audit log']];
    return (
        <div className="min-h-screen bg-slate-50 text-slate-900">
            <header className="border-b bg-white">
                <div className="mx-auto flex max-w-7xl flex-wrap items-center gap-4 px-4 py-3">
                    <b className="text-lg font-black tracking-tight">Artistik<span className="text-[#9C0D48]">City</span></b>
                    <nav role="tablist" aria-label="Workspace" className="flex flex-wrap gap-1 rounded-full bg-slate-100 p-1">
                        {tabs.map(([k, l]) => <button key={k} type="button" role="tab" aria-selected={tab === k} onClick={() => setTab(k)}
                            className={`rounded-full px-4 py-1.5 text-sm font-bold ${tab === k ? 'bg-white text-slate-900 shadow' : 'text-slate-600 hover:text-slate-900'}`}>{l}</button>)}
                    </nav>
                </div>
            </header>
            <main className="mx-auto max-w-7xl px-4 py-6">
                {tab === 'learner' && <LearnerGrid courses={courses} genres={genres} />}
                {tab === 'admin' && <AdminCatalog courses={courses} setCourses={setCourses} genres={genres} setGenres={setGenres} />}
                {tab === 'audit' && <AuditViewer />}
            </main>
        </div>
    );
}
