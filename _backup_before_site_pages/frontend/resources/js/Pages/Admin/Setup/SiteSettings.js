import React, { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import axios from 'axios';
import AdminLayout, { PageHeader, useToast, errorText } from '@/Components/Admin/AdminLayout';
import { Button, Badge, cx } from '@/Components/Admin/kit';
import { Field, DropZone, SaveBar, Row2, Row3, Toggle, useUnsavedGuard } from '@/Components/Admin/form';
import { landingOf, menuOf, cookieOf, DEFAULT_MENU, SOCIAL_NETWORKS } from '@/Components/Site/siteContent';
import { CookieBar } from '@/Components/Site/CookieBanner';

/*
 * SEO & Layout Controls (/admin/setup/site-settings): the form on the left, a live preview of the public site on the
 * right (desktop / mobile). Saves through POST /admin/api/setup/site-settings; the site uses the values at once.
 */

const SECTIONS = [
    ['landing', 'Landing hero', 'fa-home'],
    ['menu', 'Menu', 'fa-bars'],
    ['seo', 'Search & sharing', 'fa-search'],
    ['cookie', 'Cookie banner', 'fa-shield'],
    ['social', 'Social links', 'fa-share-alt'],
    ['contact', 'Contact & homepage', 'fa-address-card-o'],
];

const flatten = (values) => {
    const out = {};
    Object.entries(values || {}).forEach(([g, kv]) => Object.entries(kv || {}).forEach(([k, v]) => { out[`${g}.${k}`] = v == null ? '' : String(v); }));
    return out;
};

/** The form state from the saved values, with the site's defaults filled in so the operator sees what is live. */
function initialState(values) {
    const v = flatten(values);
    const landing = landingOf({ landing: values.landing });
    Object.entries(landing).forEach(([k, x]) => { v[`landing.${k}`] = x; });
    const cookie = cookieOf({ cookie: values.cookie });
    Object.entries(cookie).forEach(([k, x]) => { v[`cookie.${k}`] = x; });
    const social = values.social || {};
    const anySaved = SOCIAL_NETWORKS.some(([k]) => social[k]);
    SOCIAL_NETWORKS.forEach(([k, , , def]) => { v[`social.${k}`] = anySaved ? (social[k] || '') : def; });
    v['social.hidden'] = social.hidden || '';
    return { v, menu: menuOf({ nav: values.nav }) };
}

const LINK_RE = /^(\/(?!\/)[^\s<>"']*|https:\/\/[^\s<>"']+)$/i;

function checkAll(v, menu) {
    const e = {};
    if (!String(v['landing.hero_heading'] || '').trim()) e['landing.hero_heading'] = 'The landing page needs a heading.';
    if (v['landing.cta_label'] && !v['landing.cta_link']) e['landing.cta_link'] = 'Where should the button go?';
    if (!v['landing.cta_label'] && v['landing.cta_link']) e['landing.cta_label'] = 'Give the button a label, or clear the link.';
    ['landing.cta_link', 'cookie.policy_link'].forEach((k) => { if (v[k] && !LINK_RE.test(v[k])) e[k] = 'Use a page on this site such as /courses?type=all, or a full https:// link.'; });
    if (!String(v['seo.site_title'] || '').trim()) e['seo.site_title'] = 'The site needs a title for search results.';
    if (v['cookie.enabled'] === '1' && !String(v['cookie.message'] || '').trim()) e['cookie.message'] = 'Write the banner text, or switch the banner off.';
    SOCIAL_NETWORKS.forEach(([k]) => { const x = v[`social.${k}`]; if (x && !/^https?:\/\/\S+$/i.test(x)) e[`social.${k}`] = 'Enter the full link, starting with https://'; });
    if (v['contact.email'] && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v['contact.email'])) e['contact.email'] = 'Enter a valid e-mail address.';
    if (!menu.length) e['nav.menu'] = 'Keep at least one menu item (or reset the menu to the default).';
    const seen = new Set();
    menu.forEach((m, i) => {
        const label = m.label.trim();
        if (!label) e['nav.menu'] = e['nav.menu'] || `Menu item ${i + 1} needs a label.`;
        else if (seen.has(label.toLowerCase())) e['nav.menu'] = e['nav.menu'] || `“${label}” is in the menu twice.`;
        else if (!LINK_RE.test(m.href.trim())) e['nav.menu'] = e['nav.menu'] || `“${label}”: use a page on this site such as /marketplace, or a full https:// link.`;
        seen.add(label.toLowerCase());
    });
    return e;
}

/* ------------------------------------------------------------ menu builder */

function MenuBuilder({ items, onChange, error, onFocus }) {
    const [drag, setDrag] = useState(null);
    const [over, setOver] = useState(null);
    const listRef = useRef(null);
    const update = (i, patch) => onChange(items.map((m, j) => (j === i ? { ...m, ...patch } : m)));
    const move = (from, to) => {
        if (to < 0 || to >= items.length || from === to) return;
        const next = items.slice();
        const [x] = next.splice(from, 1);
        next.splice(to, 0, x);
        onChange(next);
    };
    const moveKey = (i, to) => {
        move(i, to);
        setTimeout(() => {
            const el = listRef.current && listRef.current.querySelectorAll('.ss-menu__handle')[to];
            if (el) el.focus();
        }, 0);
    };
    return (
        <div className="ss-menu" onFocus={onFocus}>
            <ol ref={listRef} className="ss-menu__list" aria-label="Header menu items, in order">
                {items.map((m, i) => (
                    <li key={i} className={cx('ss-menu__item', drag === i && 'is-drag', over === i && drag !== i && 'is-over', !m.visible && 'is-hidden')}
                        draggable onDragStart={(e) => { setDrag(i); e.dataTransfer.effectAllowed = 'move'; try { e.dataTransfer.setData('text/plain', String(i)); } catch (x) { /* IE */ } }}
                        onDragOver={(e) => { e.preventDefault(); setOver(i); }} onDragLeave={() => setOver((o) => (o === i ? null : o))}
                        onDrop={(e) => { e.preventDefault(); if (drag !== null) move(drag, i); setDrag(null); setOver(null); }}
                        onDragEnd={() => { setDrag(null); setOver(null); }}>
                        <button type="button" className="ss-menu__handle" aria-label={`Move ${m.label || `item ${i + 1}`}: use the up and down arrow keys`}
                            title="Drag to reorder (or focus and use ↑ ↓)"
                            onKeyDown={(e) => {
                                if (e.key === 'ArrowUp') { e.preventDefault(); moveKey(i, i - 1); }
                                if (e.key === 'ArrowDown') { e.preventDefault(); moveKey(i, i + 1); }
                            }}>
                            <i className="fa fa-bars" aria-hidden="true"></i>
                        </button>
                        <input className="adm-input" aria-label={`Label of menu item ${i + 1}`} value={m.label} maxLength={30} placeholder="Label" onChange={(e) => update(i, { label: e.target.value })} />
                        <input className="adm-input" aria-label={`Link of menu item ${i + 1}`} value={m.href} maxLength={255} placeholder="/page or https://…" onChange={(e) => update(i, { href: e.target.value })} />
                        <label className="ss-check" title="Show in the header">
                            <input type="checkbox" checked={m.visible} onChange={(e) => update(i, { visible: e.target.checked })} /> <span>Show</span>
                        </label>
                        <label className="ss-check" title="Highlight in brand colour">
                            <input type="checkbox" checked={m.accent} onChange={(e) => update(i, { accent: e.target.checked })} /> <span>Highlight</span>
                        </label>
                        <button type="button" className="ss-menu__remove" onClick={() => onChange(items.filter((_, j) => j !== i))} aria-label={`Remove ${m.label || `item ${i + 1}`}`} title="Remove">
                            <i className="fa fa-trash-o" aria-hidden="true"></i>
                        </button>
                    </li>
                ))}
            </ol>
            {error && <p className="adm-field__error" role="alert" id="f-nav.menu" tabIndex={-1}>{error}</p>}
            <div className="adm-inline">
                <Button size="sm" icon="fa-plus" disabled={items.length >= 10} onClick={() => onChange([...items, { label: '', href: '/', visible: true, accent: false }])}>Add link</Button>
                <Button size="sm" variant="ghost" icon="fa-undo" onClick={() => onChange(DEFAULT_MENU.map((m) => ({ ...m })))}>Reset to the default menu</Button>
                <span className="adm-muted adm-small">{items.length} / 10 · drag the ☰ handle, or focus it and press ↑ ↓</span>
            </div>
        </div>
    );
}

/* ----------------------------------------------------------- live preview */

function Simulator({ v, menu, device, focus, onDevice }) {
    const wrap = useRef(null);
    const [scale, setScale] = useState(0.5);
    const width = device === 'mobile' ? 390 : 1100;
    const siteRef = useRef(null);
    const [frameH, setFrameH] = useState(700);
    const [drawer, setDrawer] = useState(false);
    useEffect(() => {
        const el = wrap.current;
        if (!el) return undefined;
        const fit = () => setScale(Math.min(width < 500 ? 0.72 : 1, (el.clientWidth - 2) / width));
        fit();
        let ro;
        if (typeof ResizeObserver !== 'undefined') { ro = new ResizeObserver(fit); ro.observe(el); } else window.addEventListener('resize', fit);
        return () => { if (ro) ro.disconnect(); else window.removeEventListener('resize', fit); };
    }, [width]);
    useEffect(() => { if (device !== 'mobile') setDrawer(false); }, [device]);

    const shown = menu.filter((m) => m.visible && m.label);
    const hidden = String(v['social.hidden'] || '').split(',').filter(Boolean);
    const social = SOCIAL_NETWORKS.filter(([k]) => v[`social.${k}`] && !hidden.includes(k));
    const cookie = { message: v['cookie.message'], accept_label: v['cookie.accept_label'], decline_label: v['cookie.decline_label'], policy_link: v['cookie.policy_link'] };
    const title = v['seo.site_title'] || 'ArtistikCity';
    const desc = v['seo.meta_description'] || '';
    const mobile = device === 'mobile';
    const zone = (name) => cx('ss-zone', focus === name && 'is-focus');
    // the frame is as tall as the simulated page (header, hero, a row, snippet, footer)
    useLayoutEffect(() => {
        const h = siteRef.current ? siteRef.current.offsetHeight : 0;
        if (h && Math.abs(h - frameH) > 1) setFrameH(h);
    });

    return (
        <div className="ss-sim">
            <div className="ss-sim__bar">
                <span className="ss-sim__title"><i className="fa fa-eye" aria-hidden="true"></i> Live preview</span>
                <div className="adm-segments" role="group" aria-label="Preview size">
                    <button type="button" className={cx(!mobile && 'is-on')} aria-pressed={!mobile} onClick={() => onDevice('desktop')}><i className="fa fa-desktop" aria-hidden="true"></i> Desktop</button>
                    <button type="button" className={cx(mobile && 'is-on')} aria-pressed={mobile} onClick={() => onDevice('mobile')}><i className="fa fa-mobile" aria-hidden="true"></i> Mobile</button>
                </div>
            </div>
            <div className="ss-sim__chrome" aria-hidden="true">
                <span></span><span></span><span></span>
                <b>artistikcity.com</b>
                <em title={title}>{title}</em>
            </div>
            <div ref={wrap} className={cx('ss-sim__viewport', mobile && 'is-mobile')} style={{ height: frameH * scale + 2 }}>
                <div className={cx('ss-site', mobile && 'is-mobile')} ref={siteRef} style={{ width, transform: `scale(${scale})` }} aria-hidden="true">
                    <header className={zone('menu')}>
                        <img src="/assets/images/logo.png" alt="" className="ss-site__logo" />
                        {!mobile && (
                            <nav className="ss-site__nav">
                                {shown.map((m, i) => <span key={i} className={cx(m.accent && 'is-accent')}>{m.label}</span>)}
                            </nav>
                        )}
                        {!mobile && <span className="ss-site__search"><i className="fa fa-search"></i> Search courses…</span>}
                        {!mobile && <span className="ss-site__login">Log in</span>}
                        {!mobile && <span className="ss-site__join">Join for free</span>}
                        {mobile && <span className="ss-site__burger" onClick={() => setDrawer((d) => !d)}><i className={`fa ${drawer ? 'fa-times' : 'fa-bars'}`}></i></span>}
                    </header>
                    {mobile && drawer && (
                        <div className="ss-site__drawer">
                            {shown.map((m, i) => <span key={i} className={cx(m.accent && 'is-accent')}>{m.label}</span>)}
                        </div>
                    )}
                    <section className={cx('ss-site__hero', zone('landing'))}>
                        <div className="ss-site__heroin">
                            <h1>{v['landing.hero_heading'] || ' '}{v['landing.hero_emphasis'] && <> <em>{v['landing.hero_emphasis']}</em></>}</h1>
                            {v['landing.hero_subtext'] && <p>{v['landing.hero_subtext']}</p>}
                            <div className="ss-site__searchbig"><span>{v['landing.search_placeholder'] || 'What do you want to learn?'}</span><b>Search</b></div>
                            {v['landing.cta_label'] && <span className="ss-site__cta">{v['landing.cta_label']} <i className="fa fa-arrow-right"></i></span>}
                        </div>
                    </section>
                    <section className="ss-site__row">
                        <h3>Popular right now</h3>
                        <div className="ss-site__cards">{[0, 1, 2, 3].slice(0, mobile ? 2 : 4).map((i) => <span key={i}><i></i><b></b><small></small></span>)}</div>
                    </section>
                    <section className={cx('ss-site__serp', zone('seo'))}>
                        <small>How Google shows the site</small>
                        <span className="ss-serp__url">https://www.artistikcity.com</span>
                        <span className="ss-serp__title">{title.length > 60 ? `${title.slice(0, 59)}…` : title}</span>
                        <span className="ss-serp__text">{desc.length > 160 ? `${desc.slice(0, 159)}…` : desc || 'Add a meta description so search engines show a helpful summary.'}</span>
                    </section>
                    <footer className={zone('social')}>
                        <img src="/assets/images/footerlogo.png" alt="" />
                        <div className="ss-site__social">{social.map(([k, , icon]) => <span key={k}><i className={`fa ${icon}`}></i></span>)}</div>
                        <small>{v['contact.email'] || ''}{v['contact.email'] && v['contact.phone'] ? ' · ' : ''}{v['contact.phone'] || ''}</small>
                    </footer>
                    {v['cookie.enabled'] === '1' && (
                        <div className={zone('cookie')}><CookieBar cookie={cookie} preview onAccept={() => {}} onDecline={() => {}} /></div>
                    )}
                </div>
            </div>
            <p className="adm-muted adm-small ss-sim__note">
                {v['cookie.enabled'] === '1' ? 'The cookie banner shows until a visitor chooses. ' : ''}The preview updates as you type; nothing changes on the site until you save.
            </p>
        </div>
    );
}

/* ------------------------------------------------------------------- page */

export default function SiteSettings() {
    const { values = {}, defaults = {} } = usePage().props;
    const start = useRef(initialState(values));
    const [saved, setSaved] = useState(start.current);
    const [v, setV] = useState(start.current.v);
    const [menu, setMenu] = useState(start.current.menu);
    const [errors, setErrors] = useState({});
    const [busy, setBusy] = useState(false);
    const [og, setOg] = useState([]);
    const [device, setDevice] = useState('desktop');
    const [focus, setFocus] = useState('landing');
    const [toastNode, toast] = useToast();
    const dirty = JSON.stringify(v) !== JSON.stringify(saved.v) || JSON.stringify(menu) !== JSON.stringify(saved.menu) || og.length > 0;
    useUnsavedGuard(dirty);

    const set = (k, x) => { setV((p) => ({ ...p, [k]: x })); setErrors((e) => ({ ...e, [k]: null })); };
    const input = (k, label, { hint, rows, placeholder, max = 255, type = 'text', optional = true } = {}) => (
        <Field label={label} name={k} hint={hint} error={errors[k]} max={max} counter={(v[k] || '').length} optional={optional}>
            {rows ? <textarea id={`f-${k}`} className="adm-input" rows={rows} value={v[k] || ''} placeholder={placeholder} maxLength={max} onChange={(e) => set(k, e.target.value)} aria-invalid={errors[k] ? 'true' : undefined} />
                : <input id={`f-${k}`} type={type} className="adm-input" value={v[k] || ''} placeholder={placeholder} maxLength={max} onChange={(e) => set(k, e.target.value)} aria-invalid={errors[k] ? 'true' : undefined} />}
        </Field>
    );
    const showErrors = (errs, message) => {
        setErrors(errs);
        toast(message || 'Please fix the highlighted fields.', 'error');
        const first = Object.keys(errs)[0];
        const el = first && document.getElementById(`f-${first}`);
        if (el) { el.focus(); el.scrollIntoView({ block: 'center' }); }
    };
    const save = () => {
        const local = checkAll(v, menu);
        if (Object.keys(local).length) { showErrors(local); return; }
        setBusy(true);
        const data = { landing: {}, seo: {}, cookie: {}, social: {}, contact: {}, home: {}, nav: { menu } };
        Object.entries(v).forEach(([key, x]) => { const [g, k] = key.split('.'); if (data[g] && g !== 'nav') data[g][k] = x; });
        const fd = new FormData();
        fd.append('data', JSON.stringify(data));
        if (og[0]) fd.append('og_image', og[0]);
        axios.post('/admin/api/setup/site-settings', fd).then((r) => {
            const next = initialState(r.data.values);
            const merged = { v: { ...v, ...next.v }, menu: next.menu };
            setSaved(merged); setV(merged.v); setMenu(merged.menu); setOg([]); setErrors({}); toast(r.data.message);
        }).catch((e) => {
            if (e.response && e.response.status === 422) showErrors(e.response.data.errors || {}, e.response.data.message);
            else toast(errorText(e), 'error');
        }).finally(() => setBusy(false));
    };
    const discard = () => { setV(saved.v); setMenu(saved.menu); setOg([]); setErrors({}); };
    const hidden = String(v['social.hidden'] || '').split(',').filter(Boolean);
    const toggleHidden = (k, show) => set('social.hidden', (show ? hidden.filter((x) => x !== k) : [...hidden, k]).join(','));
    const sectionErrors = (id) => Object.keys(errors).filter((k) => errors[k] && (k.startsWith(`${id === 'menu' ? 'nav' : id === 'contact' ? 'contact' : id}.`) || (id === 'contact' && k.startsWith('home.')))).length;
    const section = (id, title, description, children) => (
        <section id={`sec-${id}`} className={cx('adm-card ss-sec', focus === id && 'is-focus')} onFocus={() => setFocus(id)} aria-labelledby={`h-${id}`}>
            <div className="ss-sec__head">
                <h2 id={`h-${id}`} className="rm-h">{title}</h2>
                {description && <p className="adm-muted adm-small">{description}</p>}
            </div>
            <div className="adm-stack">{children}</div>
        </section>
    );

    return (
        <AdminLayout title="SEO & Layout Controls" wide crumbs={[{ label: 'System parameters' }, { label: 'SEO & Layout Controls' }]}>
            <PageHeader eyebrow="System parameters" title="SEO & Layout Controls"
                subtitle="The public landing page, header menu, search snippet, cookie banner and social links. The preview on the right follows your typing; the site changes when you save."
                actions={<Button as="a" icon="fa-external-link" href="/" target="_blank" rel="noopener noreferrer">Open the site</Button>} />

            <nav className="ss-jump" aria-label="Sections">
                {SECTIONS.map(([id, label, icon]) => (
                    <a key={id} href={`#sec-${id}`} className={cx(focus === id && 'is-on')} onClick={(e) => { e.preventDefault(); setFocus(id); const el = document.getElementById(`sec-${id}`); if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' }); }}>
                        <i className={`fa ${icon}`} aria-hidden="true"></i> {label}
                        {sectionErrors(id) > 0 && <Badge tone="red">{sectionErrors(id)}</Badge>}
                    </a>
                ))}
            </nav>

            <div className="ss-split">
                <form className="ss-form" onSubmit={(e) => { e.preventDefault(); save(); }} noValidate>
                    {section('landing', 'Landing hero', 'The first thing visitors read on the home page.', <>
                        {input('landing.hero_heading', 'Heading', { max: 120, optional: false, hint: 'Short and specific: what someone gets here.' })}
                        {input('landing.hero_emphasis', 'Emphasised words', { max: 80, hint: 'Shown after the heading in brand colour. Leave empty for none.' })}
                        {input('landing.hero_subtext', 'Subtext', { rows: 3, max: 300 })}
                        {input('landing.search_placeholder', 'Search box hint', { max: 255 })}
                        <Row2>
                            {input('landing.cta_label', 'Button label', { max: 40, placeholder: 'Browse courses', hint: 'Optional extra button under the search.' })}
                            {input('landing.cta_link', 'Button link', { placeholder: '/courses?type=all', hint: 'A page on this site, or https://…' })}
                        </Row2>
                    </>)}

                    {section('menu', 'Menu builder', 'The links in the site header, in order. Hidden links stay here for later.', (
                        <MenuBuilder items={menu} onChange={(m) => { setMenu(m); setErrors((e) => ({ ...e, 'nav.menu': null })); }} error={errors['nav.menu']} onFocus={() => setFocus('menu')} />
                    ))}

                    {section('seo', 'Search & sharing', 'The default title and description search engines and social networks show. Stories and courses can override them.', <>
                        {input('seo.site_title', 'Site title', { hint: `Best under 60 characters (${(v['seo.site_title'] || '').length} now).`, optional: false, placeholder: defaults.site_title })}
                        {input('seo.meta_description', 'Meta description', { rows: 3, max: 500, hint: `Best between 120 and 160 characters (${(v['seo.meta_description'] || '').length} now).`, placeholder: defaults.meta_description })}
                        {input('seo.meta_keywords', 'Keywords', { hint: 'Comma separated. Most search engines ignore these.' })}
                        <Field label="Share image" name="seo.og_image" hint="Shown when the site is shared on WhatsApp, Facebook or LinkedIn. 1200 × 630 px works best." optional>
                            <DropZone name="seo.og_image" value={og} onChange={setOg} error={errors['seo.og_image']}
                                existing={v['seo.og_image'] ? [{ id: 'og', url: v['seo.og_image'], name: 'Current share image' }] : []}
                                onRemoveExisting={() => set('seo.og_image', '')} />
                        </Field>
                    </>)}

                    {section('cookie', 'Cookie & privacy banner', 'A bar at the bottom of every public page until the visitor chooses. Their choice is remembered for a year.', <>
                        <Toggle name="cookie.enabled" checked={v['cookie.enabled'] === '1'} onChange={(x) => { set('cookie.enabled', x ? '1' : '0'); setFocus('cookie'); }}
                            label={v['cookie.enabled'] === '1' ? 'Banner on' : 'Banner off'} description="Switch on if you use analytics or marketing cookies." />
                        {input('cookie.message', 'Banner text', { rows: 3, max: 500, optional: v['cookie.enabled'] !== '1' })}
                        <Row3>
                            {input('cookie.accept_label', 'Accept button', { max: 40 })}
                            {input('cookie.decline_label', 'Decline button', { max: 40, hint: 'Empty = no decline button.' })}
                            {input('cookie.policy_link', 'Policy link', { placeholder: '/privacy-policy' })}
                        </Row3>
                    </>)}

                    {section('social', 'Social links matrix', 'Footer icons. A network shows when it has a link and “Show” is ticked.', (
                        <div className="ss-matrixwrap">
                            <table className="ss-matrix">
                                <thead><tr><th scope="col">Network</th><th scope="col">Link</th><th scope="col">Show</th></tr></thead>
                                <tbody>
                                    {SOCIAL_NETWORKS.map(([k, label, icon]) => {
                                        const key = `social.${k}`;
                                        return (
                                            <tr key={k}>
                                                <th scope="row"><i className={`fa ${icon}`} aria-hidden="true"></i> {label}</th>
                                                <td>
                                                    <input id={`f-${key}`} className="adm-input" value={v[key] || ''} placeholder="https://…" aria-label={`${label} link`}
                                                        onChange={(e) => set(key, e.target.value.trim())} aria-invalid={errors[key] ? 'true' : undefined} />
                                                    {errors[key] && <span className="adm-field__error">{errors[key]}</span>}
                                                </td>
                                                <td>
                                                    <input type="checkbox" aria-label={`Show ${label}`} disabled={!v[key]} checked={!!v[key] && !hidden.includes(k)} onChange={(e) => toggleHidden(k, e.target.checked)} />
                                                </td>
                                            </tr>
                                        );
                                    })}
                                </tbody>
                            </table>
                        </div>
                    ))}

                    {section('contact', 'Contact details & homepage points', 'Contact details show on the contact page and in the footer; leave a field empty to hide it.', <>
                        <Row2>
                            {input('contact.email', 'E-mail', { type: 'email', placeholder: 'hello@artistikcity.com' })}
                            {input('contact.phone', 'Phone / WhatsApp', { placeholder: '+91 98765 43210' })}
                        </Row2>
                        {input('contact.address', 'Office address', { rows: 3, max: 500 })}
                        <Row3>
                            {input('home.course_text_1', 'Course point 1', { max: 60 })}
                            {input('home.course_text_2', 'Course point 2', { max: 60 })}
                            {input('home.course_text_3', 'Course point 3', { max: 60 })}
                        </Row3>
                        <Row3>
                            {input('home.workshop_text_1', 'Workshop point 1', { max: 60 })}
                            {input('home.workshop_text_2', 'Workshop point 2', { max: 60 })}
                            {input('home.workshop_text_3', 'Workshop point 3', { max: 60 })}
                        </Row3>
                    </>)}

                    <SaveBar dirty={dirty} busy={busy} onSave={save}>
                        {dirty && <Button variant="ghost" onClick={discard}>Discard changes</Button>}
                    </SaveBar>
                </form>

                <aside className="ss-preview" aria-label="Live preview">
                    <Simulator v={v} menu={menu} device={device} focus={focus} onDevice={setDevice} />
                </aside>
            </div>
            {toastNode}
        </AdminLayout>
    );
}
