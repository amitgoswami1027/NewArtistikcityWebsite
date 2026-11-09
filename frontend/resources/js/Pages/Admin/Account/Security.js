import React, { useEffect, useState } from 'react';
import axios from 'axios';
import AdminLayout, { PageHeader, useToast, errorText } from '@/Components/Admin/AdminLayout';
import { Card, Button, Badge, DataTable, EmptyState, useConfirm, fmtDate } from '@/Components/Admin/kit';
import { Field, Toggle } from '@/Components/Admin/form';

/*
 * Account › Security: two-step verification (an authenticator app + recovery codes) for the signed-in staff member,
 * and for super admins the staff policy (required for everyone) and resetting a colleague's MFA (AdminMfaController).
 */

function CodeInput({ value, onChange, id = 'f-mfa-code', label = 'Code from your authenticator app (or a recovery code)', error, testid = 'mfa-code' }) {
    return (
        <Field label={label} name="mfa-code" error={error}>
            <input id={id} className="adm-input adm-mono" inputMode="text" autoComplete="one-time-code" maxLength={20} value={value}
                   onChange={(e) => onChange(e.target.value)} placeholder="123456" data-testid={testid} style={{ maxWidth: 220, letterSpacing: '.15em' }} />
        </Field>
    );
}

function RecoveryCodes({ codes, onDone }) {
    const copy = () => { try { navigator.clipboard.writeText(codes.join('\n')); } catch (e) { /* ignore */ } };
    const download = () => {
        const blob = new Blob([`ArtistikCity admin recovery codes\nEach code works once.\n\n${codes.join('\n')}\n`], { type: 'text/plain' });
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = 'artistikcity-recovery-codes.txt';
        a.click();
        URL.revokeObjectURL(a.href);
    };
    return (
        <div className="adm-mfa-codes" data-testid="mfa-recovery-codes">
            <div className="adm-alert" role="status"><i className="fa fa-exclamation-triangle" aria-hidden="true"></i>
                <span>Save these recovery codes now, for example in your password manager. Each one signs you in once if you lose your phone. <b>They won't be shown again.</b></span></div>
            <ul className="adm-mfa-codes__list">{codes.map((c) => <li key={c}><code>{c}</code></li>)}</ul>
            <div className="adm-actions">
                <Button icon="fa-clipboard" onClick={copy}>Copy</Button>
                <Button icon="fa-download" onClick={download}>Download .txt</Button>
                <Button variant="dark" icon="fa-check" onClick={onDone} data-testid="mfa-codes-done">I've saved them</Button>
            </div>
        </div>
    );
}

function MyMfa({ data, setData, toast }) {
    const [setup, setSetup] = useState(null);
    const [code, setCode] = useState('');
    const [error, setError] = useState(null);
    const [busy, setBusy] = useState(false);
    const [codes, setCodes] = useState(null);
    const [mode, setMode] = useState(null);           // 'disable' | 'codes'
    const [confirmNode, confirm] = useConfirm();
    const fail = (e) => { setError((e.response && e.response.data && e.response.data.errors && e.response.data.errors.code) || null); toast(errorText(e), 'error'); };
    const start = () => {
        setBusy(true); setError(null);
        axios.post('/admin/api/account/mfa/setup').then((r) => { setSetup(r.data); setCode(''); }).catch(fail).finally(() => setBusy(false));
    };
    const enable = (e) => {
        e.preventDefault();
        setBusy(true); setError(null);
        axios.post('/admin/api/account/mfa/enable', { code }).then((r) => { setCodes(r.data.recoveryCodes); setSetup(null); setData(r.data); toast(r.data.message); })
            .catch(fail).finally(() => setBusy(false));
    };
    const act = (e) => {
        e.preventDefault();
        setBusy(true); setError(null);
        const url = mode === 'disable' ? '/admin/api/account/mfa/disable' : '/admin/api/account/mfa/recovery-codes';
        axios.post(url, { code }).then((r) => { setData(r.data); toast(r.data.message); if (r.data.recoveryCodes) setCodes(r.data.recoveryCodes); setMode(null); setCode(''); })
            .catch(fail).finally(() => setBusy(false));
    };
    const forget = async () => {
        if (!(await confirm({ title: 'Forget trusted browsers?', body: 'Every browser, including this one, will ask for a code at the next sign-in.', confirmLabel: 'Forget them' }))) return;
        axios.post('/admin/api/account/mfa/forget-browsers').then((r) => { setData(r.data); toast(r.data.message); }).catch(fail);
    };

    if (codes) return <Card title="Two-step verification">{<RecoveryCodes codes={codes} onDone={() => setCodes(null)} />}</Card>;
    return (
        <Card title="Two-step verification" subtitle="After your password, the admin asks for a 6-digit code from an authenticator app on your phone (Google Authenticator, Microsoft Authenticator, Authy, 1Password…). Someone who learns your password still can't get in."
              actions={data.enabled ? <Badge tone="green">On</Badge> : <Badge tone={data.required ? 'red' : 'amber'}>{data.required ? 'Off · required' : 'Off'}</Badge>}>
            {confirmNode}
            {!data.available && <div className="adm-alert" role="status"><i className="fa fa-exclamation-triangle" aria-hidden="true"></i><span>
                {data.schema ? 'This server has no encryption key yet (CONFIG_ENCRYPTION_KEY or OTP_SECRET), so the authenticator secret can\'t be stored safely.' : 'Needs the database update 2026-11-13_admin_mfa.sql.'}</span></div>}
            {data.enabled ? (
                <div className="adm-stack" data-testid="mfa-on">
                    <p className="adm-muted">On since {fmtDate(data.enabledAt, true)}. Recovery codes left: <b data-testid="mfa-left">{data.recoveryLeft}</b>{data.trusted ? ' · this browser is trusted for 30 days' : ''}.</p>
                    {!mode && (
                        <div className="adm-actions">
                            <Button icon="fa-refresh" onClick={() => { setMode('codes'); setCode(''); setError(null); }} data-testid="mfa-new-codes">New recovery codes</Button>
                            <Button icon="fa-desktop" onClick={forget} data-testid="mfa-forget">Forget trusted browsers</Button>
                            {!data.required && <Button icon="fa-power-off" onClick={() => { setMode('disable'); setCode(''); setError(null); }} data-testid="mfa-disable">Turn off</Button>}
                        </div>
                    )}
                    {mode && (
                        <form onSubmit={act} className="adm-stack">
                            <p className="adm-muted">{mode === 'disable' ? 'To turn two-step verification off, confirm with a current code.' : 'New codes replace the old ones. Confirm with a current code.'}</p>
                            <CodeInput value={code} onChange={setCode} error={error} />
                            <div className="adm-actions">
                                <Button type="submit" variant={mode === 'disable' ? 'danger' : 'dark'} busy={busy} data-testid="mfa-confirm">{mode === 'disable' ? 'Turn off' : 'Make new codes'}</Button>
                                <Button onClick={() => setMode(null)}>Cancel</Button>
                            </div>
                        </form>
                    )}
                </div>
            ) : setup ? (
                <form onSubmit={enable} className="adm-mfa-setup" data-testid="mfa-setup">
                    <ol>
                        <li>Install an authenticator app on your phone.</li>
                        <li>Add an account and scan this QR code:
                            <div className="adm-mfa-setup__qr" data-testid="mfa-qr" dangerouslySetInnerHTML={{ __html: setup.qr }} />
                            <details><summary>Can't scan it? Enter this key</summary><code className="adm-mono" data-testid="mfa-secret">{setup.secretGrouped}</code></details>
                        </li>
                        <li>Enter the 6-digit code the app shows:</li>
                    </ol>
                    <CodeInput value={code} onChange={setCode} label="6-digit code" error={error} />
                    <div className="adm-actions">
                        <Button type="submit" variant="dark" icon="fa-shield" busy={busy} data-testid="mfa-enable">Turn on</Button>
                        <Button onClick={() => setSetup(null)}>Cancel</Button>
                    </div>
                </form>
            ) : (
                <Button variant="dark" icon="fa-shield" onClick={start} busy={busy} disabled={!data.available} data-testid="mfa-start">Set up two-step verification</Button>
            )}
        </Card>
    );
}

