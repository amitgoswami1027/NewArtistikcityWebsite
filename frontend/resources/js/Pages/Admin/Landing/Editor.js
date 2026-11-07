import React, { useEffect, useMemo, useRef, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import axios from 'axios';
import AdminLayout, { PageHeader, useToast, errorText } from '@/Components/Admin/AdminLayout';
import { Card, Button, Badge, DataTable, Segments, EmptyState, useConfirm, fmtDate, cx } from '@/Components/Admin/kit';
import { Field, Toggle, ListEditor } from '@/Components/Admin/form';
import { STATUS, inr, has, PaymentSettings, SectionContent, TemplateThumb } from '@/Components/Admin/Landing/parts';

/*
 * Admin > Landing Page Management > one page: the creation wizard (no id yet) and the editor, as nine steps with their own
 * address (/admin/landing/pages/{id}/{step}). Content, sections, design and SEO are the versioned draft (saved with
 * "Save draft", live only after Publish); pricing, payments and checkout are saved directly and apply at once, because
 * the price shown and the price charged must always agree.
 */

const STEPS = [
    { key: 'basics', label: 'Basics', n: 1, cap: 'landing.content', hint: 'Name, course, address and campaign.' },
    { key: 'template', label: 'Template', n: 2, cap: 'landing.content', hint: 'The starting layout and look.' },
    { key: 'sections', label: 'Sections & content', n: 3, cap: 'landing.content', hint: 'Order, show or hide, and write each section.' },
    { key: 'design', label: 'Design', n: 4, cap: 'landing.content', hint: 'Colours, type and corners.' },
    { key: 'pricing', label: 'Pricing & offers', n: 5, cap: 'landing.pricing', hint: 'Price, offers with real end dates, tax, refunds.' },
    { key: 'payments', label: 'Payment settings', n: 6, cap: 'landing.payments', hint: 'Providers and methods for this page.' },
    { key: 'checkout', label: 'Checkout', n: 7, cap: 'landing.payments', hint: 'Fields, consents and messages.' },
    { key: 'seo', label: 'SEO & marketing', n: 8, cap: 'landing.content', hint: 'Search, sharing, tags and WhatsApp.' },
    { key: 'publish', label: 'Preview & publish', n: 9, cap: 'landing', hint: 'Check, publish, schedule, versions.' },
];
const DRAFT_STEPS = ['sections', 'design', 'seo'];
const SITE_SKIP = ['pricing', 'payments', 'checkout'];      // a site page sells nothing
const stepsFor = (site) => (site ? STEPS.filter((s) => !SITE_SKIP.includes(s.key)).map((s, i) => ({ ...s, n: i + 1 })) : STEPS);
const tplFor = (catalog, site) => catalog.templates.filter((t) => (t.kind || 'COURSE') === (site ? 'SITE' : 'COURSE'));

const PALETTES = {
    burgundy: { label: 'Burgundy', accent: '#9C0D48', ink: '#14213D', surface: '#FBF8F4' },
    ink: { label: 'Ink & gold', accent: '#C8A15A', ink: '#111318', surface: '#F6F3EE' },
    terracotta: { label: 'Terracotta', accent: '#B4532A', ink: '#2B2118', surface: '#FFF8F1' },
    sage: { label: 'Sage', accent: '#2F6B4F', ink: '#14213D', surface: '#F5F8F4' },
};
const BACKGROUNDS = [{ value: 'plain', label: 'Plain' }, { value: 'tint', label: 'Tint' }, { value: 'dark', label: 'Dark' }, { value: 'accent', label: 'Accent' }];
const LAYOUT_LABEL = { split: 'Split', immersive: 'Immersive', editorial: 'Editorial', video: 'Video', cards: 'Cards', list: 'List', grid: 'Grid', masonry: 'Masonry',
    carousel: 'Carousel', accordion: 'Accordion', timeline: 'Timeline', columns: 'Columns', single: 'Single', stack: 'Stack', banner: 'Banner', centered: 'Centred', wide: 'Wide' };
const label = (l) => LAYOUT_LABEL[l] || (l.charAt(0).toUpperCase() + l.slice(1));
const rid = (type) => `${type.replace(/_/g, '-')}-${Math.random().toString(36).slice(2, 8)}`;
const stepFromUrl = () => {
    const m = window.location.pathname.match(/\/admin\/landing\/pages\/\d+\/([a-z]+)/);
    return m && STEPS.some((s) => s.key === m[1]) ? m[1] : 'sections';
};
const slugify = (s) => String(s || '').toLowerCase().normalize('NFKD').replace(/[̀-ͯ]/g, '').replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 120);
const dtLocal = (v) => (v ? String(v).replace(' ', 'T').slice(0, 16) : '');

/* ============================================================================================ create wizard */

function CreatePage({ lookups, catalog, defaults, toast }) {
    const [v, setV] = useState({ kind: 'COURSE', name: '', courseId: '', slug: '', campaign: '', description: '', template: (defaults && defaults.template) || catalog.templates[0].key });
    const site = v.kind === 'SITE';
    const prefix = catalog.prefix || '/landing/';
    const setKind = (kind) => setV((o) => {
        const list = tplFor(catalog, kind === 'SITE');
        return { ...o, kind, template: list.some((t) => t.key === o.template) ? o.template : (list[0] && list[0].key) || o.template };
    });
    const [slugTouched, setSlugTouched] = useState(false);
    const [errors, setErrors] = useState({});
    const [busy, setBusy] = useState(false);
    const course = lookups.courses.find((c) => String(c.id) === String(v.courseId));
    const set = (k, val) => setV((o) => {
        const n = { ...o, [k]: val };
        if (k === 'courseId') {
            const c = lookups.courses.find((x) => String(x.id) === String(val));
            if (!o.name && c) n.name = c.title;
        }
        // the address follows the name until someone types their own (the server makes it unique)
        if (!slugTouched && (k === 'name' || k === 'courseId')) n.slug = slugify(n.name);
        return n;
    });
    const create = (e) => {
        e.preventDefault();
        setBusy(true);
        setErrors({});
        axios.post('/admin/api/landing/pages', v)
            .then((r) => window.location.assign(`/admin/landing/pages/${r.data.id}/sections`))
            .catch((x) => { setBusy(false); setErrors((x.response && x.response.data && x.response.data.errors) || {}); toast(errorText(x), 'error'); });
    };
    return (
        <form onSubmit={create} className="adm-stack" noValidate data-testid="lp-create">
            <Card title="1. Basic information" subtitle="Only staff see the name. The address is what visitors see.">
                <div className="lpa-kind">
                    <span className="lpa-label">Page type</span>
                    <Segments value={v.kind} label="Page type" onChange={setKind} options={[{ value: 'COURSE', label: 'Sells a course' }, { value: 'SITE', label: 'Information page' }]} />
                    {site && <p className="adm-muted adm-small">An information page has no price or checkout: its buttons lead to courses, artists, commissions or any page you choose.</p>}
                </div>
                <div className="lpa-grid2">
                    {site ? null : (
                    <Field label="Course this page sells" name="courseId" error={errors.courseId} required>
                        <select id="f-courseId" className="adm-input" value={v.courseId} onChange={(e) => set('courseId', e.target.value)} data-testid="lp-course">
                            <option value="">Choose a course…</option>
                            {lookups.courses.map((c) => <option key={c.id} value={c.id}>{c.title}{c.artist ? ` · ${c.artist}` : ''}</option>)}
                        </select>
                    </Field>
                    )}
                    <Field label="Landing page name" name="name" error={errors.name} required hint="For example: Watercolour Landscapes · Diwali campaign.">
                        <input id="f-name" className="adm-input" value={v.name} maxLength={190} onChange={(e) => set('name', e.target.value)} data-testid="lp-name" />
                    </Field>
                    <Field label="Address" name="slug" error={errors.slug} optional
                           hint={`Made from the name; change it if you like. The page will be at ${prefix}${v.slug || '…'}. The site's own pages are never replaced.`}>
                        <div className="lpa-slug"><span>{prefix}</span>
                            <input id="f-slug" className="adm-input" value={v.slug} maxLength={120} onChange={(e) => { setSlugTouched(true); set('slug', slugify(e.target.value)); }} data-testid="lp-slug" /></div>
                    </Field>
                    <Field label="Campaign" name="campaign" error={errors.campaign} optional hint="Groups pages, coupons and orders in reports.">
                        <input id="f-campaign" className="adm-input" list="lp-campaigns" value={v.campaign} maxLength={120} onChange={(e) => set('campaign', e.target.value)} />
                        <datalist id="lp-campaigns">{(lookups.campaigns || []).map((c) => <option key={c.campaign} value={c.campaign} />)}</datalist>
                    </Field>
                </div>
                <Field label="Description" name="description" error={errors.description} optional hint="A note for the team: who the page is for, where it's promoted.">
                    <textarea id="f-description" className="adm-input" rows={2} maxLength={500} value={v.description} onChange={(e) => set('description', e.target.value)} />
                </Field>
            </Card>
            <Card title="2. Template" subtitle="Every template uses the same sections; you can switch later without losing what you wrote.">
                <div className="lpa-templates" role="radiogroup" aria-label="Template">
                    {tplFor(catalog, site).map((t) => (
                        <label key={t.key} className={cx('lpa-template', v.template === t.key && 'is-on')}>
                            <input type="radio" name="template" value={t.key} checked={v.template === t.key} onChange={() => set('template', t.key)} />
                            <TemplateThumb t={t} />
                            <b>{t.name}</b>
                            <span>{t.description}</span>
                        </label>
                    ))}
                </div>
                {errors.template && <p className="adm-danger">{errors.template}</p>}
            </Card>
            <div className="lpa-savebar">
                <span className="adm-muted">{site ? 'The page starts as a draft with the template\'s sections. Nothing is public until you publish.' : 'The page starts as a draft filled from the course. Nothing is public until you publish.'}</span>
                <Button as="a" href="/admin/landing/pages">Cancel</Button>
                <Button type="submit" variant="brand" busy={busy} icon="fa-arrow-right" data-testid="lp-create-submit">Create and continue</Button>
            </div>
        </form>
    );
}

