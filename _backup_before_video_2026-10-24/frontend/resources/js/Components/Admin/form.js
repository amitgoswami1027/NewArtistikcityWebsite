import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
    EditorProvider, Editor, Toolbar as RswToolbar, BtnUndo, BtnRedo, BtnBold, BtnItalic, BtnUnderline, BtnStrikeThrough,
    BtnNumberedList, BtnBulletList, BtnLink, BtnClearFormatting, Separator, createButton,
} from 'react-simple-wysiwyg';
import axios from 'axios';
import { cx, Button } from '@/Components/Admin/kit';

/** Uploads an image for a rich text editor; resolves with its URL. */
export const uploadImage = (file) => {
    const fd = new FormData();
    fd.append('file', file);
    return axios.post('/admin/api/media', fd).then((r) => r.data.url);
};

/*
 * Admin form kit: useForm (values, inline validation, server 422 errors), sectioned layout, fields,
 * toggles, tag input, slug field, drag-and-drop upload zone, list editor and a rich text editor with preview.
 */

/* ---------------------------------------------------------------- validation */

const RULES = {
    required: (v) => (v === null || v === undefined || String(v).trim() === '' || (Array.isArray(v) && v.length === 0)) && 'This field is required.',
    email: (v) => v && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(v).trim()) && 'Enter a valid e-mail address.',
    url: (v) => v && !/^https?:\/\/[^\s]+$/i.test(String(v).trim()) && 'Enter a full link starting with http:// or https://',
    slug: (v) => v && !/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(String(v)) && 'Use lowercase letters, numbers and single dashes only.',
    number: (v) => v !== '' && v !== null && v !== undefined && Number.isNaN(Number(v)) && 'Enter a number.',
    date: (v) => v && Number.isNaN(new Date(String(v)).getTime()) && 'Enter a valid date.',
};

export function check(value, rules = []) {
    for (const r of rules) {
        const [name, arg] = String(r).split(':');
        if (name === 'max') { if (value && String(value).length > Number(arg)) return `Keep it under ${arg} characters.`; continue; }
        if (name === 'min') { if (value !== '' && value !== null && value !== undefined && Number(value) < Number(arg)) return `Must be at least ${arg}.`; continue; }
        if (name === 'minlen') { if (value && String(value).length < Number(arg)) return `Use at least ${arg} characters.`; continue; }
        const fn = RULES[name];
        const msg = fn && fn(value);
        if (msg) return msg;
    }
    return null;
}

/**
 * const form = useForm({title: ''}, {title: ['required', 'max:255']});
 * <input {...form.bind('title')} />  ·  form.submit(() => axios.post(...)).then(...)
 */
export function useForm(initial, rules = {}) {
    const [values, setValues] = useState(initial);
    const [errors, setErrors] = useState({});
    const [busy, setBusy] = useState(false);
    const [saved, setSaved] = useState(initial);
    const rulesRef = useRef(rules);
    rulesRef.current = rules;

    const set = (name, value) => {
        setValues((v) => ({ ...v, [name]: value }));
        setErrors((e) => (e[name] ? { ...e, [name]: null } : e));
    };
    const setMany = (patch) => setValues((v) => ({ ...v, ...patch }));
    const validateField = (name, value = values[name]) => {
        const msg = check(value, rulesRef.current[name]);
        setErrors((e) => ({ ...e, [name]: msg }));
        return msg;
    };
    const validate = () => {
        const next = {};
        Object.keys(rulesRef.current).forEach((k) => { const m = check(values[k], rulesRef.current[k]); if (m) next[k] = m; });
        setErrors(next);
        if (Object.keys(next).length) focusFirst(Object.keys(next));
        return Object.keys(next).length === 0;
    };
    const focusFirst = (names) => {
        setTimeout(() => {
            const el = names.map((n) => document.querySelector(`[data-field="${n}"] input, [data-field="${n}"] textarea, [data-field="${n}"] select, [data-field="${n}"] [contenteditable], [data-field="${n}"] button`)).find(Boolean);
            if (el) { el.focus({ preventScroll: true }); el.scrollIntoView({ block: 'center', behavior: 'smooth' }); }
        }, 20);
    };
    /** Runs the request; a 422 answer puts its errors on the fields. Resolves with the response or rejects. */
    const submit = (request, { skipValidation = false } = {}) => {
        if (!skipValidation && !validate()) return Promise.reject(Object.assign(new Error('invalid'), { invalid: true }));
        setBusy(true);
        return request().then((r) => { setSaved(values); return r; }).catch((e) => {
            if (e.response && e.response.status === 422 && e.response.data && e.response.data.errors) {
                setErrors(e.response.data.errors);
                focusFirst(Object.keys(e.response.data.errors));
            }
            throw e;
        }).finally(() => setBusy(false));
    };
    const bind = (name, { type = 'text' } = {}) => ({
        id: `f-${name}`,
        name,
        value: values[name] === null || values[name] === undefined ? '' : values[name],
        onChange: (e) => set(name, type === 'checkbox' ? e.target.checked : e.target.value),
        onBlur: () => { if (rulesRef.current[name]) validateField(name); },
        'aria-invalid': errors[name] ? 'true' : undefined,
        'aria-describedby': errors[name] ? `f-${name}-err` : undefined,
    });
    const dirty = useMemo(() => JSON.stringify(values) !== JSON.stringify(saved), [values, saved]);
    return {
        values, set, setMany, errors, setErrors, error: (n) => errors[n], busy, submit, validate, validateField, bind, dirty,
        reset: (v) => { setValues(v); setSaved(v); setErrors({}); }, markSaved: () => setSaved(values),
    };
}

