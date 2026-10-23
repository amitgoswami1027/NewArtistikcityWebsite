import React, { useState } from 'react';
import { Head } from '@inertiajs/inertia-react';
import { Inertia } from '@inertiajs/inertia';
import Authenticated from '@/Layouts/Authenticated';
import StudentHeader from '@/Components/Students/StudentHeader';
import StudentNavigation from '@/Components/Students/StudentNavigation';
import MyAccountNavigation from '@/Components/Students/MyAccountNavigation';
import { authDelete, authPost } from '@/Components/Auth/api';
import { passkeysSupported, registerPasskey } from '@/Components/Auth/webauthn';

const PROVIDERS = { google: { label: 'Google', icon: <img src="/assets/images/google-icon.png" alt="" /> },
                    facebook: { label: 'Facebook', icon: <i className="fa fa-facebook-official" style={{ color: '#1877f2' }}></i> } };
const METHOD = { password: 'Password', google: 'Google', facebook: 'Facebook', otp: 'Phone code', magic_link: 'E-mail link', passkey: 'Passkey', signup: 'Sign-up' };
const EVENT = {
    login: 'Signed in', login_failed: 'Failed sign-in attempt', signup: 'Account created', provider_linked: 'Account connected',
    provider_unlinked: 'Account disconnected', passkey_added: 'Passkey added', passkey_removed: 'Passkey removed',
    password_changed: 'Password changed', password_set: 'Password set', password_reset: 'Password reset', phone_verified: 'Phone verified',
    email_verified: 'E-mail confirmed', email_changed: 'E-mail changed', sessions_revoked: 'Signed out other devices',
    session_revoked: 'Signed out a device', refresh_reuse_detected: 'Suspicious session reuse blocked', otp_sent: 'Code sent',
    otp_invalid: 'Wrong code entered', unverified_account_claimed: 'Account secured after e-mail verification',
    magic_link_sent: 'Sign-in link sent', passkey_failed: 'Passkey sign-in failed',
};

