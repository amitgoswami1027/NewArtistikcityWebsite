import React, { useEffect, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import SiteLayout from '@/Components/Site/SiteLayout';
import { Byline, StoryTile } from '@/Pages/StudioStories';

/*
 * One Studio Story (/studio-stories/{slug}): reading progress, topic, headline, byline, cover, the story (rich text saved
 * through HtmlCleaner), tags, sharing, author box, newer/older story and three related stories. Admins can open drafts here
 * (a "Preview" bar says so); visitors only see published stories.
 */

function Progress() {
    const [p, setP] = useState(0);
    useEffect(() => {
        const on = () => {
            const el = document.getElementById('sty-article');
            if (!el) return;
            const r = el.getBoundingClientRect();
            const total = r.height - window.innerHeight * 0.6;
            setP(Math.max(0, Math.min(1, total > 0 ? -r.top / total : 1)));
        };
        on();
        window.addEventListener('scroll', on, { passive: true });
        window.addEventListener('resize', on);
        return () => { window.removeEventListener('scroll', on); window.removeEventListener('resize', on); };
    }, []);
    return <div className="sty-progress" aria-hidden="true"><span style={{ transform: `scaleX(${p})` }}></span></div>;
}

function Share({ url, title }) {
    const [copied, setCopied] = useState(false);
    const u = encodeURIComponent(url);
    const t = encodeURIComponent(title);
    const links = [
        ['WhatsApp', 'fa-whatsapp', `https://wa.me/?text=${t}%20${u}`],
        ['Facebook', 'fa-facebook', `https://www.facebook.com/sharer/sharer.php?u=${u}`],
        ['X', 'fa-twitter', `https://twitter.com/intent/tweet?text=${t}&url=${u}`],
        ['LinkedIn', 'fa-linkedin', `https://www.linkedin.com/sharing/share-offsite/?url=${u}`],
    ];
    const copy = () => {
        try {
            navigator.clipboard.writeText(url).then(() => { setCopied(true); setTimeout(() => setCopied(false), 2000); });
        } catch (e) { /* no clipboard */ }
    };
    return (
        <div className="sty-share" aria-label="Share this story">
            <span>Share</span>
            {links.map(([label, icon, href]) => (
                <a key={label} href={href} target="_blank" rel="noopener noreferrer" aria-label={`Share on ${label}`} title={label}><i className={`fa ${icon}`} aria-hidden="true"></i></a>
            ))}
            <button type="button" onClick={copy} aria-label="Copy the link"><i className={`fa ${copied ? 'fa-check' : 'fa-link'}`} aria-hidden="true"></i></button>
            <span className="sr-only" role="status">{copied ? 'Link copied' : ''}</span>
        </div>
    );
}

export default function StoryDetails() {
    const { story: s, related = [], newer, older, shareUrl } = usePage().props;
    const cat = s.categories && s.categories[0];
    return (
        <SiteLayout title={s.title}>
            <Progress />
            <div className="tw">
                {s.status !== 'published' && (
                    <div className="sty-preview" role="note"><i className="fa fa-eye" aria-hidden="true"></i> Preview: this story is not published, so only admins can see it. <a href={`/admin/content/stories/${s.id}`}>Open it in the editor</a></div>
                )}
                <article id="sty-article" className="sty-article" aria-labelledby="sty-title">
                    <header className="sty-article__head">
                        <nav aria-label="Breadcrumb" className="sty-crumbs">
                            <a href="/">Home</a><span aria-hidden="true">/</span><a href="/studio-stories">Studio Stories</a>
                            {cat && <><span aria-hidden="true">/</span><a href={`/studio-stories?category=${encodeURIComponent(cat.slug)}`}>{cat.name}</a></>}
                        </nav>
                        {cat && <p className="sty-eyebrow">{cat.name}</p>}
                        <h1 id="sty-title">{s.title}</h1>
                        {s.subtitle && <p className="sty-dek">{s.subtitle}</p>}
                        <div className="sty-article__meta">
                            <Byline s={s} />
                            <Share url={shareUrl} title={s.title} />
                        </div>
                    </header>
                    {s.image && <figure className="sty-cover"><img src={s.image} alt="" /></figure>}
                    <div className="sty-prose" dangerouslySetInnerHTML={{ __html: s.body }} />
                    <footer className="sty-article__foot">
                        {s.tags && s.tags.length > 0 && (
                            <p className="sty-tags sty-tags--lg">{s.tags.map((t) => <a key={t} href={`/studio-stories?tag=${encodeURIComponent(t)}`}>#{t}</a>)}</p>
                        )}
                        <Share url={shareUrl} title={s.title} />
                        {s.author && (
                            <div className="sty-author">
                                {s.author.photo ? <img src={s.author.photo} alt="" /> : <span className="sty-byline__dot" aria-hidden="true">{s.author.name.charAt(0)}</span>}
                                <div>
                                    <p className="sty-eyebrow">Written by</p>
                                    <p className="sty-author__name">{s.author.name}</p>
                                    {s.author.role && <p className="sty-author__role">{s.author.role}</p>}
                                </div>
                            </div>
                        )}
                        {(newer || older) && (
                            <nav className="sty-pager" aria-label="More stories">
                                {older ? <a href={older.url} className="is-older"><small>← Older story</small><b>{older.title}</b></a> : <span></span>}
                                {newer ? <a href={newer.url} className="is-newer"><small>Newer story →</small><b>{newer.title}</b></a> : <span></span>}
                            </nav>
                        )}
                    </footer>
                </article>

                {related.length > 0 && (
                    <section className="ac-wide sty-section" aria-labelledby="sty-related">
                        <div className="sty-head">
                            <h2 id="sty-related">Keep reading</h2>
                            <a href="/studio-stories" className="sty-link">All Studio Stories <i className="fa fa-arrow-right" aria-hidden="true"></i></a>
                        </div>
                        <div className="sty-grid">{related.map((r) => <StoryTile key={r.id} s={r} />)}</div>
                    </section>
                )}

                <section className="ac-wide sty-section">
                    <div className="sty-cta">
                        <div>
                            <h2>Turn inspiration into practice</h2>
                            <p>Put these ideas on paper in a live, guided ArtistikCity course.</p>
                        </div>
                        <a href="/courses?type=all" className="sty-btn sty-btn--light">Explore courses</a>
                    </div>
                </section>
            </div>
        </SiteLayout>
    );
}
