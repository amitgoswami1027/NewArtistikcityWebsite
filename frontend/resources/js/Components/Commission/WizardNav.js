import React from 'react';

/** Bottom navigation for a wizard step: secondary back button + primary next button. */
export default function WizardNav({ onBack, backLabel = '⬅ Back', onNext, nextLabel, nextDisabled = false, hint }) {
    return (
        <div className="mt-10 pt-6 border-t border-gray-200 flex flex-col-reverse sm:flex-row sm:items-center sm:justify-between gap-4">
            <div>
                {onBack && (
                    <button type="button" onClick={onBack}
                            className="inline-flex items-center justify-center h-12 px-6 rounded-full border border-gray-300 bg-white font-bold text-ink hover:border-ink focus:outline-none focus:ring-2 focus:ring-offset-2">
                        {backLabel}
                    </button>
                )}
            </div>
            <div className="flex flex-col sm:items-end gap-2">
                <button type="button" onClick={onNext} disabled={nextDisabled}
                        className="inline-flex items-center justify-center h-12 px-7 rounded-full bg-brand text-white font-bold shadow-md hover:bg-brand-dark disabled:bg-gray-300 disabled:shadow-none focus:outline-none focus:ring-2 focus:ring-offset-2">
                    {nextLabel}
                </button>
                {hint && <p className="text-xs text-gray-500">{hint}</p>}
            </div>
        </div>
    );
}
