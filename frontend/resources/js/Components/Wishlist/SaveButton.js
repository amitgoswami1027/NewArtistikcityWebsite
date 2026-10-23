import React, { useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import { Inertia } from '@inertiajs/inertia';
import { authPost } from '@/Components/Auth/api';

/**
 * Heart button: save a course / workshop (type "course") or a marketplace original (type "painting")
 * to the student's wish list. Guests are sent to log in and come back to this page.
 */
export default function SaveButton({ type, id, saved: initial, iconOnly = false, className = '', onChange }) {
    const { auth } = usePage().props;
    const [saved, setSaved] = useState(!!initial);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState(null);

    const toggle = async () => {
        if (!(auth && auth.user)) {
            const here = window.location.pathname + window.location.search;
            window.location.assign('/login?return_to=' + encodeURIComponent(here));
            return;
        }
        setBusy(true);
        setError(null);
        const next = !saved;
        try {
            await authPost('/wishlist', { type, id, saved: next });
            setSaved(next);
            if (onChange) onChange(next);
            Inertia.reload({ only: ['nav'], preserveScroll: true, preserveState: true }); // header "Your wish list" count
        } catch (e) {
            setError(e.message);
        }
        setBusy(false);
    };

    const label = saved ? 'Saved to wish list' : 'Save to wish list';
    return (
        <>
            <button type="button" onClick={toggle} disabled={busy} aria-pressed={saved}
                    aria-label={iconOnly ? label : undefined} title={iconOnly ? label : undefined}
                    className={`dm-heart ${iconOnly ? 'dm-heart--icon' : ''} ${saved ? 'is-saved' : ''} ${className}`}>
                <i className={`fa ${saved ? 'fa-heart' : 'fa-heart-o'}`} aria-hidden="true"></i>
                {!iconOnly && <span>{label}</span>}
            </button>
            {error && <p className="ac-field__error" role="alert" style={{ marginTop: 6 }}>{error}</p>}
        </>
    );
}
