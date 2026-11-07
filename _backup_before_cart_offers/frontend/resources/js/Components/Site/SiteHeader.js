import React, { useEffect, useRef, useState } from 'react';
import { usePage, InertiaLink } from '@inertiajs/inertia-react';
import axios from 'axios';
import { API_URL } from '@/Components/Constants';
import { accountNav } from '@/Components/Account/nav';
import { visibleMenu, isCurrent, isExternal } from '@/Components/Site/siteContent';

// fallback only: the real list is managed in Admin > Catalog > Age groups and arrives as the shared prop site.ageGroups
export const AGE_GROUPS = ['Sub-junior - 8 to 12 yrs', 'Junior - 12 to 15 yrs', 'Adult - 16 yrs and above'];

/** The age groups for menus and filters, in the order set in the admin. */
export function useAgeGroups() {
    const { site } = usePage().props;
    return site && Array.isArray(site.ageGroups) ? site.ageGroups : AGE_GROUPS;
}

export function catalogUrl(params = {}) {
    const qs = new URLSearchParams(Object.assign({ type: 'all' }, params)).toString();
    return route('courses') + '?' + qs;
}

export function loginUrl() {
    const here = typeof window !== 'undefined' ? window.location.pathname + window.location.search : '/';
    return route('login') + (here && here !== '/' && !here.startsWith('/login') && !here.startsWith('/join') ? '?return_to=' + encodeURIComponent(here) : '');
}

function MenuLinks({ links, here, onPick }) {
    return links.map((l) => (
        <a key={l.href} href={l.href} role="menuitem" onClick={onPick} aria-current={here === l.href ? 'page' : undefined}>
            <i className={`fa ${l.icon}`} aria-hidden="true"></i>
            <span>{l.label}</span>
            {l.count > 0 && <span className="dm-menu__count">{l.count}</span>}
        </a>
    ));
}

/** Small dropdown used by the account side of the header. Closes on outside click and Escape. */
function HeaderMenu({ id, button, buttonClass, label, align = 'left', children }) {
    const [open, setOpen] = useState(false);
    const ref = useRef(null);
    useEffect(() => {
        if (!open) return undefined;
        const onDoc = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
        const onKey = (e) => { if (e.key === 'Escape') setOpen(false); };
        document.addEventListener('mousedown', onDoc);
        window.addEventListener('keydown', onKey);
        return () => { document.removeEventListener('mousedown', onDoc); window.removeEventListener('keydown', onKey); };
    }, [open]);
    return (
        <div className="dm-menuwrap" ref={ref}>
            <button type="button" className={`${buttonClass || ''} ${open ? 'is-open' : ''}`} aria-haspopup="menu" aria-expanded={open}
                    aria-controls={id} aria-label={label} onClick={() => setOpen(!open)}>
                {button}
            </button>
            {open && (
                <div id={id} className={`dm-menu dm-menu--${align}`} role="menu">
                    {typeof children === 'function' ? children(() => setOpen(false)) : children}
                </div>
            )}
        </div>
    );
}