/** Warns before leaving a page with unsaved changes. */
export function useUnsavedGuard(dirty) {
    useEffect(() => {
        if (!dirty) return undefined;
        const h = (e) => { e.preventDefault(); e.returnValue = ''; return ''; };
        window.addEventListener('beforeunload', h);
        return () => window.removeEventListener('beforeunload', h);
    }, [dirty]);
}

/* ------------------------------------------------------------------ layout */

export function FormSection({ title, description, children, id, aside }) {
    return (
        <section className="adm-fsec" id={id} aria-labelledby={id ? `${id}-t` : undefined}>
            <div className="adm-fsec__intro">
                <h2 id={id ? `${id}-t` : undefined}>{title}</h2>
                {description && <p>{description}</p>}
                {aside}
            </div>
            <div className="adm-fsec__body">{children}</div>
        </section>
    );
}

export function Field({ label, name, hint, error, required, children, counter, max, className, optional }) {
    return (
        <div className={cx('adm-field', error && 'has-error', className)} data-field={name}>
            {label && (
                <label className="adm-field__label" htmlFor={`f-${name}`}>
                    {label}{required && <span className="adm-req" aria-hidden="true"> *</span>}
                    {optional && <span className="adm-opt">Optional</span>}
                    {max && <span className={cx('adm-count', counter > max && 'is-over')} aria-live="polite">{counter || 0}/{max}</span>}
                </label>
            )}
            {children}
            {error ? <p className="adm-field__error" id={`f-${name}-err`} role="alert"><i className="fa fa-exclamation-circle" aria-hidden="true"></i>{error}</p>
                : hint && <p className="adm-field__hint">{hint}</p>}
        </div>
    );
}

export const Row2 = ({ children }) => <div className="adm-grid2">{children}</div>;
export const Row3 = ({ children }) => <div className="adm-grid3">{children}</div>;

export function TextField({ form, name, label, hint, required, max, type = 'text', placeholder, rows, prefix, suffix, optional, inputProps = {} }) {
    const b = form.bind(name);
    const val = form.values[name] || '';
    const input = rows
        ? <textarea {...b} rows={rows} placeholder={placeholder} className="adm-input" {...inputProps}></textarea>
        : <input {...b} type={type} placeholder={placeholder} className="adm-input" {...inputProps} />;
    return (
        <Field label={label} name={name} hint={hint} error={form.error(name)} required={required} max={max} counter={String(val).length} optional={optional}>
            {prefix || suffix ? <div className="adm-affix">{prefix && <span>{prefix}</span>}{input}{suffix && <span>{suffix}</span>}</div> : input}
        </Field>
    );
}

