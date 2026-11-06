/*
 * Landing hero, header menu, cookie banner and social links as set in Admin > System parameters > SEO & Layout Controls
 * (settings tags landing / nav / cookie / social, shared prop `site`). Empty values fall back to these defaults, so the
 * site looks exactly as before until something is saved. The admin live preview uses the same functions.
 */

export const DEFAULT_LANDING = {
    hero_heading: 'Learn to draw and paint',
    hero_emphasis: 'with a real artist.',
    hero_subtext: "Live online art courses and workshops for every age and level. Pick a medium, join a small group and finish artwork you're proud of.",
    cta_label: '',
    cta_link: '',
    search_placeholder: 'What do you want to learn?',
};

export const DEFAULT_MENU = [
    { label: 'How it works', href: '/how-it-works', visible: true, accent: false },
    { label: 'Courses', href: '/courses?type=all', visible: true, accent: false },
    { label: 'Commission art', href: '/commission/step-1', visible: true, accent: false },
    { label: 'Marketplace', href: '/marketplace', visible: true, accent: false },
    { label: 'Studio Stories', href: '/studio-stories', visible: true, accent: true },
];

export const DEFAULT_COOKIE = {
    enabled: '0',
    message: 'We use essential cookies to keep you signed in and your checkout safe, and optional ones to understand how the site is used.',
    accept_label: 'Accept all',
    decline_label: 'Essential only',
    policy_link: '/privacy-policy',
};

/** [key, label, Font Awesome icon, default link] in footer order. */
export const SOCIAL_NETWORKS = [
    ['facebook', 'Facebook', 'fa-facebook', 'https://www.facebook.com/StudioVaishaliArts/'],
    ['instagram', 'Instagram', 'fa-instagram', 'https://www.instagram.com/artistikcity/'],
    ['youtube', 'YouTube', 'fa-youtube-play', 'https://www.youtube.com/@artistikcityYT'],
    ['linkedin', 'LinkedIn', 'fa-linkedin', 'https://in.linkedin.com/in/vaishali-jetly-goswami-4b5874233'],
    ['x', 'X (Twitter)', 'fa-twitter', ''],
    ['pinterest', 'Pinterest', 'fa-pinterest', ''],
    ['whatsapp', 'WhatsApp', 'fa-whatsapp', ''],
];

const filled = (v) => v !== undefined && v !== null && String(v).trim() !== '';

export function landingOf(site) {
    const l = (site && site.landing) || {};
    const out = { ...DEFAULT_LANDING };
    Object.keys(DEFAULT_LANDING).forEach((k) => { if (filled(l[k])) out[k] = l[k]; });
    // an explicitly saved heading without emphasis means "no emphasis"
    if (filled(l.hero_heading) && !filled(l.hero_emphasis)) out.hero_emphasis = '';
    return out;
}

export function parseMenu(raw) {
    if (Array.isArray(raw)) return raw;
    if (!filled(raw)) return null;
    try {
        const m = JSON.parse(raw);
        return Array.isArray(m) && m.length ? m : null;
    } catch (e) {
        return null;
    }
}

/** The full menu (hidden items included, for the builder). */
export function menuOf(site) {
    const m = parseMenu(site && site.nav && site.nav.menu);
    return (m || DEFAULT_MENU).map((i) => ({ label: String(i.label || ''), href: String(i.href || ''), visible: i.visible !== false, accent: !!i.accent }));
}

/** The items to show in the header. */
export const visibleMenu = (site) => menuOf(site).filter((i) => i.visible && i.label && i.href);

/** Whether a menu link is the page being viewed. */
export function isCurrent(href, here) {
    const path = String(href || '').split(/[?#]/)[0].replace(/\/+$/, '') || '/';
    if (path === '/') return here === '/';
    if (path === '/courses') return /^\/(courses|course|workshop)/.test(here);
    if (path.startsWith('/commission')) return here.startsWith('/commission');
    return here === path || here.startsWith(`${path}/`);
}

export function cookieOf(site) {
    const c = (site && site.cookie) || {};
    const out = { ...DEFAULT_COOKIE };
    Object.keys(DEFAULT_COOKIE).forEach((k) => { if (filled(c[k])) out[k] = c[k]; });
    return out;
}

/** The social links to show: [key, label, icon, href]. Without saved links the original four are used. */
export function socialOf(site) {
    const s = (site && site.social) || {};
    const anySaved = SOCIAL_NETWORKS.some(([k]) => filled(s[k]));
    const hidden = String(s.hidden || '').split(',').map((x) => x.trim()).filter(Boolean);
    return SOCIAL_NETWORKS
        .map(([k, label, icon, def]) => [k, label, icon, anySaved ? (s[k] || '') : def])
        .filter(([k, , , href]) => filled(href) && !hidden.includes(k));
}

/** A link is "outside" when it leaves this site (opens in a new tab). */
export const isExternal = (href) => /^https?:\/\//i.test(String(href || ''));
