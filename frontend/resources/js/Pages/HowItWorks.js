import React from 'react';
import { usePage } from '@inertiajs/inertia-react';
import SiteLayout from '@/Components/Site/SiteLayout';
import { JOURNEY } from '@/Components/Studio/Journey';

export default function HowItWorks() {
    const { auth } = usePage().props;
    const signedIn = auth && auth.user;
    return (
        <SiteLayout title="How it works">
            <div className="tw">
                {/* ---------- hero ---------- */}
                <section className="relative overflow-hidden bg-ink text-white">
                    <div className="absolute inset-0 opacity-40" style={{ background: 'radial-gradient(circle at 85% 20%, #e5156b 0, transparent 45%), radial-gradient(circle at 10% 90%, #3b5bdb 0, transparent 40%)' }} aria-hidden="true"></div>
                    <div className="ac-wide relative py-20 sm:py-28">
                        <p className="font-mono text-xs tracking-widest uppercase text-pink-300">The ArtistikCity creative lifecycle</p>
                        <h1 className="mt-4 text-5xl sm:text-7xl font-black tracking-tight leading-none text-white">
                            Learn.<br />Demonstrate.<br /><span className="text-brand">Exhibit &amp; Sell.</span>
                        </h1>
                        <p className="mt-6 max-w-2xl text-xl text-gray-300">From your first guided lesson to a finished piece in your public portfolio, with the option to sell it. Six clear steps, one studio.</p>
                        <div className="mt-8 flex flex-wrap gap-3">
                            <a href={signedIn ? '/dashboard' : '/join'} className="inline-flex items-center h-12 px-7 rounded-full bg-brand text-white font-bold hover:bg-brand-dark">{signedIn ? 'Open my studio' : 'Start for free'}</a>
                            <a href="/courses?type=all" className="inline-flex items-center h-12 px-7 rounded-full border border-white border-opacity-40 text-white font-bold hover:bg-white hover:text-ink">Browse courses</a>
                        </div>
                    </div>
                </section>

                {/* ---------- 6-step roadmap ---------- */}
                <section className="ac-wide py-20">
                    <div className="max-w-2xl">
                        <p className="font-mono text-xs tracking-widest uppercase text-brand">Roadmap</p>
                        <h2 className="mt-2 text-4xl font-black tracking-tight text-ink">Six steps from curious to exhibited</h2>
                        <p className="mt-3 text-lg text-gray-600">Each step unlocks the next. Your studio shows exactly where you are.</p>
                    </div>
                    <ol className="mt-12 grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                        {JOURNEY.map((s) => (
                            <li key={s.key} className="group relative flex flex-col rounded-2xl border border-gray-200 bg-white p-8 hover:border-ink hover:shadow-xl transition">
                                <span className="text-6xl font-black tracking-tight text-gray-200 group-hover:text-brand transition-colors">{s.n}</span>
                                <span className="mt-4 font-mono text-xs tracking-widest uppercase text-gray-500">Step {s.n} · {s.key.replace('_', ' ')}</span>
                                <h3 className="mt-2 text-2xl font-black tracking-tight text-ink">{s.title}</h3>
                                <p className="mt-2 text-base text-gray-600 flex-1">{s.text}</p>
                                <a href={s.href} className="mt-6 inline-flex items-center gap-2 font-bold text-ink hover:text-brand">
                                    {s.cta} <i className="fa fa-arrow-right text-sm" aria-hidden="true"></i>
                                </a>
                            </li>
                        ))}
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
                                ['fa-picture-o', 'Curated portfolio', 'Approved pieces form a portfolio you can share with anyone.'],
                                ['fa-inr', 'Sell your work', 'Set a price and stock for approved pieces and list them in the student shop.'],
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

                <section className="ac-wide py-16 text-center">
                    <h2 className="text-3xl font-black tracking-tight text-ink">See what students are selling</h2>
                    <p className="mt-2 text-lg text-gray-600">Original, reviewed artwork from ArtistikCity learners.</p>
                    <a href="/student-shop" className="mt-6 inline-flex items-center h-12 px-7 rounded-full bg-ink text-white font-bold hover:bg-gray-800">Visit the student shop</a>
                </section>
            </div>
        </SiteLayout>
    );
}
