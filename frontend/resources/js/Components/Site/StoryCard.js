import React from 'react';

export function storyDate(d) {
    if (!d) return '';
    const dt = new Date(String(d).replace(' ', 'T'));
    return isNaN(dt) ? d : dt.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
}

/** A story teaser (home page strip). Studio Stories open on this site; studio-blog stories open in a new tab. */
export default function StoryCard({ story }) {
    const ext = story.external || /^https?:\/\//i.test(String(story.url || ''));
    const link = ext ? { target: '_blank', rel: 'noopener noreferrer' } : {};
    return (
        <article className="dm-story">
            <a className="dm-story__img" href={story.url} {...link} tabIndex={-1} aria-hidden="true">
                {story.image ? <img src={story.image} alt="" loading="lazy" /> : null}
            </a>
            <div className="dm-story__meta">{story.category || 'Studio Stories'}{story.date ? ` · ${storyDate(story.date)}` : ''}</div>
            <h3><a href={story.url} {...link}>{story.title}</a></h3>
            {story.excerpt && <p>{story.excerpt}</p>}
            <a className="dm-readlink" href={story.url} {...link}>Read the story {ext ? <i className="fa fa-external-link" aria-hidden="true"></i> : <i className="fa fa-arrow-right" aria-hidden="true"></i>}</a>
        </article>
    );
}
