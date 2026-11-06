import React, { useEffect, useState } from 'react';
import { useForm } from '@inertiajs/inertia-react';
import AuthLayout from '@/Layouts/AuthLayout';
import { Field, SocialButtons, StrengthMeter } from '@/Components/Auth/AuthFields';
import { MagicLinkForm, MethodTabs, PhoneOtpForm } from '@/Components/Auth/SignInMethods';

/** Join: Google / Facebook, phone (SMS code), or e-mail (password or passwordless link). */
export default function Register({ providers, defaultCountryCode, otpDevMode, socialSetupHint }) {
    const { data, setData, post, processing, errors, reset } = useForm({
        name: '', email: '', password: '', password_confirmation: '', newsletter_signup: true, remember: true,
    });
    const [tab, setTab] = useState('email');
    const [passwordless, setPasswordless] = useState(false);

    useEffect(() => () => reset('password', 'password_confirmation'), []);

    const change = (e) => {
        const { name, type, checked, value } = e.target;
        if (name === 'password') setData({ ...data, password: value, password_confirmation: value });
        else setData(name, type === 'checkbox' ? checked : value);
    };
    const submit = (e) => { e.preventDefault(); post(route('register'), { preserveScroll: true, preserveState: true }); };
    const query = typeof window !== 'undefined' ? window.location.search : '';
    const loginHref = query.includes('return_to') ? route('login') + query : route('login');

    return (
        <AuthLayout title="Join for free">
            <h1>Join for free</h1>
            <p className="dm-lede">Create your ArtistikCity account and start learning to draw and paint today.</p>
            <div className="dm-perklist">
                <span><i className="fa fa-check"></i>Free starter lessons</span>
                <span><i className="fa fa-check"></i>Save courses</span>
                <span><i className="fa fa-check"></i>Artist profile</span>
            </div>

            <SocialButtons verb="Sign up" providers={providers} setupHint={socialSetupHint} />
            <div className="dm-or">or</div>
            <MethodTabs value={tab} onChange={setTab} tabs={[
                { id: 'email', label: 'Email', icon: 'fa-envelope-o' },
                { id: 'phone', label: 'Phone', icon: 'fa-mobile' },
            ]} />

            {tab === 'phone' && <PhoneOtpForm defaultCountryCode={defaultCountryCode} devMode={otpDevMode} />}

            {tab === 'email' && passwordless && (
                <>
                    <MagicLinkForm buttonLabel="Email me a link to join" />
                    <p className="dm-center dm-muted" style={{ marginTop: 14 }}>
                        <button type="button" className="dm-linkbtn" onClick={() => setPasswordless(false)}>Choose a password instead</button>
                    </p>
                </>
            )}

            {tab === 'email' && !passwordless && (
                <form onSubmit={submit} noValidate>
                    <Field label="Full name" name="name" value={data.name} onChange={change} error={errors.name}
                           autoComplete="name" placeholder="How should we call you?" />
                    <Field label="Email" name="email" type="email" value={data.email} onChange={change}
                           error={errors.email} autoComplete="email" placeholder="you@example.com" />
                    <Field label="Password" name="password" type="password" value={data.password} onChange={change}
                           error={errors.password || errors.password_confirmation} autoComplete="new-password" placeholder="At least 8 characters">
                        <StrengthMeter password={data.password} />
                    </Field>
                    <label className="ac-optin">
                        <input type="checkbox" name="newsletter_signup" checked={!!data.newsletter_signup} onChange={change} />
                        <span>Send me free lessons, course recommendations and Studio Stories by email. Unsubscribe anytime.</span>
                    </label>
                    <button type="submit" className="dm-btn dm-btn--brand dm-btn--block dm-btn--lg" disabled={processing}>
                        {processing ? 'Creating your account…' : 'Join for free'}
                    </button>
                    <p className="dm-center dm-muted" style={{ marginTop: 14 }}>
                        <button type="button" className="dm-linkbtn" onClick={() => setPasswordless(true)}>Join without a password (we'll e-mail you a link)</button>
                    </p>
                </form>
            )}

            <p className="dm-legal">
                By joining, you agree to ArtistikCity's <a href={route('terms.conditions')}>Terms of use</a> and <a href={route('privacy.policy')}>Privacy policy</a>.
            </p>
            <div className="dm-auth__switch">Already have an account? <a href={loginHref}>Log in</a></div>
        </AuthLayout>
    );
}
