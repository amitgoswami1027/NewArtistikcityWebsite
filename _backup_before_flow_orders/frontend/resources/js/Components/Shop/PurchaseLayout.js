import React from 'react';
import SiteLayout from '@/Components/Site/SiteLayout';
import { useCountdown, mmss } from '@/Components/Marketplace/shared';

export const STEPS = [
    { n: 1, label: 'Choose format', href: '/purchase/step-1' },
    { n: 2, label: 'See it on your wall', href: '/purchase/step-2' },
    { n: 3, label: 'Review & meet the artist', href: '/purchase/preview' },
    { n: 4, label: 'Checkout', href: '/purchase/checkout' },
];

export function PurchaseStepper({ current, skipRoom = false }) {
    return (
        <nav className="pz-stepper" aria-label="Purchase progress">
            <ol>
                {STEPS.map((s) => {
                    const done = s.n < current;
                    const skipped = skipRoom && s.n === 2;
                    const linkable = done && !skipped && s.n < 4;
                    const inner = (
                        <>
                            <span className="pz-stepper__dot" aria-hidden="true">{done && !skipped ? <i className="fa fa-check"></i> : s.n}</span>
                            <span className="pz-stepper__label">{s.label}{skipped ? ' (not needed)' : ''}</span>
                        </>
                    );
                    return (
                        <li key={s.n} className={`${s.n === current ? 'is-current' : ''} ${done ? 'is-done' : ''} ${skipped ? 'is-skipped' : ''}`}
                            aria-current={s.n === current ? 'step' : undefined}>
                            {linkable ? <a href={s.href}>{inner}</a> : <span>{inner}</span>}
                        </li>
                    );
                })}
            </ol>
        </nav>
    );
}

/** Thin bar shown while the visitor holds the original. */
export function HoldBar({ hold, serverNow }) {
    const left = useCountdown(hold ? hold.expiresAt : null, serverNow);
    if (!hold || left <= 0) return null;
    return (
        <div className={`pz-holdbar ${left <= 120 ? 'is-urgent' : ''}`} role="timer" aria-live={left <= 120 ? 'assertive' : 'off'}>
            <i className="fa fa-lock" aria-hidden="true"></i>
            <span><b>The original is held for you.</b> It goes back to the gallery in</span>
            <span className="pz-holdbar__time">{mmss(left)}</span>
        </div>
    );
}

export default function PurchaseLayout({ painting, step, skipRoom, hold, serverNow, title, children }) {
    return (
        <SiteLayout title={`${title} · ${painting.title}`}>
            <HoldBar hold={hold} serverNow={serverNow} />
            <div className="pz">
                <div className="ac-wide">
                    <ul className="dm-crumbs">
                        <li><a href="/">Home</a></li>
                        <li><a href="/marketplace">Marketplace</a></li>
                        <li><a href={`/marketplace/${painting.slug}`}>{painting.title}</a></li>
                        <li>{title}</li>
                    </ul>
                    <header className="pz-head">
                        <p className="pz-eyebrow">Buy “{painting.title}” · Step {step} of 4</p>
                        <h1>{title}</h1>
                    </header>
                    <PurchaseStepper current={step} skipRoom={skipRoom} />
                    <div className="pz-body">{children}</div>
                </div>
            </div>
        </SiteLayout>
    );
}
