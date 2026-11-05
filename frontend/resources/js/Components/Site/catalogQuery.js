/*
 * The course catalogue's filters <-> the address bar (/courses?type=all&medium=2,5&age=Junior%20-%2012%20to%2015%20yrs&q=sky&sort=newest).
 * Several values of one filter are comma separated (ids, and age group names, which contain no commas). The server reads
 * the same parameters for the first render (CoursesController), so a copied link, a refresh or Back restores the view.
 */

export const SORTS = ['popular', 'rated', 'price-asc', 'price-desc', 'newest'];
export const DEFAULT_SORT = 'popular';

/** "2,5" / ["2","5"] / "" -> Set{"2","5"} */
export function toSet(v) {
    if (v === undefined || v === null || v === '') return new Set();
    const parts = Array.isArray(v) ? v : String(v).split(',');
    return new Set(parts.map((s) => String(s).trim()).filter(Boolean));
}

/** Course / workshop toggles -> the type parameter: exactly one picked = that one, otherwise all. */
export function typeParam(types) {
    if (types && types.size === 1) return types.has('workshop') ? 'workshop' : 'course';
    return 'all';
}

/**
 * The query string for a filter state, in a stable order and without empty values.
 * @param {{q?: string, medium?: Set<string>, genre?: Set<string>, skill?: Set<string>, age?: Set<string>, type?: Set<string>, sort?: string}} f
 */
export function toQuery(f) {
    const p = new URLSearchParams();
    p.set('type', typeParam(f.type));
    const q = (f.q || '').trim();
    if (q) p.set('q', q);
    for (const key of ['medium', 'genre', 'skill', 'age']) {
        const s = f[key];
        if (s && s.size) p.set(key, [...s].sort().join(','));
    }
    if (f.sort && f.sort !== DEFAULT_SORT && SORTS.includes(f.sort)) p.set('sort', f.sort);
    return p.toString();
}

/** The filter state written in a query string ("?type=all&age=..."); unknown values are ignored. */
export function fromQuery(search) {
    const p = new URLSearchParams(search || '');
    const type = (p.get('type') || '').toLowerCase();
    const sort = p.get('sort');
    return {
        q: p.get('q') || '',
        medium: toSet(p.get('medium')),
        genre: toSet(p.get('genre')),
        skill: toSet(p.get('skill')),
        age: toSet(p.get('age')),
        type: new Set(type === 'course' || type === 'workshop' ? [type] : []),
        sort: SORTS.includes(sort) ? sort : DEFAULT_SORT,
    };
}

/**
 * Writes the filter state into the address bar without a page load or a new history entry. Inertia keeps its page
 * object in history.state, so that state is passed on unchanged (Back / Forward keep working).
 */
export function writeQuery(f, win = typeof window === 'undefined' ? null : window) {
    if (!win) return;
    const qs = toQuery(f);
    const next = win.location.pathname + (qs ? '?' + qs : '') + (win.location.hash || '');
    const now = win.location.pathname + win.location.search + (win.location.hash || '');
    if (next !== now) win.history.replaceState(win.history.state, '', next);
}
