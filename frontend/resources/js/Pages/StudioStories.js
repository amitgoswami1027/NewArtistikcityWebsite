import React, { useEffect, useMemo, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import axios from 'axios';
import SiteLayout from '@/Components/Site/SiteLayout';
import { storyDate } from '@/Components/Site/StoryCard';

/*
 * Studio Stories (/studio-stories): the ArtistikCity journal. Every story published in Admin > Content > Studio Stories
 * appears here: the featured (or latest) story in the banner, then category tabs, search and a grid that loads 9 at a
 * time. Category, tag and search are mirrored in the address bar (?category=slug&tag=name&q=), so links can be shared.
 * Below, a lazily loaded strip of articles from the Studio Vaishali Arts blog.
 */

const PAGE = 9;

function readQuery() {
    try {
        const u = new URLSearchParams(window.location.search);
        return { category: u.get('category') || '', tag: u.get('tag') || '', q: u.get('q') || '' };
    } catch (e) {
        return { category: '', tag: '', q: '' };
    }
}

export function Byline({ s, light = false }) {
    return (
        <p className={`sty-byline ${light ? 'is-light' : ''}`}>
            {s.author && s.author.photo ? <img src={s.author.photo} alt="" /> : <span className="sty-byline__dot" aria-hidden="true">{((s.author && s.author.name) || 'A').charAt(0)}</span>}
            <span>{s.author ? s.author.name : 'ArtistikCity'}</span>
            <span aria-hidden="true">·</span>
            <time dateTime={String(s.date || '').replace(' ', 'T')}>{storyDate(s.date)}</time>
            {Number(s.readMinutes) > 0 && <><span aria-hidden="true">·</span><span>{s.readMinutes} min read</span></>}
        </p>
    );
}

export function StoryTile({ s, onTag }) {
    const cat = s.categories && s.categories[0];
    return (
        <article className="sty-tile">
            <a href={s.url} className="sty-tile__img" tabIndex={-1} aria-hidden="true">
                {s.image ? <img src={s.image} alt="" loading="lazy" /> : <span className="sty-tile__noimg"><i className="fa fa-pencil" aria-hidden="true"></i></span>}
            </a>
            <div className="sty-tile__body">
                {cat && <p className="sty-eyebrow">{cat.name}</p>}
                <h3><a href={s.url}>{s.title}</a></h3>
                {s.subtitle && <p className="sty-tile__dek">{s.subtitle}</p>}
                <Byline s={s} />
                {onTag && s.tags && s.tags.length > 0 && (
                    <p className="sty-tags">{s.tags.slice(0, 3).map((t) => <button key={t} type="button" onClick={() => onTag(t)}>#{t}</button>)}</p>
                )}
            </div>
        </article>
    );
}

function StudioBlog() {
    const [data, setData] = useState(null);
    useEffect(() => {
        axios.get('/api/studio-stories/studio-blog?limit=3').then((r) => setData(r.data)).catch(() => setData({ stories: [] }));
    }, []);
    if (!data || !(data.stories || []).length) return null;
    return (
        <section className="ac-wide sty-section" aria-labelledby="sty-blog">
            <div className="sty-head">
                <div>
                    <p className="sty-eyebrow">From the studio blog</p>
                    <h2 id="sty-blog">More from Studio Vaishali Arts</h2>
                </div>
                <a href={data.blogUrl} target="_blank" rel="noopener noreferrer" className="sty-link">Visit the studio blog <i className="fa fa-external-link" aria-hidden="true"></i></a>
            </div>
            <div className="sty-grid">
                {data.stories.map((s) => (
                    <article key={s.url} className="sty-tile">
                        <a href={s.url} target="_blank" rel="noopener noreferrer" className="sty-tile__img" tabIndex={-1} aria-hidden="true">
                            {s.image ? <img src={s.image} alt="" loading="lazy" onError={(e) => { e.currentTarget.style.display = 'none'; }} /> : <span className="sty-tile__noimg"><i className="fa fa-pencil" aria-hidden="true"></i></span>}
                        </a>
                        <div className="sty-tile__body">
                            <p className="sty-eyebrow">{s.category || 'Studio Vaishali Arts'}</p>
                            <h3><a href={s.url} target="_blank" rel="noopener noreferrer">{s.title} <i className="fa fa-external-link sty-ext" aria-hidden="true"></i></a></h3>
                            {s.excerpt && <p className="sty-tile__dek">{s.excerpt}</p>}
                            {s.date && <p className="sty-byline"><span>{storyDate(s.date)}</span></p>}
                        </div>
                    </article>
                ))}
            </div>
        </section>
    );
}

export default function StudioStories() {
    const { stories = [], categories = [] } = usePage().props;
    const initial = useMemo(readQuery, []);
    const [category, setCategory] = useState(initial.category);
    const [tag, setTag] = useState(initial.tag);
    const [q, setQ] = useState(initial.q);
    const [shown, setShown] = useState(PAGE);

    useEffect(() => {
        try {
            const u = new URL(window.location.href);
            [['category', category], ['tag', tag], ['q', q.trim()]].forEach(([k, v]) => (v ? u.searchParams.set(k, v) : u.searchParams.delete(k)));
            window.history.replaceState(window.history.state, '', u.pathname + u.search);
        } catch (e) { /* old browser */ }
        setShown(PAGE);
    }, [category, tag, q]);

    const filtering = Boolean(category || tag || q.trim());
    const hero = stories.find((s) => s.featured) || stories[0];
    const list = useMemo(() => {
        const needle = q.trim().toLowerCase();
        return stories.filter((s) => {
            if (!filtering && hero && s.id === hero.id) return false;          // the banner already shows it
            if (category && !(s.categories || []).some((c) => c.slug === category)) return false;
            if (tag && !(s.tags || []).some((t) => t.toLowerCase() === tag.toLowerCase())) return false;
            if (needle && !`${s.title} ${s.subtitle} ${(s.tags || []).join(' ')} ${s.author ? s.author.name : ''}`.toLowerCase().includes(needle)) return false;
            return true;
        });
    }, [stories, category, tag, q, filtering, hero]);
    const catName = (categories.find((c) => c.slug === category) || {}).name;
    const clear = () => { setCategory(''); setTag(''); setQ(''); };

    return (
        <SiteLayout title="Studio Stories">
            <div className="tw">
                {/* ---------- banner ---------- */}
                <section className="relative overflow-hidden bg-ink text-white">
                    <div className="absolute inset-0 opacity-40" style={{ background: 'radial-gradient(circle at 85% 20%, #9c0d48 0, transparent 45%), radial-gradient(circle at 10% 90%, #3b5bdb 0, transparent 40%)' }} aria-hidden="true"></div>
                    <div className="ac-wide relative py-16 sm:py-20 sty-hero">
                        <div>
                            <nav aria-label="Breadcrumb" className="text-sm text-gray-400"><a href="/" className="hover:text-white">Home</a> <span className="mx-2" aria-hidden="true">/</span> <span className="text-white">Studio Stories</span></nav>
                            <p className="mt-6 font-mono text-xs tracking-widest uppercase text-pink-300">The ArtistikCity journal</p>
                            <h1 className="mt-4 text-5xl sm:text-7xl font-black tracking-tight leading-none text-white">Studio<br /><span className="text-brand">Stories.</span></h1>
                            <p className="mt-6 max-w-xl text-xl text-gray-300">Notes from the easel: techniques, materials, the artist's mindset and the stories behind the work, written by our artists and teachers.</p>
                            <p className="mt-6 text-sm text-gray-400">{stories.length} {stories.length === 1 ? 'story' : 'stories'}{categories.length ? ` · ${categories.length} ${categories.length === 1 ? 'topic' : 'topics'}` : ''}</p>
                        </div>
                        {hero && (
                            <a href={hero.url} className="sty-feature">
                                <span className="sty-feature__img">{hero.image ? <img src={hero.image} alt="" /> : <i className="fa fa-pencil" aria-hidden="true"></i>}</span>
                                <span className="sty-feature__body">
                                    <span className="sty-feature__eyebrow">{hero.featured ? 'Featured story' : 'Latest story'}{hero.categories && hero.categories[0] ? ` · ${hero.categories[0].name}` : ''}</span>
                                    <span className="sty-feature__title">{hero.title}</span>
                                    {hero.subtitle && <span className="sty-feature__dek">{hero.subtitle}</span>}
                                    <Byline s={hero} light />
                                </span>
                            </a>
                        )}
                    </div>
                </section>

                {/* ---------- topics + search ---------- */}
                <div className="sty-bar">
                    <div className="ac-wide sty-bar__in">
                        <div className="sty-cats" role="tablist" aria-label="Topics">
                            <button type="button" role="tab" aria-selected={!category} className={!category ? 'is-on' : ''} onClick={() => setCategory('')}>All stories</button>
                            {categories.map((c) => (
                                <button key={c.slug} type="button" role="tab" aria-selected={category === c.slug} className={category === c.slug ? 'is-on' : ''} onClick={() => setCategory(c.slug)}>
                                    {c.name} <small>{c.stories}</small>
                                </button>
                            ))}
                        </div>
                        <label className="sty-search" htmlFor="sty-q">
                            <i className="fa fa-search" aria-hidden="true"></i>
                            <span className="sr-only">Search stories</span>
                            <input id="sty-q" type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search stories" />
                        </label>
                    </div>
                </div>

                {/* ---------- the stories ---------- */}
                <section className="ac-wide sty-section" aria-labelledby="sty-all">
                    <div className="sty-head">
                        <h2 id="sty-all">{catName || (tag ? `#${tag}` : q.trim() ? 'Search results' : 'All stories')}</h2>
                        {filtering && (
                            <p className="sty-active" aria-live="polite">
                                {list.length} {list.length === 1 ? 'story' : 'stories'}
                                {tag && <button type="button" onClick={() => setTag('')} aria-label={`Remove tag ${tag}`}>#{tag} ×</button>}
                                <button type="button" className="sty-link" onClick={clear}>Show all stories</button>
                            </p>
                        )}
                    </div>
                    {list.length === 0 ? (
                        <div className="sty-empty">
                            <p className="sty-empty__title">{stories.length === 0 ? 'The first stories are on their way' : 'No stories match'}</p>
                            <p>{stories.length === 0 ? 'Our artists are writing them now. Meanwhile, find a course that gets you painting.' : 'Try another topic or a shorter search.'}</p>
                            {stories.length === 0 ? <a href="/courses?type=all" className="sty-btn">Browse courses</a> : <button type="button" className="sty-btn" onClick={clear}>Show all stories</button>}
                        </div>
                    ) : (
                        <>
                            <div className="sty-grid">
                                {list.slice(0, shown).map((s) => <StoryTile key={s.id} s={s} onTag={setTag} />)}
                            </div>
                            {list.length > shown && (
                                <div className="sty-more"><button type="button" className="sty-btn sty-btn--line" onClick={() => setShown((n) => n + PAGE)}>Load more stories ({list.length - shown} more)</button></div>
                            )}
                        </>
                    )}
                </section>

                <StudioBlog />

                <section className="ac-wide sty-section">
                    <div className="sty-cta">
                        <div>
                            <h2>Turn inspiration into practice</h2>
                            <p>Loved a story? Put those ideas on paper in a live, guided ArtistikCity course.</p>
                        </div>
                        <a href="/courses?type=all" className="sty-btn sty-btn--light">Explore courses</a>
                    </div>
                </section>
            </div>
        </SiteLayout>
    );
}
