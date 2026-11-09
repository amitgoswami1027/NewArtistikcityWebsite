import React from 'react';
import { usePage } from '@inertiajs/inertia-react';
import SiteLayout from '@/Components/Site/SiteLayout';
import { InfoHero, RichBody } from '@/Components/Site/InfoParts';

const LINKS = [
    ['website', 'Website', 'fa-globe'],
    ['facebook', 'Facebook', 'fa-facebook'],
    ['instagram', 'Instagram', 'fa-instagram'],
    ['youtube', 'YouTube', 'fa-youtube-play'],
    ['linkedin', 'LinkedIn', 'fa-linkedin'],
];

/** /instructor: the people chosen in Admin > SEO & Layout Controls › Instructor (profiles from Admin > People). */
export default function Instructor() {
    const { page = {}, people = [] } = usePage().props;
    return (
        <SiteLayout title={page.heading}>
            <div className="ip" data-testid="info-instructor">
                <InfoHero eyebrow="Our teachers" title={page.heading} intro={page.intro} crumb="Instructor" />
                <div className="ac-wide ip-body">
                    {people.length === 0 && <p className="ip-empty">Instructor profiles will appear here soon. Meanwhile, <a href="/courses?type=all">browse the courses</a>.</p>}
                    {people.map((p) => {
                        const links = LINKS.filter(([k]) => p.links && p.links[k]);
                        return (
                            <article key={p.id} className="ip-person" data-testid={`instructor-${p.id}`}>
                                <div className="ip-person__photo">
                                    {p.photo ? <img src={p.photo} alt={p.name} loading="lazy" /> : <span aria-hidden="true">{(p.name || '?').slice(0, 1)}</span>}
                                </div>
                                <div className="ip-person__text">
                                    <h2>{p.name}</h2>
                                    {(p.title || p.designation) && <p className="ip-person__role">{[p.title, p.designation].filter(Boolean).join(' · ')}</p>}
                                    <RichBody html={p.bio} />
                                    {links.length > 0 && (
                                        <div className="ip-social" data-testid="instructor-links">
                                            {links.map(([k, label, icon]) => (
                                                <a key={k} href={p.links[k]} target="_blank" rel="noopener noreferrer" aria-label={`${p.name} on ${label}`} title={label}><i className={`fa ${icon}`} aria-hidden="true"></i></a>
                                            ))}
                                        </div>
                                    )}
                                </div>
                                {p.courses && p.courses.length > 0 && (
                                    <div className="ip-person__courses">
                                        <h3>Courses by {String(p.name || '').split(' ')[0]}</h3>
                                        <ul className="ip-minicourses">
                                            {p.courses.map((c) => (
                                                <li key={c.href}><a href={c.href}>
                                                    {c.photo ? <img src={c.photo} alt="" loading="lazy" /> : <span className="ip-minicourses__ph" aria-hidden="true"><i className="fa fa-paint-brush"></i></span>}
                                                    <span><b>{c.title}</b>{c.subtitle && <small>{c.subtitle}</small>}</span>
                                                </a></li>
                                            ))}
                                        </ul>
                                    </div>
                                )}
                            </article>
                        );
                    })}
                </div>
            </div>
        </SiteLayout>
    );
}
