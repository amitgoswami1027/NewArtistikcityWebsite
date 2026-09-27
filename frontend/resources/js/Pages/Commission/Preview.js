import React from 'react';
import { usePage } from '@inertiajs/inertia-react';
import CommissionLayout from '@/Components/Commission/CommissionLayout';
import FramedArtwork from '@/Components/Commission/FramedArtwork';
import TextureCanvas from '@/Components/Commission/TextureCanvas';
import OrderSummary from '@/Components/Commission/OrderSummary';
import { useCommission, dimensions, isDigital, quote } from '@/Components/Commission/CommissionStore';
import useStepGuard, { go } from '@/Components/Commission/useStepGuard';

function Review() {
    const { pricing } = usePage().props;
    const state = useStepGuard(5);
    const [w, h] = dimensions(state.size, state.orientation);
    const digital = isDigital(state);
    const q = quote(state, pricing);

    return (
        <div className="grid grid-cols-1 lg:grid-cols-5 gap-8 items-start">
            <div className="lg:col-span-3">
                {digital ? (
                    <div className="rounded-2xl p-8 sm:p-12" style={{ background: 'linear-gradient(180deg,#f4f1ec,#e7e2d9)' }}>
                        <div className="mx-auto bg-white rounded-xl shadow-xl overflow-hidden" style={{ maxWidth: w >= h ? 560 : 400 }}>
                            <div className="flex items-center gap-2 px-4 py-2 border-b border-gray-100 text-xs text-gray-500">
                                <i className="fa fa-file-image-o" aria-hidden="true"></i> artistikcity-commission-{w}x{h}-300dpi.png
                            </div>
                            <div className="relative bg-white" style={{ aspectRatio: `${w} / ${h}` }}>
                                <TextureCanvas src={state.photoUrl} theme={state.theme} intensity={state.intensity} className="absolute inset-0 w-full h-full object-cover" />
                            </div>
                        </div>
                    </div>
                ) : (
                    <FramedArtwork photoUrl={state.photoUrl} theme={state.theme} intensity={state.intensity}
                                   frame={state.frameMaterial} matting={state.hasMatting} widthIn={w} heightIn={h} />
                )}
            </div>
            <aside className="lg:col-span-2 lg:sticky lg:top-24">
                <div className="rounded-2xl border border-gray-200 bg-white p-6 shadow-sm">
                    <h2 className="text-2xl font-extrabold tracking-tight text-ink">Your commission</h2>
                    <div className="mt-4"><OrderSummary state={state} q={q} /></div>
                    <p className="mt-6 rounded-xl bg-yellow-50 border border-yellow-200 p-4 text-sm text-yellow-900">
                        Your artist will paint your commission by hand using these exact configurations. A digital draft proof will be sent to your email for formal sign-off before printing or shipping.
                    </p>
                    <div className="mt-6 flex flex-col gap-3">
                        <button type="button" onClick={() => go('/commission/checkout')}
                                className="h-12 rounded-full bg-brand text-white font-bold shadow-md hover:bg-brand-dark focus:outline-none focus:ring-2 focus:ring-offset-2">
                            Proceed to Secure Payment {'➔'}
                        </button>
                        <button type="button" onClick={() => go(digital ? '/commission/step-3' : '/commission/step-4')}
                                className="h-12 rounded-full border border-gray-300 bg-white font-bold text-ink hover:border-ink focus:outline-none focus:ring-2 focus:ring-offset-2">
                            {'⬅'} Change Settings
                        </button>
                    </div>
                </div>
            </aside>
        </div>
    );
}

export default function Preview() {
    return <CommissionLayout title="Review your order" subtitle="Check every detail before you pay. You can still change anything." step={5}><Review /></CommissionLayout>;
}
