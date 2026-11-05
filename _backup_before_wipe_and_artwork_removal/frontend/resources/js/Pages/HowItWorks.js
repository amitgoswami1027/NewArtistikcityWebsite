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
                        <p className="mt-6 max-w-2xl text-xl text-gray-300">From your first guided lesson to a finished piece in your public portfolio, with the option to sell it and a certificate at the end. Seven clear steps, one studio.</p>
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
                        <h2 className="mt-2 text-4xl font-black tracking-tight text-ink">Seven steps from curious to certified</h2>
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
                                ['fa-inr', 'Sell your originals', 'List approved pieces as 1-of-1 originals in the ArtistikCity Marketplace after a quick moderator review.'],
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
                            <p className="font-mono text-xs tracking-widest uppercase text-brand">Step 07 · Certify &amp; showcase</p>
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
                    <h2 className="text-3xl font-black tracking-tight text-ink">See what students are selling</h2>
                    <p className="mt-2 text-lg text-gray-600">Reviewed, 1-of-1 originals from ArtistikCity learners, sold beside studio work in the marketplace.</p>
                    <a href="/marketplace?source=student" className="mt-6 inline-flex items-center h-12 px-7 rounded-full bg-ink text-white font-bold hover:bg-gray-800">Browse student originals</a>
                </section>
            </div>
        </SiteLayout>
    );
}
