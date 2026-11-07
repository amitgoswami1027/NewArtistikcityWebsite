/**
 * The live-preview mirror of ShopPricing's custom print sizes (Components/Shop/shop.js). The numbers are the ones
 * ShopPricingTest checks on the server, so the price the buyer sees while dragging the slider is the price charged.
 */
import { customSide, customSize, printSize, roundPrice, priceOf, normalise } from '@/Components/Shop/shop';

const square = { width_inches: 24, height_inches: 24 };
const tall = { width_inches: 12, height_inches: 16 };
const sizes = (rows) => rows.map(([code, side, price, extra]) => ({ code, longSide: side, price, extra, width: side, height: side }));
const canvas = { format: 'CANVAS', sizes: sizes([['S', 12, 2499, 600], ['M', 24, 4999, 1100], ['L', 36, 8999, 1600], ['XL', 48, 13999, 2200]]),
    frames: ['NONE', 'OAK'], finishes: [{ code: 'STRETCHED' }, { code: 'ROLLED' }], maxQty: 10, custom: { min: 8, max: 60 } };
const poster = { format: 'POSTER', sizes: sizes([['S', 12, 799, 0], ['M', 18, 1199, 0], ['L', 24, 1699, 0], ['XL', 36, 2699, 0]]),
    frames: ['NONE'], finishes: [{ code: 'MATTE' }], maxQty: 10, custom: { min: 8, max: 60 } };
const offer = { formats: [canvas, poster], frameRates: [{ code: 'OAK', perInch: 28 }] };

describe('custom print sizes', () => {
    test('codes', () => {
        expect(customSide('C30')).toBe(30);
        expect(customSide('M')).toBe(null);
        expect(customSide('C1234')).toBe(null);
    });

    test('a custom size at a preset costs the preset', () => {
        expect(customSize(square, poster, 18).price).toBe(1199);
        expect(customSize(square, canvas, 24).price).toBe(4999);
        expect(customSize(square, canvas, 24).extra).toBe(1100);
    });

    test('between presets: by area, up to the next x99 (same as the server)', () => {
        expect(customSize(square, poster, 30).price).toBe(2199);
        const c = customSize(square, canvas, 30);
        expect(c.price + c.extra).toBe(8099);
        for (let side = 8; side <= 60; side += 1) expect(customSize(square, poster, side).price % 100).toBe(99);
    });

    test('the other side keeps the artwork proportions', () => {
        expect(printSize(tall, 24)).toEqual({ width: 18, height: 24 });
        expect(customSize(tall, poster, 30).width).toBe(22.5);
    });

    test('rounding', () => {
        expect(roundPrice(1250)).toBe(1299);
        expect(roundPrice(4999)).toBe(4999);
        expect(roundPrice(10)).toBe(99);
    });

    test('priceOf and normalise accept a custom size within the limits only', () => {
        const sel = normalise(offer, { format: 'CANVAS', size: 'C30', finish: 'STRETCHED', frame: 'OAK', qty: 1 });
        expect(sel.size).toBe('C30');
        const p = priceOf(offer, sel, square);
        expect(p.framePrice).toBe(3360);
        expect(p.unit).toBe(8099 + 3360);
        expect(normalise(offer, { format: 'CANVAS', size: 'C99', frame: 'NONE', qty: 1 }).size).toBe('M');
    });
});