function when(s) {
    if (!s) return '';
    const d = new Date(String(s).replace(' ', 'T'));
    return isNaN(d) ? s : d.toLocaleString(undefined, { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

export default function LinkedAccounts(props) {
    const s = props.security;
    const [msg, setMsg] = useState(props.success ? { ok: true, text: props.success } : props.error ? { ok: false, text: props.error } : null);
    const [reauth, setReauth] = useState(false);
    const [busy, setBusy] = useState(null);

    const reload = () => Inertia.reload({ only: ['security'], preserveScroll: true });
    const run = async (key, fn, okText) => {
        setBusy(key);
        setMsg(null);
        try {
            const r = await fn();
            if (okText) setMsg({ ok: true, text: typeof okText === 'function' ? okText(r) : okText });
            reload();
            return r;
        } catch (e) {
            if (e.reauth) setReauth(true);
            else if (!e.cancelled) setMsg({ ok: false, text: e.message });
            return null;
        } finally {
            setBusy(null);
        }
    };
    const signInAgain = async () => {
        const r = await authPost('/auth/reauthenticate');
        window.location.assign(r.redirect);
    };

    return (
        <>
            <StudentHeader auth={props.auth} />
            <Authenticated auth={props.auth} errors={props.errors}>
                <Head title="Login & security" />
                <div className="container-fluid">
                    <div className="row">
                        <StudentNavigation />
                        <main className="col-md-10 px-0 ms-sm-auto rightsidebar">
                            <div className="accountPage p30">
                                <MyAccountNavigation auth={props.auth} />
                                <div className="accountDtl sec">
                                    {msg && <div className={`ac-alert ${msg.ok ? 'ac-alert--ok' : 'ac-alert--error'}`} role="status">{msg.text}</div>}
                                    {reauth && (
                                        <div className="ac-alert ac-alert--error">
                                            For your security, sign in again before changing your sign-in methods.{' '}
                                            <button type="button" className="dm-linkbtn" onClick={signInAgain}>Sign in again</button>
                                        </div>
                                    )}

                                    <SignInMethods s={s} busy={busy} run={run} />
                                    <Connected s={s} busy={busy} run={run} />
                                    <Passkeys s={s} busy={busy} run={run} />
                                    <Sessions s={s} busy={busy} run={run} />
                                    <Activity events={s.events} />
                                </div>
                            </div>
                        </main>
                    </div>
                </div>
            </Authenticated>
        </>
    );
}

/* ------------------------------------------------------------------ e-mail, phone, password */

function SignInMethods({ s, busy, run }) {
    const [editEmail, setEditEmail] = useState(false);
    const [email, setEmail] = useState(s.email || '');
    const [phoneStep, setPhoneStep] = useState(null);
    const [phone, setPhone] = useState('');
    const [sentTo, setSentTo] = useState(null);
    const [code, setCode] = useState('');
    const [devCode, setDevCode] = useState(null);
    const [pw, setPw] = useState(null);

    const saveEmail = async (e) => {
        e.preventDefault();
        const r = await run('email', () => authPost('/auth/email', { email }), (x) => x.message);
        if (r) setEditEmail(false);
    };
    const resendVerification = () => run('verify', () => authPost('/email/verification-notification'), 'Confirmation e-mail sent. Check your inbox.');
    const sendPhone = async (e) => {
        e.preventDefault();
        const full = phone.trim().startsWith('+') ? phone.trim() : `+${s.countryCode}${phone.replace(/^0+/, '')}`;
        const r = await run('phone', () => authPost('/auth/phone/send', { phone: full }));
        if (r) { setSentTo(r); setDevCode(r.devCode || null); setPhoneStep('code'); }
    };
    const verifyPhone = async (e) => {
        e.preventDefault();
        const r = await run('phone', () => authPost('/auth/phone/verify', { phone: sentTo.phone, code }), 'Phone number verified. You can now sign in with a text message code.');
        if (r) { setPhoneStep(null); setCode(''); setPhone(''); }
    };

    return (
        <section className="sec-card">
            <h4>Sign-in methods</h4>

            <div className="sec-row">
                <div className="sec-row__icon"><i className="fa fa-envelope-o"></i></div>
                <div className="sec-row__body">
                    <b>E-mail</b>
                    {!editEmail ? (
                        <div>{s.email || <span className="dm-muted">No e-mail yet</span>}{' '}
                            {s.email && (s.emailVerified ? <span className="sec-badge sec-badge--ok">Verified</span> : <span className="sec-badge">Not confirmed</span>)}
                            <div className="dm-muted sec-small">Sign in with an e-mail link{s.hasPassword ? ' or your password' : ''}.</div>
                        </div>
                    ) : (
                        <form onSubmit={saveEmail} className="sec-inline">
                            <input className="ac-input" type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required />
                            <button className="dm-btn dm-btn--brand" disabled={busy === 'email'}>Save</button>
                            <button type="button" className="dm-btn dm-btn--line" onClick={() => setEditEmail(false)}>Cancel</button>
                        </form>
                    )}
                </div>
                <div className="sec-row__actions">
                    {s.email && !s.emailVerified && !editEmail && <button className="dm-linkbtn" onClick={resendVerification} disabled={busy === 'verify'}>Resend confirmation</button>}
                    {!editEmail && <button className="dm-btn dm-btn--line" onClick={() => setEditEmail(true)}>{s.email ? 'Change' : 'Add'}</button>}
                </div>
            </div>

            <div className="sec-row">
                <div className="sec-row__icon"><i className="fa fa-mobile" style={{ fontSize: 22 }}></i></div>
                <div className="sec-row__body">
                    <b>Phone</b>
                    {!phoneStep && (
                        <div>{s.phone ? s.phoneMasked : <span className="dm-muted">No phone number</span>}{' '}
                            {s.phone && (s.phoneVerified ? <span className="sec-badge sec-badge--ok">Verified</span> : <span className="sec-badge">Not verified</span>)}
                            <div className="dm-muted sec-small">{s.phoneVerified ? 'Sign in with a code sent by text message.' : 'Verify a number to sign in with a text message code.'}</div>
                        </div>
                    )}
                    {phoneStep === 'number' && (
                        <form onSubmit={sendPhone} className="sec-inline">
                            <input className="ac-input" type="tel" inputMode="tel" autoComplete="tel" placeholder={`+${s.countryCode} 98765 43210`}
                                   value={phone} onChange={(e) => setPhone(e.target.value)} required autoFocus />
                            <button className="dm-btn dm-btn--brand" disabled={busy === 'phone'}>Send code</button>
                            <button type="button" className="dm-btn dm-btn--line" onClick={() => setPhoneStep(null)}>Cancel</button>
                        </form>
                    )}
                    {phoneStep === 'code' && (
                        <form onSubmit={verifyPhone} className="sec-inline">
                            <span className="dm-muted sec-small" style={{ width: '100%' }}>Code sent to {sentTo.masked}</span>
                            {devCode && <span className="ac-alert ac-alert--ok dm-devcode" style={{ width: '100%' }}>Development mode: your code is <b>{devCode}</b></span>}
                            <input className="ac-input dm-otp" inputMode="numeric" autoComplete="one-time-code" maxLength={6} value={code}
                                   onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))} placeholder="••••••" required autoFocus />
                            <button className="dm-btn dm-btn--brand" disabled={busy === 'phone' || code.length !== 6}>Verify</button>
                            <button type="button" className="dm-btn dm-btn--line" onClick={() => setPhoneStep(null)}>Cancel</button>
                        </form>
                    )}
                </div>
                <div className="sec-row__actions">
                    {!phoneStep && <button className="dm-btn dm-btn--line" onClick={() => setPhoneStep('number')}>{s.phone ? (s.phoneVerified ? 'Change' : 'Verify') : 'Add'}</button>}
                </div>
            </div>

            <div className="sec-row">
                <div className="sec-row__icon"><i className="fa fa-lock"></i></div>
                <div className="sec-row__body">
                    <b>Password</b>
                    {!pw && <div className="dm-muted sec-small">{s.hasPassword ? 'You can sign in with your e-mail and password.' : "You don't have a password - you sign in without one. You can add one if you like."}</div>}
                    {pw && <PasswordForm hasPassword={s.hasPassword} busy={busy} run={run} done={() => setPw(null)} />}
                </div>
                <div className="sec-row__actions">
                    {!pw && <button className="dm-btn dm-btn--line" onClick={() => setPw(true)}>{s.hasPassword ? 'Change' : 'Set a password'}</button>}
                </div>
            </div>
        </section>
    );
}

