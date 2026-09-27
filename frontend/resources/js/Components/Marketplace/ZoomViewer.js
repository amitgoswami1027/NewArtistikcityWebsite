import React, { useEffect, useRef, useState } from 'react';

/**
 * Texture zoom: hover (or tap) to magnify the brushwork at the pointer, and a full-screen viewer with
 * wheel, pinch, double-tap and drag so collectors can inspect paint strokes, canvas weave and the signature.
 */
export default function ZoomViewer({ images = [], title }) {
    const list = images.length ? images : [{ url: '/assets/images/home-banner.png', alt: title }];
    const [active, setActive] = useState(0);
    const [hover, setHover] = useState(null);
    const [full, setFull] = useState(false);
    const img = list[Math.min(active, list.length - 1)];

    const move = (e) => {
        const r = e.currentTarget.getBoundingClientRect();
        setHover({ x: ((e.clientX - r.left) / r.width) * 100, y: ((e.clientY - r.top) / r.height) * 100 });
    };

    return (
        <div>
            <div className="relative rounded-2xl bg-gray-100 overflow-hidden mk-zoom select-none"
                 onMouseMove={move} onMouseLeave={() => setHover(null)} onClick={() => setFull(true)}
                 role="button" tabIndex={0} aria-label={`Open full-screen zoom of ${img.alt || title}`}
                 onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); setFull(true); } }}>
                <div className="flex items-center justify-center p-6 sm:p-10" style={{ minHeight: 420 }}>
                    <img src={img.url} alt={img.alt || title} className="mk-art max-w-full h-auto" style={{ maxHeight: 640 }} draggable="false" />
                </div>
                {hover && (
                    <div className="hidden md:block absolute inset-0 pointer-events-none" aria-hidden="true"
                         style={{ backgroundImage: `url(${img.url})`, backgroundRepeat: 'no-repeat', backgroundSize: '260%', backgroundPosition: `${hover.x}% ${hover.y}%`, backgroundColor: '#f3f4f6' }}>
                    </div>
                )}
                <span className="absolute bottom-4 right-4 inline-flex items-center gap-2 rounded-full bg-white bg-opacity-90 shadow px-3 py-1.5 text-xs font-bold text-ink pointer-events-none">
                    <i className="fa fa-search-plus" aria-hidden="true"></i><span className="hidden md:inline">Hover to inspect texture · click for full screen</span><span className="md:hidden">Tap to zoom</span>
                </span>
            </div>
            {list.length > 1 && (
                <div className="mt-3 flex gap-3 overflow-x-auto pb-1" role="tablist" aria-label="Photos">
                    {list.map((m, i) => (
                        <button key={m.id || i} type="button" role="tab" aria-selected={i === active} onClick={() => setActive(i)}
                                className={`flex-none w-20 h-20 rounded-xl overflow-hidden border-2 ${i === active ? 'border-ink' : 'border-transparent opacity-70 hover:opacity-100'}`}>
                            <img src={m.url} alt={m.alt || `${title}, photo ${i + 1}`} className="w-full h-full object-cover" />
                        </button>
                    ))}
                </div>
            )}
            {full && <FullScreen images={list} start={active} title={title} onClose={() => setFull(false)} />}
        </div>
    );
}

