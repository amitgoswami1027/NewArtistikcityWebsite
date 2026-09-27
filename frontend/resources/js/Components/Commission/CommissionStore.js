import React, { createContext, useCallback, useContext, useEffect, useState } from 'react';

/**
 * Centralised wizard state for the Artist Commission Portal.
 * Each step is its own Inertia page, so the state is kept in React context and mirrored to
 * sessionStorage; moving between steps (or refreshing) never loses the configuration.
 */
const STORAGE_KEY = 'artistikcity.commission.v1';

export const THEMES = [
    { id: 'PORTRAIT', title: 'Classic Portraiture', blurb: 'Oil-style painting of a person or pet, rich tones and soft brushwork.' },
    { id: 'LANDSCAPE', title: 'Scenic Landscape', blurb: 'Nature, cityscapes and architecture with vivid, textured colour.' },
    { id: 'WATERCOLOR', title: 'Fluid Watercolor', blurb: 'Soft edges, paper texture and gentle colour bleeds.' },
    { id: 'CHARCOAL', title: 'Expressive Charcoal', blurb: 'High-contrast monochrome sketch with bold, textured strokes.' },
];

export const FULFILLMENTS = [
    { id: 'DIGITAL_ONLY', title: 'Digital File Only', blurb: 'High-resolution file, delivered by download link.' },
    { id: 'PHYSICAL_PRINT', title: 'Physical Canvas Print', blurb: 'Museum-grade canvas print of your painting, shipped to you.' },
    { id: 'ORIGINAL_PAINTING', title: 'Original Physical Painting', blurb: 'The hand-painted original, signed and shipped to you.' },
];

export const SIZES = ['8x10', '12x16', '16x20', '24x36'];

export const FRAMES = [
    { id: 'NONE', title: 'No Frame (Gallery Canvas Wrap)', blurb: 'Canvas wrapped around a deep stretcher bar, ready to hang.' },
    { id: 'MATTE_BLACK', title: 'Modern Matte Black Wood', blurb: 'Slim, contemporary black frame.' },
    { id: 'GALLERY_WHITE', title: 'Classic Gallery White Wood', blurb: 'Clean white frame for bright rooms.' },
    { id: 'WARM_WALNUT', title: 'Warm Walnut Wood', blurb: 'Natural walnut grain with a warm finish.' },
];

export const INITIAL_STATE = {
    theme: null,
    fulfillmentType: null,
    size: '12x16',
    orientation: 'PORTRAIT',
    photoUrl: null,
    photoName: null,
    instructions: '',
    intensity: 0.85,
    frameMaterial: 'NONE',
    hasMatting: false,
    contact: { name: '', email: '', phone: '' },
    shipping: { line1: '', city: '', postalCode: '', country: 'India' },
    paymentMethod: 'RAZORPAY_UPI',
};

function load() {
    try {
        const raw = window.sessionStorage.getItem(STORAGE_KEY);
        return raw ? { ...INITIAL_STATE, ...JSON.parse(raw) } : INITIAL_STATE;
    } catch (e) {
        return INITIAL_STATE;
    }
}

const CommissionContext = createContext(null);

export function CommissionProvider({ children }) {
    const [state, setState] = useState(() => (typeof window === 'undefined' ? INITIAL_STATE : load()));

    useEffect(() => {
        try { window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(state)); } catch (e) { /* storage unavailable */ }
    }, [state]);

    const update = useCallback((patch) => setState((s) => ({ ...s, ...(typeof patch === 'function' ? patch(s) : patch) })), []);
    const reset = useCallback(() => {
        try { window.sessionStorage.removeItem(STORAGE_KEY); } catch (e) { /* ignore */ }
        setState(INITIAL_STATE);
    }, []);

    return <CommissionContext.Provider value={{ state, update, reset }}>{children}</CommissionContext.Provider>;
}

export function useCommission() {
    const ctx = useContext(CommissionContext);
    if (!ctx) throw new Error('useCommission must be used inside <CommissionProvider>');
    return ctx;
}

export function clearCommission() {
    try { window.sessionStorage.removeItem(STORAGE_KEY); } catch (e) { /* ignore */ }
}

/** Width/height in inches, honouring the orientation. */
export function dimensions(size, orientation) {
    const [a, b] = String(size || '12x16').split('x').map(Number);
    return orientation === 'LANDSCAPE' ? [Math.max(a, b), Math.min(a, b)] : [Math.min(a, b), Math.max(a, b)];
}

export const isDigital = (state) => state.fulfillmentType === 'DIGITAL_ONLY';
export const labelOf = (list, id) => (list.find((x) => x.id === id) || {}).title || '—';

/** Mirrors CommissionPricing.java so the wizard can show live totals (the server re-prices at checkout). */
export function quote(state, pricing) {
    if (!pricing || !state.fulfillmentType || !state.theme) return null;
    const i = SIZES.indexOf(state.size);
    if (i < 0) return null;
    const round2 = (n) => Math.round(n * 100) / 100;
    const physical = state.fulfillmentType !== 'DIGITAL_ONLY';
    const artwork = round2(pricing.base[state.fulfillmentType][i] * Number(pricing.themeFactor[state.theme]));
    const frame = physical ? pricing.frames[state.frameMaterial || 'NONE'][i] : 0;
    const matting = physical && state.hasMatting ? pricing.matting[i] : 0;
    const shipping = physical ? pricing.shipping : 0;
    const totalUsd = round2(artwork + frame + matting + shipping);
    const totalInr = Math.round((totalUsd * Number(pricing.inrPerUsd)) / 10) * 10;
    return { artwork, frame, matting, shipping, totalUsd, totalInr };
}

export const usd = (n) => '$' + Number(n || 0).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
export const inr = (n) => '₹' + Number(n || 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