/* ============================================================================================ editor steps */

function Basics({ data, lookups, canEdit, onSaved, toast }) {
    const p = data.page;
    const init = { name: p.name || '', courseId: String(p.course_id || ''), slug: p.slug || '', campaign: p.campaign || '', description: p.description || '' };
    const [v, setV] = useState(init);
    const [errors, setErrors] = useState({});
    const [busy, setBusy] = useState(false);
    const dirty = JSON.stringify(v) !== JSON.stringify(init);
    const course = lookups.courses.find((c) => String(c.id) === String(v.courseId));
    const save = () => {
        setBusy(true);
        setErrors({});
        axios.put(`/admin/api/landing/pages/${p.id}/basics`, v).then((r) => { toast(r.data.message); onSaved(r.data.data); })
            .catch((e) => { setErrors((e.response && e.response.data && e.response.data.errors) || {}); toast(errorText(e), 'error'); })
            .finally(() => setBusy(false));
    };
    return (
        <Card title="Basic information" subtitle="Saved straight away. Changing the address of a live page changes the link people share.">
            <fieldset disabled={!canEdit} className="lpa-fieldset">
                <div className="lpa-grid2">
                    <Field label="Landing page name" name="name" error={errors.name} required>
                        <input id="f-name" className="adm-input" value={v.name} maxLength={190} onChange={(e) => setV({ ...v, name: e.target.value })} />
                    </Field>
                    {p.site ? null : (
                    <Field label="Course" name="courseId" error={errors.courseId} required hint={data.orders > 0 ? 'People bought through this page, so the course can no longer change.' : undefined}>
                        <select id="f-courseId" className="adm-input" value={v.courseId} disabled={data.orders > 0} onChange={(e) => setV({ ...v, courseId: e.target.value })}>
                            {lookups.courses.map((c) => <option key={c.id} value={c.id}>{c.title}{c.artist ? ` · ${c.artist}` : ''}</option>)}
                        </select>
                    </Field>
                    )}
                    <Field label="Address" name="slug" error={errors.slug} required hint="The page's own address. Changing it on a live page breaks links already shared.">
                        <div className="lpa-slug"><span>/landing/</span><input id="f-slug" className="adm-input" value={v.slug} onChange={(e) => setV({ ...v, slug: slugify(e.target.value) })} /></div>
                    </Field>
                    <Field label="Campaign" name="campaign" error={errors.campaign} optional>
                        <input id="f-campaign" className="adm-input" list="lp-campaigns2" value={v.campaign} onChange={(e) => setV({ ...v, campaign: e.target.value })} />
                        <datalist id="lp-campaigns2">{(lookups.campaigns || []).map((c) => <option key={c.campaign} value={c.campaign} />)}</datalist>
                    </Field>
                </div>
                <Field label="Description" name="description" error={errors.description} optional>
                    <textarea id="f-description" className="adm-input" rows={2} maxLength={500} value={v.description} onChange={(e) => setV({ ...v, description: e.target.value })} />
                </Field>
            </fieldset>
            {canEdit && <div className="lpa-actions"><Button variant="brand" onClick={save} busy={busy} disabled={!dirty} icon="fa-check">Save basics</Button></div>}
        </Card>
    );
}

function Template({ data, catalog, canEdit, onSaved, toast, draftDirty }) {
    const [busy, setBusy] = useState(null);
    const apply = (key) => {
        setBusy(key);
        axios.put(`/admin/api/landing/pages/${data.page.id}/template`, { template: key }).then((r) => { toast(r.data.message); onSaved(r.data.data); })
            .catch((e) => toast(errorText(e), 'error')).finally(() => setBusy(null));
    };
    return (
        <Card title="Template" subtitle="Switching keeps every text, image and FAQ you wrote; it changes the layout, the look and adds any sections the template needs. It applies to the draft until you publish.">
            {draftDirty && <p className="lpa-note is-warn" role="status">Save your draft first: switching the template reloads the draft from the server.</p>}
            <div className="lpa-templates">
                {tplFor(catalog, !!data.page.site).map((t) => (
                    <div key={t.key} className={cx('lpa-template', data.page.template_key === t.key && 'is-on')}>
                        <TemplateThumb t={t} />
                        <b>{t.name}</b>
                        <span>{t.description}</span>
                        {data.page.template_key === t.key ? <Badge tone="green">In use</Badge>
                            : canEdit && <Button size="sm" onClick={() => apply(t.key)} busy={busy === t.key} disabled={!!busy || draftDirty} data-testid={`lp-template-${t.key}`}>Use this template</Button>}
                    </div>
                ))}
            </div>
        </Card>
    );
}