export function SelectField({ form, name, label, options, hint, required, placeholder = 'Choose…', optional }) {
    return (
        <Field label={label} name={name} hint={hint} error={form.error(name)} required={required} optional={optional}>
            <select {...form.bind(name)} className="adm-input">
                <option value="">{placeholder}</option>
                {options.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
            </select>
        </Field>
    );
}

export function Toggle({ checked, onChange, label, description, name, disabled }) {
    return (
        <label className={cx('adm-toggle', disabled && 'is-disabled')} data-field={name}>
            <input type="checkbox" role="switch" checked={!!checked} disabled={disabled} onChange={(e) => onChange(e.target.checked)} aria-checked={!!checked} />
            <span className="adm-toggle__track" aria-hidden="true"><span></span></span>
            <span className="adm-toggle__text"><b>{label}</b>{description && <small>{description}</small>}</span>
        </label>
    );
}

export function ChoiceChips({ options, value = [], onChange, name, multiple = true }) {
    const on = (v) => (multiple ? value.map(String).includes(String(v)) : String(value) === String(v));
    const flip = (v) => {
        if (!multiple) return onChange(v);
        return onChange(on(v) ? value.filter((x) => String(x) !== String(v)) : [...value, v]);
    };
    return (
        <div className="adm-chips" data-field={name} role="group">
            {options.map((o) => (
                <button key={o.value} type="button" className={on(o.value) ? 'is-on' : ''} aria-pressed={on(o.value)} onClick={() => flip(o.value)}>
                    {on(o.value) && <i className="fa fa-check" aria-hidden="true"></i>}{o.label}
                </button>
            ))}
        </div>
    );
}

/* ---------------------------------------------------------------- slug */

export const slugify = (s) => String(s || '').normalize('NFKD').replace(/[̀-ͯ]/g, '').toLowerCase()
    .replace(/&/g, ' and ').replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 90);

/**
 * Slug that follows the title until edited by hand. `prefix` shows the public URL;
 * `onCheck(slug)` may resolve {available, suggestion} to warn about duplicates.
 */
export function SlugField({ form, name = 'slug', source, prefix, label = 'URL slug', onCheck }) {
    // a saved record keeps its URL when the title changes (old links keep working); new records follow the title
    const [existing] = useState(!!form.values[name]);
    const [manual, setManual] = useState(!!form.values[name]);
    const [status, setStatus] = useState(null);
    useEffect(() => {
        if (!manual) form.set(name, slugify(form.values[source]));
    }, [form.values[source]]); // eslint-disable-line react-hooks/exhaustive-deps
    useEffect(() => {
        if (!onCheck || !form.values[name]) { setStatus(null); return undefined; }
        const t = setTimeout(() => onCheck(form.values[name]).then(setStatus).catch(() => setStatus(null)), 400);
        return () => clearTimeout(t);
    }, [form.values[name]]); // eslint-disable-line react-hooks/exhaustive-deps
    const taken = status && status.available === false;
    return (
        <Field label={label} name={name} error={form.error(name) || (taken ? 'This URL is already used.' : null)}
            hint={existing ? 'Changing the URL breaks links people already shared.' : manual ? 'Edited by hand.' : 'Created from the title; edit it if you want a shorter link.'}>
            <div className="adm-affix">
                {prefix && <span>{prefix}</span>}
                <input {...form.bind(name)} className="adm-input" onChange={(e) => { setManual(true); form.set(name, slugify(e.target.value).slice(0, 90) || e.target.value.toLowerCase()); }} />
                {manual && <button type="button" className="adm-affix__btn" onClick={() => { setManual(false); form.set(name, slugify(form.values[source])); }} title="Regenerate from the title"><i className="fa fa-refresh" aria-hidden="true"></i><span className="sr-only">Regenerate from the title</span></button>}
            </div>
            {taken && status.suggestion && (
                <button type="button" className="adm-linkbtn" onClick={() => { setManual(true); form.set(name, status.suggestion); }}>Use “{status.suggestion}” instead</button>
            )}
        </Field>
    );
}

/* ---------------------------------------------------------------- tags */

