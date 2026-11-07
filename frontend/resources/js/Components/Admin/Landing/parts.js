import React, { useEffect, useRef, useState } from 'react';
import axios from 'axios';
import { Button, Badge, Drawer, cx } from '@/Components/Admin/kit';
import { Toggle, Field } from '@/Components/Admin/form';
import { errorText } from '@/Components/Admin/AdminLayout';

/*
 * Shared pieces of Admin > Landing Page Management: payment settings (per page and global defaults), the media picker,
 * the section content editor, template thumbnails and a small accessible chart.
 */

export const STATUS = {
    DRAFT: { label: 'Draft', tone: 'grey' },
    SCHEDULED: { label: 'Scheduled', tone: 'blue' },
    PUBLISHED: { label: 'Published', tone: 'green' },
    ARCHIVED: { label: 'Archived', tone: 'amber' },
};

export const inr = (v) => (v === null || v === undefined || v === '' ? '—' : `₹${Number(v).toLocaleString('en-IN', { maximumFractionDigits: 2 })}`);
export const pct = (v) => `${Number(v || 0).toLocaleString('en-IN', { maximumFractionDigits: 1 })}%`;
export const num = (v) => Number(v || 0).toLocaleString('en-IN');
export const has = (caps, c) => (caps || []).includes(c);

/* ------------------------------------------------------------------------------------------- payments */

const PROVIDER_NAME = { razorpay: 'Razorpay', paypal: 'PayPal', cashfree: 'Cashfree', payu: 'PayU' };

const GROUPS = [
    { key: 'india', title: 'Indian payment methods' },
    { key: 'networks', title: 'Card networks', hint: 'Leave all off to accept every network. Switch some on to accept only those.' },
    { key: 'international', title: 'International' },
];

/**
 * The payment settings editor. value = LandingPayments.view(): {mode, defaultProvider, providers[], methods[]}.
 * global = editing the global defaults (no mode switch). Saves through onSave(body) -> Promise of the new view.
 */
