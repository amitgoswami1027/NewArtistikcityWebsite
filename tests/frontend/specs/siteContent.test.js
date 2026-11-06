import { landingOf, menuOf, visibleMenu, isCurrent, cookieOf, socialOf, parseMenu, DEFAULT_LANDING, DEFAULT_MENU } from '@/Components/Site/siteContent';

// What Admin > System parameters > SEO & Layout Controls saves (shared prop `site`) and what the public site shows.

describe('landing hero', () => {
    test('nothing saved: the original hero', () => {
        expect(landingOf({})).toEqual(DEFAULT_LANDING);
        expect(landingOf(undefined).hero_heading).toBe('Learn to draw and paint');
    });

    test('saved values win; a saved heading without emphasis means no emphasis', () => {
        const l = landingOf({ landing: { hero_heading: 'Paint with us', hero_subtext: 'Small groups.' } });
        expect(l.hero_heading).toBe('Paint with us');
        expect(l.hero_emphasis).toBe('');
        expect(l.hero_subtext).toBe('Small groups.');
        expect(l.search_placeholder).toBe(DEFAULT_LANDING.search_placeholder);
    });
});

describe('header menu', () => {
    test('nothing saved (or unreadable JSON): the default menu', () => {
        expect(menuOf({})).toHaveLength(DEFAULT_MENU.length);
        expect(menuOf({ nav: { menu: '{broken' } })[0].label).toBe('How it works');
        expect(parseMenu('[]')).toBeNull();
    });

    test('saved order is kept and hidden items are left out of the header', () => {
        const site = { nav: { menu: JSON.stringify([
            { label: 'Shop', href: '/marketplace', visible: true, accent: true },
            { label: 'Old page', href: '/faq', visible: false },
            { label: 'Courses', href: '/courses?type=all' },
        ]) } };
        expect(menuOf(site).map((m) => m.label)).toEqual(['Shop', 'Old page', 'Courses']);
        expect(visibleMenu(site).map((m) => m.label)).toEqual(['Shop', 'Courses']);
        expect(visibleMenu(site)[0].accent).toBe(true);
    });

    test('the current page is marked', () => {
        expect(isCurrent('/courses?type=all', '/course/watercolour-basics')).toBe(true);
        expect(isCurrent('/marketplace', '/marketplace/sunset-1')).toBe(true);
        expect(isCurrent('/marketplace', '/market')).toBe(false);
        expect(isCurrent('/', '/courses')).toBe(false);
    });
});

describe('cookie banner', () => {
    test('off unless switched on, with default wording', () => {
        expect(cookieOf({}).enabled).toBe('0');
        const c = cookieOf({ cookie: { enabled: '1', accept_label: 'OK' } });
        expect(c.enabled).toBe('1');
        expect(c.accept_label).toBe('OK');
        expect(c.message.length).toBeGreaterThan(20);
    });
});

describe('social links matrix', () => {
    test('nothing saved: the original four links', () => {
        expect(socialOf({}).map(([k]) => k)).toEqual(['facebook', 'instagram', 'youtube', 'linkedin']);
    });

    test('saved links only, minus the hidden ones', () => {
        const s = socialOf({ social: { instagram: 'https://instagram.com/a', x: 'https://x.com/a', pinterest: 'https://pinterest.com/a', hidden: 'pinterest' } });
        expect(s.map(([k]) => k)).toEqual(['instagram', 'x']);
        expect(s[1][3]).toBe('https://x.com/a');
    });
});
