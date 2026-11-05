import React, { useEffect, useState } from 'react';

/*
 * One small toast for personal, reversible actions (wish list): "Removed from your wish list · Undo". Any component can
 * call showUndo(text, undo); the toast mounts itself once per page.
 */
let push = null;
export function showUndo(text, undo, ms = 6000) {
    if (push) push({ text, undo, ms, at: Date.now() });
}

export default function UndoToast() {
    const [t, setT] = useState(null);
    useEffect(() => { push = setT; return () => { push = null; }; }, []);
    useEffect(() => {
        if (!t) return undefined;
        const h = setTimeout(() => setT(null), t.ms);
        return () => clearTimeout(h);
    }, [t]);
    if (!t) return null;
    return (
        <div className="ac-undo" role="status" aria-live="polite">
            <span>{t.text}</span>
            {t.undo && <button type="button" onClick={() => { const u = t.undo; setT(null); u(); }}>Undo</button>}
            <button type="button" className="ac-undo__x" onClick={() => setT(null)} aria-label="Dismiss"><i className="fa fa-times" aria-hidden="true"></i></button>
        </div>
    );
}
