import React, { useEffect, useRef, useState } from 'react';
import { Head, usePage } from '@inertiajs/inertia-react';
import ErrorBoundary from '@/Components/Admin/ErrorBoundary';

/*
 * Admin console shell: collapsible grouped sidebar, a sticky header with breadcrumbs and the
 * account menu (profile, password, view site, log out) in the top-right corner.
 * Every console page renders inside it: <AdminLayout title="Orders" crumbs={[...]}>.
 * Links are filtered by the signed-in admin's capabilities (props.admin.capabilities).
 */
export const NAV = [
    { group: 'Workspace', items: [
        { href: '/admin/dashboard', label: 'Overview', icon: 'fa-th-large', cap: 'overview', exact: true },
        { href: '/admin/dashboard/submissions', label: 'Verification desk', icon: 'fa-check-square-o', cap: 'submissions', badge: 'submissions' },
        { href: '/admin/tasks', label: 'Student tasks', icon: 'fa-comments-o', cap: 'submissions', badge: 'tasks' },
    ] },
    { group: 'Learning', items: [
        { href: '/admin/courses', label: 'Courses', icon: 'fa-graduation-cap', cap: 'catalog', also: ['/admin/courses/'] },
        { href: '/admin/workshop', label: 'Workshops', icon: 'fa-calendar', cap: 'catalog', also: ['/admin/workshop/'] },
        { href: '/admin/free-courses', label: 'Free lessons', icon: 'fa-gift', cap: 'settings', also: ['/admin/free-courses/'] },
        { href: '/admin/catalog', label: 'Mediums, genres & skills', icon: 'fa-sliders', cap: 'settings' },
    ] },
    { group: 'Sales', items: [
        { href: '/admin/orders', label: 'Course orders', icon: 'fa-shopping-cart', cap: 'revenue', also: ['/admin/orders/'] },
        { href: '/admin/dashboard/commissions', label: 'Commissions', icon: 'fa-paint-brush', cap: 'commissions', badge: 'commissions' },
        { href: '/admin/dashboard/marketplace', label: 'Marketplace', icon: 'fa-tags', cap: 'marketplace', badge: 'marketplace', also: ['/admin/dashboard/marketplace/'] },
    ] },
    { group: 'Community', items: [
        { href: '/admin/dashboard/people', label: 'People & roles', icon: 'fa-users', cap: 'people' },
        { href: '/admin/instructors', label: 'Instructor profiles', icon: 'fa-user', cap: 'people.manage', also: ['/admin/instructors/'] },
        { href: '/admin/dashboard/testimonials', label: 'Testimonials', icon: 'fa-quote-left', cap: 'testimonials' },
        { href: '/admin/dashboard/gallery', label: 'Gallery', icon: 'fa-picture-o', cap: 'gallery' },
    ] },
    { group: 'Content', items: [
        { href: '/admin/blog/post', label: 'Blog posts', icon: 'fa-pencil-square-o', cap: 'settings', also: ['/admin/blog/post/'] },
        { href: '/admin/blog/taxonomy', label: 'Blog categories & tags', icon: 'fa-bookmark-o', cap: 'settings' },
        { href: '/admin/faq', label: 'FAQ', icon: 'fa-question-circle-o', cap: 'settings' },
        { href: '/admin/newsletter', label: 'Newsletter', icon: 'fa-envelope-o', cap: 'settings', also: ['/admin/newsletter/'] },
        { href: '/admin/contacts', label: 'Contact messages', icon: 'fa-inbox', cap: 'settings', badge: 'contacts' },
    ] },
    { group: 'Setup', items: [
        { href: '/admin/settings', label: 'Site settings & SEO', icon: 'fa-cog', cap: 'settings' },
    ] },
];

const NAV_KEY = 'ac.admin.nav';
const readCollapsed = () => { try { return window.localStorage.getItem(NAV_KEY) === 'collapsed'; } catch (e) { return false; } };
const saveCollapsed = (v) => { try { window.localStorage.setItem(NAV_KEY, v ? 'collapsed' : 'open'); } catch (e) { /* private mode */ } };

const isActive = (item, path) => item.exact ? path === item.href
    : path === item.href || (item.also || []).some((p) => path.startsWith(p));

export function activeItem(path) {
    let found = null;
    NAV.forEach((g) => g.items.forEach((i) => { if (!found && isActive(i, path)) found = { ...i, group: g.group }; }));
    return found;
}

/* ---------------------------------------------------------------- toasts (shared) */

