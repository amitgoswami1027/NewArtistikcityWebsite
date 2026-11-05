import React, { useState } from 'react';

export function Field({ label, name, type = 'text', value, onChange, error, autoComplete, placeholder, autoFocus, hint, required = true, children }) {
    const [show, setShow] = useState(false);
    const isPassword = type === 'password';
    return (
        <div className="ac-field">
            {label && <label htmlFor={`f-${name}`}>{label}</label>}
            <div className="ac-field__wrap">
                <input
                    id={`f-${name}`}
                    name={name}
                    type={isPassword && show ? 'text' : type}
                    value={value}
                    onChange={onChange}
                    autoComplete={autoComplete}
                    placeholder={placeholder}
                    autoFocus={autoFocus}
                    required={required}
                    className={`ac-input ${error ? 'is-invalid' : ''}`}
                    aria-invalid={!!error}
                    aria-describedby={error ? `e-${name}` : undefined}
                    style={isPassword ? { paddingRight: 64 } : undefined}
                />
                {isPassword && (
                    <button type="button" className="ac-field__toggle" onClick={() => setShow(!show)} aria-label={show ? 'Hide password' : 'Show password'}>{show ? 'Hide' : 'Show'}</button>
                )}
            </div>
            {children}
            {error && <div className="ac-field__error" id={`e-${name}`}>{error}</div>}
            {!error && hint && <div className="ac-field__hint">{hint}</div>}
        </div>
    );
}

function withReturnTo(href) {
    if (typeof window === 'undefined') return href;
    const rt = new URLSearchParams(window.location.search).get('return_to');
    return rt ? `${href}?return_to=${encodeURIComponent(rt)}` : href;
}

export function SocialButtons({ verb = 'Continue' }) {
    return (
        <div className="dm-social">
            <a href={withReturnTo('/social-login/google')}><img src="/assets/images/google-icon.png" alt="" /> {verb} with Google</a>
            <a href={withReturnTo('/social-login/facebook')} className="is-fb"><i className="fa fa-facebook-official"></i> {verb} with Facebook</a>
        </div>
    );
}

export function FormErrors({ errors }) {
    const keys = Object.keys(errors || {});
    if (!keys.length) return null;
    return <div className="ac-alert ac-alert--error" role="alert">{errors[keys[0]]}</div>;
}

export function passwordScore(pw) {
    let s = 0;
    if (!pw) return 0;
    if (pw.length >= 8) s++;
    if (/[A-Z]/.test(pw) && /[a-z]/.test(pw)) s++;
    if (/\d/.test(pw)) s++;
    if (/[^A-Za-z0-9]/.test(pw) || pw.length >= 12) s++;
    return s;
}

export function StrengthMeter({ password }) {
    const score = passwordScore(password);
    const labels = ['Too short', 'Weak', 'Fair', 'Good', 'Strong'];
    return (
        <>
            <div className="ac-strength" aria-hidden="true">
                {[0, 1, 2, 3].map((i) => <span key={i} className={i < score ? 'on' : ''}></span>)}
            </div>
            {password && <div className="ac-field__hint">Password strength: {labels[score]}</div>}
        </>
    );
}
