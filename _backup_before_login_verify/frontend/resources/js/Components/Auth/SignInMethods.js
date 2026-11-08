import React, { useEffect, useRef, useState } from 'react';
import { Field } from '@/Components/Auth/AuthFields';
import { authPost, goTo } from '@/Components/Auth/api';
import { passkeysSupported, signInWithPasskey } from '@/Components/Auth/webauthn';

/* --------------------------------------------------------------------------------------------- passkey */

export function PasskeyButton({ verb = 'Sign in', remember = true, onError }) {
    const [busy, setBusy] = useState(false);
    if (!passkeysSupported()) return null;
    const go = async () => {
        setBusy(true);
        onError && onError(null);
        try {
            const r = await signInWithPasskey(remember);
            goTo(r.redirect);
        } catch (e) {
            if (!e.cancelled && onError) onError(e.message);
            setBusy(false);
        }
    };
    return (
        <button type="button" className="dm-btn dm-btn--line dm-btn--block dm-btn--lg dm-passkey" onClick={go} disabled={busy}>
            <i className="fa fa-key"></i> {busy ? 'Waiting for your passkey…' : `${verb} with a passkey`}
        </button>
    );
}

/* --------------------------------------------------------------------------------------------- tabs */

export function MethodTabs({ value, onChange, tabs }) {
    return (
        <div className="dm-mtabs" role="tablist">
            {tabs.map((t) => (
                <button key={t.id} type="button" role="tab" aria-selected={value === t.id}
                        className={value === t.id ? 'is-active' : ''} onClick={() => onChange(t.id)}>
                    <i className={`fa ${t.icon}`}></i> {t.label}
                </button>
            ))}
        </div>
    );
}

/* --------------------------------------------------------------------------------------------- e-mail link */

/** "Email me a sign-in link" with the confirmation state. Also creates accounts for new e-mails. */
export function MagicLinkForm({ initialEmail = '', buttonLabel = 'Email me a sign-in link', children }) {
    const [email, setEmail] = useState(initialEmail);
    const [error, setError] = useState(null);
    const [sent, setSent] = useState(null);
    const [busy, setBusy] = useState(false);

    const send = async (e) => {
        e && e.preventDefault();
        setBusy(true);
        setError(null);
        try {
            const rt = new URLSearchParams(window.location.search).get('return_to');
            const r = await authPost('/auth/magic-link', { email, return_to: rt });
            setSent(r.message);
        } catch (err) {
            setError(err.message);
        }
        setBusy(false);
    };

    if (sent) {
        return (
            <div className="dm-sent" role="status">
                <div className="dm-sent__icon"><i className="fa fa-envelope-open-o"></i></div>
                <p>{sent}</p>
                <p className="dm-muted">Open the e-mail on any device and tap the button. Can't find it? Check your spam folder.</p>
                <div className="dm-sent__actions">
                    <button type="button" className="dm-linkbtn" onClick={send} disabled={busy}>Send it again</button>
                    <button type="button" className="dm-linkbtn" onClick={() => setSent(null)}>Use a different e-mail</button>
                </div>
            </div>
        );
    }
    return (
        <form onSubmit={send} noValidate>
            <Field label="Email" name="email" type="email" value={email} onChange={(e) => setEmail(e.target.value)}
                   error={error} autoComplete="email" placeholder="you@example.com" />
            {children}
            <button type="submit" className="dm-btn dm-btn--brand dm-btn--block dm-btn--lg" disabled={busy || !email}>
                {busy ? 'Sending…' : buttonLabel}
            </button>
        </form>
    );
}

/* --------------------------------------------------------------------------------------------- phone + OTP */

const COUNTRIES = [
    ['91', 'India +91'], ['1', 'US / Canada +1'], ['44', 'UK +44'], ['971', 'UAE +971'], ['65', 'Singapore +65'],
    ['61', 'Australia +61'], ['974', 'Qatar +974'], ['966', 'Saudi Arabia +966'], ['49', 'Germany +49'],
];

