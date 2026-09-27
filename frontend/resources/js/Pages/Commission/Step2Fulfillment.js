import React from 'react';
import { usePage } from '@inertiajs/inertia-react';
import CommissionLayout from '@/Components/Commission/CommissionLayout';
import WizardNav from '@/Components/Commission/WizardNav';
import RoomVisualizer from '@/Components/Commission/RoomVisualizer';
import { FULFILLMENTS, SIZES, useCommission, dimensions, quote, usd } from '@/Components/Commission/CommissionStore';
import useStepGuard, { go } from '@/Components/Commission/useStepGuard';

function FulfillmentStep() {
    const { pricing } = usePage().props;
    const state = useStepGuard(2);
    const { update } = useCommission();
    const [w, h] = dimensions(state.size, state.orientation);
    const q = quote(state, pricing);
    const digital = state.fulfillmentType === 'DIGITAL_ONLY';
    const sizePrice = (size) => (state.fulfillmentType ? quote({ ...state, size, frameMaterial: 'NONE', hasMatting: false }, pricing) : null);

    return (
        <div className="flex flex-col lg:flex-row gap-8">
            <div className="lg:w-5/12 space-y-8">
                <fieldset>
                    <legend className="text-xl font-extrabold tracking-tight text-ink">How would you like it delivered?</legend>
                    <div className="mt-4 space-y-3">
                        {FULFILLMENTS.map((f) => {
                            const checked = state.fulfillmentType === f.id;
                            return (
                                <label key={f.id} className={`flex items-start gap-4 rounded-xl border-2 bg-white p-4 cursor-pointer focus-within:ring-2 focus-within:ring-offset-2 ${checked ? 'border-brand' : 'border-gray-200 hover:border-gray-400'}`}>
                                    <input type="radio" name="fulfillment" value={f.id} checked={checked}
                                           onChange={() => update({ fulfillmentType: f.id, ...(f.id === 'DIGITAL_ONLY' ? { frameMaterial: 'NONE', hasMatting: false } : {}) })}
                                           className="mt-1 h-5 w-5 text-brand border-gray-300 focus:ring-brand" />
                                    <span>
                                        <span className="block font-bold text-ink">{f.title}</span>
                                        <span className="block text-sm text-gray-600">{f.blurb}</span>
                                    </span>
                                </label>
                            );
                        })}
                    </div>
                </fieldset>

                {digital && (
                    <div role="alert" className="flex gap-3 rounded-xl border border-blue-200 bg-blue-50 p-4 text-sm text-blue-900">
                        <i className="fa fa-info-circle mt-0.5" aria-hidden="true"></i>
                        <p>Delivery via ultra-high 300 DPI download link. Framing options will be automatically skipped.</p>
                    </div>
                )}

                <fieldset>
                    <legend className="text-xl font-extrabold tracking-tight text-ink">Dimensions</legend>
                    <div className="mt-4 grid grid-cols-2 gap-3">
                        {SIZES.map((s) => {
                            const checked = state.size === s;
                            const p = sizePrice(s);
                            return (
                                <label key={s} className={`relative rounded-xl border-2 bg-white px-4 py-3 cursor-pointer text-center focus-within:ring-2 focus-within:ring-offset-2 ${checked ? 'border-brand' : 'border-gray-200 hover:border-gray-400'}`}>
                                    <input type="radio" name="size" value={s} checked={checked} onChange={() => update({ size: s })} className="sr-only" />
                                    <span className="block text-lg font-extrabold text-ink">{s.replace('x', ' × ')}"</span>
                                    <span className="block text-xs text-gray-500">{p ? `from ${usd(p.totalUsd)}` : 'inches'}</span>
                                </label>
                            );
                        })}
                    </div>
                    <div className="mt-4 inline-flex rounded-full bg-gray-100 p-1" role="radiogroup" aria-label="Orientation">
                        {['PORTRAIT', 'LANDSCAPE'].map((o) => (
                            <button key={o} type="button" role="radio" aria-checked={state.orientation === o} onClick={() => update({ orientation: o })}
                                    className={`px-4 h-9 rounded-full text-sm font-bold ${state.orientation === o ? 'bg-white shadow text-ink' : 'text-gray-500'}`}>
                                <i className={`fa ${o === 'PORTRAIT' ? 'fa-mobile' : 'fa-tablet fa-rotate-90'} mr-2`} aria-hidden="true"></i>{o === 'PORTRAIT' ? 'Portrait' : 'Landscape'}
                            </button>
                        ))}
                    </div>
                </fieldset>

                {q && (
                    <div className="rounded-xl bg-gray-900 text-white p-5 flex items-center justify-between">
                        <span className="text-sm text-gray-300">Estimated total<br /><span className="text-xs">{digital ? 'Digital delivery' : 'Includes shipping'}</span></span>
                        <span className="text-2xl font-extrabold">{usd(q.totalUsd)}</span>
                    </div>
                )}
            </div>

            <div className="lg:w-7/12">
                <div className="lg:sticky lg:top-24">
                    <RoomVisualizer widthIn={w} heightIn={h} theme={state.theme} photoUrl={state.photoUrl} />
                </div>
            </div>
        </div>
    );
}

function Nav() {
    const { state } = useCommission();
    return (
        <WizardNav onBack={() => go('/commission/step-1')} onNext={() => go('/commission/step-3')}
                   nextDisabled={!state.fulfillmentType} nextLabel={'Next: Upload Photo ➔'}
                   hint={!state.fulfillmentType ? 'Choose a delivery option to continue.' : null} />
    );
}

export default function Step2Fulfillment() {
    return (
        <CommissionLayout title="Delivery & size" subtitle="Pick how you want to receive it and see the size on a real wall before you order." step={2}>
            <FulfillmentStep />
            <Nav />
        </CommissionLayout>
    );
}
