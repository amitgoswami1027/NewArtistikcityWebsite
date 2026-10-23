import React, { useEffect, useState } from 'react';
import { useForm } from '@inertiajs/inertia-react';
import AuthLayout from '@/Layouts/AuthLayout';
import { Field, SocialButtons } from '@/Components/Auth/AuthFields';
import { MagicLinkForm, MethodTabs, PasskeyButton, PhoneOtpForm } from '@/Components/Auth/SignInMethods';

/**
 * Log in: Google / Facebook, passkey, then e-mail (magic link or password) or phone (SMS code).
 */
export default function Login({ status, canResetPassword, providers, defaultCountryCode, otpDevMode, errors: pageErrors = {} }) {
    const [tab, setTab] = useState(() => (typeof window !== 'undefined' && window.localStorage && safeGet('ac.login.tab')) || 'email');
    const [usePassword, setUsePassword] = useState(() => !!(pageErrors.email || pageErrors.password));
    const [passkeyError, setPasskeyError] = useState(null);
    const { data, setData, post, processing, errors, reset } = useForm({ email: '', password: '', remember: true });

    useEffect(() => () => reset('password'), []);
    useEffect(() => { if (errors.password || (errors.email && data.password)) setUsePassword(true); }, [errors]);
    const choose = (t) => { setTab(t); safeSet('ac.login.tab', t); };

    const change = (e) => setData(e.target.name, e.target.type === 'checkbox' ? e.target.checked : e.target.value);
    const submit = (e) => { e.preventDefault(); post(route('login'), { preserveState: true, onFinish: () => setData('password', '') }); };
    const query = typeof window !== 'undefined' ? window.location.search : '';
    const joinHref = query.includes('return_to') ? route('join') + query : route('join');
    const verified = query.includes('verified=1');

    return (
        <AuthLayout title="Log in">
            <h1>Log in</h1>
            <p className="dm-lede">Welcome back. Pick up your courses right where you left off.</p>

            {status && <div className="ac-alert ac-alert--ok">{status}</div>}
            {verified && <div className="ac-alert ac-alert--ok">Your e-mail is confirmed. Log in to continue.</div>}
            {passkeyError && <div className="ac-alert ac-alert--error" role="alert">{passkeyError}</div>}

            <SocialButtons providers={providers} remember={data.remember} />
            <div style={{ marginTop: 10 }}><PasskeyButton verb="Log in" remember={data.remember} onError={setPasskeyError} /></div>

            <div className="dm-or">or</div>
            <MethodTabs value={tab} onChange={choose} tabs={[
                { id: 'email', label: 'Email', icon: 'fa-envelope-o' },
                { id: 'phone', label: 'Phone', icon: 'fa-mobile' },
            ]} />

            {tab === 'phone' && <PhoneOtpForm defaultCountryCode={defaultCountryCode} remember={data.remember} devMode={otpDevMode} />}

            {tab === 'email' && !usePassword && (
                <>
                    <MagicLinkForm initialEmail={data.email} />
                    <p className="dm-center dm-muted" style={{ marginTop: 14 }}>
                        <button type="button" className="dm-linkbtn" onClick={() => setUsePassword(true)}>Log in with a password instead</button>
                    </p>
                </>
            )}

            {tab === 'email' && usePassword && (
                <form onSubmit={submit} noValidate>
                    <Field label="Email" name="email" type="email" value={data.email} onChange={change}
                           error={errors.email} autoComplete="username" placeholder="you@example.com" autoFocus />
                    <Field label="Password" name="password" type="password" value={data.password} onChange={change}
                           error={errors.password} autoComplete="current-password" placeholder="Your password" />
                    <div className="dm-auth__row">
                        {canResetPassword && <a href={route('password.request')}>Forgot your password?</a>}
                        <button type="button" className="dm-linkbtn" onClick={() => setUsePassword(false)}>Email me a link instead</button>
                    </div>
                    <button type="submit" className="dm-btn dm-btn--brand dm-btn--block dm-btn--lg" disabled={processing}>
                        {processing ? 'Logging in…' : 'Log in'}
                    </button>
                </form>
            )}

            <label className="ac-check dm-remember">
                <input type="checkbox" name="remember" checked={!!data.remember} onChange={change} /> Keep me signed in on this device
            </label>

            <div className="dm-auth__switch">Don't have an account? <a href={joinHref}>Join for free</a></div>
        </AuthLayout>
    );
}

function safeGet(k) { try { return window.localStorage.getItem(k); } catch (e) { return null; } }
function safeSet(k, v) { try { window.localStorage.setItem(k, v); } catch (e) { /* private mode */ } }