export function PaymentSettings({ value, onSave, global = false, canEdit = true, toast }) {
    const [v, setV] = useState(value);
    const [errors, setErrors] = useState({});
    const [busy, setBusy] = useState(false);
    const [testing, setTesting] = useState(null);
    const dirty = JSON.stringify(v) !== JSON.stringify(value);
    useEffect(() => { setV(value); }, [value]);

    const providerOn = (key) => v.providers.some((p) => p.key === key && p.enabled);
    const supportedNow = (m) => m.supportedBy.some(providerOn);
    const setProvider = (key, patch) => setV({ ...v, providers: v.providers.map((p) => (p.key === key ? { ...p, ...patch } : p)) });
    const setMethod = (key, patch) => {
        let methods = v.methods.map((m) => (m.key === key ? { ...m, ...patch } : m));
        // card networks only narrow cards: with no card method left on, they go off too (the server refuses the combination)
        const cardOn = methods.some((m) => (m.key === 'credit_card' || m.key === 'debit_card') && m.enabled);
        if (!cardOn) methods = methods.map((m) => (m.group === 'networks' ? { ...m, enabled: false } : m));
        setV({ ...v, methods });
    };
    const move = (key, d) => {
        const list = [...v.methods];
        const i = list.findIndex((m) => m.key === key);
        const j = i + d;
        if (j < 0 || j >= list.length) return;
        [list[i], list[j]] = [list[j], list[i]];
        setV({ ...v, methods: list });
    };
    const custom = global || v.mode === 'CUSTOM';

    const save = () => {
        setBusy(true);
        setErrors({});
        const body = { mode: global ? 'CUSTOM' : v.mode, defaultProvider: v.defaultProvider,
            providers: v.providers.map((p) => ({ key: p.key, enabled: p.enabled, priority: p.priority })),
            methods: v.methods.map((m) => ({ key: m.key, enabled: m.enabled })) };
        onSave(body).then((next) => { if (next) setV(next); })
            .catch((e) => { setErrors((e.response && e.response.data && e.response.data.errors) || {}); toast(errorText(e), 'error'); })
            .finally(() => setBusy(false));
    };
    const test = (key) => {
        setTesting(key);
        axios.post('/admin/api/landing/payments/test', { provider: key })
            .then((r) => toast(r.data.message, r.data.ok ? 'ok' : 'error'))
            .catch((e) => toast(errorText(e), 'error'))
            .finally(() => setTesting(null));
    };

    return (
        <div className="lpa-pay" data-testid="payment-settings">
            {!global && (
                <div className="lpa-modes" role="radiogroup" aria-label="Payment settings for this page">
                    {[['GLOBAL', 'Use the global defaults', 'Follows Landing pages › Payments. Changing the defaults changes this page.'],
                        ['CUSTOM', 'Custom for this page', 'Only this page. Other pages never change.']].map(([k, t, d]) => (
                        <label key={k} className={cx('lpa-mode', v.mode === k && 'is-on')}>
                            <input type="radio" name="paymode" value={k} checked={v.mode === k} disabled={!canEdit} onChange={() => setV({ ...v, mode: k })} data-testid={`paymode-${k}`} />
                            <b>{t}</b><small>{d}</small>
                        </label>
                    ))}
                </div>
            )}

            <fieldset className="lpa-block" disabled={!custom || !canEdit}>
                <legend>Payment providers</legend>
                <p className="adm-muted adm-small">Keys stay in System parameters › Configuration Panel (super admin). Here you choose which connected providers this {global ? 'default' : 'page'} uses.</p>
                {errors.providers && <p className="adm-field__error" role="alert">{errors.providers}</p>}
                <div className="lpa-providers">
                    {v.providers.map((p) => (
                        <div key={p.key} className={cx('lpa-provider', p.enabled && 'is-on', !p.integrated && 'is-off')} data-provider={p.key}>
                            <div className="lpa-provider__main">
                                <Toggle checked={p.enabled} disabled={!p.integrated || !custom || !canEdit} onChange={(on) => setProvider(p.key, { enabled: on })}
                                        label={`${p.label} · ${p.currency}`} description={p.note} name={`provider-${p.key}`} />
                                {errors[`providers.${p.key}`] && <p className="adm-field__error">{errors[`providers.${p.key}`]}</p>}
                            </div>
                            <div className="lpa-provider__side">
                                <Badge tone={p.connected ? 'green' : p.integrated ? 'amber' : 'grey'} dot>{p.status}</Badge>
                                <label className="lpa-priority">Priority
                                    <select className="adm-input adm-input--sm" value={p.priority} disabled={!custom || !canEdit} onChange={(e) => setProvider(p.key, { priority: Number(e.target.value) })}
                                            aria-label={`${p.label} priority`}>
                                        {[1, 2, 3, 4].map((n) => <option key={n} value={n}>{n}</option>)}
                                    </select>
                                </label>
                                {p.integrated && <Button size="sm" onClick={() => test(p.key)} busy={testing === p.key} icon="fa-plug">Test connection</Button>}
                            </div>
                        </div>
                    ))}
                </div>
                <Field label="Default provider" name="defaultProvider" error={errors.defaultProvider} hint="Used first when a method is offered by more than one provider.">
                    <select id="f-defaultProvider" className="adm-input" value={v.defaultProvider || ''} onChange={(e) => setV({ ...v, defaultProvider: e.target.value })}>
                        {v.providers.filter((p) => p.integrated).map((p) => <option key={p.key} value={p.key}>{p.label}</option>)}
                    </select>
                </Field>
            </fieldset>

            <fieldset className="lpa-block" disabled={!custom || !canEdit}>
                <legend>Payment methods</legend>
                <p className="adm-muted adm-small">Buyers see a method only when it is <b>switched on</b> here, a <b>provider switched on above supports it</b>, and that provider is <b>connected</b> right now. Use the arrows to set the order buyers see.</p>
                {errors.methods && <p className="adm-field__error" role="alert">{errors.methods}</p>}
                {GROUPS.map((g) => (
                    <div key={g.key} className="lpa-mgroup">
                        <h3>{g.title}</h3>
                        {g.hint && <p className="adm-muted adm-small">{g.hint}</p>}
                        <ul className="lpa-methods">
                            {v.methods.filter((m) => m.group === g.key).map((m) => {
                                const ok = supportedNow(m);
                                return (
                                    <li key={m.key} className={cx('lpa-method', m.enabled && 'is-on', !ok && 'is-unsupported')} data-method={m.key}>
                                        <Toggle checked={m.enabled && ok} disabled={!ok || !custom || !canEdit} onChange={(on) => setMethod(m.key, { enabled: on })} label={m.label}
                                                description={!ok ? `Not supported by the providers switched on (${m.supportedBy.join(', ')} can).` : m.hint} name={`method-${m.key}`} />
                                        <span className="lpa-method__by">{m.supportedBy.map((p) => <span key={p} className={cx('lpa-chip', providerOn(p) && 'is-on')}>{PROVIDER_NAME[p] || p}</span>)}</span>
                                        <span className="lpa-method__state">{m.live ? <Badge tone="green">Shown to buyers</Badge> : m.enabled ? <Badge tone="amber">On, not live</Badge> : <Badge>Off</Badge>}</span>
                                        <span className="lpa-method__order">
                                            <button type="button" className="adm-iconbtn" onClick={() => move(m.key, -1)} aria-label={`Move ${m.label} up`}><i className="fa fa-arrow-up" aria-hidden="true"></i></button>
                                            <button type="button" className="adm-iconbtn" onClick={() => move(m.key, 1)} aria-label={`Move ${m.label} down`}><i className="fa fa-arrow-down" aria-hidden="true"></i></button>
                                        </span>
                                        {errors[`methods.${m.key}`] && <p className="adm-field__error lpa-method__err">{errors[`methods.${m.key}`]}</p>}
                                    </li>
                                );
                            })}
                        </ul>
                    </div>
                ))}
            </fieldset>
            {canEdit && (
                <div className="lpa-savebar">
                    <span className="adm-muted adm-small">{dirty ? 'Unsaved changes' : 'Saved'}</span>
                    <Button variant="brand" icon="fa-check" busy={busy} onClick={save} disabled={!dirty} data-testid="payments-save">{global ? 'Save global defaults' : 'Save payment settings'}</Button>
                </div>
            )}
        </div>
    );
}

