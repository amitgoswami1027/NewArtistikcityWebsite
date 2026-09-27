import React from 'react';
import { Head } from '@inertiajs/inertia-react';
import SiteLayout from '@/Components/Site/SiteLayout';
import { CommissionProvider } from '@/Components/Commission/CommissionStore';
import Stepper from '@/Components/Commission/Stepper';

/** Renders a wizard step inside the site's header/footer shell, with the progress stepper. */
export default function CommissionLayout({ title, subtitle, step, children }) {
    return (
        <SiteLayout title={`${title} · Artist Commission Portal`}>
            <CommissionProvider>
                <div className="ac-wide">
                    <ul className="dm-crumbs">
                        <li><a href="/">Home</a></li>
                        <li><a href="/commission/step-1">Commission art</a></li>
                        <li>{title}</li>
                    </ul>
                    <div className="dm-listhead" style={{ paddingTop: 8 }}>
                        <span className="ac-eyebrow">Artist Commission Portal · Step {step} of 6</span>
                        <h1>{title}</h1>
                        <p>A hand-painted original made from your photo by an ArtistikCity artist. Six short steps, and you can change anything before you pay.</p>
                    </div>
                </div>
                <div className="tw">
                    <div className="ac-wide">
                        <div className="border-b border-gray-200 pb-6"><Stepper current={step} /></div>
                        <div className="py-10">{children}</div>
                    </div>
                </div>
            </CommissionProvider>
        </SiteLayout>
    );
}

/** Distraction-free shell for the checkout: logo + secure badge only, no site navigation. */
export function CheckoutShell({ title, children }) {
    return (
        <div className="ac">
            <Head title={`${title} · ArtistikCity`} />
            <CommissionProvider>
                <div className="tw min-h-screen" style={{ background: "#f6f6f6" }}>
                    <header className="bg-white border-b border-gray-200">
                        <div className="ac-wide h-16 flex items-center justify-between" style={{ height: 68 }}>
                            <a href="/" aria-label="ArtistikCity home"><img src="/assets/images/logo.png" alt="ArtistikCity" style={{ height: 36, width: "auto" }} /></a>
                            <span className="inline-flex items-center gap-2 text-sm font-semibold text-gray-600">
                                <i className="fa fa-lock text-green-600" aria-hidden="true"></i> Secure checkout
                            </span>
                        </div>
                    </header>
                    <main className="ac-wide" style={{ paddingTop: 32, paddingBottom: 72 }}>{children}</main>
                </div>
            </CommissionProvider>
        </div>
    );
}
