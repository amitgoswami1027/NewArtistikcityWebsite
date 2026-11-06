import React, { useMemo, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import axios from 'axios';
import AdminLayout, { PageHeader, useToast, errorText } from '@/Components/Admin/AdminLayout';
import { Button, Badge, cx, fmtDate } from '@/Components/Admin/kit';
import { SaveBar, Toggle, useUnsavedGuard } from '@/Components/Admin/form';

/*
 * Configuration Panel (/admin/setup): payment, SMS and Google sign-in settings without editing files on the server.
 * Values saved here win over the server's environment / Parameter Store and over config/artistikcity.yml, and apply
 * at once. Secrets never come back from the server: they show as the mask, and sending the mask back keeps them.
 */

const STATUS = {
    connected: ['green', 'Connected'],
    incomplete: ['amber', 'Incomplete'],
    not_set: ['grey', 'Not set up'],
    off: ['grey', 'Switched off'],
    unused: ['grey', 'Not in use'],
    development: ['blue', 'Development (log only)'],
    not_connected: ['amber', 'Not connected'],
};

const SOURCE = {
    panel: ['brand', 'Set here', 'Saved in this panel. It wins over the server and the defaults.'],
    server: ['blue', 'Server', 'From the server environment (AWS Parameter Store / environment variable).'],
    default: ['grey', 'Default', 'The default from config/artistikcity.yml.'],
    none: ['grey', 'Empty', 'Not set anywhere.'],
};

const toBool = (v) => String(v).toLowerCase() === 'true';

function initialValues(tabs) {
    const v = {};
    tabs.forEach((t) => t.groups.forEach((g) => g.fields.forEach((f) => { v[f.key] = f.value == null ? '' : String(f.value); })));
    return v;
}

function SourceBadge({ field }) {
    const [tone, label, title] = SOURCE[field.source] || SOURCE.none;
    return <span className={cx('cfg-src', `is-${tone}`)} title={title}>{label}</span>;
}

function FieldRow({ field, value, onChange, error, mask, resetMarked, onReset, canStoreSecrets }) {
    const id = `cfg-${field.key}`;
    const [reveal, setReveal] = useState(false);
    const describedBy = [field.help && `${id}-help`, error && `${id}-err`].filter(Boolean).join(' ') || undefined;
    const common = { id, name: field.key, 'aria-invalid': error ? 'true' : undefined, 'aria-describedby': describedBy };
    let control;
    if (field.type === 'bool') {
        control = <Toggle name={field.key} checked={toBool(value)} onChange={(x) => onChange(x ? 'true' : 'false')} label={toBool(value) ? 'On' : 'Off'} />;
    } else if (field.type === 'select') {
        control = (
            <select {...common} className="adm-input" value={value} onChange={(e) => onChange(e.target.value)}>
                {!(field.options || []).includes(value) && <option value={value}>{value || 'Choose…'}</option>}
                {(field.options || []).map((o) => <option key={o} value={o}>{o}</option>)}
            </select>
        );
    } else if (field.type === 'secret') {
        const masked = value === mask;
        control = (
            <div className="cfg-secret">
                <input {...common} type={reveal && !masked ? 'text' : 'password'} className="adm-input" autoComplete="new-password" spellCheck="false"
                    value={value} maxLength={field.maxLength || 4000} disabled={!canStoreSecrets}
                    placeholder={field.isSet ? 'Saved (type to replace)' : field.placeholder || 'Not set'}
                    onFocus={(e) => { if (masked) e.target.select(); }}
                    onChange={(e) => onChange(e.target.value)} />
                {!masked && value && (
                    <button type="button" className="cfg-eye" onClick={() => setReveal((r) => !r)} aria-label={reveal ? 'Hide what you typed' : 'Show what you typed'}>
                        <i className={`fa ${reveal ? 'fa-eye-slash' : 'fa-eye'}`} aria-hidden="true"></i>
                    </button>
                )}
            </div>
        );
    } else {
        control = (
            <input {...common} type={field.type === 'number' ? 'text' : 'text'} inputMode={field.type === 'number' ? 'decimal' : undefined}
                className="adm-input" value={value} spellCheck="false" maxLength={field.maxLength || 4000}
                placeholder={field.placeholder || ''} onChange={(e) => onChange(e.target.value)} />
        );
    }
    return (
        <div className={cx('cfg-field', error && 'has-error', resetMarked && 'is-reset')}>
            <div className="cfg-field__label">
                <label htmlFor={field.type === 'bool' ? undefined : id}>{field.label}</label>
                <SourceBadge field={field} />
                {field.unreadable && <Badge tone="red">Can't decrypt</Badge>}
            </div>
            <div className="cfg-field__control">
                {control}
                {field.help && <p id={`${id}-help`} className="cfg-help">{field.help}</p>}
                {error && <p id={`${id}-err`} className="adm-field__error" role="alert"><i className="fa fa-exclamation-circle" aria-hidden="true"></i>{error}</p>}
                {resetMarked && <p className="cfg-help cfg-help--warn">Will go back to the {field.serverSet ? 'server value' : 'default'} when you save.</p>}
                <p className="cfg-meta">
                    <code title="Environment variable on the server">{field.env}</code>
                    {field.changed && field.changed.at && <span> · changed {fmtDate(field.changed.at, true)}{field.changed.by ? ` by ${field.changed.by}` : ''}</span>}
                    {field.source === 'panel' && !resetMarked && <> · <button type="button" className="cfg-link" onClick={onReset}>Reset to {field.serverSet ? 'server value' : 'default'}</button></>}
                    {resetMarked && <> · <button type="button" className="cfg-link" onClick={onReset}>Keep the panel value</button></>}
                </p>
            </div>
        </div>
    );
}

function SmsTest({ provider }) {
    const [to, setTo] = useState('');
    const [busy, setBusy] = useState(false);
    const [result, setResult] = useState(null);
    const send = () => {
        setBusy(true); setResult(null);
        axios.post('/admin/api/setup/sms-test', { to }).then((r) => setResult({ ok: true, text: r.data.message }))
            .catch((e) => setResult({ ok: false, text: (e.response && e.response.data && e.response.data.errors && e.response.data.errors.to) || errorText(e) }))
            .finally(() => setBusy(false));
    };
    return (
        <div className="cfg-test">
            <label htmlFor="cfg-sms-to"><b>Send a test message</b> <span className="adm-muted">through {provider} (saved settings)</span></label>
            <div className="adm-inline">
                <input id="cfg-sms-to" className="adm-input" type="tel" value={to} onChange={(e) => setTo(e.target.value)} placeholder="+919876543210" style={{ maxWidth: 220 }} />
                <Button size="sm" icon="fa-paper-plane" busy={busy} disabled={!to.trim()} onClick={send}>Send test</Button>
            </div>
            {result && <p role="status" className={cx('cfg-help', !result.ok && 'cfg-help--err')}>{result.text}</p>}
        </div>
    );
}

export default function Config() {
    const props = usePage().props;
    const [data, setData] = useState({ tabs: props.tabs || [], mask: props.mask, canStoreSecrets: props.canStoreSecrets, googleCallback: props.googleCallback, smsProvider: props.smsProvider });
    const initialTab = (() => {
        try { const h = window.location.hash.replace('#', ''); if ((props.tabs || []).some((t) => t.id === h)) return h; } catch (e) { /* SSR */ }
        return (props.tabs && props.tabs[0] && props.tabs[0].id) || 'payments';
    })();
    const [tab, setTab] = useState(initialTab);
    const [base, setBase] = useState(() => initialValues(props.tabs || []));
    const [v, setV] = useState(base);
    const [reset, setReset] = useState([]);
    const [errors, setErrors] = useState({});
    const [busy, setBusy] = useState(false);
    const [toastNode, toast] = useToast();

    const changedKeys = useMemo(() => Object.keys(v).filter((k) => v[k] !== base[k]), [v, base]);
    const dirty = changedKeys.length > 0 || reset.length > 0;
    useUnsavedGuard(dirty);
    const fieldTab = useMemo(() => {
        const m = {};
        data.tabs.forEach((t) => t.groups.forEach((g) => g.fields.forEach((f) => { m[f.key] = t.id; })));
        return m;
    }, [data.tabs]);

    const pick = (id) => {
        setTab(id);
        try { window.history.replaceState(window.history.state, '', `#${id}`); } catch (e) { /* old browser */ }
    };
    const set = (k, x) => { setV((p) => ({ ...p, [k]: x })); setErrors((e) => ({ ...e, [k]: null })); setReset((r) => r.filter((y) => y !== k)); };
    const toggleReset = (k) => {
        setReset((r) => (r.includes(k) ? r.filter((y) => y !== k) : [...r, k]));
        setV((p) => ({ ...p, [k]: base[k] }));
        setErrors((e) => ({ ...e, [k]: null }));
    };

    const save = () => {
        const values = {};
        changedKeys.forEach((k) => { values[k] = v[k]; });
        setBusy(true);
        axios.post('/admin/api/setup/config', { values, reset }).then((r) => {
            const cfg = r.data.config;
            setData({ tabs: cfg.tabs, mask: cfg.mask, canStoreSecrets: cfg.canStoreSecrets, googleCallback: cfg.googleCallback, smsProvider: cfg.smsProvider });
            const next = initialValues(cfg.tabs);
            setBase(next); setV(next); setReset([]); setErrors({});
            toast(r.data.message);
        }).catch((e) => {
            if (e.response && e.response.status === 422) {
                const errs = e.response.data.errors || {};
                setErrors(errs);
                const first = Object.keys(errs).find((k) => fieldTab[k]);
                if (first && fieldTab[first] !== tab) setTab(fieldTab[first]);
                toast(e.response.data.message || 'Please fix the highlighted settings.', 'error');
                setTimeout(() => { const el = first && document.getElementById(`cfg-${first}`); if (el) { el.focus(); el.scrollIntoView({ block: 'center' }); } }, 50);
            } else toast(errorText(e), 'error');
        }).finally(() => setBusy(false));
    };
    const discard = () => { setV(base); setReset([]); setErrors({}); };

    const current = data.tabs.find((t) => t.id === tab) || data.tabs[0];
    const tabErrors = (t) => t.groups.reduce((n, g) => n + g.fields.filter((f) => errors[f.key]).length, 0);
    const tabChanges = (t) => t.groups.reduce((n, g) => n + g.fields.filter((f) => changedKeys.includes(f.key) || reset.includes(f.key)).length, 0);

    return (
        <AdminLayout title="Configuration Panel" crumbs={[{ label: 'System parameters' }, { label: 'Configuration Panel' }]}>
            <PageHeader eyebrow="System parameters" title="Configuration Panel"
                subtitle="Payment gateways, SMS and Google sign-in. Saved values win over the server's settings and apply straight away; every change is in the security log (secrets never are)." />

            {!data.canStoreSecrets && (
                <div className="cfg-alert" role="alert">
                    <i className="fa fa-lock" aria-hidden="true"></i>
                    <span><b>Secrets can't be saved here yet.</b> The server has no encryption key. Set <code>CONFIG_ENCRYPTION_KEY</code> (or <code>OTP_SECRET</code>) on the server; other settings still work.</span>
                </div>
            )}

            <div className="adm-tabs" role="tablist" aria-label="Configuration sections">
                {data.tabs.map((t) => (
                    <button key={t.id} type="button" role="tab" id={`tab-${t.id}`} aria-controls={`panel-${t.id}`} aria-selected={t.id === tab} className={cx(t.id === tab && 'is-on')} onClick={() => pick(t.id)}>
                        <i className={`fa ${t.icon}`} aria-hidden="true"></i> {t.title}
                        {tabErrors(t) > 0 ? <Badge tone="red">{tabErrors(t)}</Badge> : tabChanges(t) > 0 ? <Badge tone="amber">{tabChanges(t)}</Badge> : null}
                    </button>
                ))}
            </div>

            {current && (
                <form id={`panel-${current.id}`} role="tabpanel" aria-labelledby={`tab-${current.id}`} onSubmit={(e) => { e.preventDefault(); save(); }} noValidate autoComplete="off">
                    {current.id === 'google' && data.googleCallback && (
                        <div className="cfg-note">
                            <b>Authorised redirect URI for the Google console:</b>
                            <code className="cfg-copy">{data.googleCallback}</code>
                            <Button size="sm" variant="ghost" icon="fa-clipboard" onClick={() => { try { navigator.clipboard.writeText(data.googleCallback); toast('Copied.'); } catch (e) { toast('Select the address and copy it.', 'error'); } }}>Copy</Button>
                        </div>
                    )}
                    {current.groups.map((g) => {
                        const [tone, label] = STATUS[g.status] || ['grey', g.status || ''];
                        return (
                            <section key={g.id} className={cx('adm-card cfg-group', g.status === 'not_connected' && 'is-muted')} aria-labelledby={`grp-${g.id}`}>
                                <header className="cfg-group__head">
                                    <div>
                                        <h2 id={`grp-${g.id}`} className="rm-h">{g.title}</h2>
                                        {g.intro && <p className="adm-muted adm-small">{g.intro}</p>}
                                    </div>
                                    {g.status && <Badge tone={tone} dot>{label}</Badge>}
                                </header>
                                <div className="cfg-fields">
                                    {g.fields.map((f) => (
                                        <FieldRow key={f.key} field={f} value={v[f.key] == null ? '' : v[f.key]} onChange={(x) => set(f.key, x)} error={errors[f.key]}
                                            mask={data.mask} resetMarked={reset.includes(f.key)} onReset={() => toggleReset(f.key)} canStoreSecrets={data.canStoreSecrets} />
                                    ))}
                                </div>
                                {current.id === 'sms' && g.id === 'provider' && <SmsTest provider={data.smsProvider} />}
                            </section>
                        );
                    })}
                    {errors.reset && <p className="adm-field__error" role="alert">{errors.reset}</p>}
                    <SaveBar dirty={dirty} busy={busy} onSave={save} saveLabel={dirty ? `Save ${changedKeys.length + reset.length} change${changedKeys.length + reset.length === 1 ? '' : 's'}` : 'Save changes'}>
                        {dirty && <Button variant="ghost" onClick={discard}>Discard</Button>}
                    </SaveBar>
                </form>
            )}
            {toastNode}
        </AdminLayout>
    );
}
