import React, { useEffect } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import CommissionLayout from '@/Components/Commission/CommissionLayout';
import WizardNav from '@/Components/Commission/WizardNav';
import FramedArtwork from '@/Components/Commission/FramedArtwork';
import { FRAMES, SIZES, useCommission, dimensions, isDigital, usd } from '@/Components/Commission/CommissionStore';
import useStepGuard, { go } from '@/Components/Commission/useStepGuard';

function FrameStep() {
    const { pricing } = usePage().props;
    const state = useStepGuard(4);
    const { update } = useCommission();
    const [w, h] = dimensions(state.size, state.orientation);
    const i = SIZES.indexOf(state.size);

    // Digital files have no frame: skip straight to the review.
    useEffect(() => { if (isDigital(state)) go('/commission/preview', true); }, []);
    if (isDigital(state)) return null;

    return (
        <div className="flex flex-col lg:flex-row gap-8">
            <div className="lg:w-7/12">
                <div className="lg:sticky lg:top-24">
                    <FramedArtwork photoUrl={state.photoUrl} theme={state.theme} intensity={state.intensity}
                                   frame={state.frameMaterial} matting={state.hasMatting} widthIn={w} heightIn={h} />
                    <p className="mt-3 text-center text-sm text-gray-500">{w}" × {h}" · {FRAMES.find((f) => f.id === state.frameMaterial).title}{state.hasMatting ? ' · 1" white mat' : ''}</p>
                </div>
            </div>
            <div className="lg:w-5/12 space-y-8">
                <fieldset>
                    <legend className="text-2xl font-extrabold tracking-tight text-ink">Frame type</legend>
                    <div className="mt-4 space-y-3">
                        {FRAMES.map((f) => {
                            const checked = state.frameMaterial === f.id;
                            const price = pricing && i >= 0 ? pricing.frames[f.id][i] : 0;
                            return (
                                <label key={f.id} className={`flex items-center gap-4 rounded-xl border-2 bg-white p-4 cursor-pointer focus-within:ring-2 focus-within:ring-offset-2 ${checked ? 'border-brand' : 'border-gray-200 hover:border-gray-400'}`}>
                                    <input type="radio" name="frame" value={f.id} checked={checked} onChange={() => update({ frameMaterial: f.id })}
                                           className="h-5 w-5 text-brand border-gray-300 focus:ring-brand" />
                                    <span className={`w-10 h-10 rounded flex-none border border-gray-200 ${f.id === 'MATTE_BLACK' ? 'bg-gray-900' : f.id === 'GALLERY_WHITE' ? 'bg-white' : f.id === 'WARM_WALNUT' ? 'bg-yellow-900' : 'bg-gray-100'}`} aria-hidden="true"></span>
                                    <span className="flex-1">
                                        <span className="block font-bold text-ink">{f.title}</span>
                                        <span className="block text-sm text-gray-600">{f.blurb}</span>
                                    </span>
                                    <span className="text-sm font-bold text-ink whitespace-nowrap">{price ? `+${usd(price)}` : 'Included'}</span>
                                </label>
                            );
                        })}
                    </div>
                </fieldset>
                <fieldset>
                    <legend className="text-2xl font-extrabold tracking-tight text-ink">Mount accent</legend>
                    <label className="mt-4 flex items-center gap-4 rounded-xl border-2 border-gray-200 bg-white p-4 cursor-pointer hover:border-gray-400 focus-within:ring-2 focus-within:ring-offset-2">
                        <input type="checkbox" checked={state.hasMatting} onChange={(e) => update({ hasMatting: e.target.checked })}
                               className="h-5 w-5 rounded text-brand border-gray-300 focus:ring-brand" />
                        <span className="flex-1">
                            <span className="block font-bold text-ink">Add a 1-inch white matting board</span>
                            <span className="block text-sm text-gray-600">A classic gallery border between the artwork and the frame.</span>
                        </span>
                        <span className="text-sm font-bold text-ink whitespace-nowrap">+{usd(pricing && i >= 0 ? pricing.matting[i] : 0)}</span>
                    </label>
                </fieldset>
            </div>
        </div>
    );
}

export default function Step4Frame() {
    return (
        <CommissionLayout title="The framing workshop" subtitle="Choose a frame and mat. The preview updates as you go." step={4}>
            <FrameStep />
            <WizardNav onBack={() => go('/commission/step-3')} onNext={() => go('/commission/preview')} nextLabel={'Next: Review Order ➔'} />
        </CommissionLayout>
    );
}
