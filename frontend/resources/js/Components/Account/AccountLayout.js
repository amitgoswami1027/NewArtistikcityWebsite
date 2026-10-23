import React from 'react';
import { usePage } from '@inertiajs/inertia-react';
import SiteHeader from '@/Components/Site/SiteHeader';
import SiteFooter from '@/Components/Site/SiteFooter';

import { accountNav, activeAccountItem } from '@/Components/Account/nav';

export { ACCOUNT_PATHS } from '@/Components/Account/nav';

export default function AccountLayout({ children }) {
    const page = usePage();
    const path = String(page.url || '').split('?')[0];
    const user = page.props.auth && page.props.auth.user;
    const items = accountNav(page.props.nav);
    const current = activeAccountItem(path, page.props.nav);
    const pageTitle = path.startsWith('/user/change-password') ? 'Change password' : path.startsWith('/user/language') ? 'Language' : current.title;
    const initials = user && user.name ? user.name.trim().charAt(0).toUpperCase() : 'A';
    const photo = user && user.profile_photo ? `/storage/uploads/students/${user.id}/${user.profile_photo}` : null;

    return (
        <div className="ac ac-student ac-account">
            <SiteHeader />
            <div className="ac-wide">
                <ul className="dm-crumbs">
                    <li><a href="/">Home</a></li>
                    <li><a href="/user/my-account">My account</a></li>
                    <li>{pageTitle}</li>
                </ul>
                <div className="dm-listhead" style={{ paddingTop: 8 }}>
                    <span className="ac-eyebrow">My account</span>
                    <h1>{pageTitle}</h1>
                    {current.subtitle && <p>{current.subtitle}</p>}
                </div>
            </div>
            <main className="ac-wide ac-student__body">
                <div className="acc-grid">
                    <aside className="ac-sidenav acc-nav" aria-label="My account">
                        {user && (
                            <div className="acc-who">
                                {photo ? <img src={photo} alt="" className="acc-who__img" onError={(e) => { e.currentTarget.style.display = 'none'; }} />
                                    : <span className="ac-avatar" aria-hidden="true">{initials}</span>}
                                <span className="acc-who__text">
                                    <b>{user.name}</b>
                                    {(user.email || user.phone) && <small>{user.email || user.phone}</small>}
                                    {user.slug && <a href={`/student/${user.slug}`}>View public profile</a>}
                                </span>
                            </div>
                        )}
                        <nav>
                            {items.map((i) => (
                                <a key={i.href} href={i.href} className={i.href === current.href ? 'is-active' : ''} aria-current={i.href === current.href ? 'page' : undefined}>
                                    <i className={`fa ${i.icon}`} aria-hidden="true"></i>{i.label}
                                    {i.count > 0 && <span className="acc-count">{i.count}</span>}
                                </a>
                            ))}
                        </nav>
                        <a href="/dashboard" className="acc-studio"><i className="fa fa-th-large" aria-hidden="true"></i>Go to My studio</a>
                    </aside>
                    <div className="acc-body">{children}</div>
                </div>
            </main>
            <SiteFooter />
        </div>
    );
}