/* ---------------------------------------------------------------------------------------------- media */

/** Pick (or upload) an image from the landing media library. */
export function MediaPicker({ open, onClose, onPick, toast }) {
    const [data, setData] = useState(null);
    const [busy, setBusy] = useState(false);
    const file = useRef(null);
    useEffect(() => {
        if (open) axios.get('/admin/api/landing/media').then((r) => setData(r.data)).catch((e) => toast(errorText(e), 'error'));
    }, [open]); // eslint-disable-line react-hooks/exhaustive-deps
    const upload = (f) => {
        if (!f) return;
        const fd = new FormData();
        fd.append('file', f);
        setBusy(true);
        axios.post('/admin/api/landing/media', fd).then((r) => { onPick(r.data.url); onClose(); })
            .catch((e) => toast(errorText(e), 'error')).finally(() => setBusy(false));
    };
    return (
        <Drawer open={open} onClose={onClose} title="Choose an image" subtitle="Upload once, use on any page. At least 600 px wide; JPG or PNG." size="lg">
            <div className="lpa-upload">
                <input ref={file} type="file" accept="image/jpeg,image/png,image/webp" hidden onChange={(e) => upload(e.target.files[0])} />
                <Button variant="dark" icon="fa-upload" busy={busy} onClick={() => file.current.click()}>Upload an image</Button>
            </div>
            {!data ? <p className="adm-muted">Loading…</p> : (
                <>
                    {data.media.length > 0 && <h3 className="lpa-h3">Library</h3>}
                    <ul className="lpa-media">
                        {data.media.map((m) => (
                            <li key={m.id}><button type="button" onClick={() => { onPick(m.url); onClose(); }} aria-label={`Use ${m.alt || 'this image'}`}>
                                <img src={m.thumb} alt="" loading="lazy" /><span>{m.alt || `${m.width}×${m.height}`}</span>
                            </button></li>
                        ))}
                    </ul>
                    <h3 className="lpa-h3">Already on the site</h3>
                    <ul className="lpa-media">
                        {data.site.map((m) => (
                            <li key={m.url}><button type="button" onClick={() => { onPick(m.url); onClose(); }} aria-label={`Use ${m.label}`}>
                                <img src={m.thumb} alt="" loading="lazy" /><span>{m.label}</span>
                            </button></li>
                        ))}
                    </ul>
                </>
            )}
        </Drawer>
    );
}

