import React, { useMemo, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import SiteLayout from '@/Components/Site/SiteLayout';
import StoryCard, { storyDate } from '@/Components/Site/StoryCard';

export default function StudioStories() {
    const { stories = [], blogUrl = 'https://studiovaishaliarts.com/blog/' } = usePage().props;
    const [q, setQ] = useState('');
    const [cat, setCat] = useState('');
    const categories = useMemo(() => [...new Set(stories.map((s) => s.category).filter(Boolean))], [stories]);
    const list = stories.filter((s) => (!cat || s.category === cat)
        && (!q || `${s.title} ${s.excerpt || ''}`.toLowerCase().includes(q.toLowerCase())));
    const [featured, ...rest] = list;

    return (
        <SiteLayout title="Studio Stories">
            <div className="ac-wide">
                <ul className="dm-crumbs">
                    <li><a href={route('welcome')}>Home</a></li>
                    <li>Studio Stories</li>
                </ul>
                <header className="dm-journal-hero" style={{ paddingTop: 8 }}>
                    <span className="ac-eyebrow">The ArtistikCity journal</span>
                    <h1>Studio Stories</h1>
                    <p>Notes from the easel: creative blocks, the artist's mindset, techniques and the joy of making art every day. Written at Studio Vaishali Arts.</p>
                </header>

                <div className="dm-filterbar" style={{ paddingTop: 0 }}>
                    <div className="dm-search" style={{ display: 'block', maxWidth: 320, flex: '0 1 320px' }}>
                        <i className="fa fa-search"></i>
                        <input type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search stories" aria-label="Search stories" />
                    </div>
                    {categories.length > 1 && (
                        <div className="dm-cats" style={{ border: 0 }}>
                            <ul style={{ padding: 0 }}>
                                <li><button type="button" className={!cat ? 'is-active' : ''} onClick={() => setCat('')}>All</button></li>
                                {categories.map((c) => <li key={c}><button type="button" className={cat === c ? 'is-active' : ''} onClick={() => setCat(c)}>{c}</button></li>)}
                            </ul>
                        </div>
                    )}
                    <div className="dm-count"><a href={blogUrl} target="_blank" rel="noopener" className="dm-btn dm-btn--line">Visit Studio Vaishali Arts <i className="fa fa-external-link"></i></a></div>
                </div>

                {featured ? (
                    <>
                        <article className="dm-feature">
                            <a className="dm-feature__img" href={featured.url} target="_blank" rel="noopener">
                                {featured.image && <img src={featured.image} alt={featured.title} />}
                            </a>
                            <div>
                                <div className="dm-story__meta">Latest story{featured.date ? ` · ${storyDate(featured.date)}` : ''}</div>
                                <h2><a href={featured.url} target="_blank" rel="noopener">{featured.title}</a></h2>
                                {featured.excerpt && <p>{featured.excerpt}</p>}
                                <a href={featured.url} target="_blank" rel="noopener" className="dm-btn dm-btn--ink">Read the story <i className="fa fa-external-link"></i></a>
                            </div>
                        </article>
                        {rest.length > 0 && (
                            <section className="dm-section" style={{ paddingTop: 40 }}>
                                <div className="dm-grid" style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))' }}>
                                    {rest.map((s) => <StoryCard key={s.url} story={s} />)}
                                </div>
                            </section>
                        )}
                    </>
                ) : (
                    <div className="ac-empty" style={{ margin: '24px 0 64px' }}>
                        <h3>No stories found</h3>
                        <p>Try a different search, or read everything on the studio blog.</p>
                        <a href={blogUrl} target="_blank" rel="noopener" className="dm-btn dm-btn--ink">Open the studio blog</a>
                    </div>
                )}
            </div>

            <section className="dm-section" style={{ paddingTop: 0 }}>
                <div className="ac-wide">
                    <div className="dm-join">
                        <div className="dm-join__txt">
                            <h2>Turn inspiration into practice</h2>
                            <p>Loved a story? Put those ideas on paper in a live, guided ArtistikCity course.</p>
                            <a href={route('courses') + '?type=all'} className="dm-btn dm-btn--brand dm-btn--lg">Explore courses</a>
                        </div>
                        <div className="dm-join__img" style={{ backgroundImage: `url(${(stories[1] && stories[1].image) || '/assets/images/home-banner.png'})` }}></div>
                    </div>
                </div>
            </section>
        </SiteLayout>
    );
}