function FullScreen({ images, start, title, onClose }) {
    const [i, setI] = useState(start);
    const [scale, setScale] = useState(1);
    const [pos, setPos] = useState({ x: 0, y: 0 });
    const pointers = useRef(new Map());
    const pinch = useRef(null);
    const drag = useRef(null);
    const lastTap = useRef(0);
    const img = images[i];

    useEffect(() => {
        const key = (e) => {
            if (e.key === 'Escape') onClose();
            if (e.key === 'ArrowRight') go(1);
            if (e.key === 'ArrowLeft') go(-1);
            if (e.key === '+' || e.key === '=') zoomTo(scale * 1.4);
            if (e.key === '-') zoomTo(scale / 1.4);
        };
        document.addEventListener('keydown', key);
        const prev = document.body.style.overflow;
        document.body.style.overflow = 'hidden';
        return () => { document.removeEventListener('keydown', key); document.body.style.overflow = prev; };
    });

    const clamp = (s) => Math.min(6, Math.max(1, s));
    const zoomTo = (s) => { const n = clamp(s); setScale(n); if (n === 1) setPos({ x: 0, y: 0 }); };
    const go = (d) => { setI((x) => (x + d + images.length) % images.length); setScale(1); setPos({ x: 0, y: 0 }); };

    const down = (e) => {
        e.currentTarget.setPointerCapture(e.pointerId);
        pointers.current.set(e.pointerId, { x: e.clientX, y: e.clientY });
        if (pointers.current.size === 2) {
            const [a, b] = [...pointers.current.values()];
            pinch.current = { d: Math.hypot(a.x - b.x, a.y - b.y), s: scale };
        } else {
            drag.current = { x: e.clientX, y: e.clientY, px: pos.x, py: pos.y };
            const now = Date.now();
            if (now - lastTap.current < 300) zoomTo(scale > 1 ? 1 : 2.5);
            lastTap.current = now;
        }
    };
    const moveP = (e) => {
        if (!pointers.current.has(e.pointerId)) return;
        pointers.current.set(e.pointerId, { x: e.clientX, y: e.clientY });
        if (pointers.current.size === 2 && pinch.current) {
            const [a, b] = [...pointers.current.values()];
            zoomTo(pinch.current.s * (Math.hypot(a.x - b.x, a.y - b.y) / pinch.current.d));
        } else if (drag.current && scale > 1) {
            setPos({ x: drag.current.px + (e.clientX - drag.current.x), y: drag.current.py + (e.clientY - drag.current.y) });
        }
    };
    const up = (e) => {
        pointers.current.delete(e.pointerId);
        if (pointers.current.size < 2) pinch.current = null;
        if (pointers.current.size === 0) drag.current = null;
    };

    return (
        <div className="fixed inset-0 z-50 bg-black bg-opacity-95 flex flex-col" role="dialog" aria-modal="true" aria-label={`${title}, full-screen zoom`}>
            <div className="flex items-center gap-3 px-4 h-16 text-white flex-none">
                <p className="font-bold truncate flex-1">{title}</p>
                <button type="button" onClick={() => zoomTo(scale / 1.4)} className="w-10 h-10 rounded-full border border-white border-opacity-30 hover:bg-white hover:bg-opacity-10" aria-label="Zoom out"><i className="fa fa-minus" aria-hidden="true"></i></button>
                <span className="font-mono text-sm w-14 text-center">{Math.round(scale * 100)}%</span>
                <button type="button" onClick={() => zoomTo(scale * 1.4)} className="w-10 h-10 rounded-full border border-white border-opacity-30 hover:bg-white hover:bg-opacity-10" aria-label="Zoom in"><i className="fa fa-plus" aria-hidden="true"></i></button>
                <button type="button" onClick={onClose} className="ml-2 w-10 h-10 rounded-full bg-white text-ink" aria-label="Close"><i className="fa fa-times" aria-hidden="true"></i></button>
            </div>
            <div className="relative flex-1 overflow-hidden flex items-center justify-center" style={{ touchAction: 'none' }}
                 onWheel={(e) => zoomTo(scale * (e.deltaY < 0 ? 1.15 : 1 / 1.15))}
                 onPointerDown={down} onPointerMove={moveP} onPointerUp={up} onPointerCancel={up}>
                <img src={img.url} alt={img.alt || title} draggable="false" className="max-w-full max-h-full select-none"
                     style={{ transform: `translate(${pos.x}px, ${pos.y}px) scale(${scale})`, transition: drag.current || pinch.current ? 'none' : 'transform .2s ease', cursor: scale > 1 ? 'grab' : 'zoom-in' }} />
                {images.length > 1 && (
                    <>
                        <button type="button" onClick={() => go(-1)} className="absolute left-4 top-1/2 transform -translate-y-1/2 w-12 h-12 rounded-full bg-white bg-opacity-90 text-ink" aria-label="Previous photo"><i className="fa fa-angle-left text-xl" aria-hidden="true"></i></button>
                        <button type="button" onClick={() => go(1)} className="absolute right-4 top-1/2 transform -translate-y-1/2 w-12 h-12 rounded-full bg-white bg-opacity-90 text-ink" aria-label="Next photo"><i className="fa fa-angle-right text-xl" aria-hidden="true"></i></button>
                    </>
                )}
            </div>
            <p className="text-center text-xs text-gray-400 py-3 flex-none">Scroll, pinch or double-tap to zoom · drag to move · Esc to close</p>
        </div>
    );
}