function PasswordForm({ hasPassword, busy, run, done }) {
    const [current, setCurrent] = useState('');
    const [next, setNext] = useState('');
    const [again, setAgain] = useState('');
    const submit = async (e) => {
        e.preventDefault();
        const r = await run('password', () => authPost('/auth/password', { current_password: current, password: next, password_confirmation: again }),
            (x) => `Password saved.${x.signedOutOthers ? ` We signed you out on ${x.signedOutOthers} other device(s).` : ''}`);
        if (r) done();
    };
    return (
        <form onSubmit={submit} className="sec-stack">
            {hasPassword && <input className="ac-input" type="password" placeholder="Current password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} required />}
            <input className="ac-input" type="password" placeholder="New password (at least 8 characters)" autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} required minLength={8} />
            <input className="ac-input" type="password" placeholder="Repeat new password" autoComplete="new-password" value={again} onChange={(e) => setAgain(e.target.value)} required />
            <div className="sec-inline">
                <button className="dm-btn dm-btn--brand" disabled={busy === 'password' || !next || next !== again}>Save password</button>
                <button type="button" className="dm-btn dm-btn--line" onClick={done}>Cancel</button>
            </div>
        </form>
    );
}

/* ------------------------------------------------------------------ Google / Facebook */

function Connected({ s, busy, run }) {
    const linked = {};
    (s.linked || []).forEach((l) => { linked[l.provider_name] = l; });
    const names = Object.keys(PROVIDERS).filter((p) => linked[p] || (s.providers && s.providers[p]));
    if (!names.length) return null;
    return (
        <section className="sec-card">
            <h4>Connected accounts</h4>
            {names.map((p) => (
                <div className="sec-row" key={p}>
                    <div className="sec-row__icon">{PROVIDERS[p].icon}</div>
                    <div className="sec-row__body">
                        <b>{PROVIDERS[p].label}</b>
                        <div className="dm-muted sec-small">{linked[p] ? `Connected${linked[p].email ? ` as ${linked[p].email}` : ''} · since ${when(linked[p].created_at)}` : 'Not connected'}</div>
                    </div>
                    <div className="sec-row__actions">
                        {linked[p]
                            ? <button className="dm-btn dm-btn--line" disabled={busy === p}
                                      onClick={() => run(p, () => authDelete(`/auth/linked/${p}`), `${PROVIDERS[p].label} disconnected.`)}>Disconnect</button>
                            : <a className="dm-btn dm-btn--line" href={`/social-login/${p}?intent=link`}>Connect</a>}
                    </div>
                </div>
            ))}
        </section>
    );
}

