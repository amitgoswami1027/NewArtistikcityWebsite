import React, { useEffect, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import { cookieOf } from '@/Components/Site/siteContent';

const NAME = 'ac_consent';

export function readConsent() {
    try {
        const m = document.cookie.match(/(?:^|;\s*)ac_consent=([^;]+)/);
        return m ? decodeURIComponent(m[1]) : null;
    } catch (e) {
        return null;
    }
}

function saveConsent(value) {
    try {
        const secure = window.location.protocol === 'https:' ? '; Secure' : '';
        document.cookie = `${NAME}=${encodeURIComponent(value)}; Max-Age=${60 * 60 * 24 * 365}; Path=/; SameSite=Lax${secure}`;
    } catch (e) { /* cookies blocked: the banner simply shows again next visit */ }
    try { window.dispatchEvent(new CustomEvent('ac:consent', { detail: value })); } catch (e) { /* old browser */ }
}

/** The bar itself (also used, without the cookie logic, by the admin live preview). */
export function CookieBar({ cookie, onAccept, onDecline, preview = false }) {
    return (
        <div className={`ac-cookie ${preview ? 'is-preview' : ''}`} role={preview ? undefined : 'region'} aria-label="Cookie consent">
            <p>
                {cookie.message}
                {cookie.policy_link && <> <a href={cookie.policy_link} target={preview ? '_blank' : undefined} rel="noopener noreferrer">Privacy policy</a></>}
            </p>
            <div className="ac-cookie__actions">
                {cookie.decline_label && <button type="button" className="dm-btn dm-btn--line" onClick={onDecline}>{cookie.decline_label}</button>}
                <button type="button" className="dm-btn dm-btn--brand" onClick={onAccept}>{cookie.accept_label || 'OK'}</button>
            </div>
        </div>
    );
}

/** Cookie & privacy banner, switched on and worded in Admin > System parameters > SEO & Layout Controls. */
export default function CookieBanner() {
    const { site } = usePage().props;
    const cookie = cookieOf(site);
    const [open, setOpen] = useState(false);
    useEffect(() => { setOpen(cookie.enabled === '1' && !readConsent()); }, [cookie.enabled]);
    if (!open) return null;
    const choose = (v) => { saveConsent(v); setOpen(false); };
    return <CookieBar cookie={cookie} onAccept={() => choose('all')} onDecline={() => choose('essential')} />;
}
