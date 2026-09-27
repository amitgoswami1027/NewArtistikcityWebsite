import React from 'react';
import SiteLayout from '@/Components/Site/SiteLayout';
import { JOURNEY } from '@/Components/Studio/Journey';

const TABS = [
    { href: '/dashboard', label: 'My studio', icon: 'fa-th-large' },
    { href: '/dashboard/submissions', label: 'Submissions', icon: 'fa-upload' },
    { href: '/dashboard/portfolio', label: 'Portfolio & shop', icon: 'fa-picture-o' },
    { href: '/user/my-account', label: 'Account', icon: 'fa-user-o' },
];

/** The student's lifecycle progress, styled like the commission wizard stepper. */
export function JourneyStepper({ stage = 4 }) {
    return (
        <ol className="flex flex-wrap items-center gap-x-2 gap-y-3" aria-label="Your creative journey">
            {JOURNEY.map((s, i) => {
                const n = i + 1;
                const done = n < stage;
                const active = n === stage;
                return (
                    <li key={s.key} className="flex items-center gap-2">
                        <a href={s.href} aria-current={active ? 'step' : undefined}
                           className={`flex items-center justify-center w-8 h-8 rounded-full text-sm font-bold ${active ? 'bg-brand text-white' : done ? 'bg-ink text-white' : 'bg-white border border-gray-300 text-gray-500'}`}>
                            {done ? <i className="fa fa-check" aria-hidden="true"></i> : n}
                        </a>
                        <span className={`text-base ${active ? 'font-bold text-ink' : 'text-gray-500'}`}>{s.title}</span>
                        {n < JOURNEY.length && <span className="hidden sm:block w-6 h-px bg-gray-300 mx-1" aria-hidden="true"></span>}
                    </li>
                );
            })}
        </ol>
    );
}

export default function StudioLayout({ title, subtitle, active, stage, actions, children }) {
    return (
        <SiteLayout title={title}>
            <div className="ac-wide">
                <ul className="dm-crumbs">
                    <li><a href="/">Home</a></li>
                    <li><a href="/dashboard">My studio</a></li>
                    {title !== 'My studio' && <li>{title}</li>}
                </ul>
                <div className="dm-listhead" style={{ paddingTop: 8, display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', gap: 16, flexWrap: 'wrap' }}>
                    <div>
                        <span className="ac-eyebrow">Student studio</span>
                        <h1>{title}</h1>
                        {subtitle && <p>{subtitle}</p>}
                    </div>
                    {actions}
                </div>
            </div>
            <div className="tw">
                <div className="ac-wide">
                    <nav className="mt-6 flex gap-2 overflow-x-auto border-b border-gray-200" aria-label="Studio">
                        {TABS.map((t) => (
                            <a key={t.href} href={t.href} aria-current={active === t.href ? 'page' : undefined}
                               className={`whitespace-nowrap px-4 py-3 text-base font-bold border-b-2 -mb-px ${active === t.href ? 'border-ink text-ink' : 'border-transparent text-gray-500 hover:text-ink'}`}>
                                <i className={`fa ${t.icon} mr-2`} aria-hidden="true"></i>{t.label}
                            </a>
                        ))}
                    </nav>
                    {stage && <div className="py-5 border-b border-gray-100"><JourneyStepper stage={stage} /></div>}
                    <div className="py-10">{children}</div>
                </div>
            </div>
        </SiteLayout>
    );
}
