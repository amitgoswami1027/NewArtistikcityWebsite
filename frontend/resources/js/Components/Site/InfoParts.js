import React from 'react';
import { isExternal } from '@/Components/Site/siteContent';

/*
 * Shared parts of the information pages (Pages/Info/*: Our vision, Instructor, Customer support, FAQ). Their texts come
 * from Admin > System parameters > SEO & Layout Controls (admin/SitePages); styles are the .ip-* rules in ac-theme.css.
 */

export function InfoHero({ eyebrow, title, intro, crumb, children }) {
    return (
        <section className="ip-hero">
            <div className="ac-wide">
                <nav className="ip-crumbs" aria-label="Breadcrumb"><a href="/">Home</a><i className="fa fa-angle-right" aria-hidden="true"></i><span aria-current="page">{crumb || title}</span></nav>
                {eyebrow && <p className="ip-eyebrow">{eyebrow}</p>}
                <h1 className="ip-title">{title}</h1>
                {intro && <p className="ip-intro">{intro}</p>}
                {children}
            </div>
        </section>
    );
}

/** A link that opens outside sites in a new tab. */
export function SmartLink({ href, className, children, ...rest }) {
    const ext = isExternal(href);
    return <a href={href} className={className} {...(ext ? { target: '_blank', rel: 'noopener noreferrer' } : {})} {...rest}>{children}</a>;
}

/** Rich text saved through HtmlCleaner on the server. */
export function RichBody({ html, className = '' }) {
    if (!html) return null;
    return <div className={`ip-rich ${className}`} dangerouslySetInnerHTML={{ __html: html }} />;
}

/** "Still need help?" band with the contact details from SEO & Layout Controls › Contact. */
export function ContactBand({ contact, title = 'Still need help?', text = 'Send us a message and we will get back to you.' }) {
    const c = contact || {};
    return (
        <section className="ip-contact" data-testid="info-contact">
            <div>
                <h2>{title}</h2>
                <p>{text}</p>
            </div>
            <div className="ip-contact__ways">
                {c.email && <a href={`mailto:${c.email}`} className="ip-contact__way"><i className="fa fa-envelope-o" aria-hidden="true"></i><span><small>E-mail</small>{c.email}</span></a>}
                {c.phone && <a href={`tel:${c.phoneLink || c.phone}`} className="ip-contact__way"><i className="fa fa-phone" aria-hidden="true"></i><span><small>Phone</small>{c.phone}</span></a>}
                <a href="/contact" className="ip-btn ip-btn--brand"><i className="fa fa-paper-plane-o" aria-hidden="true"></i> Contact us</a>
            </div>
        </section>
    );
}

/** One question, opened and closed with the native details element (works without script, searchable by the browser). */
export function FaqItem({ q, open = false }) {
    return (
        <details className="ip-faq" open={open} id={`faq-${q.id}`}>
            <summary><span>{q.question}</span><i className="fa fa-plus" aria-hidden="true"></i></summary>
            <div className="ip-rich ip-faq__a" dangerouslySetInnerHTML={{ __html: q.answer }} />
        </details>
    );
}
