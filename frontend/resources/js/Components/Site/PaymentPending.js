import React, { useEffect, useState } from 'react';
import { Inertia } from '@inertiajs/inertia';

/*
 * Payment confirmed late (finding 3): while an order still waits for the bank, the page says so and re-checks every
 * 10 seconds for up to 10 minutes (only the order prop is reloaded). The scheduled gateway check on the server
 * completes the order even if the browser never comes back, so after 10 minutes the visitor is told it continues
 * without them.
 */
export function usePaymentPolling(pending, only = ['order'], everyMs = 10000, forMs = 600000) {
    const [elapsed, setElapsed] = useState(0);
    useEffect(() => {
        if (!pending) return undefined;
        const started = Date.now();
        const t = setInterval(() => {
            const e = Date.now() - started;
            setElapsed(e);
            if (e >= forMs) { clearInterval(t); return; }
            Inertia.reload({ only, preserveScroll: true, preserveState: true });
        }, everyMs);
        return () => clearInterval(t);
    }, [pending]);
    return { elapsed, gaveUp: elapsed >= forMs };
}

export default function PaymentPending({ pending, only, email }) {
    const { gaveUp } = usePaymentPolling(pending, only);
    if (!pending) return null;
    return (
        <div className="ac-pending" role="status" aria-live="polite">
            {gaveUp ? (
                <p><b>Still waiting for the bank.</b> We keep checking with the payment provider in the background - you'll get an e-mail{email ? ` at ${email}` : ''} the moment it's confirmed. You can close this page.</p>
            ) : (
                <p><span className="ac-pending__dot" aria-hidden="true"></span><b>We're confirming your payment with the bank.</b> This page updates by itself, usually within a minute. Please don't pay again.</p>
            )}
        </div>
    );
}