export function ImageField({ label, value, onChange, hint, error, toast, name, clearLabel = 'Use the course image' }) {
    const [open, setOpen] = useState(false);
    return (
        <Field label={label} name={name} hint={hint} error={error}>
            <div className="lpa-imgfield">
                {value ? <img src={value} alt="" /> : <span className="lpa-imgfield__empty"><i className="fa fa-picture-o" aria-hidden="true"></i></span>}
                <div className="lpa-imgfield__actions">
                    <Button size="sm" icon="fa-picture-o" onClick={() => setOpen(true)}>{value ? 'Change' : 'Choose'}</Button>
                    {value && <Button size="sm" variant="ghost" onClick={() => onChange('')}>{clearLabel}</Button>}
                </div>
            </div>
            <MediaPicker open={open} onClose={() => setOpen(false)} onPick={onChange} toast={toast} />
        </Field>
    );
}

/* -------------------------------------------------------------------------------------- section content */

const TEXT_FIELDS = {
    kicker: { label: 'Small label above the heading', max: 60, hint: 'Leave empty for the usual label.' },
    eyebrow: { label: 'Small label above the title', max: 60 },
    heading: { label: 'Heading', max: 120 },
    subheading: { label: 'Tagline', max: 200 },
    body: { label: 'Text', max: 2000, rows: 4 },
    imageAlt: { label: 'Image description (alt text)', max: 160 },
    language: { label: 'Language of the classes', max: 60 },
    video: { label: 'Video link', max: 1000, hint: 'YouTube, Vimeo or an uploaded .mp4. Plays only when clicked (no cookies before that).' },
};
const ITEM_HELP = {
    hero: { label: 'Trust points', hint: 'Short and true, e.g. "Live classes with the artist".', title: 'Point', noText: true },
    intro: { label: 'Highlights', title: 'Title', text: 'Text' },
    outcomes: { label: 'Outcomes', title: 'Outcome', text: 'What it means', hint: 'Leave empty to use the course\'s "What you\'ll learn".' },
    artist: { label: 'Awards, exhibitions and experience', title: 'Title', text: 'Detail', hint: 'Only facts you can stand behind.' },
    gallery: { label: 'Artworks', title: 'Caption', text: 'Story', image: true, hint: 'Leave empty to show the course photos and approved student work.' },
    benefits: { label: 'Benefits', title: 'Benefit', text: 'Detail', hint: 'Leave empty to build them from the course.' },
    faq: { label: 'Questions', title: 'Question', text: 'Answer', hint: 'Leave empty to show the site FAQ.' },
    cards: { label: 'Cards', title: 'Title', text: 'Text', image: true, link: true, hint: 'Only what the platform really does. A link is optional (a site address like /marketplace, or #section-id).' },
    steps: { label: 'Steps', title: 'Step', text: 'What happens', link: true, hint: 'In order. A link is optional.' },
    final_cta: { label: 'Buttons', title: 'Button label', noText: true, link: true, noLinkLabel: true, hint: 'Empty: one main button (Enroll with the price on course pages). Each button needs a label and an address.' },
};
const SOURCES = {
    gallery: [{ value: 'course', label: 'The artworks below, else course photos and approved student work' }, { value: 'marketplace', label: 'The artworks below, else marketplace artworks' }],
    cards: [{ value: 'custom', label: 'The cards below' }, { value: 'course_for', label: 'The course\'s "Who it\'s for" list' }, { value: 'course_learn', label: 'The course\'s "What you\'ll learn" list' },
        { value: 'course_deliverables', label: 'The course\'s "What you finish with" list' }],
};

