import { placeInRoom, framedSize } from '@/Components/Shop/ArtPreview';

// a 2000 x 1250 photo whose marked area is 50% wide = 1000 px = 100 inches of wall: 10 photo px per inch
const ROOM = { id: 1, label: 'Living room', width: 2000, height: 1250, area: { x: 25, y: 10, w: 50, h: 50 }, areaWidthIn: 100 };

describe('placeInRoom', () => {
    test('a measured wall draws the piece to scale, centred in the area', () => {
        const s = placeInRoom(ROOM, 24, 30, 1000);
        expect(s.toScale).toBe(true);
        expect(s.box.w).toBeCloseTo(240);              // 24 in x 10 px/in
        expect(s.box.h).toBeCloseTo(300);
        expect(s.box.x + s.box.w / 2).toBeCloseTo(1000); // area centre: 25% + 50% / 2 of 2000
    });

    test('the camera never enlarges the photo much past its own pixels', () => {
        const wide = placeInRoom(ROOM, 12, 12, 1900);  // a small piece on a wide screen
        expect(wide.zoom).toBeLessThanOrEqual((2000 / 1900) * 1.35 + 1e-9);
        const small = placeInRoom({ ...ROOM, width: 1200, height: 800 }, 12, 12, 600);
        expect(small.zoom).toBeLessThanOrEqual(2.4);
        expect(small.zoom).toBeGreaterThanOrEqual(1);
    });

    test('a piece bigger than the wall is shown as large as the wall allows, and says so', () => {
        const s = placeInRoom(ROOM, 160, 120, 1000);
        expect(s.tooBig).toBe(true);
        expect(s.box.w).toBeLessThanOrEqual(1000 + 1e-6);
        expect(s.box.h).toBeLessThanOrEqual(625 + 1e-6);
    });

    test('without a measurement the piece is fitted into the area', () => {
        const s = placeInRoom({ ...ROOM, areaWidthIn: null }, 24, 30, 1000);
        expect(s.toScale).toBe(false);
        expect(s.box.w).toBeLessThanOrEqual(1000 * 0.6 + 1e-6);
        expect(s.box.h).toBeLessThanOrEqual(625 * 0.75 + 1e-6);
    });
});

test('frames add their moulding on every side', () => {
    expect(framedSize(20, 30, 'NONE')).toMatchObject({ w: 20, h: 30 });
    expect(framedSize(20, 30, 'OAK').w).toBeCloseTo(22.5);
    expect(framedSize(20, 30, 'WHITE_FLOAT').w).toBeCloseTo(23.1);
});
