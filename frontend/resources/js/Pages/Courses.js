import React, { useEffect, useMemo, useRef, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import SiteLayout from '@/Components/Site/SiteLayout';
import CourseCard from '@/Components/Site/CourseCard';
import { isWorkshop } from '@/Components/Site/ProgramCard';
import { useAgeGroups } from '@/Components/Site/SiteHeader';
import { toSet, fromQuery, writeQuery, DEFAULT_SORT, fuzzyScore } from '@/Components/Site/catalogQuery';

/*
 * /courses: progressive disclosure.
 *   Always visible: Medium pills (the main way people browse), Age group pills, search, sort and the result count.
 *   "More filters" drawer: Format, Skill level and Subject (genres, nested as set up in Admin > Catalog).
 * Search and filters work together: filters narrow the list, the search ranks and narrows within it (forgiving of
 * typos and accents). Every active value shows as a removable chip; when nothing matches, the page offers the one
 * change that brings results back, plus a single "Clear filters". The state lives in the address bar (catalogQuery).
 */

/** A course belongs to an age group when it picked that group (exact name, set in the course editor). */
function ageMatches(courseAge, filterAge) {
    if (!courseAge) return false;
    return String(courseAge).trim().toLowerCase() === String(filterAge).trim().toLowerCase();
}

/** id -> [id and every descendant id] for nested lists, so picking "Painting" also finds "Watercolour" courses. */
function descendants(items) {
    const kids = {};
    items.forEach((i) => { if (i.parent_id) (kids[String(i.parent_id)] = kids[String(i.parent_id)] || []).push(String(i.id)); });
    const out = {};
    items.forEach((i) => {
        const all = new Set([String(i.id)]);
        const stack = [String(i.id)];
        while (stack.length) (kids[stack.pop()] || []).forEach((k) => { if (!all.has(k)) { all.add(k); stack.push(k); } });
        out[String(i.id)] = all;
    });
    return out;
}

/** Nested list in display order with depth. */
function tree(items) {
    const ids = new Set(items.map((i) => String(i.id)));
    const by = {};
    items.forEach((i) => { const k = i.parent_id && ids.has(String(i.parent_id)) ? String(i.parent_id) : ''; (by[k] = by[k] || []).push(i); });
    const out = [];
    const walk = (k, d) => (by[k] || []).forEach((i) => { out.push({ ...i, depth: d }); if (d < 4) walk(String(i.id), d + 1); });
    walk('', 0);
    return out;
}


function CountBadge({ n }) {
    return (
        <small>
            <span aria-hidden="true">{n}</span>
            <span className="sr-only">{`, ${n} ${n === 1 ? 'course' : 'courses'}`}</span>
        </small>
    );
}

function CheckGroup({ legend, options, selected, onToggle, counts }) {
    if (!options.length) return null;
    return (
        <fieldset className="cf-group">
            <legend>{legend}</legend>
            {options.map((o) => (
                <label key={o.value} className="cf-check" style={o.depth ? { paddingLeft: 8 + o.depth * 18 } : undefined}>
                    <input type="checkbox" checked={selected.has(String(o.value))} onChange={() => onToggle(String(o.value))} />
                    <span>{o.label}</span>
                    {counts && <CountBadge n={counts[String(o.value)] || 0} />}
                </label>
            ))}
        </fieldset>
    );
}

function Drawer({ open, onClose, count, onClear, children }) {
    const panel = useRef(null);
    useEffect(() => {
        if (!open) return undefined;
        const prev = document.activeElement;
        const first = panel.current && panel.current.querySelector('input, button');
        if (first) first.focus();
        const key = (e) => {
            if (e.key === 'Escape') onClose();
            if (e.key === 'Tab' && panel.current) {
                // keep focus inside the panel while it is open
                const f = panel.current.querySelectorAll('button, input, select, a[href]');
                if (!f.length) return;
                if (e.shiftKey && document.activeElement === f[0]) { e.preventDefault(); f[f.length - 1].focus(); }
                else if (!e.shiftKey && document.activeElement === f[f.length - 1]) { e.preventDefault(); f[0].focus(); }
            }
        };
        document.addEventListener('keydown', key);
        document.body.classList.add('cf-noscroll');
        return () => { document.removeEventListener('keydown', key); document.body.classList.remove('cf-noscroll'); if (prev && prev.focus) prev.focus(); };
    }, [open]);
    if (!open) return null;
    return (
        <div className="cf-backdrop" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
            <aside className="cf-drawer" role="dialog" aria-modal="true" aria-labelledby="cf-drawer-h" ref={panel}>
                <header>
                    <h2 id="cf-drawer-h">More filters</h2>
                    <button type="button" className="cf-x" onClick={onClose} aria-label="Close filters"><i className="fa fa-times" aria-hidden="true"></i></button>
                </header>
                <div className="cf-drawer__body">{children}</div>
                <footer>
                    <button type="button" className="dm-clear" onClick={onClear}>Reset these</button>
                    <button type="button" className="dm-btn dm-btn--ink" onClick={onClose}>Show {count} {count === 1 ? 'course' : 'courses'}</button>
                </footer>
            </aside>
        </div>
    );
}

export default function Courses() {
    const { courses, genres = [], mediums = [], skills = [], type, filters = {} } = usePage().props;
    const all = (courses && courses.data) || [];
    const typeKey = String(type || 'All').toLowerCase();

    const [q, setQ] = useState(filters.q || '');
    const [fMedium, setFMedium] = useState(toSet(filters.medium));
    const [fGenre, setFGenre] = useState(toSet(filters.genre));
    const [fSkill, setFSkill] = useState(toSet(filters.skill));
    const ageGroups = useAgeGroups();
    const [fAge, setFAge] = useState(toSet(filters.age));
    const [fType, setFType] = useState(new Set(typeKey === 'course' || typeKey === 'workshop' ? [typeKey] : []));
    // the sort isn't sent to the server; it comes back from the address bar on a refresh or a shared link
    const [sort, setSort] = useState(() => (typeof window === 'undefined' ? DEFAULT_SORT : fromQuery(window.location.search).sort));
    const [drawer, setDrawer] = useState(false);

    // every filter change is mirrored into the address bar (no reload, no extra history entry)
    useEffect(() => {
        writeQuery({ q, medium: fMedium, genre: fGenre, skill: fSkill, age: fAge, type: fType, sort });
    }, [q, fMedium, fGenre, fSkill, fAge, fType, sort]);

    const toggle = (setter) => (value) => setter((prev) => {
        const next = new Set(prev);
        next.has(value) ? next.delete(value) : next.add(value);
        return next;
    });
    const onlyMedium = (id) => setFMedium(id ? new Set([String(id)]) : new Set());
    const genreIds = (c) => String(c.genre_id || '').split(',').map((s) => s.trim()).filter(Boolean);
    const mediumTree = useMemo(() => descendants(mediums), [mediums]);
    const genreTree = useMemo(() => descendants(genres), [genres]);
    const names = useMemo(() => {
        const m = {};
        genres.forEach((g) => { m[`g${g.id}`] = g.name; });
        skills.forEach((s) => { m[`s${s.id}`] = s.name; });
        mediums.forEach((x) => { m[`m${x.id}`] = x.name; });
        return m;
    }, [genres, skills, mediums]);

    /** Everything a course can be found by. */
    const haystack = (c) => [c.title, c.sub_title, c.introduction, c.medium && c.medium.name, c.teacher && c.teacher.name, c.age_group,
        c.skill && c.skill.name, ...genreIds(c).map((g) => names[`g${g}`])].filter(Boolean).join(' ');

    /** The filter test, with one filter optionally left out (for the counts and the "remove this" suggestions). */
    const passes = (c, f, skip) => {
        if (skip !== 'medium' && f.medium.size && ![...f.medium].some((m) => (mediumTree[m] || new Set([m])).has(String(c.medium_id)))) return false;
        if (skip !== 'skill' && f.skill.size && !f.skill.has(String(c.skill_id))) return false;
        if (skip !== 'genre' && f.genre.size && ![...f.genre].some((g) => genreIds(c).some((x) => (genreTree[g] || new Set([g])).has(x)))) return false;
        if (skip !== 'age' && f.age.size && ![...f.age].some((a) => ageMatches(c.age_group, a))) return false;
        if (skip !== 'type' && f.type.size && !f.type.has(isWorkshop(c) ? 'workshop' : 'course')) return false;
        if (skip !== 'q' && f.q.trim() && !fuzzyScore(haystack(c), f.q)) return false;
        return true;
    };
    const state = { q, medium: fMedium, genre: fGenre, skill: fSkill, age: fAge, type: fType };

    const results = useMemo(() => {
        let list = all.filter((c) => passes(c, state));
        const price = (c) => Number((c.prices && c.prices[0] && c.prices[0].price_inr) || 0);
        if (q.trim() && sort === DEFAULT_SORT) {
            // a search ranks by how well it matches, then by popularity
            list = list.map((c) => [c, fuzzyScore(haystack(c), q)]).sort((a, b) => b[1] - a[1] || Number(b[0].students || 0) - Number(a[0].students || 0)).map((x) => x[0]);
        } else if (sort === 'popular') list = [...list].sort((a, b) => Number(b.students || 0) - Number(a.students || 0));
        if (sort === 'rated') list = [...list].sort((a, b) => Number(b.positive || 0) - Number(a.positive || 0));
        if (sort === 'price-asc') list = [...list].sort((a, b) => price(a) - price(b));
        if (sort === 'price-desc') list = [...list].sort((a, b) => price(b) - price(a));
        if (sort === 'newest') list = [...list].sort((a, b) => Number(b.id) - Number(a.id));
        return list;
    }, [all, q, fMedium, fGenre, fSkill, fAge, fType, sort, names]);

    /** Counts next to each option: courses that would match if that option were picked (other filters kept). */
    const countFor = (key, fn) => all.reduce((acc, c) => {
        if (!passes(c, state, key)) return acc;
        [].concat(fn(c)).forEach((k) => { acc[k] = (acc[k] || 0) + 1; });
        return acc;
    }, {});
    const skillCounts = countFor('skill', (c) => String(c.skill_id));
    const typeCounts = countFor('type', (c) => (isWorkshop(c) ? 'workshop' : 'course'));
    const genreCounts = (() => {
        const direct = countFor('genre', genreIds);
        const out = {};
        Object.keys(genreTree).forEach((g) => { out[g] = all.filter((c) => passes(c, state, 'genre') && genreIds(c).some((x) => genreTree[g].has(x))).length; });
        return Object.keys(out).length ? out : direct;
    })();

    const drawerCount = fSkill.size + fGenre.size;
    const anyFilter = !!(q.trim() || fMedium.size || fGenre.size || fSkill.size || fAge.size || fType.size);
    const clearAll = () => { setQ(''); setFMedium(new Set()); setFGenre(new Set()); setFSkill(new Set()); setFAge(new Set()); setFType(new Set()); };
    const clearDrawer = () => { setFSkill(new Set()); setFGenre(new Set()); };

    // one chip per active value
    const chips = [];
    if (q.trim()) chips.push({ key: 'q', label: `“${q.trim()}”`, raw: q.trim(), kind: 'Search', remove: () => setQ(''), skip: 'q' });
    fMedium.forEach((m) => chips.push({ key: `m${m}`, label: names[`m${m}`] || m, kind: 'Medium', remove: () => toggle(setFMedium)(m), skip: 'medium' }));
    fAge.forEach((a) => chips.push({ key: `a${a}`, label: a, kind: 'Age', remove: () => toggle(setFAge)(a), skip: 'age' }));
    fType.forEach((t) => chips.push({ key: `t${t}`, label: t === 'workshop' ? 'Workshops' : 'Live courses', kind: 'Format', remove: () => toggle(setFType)(t), skip: 'type' }));
    fSkill.forEach((s) => chips.push({ key: `s${s}`, label: names[`s${s}`] || s, kind: 'Level', remove: () => toggle(setFSkill)(s), skip: 'skill' }));
    fGenre.forEach((g) => chips.push({ key: `g${g}`, label: names[`g${g}`] || g, kind: 'Subject', remove: () => toggle(setFGenre)(g), skip: 'genre' }));
    // zero results: what each single change would bring back
    const rescue = results.length === 0 ? chips.map((ch) => ({ ...ch, n: all.filter((c) => passes(c, state, ch.skip)).length })).filter((x) => x.n > 0).sort((a, b) => b.n - a.n).slice(0, 3) : [];

    const activeMedium = fMedium.size === 1 ? mediums.find((m) => fMedium.has(String(m.id))) : null;
    const heading = activeMedium ? `${activeMedium.name} courses` : fType.size === 1 ? (fType.has('workshop') ? 'Art workshops' : 'Live art courses') : 'Online art courses';
    const topMediums = mediums.filter((m) => !m.parent_id || !mediums.some((p) => String(p.id) === String(m.parent_id)));

    return (
        <SiteLayout title={heading}>
            <div className="ac-wide">
                <ul className="dm-crumbs">
                    <li><a href={route('welcome')}>Home</a></li>
                    <li><a href={route('courses') + '?type=all'}>Courses</a></li>
                    {activeMedium && <li>{activeMedium.name}</li>}
                </ul>
                <div className="dm-listhead" style={{ paddingTop: 8 }}>
                    <h1>{heading}</h1>
                    <p>Learn from a working artist with live classes, guided projects and personal feedback. Choose a medium, your level and a format that fits your week.</p>
                </div>
            </div>

            <nav className="dm-cats" style={{ borderTop: 0, marginTop: 20 }} aria-label="Mediums">
                <div className="ac-wide">
                    <ul>
                        <li><button type="button" className={!fMedium.size ? 'is-active' : ''} aria-pressed={!fMedium.size} onClick={() => onlyMedium(null)}>All</button></li>
                        {topMediums.map((m) => (
                            <li key={m.id}><button type="button" className={fMedium.size === 1 && fMedium.has(String(m.id)) ? 'is-active' : ''} aria-pressed={fMedium.has(String(m.id))} onClick={() => onlyMedium(m.id)}>{m.name}</button></li>
                        ))}
                    </ul>
                </div>
            </nav>

            <section className="ac-wide" style={{ paddingBottom: 72 }}>
                <div className="cf-formats" role="group" aria-label="Format">
                    {[{ value: '', label: 'All formats', icon: 'fa-th-large' }, { value: 'course', label: 'Live courses', icon: 'fa-video-camera' },
                        { value: 'workshop', label: 'Workshops', icon: 'fa-paint-brush' }].map((o) => {
                        const on = o.value ? fType.size === 1 && fType.has(o.value) : fType.size !== 1;
                        const n = o.value ? (typeCounts[o.value] || 0) : (typeCounts.course || 0) + (typeCounts.workshop || 0);
                        return (
                            <button key={o.value || 'all'} type="button" className={`cf-format${on ? ' is-on' : ''}`} aria-pressed={on}
                                    onClick={() => setFType(o.value ? new Set([o.value]) : new Set())}>
                                <i className={`fa ${o.icon}`} aria-hidden="true"></i> {o.label} <span className="cf-format__n">{n}</span>
                            </button>
                        );
                    })}
                </div>

                <div className="cf-bar">
                    <div className="dm-search cf-search">
                        <i className="fa fa-search" aria-hidden="true"></i>
                        <input type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search courses, techniques, teachers" aria-label="Search in results" />
                    </div>
                    {ageGroups.length > 0 && (
                        <div className="cf-pills" role="group" aria-label="Age group">
                            <span className="cf-pills__label" aria-hidden="true">Age</span>
                            {ageGroups.map((a) => (
                                <button key={a} type="button" className={`cf-pill${fAge.has(a) ? ' is-on' : ''}`} aria-pressed={fAge.has(a)} onClick={() => toggle(setFAge)(a)}>{a}</button>
                            ))}
                        </div>
                    )}
                    <button type="button" className={`cf-more${drawerCount ? ' is-set' : ''}`} onClick={() => setDrawer(true)} aria-haspopup="dialog">
                        <i className="fa fa-sliders" aria-hidden="true"></i> More filters{drawerCount ? <span className="cf-more__n">{drawerCount}</span> : null}
                    </button>
                    <div className="dm-count cf-count">
                        <span aria-live="polite" aria-atomic="true">{results.length} {results.length === 1 ? 'course' : 'courses'}</span>
                        <select value={sort} onChange={(e) => setSort(e.target.value)} aria-label="Sort by">
                            <option value="popular">{q.trim() ? 'Best match' : 'Most popular'}</option>
                            <option value="rated">Best rated</option>
                            <option value="newest">Newest</option>
                            <option value="price-asc">Price: low to high</option>
                            <option value="price-desc">Price: high to low</option>
                        </select>
                    </div>
                </div>

                {chips.length > 0 && (
                    <ul className="cf-chips" aria-label="Active filters">
                        {chips.map((c) => (
                            <li key={c.key}>
                                <button type="button" className="cf-chip" onClick={c.remove} aria-label={`Remove ${c.kind.toLowerCase()} filter ${c.label}`}>
                                    <span className="cf-chip__kind">{c.kind}</span> {c.label} <i className="fa fa-times" aria-hidden="true"></i>
                                </button>
                            </li>
                        ))}
                        <li><button type="button" className="dm-clear" onClick={clearAll}>Clear filters</button></li>
                    </ul>
                )}

                {results.length > 0 ? (
                    <div className="dm-grid">
                        {results.map((c, i) => <CourseCard key={c.id} course={c} index={i} />)}
                    </div>
                ) : (
                    <div className="ac-empty cf-empty" role="status">
                        <i className="fa fa-search cf-empty__icon" aria-hidden="true"></i>
                        <h3>No courses match your filters</h3>
                        {rescue.length > 0 ? (
                            <>
                                <p>One change brings courses back:</p>
                                <div className="cf-empty__fix">
                                    {rescue.map((r) => <button key={r.key} type="button" className="cf-chip" onClick={r.remove}>Remove {r.kind.toLowerCase()} “{r.raw || r.label}” · {r.n} {r.n === 1 ? 'course' : 'courses'}</button>)}
                                </div>
                            </>
                        ) : <p>Try a different word or another medium.</p>}
                        <button type="button" className="dm-btn dm-btn--ink" onClick={clearAll}>Clear filters</button>
                    </div>
                )}
            </section>

            <Drawer open={drawer} onClose={() => setDrawer(false)} count={results.length} onClear={clearDrawer}>
                <CheckGroup legend="Skill level" options={skills.map((s) => ({ value: s.id, label: s.name }))} selected={fSkill} onToggle={toggle(setFSkill)} counts={skillCounts} />
                <CheckGroup legend="Subject" options={tree(genres).map((g) => ({ value: g.id, label: g.name, depth: g.depth }))} selected={fGenre} onToggle={toggle(setFGenre)} counts={genreCounts} />
            </Drawer>
        </SiteLayout>
    );
}