export function SectionContent({ section, onChange, lookups, toast, errors = {}, prefix }) {
    const c = section.content || {};
    const set = (k, val) => onChange({ ...section, content: { ...c, [k]: val } });
    const err = (k) => errors[`${prefix}.content.${k}`];
    const items = c.items || [];
    const help = ITEM_HELP[section.type];
    return (
        <div className="lpa-content">
            {Object.keys(TEXT_FIELDS).filter((k) => k in c && !(lookups.site && k === 'language')).map((k) => (
                <Field key={k} label={TEXT_FIELDS[k].label} name={`${section.id}-${k}`} hint={TEXT_FIELDS[k].hint || (k === 'heading' && section.type === 'hero' ? 'Leave empty to use the course title.' : undefined)}
                       error={err(k)} max={TEXT_FIELDS[k].max} counter={String(c[k] || '').length}>
                    {TEXT_FIELDS[k].rows
                        ? <textarea id={`f-${section.id}-${k}`} className="adm-input" rows={TEXT_FIELDS[k].rows} value={c[k] || ''} onChange={(e) => set(k, e.target.value)} />
                        : <input id={`f-${section.id}-${k}`} className="adm-input" value={c[k] || ''} onChange={(e) => set(k, e.target.value)} />}
                </Field>
            ))}
            {'image' in c && <ImageField label="Image" clearLabel={lookups.site ? 'Remove' : undefined} value={c.image} onChange={(u) => set('image', u)} toast={toast} error={err('image')} name={`${section.id}-image`}
                                         hint={lookups.site ? 'Leave empty for no image (or, in the hero collage, the first collage artwork).' : 'Leave empty to use the course image.'} />}
            {'images' in c && section.layout === 'collage' && (
                <Field label="Collage artworks" name={`${section.id}-images`} hint="Up to four artworks hung like a small gallery wall. The first is the largest.">
                    <div className="adm-grid2">
                        {[0, 1, 2, 3].map((i) => (
                            <ImageField key={i} clearLabel="Remove" label={`Artwork ${i + 1}`} value={(c.images || [])[i] || ''} toast={toast} name={`${section.id}-img-${i}`} error={err(`images.${i}`)}
                                        onChange={(u) => { const l = [...(c.images || [])]; l[i] = u; set('images', l.filter(Boolean)); }} />
                        ))}
                    </div>
                </Field>
            )}
            {'beforeImage' in c && (
                <div className="adm-grid2">
                    <ImageField label="Before (optional)" value={c.beforeImage} onChange={(u) => set('beforeImage', u)} toast={toast} name={`${section.id}-before`} hint="With an After image, shows a slider." />
                    <ImageField label="After (optional)" value={c.afterImage} onChange={(u) => set('afterImage', u)} toast={toast} name={`${section.id}-after`} />
                </div>
            )}
            {'source' in c && SOURCES[section.type] && (
                <Field label="Content" name={`${section.id}-source`}>
                    <select className="adm-input" value={c.source} onChange={(e) => set('source', e.target.value)} id={`f-${section.id}-source`}>
                        {SOURCES[section.type].map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
                    </select>
                </Field>
            )}
            {'limit' in c && (
                <Field label="How many" name={`${section.id}-limit`} hint="1 to 12.">
                    <input id={`f-${section.id}-limit`} type="number" min={1} max={12} className="adm-input" value={c.limit || 6} onChange={(e) => set('limit', Number(e.target.value))} />
                </Field>
            )}
            {'ids' in c && section.type === 'courses' && (
                <Field label="Courses" name={`${section.id}-ids`} hint="None ticked = the published courses and workshops, in catalogue order. Each links to its own (landing) page.">
                    <div className="lpa-checks">
                        {(lookups.courses || []).filter((x) => String(x.status) === '1').map((x) => (
                            <label key={x.id}><input type="checkbox" checked={(c.ids || []).includes(x.id)}
                                                     onChange={(e) => set('ids', e.target.checked ? [...(c.ids || []), x.id] : (c.ids || []).filter((y) => y !== x.id))} /> {x.title}</label>
                        ))}
                    </div>
                </Field>
            )}
            {'ids' in c && section.type === 'artists' && (
                <Field label="Artists" name={`${section.id}-ids`} hint="None ticked = every instructor, most courses first. Each card links to the artist's profile.">
                    <div className="lpa-checks">
                        {(lookups.artists || []).map((a) => (
                            <label key={a.id}><input type="checkbox" checked={(c.ids || []).includes(a.id)}
                                                     onChange={(e) => set('ids', e.target.checked ? [...(c.ids || []), a.id] : (c.ids || []).filter((x) => x !== a.id))} /> {a.name}</label>
                        ))}
                    </div>
                </Field>
            )}
            {'source' in c && !SOURCES[section.type] && (
                <Field label="Which reviews" name={`${section.id}-source`} hint="Only approved course reviews and approved testimonials are ever shown.">
                    <select className="adm-input" value={c.source} onChange={(e) => set('source', e.target.value)} id={`f-${section.id}-source`}>
                        <option value="reviews">Approved reviews of this course</option>
                        <option value="testimonials">Selected site testimonials</option>
                        <option value="both">Both</option>
                    </select>
                </Field>
            )}
            {'ids' in c && section.type === 'testimonials' && c.source !== 'reviews' && (
                <Field label="Testimonials" name={`${section.id}-ids`} hint="None ticked = all approved testimonials.">
                    <div className="lpa-checks">
                        {(lookups.testimonials || []).map((t) => (
                            <label key={t.id}><input type="checkbox" checked={(c.ids || []).includes(t.id)}
                                                     onChange={(e) => set('ids', e.target.checked ? [...(c.ids || []), t.id] : (c.ids || []).filter((x) => x !== t.id))} /> {t.name}{t.title ? ` · ${t.title}` : ''}</label>
                        ))}
                    </div>
                </Field>
            )}
            {'ids' in c && section.type === 'artist' && (
                <Field label="Artists" name={`${section.id}-ids`} hint="None ticked = the course's instructor. Tick several for a course taught by more than one artist.">
                    <div className="lpa-checks">
                        {(lookups.artists || []).map((a) => (
                            <label key={a.id}><input type="checkbox" checked={(c.ids || []).includes(a.id)}
                                                     onChange={(e) => set('ids', e.target.checked ? [...(c.ids || []), a.id] : (c.ids || []).filter((x) => x !== a.id))} /> {a.name}</label>
                        ))}
                    </div>
                </Field>
            )}
            {'socials' in c && (
                <Field label="Social links" name={`${section.id}-socials`} hint="Full https:// links (Instagram, YouTube, Behance…). Up to 6.">
                    <SimpleList value={c.socials || []} onChange={(l) => set('socials', l)} placeholder="https://instagram.com/…" max={6} />
                    {Object.keys(errors).filter((k) => k.startsWith(`${prefix}.content.socials`)).map((k) => <p key={k} className="adm-field__error">{errors[k]}</p>)}
                </Field>
            )}
            {'html' in c && (
                <Field label="Content" name={`${section.id}-html`} hint="Headings, paragraphs, lists, links and images. Scripts and styles are removed when saved.">
                    <textarea id={`f-${section.id}-html`} className="adm-input adm-mono" rows={10} value={c.html || ''} onChange={(e) => set('html', e.target.value)} />
                </Field>
            )}
            {'items' in c && help && (
                <Field label={help.label} name={`${section.id}-items`} hint={help.hint} error={err('items')}>
                    <ul className="lpa-items">
                        {items.map((it, i) => (
                            <li key={i} className="lpa-item">
                                <span className="lpa-item__n">{i + 1}</span>
                                <div className="lpa-item__fields">
                                    <input className="adm-input" placeholder={help.title} value={it.title || ''} aria-label={`${help.title} ${i + 1}`}
                                           onChange={(e) => set('items', items.map((x, j) => (j === i ? { ...x, title: e.target.value } : x)))} />
                                    {!help.noText && <textarea className="adm-input" rows={2} placeholder={help.text} value={it.text || ''} aria-label={`${help.text} ${i + 1}`}
                                                               onChange={(e) => set('items', items.map((x, j) => (j === i ? { ...x, text: e.target.value } : x)))} />}
                                    {help.image && <ImageField label="Image" clearLabel="Remove" value={it.image || ''} toast={toast} name={`${section.id}-item-${i}`} error={err(`items.${i}.image`)}
                                                               onChange={(u) => set('items', items.map((x, j) => (j === i ? { ...x, image: u } : x)))} />}
                                    {help.image && it.image && <input className="adm-input" placeholder="Describe the artwork (alt text)" value={it.alt || ''} aria-label={`Alt text ${i + 1}`}
                                                                      onChange={(e) => set('items', items.map((x, j) => (j === i ? { ...x, alt: e.target.value } : x)))} />}
                                    {help.link && <input className="adm-input" placeholder="Link (optional): /marketplace or #section-id" value={it.link || ''} aria-label={`Link ${i + 1}`}
                                                         onChange={(e) => set('items', items.map((x, j) => (j === i ? { ...x, link: e.target.value } : x)))} />}
                                    {help.link && !help.noLinkLabel && it.link && <input className="adm-input" placeholder="Link text, e.g. Browse courses" value={it.linkLabel || ''} aria-label={`Link text ${i + 1}`}
                                                                                       onChange={(e) => set('items', items.map((x, j) => (j === i ? { ...x, linkLabel: e.target.value } : x)))} />}
                                    {err(`items.${i}.link`) && <p className="adm-field__error">{err(`items.${i}.link`)}</p>}
                                </div>
                                <div className="lpa-item__actions">
                                    <button type="button" className="adm-iconbtn" disabled={i === 0} aria-label="Move up" onClick={() => { const n = [...items]; [n[i - 1], n[i]] = [n[i], n[i - 1]]; set('items', n); }}><i className="fa fa-arrow-up" aria-hidden="true"></i></button>
                                    <button type="button" className="adm-iconbtn" disabled={i === items.length - 1} aria-label="Move down" onClick={() => { const n = [...items]; [n[i + 1], n[i]] = [n[i], n[i + 1]]; set('items', n); }}><i className="fa fa-arrow-down" aria-hidden="true"></i></button>
                                    <button type="button" className="adm-iconbtn" aria-label="Remove" onClick={() => set('items', items.filter((_, j) => j !== i))}><i className="fa fa-trash-o" aria-hidden="true"></i></button>
                                </div>
                            </li>
                        ))}
                    </ul>
                    {items.length < 24 && <Button size="sm" icon="fa-plus" onClick={() => set('items', [...items, { title: '', text: '' }])}>Add</Button>}
                </Field>
            )}
            {['curriculum', 'related'].includes(section.type) && <p className="adm-muted adm-small">{section.type === 'curriculum' ? 'Modules and lessons come from the course (Admin › Courses), so they are always up to date.' : 'Other published courses, in the same medium first.'}</p>}
            {section.type === 'courses' && <p className="adm-muted adm-small">Titles, pictures, levels and artists come from Admin › Courses, so the list is always current.</p>}
            {section.type === 'artists' && <p className="adm-muted adm-small">Names, photos and bios come from Admin › Instructors.</p>}
            {section.type === 'commission_form' && <p className="adm-muted adm-small">The four styles of the commission wizard, its progress bar and the Next button. The choice is handed to step 2 exactly as the original step 1 does; steps 2-4, preview, upload and checkout are unchanged.</p>}
            {section.type === 'commission_pricing' && <p className="adm-muted adm-small">Starting prices are calculated from the commission price list (CommissionPricing) every time the page is shown; they can't be typed in.</p>}
            {section.type === 'pricing' && <p className="adm-muted adm-small">Price, offers, inclusions, access and refund policy are set in the Pricing step. Payment options come from the Payments step.</p>}
        </div>
    );
}

function SimpleList({ value, onChange, placeholder, max }) {
    const [t, setT] = useState('');
    return (
        <div className="lpa-simplelist">
            {value.map((x, i) => (
                <div key={i} className="lpa-simplelist__row"><input className="adm-input" value={x} onChange={(e) => onChange(value.map((y, j) => (j === i ? e.target.value : y)))} aria-label={`Link ${i + 1}`} />
                    <button type="button" className="adm-iconbtn" aria-label="Remove" onClick={() => onChange(value.filter((_, j) => j !== i))}><i className="fa fa-trash-o" aria-hidden="true"></i></button></div>
            ))}
            {value.length < max && (
                <div className="lpa-simplelist__row"><input className="adm-input" value={t} placeholder={placeholder} onChange={(e) => setT(e.target.value)} aria-label="New link"
                                                            onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); if (t.trim()) { onChange([...value, t.trim()]); setT(''); } } }} />
                    <Button size="sm" onClick={() => { if (t.trim()) { onChange([...value, t.trim()]); setT(''); } }}>Add</Button></div>
            )}
        </div>
    );
}

