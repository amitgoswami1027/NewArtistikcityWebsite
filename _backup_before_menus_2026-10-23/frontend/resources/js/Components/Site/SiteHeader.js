import React, { useEffect, useRef, useState } from 'react';
import { usePage, InertiaLink } from '@inertiajs/inertia-react';
import axios from 'axios';
import { API_URL } from '@/Components/Constants';

export const AGE_GROUPS = ['Sub-junior - 8 to 12 yrs', 'Junior - 12 to 15 yrs', 'Adult - 16 yrs and above'];

export function catalogUrl(params = {}) {
    const qs = new URLSearchParams(Object.assign({ type: 'all' }, params)).toString();
    return route('courses') + '?' + qs;
}

export function loginUrl() {
    const here = typeof window !== 'undefined' ? window.location.pathname + window.location.search : '/';
    return route('login') + (here && here !== '/' && !here.startsWith('/login') && !here.startsWith('/join') ? '?return_to=' + encodeURIComponent(here) : '');
}

export default function SiteHeader({ showCategories = false, activeMedium = null }) {
    const { auth } = usePage().props;
    const user = auth && auth.user;
    const [mediums, setMediums] = useState([]);
    const [open, setOpen] = useState(false);
    const [drawer, setDrawer] = useState(false);
    const [query, setQuery] = useState('');
    const exploreRef = useRef(null);

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
        const onDoc = (e) => { if (exploreRef.current && !exploreRef.current.contains(e.target)) setOpen(false); };
        const onKey = (e) => { if (e.key === 'Escape') { setOpen(false); setDrawer(false); } };
        document.addEventListener('mousedown', onDoc);
        window.addEventListener('keydown', onKey);
        return () => { document.removeEventListener('mousedown', onDoc); window.removeEventListener('keydown', onKey); };
    }, []);

    const search = (e) => { e.preventDefault(); window.location.href = catalogUrl({ q: query }); };
    const here = typeof window !== 'undefined' ? window.location.pathname : '';
    const initials = user && user.name ? user.name.trim().charAt(0).toUpperCase() : 'A';

    return (
        <>
            <header className="dm-header">
                <div className="ac-wide dm-header__row">
                    <a href={route('welcome')} className="ac-logo" aria-label="ArtistikCity home">
                        <img src="/assets/images/logo.png" alt="ArtistikCity" />
                    </a>

                    <nav className="dm-links dm-links--main" aria-label="Main">
                        <a href={route('how.it.works')} aria-current={here.startsWith('/how-it-works') ? 'page' : undefined}>How it works</a>
                        <div className="dm-explore" ref={exploreRef}>
                            <button type="button" className={open ? 'is-open' : ''} aria-expanded={open} onClick={() => setOpen(!open)}>
                                Courses <i className={`fa fa-angle-${open ? 'up' : 'down'}`}></i>
                            </button>
                            {open && (
                                <div className="dm-dropdown">
                                    <div>
                                        <h6>By medium</h6>
                                        {mediums.map((m) => (
                                            <a key={m.id} href={catalogUrl({ medium: m.id })}>
                                                <span className="ac-mega__thumb" style={{ backgroundImage: `url(/storage/uploads/mediums/${m.id}/${m.photo})` }}></span>
                                                {m.name}
                                            </a>
                                        ))}
                                    </div>
                                    <div>
                                        <h6>Format</h6>
                                        <a href={catalogUrl({ type: 'course' })}><i className="fa fa-graduation-cap"></i> Live courses</a>
                                        <a href={catalogUrl({ type: 'workshop' })}><i className="fa fa-paint-brush"></i> Workshops</a>
                                        <h6 style={{ marginTop: 16 }}>By age</h6>
                                        {AGE_GROUPS.map((a) => <a key={a} href={catalogUrl({ age: a })}><i className="fa fa-user-o"></i> {a}</a>)}
                                    </div>
                                    <div className="dm-all"><a href={catalogUrl({})}>Browse all courses <i className="fa fa-arrow-right"></i></a></div>
                                </div>
                            )}
                        </div>

                        <a href={route('commission.step1')} aria-current={here.startsWith('/commission') ? 'page' : undefined}>Commission art</a>
                        <a href="/marketplace" aria-current={here.startsWith('/marketplace') ? 'page' : undefined}>Marketplace</a>
                        <a href={route('studio.stories')} className="is-accent" aria-current={here.startsWith('/studio-stories') ? 'page' : undefined}>Studio Stories</a>
                    </nav>

                    <form className="dm-search" onSubmit={search} role="search">
                        <i className="fa fa-search"></i>
                        <input type="search" value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search courses…" aria-label="Search courses" />
                    </form>

                    <nav className="dm-links dm-links--account" aria-label="Account">
                        {user ? (
                            <>
                                <a href="/dashboard">My studio</a>
                                <InertiaLink as="button" method="post" href={route('logout')} className="dm-btn dm-btn--line">Log out</InertiaLink>
                                <a href={route('user.account')} className="ac-avatar" style={{ marginLeft: 8 }} title={user.name}>{initials}</a>
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
                        <button className="dm-btn dm-btn--ink" type="submit"><i className="fa fa-search"></i></button>
                    </form>
                    <a href={route('how.it.works')} style={{ fontWeight: 700 }}>How it works</a>
                    <h6>Courses</h6>
                    <a href={catalogUrl({})}>All courses</a>
                    {mediums.map((m) => <a key={m.id} href={catalogUrl({ medium: m.id })}>{m.name}</a>)}
                    <a href={catalogUrl({ type: 'workshop' })}>Workshops</a>
                    <h6>ArtistikCity</h6>
                    <a href={route('commission.step1')}>Commission art</a>
                    <a href="/marketplace">Marketplace</a>
                    <a href="/marketplace?source=student">Student originals</a>
                    <a href={route('studio.stories')}>Studio Stories</a>
                    {user ? (
                        <>
                            <a href="/dashboard" className="dm-btn dm-btn--line dm-btn--block">My studio</a>
                            <InertiaLink as="button" method="post" href={route('logout')} className="dm-btn dm-btn--ink dm-btn--block" style={{ marginTop: 10 }}>Log out</InertiaLink>
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
