import React, { useEffect, useRef, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import SiteLayout from '@/Components/Site/SiteLayout';
import { JOURNEY, SELL_STEP, JOURNEY_ICONS } from '@/Components/Studio/Journey';

/*
 * How it works (/how-it-works): the seven-step student journey, Discover > Join > Choose a course > Learn > Submit >
 * Certify > Sell. A sticky process tracker at the top follows the reader down a zigzag timeline (one column on phones)
 * and jumps to any step. Step 7 quotes the real marketplace terms from the server (prop `sell`).
 *
 * Note: the root template has <base href="/">, so in-page "#step" links would leave the page; the tracker scrolls in code.
 */

const reduceMotion = () => {
    try { return window.matchMedia('(prefers-reduced-motion: reduce)').matches; } catch (e) { return false; }
};

function scrollToStep(id) {
    const el = typeof document !== 'undefined' && document.getElementById(id);
    if (!el) return;
    const tracker = document.querySelector('.hiw-tracker');
    const offset = 68 + (tracker ? tracker.offsetHeight : 0) + 16;          // site header + tracker + breathing room
    window.scrollTo({ top: el.getBoundingClientRect().top + window.pageYOffset - offset, behavior: reduceMotion() ? 'auto' : 'smooth' });
    try { window.history.replaceState(window.history.state, '', `/how-it-works#${id}`); } catch (e) { /* old browser */ }
}

/* ------------------------------------------------------------- the tracker */

function ProcessTracker({ steps, active }) {
    const listRef = useRef(null);

    // keep the current step in view on narrow screens (scroll the strip only, never the page)
    useEffect(() => {
        const list = listRef.current;
        const node = list && list.children[active];
        if (!list || !node || list.scrollWidth <= list.clientWidth) return;
        list.scrollTo({ left: node.offsetLeft - (list.clientWidth - node.offsetWidth) / 2, behavior: reduceMotion() ? 'auto' : 'smooth' });
    }, [active]);

    return (
        <nav className="hiw-tracker" aria-label="The student journey, step by step">
            <div className="ac-wide hiw-tracker__inner">
                <ol ref={listRef} className="hiw-tracker__list">
                    {steps.map((s, i) => {
                        const state = i < active ? 'is-done' : i === active ? 'is-current' : '';
                        return (
                            <li key={s.key} className={`hiw-node ${state} ${s.key === 'SELL' ? 'is-goal' : ''}`}>
                                <a href={`/how-it-works#${s.id}`} onClick={(e) => { e.preventDefault(); scrollToStep(s.id); }}
                                   aria-current={i === active ? 'step' : undefined} aria-label={`Step ${i + 1} of ${steps.length}: ${s.title}`}>
                                    <span className="hiw-node__dot">
                                        <i className={`fa ${JOURNEY_ICONS[s.key]}`} aria-hidden="true"></i>
                                        <b>{i + 1}</b>
                                    </span>
                                    <span className="hiw-node__label">{s.title}</span>
                                </a>
                                {i < steps.length - 1 && <i className="fa fa-angle-right hiw-node__arrow" aria-hidden="true"></i>}
                            </li>
                        );
                    })}
                </ol>
            </div>
        </nav>
    );
}

/* ----------------------------------------------------------- one step card */

function StepCard({ step, index, total }) {
    const side = index % 2 === 0 ? 'is-left' : 'is-right';
    return (
        <li id={step.id} className={`hiw-step ${side}`} data-step={index}>
            <span className="hiw-step__node" aria-hidden="true"><i className={`fa ${JOURNEY_ICONS[step.key]}`}></i></span>
            <article className="hiw-card">
                <span className="hiw-card__num" aria-hidden="true">{step.n}</span>
                <p className="hiw-card__eyebrow">Step {index + 1} of {total}</p>
                <h3 className="hiw-card__title">{step.title}</h3>
                <p className="hiw-card__text">{step.text}</p>
                <a href={step.href} className="hiw-card__cta">
                    {step.cta} <i className="fa fa-arrow-right" aria-hidden="true"></i>
                </a>
            </article>
        </li>
    );
}

/** Step 7: the finale, full width. Terms come from the server so the promise always matches the payouts. */
function SellStep({ step, index, total, sell, signedIn }) {
    const perks = [
        ['fa-percent', `You keep ${sell.artistPercent}%`, 'of every sale. Our share covers the listing, secure checkout and payment handling.'],
        ['fa-clock-o', `Payable ${sell.payoutDays} days after the sale`, 'Then paid out in our weekly payout run.'],
        ['fa-clone', 'One painting, four ways to buy', 'The one-of-a-kind original, plus numbered canvas editions, fine-art posters and digital downloads.'],
        ['fa-globe', 'Collectors in India and worldwide', 'Rupee payments through Razorpay, international payments through PayPal.'],
    ];
    return (
        <li id={step.id} className="hiw-step is-goal" data-step={index}>
            <span className="hiw-step__node" aria-hidden="true"><i className={`fa ${JOURNEY_ICONS.SELL}`}></i></span>
            <article className="hiw-goal">
                <div className="hiw-goal__copy">
                    <p className="hiw-goal__eyebrow">Step {index + 1} of {total} · The finish line</p>
                    <h3 className="hiw-goal__title">Launch your gallery &amp; sell</h3>
                    <p className="hiw-goal__text">
                        Your certificate proves the skill; your paintings can now earn from it. Once you're certified, your best approved
                        studio pieces can be listed in the ArtistikCity Marketplace, each with its own gallery page: the original for one
                        collector, and prints and downloads for many more. Our team reviews every piece before it goes live, and we run
                        the checkout, payments and collector messages for you, so you can keep painting while your practice grows into a
                        sustainable art career.
                    </p>
                    <div className="hiw-goal__actions">
                        <a href={step.href} className="hiw-btn hiw-btn--light">{step.cta} <i className="fa fa-arrow-right" aria-hidden="true"></i></a>
                        <a href={signedIn ? '/dashboard' : '/join'} className="hiw-btn hiw-btn--ghost">{signedIn ? 'Back to my studio' : 'Start creating'}</a>
                    </div>
                    {Number(sell.listed) > 0 && (
                        <p className="hiw-goal__note"><i className="fa fa-picture-o" aria-hidden="true"></i> {Number(sell.listed).toLocaleString('en-IN')} artworks on sale right now</p>
                    )}
                </div>
                <ul className="hiw-goal__perks">
                    {perks.map(([icon, title, text]) => (
                        <li key={title}>
                            <i className={`fa ${icon}`} aria-hidden="true"></i>
                            <span><b>{title}</b>{text}</span>
                        </li>
                    ))}
                </ul>
            </article>
        </li>
    );
}

/* -------------------------------------------------------------------- page */

export default function HowItWorks() {
    const { auth, sell = { artistPercent: '70', payoutDays: 14, listed: 0 } } = usePage().props;
    const signedIn = auth && auth.user;
    const steps = [
        ...JOURNEY.map((s) => ({ ...s, id: `step-${s.key.toLowerCase().replace('_', '-')}` })),
        { ...SELL_STEP, id: 'step-sell' },
    ];
    const [active, setActive] = useState(0);
    const timeline = useRef(null);

    // the step whose card crosses the upper third of the screen is the current one
    useEffect(() => {
        const root = timeline.current;
        if (!root || typeof IntersectionObserver === 'undefined') return undefined;
        const seen = new Map();
        const io = new IntersectionObserver((entries) => {
            entries.forEach((e) => seen.set(Number(e.target.dataset.step), e.isIntersecting));
            const on = [...seen.entries()].filter(([, v]) => v).map(([k]) => k);
            if (on.length) setActive(Math.max(...on));
        }, { rootMargin: '-30% 0px -60% 0px' });
        root.querySelectorAll('[data-step]').forEach((el) => io.observe(el));
        return () => io.disconnect();
    }, []);

    // arriving with /how-it-works#step-sell (e.g. from the studio) opens that step
    useEffect(() => {
        const id = typeof window !== 'undefined' ? window.location.hash.replace('#', '') : '';
        if (id && steps.some((s) => s.id === id)) setTimeout(() => scrollToStep(id), 150);
    }, []);

    return (
        <SiteLayout title="How it works">
            <ProcessTracker steps={steps} active={active} />
            <div className="tw">
                {/* ---------- hero ---------- */}
                <section className="relative overflow-hidden bg-ink text-white">
                    <div className="absolute inset-0 opacity-40" style={{ background: 'radial-gradient(circle at 85% 20%, #9c0d48 0, transparent 45%), radial-gradient(circle at 10% 90%, #3b5bdb 0, transparent 40%)' }} aria-hidden="true"></div>
                    <div className="ac-wide relative py-20 sm:py-28">
                        <p className="font-mono text-xs tracking-widest uppercase text-pink-300">The ArtistikCity creative lifecycle</p>
                        <h1 className="mt-4 text-5xl sm:text-7xl font-black tracking-tight leading-none text-white">
                            Learn.<br />Get certified.<br /><span className="text-brand">Sell your art.</span>
                        </h1>
                        <p className="mt-6 max-w-2xl text-xl text-gray-300">From your first guided lesson to a verifiable certificate, and from there to your paintings in front of collectors. Seven clear steps, one studio.</p>
                        <div className="mt-8 flex flex-wrap gap-3">
                            <a href={signedIn ? '/dashboard' : '/join'} className="inline-flex items-center h-12 px-7 rounded-full bg-brand text-white font-bold hover:bg-brand-dark">{signedIn ? 'Open my studio' : 'Start for free'}</a>
                            <a href="/courses?type=all" className="inline-flex items-center h-12 px-7 rounded-full border border-white border-opacity-40 text-white font-bold hover:bg-white hover:text-ink">Browse courses</a>
                        </div>
                    </div>
                </section>

                {/* ---------- 7-step timeline ---------- */}
                <section className="ac-wide py-20" aria-labelledby="hiw-roadmap">
                    <div className="max-w-2xl">
                        <p className="font-mono text-xs tracking-widest uppercase text-brand">Roadmap</p>
                        <h2 id="hiw-roadmap" className="mt-2 text-4xl font-black tracking-tight text-ink">Seven steps from curious to collected</h2>
                        <p className="mt-3 text-lg text-gray-600">Each step unlocks the next, and the last one turns your practice into income. Your studio always shows where you are.</p>
                    </div>
                    <ol ref={timeline} className="hiw-timeline">
                        {steps.map((s, i) => (s.key === 'SELL'
                            ? <SellStep key={s.key} step={s} index={i} total={steps.length} sell={sell} signedIn={signedIn} />
                            : <StepCard key={s.key} step={s} index={i} total={steps.length} />))}
                    </ol>
                </section>

                {/* ---------- what you get ---------- */}
                <section className="bg-gray-50 border-t border-gray-200">
                    <div className="ac-wide py-20 grid grid-cols-1 lg:grid-cols-3 gap-10">
                        <div>
                            <p className="font-mono text-xs tracking-widest uppercase text-brand">Why it works</p>
                            <h2 className="mt-2 text-3xl font-black tracking-tight text-ink">Real feedback, real outcomes</h2>
                        </div>
                        <div className="lg:col-span-2 grid grid-cols-1 sm:grid-cols-2 gap-6">
                            {[
                                ['fa-video-camera', 'Guided classroom', 'Structured units and lessons with progress tracking and study material.'],
                                ['fa-comments-o', 'Milestone reviews', 'Every submission gets personal notes from a reviewer before it is approved.'],
                                ['fa-certificate', 'Verifiable certificates', 'Finish a course with approved work to earn a certificate anyone can check online.'],
                                ['fa-shopping-bag', 'A marketplace for your work', `Approved pieces can be sold as originals, prints and downloads, and you keep ${sell.artistPercent}% of each sale.`],
                            ].map(([icon, t, d]) => (
                                <div key={t} className="rounded-2xl bg-white border border-gray-200 p-6">
                                    <i className={`fa ${icon} text-2xl text-brand`} aria-hidden="true"></i>
                                    <h3 className="mt-3 text-lg font-extrabold text-ink">{t}</h3>
                                    <p className="mt-1 text-gray-600">{d}</p>
                                </div>
                            ))}
                        </div>
                    </div>
                </section>

                {/* ---------- certificates & portfolio report ---------- */}
                <section className="ac-wide py-20">
                    <div className="grid grid-cols-1 lg:grid-cols-2 gap-12 items-center">
                        <div>
                            <p className="font-mono text-xs tracking-widest uppercase text-brand">Step 06 · Certify</p>
                            <h2 className="mt-2 text-4xl font-black tracking-tight text-ink">Your work, on paper</h2>
                            <p className="mt-3 text-lg text-gray-600">Finish every lesson and get one assignment approved to earn a verifiable ArtistikCity certificate for that course. Then generate a detailed, print-ready PDF portfolio of everything you've made.</p>
                            <ul className="mt-6 space-y-3 text-base text-gray-700">
                                <li className="flex gap-3"><i className="fa fa-certificate text-brand mt-1" aria-hidden="true"></i><span><strong className="text-ink">Course certificates</strong> with a unique number anyone can verify online</span></li>
                                <li className="flex gap-3"><i className="fa fa-file-pdf-o text-brand mt-1" aria-hidden="true"></i><span><strong className="text-ink">Portfolio PDF</strong> with every course, its full curriculum and your progress</span></li>
                                <li className="flex gap-3"><i className="fa fa-comments-o text-brand mt-1" aria-hidden="true"></i><span><strong className="text-ink">Instructor comments</strong> printed beside each approved artwork</span></li>
                                <li className="flex gap-3"><i className="fa fa-print text-brand mt-1" aria-hidden="true"></i><span><strong className="text-ink">Print or share any time</strong> for applications, exhibitions or school</span></li>
                            </ul>
                            <a href={signedIn ? '/dashboard/certificates' : '/join'} className="mt-8 inline-flex items-center h-12 px-7 rounded-full bg-ink text-white font-bold hover:bg-gray-800">{signedIn ? 'Open certificates & reports' : 'Join and start earning'}</a>
                        </div>
                        <div className="relative h-96" aria-hidden="true">
                            <div className="absolute left-0 top-6 w-3/4 rounded-lg bg-white shadow-2xl border border-gray-200 p-6 transform -rotate-3" style={{ aspectRatio: '297 / 210' }}>
                                <div className="h-full border-4 border-ink p-4 relative">
                                    <div className="absolute inset-y-0 left-0 w-3 bg-brand"></div>
                                    <p className="pl-5 font-mono text-xs tracking-widest uppercase text-brand">Certificate of completion</p>
                                    <p className="pl-5 mt-2 text-2xl font-black text-ink">Course Certificate</p>
                                    <p className="pl-5 mt-3 text-xs text-gray-500">This certifies that</p>
                                    <p className="pl-5 text-lg font-bold text-ink border-b border-ink inline-block">Your name</p>
                                    <p className="pl-5 mt-3 font-mono text-xs text-gray-500">AC-2026-XXXXXXXX</p>
                                </div>
                            </div>
                            <div className="absolute right-0 bottom-0 w-1/2 rounded-lg bg-ink shadow-2xl p-5 text-white transform rotate-2" style={{ aspectRatio: '210 / 297' }}>
                                <div className="h-full border-l-4 border-brand pl-4 flex flex-col">
                                    <p className="font-mono text-xs tracking-widest uppercase text-pink-300 mt-8">Artist portfolio</p>
                                    <p className="mt-2 text-2xl font-black leading-tight">Your name</p>
                                    <div className="mt-auto grid grid-cols-2 gap-2 text-xs text-gray-300">
                                        <span><b className="block text-xl text-white">3</b>Courses</span><span><b className="block text-xl text-white">12</b>Artworks</span>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </section>

                <section className="ac-wide py-16 text-center">
                    <h2 className="text-3xl font-black tracking-tight text-ink">Ready to start?</h2>
                    <p className="mt-2 text-lg text-gray-600">Pick a live course or a weekend workshop in the medium you love, or see what our artists are selling.</p>
                    <div className="mt-6 flex flex-wrap justify-center gap-3">
                        <a href="/courses?type=all" className="inline-flex items-center h-12 px-7 rounded-full bg-ink text-white font-bold hover:bg-gray-800">Browse courses</a>
                        <a href="/marketplace" className="inline-flex items-center h-12 px-7 rounded-full border border-gray-200 text-ink font-bold hover:border-ink">Explore the Marketplace</a>
                    </div>
                </section>
            </div>
        </SiteLayout>
    );
}
