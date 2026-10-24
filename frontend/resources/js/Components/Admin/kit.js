import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Inertia } from '@inertiajs/inertia';
import { usePage } from '@inertiajs/inertia-react';

/*
 * Admin UI kit: cards, buttons, badges, a server-driven data table (search, filters, sort,
 * pagination, loading skeleton), drawers and a confirm dialog. Styles are the adm-* rules in ac-theme.css.
 */

export const cx = (...a) => a.filter(Boolean).join(' ');

export function Card({ title, subtitle, actions, children, flush = false, className }) {
    return (
        <section className={cx('adm-card', flush && 'is-flush', className)}>
            {(title || actions) && (
                <header className="adm-card__head">
                    <div>
                        {title && <h2>{title}</h2>}
                        {subtitle && <p>{subtitle}</p>}
                    </div>
                    {actions && <div className="adm-card__actions">{actions}</div>}
                </header>
            )}
            {children}
        </section>
    );
}

export function Button({ as = 'button', variant = 'line', size, icon, children, busy, className, type, ...rest }) {
    const cls = cx('adm-btn', `adm-btn--${variant}`, size && `adm-btn--${size}`, className);
    const inner = (
        <>
            {busy ? <i className="fa fa-circle-o-notch fa-spin" aria-hidden="true"></i> : icon && <i className={`fa ${icon}`} aria-hidden="true"></i>}
            {children && <span>{children}</span>}
        </>
    );
    if (as === 'a') return <a className={cls} {...rest}>{inner}</a>;
    return <button type={type || 'button'} className={cls} disabled={busy || rest.disabled} {...rest}>{inner}</button>;
}

const TONES = { green: 'is-green', red: 'is-red', amber: 'is-amber', blue: 'is-blue', grey: 'is-grey', pink: 'is-pink' };
export function Badge({ tone = 'grey', children, dot }) {
    return <span className={cx('adm-badge', TONES[tone])}>{dot && <i className="adm-badge__dot" aria-hidden="true"></i>}{children}</span>;
}

export function EmptyState({ icon = 'fa-inbox', title, children, action }) {
    return (
        <div className="adm-empty">
            <i className={`fa ${icon}`} aria-hidden="true"></i>
            <h3>{title}</h3>
            {children && <p>{children}</p>}
            {action}
        </div>
    );
}

export function Stat({ label, value, icon, tone }) {
    return (
        <div className={cx('adm-stat', tone && `is-${tone}`)}>
            {icon && <i className={`fa ${icon}`} aria-hidden="true"></i>}
            <b>{value}</b>
            <span>{label}</span>
        </div>
    );
}

/* -------------------------------------------------------------- search box */

export function SearchBox({ value = '', onSearch, placeholder = 'Search', delay = 350, label }) {
    const [text, setText] = useState(value);
    const first = useRef(true);
    useEffect(() => { setText(value); }, [value]);
    useEffect(() => {
        if (first.current) { first.current = false; return undefined; }
        if (text === value) return undefined;
        const t = setTimeout(() => onSearch(text), delay);
        return () => clearTimeout(t);
    }, [text]); // eslint-disable-line react-hooks/exhaustive-deps
    return (
        <label className="adm-search">
            <i className="fa fa-search" aria-hidden="true"></i>
            <span className="sr-only">{label || placeholder}</span>
            <input type="search" value={text} placeholder={placeholder} onChange={(e) => setText(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); onSearch(text); } }} />
            {text && <button type="button" onClick={() => { setText(''); onSearch(''); }} aria-label="Clear search"><i className="fa fa-times" aria-hidden="true"></i></button>}
        </label>
    );
}