/** Passwordless phone sign-in / sign-up: number -> 6-digit code -> (new accounts) name. */
export function PhoneOtpForm({ defaultCountryCode = '91', remember = true, devMode = false }) {
    const [step, setStep] = useState('phone');
    const [cc, setCc] = useState(defaultCountryCode);
    const [number, setNumber] = useState('');
    const [phone, setPhone] = useState(null);
    const [masked, setMasked] = useState('');
    const [code, setCode] = useState('');
    const [devCode, setDevCode] = useState(null);
    const [name, setName] = useState('');
    const [email, setEmail] = useState('');
    const [errors, setErrors] = useState({});
    const [notice, setNotice] = useState(null);
    const [busy, setBusy] = useState(false);
    const [wait, setWait] = useState(0);
    const codeRef = useRef(null);

    useEffect(() => {
        if (wait <= 0) return undefined;
        const t = setTimeout(() => setWait(wait - 1), 1000);
        return () => clearTimeout(t);
    }, [wait]);

    // Android Chrome: read the code from the SMS automatically (WebOTP API)
    useEffect(() => {
        if (step !== 'code' || !('OTPCredential' in window)) return undefined;
        const ac = new AbortController();
        navigator.credentials.get({ otp: { transport: ['sms'] }, signal: ac.signal })
            .then((otp) => { if (otp && otp.code) { setCode(otp.code); verify(null, otp.code); } })
            .catch(() => {});
        return () => ac.abort();
    }, [step, phone]);

    const fullNumber = () => (number.trim().startsWith('+') ? number.trim() : `+${cc}${number.replace(/^0+/, '')}`);

    const send = async (e) => {
        e && e.preventDefault();
        setBusy(true);
        setErrors({});
        setNotice(null);
        try {
            const r = await authPost('/auth/otp/send', { phone: phone || fullNumber() });
            setPhone(r.phone);
            setMasked(r.masked);
            setWait(r.resendIn || 30);
            setDevCode(r.devCode || null);
            setCode('');
            setStep('code');
            setTimeout(() => codeRef.current && codeRef.current.focus(), 50);
        } catch (err) {
            setErrors({ phone: err.message });
            if (err.retryAfter) setWait(Math.min(err.retryAfter, 3600));
        }
        setBusy(false);
    };

    const verify = async (e, value) => {
        e && e.preventDefault();
        const c = (value || code).replace(/\D/g, '');
        if (c.length !== 6) {
            setErrors({ code: 'Enter the 6-digit code.' });
            return;
        }
        setBusy(true);
        setErrors({});
        try {
            const r = await authPost('/auth/otp/verify', { phone, code: c, remember });
            if (r.next === 'profile') {
                setStep('profile');
                setBusy(false);
                return;
            }
            goTo(r.redirect);
        } catch (err) {
            setErrors({ code: err.message });
            if (err.status === 410 || err.status === 429) setCode('');
            setBusy(false);
        }
    };

    const complete = async (e) => {
        e.preventDefault();
        setBusy(true);
        setErrors({});
        try {
            const r = await authPost('/auth/otp/complete', { name, email: email || null });
            goTo(r.redirect);
        } catch (err) {
            if (err.status === 410) {
                setStep('phone');
                setNotice(err.message);
            } else {
                setErrors(Object.keys(err.fieldErrors).length ? err.fieldErrors : { name: err.message });
            }
            setBusy(false);
        }
    };

    if (step === 'profile') {
        return (
            <form onSubmit={complete} noValidate>
                <div className="ac-alert ac-alert--ok">Number verified. One last step to create your account.</div>
                <Field label="Your name" name="name" value={name} onChange={(e) => setName(e.target.value)} error={errors.name}
                       autoComplete="name" placeholder="How should we call you?" autoFocus />
                <Field label="Email (optional)" name="email" type="email" value={email} onChange={(e) => setEmail(e.target.value)}
                       error={errors.email} autoComplete="email" placeholder="For course updates and receipts" required={false} />
                <button type="submit" className="dm-btn dm-btn--brand dm-btn--block dm-btn--lg" disabled={busy || !name.trim()}>
                    {busy ? 'Creating your account…' : 'Create my account'}
                </button>
            </form>
        );
    }

    if (step === 'code') {
        return (
            <form onSubmit={verify} noValidate>
                <p className="dm-muted dm-center">We sent a 6-digit code to <b>{masked}</b>.{' '}
                    <button type="button" className="dm-linkbtn" onClick={() => { setStep('phone'); setPhone(null); }}>Change</button>
                </p>
                {devMode && devCode && (
                    <div className="ac-alert ac-alert--ok dm-devcode">Development mode (no SMS is sent): your code is <b>{devCode}</b></div>
                )}
                <div className="ac-field">
                    <label htmlFor="f-otp">Verification code</label>
                    <input id="f-otp" ref={codeRef} className={`ac-input dm-otp ${errors.code ? 'is-invalid' : ''}`} inputMode="numeric"
                           autoComplete="one-time-code" pattern="[0-9]*" maxLength={6} value={code} placeholder="••••••"
                           aria-invalid={!!errors.code} aria-describedby={errors.code ? 'e-otp' : undefined}
                           onChange={(e) => {
                               const v = e.target.value.replace(/\D/g, '').slice(0, 6);
                               setCode(v);
                               if (v.length === 6) verify(null, v);
                           }} />
                    {errors.code && <div className="ac-field__error" id="e-otp">{errors.code}</div>}
                </div>
                <button type="submit" className="dm-btn dm-btn--brand dm-btn--block dm-btn--lg" disabled={busy || code.length !== 6}>
                    {busy ? 'Checking…' : 'Verify and continue'}
                </button>
                <p className="dm-muted dm-center" style={{ marginTop: 14 }}>
                    {wait > 0 ? <>You can request a new code in {wait}s</> :
                        <button type="button" className="dm-linkbtn" onClick={send} disabled={busy}>Send a new code</button>}
                </p>
            </form>
        );
    }

    return (
        <form onSubmit={send} noValidate>
            {notice && <div className="ac-alert ac-alert--error">{notice}</div>}
            <div className="ac-field">
                <label htmlFor="f-phone">Mobile number</label>
                <div className="dm-phone">
                    <select aria-label="Country code" value={cc} onChange={(e) => setCc(e.target.value)} className="ac-input">
                        {COUNTRIES.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
                    </select>
                    <input id="f-phone" type="tel" inputMode="tel" autoComplete="tel-national" className={`ac-input ${errors.phone ? 'is-invalid' : ''}`}
                           placeholder="98765 43210" value={number} onChange={(e) => setNumber(e.target.value)}
                           aria-invalid={!!errors.phone} aria-describedby={errors.phone ? 'e-phone' : 'h-phone'} />
                </div>
                {errors.phone ? <div className="ac-field__error" id="e-phone">{errors.phone}</div>
                    : <div className="ac-field__hint" id="h-phone">We'll text you a code. No password needed. Standard SMS rates may apply.</div>}
            </div>
            <button type="submit" className="dm-btn dm-btn--brand dm-btn--block dm-btn--lg" disabled={busy || number.replace(/\D/g, '').length < 6 || wait > 0}>
                {busy ? 'Sending code…' : wait > 0 ? `Try again in ${wait}s` : 'Send code'}
            </button>
        </form>
    );
}
