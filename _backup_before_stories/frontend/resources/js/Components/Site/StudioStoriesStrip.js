import React, { useEffect, useState } from 'react';
import axios from 'axios';
import StoryCard from '@/Components/Site/StoryCard';

/** Latest Studio Stories (from studiovaishaliarts.com) shown as a strip on other pages. */
export default function StudioStoriesStrip({ limit = 4, title = 'Fresh from Studio Stories' }) {
    const [stories, setStories] = useState([]);
    useEffect(() => {
        axios.get(`/api/studio-stories?limit=${limit}`).then((r) => setStories((r.data && r.data.stories) || [])).catch(() => setStories([]));
    }, [limit]);
    if (!stories.length) return null;
    return (
        <section className="dm-section">
            <div className="ac-wide">
                <div className="dm-head">
                    <div>
                        <h2>{title}</h2>
                        <p>Ideas, inspiration and honest notes on the artist's life from Studio Vaishali Arts.</p>
                    </div>
                    <a href={route('studio.stories')}>Explore all stories</a>
                </div>
                <div className="dm-grid">
                    {stories.map((s) => <StoryCard key={s.url} story={s} />)}
                </div>
            </div>
        </section>
    );
}
