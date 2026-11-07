import React, { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { num } from '@/Components/Shop/shop';

/*
 * The artwork preview of the purchase flow (screen 2) and the artwork page's "On a wall" tab.
 *
 *   ArtworkView  the piece on its own, large, on a lit plaster wall, with framer's dimension lines: the default view,
 *                so the painting is what the buyer looks at first.
 *   RoomView     the piece in one of the room photos managed in Admin > Room preview, to scale when the room has a
 *                measured hanging area (area_width_in), otherwise fitted into the area.
 *   ViewPicker   "Artwork" + one tile per room; adapts to however many rooms the admin has (0 rooms = no picker).
 *   ArtPreview   the carousel: previous / next arrows on the picture, swipe on touch screens, arrow keys, "2 of 7", the
 *                thumbnail strip kept centred on the current view, and (in rooms measured to scale) a person for scale.
 *
 * A room photo is enlarged at most a little past its own pixels (no soft, smeared backdrops): the camera only moves closer while
 * the photo still has detail to show.
 */

// frame mouldings, in inches: face width, float gap, depth (for the shadow)
export const FRAMES = {
    NONE: { t: 0, gap: 0, depth: 0.75 },
    BLACK_WOOD: { t: 1.25, gap: 0, depth: 1.5 },
    OAK: { t: 1.25, gap: 0, depth: 1.5 },
    WHITE_FLOAT: { t: 1.1, gap: 0.45, depth: 1.75 },
};

/** Outer size of the framed piece in inches. */
export function framedSize(widthIn, heightIn, frame) {
    const f = FRAMES[frame] || FRAMES.NONE;
    const add = 2 * (f.t + f.gap);
    return { w: Number(widthIn) + add, h: Number(heightIn) + add, f };
}

/** The framed piece as a box that fills its parent (positioned by the caller). */
export function FramedArt({ image, title, widthIn, heightIn, frame = 'NONE', surface = 'canvas', lift = 1 }) {
    const { w, f } = framedSize(widthIn, heightIn, frame);
    const pad = (inches) => `${(inches / w) * 100}%`;
    return (
        <div className={`ap-art ap-art--${String(frame).toLowerCase()} ap-art--${surface}`} style={{ '--ap-lift': lift }}>
            <div className="ap-art__frame" style={{ padding: pad(f.t) }}>
                <div className="ap-art__float" style={{ padding: f.gap ? pad(f.gap) : 0 }}>
                    <div className="ap-art__canvas">
                        {image ? <img src={image} alt={title ? `${title}` : ''} draggable="false" /> : <span className="ap-art__blank"></span>}
                    </div>
                </div>
            </div>
        </div>
    );
}

function useWidth() {
    const ref = useRef(null);
    const [w, setW] = useState(0);
    useLayoutEffect(() => {
        const el = ref.current;
        if (!el) return undefined;
        const update = () => setW(el.clientWidth);
        update();
        if (typeof ResizeObserver === 'undefined') { window.addEventListener('resize', update); return () => window.removeEventListener('resize', update); }
        const ro = new ResizeObserver(update);
        ro.observe(el);
        return () => ro.disconnect();
    }, []);
    return [ref, w];
}

/** The piece alone on a lit wall, as large as the stage allows, with its real size drawn beside it. */
export function ArtworkView({ image, title, widthIn, heightIn, frame, surface }) {
    const [ref, stageW] = useWidth();
    const { w, h } = framedSize(widthIn, heightIn, frame);
    // fit the framed piece into the stage (4:3, a little less on phones), leaving room for the measurements
    const phone = stageW > 0 && stageW < 560;
    const ratio = phone ? 1.05 : 4 / 3;
    const maxW = phone ? 0.74 : 0.62;
    const maxH = phone ? 0.70 : 0.72;
    const scale = Math.min((maxW * ratio) / w, maxH / h);       // stage heights per inch
    const boxW = (w * scale) / ratio;                              // share of the stage width
    const boxH = h * scale;                                        // share of the stage height
    const left = (1 - boxW) / 2;
    const top = (1 - boxH) / 2 - 0.02;
    return (
        <div className="ap-stage ap-stage--wall" ref={ref} style={{ aspectRatio: `${ratio}` }} data-view="artwork">
            <div className="ap-wall__light" aria-hidden="true"></div>
            <div className="ap-place" style={{ left: `${left * 100}%`, top: `${top * 100}%`, width: `${boxW * 100}%`, height: `${boxH * 100}%` }}>
                <FramedArt image={image} title={title} widthIn={widthIn} heightIn={heightIn} frame={frame} surface={surface} lift={1.4} />
                <span className="ap-dim ap-dim--w" aria-hidden="true"><span>{num(widthIn)} in</span></span>
                <span className="ap-dim ap-dim--h" aria-hidden="true"><span>{num(heightIn)} in</span></span>
            </div>
        </div>
    );
}

/**
 * Where the piece sits in a room photo, in photo pixels, and how close the camera may come.
 * Exported for the unit tests.
 */
export function placeInRoom(room, outerWIn, outerHIn, stageW) {
    const pw = Number(room.width) || 1600;
    const ph = Number(room.height) || 1067;
    const a = room.area || { x: 20, y: 15, w: 60, h: 50 };
    const ax = (a.x / 100) * pw;
    const ay = (a.y / 100) * ph;
    const aw = (a.w / 100) * pw;
    const ah = (a.h / 100) * ph;
    let ppi;                        // photo pixels per inch on that wall
    let toScale = !!room.areaWidthIn;
    if (toScale) ppi = aw / Number(room.areaWidthIn);
    else ppi = Math.min((aw * 0.6) / outerWIn, (ah * 0.75) / outerHIn);
    let wpx = outerWIn * ppi;
    let hpx = outerHIn * ppi;
    let tooBig = false;
    if (wpx > aw || hpx > ah) {     // bigger than that wall: shown as large as the wall allows, and said so
        const k = Math.min(aw / wpx, ah / hpx);
        wpx *= k; hpx *= k; tooBig = toScale; toScale = false;
    }
    const cx = ax + aw / 2;
    const cy = ay + ah / 2;
    // the camera: closer until the piece fills ~34% of the stage width, never past the photo's own resolution
    const want = (0.32 * pw) / wpx;
    const native = stageW > 0 ? pw / stageW : 1;
    const zoom = Math.max(1, Math.min(want, native * 1.35, 2.4));
    const cw = pw / zoom;
    const ch = ph / zoom;
    const crop = {
        x: Math.min(pw - cw, Math.max(0, cx - cw / 2)),
        y: Math.min(ph - ch, Math.max(0, cy - ch * 0.48)),
        w: cw,
        h: ch,
    };
    return { pw, ph, box: { x: cx - wpx / 2, y: cy - hpx / 2, w: wpx, h: hpx }, crop, zoom, toScale, tooBig };
}

/** A 5 ft 7 in person standing beside the piece (art hung with its centre 57 in above the floor), in photo pixels. */
export const PERSON_IN = 67;
export function personBox(s, outerWIn) {
    const ppi = s.box.w / outerWIn;
    const h = PERSON_IN * ppi;
    const w = h * 0.3;
    const feet = s.box.y + s.box.h / 2 + 57 * ppi;
    let x = s.box.x + s.box.w + 14 * ppi;
    if (x + w > s.pw) x = s.box.x - 14 * ppi - w;
    return { x: Math.max(0, Math.min(s.pw - w, x)), y: feet - h, w, h };
}

function Person() {
    return (
        <svg viewBox="0 0 30 100" preserveAspectRatio="none" aria-hidden="true" focusable="false">
            <circle cx="15" cy="7" r="6" />
            <path d="M8 16 h14 q4 0 4 4 v30 q0 3 -3 3 h-1 v44 q0 3 -3 3 h-2 q-3 0 -3 -3 v-38 h-2 v38 q0 3 -3 3 h-2 q-3 0 -3 -3 v-44 h-1 q-3 0 -3 -3 v-30 q0 -4 4 -4 z" />
        </svg>
    );
}

/** The piece hung in an admin-managed room photo. */
export function RoomView({ room, image, title, widthIn, heightIn, frame, surface, showPerson = false }) {
    const [ref, stageW] = useWidth();
    const [loaded, setLoaded] = useState(false);
    const { w, h } = framedSize(widthIn, heightIn, frame);
    const s = useMemo(() => placeInRoom(room, w, h, stageW), [room, w, h, stageW]);
    useEffect(() => { setLoaded(false); }, [room.photo]);
    const pct = (v, total) => `${(v / total) * 100}%`;
    return (
        <div className={`ap-stage ap-stage--room ${loaded ? 'is-loaded' : ''}`} ref={ref} style={{ aspectRatio: `${s.pw} / ${s.ph}` }}
             data-view="room" data-room={room.id} data-zoom={s.zoom.toFixed(2)}>
            <div className="ap-scene" style={{ width: `${s.zoom * 100}%`, height: `${s.zoom * 100}%`, transform: `translate(${-(s.crop.x / s.pw) * 100}%, ${-(s.crop.y / s.ph) * 100}%)` }}>
                <img className="ap-scene__photo" src={room.photo} alt={`${room.label}${room.note ? `, ${room.note.toLowerCase()}` : ''}`} draggable="false" onLoad={() => setLoaded(true)} />
                <div className="ap-place" style={{ left: pct(s.box.x, s.pw), top: pct(s.box.y, s.ph), width: pct(s.box.w, s.pw), height: pct(s.box.h, s.ph) }}>
                    <FramedArt image={image} title={title} widthIn={widthIn} heightIn={heightIn} frame={frame} surface={surface} lift={0.8} />
                </div>
                {showPerson && s.toScale && (() => {
                    const b = personBox(s, w);
                    return <div className="ap-person" data-testid="room-person" style={{ left: pct(b.x, s.pw), top: pct(b.y, s.ph), width: pct(b.w, s.pw), height: pct(b.h, s.ph) }}><Person /></div>;
                })()}
            </div>
            <span className="ap-scale" data-testid="room-scale">
                {s.toScale ? `To scale · ${num(widthIn)} × ${num(heightIn)} in` : s.tooBig ? 'Larger than this wall: shown smaller' : `${num(widthIn)} × ${num(heightIn)} in`}
            </span>
        </div>
    );
}

/** "Artwork" and the rooms, one click each. Arrow keys move between them; the strip keeps the current one centred. */
export function ViewPicker({ rooms, value, onChange, image }) {
    const list = [{ id: 'art', label: 'Artwork', thumb: image, isArt: true }, ...rooms.map((r) => ({ id: String(r.id), label: r.label, thumb: r.photo }))];
    const refs = useRef([]);
    const strip = useRef(null);
    useEffect(() => {
        const i = list.findIndex((v) => v.id === value);
        const el = refs.current[i];
        const box = strip.current;
        if (!el || !box || box.scrollWidth <= box.clientWidth) return;
        // scroll only the strip sideways (scrollIntoView would also move the page)
        const left = el.offsetLeft - (box.clientWidth - el.offsetWidth) / 2;
        try { box.scrollTo({ left, behavior: reducedMotion() ? 'auto' : 'smooth' }); } catch (e) { box.scrollLeft = left; }
    }, [value]); // eslint-disable-line react-hooks/exhaustive-deps
    const onKey = (e, i) => {
        const d = e.key === 'ArrowRight' ? 1 : e.key === 'ArrowLeft' ? -1 : 0;
        if (!d) return;
        e.preventDefault();
        const n = (i + d + list.length) % list.length;
        onChange(list[n].id);
        if (refs.current[n]) refs.current[n].focus();
    };
    if (rooms.length === 0) return null;
    return (
        <div className={`ap-views ${list.length > 5 ? 'is-many' : ''}`} role="radiogroup" aria-label="Backgrounds" ref={strip}>
            {list.map((v, i) => (
                <button key={v.id} ref={(el) => { refs.current[i] = el; }} type="button" role="radio" aria-checked={value === v.id}
                        tabIndex={value === v.id ? 0 : -1} className={`ap-view ${value === v.id ? 'is-on' : ''} ${v.isArt ? 'ap-view--art' : ''}`}
                        data-view-id={v.id} onClick={() => onChange(v.id)} onKeyDown={(e) => onKey(e, i)}>
                    <span className="ap-view__img">{v.thumb ? <img src={v.thumb} alt="" loading="lazy" /> : null}</span>
                    <span className="ap-view__label">{v.label}</span>
                </button>
            ))}
        </div>
    );
}

export function reducedMotion() {
    try { return window.matchMedia('(prefers-reduced-motion: reduce)').matches; } catch (e) { return false; }
}

/** Warm the cache for the rooms next to the current one, so the next swipe is instant. */
export function usePreload(rooms, index = 0) {
    useEffect(() => {
        const near = rooms.filter((r, i) => Math.abs(i + 1 - index) <= 1 || (index === 0 && i === rooms.length - 1));
        const keep = near.map((r) => { const i = new Image(); i.src = r.photo; return i; });
        return () => { keep.length = 0; };
    }, [rooms, index]);
}

/**
 * The preview carousel: the stage (artwork on a lit wall, or one of the rooms), previous / next arrows on it, swipe on touch
 * screens, arrow keys when it has focus, "2 of 7 · Living room" (announced to screen readers), the thumbnail strip, and a
 * "person for scale" switch in rooms measured to scale. The last view is remembered for the visit.
 */
export default function ArtPreview({ rooms = [], image, title, widthIn, heightIn, frame = 'NONE', surface = 'canvas', storageKey = 'artistikcity.view', showRoom = false }) {
    const views = useMemo(() => ['art', ...rooms.map((r) => String(r.id))], [rooms]);
    const [view, setView] = useState(() => {
        try {
            const v = window.sessionStorage.getItem(storageKey);
            return v && (v === 'art' || rooms.some((r) => String(r.id) === v)) ? v : 'art';
        } catch (e) { return 'art'; }
    });
    const [dir, setDir] = useState(0);
    const [person, setPerson] = useState(() => { try { return window.localStorage.getItem('artistikcity.person') === '1'; } catch (e) { return false; } });
    const index = Math.max(0, views.indexOf(view));
    usePreload(rooms, index);
    const pick = (v, d = 0) => {
        setDir(d || (views.indexOf(v) > index ? 1 : -1));
        setView(v);
        try { window.sessionStorage.setItem(storageKey, v); } catch (e) { /* private mode */ }
    };
    const step = (d) => { if (views.length > 1) pick(views[(index + d + views.length) % views.length], d); };
    // "See it on your wall" (step 2, part C): move from the plain wall to the first room, once per arrival
    useEffect(() => {
        if (showRoom && view === 'art' && rooms.length > 0) pick(String(rooms[0].id), 1);
    }, [showRoom]); // eslint-disable-line react-hooks/exhaustive-deps
    const togglePerson = (on) => { setPerson(on); try { window.localStorage.setItem('artistikcity.person', on ? '1' : '0'); } catch (e) { /* private mode */ } };

    // swipe: a mostly horizontal drag of 40 px or more
    const touch = useRef(null);
    const onTouchStart = (e) => { const t = e.touches[0]; touch.current = { x: t.clientX, y: t.clientY }; };
    const onTouchEnd = (e) => {
        const s0 = touch.current;
        touch.current = null;
        if (!s0) return;
        const t = e.changedTouches[0];
        const dx = t.clientX - s0.x;
        const dy = t.clientY - s0.y;
        if (Math.abs(dx) >= 40 && Math.abs(dx) > Math.abs(dy) * 1.5) step(dx < 0 ? 1 : -1);
    };
    const onKey = (e) => {
        if (e.key === 'ArrowRight') { e.preventDefault(); step(1); }
        if (e.key === 'ArrowLeft') { e.preventDefault(); step(-1); }
    };

    const room = rooms.find((r) => String(r.id) === view);
    const label = room ? room.label : 'Artwork on a plain wall';
    const measured = !!(room && room.areaWidthIn);
    const common = { image, title, widthIn, heightIn, frame, surface };
    const many = views.length > 1;
    return (
        <div className="ap" role="group" aria-roledescription="carousel" aria-label="Preview backgrounds">
            <div className={`ap-carousel ${dir > 0 ? 'is-next' : dir < 0 ? 'is-prev' : ''}`} tabIndex={many ? 0 : undefined} onKeyDown={many ? onKey : undefined}
                 onTouchStart={many ? onTouchStart : undefined} onTouchEnd={many ? onTouchEnd : undefined}
                 aria-label={many ? 'Use the left and right arrow keys to change the background' : undefined}>
                <div className="ap-slide" key={view}>
                    {room ? <RoomView room={room} {...common} showPerson={person} /> : <ArtworkView {...common} />}
                </div>
                {many && (
                    <>
                        <button type="button" className="ap-nav ap-nav--prev" onClick={() => step(-1)} aria-label="Previous background" data-testid="bg-prev">
                            <i className="fa fa-chevron-left" aria-hidden="true"></i></button>
                        <button type="button" className="ap-nav ap-nav--next" onClick={() => step(1)} aria-label="Next background" data-testid="bg-next">
                            <i className="fa fa-chevron-right" aria-hidden="true"></i></button>
                    </>
                )}
            </div>
            {many && (
                <div className="ap-bar">
                    <p className="ap-count" aria-live="polite" data-testid="bg-count"><b>{index + 1} of {views.length}</b> · {label}</p>
                    {measured && (
                        <label className="ap-personsw">
                            <input type="checkbox" checked={person} onChange={(e) => togglePerson(e.target.checked)} data-testid="person-toggle" />
                            <span>Person for scale <small>(5 ft 7 in)</small></span>
                        </label>
                    )}
                </div>
            )}
            <ViewPicker rooms={rooms} value={room ? view : 'art'} onChange={(v) => pick(v)} image={image} />
        </div>
    );
}
