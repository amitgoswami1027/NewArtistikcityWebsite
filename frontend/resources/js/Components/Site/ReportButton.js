import React, { useEffect, useRef, useState } from 'react';
import { authPost } from '@/Components/Auth/api';

/*
 * "Report" link + small dialog (moderation/ModerationController, POST /reports). A reason code is required, a note
 * optional (required for "Something else"). Signed-out visitors are sent to sign in first. The owner is never told
 * who reported.
 */
export const REPORT_REASONS = [
    ['SPAM', 'Spam or advertising'],
    ['OFFENSIVE', 'Offensive or abusive'],
    ['COPYRIGHT', "Someone else's work (copyright)"],
    ['MISLEADING', 'Misleading or fake'],
    ['PERSONAL_INFO', 'Shares personal information'],
    ['OTHER', 'Something else'],
];

export default function ReportButton({ type, id, label = 'Report', signedIn, className = '' }) {
    const [open, setOpen] = useState(false);
    const [reason, setReason] = useState('');
    const [detail, setDetail] = useState('');
    const [busy, setBusy] = useState(false);
    const [msg, setMsg] = useState(null);
    const dialog = useRef(null);
    const opener = useRef(null);
    useEffect(() => {
        if (!open) return undefined;
        const first = dialog.current && dialog.current.querySelector('input');
        if (first) first.focus();
        const esc = (e) => { if (e.key === 'Escape') close(); };
        document.addEventListener('keydown', esc);
        return () => document.removeEventListener('keydown', esc);
    }, [open]);
    const close = () => { setOpen(false); setMsg(null); if (opener.current) opener.current.focus(); };
    const start = () => {
        if (!signedIn) { window.location.assign(`/login?return_to=${encodeURIComponent(window.location.pathname)}`); return; }
        setOpen(true);
    };
    const send = async (e) => {
        e.preventDefault();
        if (!reason) { setMsg({ ok: false, text: 'Choose a reason.' }); return; }
        setBusy(true);
        try {
            const r = await authPost('/reports', { targetType: type, targetId: id, reason, detail });
            setMsg({ ok: true, text: r.message });
        } catch (err) {
            setMsg({ ok: false, text: (err.fieldErrors && (err.fieldErrors.detail || err.fieldErrors.reason)) || err.message });
        }
        setBusy(false);
    };
    return (
        <>
            <button type="button" ref={opener} className={`ac-report ${className}`} onClick={start} aria-haspopup="dialog"><i className="fa fa-flag-o" aria-hidden="true"></i> {label}</button>
            {open && (
                <div className="ac-report__backdrop" onClick={(e) => { if (e.target === e.currentTarget) close(); }}>
                    <form className="ac-report__dialog" role="dialog" aria-modal="true" aria-labelledby={`rep-${type}-${id}`} ref={dialog} onSubmit={send}>
                        <h2 id={`rep-${type}-${id}`}>What's wrong with it?</h2>
                        {msg && msg.ok ? (
                            <>
                                <p role="status">{msg.text}</p>
                                <button type="button" className="dm-btn dm-btn--brand" onClick={close}>Done</button>
                            </>
                        ) : (
                            <>
                                <fieldset>
                                    <legend className="sr-only">Reason</legend>
                                    {REPORT_REASONS.map(([v, l]) => (
                                        <label key={v} className="ac-report__opt"><input type="radio" name="reason" value={v} checked={reason === v} onChange={() => setReason(v)} /> {l}</label>
                                    ))}
                                </fieldset>
                                <label className="ac-report__label" htmlFor={`rep-d-${type}-${id}`}>Anything we should know? {reason === 'OTHER' ? '(required)' : '(optional)'}</label>
                                <textarea id={`rep-d-${type}-${id}`} rows={3} maxLength={1000} value={detail} onChange={(e) => setDetail(e.target.value)} />
                                {msg && <p className="ac-report__err" role="alert">{msg.text}</p>}
                                <div className="ac-report__actions">
                                    <button type="button" className="dm-btn dm-btn--ghost" onClick={close}>Cancel</button>
                                    <button type="submit" className="dm-btn dm-btn--brand" disabled={busy}>{busy ? 'Sending…' : 'Send report'}</button>
                                </div>
                            </>
                        )}
                    </form>
                </div>
            )}
        </>
    );
}