/* ------------------------------------------------------------------ passkeys */

function Passkeys({ s, busy, run }) {
    const supported = passkeysSupported();
    const add = () => run('passkey-add', () => registerPasskey(), 'Passkey added. Next time, choose "Log in with a passkey".');
    return (
        <section className="sec-card">
            <h4>Passkeys</h4>
            <p className="dm-muted sec-small">Sign in with your fingerprint, face or device PIN instead of a password. Passkeys can't be phished or guessed.</p>
            {(s.passkeys || []).map((k) => (
                <div className="sec-row" key={k.id}>
                    <div className="sec-row__icon"><i className="fa fa-key"></i></div>
                    <div className="sec-row__body">
                        <b>{k.name}</b>{k.backed_up ? <span className="sec-badge sec-badge--ok" style={{ marginLeft: 6 }}>Synced</span> : null}
                        <div className="dm-muted sec-small">Added {when(k.created_at)}{k.last_used_at ? ` · last used ${when(k.last_used_at)}` : ' · not used yet'}</div>
                    </div>
                    <div className="sec-row__actions">
                        <button className="dm-btn dm-btn--line" disabled={busy === `pk${k.id}`}
                                onClick={() => run(`pk${k.id}`, () => authDelete(`/auth/passkey/${k.id}`), 'Passkey removed.')}>Remove</button>
                    </div>
                </div>
            ))}
            {supported
                ? <button className="dm-btn dm-btn--brand" style={{ marginTop: 12 }} onClick={add} disabled={busy === 'passkey-add'}>
                    <i className="fa fa-plus"></i> {busy === 'passkey-add' ? 'Follow the prompt on your device…' : 'Add a passkey'}
                </button>
                : <p className="dm-muted sec-small">This browser doesn't support passkeys.</p>}
        </section>
    );
}

/* ------------------------------------------------------------------ sessions & activity */

function Sessions({ s, busy, run }) {
    const others = (s.sessions || []).filter((x) => !x.current).length;
    return (
        <section className="sec-card">
            <h4>Where you're signed in</h4>
            {(s.sessions || []).map((x) => (
                <div className="sec-row" key={x.id}>
                    <div className="sec-row__icon"><i className={`fa ${/iPhone|Android/.test(x.device) ? 'fa-mobile' : 'fa-desktop'}`}></i></div>
                    <div className="sec-row__body">
                        <b>{x.device}</b>{x.current && <span className="sec-badge sec-badge--ok" style={{ marginLeft: 6 }}>This device</span>}
                        <div className="dm-muted sec-small">Signed in with {METHOD[x.auth_method] || x.auth_method} · {when(x.created_at)} · last active {when(x.last_seen_at)}{x.ip_address ? ` · ${x.ip_address}` : ''}</div>
                    </div>
                    <div className="sec-row__actions">
                        {!x.current && <button className="dm-btn dm-btn--line" disabled={busy === `s${x.id}`}
                                               onClick={() => run(`s${x.id}`, () => authPost(`/auth/sessions/${x.id}/revoke`), 'Signed out on that device.')}>Sign out</button>}
                    </div>
                </div>
            ))}
            {others > 0 && (
                <button className="dm-btn dm-btn--line" style={{ marginTop: 12 }} disabled={busy === 'others'}
                        onClick={() => run('others', () => authPost('/auth/sessions/revoke-others'), (r) => `Signed out on ${r.revoked} other device(s).`)}>
                    Sign out of all other devices
                </button>
            )}
        </section>
    );
}

function Activity({ events }) {
    if (!events || !events.length) return null;
    return (
        <section className="sec-card">
            <h4>Recent security activity</h4>
            <ul className="sec-events">
                {events.map((e, i) => (
                    <li key={i} className={e.success ? '' : 'is-bad'}>
                        <span>{EVENT[e.event] || e.event}{e.method ? ` · ${METHOD[e.method] || e.method}` : ''}</span>
                        <span className="dm-muted">{when(e.created_at)}{e.ip_address ? ` · ${e.ip_address}` : ''}</span>
                    </li>
                ))}
            </ul>
            <p className="dm-muted sec-small">Don't recognise something? Change your password and sign out of all other devices.</p>
        </section>
    );
}
