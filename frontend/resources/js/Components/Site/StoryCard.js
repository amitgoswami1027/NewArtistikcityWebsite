import React from 'react';

export function storyDate(d) {
    if (!d) return '';
    const dt = new Date(d);
    return isNaN(dt) ? d : dt.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
}

export default function StoryCard({ story }) {
    return (
        <article className="dm-story">
            <a className="dm-story__img" href={story.url} target="_blank" rel="noopener">
                {story.image ? <img src={story.image} alt={story.title} loading="lazy" /> : null}
            </a>
            <div className="dm-story__meta">{story.category || 'Studio Stories'}{story.date ? ` · ${storyDate(story.date)}` : ''}</div>
            <h3><a href={story.url} target="_blank" rel="noopener">{story.title}</a></h3>
            {story.excerpt && <p>{story.excerpt}</p>}
            <a className="dm-readlink" href={story.url} target="_blank" rel="noopener">Read the story <i className="fa fa-external-link"></i></a>
        </article>
    );
}
