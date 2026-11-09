import React from 'react';
import { usePage } from '@inertiajs/inertia-react';
import SiteLayout from '@/Components/Site/SiteLayout';
import { InfoHero, RichBody, SmartLink } from '@/Components/Site/InfoParts';

/** /artistikcity-vision: text from Admin > SEO & Layout Controls › Our vision. */
export default function Vision() {
    const { page = {} } = usePage().props;
    const highlights = page.highlights || [];
    return (
        <SiteLayout title={page.heading}>
            <div className="ip" data-testid="info-vision">
                <InfoHero eyebrow="About ArtistikCity" title={page.heading} intro={page.intro} crumb="Our vision">
                    {page.cta_label && page.cta_link && (
                        <p className="ip-hero__cta"><SmartLink href={page.cta_link} className="ip-btn ip-btn--brand ip-btn--lg">{page.cta_label} <i className="fa fa-arrow-right" aria-hidden="true"></i></SmartLink></p>
                    )}
                </InfoHero>
                <div className="ac-wide ip-body">
                    {highlights.length > 0 && (
                        <ul className="ip-cards ip-cards--3" data-testid="vision-highlights">
                            {highlights.map((h, i) => (
                                <li key={i} className="ip-card">
                                    <span className="ip-card__icon"><i className={`fa ${h.icon || 'fa-star-o'}`} aria-hidden="true"></i></span>
                                    <h2>{h.title}</h2>
                                    {h.text && <p>{h.text}</p>}
                                    {h.href && <SmartLink href={h.href} className="ip-card__link">{h.label || 'Learn more'} <i className="fa fa-angle-right" aria-hidden="true"></i></SmartLink>}
                                </li>
                            ))}
                        </ul>
                    )}
                    <RichBody html={page.body} className="ip-rich--narrow" />
                    {page.cta_label && page.cta_link && (
                        <div className="ip-endcta">
                            <SmartLink href={page.cta_link} className="ip-btn ip-btn--brand ip-btn--lg">{page.cta_label}</SmartLink>
                            <a href="/how-it-works" className="ip-btn">How it works</a>
                        </div>
                    )}
                </div>
            </div>
        </SiteLayout>
    );
}