function StaffPolicy({ data, setData, toast }) {
    const [confirmNode, confirm] = useConfirm();
    const policy = (on) => axios.put('/admin/api/setup/mfa-policy', { required: on }).then((r) => { setData(r.data); toast(r.data.message); }).catch((e) => toast(errorText(e), 'error'));
    const reset = async (s) => {
        if (!(await confirm({ title: `Reset two-step verification for ${s.name}?`, body: 'Use this when they lost their phone and recovery codes. Their authenticator and recovery codes stop working; they set it up again at the next sign-in when it is required.', confirmLabel: 'Reset' }))) return;
        axios.post(`/admin/api/setup/mfa/${s.id}/reset`).then((r) => { setData(r.data); toast(r.data.message); }).catch((e) => toast(errorText(e), 'error'));
    };
    const on = (data.staff || []).filter((s) => s.enabled_at).length;
    return (
        <Card title="Staff policy" subtitle="Super admins only. Every change is in the security log." flush>
            {confirmNode}
            <div className="adm-pad" data-testid="mfa-policy">
                <Toggle name="mfa-required" checked={!!data.required} onChange={policy} label="Require two-step verification for all staff"
                        description={`Staff without it set it up right after their password at their next sign-in. ${on} of ${(data.staff || []).length} accounts have it on.${data.enabled ? '' : ' Turn it on for your own account first.'}`} />
            </div>
            <DataTable rows={data.staff || []} columns={[
                { key: 'name', label: 'Staff member', render: (r) => <>{r.name}<br /><small className="adm-muted">{r.email}</small></> },
                { key: 'enabled_at', label: 'Two-step verification', render: (r) => (r.enabled_at ? <Badge tone="green">On since {fmtDate(r.enabled_at)}</Badge> : <Badge tone="grey">Off</Badge>) },
                { key: 'reset', label: '', render: (r) => (r.enabled_at && r.id !== data.selfId ? <Button size="sm" icon="fa-undo" onClick={() => reset(r)} data-testid={`mfa-reset-${r.id}`}>Reset</Button> : null) },
            ]} empty={<EmptyState icon="fa-users" title="No staff accounts" />} />
        </Card>
    );
}

export default function Security({ admin }) {
    const [data, setData] = useState(null);
    const [toastNode, toast] = useToast();
    useEffect(() => { axios.get('/admin/api/account/mfa').then((r) => setData(r.data)).catch((e) => toast(errorText(e), 'error')); }, []); // eslint-disable-line react-hooks/exhaustive-deps
    const withSelf = data && { ...data, selfId: admin && admin.id };
    return (
        <AdminLayout title="Account security" crumbs={[{ label: 'Your account' }, { label: 'Security' }]}>
            <PageHeader eyebrow="Your account" title="Account security" subtitle="Two-step verification for signing in to the admin." />
            {!data ? <Card><p className="adm-muted">Loading…</p></Card> : (
                <div className="adm-stack" data-testid="account-security">
                    <MyMfa data={withSelf} setData={setData} toast={toast} />
                    {data.canManage && <StaffPolicy data={withSelf} setData={setData} toast={toast} />}
                </div>
            )}
            {toastNode}
        </AdminLayout>
    );
}