function Sections({ cfg, setCfg, catalog, lookups, errors, canEdit, toast, site }) {
    const [sel, setSel] = useState(cfg.sections[0] ? cfg.sections[0].id : null);
    const [drag, setDrag] = useState(null);
    const [adding, setAdding] = useState('');
    const types = useMemo(() => Object.fromEntries(catalog.sections.map((s) => [s.key, s])), [catalog]);
    const sections = cfg.sections;
    const idx = sections.findIndex((s) => s.id === sel);
    const current = idx >= 0 ? sections[idx] : null;
    const setSections = (list) => setCfg({ ...cfg, sections: list });
    const update = (s) => setSections(sections.map((x) => (x.id === s.id ? s : x)));
    const move = (from, to) => {
        if (to < 1 || to >= sections.length || from < 1) return;     // the hero stays first
        const list = [...sections];
        const [x] = list.splice(from, 1);
        list.splice(to, 0, x);
        setSections(list);
    };
    const remove = (s) => {
        const list = sections.filter((x) => x.id !== s.id);
        setSections(list);
        if (sel === s.id) setSel(list[0] && list[0].id);
    };
    const add = () => {
        if (!adding) return;
        const base = JSON.parse(JSON.stringify(catalog.defaultSections[adding]));
        base.id = rid(adding);
        const at = sections.findIndex((s) => s.type === 'footer' || s.type === 'final_cta');
        const list = [...sections];
        list.splice(at > 0 ? at : list.length, 0, base);
        setSections(list);
        setSel(base.id);
        setAdding('');
    };
    const present = new Set(sections.map((s) => s.type));
    const errFor = (i) => Object.keys(errors).some((k) => k.startsWith(`sections.${i}.`));
    const cta = cfg.cta || {};
    return (
        <div className="lpa-builder">
            <Card title="Sections" subtitle="Drag to reorder (or use the arrows). The hero stays first." className="lpa-builder__list">
                {errors.sections && <p className="adm-danger" role="alert">{errors.sections}</p>}
                <ol className="lpa-seclist" aria-label="Sections on this page">
                    {sections.map((s, i) => {
                        const t = types[s.type] || { label: s.type };
                        return (
                            <li key={s.id} className={cx('lpa-sec', sel === s.id && 'is-on', !s.enabled && 'is-off', drag === i && 'is-drag', errFor(i) && 'has-error')}
                                draggable={canEdit && i > 0} onDragStart={() => setDrag(i)} onDragEnd={() => setDrag(null)}
                                onDragOver={(e) => { if (drag !== null && i > 0) e.preventDefault(); }}
                                onDrop={(e) => { e.preventDefault(); if (drag !== null) move(drag, i); setDrag(null); }} data-testid="lp-section" data-type={s.type}>
                                <span className="lpa-sec__grip" aria-hidden="true">{i > 0 ? <i className="fa fa-bars"></i> : <i className="fa fa-thumb-tack"></i>}</span>
                                <button type="button" className="lpa-sec__name" onClick={() => setSel(s.id)} aria-current={sel === s.id ? 'true' : undefined}>
                                    <b>{t.label}</b>
                                    <small>{(s.content && s.content.heading) || t.description}{s.hideOnMobile ? ' · hidden on phones' : ''}</small>
                                </button>
                                {canEdit && (
                                    <span className="lpa-sec__tools">
                                        <Toggle checked={s.enabled} onChange={(on) => update({ ...s, enabled: on })} label={<span className="sr-only">Show {t.label}</span>} name={`on-${s.id}`} disabled={s.type === 'hero'} />
                                        <button type="button" className="lpa-icon" onClick={() => move(i, i - 1)} disabled={i < 2} aria-label={`Move ${t.label} up`}><i className="fa fa-arrow-up" aria-hidden="true"></i></button>
                                        <button type="button" className="lpa-icon" onClick={() => move(i, i + 1)} disabled={i === 0 || i === sections.length - 1} aria-label={`Move ${t.label} down`}><i className="fa fa-arrow-down" aria-hidden="true"></i></button>
                                        {s.type !== 'hero' && <button type="button" className="lpa-icon is-danger" onClick={() => remove(s)} aria-label={`Remove ${t.label}`}><i className="fa fa-trash-o" aria-hidden="true"></i></button>}
                                    </span>
                                )}
                            </li>
                        );
                    })}
                </ol>
                {canEdit && (
                    <div className="lpa-add">
                        <label htmlFor="lp-add" className="sr-only">Section to add</label>
                        <select id="lp-add" className="adm-input" value={adding} onChange={(e) => setAdding(e.target.value)} data-testid="lp-add-type">
                            <option value="">Add a section…</option>
                            {catalog.sections.filter((s) => s.key !== 'hero' && !(site && (catalog.courseOnly || []).includes(s.key))).map((s) => (
                                <option key={s.key} value={s.key} disabled={s.single && present.has(s.key)}>{s.label}{s.single && present.has(s.key) ? ' (already on the page)' : ''}</option>
                            ))}
                        </select>
                        <Button onClick={add} disabled={!adding} icon="fa-plus" data-testid="lp-add">Add</Button>
                    </div>
                )}
                <div className="lpa-cta">
                    <h3 className="lpa-h">Buttons</h3>
                    <div className="lpa-grid2">
                        <Field label={site ? 'Main button' : 'Enroll button'} name="cta.primary" error={errors['cta.primary']} max={40} counter={String(cta.primary || '').length}>
                            <input id="f-cta.primary" className="adm-input" value={cta.primary || ''} disabled={!canEdit} onChange={(e) => setCfg({ ...cfg, cta: { ...cta, primary: e.target.value } })} />
                        </Field>
                        {site && (
                            <Field label="Main button goes to" name="cta.primaryHref" error={errors['cta.primaryHref']} hint="A site address like /courses?type=all, or #section-id for a section on this page.">
                                <input id="f-cta.primaryHref" className="adm-input" value={cta.primaryHref || ''} disabled={!canEdit} onChange={(e) => setCfg({ ...cfg, cta: { ...cta, primaryHref: e.target.value } })} />
                            </Field>
                        )}
                        <Field label="Second button" name="cta.secondary" max={40} counter={String(cta.secondary || '').length} hint={cta.secondaryVideo ? 'Plays the course preview video; without one it says "Explore the course".' : 'Leave the address empty to scroll to the curriculum (or the second section).'}>
                            <input id="f-cta.secondary" className="adm-input" value={cta.secondary || ''} disabled={!canEdit} onChange={(e) => setCfg({ ...cfg, cta: { ...cta, secondary: e.target.value } })} />
                        </Field>
                        {!cta.secondaryVideo && (
                            <Field label="Second button goes to" name="cta.secondaryHref" error={errors['cta.secondaryHref']} optional>
                                <input id="f-cta.secondaryHref" className="adm-input" value={cta.secondaryHref || ''} disabled={!canEdit} onChange={(e) => setCfg({ ...cfg, cta: { ...cta, secondaryHref: e.target.value } })} />
                            </Field>
                        )}
                    </div>
                    {!site && <Toggle checked={!!cta.secondaryVideo} disabled={!canEdit} onChange={(on) => setCfg({ ...cfg, cta: { ...cta, secondaryVideo: on } })} name="cta.secondaryVideo"
                            label="Second button plays the preview video" description="Only shown as a video button when the course (or a Video section) has a preview video." />}
                    {!site && <Toggle checked={cta.sticky !== false} disabled={!canEdit} onChange={(on) => setCfg({ ...cfg, cta: { ...cta, sticky: on } })} name="cta.sticky"
                            label="Sticky enrol bar" description="A slim bar with the price and the Enroll button once the hero scrolls out of view (desktop and phones)." />}
                </div>
            </Card>
            {current ? (
                <Card title={(types[current.type] || {}).label || current.type} subtitle={(types[current.type] || {}).description} className="lpa-builder__edit">
                    <fieldset disabled={!canEdit} className="lpa-fieldset" data-testid="lp-section-editor">
                        <div className="lpa-sec-opts">
                            {(types[current.type] || { layouts: [] }).layouts.length > 1 && (
                                <div><span className="lpa-label">Layout</span>
                                    <Segments value={current.layout} label="Layout" onChange={(l) => update({ ...current, layout: l })}
                                              options={types[current.type].layouts.map((l) => ({ value: l, label: label(l) }))} /></div>
                            )}
                            <div><span className="lpa-label">Background</span>
                                <Segments value={current.background} label="Background" onChange={(b) => update({ ...current, background: b })} options={BACKGROUNDS} /></div>
                            {current.type !== 'hero' && (
                                <Toggle checked={current.hideOnMobile} onChange={(on) => update({ ...current, hideOnMobile: on })} name="hideOnMobile"
                                        label="Hide on phones" description="Keeps long sections out of the way on small screens." />
                            )}
                        </div>
                        <SectionContent section={current} onChange={update} lookups={lookups} toast={toast} errors={errors} prefix={`sections.${idx}`} />
                    </fieldset>
                </Card>
            ) : <Card><EmptyState icon="fa-columns" title="Choose a section to edit" /></Card>}
        </div>
    );
}

