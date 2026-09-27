import React from 'react';
import { THEMES, FULFILLMENTS, FRAMES, labelOf, dimensions, isDigital, usd, inr } from '@/Components/Commission/CommissionStore';

/** Itemised configuration + price breakdown used on the review and checkout pages. */
export default function OrderSummary({ state, q, currency = 'USD', compact = false }) {
    const [w, h] = dimensions(state.size, state.orientation);
    const digital = isDigital(state);
    const fmt = (n) => (currency === 'INR' ? inr(n) : usd(n));
    const conv = (n) => (currency === 'INR' && q ? Math.round(n * (q.totalInr / q.totalUsd)) : n);
    const rows = [
        ['Theme', labelOf(THEMES, state.theme)],
        ['Delivery', labelOf(FULFILLMENTS, state.fulfillmentType)],
        ['Size', `${w}" × ${h}" (${state.orientation === 'LANDSCAPE' ? 'landscape' : 'portrait'})`],
        ...(!digital ? [['Frame', labelOf(FRAMES, state.frameMaterial)], ['Matting', state.hasMatting ? '1" white mat' : 'None']] : []),
        ['Reference photo', state.photoName || 'Uploaded'],
    ];
    return (
        <div>
            <dl className="divide-y divide-gray-100">
                {rows.map(([k, v]) => (
                    <div key={k} className="flex justify-between gap-4 py-2.5 text-sm">
                        <dt className="text-gray-500">{k}</dt>
                        <dd className="font-semibold text-ink text-right break-all">{v}</dd>
                    </div>
                ))}
                {!compact && state.instructions && (
                    <div className="py-2.5 text-sm">
                        <dt className="text-gray-500">Special instructions</dt>
                        <dd className="mt-1 text-ink whitespace-pre-line">{state.instructions}</dd>
                    </div>
                )}
            </dl>
            {q && (
                <dl className="mt-4 pt-4 border-t border-gray-200 space-y-1.5 text-sm">
                    <div className="flex justify-between"><dt className="text-gray-500">Artwork</dt><dd>{fmt(conv(q.artwork))}</dd></div>
                    {!digital && <div className="flex justify-between"><dt className="text-gray-500">Frame</dt><dd>{q.frame ? fmt(conv(q.frame)) : 'Included'}</dd></div>}
                    {!digital && state.hasMatting && <div className="flex justify-between"><dt className="text-gray-500">Matting</dt><dd>{fmt(conv(q.matting))}</dd></div>}
                    <div className="flex justify-between"><dt className="text-gray-500">{digital ? 'Delivery' : 'Shipping'}</dt><dd>{q.shipping ? fmt(conv(q.shipping)) : 'Free'}</dd></div>
                    <div className="flex justify-between pt-3 mt-2 border-t border-gray-200 text-lg font-extrabold text-ink">
                        <dt>Total</dt><dd>{currency === 'INR' ? inr(q.totalInr) : usd(q.totalUsd)}</dd>
                    </div>
                    {currency === 'USD' && <p className="text-xs text-gray-500 text-right">≈ {inr(q.totalInr)} when paying with UPI / card in India</p>}
                </dl>
            )}
        </div>
    );
}
