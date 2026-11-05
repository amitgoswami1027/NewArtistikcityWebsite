import React from 'react';
import { usePage, InertiaLink } from '@inertiajs/inertia-react';

const LINKS = [
    { href: '/dashboard', label: 'My studio', icon: 'fa-th-large', match: /^\/(dashboard|user\/dashboard)$/ },
    { href: '/user/my-courses', label: 'My courses', icon: 'fa-graduation-cap', match: /^\/user\/(my-courses|course)/ },
    { href: '/user/my-workshop', label: 'My workshops', icon: 'fa-calendar', match: /^\/user\/(my-workshop|workshop)/ },
    { href: '/dashboard/certificates', label: 'Certificates', icon: 'fa-certificate', match: /^$/ },
    { href: '/user/my-account', label: 'Account', icon: 'fa-user', match: /^\/user\/(my-account|my-address|change-password|order-history|notifications)/ },
    { href: '/user/help', label: 'Help', icon: 'fa-question-circle-o', match: /^\/user\/help/ },
];

/** Left navigation for the classic student pages, in the site's look. */
export default function StudentNavigation() {
    const { url } = usePage();
    const path = String(url || '').split('?')[0];
    return (
        <aside className="col-md-3 col-lg-2 px-0 ac-sidenav" aria-label="My courses and account">
            <nav>
                {LINKS.map((l) => (
                    <a key={l.href} href={l.href} className={l.match.test(path) ? 'is-active' : ''} aria-current={l.match.test(path) ? 'page' : undefined}>
                        <i className={`fa ${l.icon}`} aria-hidden="true"></i>{l.label}
                    </a>
                ))}
                <InertiaLink href={route('logout')} method="post" as="button" className="ac-sidenav__logout">
                    <i className="fa fa-sign-out" aria-hidden="true"></i>Log out
                </InertiaLink>
            </nav>
        </aside>
    );
}
