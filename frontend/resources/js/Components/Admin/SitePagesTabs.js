import React, { useState } from 'react';
import axios from 'axios';
import { errorText } from '@/Components/Admin/AdminLayout';
import { Button, Card, Badge } from '@/Components/Admin/kit';
import { Field, Row2, Toggle, RichText, SaveBar, useUnsavedGuard } from '@/Components/Admin/form';
import { FaqManager } from '@/Pages/Admin/Faq';

/*
 * The page tabs of SEO & Layout Controls: Our vision (/artistikcity-vision), Instructor (/instructor), Customer support
 * (/customer-support) and FAQ (/faq). Saved per page through POST /admin/api/setup/site-pages/{page} (admin/SitePages);
 * an empty box means "use the default text", which is shown as the placeholder.
 */

const LINK_RE = /^(\/(?!\/)[^\s<>"']*|https:\/\/[^\s<>"']+)$/i;
const ICONS = ['fa-graduation-cap', 'fa-play-circle', 'fa-shopping-bag', 'fa-certificate', 'fa-shield', 'fa-picture-o', 'fa-paint-brush', 'fa-video-camera',
    'fa-credit-card', 'fa-truck', 'fa-user', 'fa-question-circle', 'fa-life-ring', 'fa-calendar', 'fa-star-o', 'fa-heart-o', 'fa-users', 'fa-envelope-o'];

const parseList = (s) => { try { const x = JSON.parse(s || '[]'); return Array.isArray(x) ? x : []; } catch (e) { return []; } };

/** One page's form state: saved values, the defaults, dirty tracking and save. */
function usePageForm(page, saved0, defaults, toast) {
    const [saved, setSaved] = useState(saved0 || {});
    const [v, setV] = useState(saved0 || {});
    const [errors, setErrors] = useState({});
    const [busy, setBusy] = useState(false);
    const dirty = JSON.stringify(v) !== JSON.stringify(saved);
    useUnsavedGuard(dirty);
    const set = (k, x) => { setV((p) => ({ ...p, [k]: x })); setErrors((e) => ({ ...e, [k]: null })); };
    const save = () => {
        setBusy(true);
        axios.post(`/admin/api/setup/site-pages/${page}`, v).then((r) => { setSaved(r.data.saved); setV(r.data.saved); setErrors({}); toast(r.data.message); })
            .catch((e) => {
                if (e.response && e.response.status === 422) {
                    const errs = e.response.data.errors || {};
                    setErrors(errs);
                    toast(Object.values(errs)[0] || e.response.data.message, 'error');
                    const el = document.getElementById(`f-${page}-${Object.keys(errs)[0]}`);
                    if (el) { el.focus(); el.scrollIntoView({ block: 'center' }); }
                } else toast(errorText(e), 'error');
            }).finally(() => setBusy(false));
    };
    const value = (k) => (v[k] == null ? '' : v[k]);
    const eff = (k) => (value(k) !== '' ? value(k) : (defaults[k] || ''));
    const text = (k, label, { rows, max = 120, hint } = {}) => (
        <Field label={label} name={`${page}-${k}`} hint={hint || (defaults[k] ? 'Empty = the default shown in grey.' : undefined)} error={errors[k]} max={max} counter={value(k).length} optional>
            {rows ? <textarea id={`f-${page}-${k}`} className="adm-input" rows={rows} maxLength={max} value={value(k)} placeholder={defaults[k] || ''} onChange={(e) => set(k, e.target.value)} aria-invalid={errors[k] ? 'true' : undefined} />
                : <input id={`f-${page}-${k}`} className="adm-input" maxLength={max} value={value(k)} placeholder={defaults[k] || ''} onChange={(e) => set(k, e.target.value)} aria-invalid={errors[k] ? 'true' : undefined} />}
        </Field>
    );
    const flag = (k, label, description) => (
        <Toggle name={`${page}-${k}`} checked={eff(k) === '1'} onChange={(x) => set(k, x ? '1' : '0')} label={label} description={description} />
    );
    const rich = (k, label, hint) => (
        <Field label={label} name={`${page}-${k}`} hint={hint} error={errors[k]} optional>
            <RichText name={`${page}-${k}`} value={value(k) || ''} onChange={(x) => set(k, x)} minHeight={220} error={errors[k]} />
            {!value(k) && defaults[k] && (
                <div className="adm-inline" style={{ marginTop: 8 }}>
                    <span className="adm-muted adm-small">Empty: the page shows the default text.</span>
                    <Button size="sm" variant="ghost" icon="fa-pencil" onClick={() => set(k, defaults[k])}>Start from the default</Button>
                </div>
            )}
        </Field>
    );
    const seo = () => (
        <Card title="Search & sharing" subtitle="Title and description for Google and when the page is shared.">
            <div className="adm-stack">{text('seo_title', 'Page title', { max: 120 })}{text('seo_description', 'Description', { rows: 2, max: 300 })}</div>
        </Card>
    );
    const bar = (href) => (
        <SaveBar dirty={dirty} busy={busy} onSave={save}>
            <Button as="a" variant="ghost" icon="fa-external-link" href={href} target="_blank" rel="noopener noreferrer">View page</Button>
            {dirty && <Button variant="ghost" onClick={() => { setV(saved); setErrors({}); }}>Discard changes</Button>}
        </SaveBar>
    );
    return { v, set, value, eff, errors, text, flag, rich, seo, bar, dirty };
}

/* ------------------------------------------------------------ cards editor */

function CardsEditor({ page, k, label, hint, f, defaults, max, withLink = true }) {
    const saved = f.value(k);
    const usingDefault = saved === '';
    const list = parseList(usingDefault ? defaults[k] : saved);
    const write = (next) => f.set(k, JSON.stringify(next));
    const update = (i, patch) => write(list.map((c, j) => (j === i ? { ...c, ...patch } : c)));
    const move = (i, to) => { if (to < 0 || to >= list.length) return; const n = list.slice(); const [x] = n.splice(i, 1); n.splice(to, 0, x); write(n); };
    return (
        <Field label={label} name={`${page}-${k}`} hint={hint} error={f.errors[k]} optional>
            <div className="ss-cards" id={`f-${page}-${k}`} tabIndex={-1} data-testid={`cards-${page}-${k}`}>
                {usingDefault && list.length > 0 && <p className="adm-muted adm-small"><Badge>Default</Badge> Edit any field to make these your own.</p>}
                {list.map((c, i) => (
                    <div key={i} className="ss-cards__item">
                        <div className="ss-cards__row">
                            <select className="adm-input ss-cards__icon" aria-label={`Icon of item ${i + 1}`} value={c.icon || ''} onChange={(e) => update(i, { icon: e.target.value })}>
                                <option value="">No icon</option>
                                {ICONS.map((x) => <option key={x} value={x}>{x.replace('fa-', '').replace(/-/g, ' ')}</option>)}
                            </select>
                            <i className={`fa ${c.icon || 'fa-square-o'} ss-cards__glyph`} aria-hidden="true"></i>
                            <input className="adm-input" aria-label={`Title of item ${i + 1}`} placeholder="Title" maxLength={80} value={c.title || ''} onChange={(e) => update(i, { title: e.target.value })} />
                            <div className="adm-rowactions">
                                <Button size="sm" variant="ghost" icon="fa-arrow-up" disabled={i === 0} onClick={() => move(i, i - 1)} aria-label="Move up" title="Move up" />
                                <Button size="sm" variant="ghost" icon="fa-arrow-down" disabled={i === list.length - 1} onClick={() => move(i, i + 1)} aria-label="Move down" title="Move down" />
                                <Button size="sm" variant="ghost" icon="fa-trash-o" onClick={() => write(list.filter((_, j) => j !== i))} aria-label={`Remove item ${i + 1}`} title="Remove" />
                            </div>
                        </div>
                        <textarea className="adm-input" rows={2} aria-label={`Text of item ${i + 1}`} placeholder="One or two sentences" maxLength={300} value={c.text || ''} onChange={(e) => update(i, { text: e.target.value })} />
                        {withLink && (
                            <Row2>
                                <input className="adm-input" aria-label={`Button label of item ${i + 1}`} placeholder="Link label (e.g. Your orders)" maxLength={40} value={c.label || ''} onChange={(e) => update(i, { label: e.target.value })} />
                                <input className={`adm-input ${c.href && !LINK_RE.test(c.href) ? 'is-invalid' : ''}`} aria-label={`Link of item ${i + 1}`} placeholder="/user/orders or https://…" maxLength={300} value={c.href || ''} onChange={(e) => update(i, { href: e.target.value.trim() })} />
                            </Row2>
                        )}
                    </div>
                ))}
                <div className="adm-inline">
                    <Button size="sm" icon="fa-plus" disabled={list.length >= max} onClick={() => write([...list, { icon: '', title: '', text: '', label: '', href: '' }])}>Add item</Button>
                    {!usingDefault && <Button size="sm" variant="ghost" icon="fa-undo" onClick={() => f.set(k, '')}>Back to the default</Button>}
                    <span className="adm-muted adm-small">{list.length} / {max}</span>
                </div>
            </div>
        </Field>
    );
}

/* ------------------------------------------------------------------ tabs */

export function VisionTab({ saved, defaults, toast }) {
    const f = usePageForm('vision', saved, defaults, toast);
    return (
        <div className="adm-stack" data-testid="tab-vision">
            <Card title="Our vision" subtitle={<>The page at <a href="/artistikcity-vision" target="_blank" rel="noopener noreferrer">/artistikcity-vision</a> (footer › Our vision).</>}>
                <div className="adm-stack">
                    {f.text('heading', 'Heading')}
                    {f.text('intro', 'Introduction', { rows: 3, max: 400 })}
                    <Row2>
                        {f.text('cta_label', 'Button label', { max: 40 })}
                        {f.text('cta_link', 'Button link', { max: 300, hint: 'A page on this site such as /courses?type=all, or https://…' })}
                    </Row2>
                </div>
            </Card>
            <Card title="Highlights" subtitle="Up to six short cards under the heading.">
                <CardsEditor page="vision" k="highlights" label="Cards" f={f} defaults={defaults} max={6} />
            </Card>
            <Card title="Story">
                {f.rich('body', 'Text', 'Headings, paragraphs, lists and links. Write only what is true today.')}
            </Card>
            {f.seo()}
            {f.bar('/artistikcity-vision')}
        </div>
    );
}

export function SupportTab({ saved, defaults, toast }) {
    const f = usePageForm('support', saved, defaults, toast);
    return (
        <div className="adm-stack" data-testid="tab-support">
            <Card title="Customer support" subtitle={<>The help centre at <a href="/customer-support" target="_blank" rel="noopener noreferrer">/customer-support</a>. Contact details come from Layout &amp; SEO › Contact.</>}>
                <div className="adm-stack">
                    {f.text('heading', 'Heading')}
                    {f.text('intro', 'Introduction', { rows: 2, max: 400 })}
                    {f.text('hours', 'Support hours', { max: 200, hint: 'Optional, e.g. "Monday to Saturday, 10am to 6pm IST". Only write hours you keep.' })}
                </div>
            </Card>
            <Card title="Help topics" subtitle="Cards that link to the right place on the site. Every link is checked when you save.">
                <CardsEditor page="support" k="topics" label="Topics" f={f} defaults={defaults} max={9} />
            </Card>
            <Card title="On the page">
                <div className="adm-stack">
                    {f.flag('show_faq', 'Show common questions', 'The first questions of the FAQ tab, with a link to all of them.')}
                    {f.eff('show_faq') === '1' && f.text('faq_count', 'How many questions', { max: 2, hint: '1 to 20.' })}
                    {f.flag('show_contact', 'Show the contact band', 'E-mail, phone and a Contact us button.')}
                    {f.rich('body', 'Extra text (optional)', 'Shown under the questions, e.g. refund or delivery notes that are already your policy.')}
                </div>
            </Card>
            {f.seo()}
            {f.bar('/customer-support')}
        </div>
    );
}

export function FaqTab({ saved, defaults, faqs, toast }) {
    const f = usePageForm('faq', saved, defaults, toast);
    return (
        <div className="adm-stack" data-testid="tab-faq">
            <Card title="Questions" subtitle={<>Shown on <a href="/faq" target="_blank" rel="noopener noreferrer">/faq</a> grouped by category, in this order; hidden ones stay here. Changes save at once.</>}>
                <FaqManager initial={faqs} toast={toast} />
            </Card>
            <Card title="FAQ page">
                <div className="adm-stack">
                    {f.text('heading', 'Heading')}
                    {f.text('intro', 'Introduction', { rows: 2, max: 400 })}
                    {f.flag('show_search', 'Search box', 'Visitors can filter the questions as they type.')}
                    {f.flag('show_contact', 'Contact band at the end', '"Didn\'t find your answer?" with your contact details.')}
                </div>
            </Card>
            {f.seo()}
            {f.bar('/faq')}
        </div>
    );
}

const PERSON_LINKS = [['website', 'Website', 'fa-globe'], ['facebook', 'Facebook', 'fa-facebook'], ['instagram', 'Instagram', 'fa-instagram'], ['youtube', 'YouTube', 'fa-youtube-play'], ['linkedin', 'LinkedIn', 'fa-linkedin']];

export function InstructorTab({ saved, defaults, staff = [], toast }) {
    const f = usePageForm('instructor', saved, defaults, toast);
    const usingDefault = f.value('people') === '';
    const people = parseList(usingDefault ? defaults.people : f.value('people')).map((p) => {
        if (p.id) return p;
        const s = staff.find((x) => x.slug && x.slug === p.slug);
        return { ...p, id: s ? s.id : '' };
    });
    const write = (next) => f.set('people', JSON.stringify(next.map(({ slug, ...rest }) => rest)));
    const update = (i, patch) => write(people.map((p, j) => (j === i ? { ...p, ...patch } : p)));
    const move = (i, to) => { if (to < 0 || to >= people.length) return; const n = people.slice(); const [x] = n.splice(i, 1); n.splice(to, 0, x); write(n); };
    return (
        <div className="adm-stack" data-testid="tab-instructor">
            <Card title="Instructor page" subtitle={<>The page at <a href="/instructor" target="_blank" rel="noopener noreferrer">/instructor</a>. Name, photo, title and biography come from each person's profile in Admin › People.</>}>
                <div className="adm-stack">
                    {f.text('heading', 'Heading')}
                    {f.text('intro', 'Introduction', { rows: 2, max: 400 })}
                    {f.flag('show_courses', 'Show their live courses', 'Up to six published courses of each person.')}
                </div>
            </Card>
            <Card title="People on the page" subtitle="In this order. Social icons appear only for links you fill in (https://…).">
                <div className="adm-stack" id="f-instructor-people" tabIndex={-1} data-testid="instructor-people">
                    {f.errors.people && <p className="adm-field__error" role="alert">{f.errors.people}</p>}
                    {usingDefault && <p className="adm-muted adm-small"><Badge>Default</Badge> Edit anything to make this list your own.</p>}
                    {people.map((p, i) => (
                        <div key={i} className="ss-cards__item">
                            <div className="ss-cards__row">
                                <select className="adm-input" aria-label={`Person ${i + 1}`} value={p.id || ''} onChange={(e) => update(i, { id: e.target.value ? Number(e.target.value) : '' })}>
                                    <option value="">Choose a staff member…</option>
                                    {staff.map((s) => <option key={s.id} value={s.id}>{s.name}{s.profile_title ? ` · ${s.profile_title}` : ''}</option>)}
                                </select>
                                <div className="adm-rowactions">
                                    <Button size="sm" variant="ghost" icon="fa-arrow-up" disabled={i === 0} onClick={() => move(i, i - 1)} aria-label="Move up" title="Move up" />
                                    <Button size="sm" variant="ghost" icon="fa-arrow-down" disabled={i === people.length - 1} onClick={() => move(i, i + 1)} aria-label="Move down" title="Move down" />
                                    <Button size="sm" variant="ghost" icon="fa-trash-o" onClick={() => write(people.filter((_, j) => j !== i))} aria-label={`Remove person ${i + 1}`} title="Remove" />
                                </div>
                            </div>
                            <div className="ss-people__links">
                                {PERSON_LINKS.map(([k, label, icon]) => (
                                    <label key={k} className="ss-people__link"><i className={`fa ${icon}`} aria-hidden="true"></i>
                                        <input className={`adm-input ${p[k] && !/^https:\/\/\S+$/i.test(p[k]) ? 'is-invalid' : ''}`} aria-label={`${label} link`} placeholder={`${label}: https://…`} maxLength={300}
                                               value={p[k] || ''} onChange={(e) => update(i, { [k]: e.target.value.trim() })} />
                                    </label>
                                ))}
                            </div>
                        </div>
                    ))}
                    <div className="adm-inline">
                        <Button size="sm" icon="fa-plus" disabled={people.length >= 8} onClick={() => write([...people, { id: '', website: '', facebook: '', instagram: '', youtube: '', linkedin: '' }])}>Add person</Button>
                        {!usingDefault && <Button size="sm" variant="ghost" icon="fa-undo" onClick={() => f.set('people', '')}>Back to the default</Button>}
                    </div>
                </div>
            </Card>
            {f.seo()}
            {f.bar('/instructor')}
        </div>
    );
}