/* -------------------------------------------------------------------------------------------- templates */

/** A small drawing of a template (its hero layout and section rhythm) in its own colours. */
export function TemplateThumb({ t }) {
    const d = t.design;
    const dark = t.hero === 'immersive';
    return (
        <div className={`lpa-thumb lpa-thumb--${t.hero}`} style={{ '--a': d.accent, '--i': d.ink, '--s': d.surface }} aria-hidden="true">
            <div className="lpa-thumb__hero" style={{ background: dark ? d.ink : d.surface }}>
                <div className="lpa-thumb__text">
                    <span style={{ background: dark ? '#fff' : d.ink, width: '70%' }}></span>
                    <span style={{ background: dark ? '#fff' : d.ink, width: '50%' }}></span>
                    <span className="is-thin" style={{ background: dark ? 'rgba(255,255,255,.5)' : '#9aa3ad', width: '80%' }}></span>
                    <span className="is-btn" style={{ background: d.accent }}></span>
                </div>
                <div className="lpa-thumb__art">{t.hero === 'video' && <i className="fa fa-play" aria-hidden="true"></i>}</div>
            </div>
            {t.sections.slice(1, 5).map((s, i) => (
                <div key={s} className="lpa-thumb__row" style={{ background: i % 2 ? d.surface : '#fff' }}>
                    <span style={{ background: d.ink }}></span>
                    <i>{s.replace('_', ' ')}</i>
                </div>
            ))}
        </div>
    );
}

