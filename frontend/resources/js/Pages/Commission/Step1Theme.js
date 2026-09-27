import React from 'react';
import CommissionLayout from '@/Components/Commission/CommissionLayout';
import WizardNav from '@/Components/Commission/WizardNav';
import ThemeArt from '@/Components/Commission/ThemeArt';
import { THEMES, useCommission } from '@/Components/Commission/CommissionStore';
import { go } from '@/Components/Commission/useStepGuard';

function ThemeStep() {
    const { state, update } = useCommission();
    return (
        <form onSubmit={(e) => { e.preventDefault(); if (state.theme) go('/commission/step-2'); }}>
            <fieldset>
                <legend className="text-2xl font-extrabold tracking-tight text-ink">Choose the style of your painting</legend>
                <p className="mt-2 text-lg text-gray-600">Pick the look you love. You can preview it on your own photo in step 3.</p>
                <div className="mt-6 grid grid-cols-1 md:grid-cols-2 gap-6">
                    {THEMES.map((t) => {
                        const checked = state.theme === t.id;
                        return (
                            <label key={t.id}
                                   className={`group relative block rounded-2xl border-2 bg-white overflow-hidden cursor-pointer transition-shadow focus-within:ring-2 focus-within:ring-offset-2 ${
                                       checked ? 'border-brand shadow-lg' : 'border-gray-200 hover:border-gray-400 hover:shadow-md'}`}>
                                <div className="h-44 sm:h-52 overflow-hidden bg-gray-100">
                                    <ThemeArt theme={t.id} className="transform transition-transform duration-500 group-hover:scale-105" />
                                </div>
                                <div className="flex items-start gap-4 p-5">
                                    <input type="radio" name="theme" value={t.id} checked={checked}
                                           onChange={() => update({ theme: t.id })}
                                           className="mt-1 h-5 w-5 text-brand border-gray-300 focus:ring-brand" />
                                    <span>
                                        <span className="block text-lg font-extrabold text-ink">{t.title}</span>
                                        <span className="block mt-1 text-sm text-gray-600">{t.blurb}</span>
                                    </span>
                                </div>
                                {checked && <span className="absolute top-3 right-3 rounded-full bg-brand text-white text-xs font-bold px-3 py-1">Selected</span>}
                            </label>
                        );
                    })}
                </div>
            </fieldset>
            <WizardNav onNext={() => go('/commission/step-2')} nextDisabled={!state.theme}
                       nextLabel={'Next: Choose Delivery & Size ➔'} hint={!state.theme ? 'Select a theme to continue.' : null} />
        </form>
    );
}

export default function Step1Theme() {
    return <CommissionLayout title="Choose your theme" subtitle="Tell us the style you love. A real artist paints your commission by hand from your photo." step={1}><ThemeStep /></CommissionLayout>;
}
