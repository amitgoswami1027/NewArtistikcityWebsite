import React, { useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import { Inertia } from '@inertiajs/inertia';
import { authPost } from '@/Components/Auth/api';
import { showUndo } from '@/Components/Site/UndoToast';

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
        setError(null);
        const next = !saved;
        // optimistic: the heart changes at once; a refusal puts it back and says why; removing offers Undo
        const send = async (value) => {
            setSaved(value);
            setBusy(true);
            try {
                await authPost('/wishlist', { type, id, saved: value });
                if (onChange) onChange(value);
                Inertia.reload({ only: ['nav'], preserveScroll: true, preserveState: true }); // header "Your wish list" count
                setBusy(false);
                return true;
            } catch (e) {
                setSaved(!value);
                setError(e.message);
                setBusy(false);
                return false;
            }
        };
        if (await send(next) && !next) showUndo('Removed from your wish list.', () => send(true));
    };

    const label = saved ? 'Saved to wish list' : 'Save to wish list';
    return (
        <>
            <button type="button" onClick={toggle} aria-busy={busy} aria-pressed={saved}
                    aria-label={iconOnly ? label : undefined} title={iconOnly ? label : undefined}
                    className={`dm-heart ${iconOnly ? 'dm-heart--icon' : ''} ${saved ? 'is-saved' : ''} ${className}`}>
                <i className={`fa ${saved ? 'fa-heart' : 'fa-heart-o'}`} aria-hidden="true"></i>
                {!iconOnly && <span>{label}</span>}
            </button>
            {error && <p className="ac-field__error" role="alert" style={{ marginTop: 6 }}>{error}</p>}
        </>
    );
}
