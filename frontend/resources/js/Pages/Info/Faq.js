import React, { useEffect, useMemo, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import SiteLayout from '@/Components/Site/SiteLayout';
import { InfoHero, ContactBand, FaqItem } from '@/Components/Site/InfoParts';

const plain = (html) => String(html || '').replace(/<[^>]+>/g, ' ');
const slug = (s) => String(s || 'general').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');

/** /faq: questions from Admin > SEO & Layout Controls › FAQ, grouped by category; ?q= filters (the help centre's search). */
export default function Faq() {
    const { page = {}, groups = [], contact } = usePage().props;
    const [q, setQ] = useState(() => { try { return new URLSearchParams(window.location.search).get('q') || ''; } catch (e) { return ''; } });
    useEffect(() => {
        try {
            const u = new URL(window.location.href);
            if (q) u.searchParams.set('q', q); else u.searchParams.delete('q');
            window.history.replaceState(window.history.state, '', u.pathname + u.search + u.hash);
        } catch (e) { /* ignore */ }
    }, [q]);
    const shown = useMemo(() => {
        const words = q.toLowerCase().split(/\s+/).filter(Boolean);
        if (!words.length) return groups;
        return groups.map((g) => ({ ...g, items: g.items.filter((it) => {
            const hay = `${it.question} ${plain(it.answer)} ${g.category || ''}`.toLowerCase();
            return words.every((w) => hay.includes(w));
        }) })).filter((g) => g.items.length);
    }, [q, groups]);
    const count = shown.reduce((n, g) => n + g.items.length, 0);
    const named = groups.filter((g) => g.category);
    return (
        <SiteLayout title={page.heading}>
            <div className="ip" data-testid="info-faq">
                <InfoHero eyebrow="Help centre" title={page.heading} intro={page.intro} crumb="FAQ">
                    {page.show_search === '1' && groups.length > 0 && (
                        <div className="ip-search" role="search">
                            <i className="fa fa-search" aria-hidden="true"></i>
                            <input type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search questions" aria-label="Search questions" data-testid="faq-search" />
                        </div>
                    )}
                </InfoHero>
                <div className={`ac-wide ip-body ip-faqpage ${named.length > 1 && !q ? 'has-nav' : ''}`}>
                    {named.length > 1 && !q && (
                        <nav className="ip-faqnav" aria-label="Topics">
                            {named.map((g) => <button type="button" key={g.category} onClick={() => { const el = document.getElementById(`cat-${slug(g.category)}`); if (el) window.scrollTo({ top: el.getBoundingClientRect().top + window.scrollY - 96, behavior: 'smooth' }); }}>{g.category}</button>)}
                        </nav>
                    )}
                    <div>
                        {groups.length === 0 && <p className="ip-empty">Questions and answers will appear here soon. Meanwhile, <a href="/contact">contact us</a> with any question.</p>}
                        {groups.length > 0 && count === 0 && <p className="ip-empty" data-testid="faq-none">No question matches “{q}”. <button type="button" className="ip-linkbtn" onClick={() => setQ('')}>Show all questions</button> or <a href="/contact">ask us directly</a>.</p>}
                        {shown.map((g) => (
                            <section key={g.category || '_'} id={`cat-${slug(g.category)}`} className="ip-faqgroup">
                                {g.category && <h2 className="ip-h2">{g.category}</h2>}
                                <div className="ip-faqs">{g.items.map((it) => <FaqItem key={it.id} q={it} open={!!q && count <= 3} />)}</div>
                            </section>
                        ))}
                    </div>
                    {page.show_contact === '1' && <ContactBand contact={contact} title="Didn't find your answer?" />}
                </div>
            </div>
        </SiteLayout>
    );
}