function Design({ cfg, setCfg, errors, canEdit }) {
    const d = cfg.design || {};
    const set = (patch) => setCfg({ ...cfg, design: { ...d, ...patch } });
    const pick = (key) => (key === 'custom' ? set({ palette: 'custom' }) : set({ palette: key, accent: PALETTES[key].accent, ink: PALETTES[key].ink, surface: PALETTES[key].surface }));
    const color = (k, labelText, hint) => (
        <Field label={labelText} name={`design.${k}`} error={errors[`design.${k}`]} hint={hint}>
            <div className="lpa-color">
                <input type="color" value={/^#[0-9a-f]{6}$/i.test(d[k] || '') ? d[k] : '#000000'} aria-label={`${labelText} colour picker`} onChange={(e) => set({ palette: 'custom', [k]: e.target.value.toUpperCase() })} />
                <input id={`f-design.${k}`} className="adm-input" value={d[k] || ''} maxLength={7} onChange={(e) => set({ palette: 'custom', [k]: e.target.value })} />
            </div>
        </Field>
    );
    return (
        <div className="lpa-designgrid">
            <Card title="Colours" subtitle="Text on the accent colour and on the surface is checked for contrast when the page is drawn.">
                <fieldset disabled={!canEdit} className="lpa-fieldset">
                    <div className="lpa-palettes" role="radiogroup" aria-label="Palette">
                        {Object.entries(PALETTES).map(([k, p]) => (
                            <label key={k} className={cx('lpa-palette', d.palette === k && 'is-on')}>
                                <input type="radio" name="palette" checked={d.palette === k} onChange={() => pick(k)} />
                                <span className="lpa-palette__sw" aria-hidden="true"><i style={{ background: p.accent }}></i><i style={{ background: p.ink }}></i><i style={{ background: p.surface }}></i></span>
                                {p.label}
                            </label>
                        ))}
                        <label className={cx('lpa-palette', d.palette === 'custom' && 'is-on')}>
                            <input type="radio" name="palette" checked={d.palette === 'custom'} onChange={() => pick('custom')} />
                            <span className="lpa-palette__sw" aria-hidden="true"><i style={{ background: d.accent }}></i><i style={{ background: d.ink }}></i><i style={{ background: d.surface }}></i></span>
                            Custom
                        </label>
                    </div>
                    <div className="lpa-grid3">
                        {color('accent', 'Accent', 'Buttons, links, highlights.')}
                        {color('ink', 'Text', 'Headings and body text.')}
                        {color('surface', 'Page background')}
                    </div>
                </fieldset>
            </Card>
            <Card title="Type and shape">
                <fieldset disabled={!canEdit} className="lpa-fieldset">
                    <span className="lpa-label">Headings</span>
                    <Segments value={d.font} label="Heading font" onChange={(f) => set({ font: f })} options={[{ value: 'jakarta', label: 'Plus Jakarta Sans' }, { value: 'serif', label: 'Editorial serif' }]} />
                    <span className="lpa-label">Corners</span>
                    <Segments value={d.radius} label="Corners" onChange={(r) => set({ radius: r })} options={[{ value: 'soft', label: 'Soft' }, { value: 'sharp', label: 'Sharp' }]} />
                </fieldset>
                <div className={cx('lpa-swatch', d.font === 'serif' && 'is-serif', d.radius === 'sharp' && 'is-sharp')} style={{ background: d.surface, color: d.ink }} aria-label="Sample">
                    <small style={{ color: d.accent }}>Live course</small>
                    <b>Watercolour Landscapes</b>
                    <span>Paint light, sky and water with confidence.</span>
                    <i style={{ background: d.accent }}>Enroll now</i>
                </div>
            </Card>
        </div>
    );
}

function Pricing({ data, canEdit, onSaved, toast }) {
    const toForm = (p) => ({ listPrice: p.listPrice ?? '', price: p.price ?? '', priceUsd: p.priceUsd ?? '', promoLabel: p.promoLabel || '', promoPrice: p.promoPrice ?? '',
        promoStartsAt: dtLocal(p.promoStartsAt), promoEndsAt: dtLocal(p.promoEndsAt), earlyPrice: p.earlyPrice ?? '', earlyEndsAt: dtLocal(p.earlyEndsAt),
        couponsEnabled: p.couponsEnabled !== false, taxMode: p.taxMode || 'INCLUDED', taxRate: p.taxRate ?? '0', taxLabel: p.taxLabel || '', accessDuration: p.accessDuration || '',
        refundPolicy: p.refundPolicy || '', inclusions: p.inclusions ? String(p.inclusions).split('\n').filter(Boolean) : [] });
    const init = toForm(data.pricing || {});
    const [v, setV] = useState(init);
    const [quote, setQuote] = useState(data.quote);
    const [errors, setErrors] = useState({});
    const [busy, setBusy] = useState(false);
    const dirty = JSON.stringify(v) !== JSON.stringify(init);
    const cp = data.coursePrice || [];
    const save = () => {
        setBusy(true);
        setErrors({});
        axios.put(`/admin/api/landing/pages/${data.page.id}/pricing`, v).then((r) => { toast(r.data.message); setQuote(r.data.quote); onSaved({ pricing: r.data.pricing, quote: r.data.quote }); })
            .catch((e) => { setErrors((e.response && e.response.data && e.response.data.errors) || {}); toast(errorText(e), 'error'); })
            .finally(() => setBusy(false));
    };
    const money = (k, l, hint) => (
        <Field label={l} name={k} error={errors[k]} hint={hint} optional>
            <div className="lpa-money"><span aria-hidden="true">{k === 'priceUsd' ? '$' : '₹'}</span>
                <input id={`f-${k}`} className="adm-input" inputMode="decimal" value={v[k]} onChange={(e) => setV({ ...v, [k]: e.target.value.replace(/[^\d.]/g, '') })} data-testid={`lp-${k}`} /></div>
        </Field>
    );
    const when = (k, l) => (
        <Field label={l} name={k} error={errors[k]} hint="India time (IST)." optional>
            <input id={`f-${k}`} type="datetime-local" className="adm-input" value={v[k]} onChange={(e) => setV({ ...v, [k]: e.target.value })} />
        </Field>
    );
    return (
        <div className="lpa-pricegrid">
            <div className="adm-stack">
                <Card title="Price" subtitle={`The course's own price is ${inr(cp[0])}${cp[1] ? ` / $${cp[1]}` : ''}. Leave a box empty to use it. Prices are saved for this page only and apply at checkout straight away.`}>
                    <fieldset disabled={!canEdit} className="lpa-fieldset"><div className="lpa-grid3">
                        {money('price', 'Price on this page', 'What buyers pay without an offer.')}
                        {money('listPrice', 'Original price (struck through)', 'Only if it is a price you really charged.')}
                        {money('priceUsd', 'Price in US dollars', 'For PayPal and international cards.')}
                    </div></fieldset>
                </Card>
                <Card title="Limited-time offer" subtitle="Shown with its real end date; the price goes back by itself when it ends. No endless timers.">
                    <fieldset disabled={!canEdit} className="lpa-fieldset"><div className="lpa-grid3">
                        <Field label="Offer name" name="promoLabel" error={errors.promoLabel} optional hint="For example: Diwali offer.">
                            <input id="f-promoLabel" className="adm-input" maxLength={80} value={v.promoLabel} onChange={(e) => setV({ ...v, promoLabel: e.target.value })} />
                        </Field>
                        {money('promoPrice', 'Offer price')}
                        <span />
                        {when('promoStartsAt', 'Starts')}
                        {when('promoEndsAt', 'Ends')}
                    </div></fieldset>
                </Card>
                <Card title="Early-bird price" subtitle="A lower price until a date, usually before the course starts.">
                    <fieldset disabled={!canEdit} className="lpa-fieldset"><div className="lpa-grid3">
                        {money('earlyPrice', 'Early-bird price')}
                        {when('earlyEndsAt', 'Ends')}
                    </div></fieldset>
                </Card>
                <Card title="Tax, coupons, access and refunds">
                    <fieldset disabled={!canEdit} className="lpa-fieldset">
                        <Field label="Tax" name="taxMode"><Segments value={v.taxMode} label="Tax" onChange={(m) => setV({ ...v, taxMode: m })}
                            options={[{ value: 'INCLUDED', label: 'Included in the price' }, { value: 'ADDED', label: 'Added at checkout' }]} /></Field>
                        <div className="lpa-grid3">
                            <Field label="Tax rate (%)" name="taxRate" error={errors.taxRate}>
                                <input id="f-taxRate" className="adm-input" inputMode="decimal" value={v.taxRate} onChange={(e) => setV({ ...v, taxRate: e.target.value.replace(/[^\d.]/g, '') })} /></Field>
                            <Field label="Tax name" name="taxLabel" optional hint="For example: GST.">
                                <input id="f-taxLabel" className="adm-input" maxLength={40} value={v.taxLabel} onChange={(e) => setV({ ...v, taxLabel: e.target.value })} /></Field>
                        </div>
                        <Toggle checked={v.couponsEnabled} onChange={(on) => setV({ ...v, couponsEnabled: on })} name="couponsEnabled" label="Coupons can be used on this page"
                                description="Codes are managed in Coupons & promotions." />
                        <Field label="Access" name="accessDuration" error={errors.accessDuration} optional hint="For example: Recordings for 12 months.">
                            <input id="f-accessDuration" className="adm-input" maxLength={120} value={v.accessDuration} onChange={(e) => setV({ ...v, accessDuration: e.target.value })} /></Field>
                        <Field label="Refund policy" name="refundPolicy" error={errors.refundPolicy} optional counter={v.refundPolicy.length} max={1000}>
                            <textarea id="f-refundPolicy" className="adm-input" rows={3} value={v.refundPolicy} onChange={(e) => setV({ ...v, refundPolicy: e.target.value })} /></Field>
                        <Field label="What's included" name="inclusions" error={errors.inclusions} optional hint="Listed next to the price. Leave empty to use the course's own list.">
                            <ListEditor value={v.inclusions} onChange={(l) => setV({ ...v, inclusions: l })} placeholder="Add an inclusion" name="inclusions" max={20} />
                        </Field>
                    </fieldset>
                </Card>
            </div>
            <aside className="lpa-quote" aria-live="polite" data-testid="lp-quote">
                <span className="lpa-label">Buyers see now</span>
                {quote && quote.listLabel && <s>{quote.listLabel}</s>}
                <b>{quote ? quote.totalLabel : '—'}</b>
                {quote && quote.savingPct && <Badge tone="green">{quote.savingPct}</Badge>}
                {quote && quote.offerEndsLabel && <small>Offer ends {quote.offerEndsLabel}</small>}
                {quote && quote.taxNote && <small>{quote.taxNote}</small>}
                {quote && quote.usdLabel && <small>Abroad: {quote.usdLabel}</small>}
                {canEdit && <Button variant="brand" onClick={save} busy={busy} disabled={!dirty} icon="fa-check" data-testid="lp-pricing-save">Save pricing</Button>}
                {dirty && <small className="adm-muted">Unsaved changes: the figure above updates after saving.</small>}
            </aside>
        </div>
    );
}

function Checkout({ data, canEdit, onSaved, toast }) {
    const init = { ...data.checkout, successMessage: data.checkout.successMessage || '', successRedirect: data.checkout.successRedirect || '',
        failureMessage: data.checkout.failureMessage || '', pendingMessage: data.checkout.pendingMessage || '' };
    const [v, setV] = useState(init);
    const [errors, setErrors] = useState({});
    const [busy, setBusy] = useState(false);
    const dirty = JSON.stringify(v) !== JSON.stringify(init);
    const save = () => {
        setBusy(true);
        setErrors({});
        axios.put(`/admin/api/landing/pages/${data.page.id}/checkout`, v).then((r) => { toast(r.data.message); onSaved({ checkout: r.data.checkout }); })
            .catch((e) => { setErrors((e.response && e.response.data && e.response.data.errors) || {}); toast(errorText(e), 'error'); })
            .finally(() => setBusy(false));
    };
    const tog = (k, l, d, lock) => <Toggle checked={!!v[k]} onChange={(on) => setV({ ...v, [k]: on })} name={k} label={l} description={d} disabled={!canEdit || lock} />;
    const msg = (k, l, hint) => (
        <Field label={l} name={k} error={errors[k]} hint={hint} optional counter={v[k].length} max={500}>
            <textarea id={`f-${k}`} className="adm-input" rows={2} value={v[k]} disabled={!canEdit} onChange={(e) => setV({ ...v, [k]: e.target.value })} />
        </Field>
    );
    return (
        <div className="adm-stack">
            <Card title="Details we ask for" subtitle="Name and e-mail are always required (they make the account and the receipt).">
                <div className="lpa-toggles">
                    {tog('requirePhone', 'Phone number', 'For class reminders on WhatsApp. Kept on the order, never used to sign in.')}
                    {tog('requireAddress', 'Billing address', 'Only if your accountant needs it for invoices.')}
                    {tog('requireTerms', 'Accept the terms', 'A required tick box linking to the terms of use.')}
                    {tog('requirePrivacy', 'Privacy consent', 'A required tick box linking to the privacy policy.')}
                    {tog('showCoupon', 'Coupon box', data.pricing && data.pricing.couponsEnabled === false ? 'Coupons are switched off in Pricing, so the box stays hidden.' : 'Lets buyers enter a code.')}
                    {tog('showSummary', 'Order summary', 'Price, discount, tax and total next to the form.')}
                </div>
            </Card>
            <Card title="After paying" subtitle="Access is only given after the gateway confirms the payment on our server.">
                {msg('successMessage', 'Success message', 'Leave empty for: "You\'re enrolled. We\'ve e-mailed your receipt."')}
                <Field label="Then send buyers to" name="successRedirect" error={errors.successRedirect} optional hint="A page on this site, e.g. /dashboard. Leave empty to stay on the confirmation.">
                    <input id="f-successRedirect" className="adm-input" value={v.successRedirect} disabled={!canEdit} onChange={(e) => setV({ ...v, successRedirect: e.target.value })} />
                </Field>
                {msg('pendingMessage', 'Payment pending', 'When the bank hasn\'t confirmed yet (UPI can take a few minutes).')}
                {msg('failureMessage', 'Payment failed', 'Shown with a "Try again" button; nothing was charged.')}
            </Card>
            {canEdit && <div className="lpa-savebar"><Button variant="brand" onClick={save} busy={busy} disabled={!dirty} icon="fa-check" data-testid="lp-checkout-save">Save checkout settings</Button></div>}
        </div>
    );
}

function Seo({ cfg, setCfg, data, errors, canEdit }) {
    const s = cfg.seo || {};
    const m = cfg.marketing || {};
    const setS = (patch) => setCfg({ ...cfg, seo: { ...s, ...patch } });
    const setM = (patch) => setCfg({ ...cfg, marketing: { ...m, ...patch } });
    const course = data.course || {};
    const title = s.title || `${course.title || data.page.name} | ArtistikCity`;
    const desc = s.description || course.subTitle || course.intro || '';
    const text = (k, l, max, hint, rows, obj = s, set = setS, pre = 'seo') => (
        <Field label={l} name={`${pre}.${k}`} error={errors[`${pre}.${k}`]} hint={hint} optional max={max} counter={max ? String(obj[k] || '').length : undefined}>
            {rows ? <textarea id={`f-${pre}.${k}`} className="adm-input" rows={rows} maxLength={max} value={obj[k] || ''} onChange={(e) => set({ [k]: e.target.value })} />
                : <input id={`f-${pre}.${k}`} className="adm-input" maxLength={max} value={obj[k] || ''} onChange={(e) => set({ [k]: e.target.value })} />}
        </Field>
    );
    return (
        <fieldset disabled={!canEdit} className="lpa-fieldset adm-stack">
            <Card title="Search" subtitle={data.page.site ? 'Empty boxes use the page name and the hero tagline.' : "Empty boxes use the course's title and summary."}>
                <div className="lpa-serp" aria-label="Search result preview">
                    <small>artistikcity.com{String(data.page.publicUrl || '').replace(/\//g, ' › ')}</small>
                    <b>{title.slice(0, 70)}</b>
                    <span>{String(desc).slice(0, 170)}</span>
                </div>
                {text('title', 'Page title', 70, 'Around 50–60 characters reads best in search results.')}
                {text('description', 'Meta description', 170, undefined, 2)}
                {text('canonical', 'Canonical address', undefined, 'Only for a copy that should credit another page, e.g. /landing/watercolour-landscapes-for-beginners.')}
                <div className="lpa-toggles">
                    <Toggle checked={s.index !== false} onChange={(on) => setS({ index: on })} name="seo.index" label="Show in search engines" description="Off adds noindex, e.g. for paid-ads-only pages." />
                    <Toggle checked={s.sitemap !== false} onChange={(on) => setS({ sitemap: on })} name="seo.sitemap" label="List in the sitemap" />
                    <Toggle checked={s.structuredData !== false} onChange={(on) => setS({ structuredData: on })} name="seo.structuredData" label="Course structured data"
                            description="Tells search engines the course name, provider, price and rating (only real reviews are counted)." />
                </div>
            </Card>
            <Card title="Sharing" subtitle="What WhatsApp, Facebook and LinkedIn show when the link is shared.">
                {text('ogTitle', 'Share title', 90)}
                {text('ogDescription', 'Share description', 200, undefined, 2)}
                {text('ogImage', 'Share image', undefined, 'An image address from the media library (1200 × 630 works everywhere). Empty uses the hero image.')}
            </Card>
            <Card title="Marketing tags and support" subtitle="Tags only load after a visitor accepts cookies. Enrolments and checkout steps are sent as events.">
                <div className="lpa-grid3">
                    {text('ga4', 'Google Analytics 4', undefined, 'Measurement id, e.g. G-ABC123XYZ.', 0, m, setM, 'marketing')}
                    {text('gtm', 'Google Tag Manager', undefined, 'Container id, e.g. GTM-ABC1234.', 0, m, setM, 'marketing')}
                    {text('metaPixel', 'Meta Pixel', undefined, 'Numeric pixel id.', 0, m, setM, 'marketing')}
                </div>
                {text('whatsapp', 'WhatsApp support number', undefined, 'With country code, e.g. +919876543210. Adds a "Questions? Chat with us" button.', 0, m, setM, 'marketing')}
                <Toggle checked={!!m.leadCapture} onChange={(on) => setM({ leadCapture: on })} name="marketing.leadCapture" label="Optional lead capture"
                        description="A small 'Join the newsletter' box at the end of the page (never a pop-up), with a consent tick. Sign-ups appear in Admin › Newsletter, tagged with this page." />
            </Card>
        </fieldset>
    );
}

/* ---------------------------------------------------------------------------------------------- publish */

function availableMethods(payments) {
    // the server decides: enabled for the page, supported by a provider switched on for it, and that provider connected
    return ((payments && payments.methods) || []).filter((m) => m.live);
}

function Publish({ data, caps, draftDirty, saveDraft, onSaved, toast }) {
    const p = data.page;
    const [note, setNote] = useState('');
    const [when, setWhen] = useState(dtLocal(p.publish_at));
    const [busy, setBusy] = useState(null);
    const [errors, setErrors] = useState({});
    const [frame, setFrame] = useState('desktop');
    const [dialog, confirm] = useConfirm();
    const canPublish = has(caps, 'landing.publish');
    const methods = availableMethods(data.payments);
    const checks = p.site ? [
        { ok: !draftDirty, text: draftDirty ? 'You have unsaved changes in the draft.' : 'Draft saved.', fix: draftDirty ? 'Save draft' : null },
        { ok: true, text: `Goes live at ${p.fullUrl || p.publicUrl}.` },
        { ok: !!(data.config.seo && data.config.seo.description), text: 'Has a search description.', warn: true, step: 'seo' },
    ] : [
        { ok: !draftDirty, text: draftDirty ? 'You have unsaved changes in the draft.' : 'Draft saved.', fix: draftDirty ? 'Save draft' : null },
        { ok: methods.length > 0 || (data.quote && data.quote.totalLabel === 'Free'), text: methods.length ? `Buyers can pay with: ${methods.map((m) => m.label).join(', ')}.` : 'No payment method is available: buyers could not pay.', step: 'payments' },
        { ok: data.course && data.course.title, text: data.course && data.course.title ? `Sells "${data.course.title}".` : 'The course is missing.' },
        { ok: !!(data.config.seo && (data.config.seo.description || (data.course && (data.course.subTitle || data.course.intro)))), text: 'Has a search description.', warn: true, step: 'seo' },
    ];
    const blocked = checks.some((c) => !c.ok && !c.warn);
    const act = async (action, body = {}) => {
        if (action === 'unpublish' && !(await confirm({ title: 'Unpublish this page?', body: `${data.page.publicUrl} stops working until you publish again.`, confirmLabel: 'Unpublish' }))) return;
        if (action === 'archive' && !(await confirm({ title: 'Archive this page?', body: 'It disappears from the lists and its address stops working. Orders and analytics are kept.', confirmLabel: 'Archive' }))) return;
        setBusy(action);
        setErrors({});
        axios.post(`/admin/api/landing/pages/${p.id}/${action}`, body)
            .then((r) => {
                toast(r.data.message);
                if (action === 'duplicate') window.location.assign(`/admin/landing/pages/${r.data.id}/basics`);
                else onSaved(r.data.data);
                if (action === 'publish') setNote('');
            })
            .catch((e) => { setErrors((e.response && e.response.data && e.response.data.errors) || {}); toast(errorText(e), 'error'); })
            .finally(() => setBusy(null));
    };
    const remove = async () => {
        if (!(await confirm({ title: `Delete "${p.name}"?`, body: 'This cannot be undone.', confirmLabel: 'Delete', danger: true }))) return;
        axios.delete(`/admin/api/landing/pages/${p.id}`).then(() => window.location.assign('/admin/landing/pages')).catch((e) => toast(errorText(e), 'error'));
    };
    const rollback = async (v) => {
        if (!(await confirm({ title: `Make version ${v.version_no} live again?`, body: 'It is published as a new version, so nothing is lost. Your current draft is replaced by it.', confirmLabel: 'Restore and publish' }))) return;
        axios.post(`/admin/api/landing/pages/${p.id}/versions/${v.id}/restore`).then((r) => { toast(r.data.message); onSaved(r.data.data); }).catch((e) => toast(errorText(e), 'error'));
    };
    const previewSrc = `/admin/landing/preview/${p.id}?t=${encodeURIComponent(p.draft_saved_at || '')}`;
    return (
        <div className="adm-stack">
            <div className="lpa-publishgrid">
                <Card title="Ready to publish?" subtitle="Publishing makes the saved draft live as a new version. Prices, payments and checkout settings are already live.">
                    <ul className="lpa-checklist" data-testid="lp-checklist">
                        {checks.map((c, i) => (
                            <li key={i} className={c.ok ? 'is-ok' : c.warn ? 'is-warn' : 'is-bad'}>
                                <i className={`fa ${c.ok ? 'fa-check-circle' : c.warn ? 'fa-exclamation-circle' : 'fa-times-circle'}`} aria-hidden="true"></i>
                                <span>{c.ok || !c.warn ? c.text : `Suggestion: ${c.text.toLowerCase()}`}</span>
                                {c.fix && <Button size="sm" onClick={saveDraft}>{c.fix}</Button>}
                                {!c.ok && c.step && <a href={`/admin/landing/pages/${p.id}/${c.step}`}>Fix</a>}
                            </li>
                        ))}
                    </ul>
                    {canPublish ? (
                        <>
                            <Field label="What changed?" name="note" optional hint="Kept with the version, so the team can find it later.">
                                <input id="f-note" className="adm-input" maxLength={255} value={note} onChange={(e) => setNote(e.target.value)} placeholder="e.g. New Diwali offer and gallery" />
                            </Field>
                            {Object.values(errors).length > 0 && <ul className="adm-danger" role="alert">{Object.values(errors).map((e) => <li key={e}>{e}</li>)}</ul>}
                            <div className="lpa-actions">
                                {p.status !== 'ARCHIVED' && <Button variant="brand" icon="fa-globe" onClick={() => act('publish', { note })} busy={busy === 'publish'} disabled={blocked || !!busy} data-testid="lp-publish">
                                    {p.status === 'PUBLISHED' ? 'Publish changes' : 'Publish now'}</Button>}
                                {p.status === 'PUBLISHED' && <Button icon="fa-eye-slash" onClick={() => act('unpublish')} busy={busy === 'unpublish'} data-testid="lp-unpublish">Unpublish</Button>}
                            </div>
                            {p.status !== 'PUBLISHED' && p.status !== 'ARCHIVED' && (
                                <div className="lpa-schedule">
                                    <Field label="Or publish later" name="publishAt" error={errors.publishAt} hint="India time (IST). The saved draft at that moment goes live.">
                                        <input id="f-publishAt" type="datetime-local" className="adm-input" value={when} onChange={(e) => setWhen(e.target.value)} data-testid="lp-when" />
                                    </Field>
                                    <Button icon="fa-clock-o" onClick={() => act('schedule', { publishAt: when })} disabled={!when || blocked || !!busy} busy={busy === 'schedule'} data-testid="lp-schedule">Schedule</Button>
                                </div>
                            )}
                            <hr className="lpa-hr" />
                            <div className="lpa-actions">
                                <Button size="sm" icon="fa-files-o" onClick={() => act('duplicate')} busy={busy === 'duplicate'}>Duplicate</Button>
                                {p.status === 'ARCHIVED' ? <Button size="sm" icon="fa-undo" onClick={() => act('restore')}>Restore as draft</Button>
                                    : p.status !== 'PUBLISHED' && <Button size="sm" icon="fa-archive" onClick={() => act('archive')}>Archive</Button>}
                                {p.status !== 'PUBLISHED' && data.orders === 0 && <Button size="sm" variant="danger" icon="fa-trash-o" onClick={remove}>Delete</Button>}
                            </div>
                        </>
                    ) : <p className="adm-muted">Ask someone with publishing rights to publish your changes.</p>}
                </Card>
                <Card title="Preview" subtitle="The saved draft, drawn exactly like the public page." actions={
                    <Segments value={frame} label="Device" onChange={setFrame} options={[{ value: 'desktop', label: 'Desktop' }, { value: 'mobile', label: 'Phone' }]} />} className="lpa-previewcard">
                    {draftDirty && <p className="lpa-note is-warn">Showing the last saved draft. Save to see your latest changes.</p>}
                    <div className={cx('lpa-frame', `is-${frame}`)}>
                        <iframe key={previewSrc + frame} title={`Preview (${frame})`} src={previewSrc} loading="lazy" data-testid="lp-preview" />
                    </div>
                    <a href={previewSrc} target="_blank" rel="noopener noreferrer" className="lpa-ext">Open the preview in a new tab <i className="fa fa-external-link" aria-hidden="true"></i></a>
                </Card>
            </div>
            <Card title="Version history" subtitle="Every publish is kept. Preview any version, or make it live again." flush>
                <DataTable rows={data.versions || []} columns={[
                    { key: 'version_no', label: 'Version', render: (v) => <b>v{v.version_no}{String(v.id) === String(p.published_version_id) && p.status === 'PUBLISHED' ? <> <Badge tone="green">Live</Badge></> : null}</b> },
                    { key: 'created_at', label: 'Published', render: (v) => fmtDate(v.created_at, true) },
                    { key: 'by', label: 'By', render: (v) => v.created_by_name || '—' },
                    { key: 'note', label: 'Note', render: (v) => v.note || <span className="adm-muted">—</span> },
                    { key: 'a', label: '', render: (v) => (
                        <span className="lpa-actions is-tight">
                            <Button as="a" size="sm" href={`/admin/landing/preview/${p.id}?version=${v.id}`} target="_blank" rel="noopener noreferrer" icon="fa-eye">Preview</Button>
                            {canPublish && !(String(v.id) === String(p.published_version_id) && p.status === 'PUBLISHED') && <Button size="sm" icon="fa-undo" onClick={() => rollback(v)} data-testid="lp-rollback">Make live</Button>}
                        </span>
                    ) },
                ]} empty={<EmptyState icon="fa-history" title="Not published yet">The first publish creates version 1.</EmptyState>} />
            </Card>
            <Card title="Activity" subtitle="Who changed what on this page (from the audit log).">
                {(data.history || []).length === 0 ? <p className="adm-muted">Nothing yet.</p> : (
                    <ul className="lpa-activity">
                        {data.history.map((h, i) => <li key={i}><time>{fmtDate(h.created_at, true)}</time> <b>{h.admin_name || 'System'}</b> {h.details}</li>)}
                    </ul>
                )}
            </Card>
            {dialog}
        </div>
    );
}

/* ================================================================================================= editor */

function EditPage({ pageId, caps, lookups, catalog, toast }) {
    const [data, setData] = useState(null);
    const [cfg, setCfg] = useState(null);
    const [savedCfg, setSavedCfg] = useState(null);
    const [step, setStep] = useState(stepFromUrl());
    const [errors, setErrors] = useState({});
    const [saving, setSaving] = useState(false);
    const [loadError, setLoadError] = useState(null);
    const [dialog, confirm] = useConfirm();
    const savedAt = useRef(null);

    const take = (d) => {
        setData(d);
        setCfg(d.config);
        setSavedCfg(d.config);
        savedAt.current = d.page.draft_saved_at;
    };
    useEffect(() => {
        axios.get(`/admin/api/landing/pages/${pageId}`).then((r) => take(r.data)).catch((e) => setLoadError(errorText(e)));
    }, [pageId]);
    const draftDirty = !!cfg && JSON.stringify(cfg) !== JSON.stringify(savedCfg);
    useEffect(() => {
        const warn = (e) => { if (draftDirty) { e.preventDefault(); e.returnValue = ''; } };
        window.addEventListener('beforeunload', warn);
        return () => window.removeEventListener('beforeunload', warn);
    }, [draftDirty]);
    useEffect(() => {
        const pop = () => setStep(stepFromUrl());
        window.addEventListener('popstate', pop);
        return () => window.removeEventListener('popstate', pop);
    }, []);
    const go = (key) => {
        setStep(key);
        window.history.pushState(window.history.state, '', `/admin/landing/pages/${pageId}/${key}`);
        window.scrollTo(0, 0);
    };

    const saveDraft = async (force = false) => {
        setSaving(true);
        setErrors({});
        try {
            const r = await axios.put(`/admin/api/landing/pages/${pageId}/draft`, { config: cfg, savedAt: savedAt.current, force });
            savedAt.current = r.data.savedAt;
            setCfg(r.data.config);
            setSavedCfg(r.data.config);
            setData((d) => ({ ...d, config: r.data.config, hasUnpublishedChanges: r.data.hasUnpublishedChanges, page: { ...d.page, draft_saved_at: r.data.savedAt } }));
            toast(r.data.message);
        } catch (e) {
            if (e.response && e.response.status === 409 && !force) {
                const ok = await confirm({ title: 'Someone else saved this page', body: errorText(e), confirmLabel: 'Save mine anyway', cancelLabel: 'Keep editing' });
                if (ok) { setSaving(false); return saveDraft(true); }
            } else {
                setErrors((e.response && e.response.data && e.response.data.errors) || {});
                toast(errorText(e), 'error');
            }
        } finally {
            setSaving(false);
        }
        return null;
    };

    if (loadError) return <Card><EmptyState icon="fa-exclamation-triangle" title="Can't open this page">{loadError}</EmptyState></Card>;
    if (!data || !cfg) return <Card><p className="adm-muted">Loading…</p></Card>;   // React 17 doesn't batch updates in promises
    const p = data.page;
    const st = STATUS[p.status] || STATUS.DRAFT;
    const steps = stepsFor(!!p.site);
    const current = steps.find((s) => s.key === step) || steps[2];
    const canEdit = has(caps, current.cap);
    const isDraftStep = DRAFT_STEPS.includes(current.key);
    const errorCount = Object.keys(errors).length;
    const merge = (patch) => setData((d) => ({ ...d, ...patch }));

    return (
        <div className="lpa-editor" data-testid="lp-editor" data-page-id={p.id}>
            <div className="lpa-ehead">
                <div>
                    <span className="adm-eyebrow">Landing page</span>
                    <h1>{p.name}</h1>
                    <p className="lpa-ehead__meta">
                        <Badge tone={st.tone} dot>{st.label}</Badge>
                        {data.hasUnpublishedChanges && p.status === 'PUBLISHED' && <Badge tone="amber">Unpublished changes</Badge>}
                        {p.status === 'SCHEDULED' && p.publish_at && <span>Goes live {fmtDate(p.publish_at, true)}</span>}
                        <span className="lpa-url" data-testid="lp-url">{p.status === 'PUBLISHED' ? <a href={p.publicUrl} target="_blank" rel="noopener noreferrer">{p.fullUrl || p.publicUrl}</a> : <>{p.fullUrl || p.publicUrl} <small>(after publishing)</small></>}
                            <button type="button" className="lpa-copy" aria-label="Copy the page address" onClick={() => { try { navigator.clipboard.writeText(p.fullUrl || p.publicUrl); toast('Address copied.'); } catch (e) { /* not allowed */ } }}><i className="fa fa-clipboard" aria-hidden="true"></i></button></span>
                        <span>{p.site ? 'Information page' : p.course_title}</span>
                    </p>
                </div>
                <div className="lpa-ehead__actions">
                    <Button as="a" size="sm" href={`/admin/landing/preview/${p.id}`} target="_blank" rel="noopener noreferrer" icon="fa-eye">Preview</Button>
                    {p.status === 'PUBLISHED' && <Button as="a" size="sm" href={p.publicUrl} target="_blank" rel="noopener noreferrer" icon="fa-external-link">View live</Button>}
                    {has(caps, 'landing.content') && <Button variant={draftDirty ? 'brand' : 'line'} size="sm" icon="fa-floppy-o" onClick={() => saveDraft()} busy={saving} disabled={!draftDirty} data-testid="lp-save-draft">
                        {draftDirty ? 'Save draft' : 'Draft saved'}</Button>}
                </div>
            </div>
            <div className="lpa-ebody">
                <nav className="lpa-steps" aria-label="Steps">
                    <ol>
                        {steps.map((s) => (
                            <li key={s.key}>
                                <a href={`/admin/landing/pages/${p.id}/${s.key}`} className={cx(step === s.key && 'is-on', !has(caps, s.cap) && 'is-locked')} aria-current={step === s.key ? 'step' : undefined}
                                   onClick={(e) => { e.preventDefault(); go(s.key); }} data-step={s.key}>
                                    <span className="lpa-steps__n">{s.n}</span>
                                    <span><b>{s.label}</b><small>{s.hint}</small></span>
                                    {!has(caps, s.cap) && <i className="fa fa-lock" aria-label="View only"></i>}
                                    {DRAFT_STEPS.includes(s.key) && draftDirty && <i className="lpa-dot" aria-label="Unsaved changes"></i>}
                                </a>
                            </li>
                        ))}
                    </ol>
                    <p className="lpa-steps__note"><i className="fa fa-info-circle" aria-hidden="true"></i> {p.site ? 'Sections, design and SEO change the draft (live after publishing).' : 'Steps 3, 4 and 8 change the draft (live after publishing). Pricing, payments and checkout save straight to the page.'}</p>
                </nav>
                <div className="lpa-estep">
                    <div className="lpa-estep__head">
                        <h2>{current.n}. {current.label}</h2>
                        {!canEdit && <Badge>View only</Badge>}
                    </div>
                    {errorCount > 0 && isDraftStep && (
                        <div className="lpa-note is-bad" role="alert">The draft wasn't saved: {errorCount === 1 ? 'one field needs' : `${errorCount} fields need`} a fix ({Object.values(errors)[0]}).</div>
                    )}
                    {current.key === 'basics' && <Basics key={p.updated_at} data={data} lookups={lookups} canEdit={canEdit} onSaved={take} toast={toast} />}
                    {current.key === 'template' && <Template data={data} catalog={catalog} canEdit={canEdit} onSaved={take} toast={toast} draftDirty={draftDirty} />}
                    {current.key === 'sections' && <Sections cfg={cfg} setCfg={setCfg} catalog={catalog} lookups={{ ...lookups, site: !!p.site }} errors={errors} canEdit={canEdit} toast={toast} site={!!p.site} />}
                    {current.key === 'design' && <Design cfg={cfg} setCfg={setCfg} errors={errors} canEdit={canEdit} />}
                    {current.key === 'pricing' && <Pricing data={data} canEdit={canEdit} onSaved={merge} toast={toast} />}
                    {current.key === 'payments' && (
                        <Card title="Payment settings for this page" subtitle="Use the global defaults, or choose exactly which providers and methods buyers see on this page. Another page is never affected.">
                            <PaymentSettings value={data.payments} toast={toast} canEdit={canEdit}
                                             onSave={(body) => axios.put(`/admin/api/landing/pages/${p.id}/payments`, body).then((r) => { toast(r.data.message); merge({ payments: r.data.payments }); return r.data.payments; })} />
                        </Card>
                    )}
                    {current.key === 'checkout' && <Checkout data={data} canEdit={canEdit} onSaved={merge} toast={toast} />}
                    {current.key === 'seo' && <Seo cfg={cfg} setCfg={setCfg} data={data} errors={errors} canEdit={canEdit} />}
                    {current.key === 'publish' && <Publish data={data} caps={caps} draftDirty={draftDirty} saveDraft={() => saveDraft()} onSaved={take} toast={toast} />}
                    <div className="lpa-stepnav">
                        {current.n > 1 && <Button icon="fa-arrow-left" onClick={() => go(steps[current.n - 2].key)}>{steps[current.n - 2].label}</Button>}
                        <span />
                        {isDraftStep && has(caps, 'landing.content') && draftDirty && <Button variant="brand" icon="fa-floppy-o" onClick={() => saveDraft()} busy={saving}>Save draft</Button>}
                        {current.n < steps.length && <Button variant={isDraftStep && draftDirty ? 'line' : 'dark'} onClick={() => go(steps[current.n].key)} data-testid="lp-next">
                            Next: {steps[current.n].label} <i className="fa fa-arrow-right" aria-hidden="true"></i></Button>}
                    </div>
                </div>
            </div>
            {dialog}
        </div>
    );
}

export default function LandingEditor() {
    const { pageId, caps, lookups, catalog, defaults } = usePage().props;
    const [toastNode, toast] = useToast();
    const isNew = !pageId;
    return (
        <AdminLayout title={isNew ? 'Create landing page' : 'Edit landing page'} wide
                     crumbs={[{ label: 'Landing pages', href: '/admin/landing' }, { label: 'All landing pages', href: '/admin/landing/pages' }, { label: isNew ? 'Create' : 'Edit' }]}>
            {isNew ? (
                <>
                    <PageHeader eyebrow="Landing Page Management" title="Create landing page" subtitle="Choose the page type and a template. The page gets its own address under /landing/, made from its name." />
                    {has(caps, 'landing.publish') ? <CreatePage lookups={lookups} catalog={catalog} defaults={defaults} toast={toast} />
                        : <Card><EmptyState icon="fa-lock" title="You can't create pages">Ask a super admin for publishing rights.</EmptyState></Card>}
                </>
            ) : <EditPage pageId={pageId} caps={caps} lookups={lookups} catalog={catalog} toast={toast} />}
            {toastNode}
        </AdminLayout>
    );
}