export default function SiteHeader({ showCategories = false, activeMedium = null }) {
    const { auth, nav, site } = usePage().props;
    const user = auth && auth.user;
    const [mediums, setMediums] = useState([]);
    const [drawer, setDrawer] = useState(false);
    const [query, setQuery] = useState('');

    useEffect(() => {
        axios.get(`${API_URL}/get_mediums`)
            .then((r) => setMediums(Array.isArray(r.data) ? r.data : []))
            .catch(() => setMediums([]));
        if (typeof window !== 'undefined') {
            const q = new URLSearchParams(window.location.search).get('q');
            if (q) setQuery(q);
        }
    }, []);

    useEffect(() => {
        const onKey = (e) => { if (e.key === 'Escape') setDrawer(false); };
        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    }, []);

    const search = (e) => { e.preventDefault(); window.location.href = catalogUrl({ q: query }); };
    const here = typeof window !== 'undefined' ? window.location.pathname : '';
    const initials = user && user.name ? user.name.trim().charAt(0).toUpperCase() : 'A';
    const account = user ? accountNav(nav) : [];
    const menu = visibleMenu(site);

    return (
        <>
            <header className="dm-header">
                <div className="ac-wide dm-header__row">
                    <a href={route('welcome')} className="ac-logo" aria-label="ArtistikCity home">
                        <img src="/assets/images/logo.png" alt="ArtistikCity" />
                    </a>

                    <nav className="dm-links dm-links--main" aria-label="Main">
                        {menu.map((m) => (
                            <a key={`${m.label}-${m.href}`} href={m.href} className={m.accent ? 'is-accent' : undefined}
                                aria-current={isCurrent(m.href, here) ? 'page' : undefined}
                                {...(isExternal(m.href) ? { target: '_blank', rel: 'noopener noreferrer' } : {})}>{m.label}</a>
                        ))}
                    </nav>

                    <form className="dm-search" onSubmit={search} role="search">
                        <i className="fa fa-search"></i>
                        <input type="search" value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search courses…" aria-label="Search courses" />
                    </form>

                    <nav className="dm-links dm-links--account" aria-label="Account">
                        {user ? (
                            <>
                                <a href="/dashboard" aria-current={/^\/(dashboard|user\/(my-courses|course|workshop))/.test(here) ? 'page' : undefined}>My studio</a>
                                <InertiaLink as="button" method="post" href={route('logout')} className="dm-btn dm-btn--line">Log out</InertiaLink>
                                <HeaderMenu id="menu-account" align="right" buttonClass="ac-avatar dm-avatarbtn" label={`Account menu for ${user.name}`}
                                            button={initials}>
                                    {(close) => (
                                        <>
                                            <div className="dm-menu__head">
                                                <span className="ac-avatar" aria-hidden="true">{initials}</span>
                                                <span>
                                                    <b>{user.name}</b>
                                                    {(user.email || user.phone) && <small>{user.email || user.phone}</small>}
                                                </span>
                                            </div>
                                            <MenuLinks links={account} here={here} onPick={close} />
                                        </>
                                    )}
                                </HeaderMenu>
                            </>
                        ) : (
                            <>
                                <a href={loginUrl()}>Log in</a>
                                <a href={route('join')} className="dm-btn dm-btn--brand" style={{ marginLeft: 6 }}>Join for free</a>
                            </>
                        )}
                    </nav>

                    <button type="button" className="ac-burger" aria-label="Menu" onClick={() => setDrawer(!drawer)}>
                        <i className={`fa ${drawer ? 'fa-times' : 'fa-bars'}`}></i>
                    </button>
                </div>
            </header>

            {showCategories && mediums.length > 0 && (
                <nav className="dm-cats" aria-label="Categories">
                    <div className="ac-wide">
                        <ul>
                            <li><a href={catalogUrl({})} className={!activeMedium ? 'is-active' : ''}>All courses</a></li>
                            {mediums.map((m) => (
                                <li key={m.id}><a href={catalogUrl({ medium: m.id })} className={String(activeMedium) === String(m.id) ? 'is-active' : ''}>{m.name}</a></li>
                            ))}
                            <li><a href={catalogUrl({ type: 'workshop' })}>Workshops</a></li>
                        </ul>
                    </div>
                </nav>
            )}

            {drawer && (
                <div className="ac-drawer" style={{ top: 68 }}>
                    <form className="dm-hero__search" onSubmit={search} style={{ border: '1px solid var(--dm-line)' }}>
                        <input type="search" value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search courses" />
                        <button className="dm-btn dm-btn--ink" type="submit" aria-label="Search"><i className="fa fa-search" aria-hidden="true"></i></button>
                    </form>
                    {menu.map((m, i) => (
                        <a key={`${m.label}-${m.href}`} href={m.href} style={i < 2 ? { fontWeight: 700 } : undefined}
                            {...(isExternal(m.href) ? { target: '_blank', rel: 'noopener noreferrer' } : {})}>{m.label}</a>
                    ))}
                    {user ? (
                        <>
                            <a href="/dashboard" className="dm-btn dm-btn--line dm-btn--block" style={{ marginTop: 16 }}>My studio</a>
                            <h6>My account</h6>
                            {account.map((l) => <a key={l.href} href={l.href}>{l.label}{l.count > 0 ? ` (${l.count})` : ''}</a>)}
                            <InertiaLink as="button" method="post" href={route('logout')} className="dm-btn dm-btn--ink dm-btn--block" style={{ marginTop: 16 }}>Log out</InertiaLink>
                        </>
                    ) : (
                        <>
                            <a href={route('join')} className="dm-btn dm-btn--brand dm-btn--block">Join for free</a>
                            <a href={loginUrl()} className="dm-btn dm-btn--line dm-btn--block" style={{ marginTop: 10 }}>Log in</a>
                        </>
                    )}
                </div>
            )}
        </>
    );
}
