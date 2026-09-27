import React from 'react';
import { useCommission, isDigital } from '@/Components/Commission/CommissionStore';

const STEPS = ['Theme', 'Size & delivery', 'Photo', 'Frame', 'Review', 'Checkout'];

export default function Stepper({ current }) {
    const { state } = useCommission();
    const skipFrame = isDigital(state);
    return (
        <nav aria-label="Commission progress" className="py-3 overflow-x-auto">
            <ol className="flex items-center gap-2 whitespace-nowrap">
                {STEPS.map((label, i) => {
                    const n = i + 1;
                    const skipped = n === 4 && skipFrame;
                    const done = n < current;
                    const active = n === current;
                    return (
                        <li key={label} className={`flex items-center gap-2 rounded-full pl-1.5 pr-4 py-1.5 ${active ? 'bg-ink' : ''}`}>
                            <span
                                aria-current={active ? 'step' : undefined}
                                className={`flex items-center justify-center w-8 h-8 rounded-full text-sm font-bold transition-colors ${
                                    active ? 'bg-brand text-white' : done ? 'bg-ink text-white' : 'bg-white border border-gray-300 text-gray-500'
                                } ${skipped ? 'opacity-40' : ''}`}
                            >
                                {done && !skipped ? <i className="fa fa-check" aria-hidden="true"></i> : n}
                            </span>
                            <span className={`text-sm font-semibold ${active ? 'text-white' : done ? 'text-ink' : 'text-gray-500'} ${skipped ? 'line-through' : ''}`}>
                                {label}{skipped ? ' (skipped)' : ''}
                            </span>
                            {n < STEPS.length && !active && <span className="hidden sm:block w-5 h-px bg-gray-300 ml-2" aria-hidden="true"></span>}
                        </li>
                    );
                })}
            </ol>
        </nav>
    );
}
