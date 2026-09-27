import React, { useEffect, useState } from 'react';
import { Head, usePage } from '@inertiajs/inertia-react';

const NAV = [
    { section: 'Workspace' },
    { href: '/admin/dashboard', label: 'Overview', icon: 'fa-th-large', cap: 'overview' },
    { href: '/admin/dashboard/submissions', label: 'Verification desk', icon: 'fa-check-square-o', cap: 'submissions', badge: 'submissions' },
    { href: '/admin/dashboard/commissions', label: 'Commissions', icon: 'fa-paint-brush', cap: 'commissions', badge: 'commissions' },
    { href: '/admin/dashboard/marketplace', label: 'Marketplace', icon: 'fa-tags', cap: 'marketplace', badge: 'marketplace' },
    { href: '/admin/dashboard/people', label: 'People & roles', icon: 'fa-users', cap: 'people' },
    { section: 'Showcase' },
    { href: '/admin/dashboard/gallery', label: 'Gallery', icon: 'fa-picture-o', cap: 'gallery' },
    { href: '/admin/dashboard/testimonials', label: 'Testimonials', icon: 'fa-quote-left', cap: 'testimonials' },
    { href: '/admin/blog/post', label: 'Blog posts', icon: 'fa-pencil-square-o', cap: 'settings', classic: true },
    { section: 'Catalog' },
    { href: '/admin/courses', label: 'Courses', icon: 'fa-graduation-cap', cap: 'catalog', classic: true },
    { href: '/admin/workshop', label: 'Workshops', icon: 'fa-calendar', cap: 'catalog', classic: true },
    { href: '/admin/free-courses', label: 'Free lessons', icon: 'fa-gift', cap: 'settings', classic: true },
    { href: '/admin/mediums', label: 'Mediums, genres & skills', icon: 'fa-sliders', cap: 'settings', classic: true },
    { section: 'Business' },
    { href: '/admin/orders', label: 'Course orders', icon: 'fa-shopping-cart', cap: 'revenue', classic: true },
    { href: '/admin/newsletter', label: 'Newsletter', icon: 'fa-envelope-o', cap: 'settings', classic: true },
    { href: '/admin/settings', label: 'Settings', icon: 'fa-cog', cap: 'settings', classic: true },
];

export function useToast() {
    const [toast, setToast] = useState(null);
    useEffect(() => { if (!toast) return undefined; const t = setTimeout(() => setToast(null), 3500); return () => clearTimeout(t); }, [toast]);
    const node = toast ? (
        <div role="status" className={`fixed bottom-6 right-6 z-50 rounded-xl px-5 py-3 shadow-xl text-sm font-bold ${toast.type === 'error' ? 'bg-red-600 text-white' : 'bg-ink text-white'}`}>
            <i className={`fa ${toast.type === 'error' ? 'fa-exclamation-circle' : 'fa-check-circle'} mr-2`} aria-hidden="true"></i>{toast.text}
        </div>
    ) : null;
    return [node, (text, type = 'ok') => setToast({ text, type })];
}

export const errorText = (e, fallback = 'Something went wrong.') => (e && e.response && e.response.data && e.response.data.message) || fallback;

