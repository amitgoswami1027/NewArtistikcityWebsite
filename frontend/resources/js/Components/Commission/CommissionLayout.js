import React from 'react';
import { Head } from '@inertiajs/inertia-react';
import SiteLayout from '@/Components/Site/SiteLayout';
import { CommissionProvider } from '@/Components/Commission/CommissionStore';
import Stepper from '@/Components/Commission/Stepper';
import ThemeArt from '@/Components/Commission/ThemeArt';
import { THEMES } from '@/Components/Commission/CommissionStore';

const reduceMotion = () => {
    try { return window.matchMedia('(prefers-reduced-motion: reduce)').matches; } catch (e) { return false; }
};

/**
 * The landing banner of the Artist Commission Portal (step 1), in the style of the /how-it-works hero: dark ink band,
 * brand glow, mono eyebrow, a three-line headline and two calls to action; on the right, the four painting styles.
 */
export function CommissionHero() {
    const start = (e) => {
        e.preventDefault();               // <base href="/"> would turn "#id" into a link to the home page
        const el = document.getElementById('commission-start');
        if (el) el.scrollIntoView({ behavior: reduceMotion() ? 'auto' : 'smooth', block: 'start' });
    };
    return (
        <section className="relative overflow-hidden bg-ink text-white cm-hero">
            <div className="absolute inset-0 opacity-40" style={{ background: 'radial-gradient(circle at 85% 20%, #9c0d48 0, transparent 45%), radial-gradient(circle at 10% 90%, #3b5bdb 0, transparent 40%)' }} aria-hidden="true"></div>
            <div className="ac-wide relative py-20 sm:py-28 cm-hero__grid">
                <div>
                    <nav aria-label="Breadcrumb" className="text-sm text-gray-400"><a href="/" className="hover:text-white">Home</a> <span className="mx-2" aria-hidden="true">/</span> <span className="text-white">Commission art</span></nav>
                    <p className="mt-6 font-mono text-xs tracking-widest uppercase text-pink-300">The ArtistikCity Artist Commission Portal</p>
                    <h1 className="mt-4 text-5xl sm:text-7xl font-black tracking-tight leading-none text-white">
                        Your photo.<br />Hand-painted.<br /><span className="text-brand">Made for your wall.</span>
                    </h1>
                    <p className="mt-6 max-w-2xl text-xl text-gray-300">Send us a photo you love and an ArtistikCity artist paints it by hand, in the style you choose. Preview it on your own photo, pick the size and frame, and change anything before you pay. Six short steps.</p>
                    <div className="mt-8 flex flex-wrap gap-3">
                        <a href="/commission/step-1#commission-start" onClick={start} className="inline-flex items-center h-12 px-7 rounded-full bg-brand text-white font-bold hover:bg-brand-dark">Start your commission <i className="fa fa-arrow-down ml-2" aria-hidden="true"></i></a>
                        <a href="/marketplace" className="inline-flex items-center h-12 px-7 rounded-full border border-white border-opacity-40 text-white font-bold hover:bg-white hover:text-ink">Browse ready-made originals</a>
                    </div>
                </div>
                <ul className="cm-hero__styles" aria-label="Painting styles you can choose">
                    {THEMES.map((t, i) => (
                        <li key={t.id} className={`cm-hero__frame is-${i}`}>
                            <span className="cm-hero__canvas"><ThemeArt theme={t.id} /></span>
                            <span className="cm-hero__label">{t.title}</span>
                        </li>
                    ))}
                </ul>
            </div>
        </section>
    );
}

/** Renders a wizard step inside the site's header/footer shell, with the progress stepper. Step 1 opens with the banner. */
export default function CommissionLayout({ title, subtitle, step, children, hero = false }) {
    if (hero) {
        return (
            <SiteLayout title={`${title} · Artist Commission Portal`}>
                <CommissionProvider>
                    <div className="tw"><CommissionHero /></div>
                    <div className="tw">
                        <div className="ac-wide">
                            <div className="pt-10 cm-start" id="commission-start">
                                <p className="font-mono text-xs tracking-widest uppercase text-brand">Step {step} of 6</p>
                                <h2 className="mt-2 text-4xl font-black tracking-tight text-ink">{title}</h2>
                                {subtitle && <p className="mt-2 text-lg text-gray-600">{subtitle}</p>}
                            </div>
                            <div className="border-b border-gray-200 pb-6 mt-4"><Stepper current={step} /></div>
                            <div className="py-10">{children}</div>
                        </div>
                    </div>
                </CommissionProvider>
            </SiteLayout>
        );
    }
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