export function FilterSelect({ label, value, options, onChange, allLabel = 'All' }) {
    return (
        <label className="adm-filter">
            <span>{label}</span>
            <select value={value || ''} onChange={(e) => onChange(e.target.value)}>
                <option value="">{allLabel}</option>
                {options.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
            </select>
        </label>
    );
}

/** Pill tabs used as quick filters ("All · Paid · Pending …"). */
export function Segments({ value, options, onChange, label }) {
    return (
        <div className="adm-segments" role="tablist" aria-label={label}>
            {options.map((o) => (
                <button key={o.value} type="button" role="tab" aria-selected={String(value || '') === String(o.value)}
                    className={String(value || '') === String(o.value) ? 'is-on' : ''} onClick={() => onChange(o.value)}>
                    {o.label}{o.count !== undefined && <span>{o.count}</span>}
                </button>
            ))}
        </div>
    );
}

export function Toolbar({ children, end }) {
    return (
        <div className="adm-toolbar">
            <div className="adm-toolbar__main">{children}</div>
            {end && <div className="adm-toolbar__end">{end}</div>}
        </div>
    );
}

/* ----------------------------------------------------- server-driven grid */

/**
 * State of a server-side table whose page prop is {rows, meta}. Changing search, filters, sort or page
 * reloads only that prop (Inertia partial reload) and keeps the URL in sync so it can be bookmarked.
 */
export function useGrid(prop = 'grid', extraProps = []) {
    const page = usePage();
    const grid = page.props[prop] || { rows: [], meta: {} };
    const [loading, setLoading] = useState(false);
    const meta = grid.meta || {};
    const params = useCallback(() => {
        const p = {};
        if (meta.q) p.q = meta.q;
        if (meta.sort) p.sort = meta.sort;
        if (meta.dir) p.dir = meta.dir;
        if (meta.perPage && meta.perPage !== 20) p.per = meta.perPage;
        if (meta.page && meta.page > 1) p.page = meta.page;
        Object.entries(meta.filters || {}).forEach(([k, v]) => { if (v) p[k] = v; });
        return p;
    }, [meta]);
    const go = (change, { resetPage = true } = {}) => {
        const next = { ...params(), ...change };
        if (resetPage && !('page' in change)) delete next.page;
        Object.keys(next).forEach((k) => { if (next[k] === '' || next[k] === null || next[k] === undefined) delete next[k]; });
        Inertia.get(window.location.pathname, next, {
            preserveState: true, preserveScroll: true, replace: true, only: [prop, 'badges', ...extraProps],
            onStart: () => setLoading(true), onFinish: () => setLoading(false),
        });
    };
    const reload = () => Inertia.reload({ only: [prop, 'badges', ...extraProps], preserveScroll: true, onStart: () => setLoading(true), onFinish: () => setLoading(false) });
    return {
        rows: grid.rows || [], meta, loading, go, reload,
        filter: (name) => (meta.filters || {})[name] || '',
        search: (q) => go({ q }),
        sortBy: (key) => go({ sort: key, dir: meta.sort === key && meta.dir === 'asc' ? 'desc' : 'asc' }, { resetPage: false }),
        setPage: (n) => go({ page: n }, { resetPage: false }),
        setPer: (n) => go({ per: n }),
        exportUrl: (base) => { const p = params(); delete p.page; delete p.per; const qs = new URLSearchParams(p).toString(); return qs ? `${base}?${qs}` : base; },
    };
}

function SortHeader({ col, meta, onSort }) {
    if (!col.sort) return <span>{col.label}</span>;
    const on = meta.sort === col.sort;
    const dir = on ? meta.dir : null;
    return (
        <button type="button" className={cx('adm-th-sort', on && 'is-on')} onClick={() => onSort(col.sort)}
            aria-label={`Sort by ${col.label}${on ? (dir === 'asc' ? ', currently ascending' : ', currently descending') : ''}`}>
            {col.label}
            <i className={`fa ${!on ? 'fa-sort' : dir === 'asc' ? 'fa-sort-asc' : 'fa-sort-desc'}`} aria-hidden="true"></i>
        </button>
    );
}

/**
 * columns: [{key, label, sort?: 'serverKey', render?: (row) => node, className?, align?: 'right', hide?: 'md'|'lg'}]
 */
export function DataTable({ columns, rows, meta = {}, loading, onSort, rowKey = 'id', empty, onRowClick, caption, selected, onSelect }) {
    const selectable = !!onSelect;
    const allOn = selectable && rows.length > 0 && rows.every((r) => selected.includes(r[rowKey]));
    const skeleton = loading && rows.length === 0;
    return (
        <div className={cx('adm-table', loading && 'is-loading')} aria-busy={loading ? 'true' : 'false'}>
            <div className="adm-table__scroll">
                <table>
                    {caption && <caption className="sr-only">{caption}</caption>}
                    <thead>
                        <tr>
                            {selectable && (
                                <th className="adm-td-check">
                                    <input type="checkbox" checked={allOn} aria-label="Select all rows on this page"
                                        onChange={() => onSelect(allOn ? selected.filter((id) => !rows.some((r) => r[rowKey] === id)) : Array.from(new Set([...selected, ...rows.map((r) => r[rowKey])])))} />
                                </th>
                            )}
                            {columns.map((c) => (
                                <th key={c.key} scope="col" className={cx(c.align === 'right' && 'is-right', c.hide && `hide-${c.hide}`, c.className)}
                                    aria-sort={meta.sort && meta.sort === c.sort ? (meta.dir === 'asc' ? 'ascending' : 'descending') : undefined}>
                                    <SortHeader col={c} meta={meta} onSort={onSort} />
                                </th>
                            ))}
                        </tr>
                    </thead>
                    <tbody>
                        {skeleton && [0, 1, 2, 3, 4].map((i) => (
                            <tr key={`sk${i}`} className="adm-skel-row">
                                {selectable && <td></td>}
                                {columns.map((c) => <td key={c.key} className={cx(c.hide && `hide-${c.hide}`)}><span className="adm-skel"></span></td>)}
                            </tr>
                        ))}
                        {!skeleton && rows.map((r) => (
                            <tr key={r[rowKey]} className={cx(onRowClick && 'is-clickable', selectable && selected.includes(r[rowKey]) && 'is-selected')}
                                onClick={onRowClick ? (e) => { if (!e.target.closest('a,button,input,select,label')) onRowClick(r); } : undefined}>
                                {selectable && (
                                    <td className="adm-td-check">
                                        <input type="checkbox" checked={selected.includes(r[rowKey])} aria-label="Select row"
                                            onChange={() => onSelect(selected.includes(r[rowKey]) ? selected.filter((x) => x !== r[rowKey]) : [...selected, r[rowKey]])} />
                                    </td>
                                )}
                                {columns.map((c) => (
                                    <td key={c.key} className={cx(c.align === 'right' && 'is-right', c.hide && `hide-${c.hide}`, c.className)} data-label={c.label}>
                                        {c.render ? c.render(r) : (r[c.key] === null || r[c.key] === undefined || r[c.key] === '' ? <span className="adm-muted">—</span> : String(r[c.key]))}
                                    </td>
                                ))}
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
            {!skeleton && rows.length === 0 && (empty || <EmptyState title="Nothing here yet" />)}
        </div>
    );
}

export function Pagination({ meta = {}, onPage, onPer }) {
    const { page = 1, pages = 1, total = 0, perPage = 20 } = meta;
    if (!total) return null;
    const from = (page - 1) * perPage + 1;
    const to = Math.min(total, page * perPage);
    const nums = [];
    for (let i = Math.max(1, page - 2); i <= Math.min(pages, page + 2); i++) nums.push(i);
    return (
        <nav className="adm-pager" aria-label="Pagination">
            <span className="adm-pager__info">{from}–{to} of {total}</span>
            <div className="adm-pager__pages">
                <button type="button" onClick={() => onPage(page - 1)} disabled={page <= 1} aria-label="Previous page"><i className="fa fa-angle-left" aria-hidden="true"></i></button>
                {nums[0] > 1 && <><button type="button" onClick={() => onPage(1)}>1</button>{nums[0] > 2 && <span>…</span>}</>}
                {nums.map((n) => <button key={n} type="button" className={n === page ? 'is-on' : ''} aria-current={n === page ? 'page' : undefined} onClick={() => onPage(n)}>{n}</button>)}
                {nums[nums.length - 1] < pages && <>{nums[nums.length - 1] < pages - 1 && <span>…</span>}<button type="button" onClick={() => onPage(pages)}>{pages}</button></>}
                <button type="button" onClick={() => onPage(page + 1)} disabled={page >= pages} aria-label="Next page"><i className="fa fa-angle-right" aria-hidden="true"></i></button>
            </div>
            {onPer && (
                <label className="adm-pager__per">
                    <span>Rows</span>
                    <select value={perPage} onChange={(e) => onPer(Number(e.target.value))}>
                        {[10, 20, 50, 100].map((n) => <option key={n} value={n}>{n}</option>)}
                    </select>
                </label>
            )}
        </nav>
    );
}

/* ----------------------------------------------------------- overlays */

export function Drawer({ open, title, subtitle, onClose, children, footer, size = 'md' }) {
    const ref = useRef(null);
    useEffect(() => {
        if (!open) return undefined;
        const esc = (e) => { if (e.key === 'Escape') onClose(); };
        document.addEventListener('keydown', esc);
        document.body.classList.add('adm-noscroll');
        const t = setTimeout(() => { const el = ref.current && ref.current.querySelector('input:not([type=hidden]),textarea,select,button'); if (el) el.focus(); }, 30);
        return () => { document.removeEventListener('keydown', esc); document.body.classList.remove('adm-noscroll'); clearTimeout(t); };
    }, [open]); // eslint-disable-line react-hooks/exhaustive-deps
    if (!open) return null;
    return (
        <div className="adm-overlay" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
            <div className={cx('adm-drawer', `is-${size}`)} role="dialog" aria-modal="true" aria-label={title} ref={ref}>
                <header className="adm-drawer__head">
                    <div><h2>{title}</h2>{subtitle && <p>{subtitle}</p>}</div>
                    <button type="button" className="adm-iconbtn" onClick={onClose} aria-label="Close"><i className="fa fa-times" aria-hidden="true"></i></button>
                </header>
                <div className="adm-drawer__body">{children}</div>
                {footer && <footer className="adm-drawer__foot">{footer}</footer>}
            </div>
        </div>
    );
}

/** const [dialog, confirm] = useConfirm(); if (await confirm({title, body, confirmLabel, danger})) … */
export function useConfirm() {
    const [state, setState] = useState(null);
    const confirm = (opts) => new Promise((resolve) => setState({ ...opts, resolve }));
    const close = (v) => { if (state) state.resolve(v); setState(null); };
    const node = state ? (
        <div className="adm-overlay is-center" onMouseDown={(e) => { if (e.target === e.currentTarget) close(false); }}>
            <div className="adm-dialog" role="alertdialog" aria-modal="true" aria-labelledby="adm-dialog-title"
                onKeyDown={(e) => { if (e.key === 'Escape') close(false); }}>
                <h2 id="adm-dialog-title">{state.title}</h2>
                {state.body && <p>{state.body}</p>}
                <div className="adm-dialog__actions">
                    <Button onClick={() => close(false)}>{state.cancelLabel || 'Cancel'}</Button>
                    <Button variant={state.danger ? 'danger' : 'dark'} onClick={() => close(true)} autoFocus>{state.confirmLabel || 'Confirm'}</Button>
                </div>
            </div>
        </div>
    ) : null;
    return [node, confirm];
}

/* ------------------------------------------------------------- formatting */

export const fmtDate = (s, withTime = false) => {
    if (!s) return '—';
    const d = new Date(String(s).replace(' ', 'T'));
    if (Number.isNaN(d.getTime())) return String(s);
    return d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric', ...(withTime ? { hour: '2-digit', minute: '2-digit' } : {}) });
};

export const money = (amount, currency) => {
    const n = Number(amount);
    if (amount === null || amount === undefined || amount === '' || Number.isNaN(n)) return '—';
    const cur = String(currency || 'inr').toUpperCase() === 'USD' ? 'USD' : 'INR';
    return n.toLocaleString(cur === 'USD' ? 'en-US' : 'en-IN', { style: 'currency', currency: cur, maximumFractionDigits: n % 1 ? 2 : 0 });
};

export const plural = (n, one, many) => `${n} ${Number(n) === 1 ? one : (many || `${one}s`)}`;
