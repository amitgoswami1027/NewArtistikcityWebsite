/*
 * Shared helpers for the marketplace purchase flow: money, format names, and a client-side mirror of
 * ShopPricing.quote() so prices update instantly while the visitor clicks. The server re-prices everything
 * (draft, cart, checkout); these numbers are only for the live preview.
 */

export const inr = (n) => '₹' + Number(n || 0).toLocaleString('en-IN', { maximumFractionDigits: 0 });
export const num = (n) => {
    const v = Number(n);
    if (!isFinite(v)) return '';
    return Number.isInteger(v) ? String(v) : v.toFixed(1).replace(/\.0$/, '');
};
export const errorText = (e, fallback = 'Something went wrong. Please try again.') =>
    (e && e.response && e.response.data && e.response.data.message) || fallback;

export const FORMAT_INFO = {
    ORIGINAL: { name: 'Original painting', icon: 'fa-diamond', blurb: 'The one-of-one artwork, signed by the artist and shipped insured.', tag: '1 of 1' },
    CANVAS: { name: 'Limited edition canvas', icon: 'fa-th-large', blurb: 'Museum-grade giclée on cotton canvas, hand-numbered.', tag: 'Numbered' },
    POSTER: { name: 'Fine art poster', icon: 'fa-file-image-o', blurb: 'Archival paper print in matte or glossy. Budget friendly.', tag: 'Paper print' },
    DIGITAL: { name: 'Digital download', icon: 'fa-cloud-download', blurb: 'High-resolution file for personal use. Instant access, no shipping.', tag: 'Instant' },
};

export const FRAME_INFO = {
    NONE: { name: 'No frame', swatch: 'pz-sw--none' },
    BLACK_WOOD: { name: 'Black wood', swatch: 'pz-sw--black' },
    OAK: { name: 'Natural oak', swatch: 'pz-sw--oak' },
    WHITE_FLOAT: { name: 'White canvas float', swatch: 'pz-sw--white' },
};

export const formatOf = (offer, format) => (offer && offer.formats ? offer.formats.find((f) => f.format === format) : null) || null;

/** The selection normalised the way the server does it (default size, finish rules, quantity limits). */
export function normalise(offer, sel) {
    const f = formatOf(offer, sel.format);
    if (!f) return sel;
    const out = { ...sel };
    if (sel.format === 'ORIGINAL') out.size = '';
    else if (!f.sizes.some((s) => s.code === sel.size)) out.size = f.sizes.length ? f.sizes[Math.min(1, f.sizes.length - 1)].code : '';
    if (sel.format === 'CANVAS') out.finish = sel.finish === 'ROLLED' && (!sel.frame || sel.frame === 'NONE') ? 'ROLLED' : 'STRETCHED';
    else if (sel.format === 'POSTER') out.finish = sel.finish === 'GLOSSY' ? 'GLOSSY' : 'MATTE';
    else out.finish = '';
    if (!f.frames.includes(sel.frame || 'NONE')) out.frame = 'NONE';
    out.frame = out.frame || 'NONE';
    out.qty = Math.max(1, Math.min(Number(sel.qty) || 1, f.maxQty || 1));
    return out;
}

/** {unit, total, width, height, framePrice} for a normalised selection. */
export function priceOf(offer, sel) {
    const f = formatOf(offer, sel.format);
    if (!f) return null;
    const size = sel.format === 'ORIGINAL' ? f.sizes[0] : f.sizes.find((s) => s.code === sel.size) || (sel.format === 'DIGITAL' ? f.sizes[0] : null);
    if (!size) return null;
    let unit = Number(size.price);
    if (sel.format === 'CANVAS' && sel.finish !== 'ROLLED') unit += Number(size.extra || 0);
    let framePrice = 0;
    if (sel.frame && sel.frame !== 'NONE' && size.width) {
        const rate = (offer.frameRates || []).find((r) => r.code === sel.frame);
        framePrice = rate ? Math.round(Number(rate.perInch) * 2 * (Number(size.width) + Number(size.height))) : 0;
    }
    unit += framePrice;
    return { unit, total: unit * (sel.qty || 1), width: size.width, height: size.height, framePrice, size };
}

/** Frame cost for another frame at the same size (for the frame chips). */
export function frameCost(offer, sel, frame) {
    const p = priceOf(offer, { ...sel, frame, finish: frame !== 'NONE' && sel.format === 'CANVAS' ? 'STRETCHED' : sel.finish });
    const base = priceOf(offer, { ...sel, frame: 'NONE' });
    return p && base ? p.unit - base.unit : 0;
}

export function variantLine(sel, price) {
    const parts = [FORMAT_INFO[sel.format] ? FORMAT_INFO[sel.format].name : 'Artwork'];
    if (price && price.width) parts.push(`${num(price.width)} × ${num(price.height)} in`);
    if (sel.format === 'CANVAS') parts.push(sel.finish === 'ROLLED' ? 'rolled' : 'stretched');
    if (sel.format === 'POSTER') parts.push((sel.finish || 'MATTE').toLowerCase());
    if (sel.frame && sel.frame !== 'NONE') parts.push(FRAME_INFO[sel.frame].name.toLowerCase() + ' frame');
    return parts.join(' · ');
}

export function loadRazorpay() {
    return new Promise((resolve, reject) => {
        if (window.Razorpay) return resolve(window.Razorpay);
        const s = document.createElement('script');
        s.src = 'https://checkout.razorpay.com/v1/checkout.js';
        s.onload = () => resolve(window.Razorpay);
        s.onerror = () => reject(new Error('Could not load Razorpay'));
        document.body.appendChild(s);
        return undefined;
    });
}
