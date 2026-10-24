import React from 'react';

const FILE_EXT = /\.(mp4|m4v|mov|webm)(\?.*)?$/i;

/**
 * What a saved video value is and how to play it.
 * Values come from Admin > Courses: a link (YouTube, Vimeo, a direct video file) or an uploaded file
 * (/storage/uploads/courses/<id>/videos/<name>.mp4).
 *
 * @returns {{kind: 'embed'|'file'|'link', src: string, provider?: string}|null}
 */
export function videoSource(url, { autoplay = false } = {}) {
    const u = String(url || '').trim();
    if (!u) return null;
    const yt = u.match(/(?:youtu\.be\/|youtube(?:-nocookie)?\.com\/(?:watch\?(?:.*&)?v=|embed\/|shorts\/|live\/))([\w-]{6,})/);
    if (yt) return { kind: 'embed', provider: 'YouTube', src: `https://www.youtube-nocookie.com/embed/${yt[1]}?rel=0${autoplay ? '&autoplay=1' : ''}` };
    const vm = u.match(/vimeo\.com\/(?:video\/)?(\d{5,})/);
    if (vm) return { kind: 'embed', provider: 'Vimeo', src: `https://player.vimeo.com/video/${vm[1]}${autoplay ? '?autoplay=1' : ''}` };
    if (u.startsWith('/storage/') || FILE_EXT.test(u)) return { kind: 'file', src: u };
    return { kind: 'link', src: u };
}

/**
 * Plays a video value: an embedded YouTube / Vimeo player or the browser's own player for files.
 * Renders nothing for other links (the caller can show a "watch" link instead).
 */
export default function VideoPlayer({ url, title, autoplay = false, className, poster }) {
    const s = videoSource(url, { autoplay });
    if (!s || s.kind === 'link') return null;
    if (s.kind === 'embed') {
        return (
            <iframe className={className} src={s.src} title={title || 'Video'} loading="lazy"
                allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture; fullscreen" allowFullScreen></iframe>
        );
    }
    return (
        // eslint-disable-next-line jsx-a11y/media-has-caption
        <video className={className} src={s.src} title={title || 'Video'} controls playsInline preload="metadata" autoPlay={autoplay} poster={poster}></video>
    );
}