export function PageHeader({ title, subtitle, actions }) {
    return (
        <div className="flex flex-wrap items-end justify-between gap-4 mb-8">
            <div>
                <h1 className="text-3xl sm:text-4xl font-black tracking-tight text-ink">{title}</h1>
                {subtitle && <p className="mt-1 text-base text-gray-600 max-w-2xl">{subtitle}</p>}
            </div>
            {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
        </div>
    );
}

export default function AdminLayout({ title, children }) {
    const { admin = {}, badges = {} } = usePage().props;
    const caps = admin.capabilities || [];
    const [open, setOpen] = useState(false);
    const here = typeof window !== 'undefined' ? window.location.pathname : '';
    const token = typeof document !== 'undefined' ? (document.head.querySelector('meta[name="csrf-token"]') || {}).content : '';
    // keep only links the persona may use, and drop section titles that end up empty
    const items = [];
    NAV.forEach((n) => {
        if (n.section) { items.push(n); return; }
        if (caps.includes(n.cap)) items.push(n);
    });
    const visible = items.filter((n, i) => !n.section || (items[i + 1] && !items[i + 1].section));

    const sidebar = (
        <nav className="flex flex-col h-full" aria-label="Admin">
            <a href="/admin/dashboard" className="flex items-center gap-3 px-6 h-16 border-b border-gray-200">
                <img src="/assets/images/logo.png" alt="ArtistikCity" className="h-8 w-auto" />
                <span className="font-mono text-xs uppercase tracking-widest text-gray-500">Console</span>
            </a>
            <div className="flex-1 overflow-y-auto py-4">
                {visible.map((n, i) => n.section ? (
                    <p key={`s${i}`} className="px-6 pt-5 pb-2 font-mono text-xs uppercase tracking-widest text-gray-400">{n.section}</p>
                ) : (
                    <a key={n.href} href={n.href} aria-current={here === n.href ? 'page' : undefined}
                       className={`mx-3 flex items-center gap-3 rounded-lg px-3 py-2.5 text-base font-semibold ${here === n.href ? 'bg-ink text-white' : 'text-gray-700 hover:bg-gray-100'}`}>
                        <i className={`fa ${n.icon} w-5 text-center`} aria-hidden="true"></i>
                        <span className="flex-1">{n.label}</span>
                        {n.badge && Number(badges[n.badge]) > 0 && <span className="rounded-full bg-brand text-white text-xs font-bold px-2 py-0.5">{badges[n.badge]}</span>}
                        {n.classic && <i className="fa fa-external-link text-xs opacity-40" aria-hidden="true" title="Classic editor"></i>}
                    </a>
                ))}
            </div>
            <div className="border-t border-gray-200 p-4">
                <div className="flex items-center gap-3">
                    <span className="w-10 h-10 rounded-full bg-brand text-white font-black flex items-center justify-center">{(admin.name || 'A').charAt(0)}</span>
                    <div className="flex-1 min-w-0">
                        <p className="font-bold text-ink truncate">{admin.name}</p>
                        <p className="font-mono text-xs uppercase tracking-widest text-brand">{admin.personaLabel}</p>
                    </div>
                </div>
                <div className="mt-3 grid grid-cols-3 gap-2 text-sm">
                    <a href="/admin/profile" className="text-center rounded-lg border border-gray-200 py-2 font-semibold hover:border-ink">Profile</a>
                    <a href="/" className="text-center rounded-lg border border-gray-200 py-2 font-semibold hover:border-ink">Site</a>
                    <form method="post" action="/admin/logout"><input type="hidden" name="_token" value={token} /><button className="w-full rounded-lg border border-gray-200 py-2 font-semibold hover:border-ink">Log out</button></form>
                </div>
            </div>
        </nav>
    );

    return (
        <div className="ac">
            <Head title={`${title} · Admin`} />
            <div className="tw min-h-screen bg-gray-50">
                <aside className="hidden lg:block fixed inset-y-0 left-0 w-72 bg-white border-r border-gray-200 z-30">{sidebar}</aside>
                {open && (
                    <div className="lg:hidden fixed inset-0 z-40 flex">
                        <div className="w-72 bg-white shadow-xl">{sidebar}</div>
                        <button type="button" className="flex-1 bg-black bg-opacity-40" aria-label="Close menu" onClick={() => setOpen(false)}></button>
                    </div>
                )}
                <div className="lg:pl-72">
                    <header className="sticky top-0 z-20 h-16 bg-white border-b border-gray-200 flex items-center gap-4 px-4 sm:px-8">
                        <button type="button" className="lg:hidden w-10 h-10 rounded-lg border border-gray-200" aria-label="Open menu" onClick={() => setOpen(true)}><i className="fa fa-bars" aria-hidden="true"></i></button>
                        <p className="font-mono text-xs uppercase tracking-widest text-gray-500 truncate">ArtistikCity admin · {title}</p>
                        <div className="ml-auto flex items-center gap-2">
                            {caps.includes('submissions') && <a href="/admin/dashboard/submissions" className="relative w-10 h-10 rounded-full border border-gray-200 flex items-center justify-center hover:border-ink" title="Pending reviews">
                                <i className="fa fa-bell-o" aria-hidden="true"></i>
                                {Number(badges.submissions) > 0 && <span className="absolute -top-1 -right-1 rounded-full bg-brand text-white text-xs font-bold px-1.5">{badges.submissions}</span>}
                            </a>}
                        </div>
                    </header>
                    <main className="px-4 sm:px-8 py-8 max-w-[1500px]">{children}</main>
                </div>
            </div>
        </div>
    );
}