export function useToast() {
    const [toast, setToast] = useState(null);
    useEffect(() => {
        if (!toast) return undefined;
        const t = setTimeout(() => setToast(null), toast.type === 'error' ? 6000 : 3500);
        return () => clearTimeout(t);
    }, [toast]);
    const node = toast ? (
        <div key={toast.at} data-at={toast.at} role={toast.type === 'error' ? 'alert' : 'status'} className={`adm-toast ${toast.type === 'error' ? 'is-error' : ''}`}>
            <i className={`fa ${toast.type === 'error' ? 'fa-exclamation-circle' : 'fa-check-circle'}`} aria-hidden="true"></i>
            <span>{toast.text}</span>
            <button type="button" onClick={() => setToast(null)} aria-label="Dismiss"><i className="fa fa-times" aria-hidden="true"></i></button>
        </div>
    ) : null;
    return [node, (text, type = 'ok') => setToast({ text, type, at: Date.now() })];
}

export const errorText = (e, fallback = 'Something went wrong. Please try again.') => {
    if (e && e.response) {
        if (e.response.status === 419) return 'Your session expired. Reload the page and try again.';
        if (e.response.status === 413) return 'That file is too large.';
        if (e.response.data && e.response.data.message) return e.response.data.message;
        if (e.response.status === 403) return "You don't have access to this.";
        if (e.response.status >= 500) return 'The server had a problem. Please try again in a moment.';
    } else if (e && e.request) {
        return 'Could not reach the server. Check your connection.';
    }
    return fallback;
};

/* ------------------------------------------------------------- page header */

export function PageHeader({ title, subtitle, actions, eyebrow }) {
    return (
        <div className="adm-pagehead">
            <div className="adm-pagehead__text">
                {eyebrow && <span className="adm-eyebrow">{eyebrow}</span>}
                <h1>{title}</h1>
                {subtitle && <p>{subtitle}</p>}
            </div>
            {actions && <div className="adm-pagehead__actions">{actions}</div>}
        </div>
    );
}

/* ------------------------------------------------------------------ account */

function AccountMenu({ admin, token }) {
    const [open, setOpen] = useState(false);
    const ref = useRef(null);
    useEffect(() => {
        if (!open) return undefined;
        const close = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
        const esc = (e) => { if (e.key === 'Escape') setOpen(false); };
        document.addEventListener('mousedown', close);
        document.addEventListener('keydown', esc);
        return () => { document.removeEventListener('mousedown', close); document.removeEventListener('keydown', esc); };
    }, [open]);
    const initial = (admin.name || 'A').trim().charAt(0).toUpperCase();
    return (
        <div className="adm-account" ref={ref}>
            <button type="button" className="adm-account__btn" aria-haspopup="menu" aria-expanded={open} onClick={() => setOpen((o) => !o)}>
                {admin.photoUrl ? <img src={admin.photoUrl} alt="" className="adm-avatar" /> : <span className="adm-avatar" aria-hidden="true">{initial}</span>}
                <span className="adm-account__name"><b>{admin.name}</b><small>{admin.personaLabel}</small></span>
                <i className="fa fa-angle-down" aria-hidden="true"></i>
            </button>
            {open && (
                <div className="adm-menu" role="menu">
                    <div className="adm-menu__head"><b>{admin.name}</b><span>{admin.email}</span></div>
                    <a role="menuitem" href="/admin/profile"><i className="fa fa-user" aria-hidden="true"></i>Your profile</a>
                    <a role="menuitem" href="/admin/change-password"><i className="fa fa-lock" aria-hidden="true"></i>Change password</a>
                    <a role="menuitem" href="/" target="_blank" rel="noopener noreferrer"><i className="fa fa-external-link" aria-hidden="true"></i>View website</a>
                    <form method="post" action="/admin/logout" className="adm-menu__logout">
                        <input type="hidden" name="_token" value={token} />
                        <button type="submit" role="menuitem"><i className="fa fa-sign-out" aria-hidden="true"></i>Log out</button>
                    </form>
                </div>
            )}
        </div>
    );
}

/* ------------------------------------------------------------------- layout */