export function TagInput({ value = [], onChange, suggestions = [], placeholder = 'Add a tag and press Enter', name, max = 20 }) {
    const [text, setText] = useState('');
    const [open, setOpen] = useState(false);
    const add = (t) => {
        const tag = String(t).trim().replace(/\s+/g, ' ').slice(0, 60);
        if (!tag || value.some((v) => v.toLowerCase() === tag.toLowerCase()) || value.length >= max) { setText(''); return; }
        onChange([...value, tag]);
        setText('');
    };
    const matches = suggestions.filter((s) => s.toLowerCase().includes(text.toLowerCase()) && !value.some((v) => v.toLowerCase() === s.toLowerCase())).slice(0, 8);
    return (
        <div className="adm-tags" data-field={name}>
            <div className="adm-tags__box">
                {value.map((t) => (
                    <span key={t} className="adm-tag">{t}<button type="button" onClick={() => onChange(value.filter((x) => x !== t))} aria-label={`Remove ${t}`}><i className="fa fa-times" aria-hidden="true"></i></button></span>
                ))}
                <input id={`f-${name}`} value={text} placeholder={value.length ? '' : placeholder}
                    onChange={(e) => { setText(e.target.value); setOpen(true); }} onFocus={() => setOpen(true)} onBlur={() => setTimeout(() => setOpen(false), 150)}
                    onKeyDown={(e) => {
                        if (e.key === 'Enter' || e.key === ',') { e.preventDefault(); add(text); }
                        if (e.key === 'Backspace' && !text && value.length) onChange(value.slice(0, -1));
                    }} />
            </div>
            {open && text && matches.length > 0 && (
                <ul className="adm-tags__list" role="listbox">
                    {matches.map((m) => <li key={m}><button type="button" onMouseDown={(e) => { e.preventDefault(); add(m); }}>{m}</button></li>)}
                </ul>
            )}
        </div>
    );
}

/* ---------------------------------------------------------- list editor */

/** Editable list of short texts (highlights, outcomes…): add, edit, reorder, remove. */
export function ListEditor({ value = [], onChange, placeholder = 'Add an item', name, max = 30 }) {
    const [text, setText] = useState('');
    const move = (i, d) => { const n = [...value]; const [x] = n.splice(i, 1); n.splice(i + d, 0, x); onChange(n); };
    return (
        <div className="adm-list" data-field={name}>
            {value.map((v, i) => (
                <div key={i} className="adm-list__row">
                    <span className="adm-list__n">{i + 1}</span>
                    <input className="adm-input" value={v} aria-label={`Item ${i + 1}`} onChange={(e) => onChange(value.map((x, j) => (j === i ? e.target.value : x)))} />
                    <button type="button" onClick={() => move(i, -1)} disabled={i === 0} aria-label="Move up"><i className="fa fa-arrow-up" aria-hidden="true"></i></button>
                    <button type="button" onClick={() => move(i, 1)} disabled={i === value.length - 1} aria-label="Move down"><i className="fa fa-arrow-down" aria-hidden="true"></i></button>
                    <button type="button" onClick={() => onChange(value.filter((_, j) => j !== i))} aria-label="Remove"><i className="fa fa-trash-o" aria-hidden="true"></i></button>
                </div>
            ))}
            {value.length < max && (
                <div className="adm-list__add">
                    <input className="adm-input" value={text} placeholder={placeholder} aria-label={placeholder} id={`f-${name}`}
                        onChange={(e) => setText(e.target.value)}
                        onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); if (text.trim()) { onChange([...value, text.trim()]); setText(''); } } }} />
                    <Button size="sm" icon="fa-plus" onClick={() => { if (text.trim()) { onChange([...value, text.trim()]); setText(''); } }}>Add</Button>
                </div>
            )}
        </div>
    );
}

/* ------------------------------------------------------------- drop zone */

const fmtSize = (b) => (b > 1024 * 1024 ? `${(b / 1024 / 1024).toFixed(1)} MB` : `${Math.max(1, Math.round(b / 1024))} KB`);

/**
 * Drag-and-drop / click-to-choose upload area with previews and client-side checks.
 * value: File[]; existing: [{id, url, name}] already stored; kind: 'image' | 'pdf'.
 */
