import React from 'react';
import { usePage } from '@inertiajs/inertia-react';
import SiteLayout from '@/Components/Site/SiteLayout';
import { InfoHero, RichBody, SmartLink, ContactBand, FaqItem } from '@/Components/Site/InfoParts';

/** /customer-support: help topics, top questions and contact, from Admin > SEO & Layout Controls › Customer support. */
export default function Support() {
    const { page = {}, faqs, contact } = usePage().props;
    const topics = page.topics || [];
    return (
        <SiteLayout title={page.heading}>
            <div className="ip" data-testid="info-support">
                <InfoHero eyebrow="Help centre" title={page.heading} intro={page.intro} crumb="Customer support">
                    <form className="ip-search" action="/faq" method="get" role="search">
                        <i className="fa fa-search" aria-hidden="true"></i>
                        <input type="search" name="q" placeholder="Search the FAQ" aria-label="Search the FAQ" />
                        <button type="submit" className="ip-btn ip-btn--brand">Search</button>
                    </form>
                    {page.hours && <p className="ip-hours"><i className="fa fa-clock-o" aria-hidden="true"></i> {page.hours}</p>}
                </InfoHero>
                <div className="ac-wide ip-body">
                    {topics.length > 0 && (
                        <section aria-labelledby="ip-topics">
                            <h2 id="ip-topics" className="ip-h2">How can we help?</h2>
                            <ul className="ip-cards ip-cards--3" data-testid="support-topics">
                                {topics.map((t, i) => (
                                    <li key={i} className="ip-card ip-card--link">
                                        <span className="ip-card__icon"><i className={`fa ${t.icon || 'fa-life-ring'}`} aria-hidden="true"></i></span>
                                        <h3>{t.title}</h3>
                                        {t.text && <p>{t.text}</p>}
                                        {t.href && <SmartLink href={t.href} className="ip-card__link">{t.label || 'Open'} <i className="fa fa-angle-right" aria-hidden="true"></i></SmartLink>}
                                    </li>
                                ))}
                            </ul>
                        </section>
                    )}
                    {faqs && faqs.length > 0 && (
                        <section aria-labelledby="ip-top-faq" className="ip-section">
                            <div className="ip-section__head">
                                <h2 id="ip-top-faq" className="ip-h2">Common questions</h2>
                                <a href="/faq" className="ip-card__link">All questions <i className="fa fa-angle-right" aria-hidden="true"></i></a>
                            </div>
                            <div className="ip-faqs">{faqs.map((q) => <FaqItem key={q.id} q={q} />)}</div>
                        </section>
                    )}
                    <RichBody html={page.body} className="ip-rich--narrow ip-section" />
                    {page.show_contact === '1' && <ContactBand contact={contact} />}
                </div>
            </div>
        </SiteLayout>
    );
}