export default function AdminLayout({ title, crumbs, children, wide = false }) {
    const { admin = {}, badges = {} } = usePage().props;
    const caps = admin.capabilities || [];
    const [collapsed, setCollapsed] = useState(readCollapsed);
    const [drawer, setDrawer] = useState(false);
    const path = typeof window !== 'undefined' ? window.location.pathname.replace(/\/+$/, '') || '/' : '';
    const token = typeof document !== 'undefined' ? (document.head.querySelector('meta[name="csrf-token"]') || {}).content : '';
    const current = activeItem(path);

    useEffect(() => {
        if (!drawer) return undefined;
        const esc = (e) => { if (e.key === 'Escape') setDrawer(false); };
        document.addEventListener('keydown', esc);
        return () => document.removeEventListener('keydown', esc);
    }, [drawer]);

    const toggle = () => {
        if (window.matchMedia && window.matchMedia('(max-width: 1023px)').matches) { setDrawer((d) => !d); return; }
        setCollapsed((c) => { saveCollapsed(!c); return !c; });
    };

    const trail = crumbs || [
        ...(current && current.href !== '/admin/dashboard' ? [{ label: current.group }] : []),
        ...(current && current.href !== path ? [{ href: current.href, label: current.label }] : []),
        { label: title },
    ];

    const groups = NAV.map((g) => ({ ...g, items: g.items.filter((i) => caps.includes(i.cap)) })).filter((g) => g.items.length);

    return (
        <div className={`ac adm ${collapsed ? 'is-collapsed' : ''} ${drawer ? 'is-drawer' : ''}`}>
            <Head title={`${title} · Admin`} />
            <a href="#adm-main" className="adm-skip">Skip to content</a>
            <aside className="adm-side" aria-label="Admin navigation">
                <div className="adm-side__brand">
                    <a href="/admin/dashboard" className="adm-side__logo" title="Admin overview">
                        <img src="/assets/images/logo.png" alt="ArtistikCity" />
                    </a>
                    <span className="adm-side__tag">Admin</span>
                    <button type="button" className="adm-side__close" onClick={() => setDrawer(false)} aria-label="Close menu"><i className="fa fa-times" aria-hidden="true"></i></button>
                </div>
                <nav className="adm-side__nav">
                    {groups.map((g) => (
                        <div key={g.group} className="adm-side__group">
                            <p className="adm-side__label">{g.group}</p>
                            {g.items.map((i) => {
                                const on = isActive(i, path);
                                const n = i.badge ? Number(badges[i.badge]) || 0 : 0;
                                return (
                                    <a key={i.href} href={i.href} className={`adm-side__link ${on ? 'is-on' : ''}`} aria-current={on ? 'page' : undefined} title={collapsed ? i.label : undefined}>
                                        <i className={`fa ${i.icon}`} aria-hidden="true"></i>
                                        <span className="adm-side__text">{i.label}</span>
                                        {n > 0 && <span className="adm-side__badge" aria-label={`${n} waiting`}>{n > 99 ? '99+' : n}</span>}
                                    </a>
                                );
                            })}
                        </div>
                    ))}
                </nav>
                <button type="button" className="adm-side__collapse" onClick={toggle} aria-label={collapsed ? 'Expand menu' : 'Collapse menu'}>
                    <i className={`fa ${collapsed ? 'fa-angle-double-right' : 'fa-angle-double-left'}`} aria-hidden="true"></i>
                    <span className="adm-side__text">Collapse menu</span>
                </button>
            </aside>
            <div className="adm-scrim" onClick={() => setDrawer(false)} aria-hidden="true"></div>

            <div className="adm-body">
                <header className="adm-top">
                    <button type="button" className="adm-top__menu" onClick={toggle} aria-label="Toggle menu" aria-expanded={drawer || !collapsed}>
                        <i className="fa fa-bars" aria-hidden="true"></i>
                    </button>
                    <nav className="adm-crumbs" aria-label="Breadcrumb">
                        <ol>
                            <li><a href="/admin/dashboard"><i className="fa fa-home" aria-hidden="true"></i><span className="sr-only">Admin home</span></a></li>
                            {trail.map((c, i) => (
                                <li key={`${c.label}-${i}`} aria-current={i === trail.length - 1 ? 'page' : undefined}>
                                    {c.href && i < trail.length - 1 ? <a href={c.href}>{c.label}</a> : <span>{c.label}</span>}
                                </li>
                            ))}
                        </ol>
                    </nav>
                    <div className="adm-top__right">
                        {caps.includes('submissions') && (
                            <a href="/admin/dashboard/submissions" className="adm-iconbtn" title="Pending reviews" aria-label={`Pending reviews: ${Number(badges.submissions) || 0}`}>
                                <i className="fa fa-bell-o" aria-hidden="true"></i>
                                {Number(badges.submissions) > 0 && <span className="adm-iconbtn__dot">{badges.submissions}</span>}
                            </a>
                        )}
                        <AccountMenu admin={admin} token={token} />
                        <form method="post" action="/admin/logout" className="adm-top__logout">
                            <input type="hidden" name="_token" value={token} />
                            <button type="submit" className="adm-btn adm-btn--line adm-btn--sm"><i className="fa fa-sign-out" aria-hidden="true"></i><span>Log out</span></button>
                        </form>
                    </div>
                </header>
                <main id="adm-main" className={`adm-main ${wide ? 'is-wide' : ''}`}>
                    <div className="tw">
                        <ErrorBoundary>{children}</ErrorBoundary>
                    </div>
                </main>
            </div>
        </div>
    );
}