export function DropZone({ name, value = [], onChange, existing = [], onRemoveExisting, multiple = false, kind = 'image', maxMB, hint, label = 'Drop files here or click to choose', error }) {
    const [over, setOver] = useState(false);
    const [local, setLocal] = useState(null);
    const input = useRef(null);
    const limit = maxMB || (kind === 'pdf' ? 15 : 8);
    const accept = kind === 'pdf' ? 'application/pdf,.pdf' : 'image/jpeg,image/png,image/webp,image/gif';
    const previews = useMemo(() => value.map((f) => ({ f, url: kind === 'image' ? URL.createObjectURL(f) : null })), [value, kind]);
    useEffect(() => () => previews.forEach((p) => p.url && URL.revokeObjectURL(p.url)), [previews]);
    const take = (list) => {
        const files = Array.from(list || []);
        const bad = files.find((f) => (kind === 'pdf' ? !/pdf$/i.test(f.type) && !/\.pdf$/i.test(f.name) : !/^image\/(jpeg|png|webp|gif)$/.test(f.type)));
        if (bad) { setLocal(kind === 'pdf' ? `“${bad.name}” is not a PDF.` : `“${bad.name}” is not a JPG, PNG, WebP or GIF image.`); return; }
        const big = files.find((f) => f.size > limit * 1024 * 1024);
        if (big) { setLocal(`“${big.name}” is ${fmtSize(big.size)}; the limit is ${limit} MB.`); return; }
        setLocal(null);
        onChange(multiple ? [...value, ...files] : files.slice(0, 1));
    };
    const shown = multiple || (!value.length && !existing.length);
    const err = local || error;
    return (
        <div className={cx('adm-drop-wrap', err && 'has-error')} data-field={name}>
            {(existing.length > 0 || value.length > 0) && (
                <ul className={cx('adm-files', kind === 'image' && 'is-grid')}>
                    {existing.map((x) => (
                        <li key={`e${x.id || x.url}`}>
                            {kind === 'image' ? <img src={x.url} alt="" /> : <i className="fa fa-file-pdf-o" aria-hidden="true"></i>}
                            <span className="adm-files__name">{kind === 'pdf' ? <a href={x.url} target="_blank" rel="noopener noreferrer">{x.name || 'Current file'}</a> : x.name}</span>
                            {onRemoveExisting && <button type="button" onClick={() => onRemoveExisting(x)} aria-label={`Remove ${x.name || 'file'}`}><i className="fa fa-trash-o" aria-hidden="true"></i></button>}
                        </li>
                    ))}
                    {previews.map(({ f, url }, i) => (
                        <li key={`n${i}`} className="is-new">
                            {url ? <img src={url} alt="" /> : <i className="fa fa-file-pdf-o" aria-hidden="true"></i>}
                            <span className="adm-files__name">{f.name}<small>{fmtSize(f.size)} · new</small></span>
                            <button type="button" onClick={() => onChange(value.filter((_, j) => j !== i))} aria-label={`Remove ${f.name}`}><i className="fa fa-times" aria-hidden="true"></i></button>
                        </li>
                    ))}
                </ul>
            )}
            {shown && (
                <div className={cx('adm-drop', over && 'is-over')} role="button" tabIndex={0} id={`f-${name}`}
                    aria-describedby={err ? `f-${name}-err` : undefined}
                    onClick={() => input.current && input.current.click()}
                    onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); input.current.click(); } }}
                    onDragOver={(e) => { e.preventDefault(); setOver(true); }} onDragLeave={() => setOver(false)}
                    onDrop={(e) => { e.preventDefault(); setOver(false); take(e.dataTransfer.files); }}>
                    <i className={`fa ${kind === 'pdf' ? 'fa-file-pdf-o' : 'fa-cloud-upload'}`} aria-hidden="true"></i>
                    <b>{label}</b>
                    <span>{hint || (kind === 'pdf' ? `PDF up to ${limit} MB` : `JPG, PNG, WebP or GIF up to ${limit} MB${multiple ? ' · several at once' : ''}`)}</span>
                    <input ref={input} type="file" accept={accept} multiple={multiple} hidden onChange={(e) => { take(e.target.files); e.target.value = ''; }} />
                </div>
            )}
            {!shown && (
                <button type="button" className="adm-linkbtn" onClick={() => input.current && input.current.click()}>
                    Replace file<input ref={input} type="file" accept={accept} hidden onChange={(e) => { take(e.target.files); e.target.value = ''; }} />
                </button>
            )}
            {err && <p className="adm-field__error" id={`f-${name}-err`} role="alert"><i className="fa fa-exclamation-circle" aria-hidden="true"></i>{err}</p>}
        </div>
    );
}

/* ---------------------------------------------------------- rich text */

const fmt = (tag) => ({ $el }) => { if ($el) $el.focus(); document.execCommand('formatBlock', false, tag); };
const BtnH2 = createButton('Heading', <b>H2</b>, fmt('H2'));
const BtnH3 = createButton('Subheading', <b>H3</b>, fmt('H3'));
const BtnPara = createButton('Paragraph', <span>¶</span>, fmt('P'));
const BtnQuote = createButton('Quote', <i className="fa fa-quote-left" aria-hidden="true"></i>, fmt('BLOCKQUOTE'));

const wordCount = (html) => {
    const text = String(html || '').replace(/<[^>]+>/g, ' ').replace(/&nbsp;/g, ' ').trim();
    return text ? text.split(/\s+/).length : 0;
};

