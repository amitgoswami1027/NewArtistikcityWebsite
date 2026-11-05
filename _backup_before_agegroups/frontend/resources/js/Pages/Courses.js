import React, { useEffect, useMemo, useRef, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import SiteLayout from '@/Components/Site/SiteLayout';
import CourseCard from '@/Components/Site/CourseCard';
import { isWorkshop } from '@/Components/Site/ProgramCard';
import { AGE_GROUPS } from '@/Components/Site/SiteHeader';

const toSet = (v) => new Set(v === undefined || v === null || v === '' ? [] : [String(v)]);

function ageMatches(courseAge, filterAge) {
    if (!courseAge) return false;
    const a = String(courseAge).toLowerCase();
    const f = filterAge.toLowerCase();
    return a.includes(f) || f.includes(a) || a.includes(f.split(' - ')[0]);
}

function Pill({ label, options, selected, onToggle, counts }) {
    const [open, setOpen] = useState(false);
    const ref = useRef(null);
    useEffect(() => {
        const close = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
        document.addEventListener('mousedown', close);
        return () => document.removeEventListener('mousedown', close);
    }, []);
    if (!options.length) return null;
    const n = selected.size;
    return (
        <div className="dm-pill" ref={ref}>
            <button type="button" className={n ? 'is-set' : ''} onClick={() => setOpen(!open)} aria-expanded={open}>
                {label}{n ? ` (${n})` : ''} <i className={`fa fa-angle-${open ? 'up' : 'down'}`}></i>
            </button>
            {open && (
                <div className="dm-pill__menu">
                    {options.map((o) => (
                        <label key={o.value}>
                            <input type="checkbox" checked={selected.has(String(o.value))} onChange={() => onToggle(String(o.value))} />
                            {o.label}
                            {counts && <small>{counts[String(o.value)] || 0}</small>}
                        </label>
                    ))}
                </div>
            )}
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
    const [fAge, setFAge] = useState(toSet(filters.age));
    const [fType, setFType] = useState(new Set(typeKey === 'course' || typeKey === 'workshop' ? [typeKey] : []));
    const [sort, setSort] = useState('popular');

    const toggle = (setter) => (value) => setter((prev) => {
        const next = new Set(prev);
        next.has(value) ? next.delete(value) : next.add(value);
        return next;
    });
    const onlyMedium = (id) => setFMedium(id ? new Set([String(id)]) : new Set());
    const genreIds = (c) => String(c.genre_id || '').split(',').map((s) => s.trim()).filter(Boolean);

    const results = useMemo(() => {
        const needle = q.trim().toLowerCase();
        let list = all.filter((c) => {
            if (needle && !(`${c.title} ${c.sub_title || ''} ${c.introduction || ''} ${(c.medium && c.medium.name) || ''}`.toLowerCase().includes(needle))) return false;
            if (fMedium.size && !fMedium.has(String(c.medium_id))) return false;
            if (fSkill.size && !fSkill.has(String(c.skill_id))) return false;
            if (fGenre.size && !genreIds(c).some((g) => fGenre.has(g))) return false;
            if (fAge.size && ![...fAge].some((a) => ageMatches(c.age_group, a))) return false;
            if (fType.size && !fType.has(isWorkshop(c) ? 'workshop' : 'course')) return false;
            return true;
        });
        const price = (c) => Number((c.prices && c.prices[0] && c.prices[0].price_inr) || 0);
        if (sort === 'popular') list = [...list].sort((a, b) => Number(b.students || 0) - Number(a.students || 0));
        if (sort === 'rated') list = [...list].sort((a, b) => Number(b.positive || 0) - Number(a.positive || 0));
        if (sort === 'price-asc') list = [...list].sort((a, b) => price(a) - price(b));
        if (sort === 'price-desc') list = [...list].sort((a, b) => price(b) - price(a));
        if (sort === 'newest') list = [...list].sort((a, b) => Number(b.id) - Number(a.id));
        return list;
    }, [all, q, fMedium, fGenre, fSkill, fAge, fType, sort]);

    const countBy = (fn) => all.reduce((acc, c) => { [].concat(fn(c)).forEach((k) => { acc[k] = (acc[k] || 0) + 1; }); return acc; }, {});
    const anyFilter = q || fMedium.size || fGenre.size || fSkill.size || fAge.size || fType.size;
    const clearAll = () => { setQ(''); setFMedium(new Set()); setFGenre(new Set()); setFSkill(new Set()); setFAge(new Set()); setFType(new Set()); };

    const activeMedium = fMedium.size === 1 ? mediums.find((m) => fMedium.has(String(m.id))) : null;
    const heading = activeMedium ? `${activeMedium.name} courses` : fType.size === 1 ? (fType.has('workshop') ? 'Art workshops' : 'Live art courses') : 'Online art courses';

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
                        <li><button type="button" className={!fMedium.size ? 'is-active' : ''} onClick={() => onlyMedium(null)}>All</button></li>
                        {mediums.map((m) => (
                            <li key={m.id}><button type="button" className={fMedium.size === 1 && fMedium.has(String(m.id)) ? 'is-active' : ''} onClick={() => onlyMedium(m.id)}>{m.name}</button></li>
                        ))}
                    </ul>
                </div>
            </nav>

            <section className="ac-wide" style={{ paddingBottom: 72 }}>
                <div className="dm-filterbar">
                    <div className="dm-search" style={{ display: 'block', maxWidth: 280, flex: '0 1 280px' }}>
                        <i className="fa fa-search"></i>
                        <input type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search in results" aria-label="Search in results" />
                    </div>
                    <Pill label="Format" selected={fType} onToggle={toggle(setFType)}
                          options={[{ value: 'course', label: 'Live courses' }, { value: 'workshop', label: 'Workshops' }]} />
                    <Pill label="Level" selected={fSkill} onToggle={toggle(setFSkill)} counts={countBy((c) => String(c.skill_id))}
                          options={skills.map((s) => ({ value: s.id, label: s.name }))} />
                    <Pill label="Age" selected={fAge} onToggle={toggle(setFAge)} options={AGE_GROUPS.map((a) => ({ value: a, label: a }))} />
                    <Pill label="Subject" selected={fGenre} onToggle={toggle(setFGenre)} counts={countBy(genreIds)}
                          options={genres.map((g) => ({ value: g.id, label: g.name }))} />
                    {anyFilter ? <button type="button" className="dm-clear" onClick={clearAll}>Clear filters</button> : null}
                    <div className="dm-count">
                        {results.length} {results.length === 1 ? 'course' : 'courses'}
                        <select value={sort} onChange={(e) => setSort(e.target.value)} aria-label="Sort by">
                            <option value="popular">Most popular</option>
                            <option value="rated">Best rated</option>
                            <option value="newest">Newest</option>
                            <option value="price-asc">Price: low to high</option>
                            <option value="price-desc">Price: high to low</option>
                        </select>
                    </div>
                </div>

                {results.length > 0 ? (
                    <div className="dm-grid">
                        {results.map((c, i) => <CourseCard key={c.id} course={c} index={i} />)}
                    </div>
                ) : (
                    <div className="ac-empty">
                        <h3>No courses match your filters</h3>
                        <p>Try removing a filter or searching for a different technique.</p>
                        <button type="button" className="dm-btn dm-btn--ink" onClick={clearAll}>Clear filters</button>
                    </div>
                )}
            </section>
        </SiteLayout>
    );
}