/* ---------------------------------------------------------------------------------------------- chart */

/** Daily series as an SVG area/line chart with a hidden data table for screen readers. */
export function SeriesChart({ series, keyName, label, format = num }) {
    const w = 640;
    const h = 160;
    const values = series.map((s) => Number(s[keyName] || 0));
    const max = Math.max(1, ...values);
    const step = series.length > 1 ? w / (series.length - 1) : w;
    const pts = values.map((v, i) => [i * step, h - (v / max) * (h - 16) - 4]);
    const line = pts.map((p, i) => `${i ? 'L' : 'M'}${p[0].toFixed(1)},${p[1].toFixed(1)}`).join(' ');
    const total = values.reduce((a, b) => a + b, 0);
    return (
        <figure className="lpa-chart">
            <figcaption><span>{label}</span><b>{format(total)}</b></figcaption>
            <svg viewBox={`0 0 ${w} ${h}`} preserveAspectRatio="none" role="img" aria-label={`${label} per day, total ${format(total)}`}>
                <path d={`${line} L${w},${h} L0,${h} Z`} className="lpa-chart__area" />
                <path d={line} className="lpa-chart__line" />
            </svg>
            <div className="lpa-chart__axis"><span>{series[0] && series[0].date}</span><span>{series.length > 0 && series[series.length - 1].date}</span></div>
            <table className="sr-only"><caption>{label}</caption><tbody>{series.map((s) => <tr key={s.date}><th>{s.date}</th><td>{format(s[keyName])}</td></tr>)}</tbody></table>
        </figure>
    );
}
