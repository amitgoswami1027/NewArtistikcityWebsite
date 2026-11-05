/** The pure URL helpers behind the course catalogue filters (frontend/resources/js/Components/Site/catalogQuery.js). */
import { toSet, typeParam, toQuery, fromQuery, writeQuery, DEFAULT_SORT } from '@/Components/Site/catalogQuery';

describe('catalogQuery', () => {
    test('toSet splits comma lists and drops blanks', () => {
        expect([...toSet('2, 5,,7')]).toEqual(['2', '5', '7']);
        expect(toSet('').size).toBe(0);
        expect(toSet(null).size).toBe(0);
        expect([...toSet(['a', ' b '])]).toEqual(['a', 'b']);
    });

    test('typeParam: exactly one format picked is that format, otherwise all', () => {
        expect(typeParam(new Set())).toBe('all');
        expect(typeParam(new Set(['course']))).toBe('course');
        expect(typeParam(new Set(['workshop']))).toBe('workshop');
        expect(typeParam(new Set(['course', 'workshop']))).toBe('all');
    });

    test('toQuery writes a stable, minimal query string', () => {
        const q = toQuery({
            q: '  sky ', medium: new Set(['5', '2']), genre: new Set(), skill: new Set(['1']),
            age: new Set(['Junior - 12 to 15 yrs']), type: new Set(['course']), sort: 'newest',
        });
        expect(q).toBe('type=course&q=sky&medium=2%2C5&skill=1&age=Junior+-+12+to+15+yrs&sort=newest');
        expect(toQuery({ type: new Set(), sort: DEFAULT_SORT })).toBe('type=all');
    });

    test('fromQuery reads it back, and ignores unknown values', () => {
        const f = fromQuery('?type=workshop&q=sky&medium=2,5&age=Adult%20-%2016%20yrs%20and%20above&sort=bogus');
        expect(f.q).toBe('sky');
        expect([...f.medium]).toEqual(['2', '5']);
        expect([...f.age]).toEqual(['Adult - 16 yrs and above']);
        expect([...f.type]).toEqual(['workshop']);
        expect(f.sort).toBe(DEFAULT_SORT);
        expect(fromQuery('?type=all').type.size).toBe(0);
    });

    test('round trip: fromQuery(toQuery(x)) gives x back', () => {
        const x = { q: 'ink', medium: new Set(['3']), genre: new Set(['1', '4']), skill: new Set(), age: new Set(['Sub-junior - 8 to 12 yrs']), type: new Set(['course']), sort: 'price-desc' };
        const y = fromQuery('?' + toQuery(x));
        expect(y.q).toBe(x.q);
        expect([...y.medium]).toEqual([...x.medium]);
        expect([...y.genre].sort()).toEqual([...x.genre].sort());
        expect([...y.age]).toEqual([...x.age]);
        expect([...y.type]).toEqual([...x.type]);
        expect(y.sort).toBe(x.sort);
    });

    test('writeQuery replaces the URL only when it changes, keeping history.state and the #hash', () => {
        const state = { component: 'Courses' };
        const win = {
            location: { pathname: '/courses', search: '?type=all', hash: '#grid' },
            history: { state, replaceState: jest.fn() },
        };
        writeQuery({ type: new Set() }, win);
        expect(win.history.replaceState).not.toHaveBeenCalled();

        writeQuery({ type: new Set(['workshop']), q: 'tree' }, win);
        expect(win.history.replaceState).toHaveBeenCalledWith(state, '', '/courses?type=workshop&q=tree#grid');
    });
});

describe('fuzzyScore', () => {
    const { fuzzyScore, normalise } = require('@/Components/Site/catalogQuery');
    test('accents, case and punctuation do not matter', () => {
        expect(normalise('Aquarelle, Café!')).toBe('aquarelle cafe');
        expect(fuzzyScore('Café sketching', 'cafe')).toBeGreaterThan(0);
    });
    test('every word must match; small typos are forgiven on longer words', () => {
        expect(fuzzyScore('Pencil Sketching Essentials', 'pencl skeching')).toBeGreaterThan(0);
        expect(fuzzyScore('Watercolour Landscapes', 'watercolor')).toBeGreaterThan(0);
        expect(fuzzyScore('Pencil Sketching', 'oil')).toBe(0);
        expect(fuzzyScore('Oil Painting', 'oli')).toBe(0);         // 3 letters: no typo allowed
    });
    test('exact words rank above prefixes and typos', () => {
        expect(fuzzyScore('oil painting', 'oil')).toBeGreaterThan(fuzzyScore('oils on paper', 'oil'));
        expect(fuzzyScore('pencil', 'pencil')).toBeGreaterThan(fuzzyScore('pencil', 'pencl'));
    });
});
