import React from 'react';
import { usePage } from '@inertiajs/inertia-react';
import SiteHeader from '@/Components/Site/SiteHeader';
import SiteFooter from '@/Components/Site/SiteFooter';
import { StudioTabs } from '@/Components/Studio/StudioLayout';

/*
 * Shell for the classic student pages under /user (course home, modules, lessons, account, orders, help).
 * They now share the site header, the "My studio" tab bar and the footer with the rest of ArtistikCity,
 * so moving between the studio and a course never feels like leaving the website.
 */
const SECTIONS = [
    { test: /^\/user\/(my-courses|my-workshop|course|workshop|free-course)/, tab: '/user/my-courses', title: 'My courses', crumb: 'My courses',
        subtitle: 'Your live courses and workshops: schedules, modules, lessons and tasks.' },
    { test: /^\/user\/(my-account|my-address|change-password|order-history|notifications|linked-accounts|language)/, tab: '/user/my-account', title: 'Account', crumb: 'Account',
        subtitle: 'Your personal details, address, password, orders and notifications.' },
    { test: /^\/user\/help/, tab: '/user/my-account', title: 'Help', crumb: 'Help', subtitle: 'Answers to common questions and ways to reach the studio.' },
    { test: /.*/, tab: '/dashboard', title: 'My studio', crumb: null, subtitle: null },
];

export default function Authenticated({ children }) {
    const { url } = usePage();
    const path = String(url || '').split('?')[0];
    const sec = SECTIONS.find((s) => s.test.test(path));
    return (
        <div className="ac ac-student">
            <SiteHeader />
            <div className="ac-wide">
                <ul className="dm-crumbs">
                    <li><a href="/">Home</a></li>
                    <li><a href="/dashboard">My studio</a></li>
                    {sec.crumb && <li>{sec.crumb}</li>}
                </ul>
                <div className="dm-listhead" style={{ paddingTop: 8 }}>
                    <span className="ac-eyebrow">Student studio</span>
                    <h1>{sec.title}</h1>
                    {sec.subtitle && <p>{sec.subtitle}</p>}
                </div>
            </div>
            <div className="tw"><div className="ac-wide"><StudioTabs active={sec.tab} /></div></div>
            <main className="ac-wide ac-student__body">{children}</main>
            <SiteFooter />
        </div>
    );
}