/**
 * WYSIWYG editor (HTML) with Write / Preview / HTML tabs, word count and optional image upload
 * (`onUpload(file) => Promise<url>` inserts the image at the cursor).
 */
export function RichText({ value, onChange, name, placeholder = 'Start writing…', onUpload, minHeight = 280, error }) {
    const [tab, setTab] = useState('write');
    const [uploading, setUploading] = useState(false);
    const fileRef = useRef(null);
    const rangeRef = useRef(null);
    const BtnImage = useMemo(() => createButton('Insert image', <i className="fa fa-picture-o" aria-hidden="true"></i>, () => {
        const sel = window.getSelection();
        rangeRef.current = sel && sel.rangeCount ? sel.getRangeAt(0).cloneRange() : null;
        if (fileRef.current) fileRef.current.click();
    }), []);
    const insertImage = (file) => {
        if (!file || !onUpload) return;
        setUploading(true);
        onUpload(file).then((url) => {
            const el = document.querySelector(`[data-field="${name}"] .rsw-ce`);
            if (el) el.focus();
            const sel = window.getSelection();
            if (rangeRef.current && sel) { sel.removeAllRanges(); sel.addRange(rangeRef.current); }
            document.execCommand('insertHTML', false, `<img src="${url}" alt="">`);
            if (el) onChange(el.innerHTML);
        }).finally(() => setUploading(false));
    };
    const words = wordCount(value);
    return (
        <div className={cx('adm-rte', error && 'has-error')} data-field={name}>
            <div className="adm-rte__tabs" role="tablist" aria-label="Editor mode">
                {[['write', 'Write'], ['preview', 'Preview'], ['html', 'HTML']].map(([k, l]) => (
                    <button key={k} type="button" role="tab" aria-selected={tab === k} className={tab === k ? 'is-on' : ''} onClick={() => setTab(k)}>{l}</button>
                ))}
                <span className="adm-rte__meta">{uploading ? 'Uploading image…' : `${words} words · ${Math.max(1, Math.round(words / 200))} min read`}</span>
            </div>
            {tab === 'write' && (
                <EditorProvider>
                    <Editor value={value || ''} onChange={(e) => onChange(e.target.value)} placeholder={placeholder}
                        containerProps={{ style: { minHeight } }}>
                        <RswToolbar>
                            <BtnUndo /><BtnRedo /><Separator />
                            <BtnPara /><BtnH2 /><BtnH3 /><BtnQuote /><Separator />
                            <BtnBold /><BtnItalic /><BtnUnderline /><BtnStrikeThrough /><Separator />
                            <BtnBulletList /><BtnNumberedList /><Separator />
                            <BtnLink />{onUpload && <BtnImage />}<BtnClearFormatting />
                        </RswToolbar>
                    </Editor>
                </EditorProvider>
            )}
            {tab === 'preview' && (
                value && wordCount(value) + (String(value).includes('<img') ? 1 : 0) > 0
                    ? <div className="adm-prose adm-rte__preview" dangerouslySetInnerHTML={{ __html: value }}></div>
                    : <div className="adm-rte__preview adm-muted">Nothing to preview yet.</div>
            )}
            {tab === 'html' && (
                <textarea className="adm-input adm-rte__html" value={value || ''} onChange={(e) => onChange(e.target.value)} style={{ minHeight }} aria-label="HTML source" spellCheck="false"></textarea>
            )}
            <input ref={fileRef} type="file" accept="image/jpeg,image/png,image/webp,image/gif" hidden onChange={(e) => { insertImage(e.target.files[0]); e.target.value = ''; }} />
        </div>
    );
}

/* -------------------------------------------------------------- save bar */

export function SaveBar({ dirty, busy, onSave, saveLabel = 'Save changes', children, status }) {
    return (
        <div className={cx('adm-savebar', dirty && 'is-dirty')}>
            <span className="adm-savebar__status" aria-live="polite">
                {busy ? 'Saving…' : dirty ? <><i className="adm-badge__dot is-amber" aria-hidden="true"></i>Unsaved changes</> : status || <><i className="fa fa-check" aria-hidden="true"></i>All changes saved</>}
            </span>
            <div className="adm-savebar__actions">
                {children}
                <Button variant="brand" onClick={onSave} busy={busy} icon="fa-check">{saveLabel}</Button>
            </div>
        </div>
    );
}
