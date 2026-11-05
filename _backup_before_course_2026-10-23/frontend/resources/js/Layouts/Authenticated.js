import React from 'react';
import { usePage } from '@inertiajs/inertia-react';
import SiteHeader from '@/Components/Site/SiteHeader';
import SiteFooter from '@/Components/Site/SiteFooter';
import { StudioTabs } from '@/Components/Studio/StudioLayout';
import AccountLayout, { ACCOUNT_PATHS } from '@/Components/Account/AccountLayout';

/*
 * Shell for the classic student pages under /user.
 *  - Account pages (profile, orders, wish list, addresses, security, notifications, help) use the separate
 *    My account area (Components/Account/AccountLayout).
 *  - Course pages (course home, modules, lessons, tasks, free courses) live inside My studio, under the
 *    "My courses" tab: Home > My studio > My courses > <course>.
 */
function courseTitle(props) {
    return (props.CourseNav && props.CourseNav.course_title)
        || (props.lessonNav && props.lessonNav.course_title)
        || (props.CourseDetails && props.CourseDetails.coursesData && props.CourseDetails.coursesData.title)
        || (props.CourseModule && props.CourseModule.course && props.CourseModule.course.title)
        || null;
}

const STUDIO = [
    { test: /^\/user\/(course|workshop)/, title: 'Course', crumbs: [{ href: '/user/my-courses', label: 'My courses' }],
        subtitle: 'Schedule, modules, lessons and tasks.' },
    { test: /^\/user\/(free-course|free-courses)/, title: 'Free courses', crumbs: [{ href: '/user/my-courses', label: 'My courses' }],
        subtitle: 'Free lessons to get you started.' },
    { test: /.*/, title: 'My courses', crumbs: [], subtitle: null },
];

export default function Authenticated({ children }) {
    const page = usePage();
    const path = String(page.url || '').split('?')[0];
    if (ACCOUNT_PATHS.test(path)) {
        return <AccountLayout>{children}</AccountLayout>;
    }
    const sec = STUDIO.find((s) => s.test.test(path));
    const title = (sec.title === 'Course' && courseTitle(page.props)) || sec.title;
    return (
        <div className="ac ac-student">
            <SiteHeader />
            <div className="ac-wide">
                <ul className="dm-crumbs">
                    <li><a href="/">Home</a></li>
                    <li><a href="/dashboard">My studio</a></li>
                    {sec.crumbs.map((c) => <li key={c.href}><a href={c.href}>{c.label}</a></li>)}
                    <li>{title}</li>
                </ul>
                <div className="dm-listhead" style={{ paddingTop: 8 }}>
                    <span className="ac-eyebrow">Student studio</span>
                    <h1>{title}</h1>
                    {sec.subtitle && <p>{sec.subtitle}</p>}
                </div>
            </div>
            <div className="tw"><div className="ac-wide"><StudioTabs active="/user/my-courses" /></div></div>
            <main className="ac-wide ac-student__body">{children}</main>
            <SiteFooter />
        </div>
    );
}
