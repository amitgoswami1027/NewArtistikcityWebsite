import React, { useEffect, useState } from 'react';
import { Inertia } from '@inertiajs/inertia';

/*
 * Waiting for data: when moving to another page (course grid, marketplace, orders...) takes longer than 150 ms, the
 * content area shows grey placeholder cards instead of the old page, so a slow network never looks frozen. Fast
 * visits show nothing (no flash). Partial reloads (only: [...]) don't trigger it.
 */
export default function SlowVisit({ delay = 150 }) {
    const [slow, setSlow] = useState(false);
    useEffect(() => {
        let timer = null;
        const offStart = Inertia.on('start', (e) => {
            const v = e && e.detail && e.detail.visit;
            if (v && v.only && v.only.length) return;
            clearTimeout(timer);
            timer = setTimeout(() => setSlow(true), delay);
        });
        const offFinish = Inertia.on('finish', () => { clearTimeout(timer); setSlow(false); });
        return () => { clearTimeout(timer); offStart(); offFinish(); };
    }, []);
    if (!slow) return null;
    return (
        <div className="ac-slow" aria-busy="true" aria-live="polite">
            <span className="sr-only">Loading…</span>
            <div className="ac-wide ac-slow__grid">
                {Array.from({ length: 8 }).map((_, i) => (
                    <div key={i} className="ac-skel-card"><span className="ac-skel ac-skel--img"></span><span className="ac-skel" style={{ width: '80%' }}></span><span className="ac-skel" style={{ width: '50%' }}></span></div>
                ))}
            </div>
        </div>
    );
}
