import React, { useState } from 'react';

const SERIES = [
    { key: 'coursesInr', label: 'Course sales', color: '#3b5bdb' },
    { key: 'commissionsInr', label: 'Commissions', color: '#e5156b' },
];
const inr = (n) => '₹' + Number(n || 0).toLocaleString('en-IN');
const monthLabel = (m) => { const [y, mm] = m.split('-'); return new Date(Number(y), Number(mm) - 1, 1).toLocaleDateString('en-IN', { month: 'short' }); };

/** Stacked monthly INR revenue (single axis). Hover shows the month's breakdown; a table view is provided for screen readers. */
export default function RevenueChart({ data = [] }) {
    const [hover, setHover] = useState(null);
    const W = 720, H = 260, pad = { l: 56, r: 12, t: 16, b: 32 };
    const totals = data.map((d) => SERIES.reduce((a, s) => a + Number(d[s.key] || 0), 0));
    const max = Math.max(1000, ...totals);
    const nice = Math.pow(10, Math.floor(Math.log10(max)));
    const top = Math.ceil(max / nice) * nice;
    const bw = (W - pad.l - pad.r) / Math.max(1, data.length);
    const y = (v) => pad.t + (H - pad.t - pad.b) * (1 - v / top);
    const ticks = [0, top / 2, top];

    return (
        <figure>
            <div className="flex flex-wrap gap-4 text-sm text-gray-700 mb-2">
                {SERIES.map((s) => <span key={s.key} className="inline-flex items-center gap-2"><span className="w-3 h-3 rounded-sm" style={{ background: s.color }}></span>{s.label}</span>)}
            </div>
            <div className="relative">
                <svg viewBox={`0 0 ${W} ${H}`} className="w-full h-auto" role="img" aria-label="Monthly revenue in rupees for the last 12 months">
                    {ticks.map((t) => (
                        <g key={t}>
                            <line x1={pad.l} x2={W - pad.r} y1={y(t)} y2={y(t)} stroke="#e5e7eb" strokeWidth="1" />
                            <text x={pad.l - 8} y={y(t) + 4} textAnchor="end" fontSize="11" fill="#6b7280">{t >= 100000 ? `${(t / 100000).toFixed(1)}L` : t >= 1000 ? `${Math.round(t / 1000)}k` : t}</text>
                        </g>
                    ))}
                    {data.map((d, i) => {
                        let acc = 0;
                        const x = pad.l + i * bw + bw * 0.2;
                        const w = bw * 0.6;
                        return (
                            <g key={d.month} onMouseEnter={() => setHover(i)} onMouseLeave={() => setHover(null)}>
                                <rect x={pad.l + i * bw} y={pad.t} width={bw} height={H - pad.t - pad.b} fill={hover === i ? '#f3f4f6' : 'transparent'} />
                                {SERIES.map((s, si) => {
                                    const v = Number(d[s.key] || 0);
                                    if (!v) return null;
                                    const y0 = y(acc), y1 = y(acc + v);
                                    acc += v;
                                    const isTop = SERIES.slice(si + 1).every((n) => !Number(d[n.key]));
                                    return <rect key={s.key} x={x} y={y1 + (si > 0 ? 1 : 0)} width={w} height={Math.max(0, y0 - y1 - (si > 0 ? 1 : 0))} rx={isTop ? 4 : 0} fill={s.color} />;
                                })}
                                <text x={x + w / 2} y={H - 10} textAnchor="middle" fontSize="11" fill="#6b7280">{monthLabel(d.month)}</text>
                            </g>
                        );
                    })}
                </svg>
                {hover !== null && data[hover] && (
                    <div className="absolute top-2 right-2 rounded-lg bg-white shadow-lg border border-gray-200 px-3 py-2 text-sm pointer-events-none">
                        <p className="font-bold text-ink">{monthLabel(data[hover].month)} {data[hover].month.slice(0, 4)}</p>
                        {SERIES.map((s) => <p key={s.key} className="text-gray-700"><span className="inline-block w-2 h-2 rounded-sm mr-2" style={{ background: s.color }}></span>{s.label}: <span className="font-mono">{inr(data[hover][s.key])}</span></p>)}
                        {Number(data[hover].usd) > 0 && <p className="text-gray-500 font-mono">+ ${data[hover].usd} USD</p>}
                    </div>
                )}
            </div>
            <details className="mt-2 text-sm">
                <summary className="cursor-pointer text-gray-500">View as table</summary>
                <table className="mt-2 w-full text-left">
                    <thead><tr className="text-gray-500"><th className="py-1">Month</th><th>Courses (₹)</th><th>Commissions (₹)</th><th>USD</th></tr></thead>
                    <tbody>{data.map((d) => <tr key={d.month} className="border-t border-gray-100 font-mono"><td className="py-1">{d.month}</td><td>{d.coursesInr}</td><td>{d.commissionsInr}</td><td>{d.usd}</td></tr>)}</tbody>
                </table>
            </details>
        </figure>
    );
}
