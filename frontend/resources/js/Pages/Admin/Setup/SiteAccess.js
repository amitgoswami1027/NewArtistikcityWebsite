import React, { useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import axios from 'axios';
import AdminLayout, { PageHeader, useToast, errorText } from '@/Components/Admin/AdminLayout';
import { Badge, Button, cx, fmtDate } from '@/Components/Admin/kit';
import { SaveBar, Toggle, useUnsavedGuard } from '@/Components/Admin/form';

/*
 * Site access (/admin/parameters, also /admin/setup/site-access): the Global Site Access Password and the switch that
 * puts the whole public site behind it. The password is hashed on the server and never comes back; leaving both boxes
 * empty keeps the current one. Staff signed in to the admin always see the site and the admin itself is never locked.
 */

function PasswordBox({ id, label, value, onChange, error, help, autoComplete }) {
    const [show, setShow] = useState(false);
    const describedBy = [help && `${id}-help`, error && `${id}-err`].filter(Boolean).join(' ') || undefined;
    return (
        <div className={cx('cfg-field', error && 'has-error')}>
            <div className="cfg-field__label"><label htmlFor={id}>{label}</label></div>
            <div className="cfg-field__control">
                <div className="cfg-secret">
                    <input id={id} type={show ? 'text' : 'password'} className="adm-input" value={value} onChange={(e) => onChange(e.target.value)}
                        autoComplete={autoComplete} spellCheck={false} maxLength={128} aria-invalid={error ? 'true' : undefined} aria-describedby={describedBy} />
                    <button type="button" className="cfg-eye" onClick={() => setShow((x) => !x)} aria-label={show ? 'Hide password' : 'Show password'} aria-pressed={show}>
                        <i className={`fa ${show ? 'fa-eye-slash' : 'fa-eye'}`} aria-hidden="true"></i>
                    </button>
                </div>
                {error ? <p className="cfg-help cfg-help--err" id={`${id}-err`} role="alert">{error}</p> : help && <p className="cfg-help" id={`${id}-help`}>{help}</p>}
            </div>
        </div>
    );
}

export default function SiteAccess() {
    const { access: initial } = usePage().props;
    const [a, setA] = useState(initial);
    const [enabled, setEnabled] = useState(!!initial.enabled);
    const [password, setPassword] = useState('');
    const [confirm, setConfirm] = useState('');
    const [errors, setErrors] = useState({});
    const [busy, setBusy] = useState(false);
    const [toastNode, toast] = useToast();

    const dirty = enabled !== !!a.enabled || password !== '' || confirm !== '';
    useUnsavedGuard(dirty);

    const save = () => {
        setBusy(true);
        axios.post('/admin/api/setup/site-access', { enabled, password, confirm }).then((r) => {
            setA(r.data.access); setEnabled(!!r.data.access.enabled); setPassword(''); setConfirm(''); setErrors({});
            toast(r.data.message);
        }).catch((e) => {
            if (e.response && e.response.status === 422) {
                const errs = e.response.data.errors || {};
                setErrors(errs);
                toast(e.response.data.message || 'Please fix the highlighted fields.', 'error');
                setTimeout(() => { const el = document.getElementById(errs.password ? 'sa-password' : 'sa-confirm'); if (el) el.focus(); }, 50);
            } else toast(errorText(e), 'error');
        }).finally(() => setBusy(false));
    };
    const discard = () => { setEnabled(!!a.enabled); setPassword(''); setConfirm(''); setErrors({}); };

    const [tone, label] = a.active ? ['amber', 'Protected: password required'] : ['green', 'Open to everyone'];

    return (
        <AdminLayout title="Site access" crumbs={[{ label: 'System parameters' }, { label: 'Site access' }]}>
            <PageHeader eyebrow="System parameters" title="Site access"
                subtitle="Put the whole public site behind one password while it is being tested. Visitors see a “Site under construction” page until they enter it." />

            {a.serverLock && (
                <div className="cfg-alert" role="note">
                    <i className="fa fa-server" aria-hidden="true"></i>
                    <span><b>The server also has a preview password</b> (<code>SITE_PREVIEW_PASSWORD</code>). While it is set the site stays protected whatever you choose here; either password lets people in.</span>
                </div>
            )}

            <form onSubmit={(e) => { e.preventDefault(); save(); }} noValidate autoComplete="off">
                <section className="adm-card cfg-group" aria-labelledby="sa-switch-h">
                    <header className="cfg-group__head">
                        <div>
                            <h2 id="sa-switch-h" className="rm-h">Site Password Protection</h2>
                            <p className="adm-muted adm-small">Applies within a few seconds on every server. The admin area stays reachable, and you see the site as normal while you are signed in here.</p>
                        </div>
                        <Badge tone={tone} dot>{label}</Badge>
                    </header>
                    <div className="cfg-fields">
                        <div className="cfg-field">
                            <div className="cfg-field__label"><span className="sa-label">Enable/Disable Site Password Protection</span></div>
                            <div className="cfg-field__control">
                                <Toggle name="enabled" checked={enabled} onChange={(x) => { setEnabled(x); setErrors((e) => ({ ...e, password: null })); }}
                                    label={enabled ? 'Protection on' : 'Protection off'}
                                    description={enabled ? 'Every page and API asks for the password first.' : 'Anyone can visit the site.'} />
                                {enabled && !a.enabled && !a.hasPassword && !password && <p className="cfg-help cfg-help--warn">Set a password below to switch protection on.</p>}
                            </div>
                        </div>
                    </div>
                </section>

                <section className="adm-card cfg-group" aria-labelledby="sa-pw-h">
                    <header className="cfg-group__head">
                        <div>
                            <h2 id="sa-pw-h" className="rm-h">Global Site Access Password</h2>
                            <p className="adm-muted adm-small">
                                {a.hasPassword ? 'A password is set. Leave both boxes empty to keep it; a new one signs everyone out of the preview.' : 'No password yet.'}
                                {' '}It is stored hashed and can't be shown again, so keep it somewhere safe.
                            </p>
                        </div>
                        <Badge tone={a.hasPassword ? 'blue' : 'grey'}>{a.hasPassword ? 'Password set' : 'Not set'}</Badge>
                    </header>
                    <div className="cfg-fields">
                        <PasswordBox id="sa-password" label={a.hasPassword ? 'New password' : 'Password'} value={password} autoComplete="new-password"
                            onChange={(x) => { setPassword(x); setErrors((e) => ({ ...e, password: null })); }} error={errors.password}
                            help={`At least ${a.minLength || 8} characters. Share it only with the people testing the site.`} />
                        <PasswordBox id="sa-confirm" label="Confirm password" value={confirm} autoComplete="new-password"
                            onChange={(x) => { setConfirm(x); setErrors((e) => ({ ...e, confirm: null })); }} error={errors.confirm} />
                    </div>
                    {a.changedAt && <p className="cfg-meta">Last changed {fmtDate(a.changedAt, true)}{a.changedBy ? ` by ${a.changedBy}` : ''}. Every change is in the Security log.</p>}
                </section>

                <SaveBar dirty={dirty} busy={busy} onSave={save} saveLabel="Save site access">
                    {dirty && <Button variant="ghost" onClick={discard}>Discard</Button>}
                </SaveBar>
            </form>
            {toastNode}
        </AdminLayout>
    );
}
