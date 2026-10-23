import { authPost } from '@/Components/Auth/api';

/** Passkeys (WebAuthn) in the browser: base64url <-> ArrayBuffer conversion and the two ceremonies. */

export function passkeysSupported() {
    return typeof window !== 'undefined' && !!window.PublicKeyCredential && !!navigator.credentials;
}

function toBuffer(b64url) {
    const b64 = b64url.replace(/-/g, '+').replace(/_/g, '/') + '==='.slice((b64url.length + 3) % 4);
    const bin = atob(b64);
    const out = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
    return out.buffer;
}

function toB64url(buf) {
    const bytes = new Uint8Array(buf);
    let bin = '';
    for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
    return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function explain(e) {
    if (!e) return 'Something went wrong.';
    if (e.name === 'NotAllowedError') return 'The passkey request was cancelled or timed out.';
    if (e.name === 'InvalidStateError') return 'This device already has a passkey for your account.';
    if (e.name === 'SecurityError') return 'Passkeys need a secure connection (https) on this website address.';
    if (e.name === 'NotSupportedError') return "This device doesn't support passkeys.";
    return e.message || 'Something went wrong.';
}

/** Sign in with a discoverable passkey. Resolves to the server response ({ ok, redirect }). */
export async function signInWithPasskey(remember = true) {
    const options = await authPost('/auth/passkey/options');
    let cred;
    try {
        cred = await navigator.credentials.get({
            publicKey: {
                challenge: toBuffer(options.challenge),
                rpId: options.rpId,
                timeout: options.timeout,
                userVerification: options.userVerification,
                allowCredentials: [],
            },
        });
    } catch (e) {
        throw { message: explain(e), cancelled: e && e.name === 'NotAllowedError' };
    }
    const r = cred.response;
    return authPost('/auth/passkey/verify', {
        remember,
        credential: {
            id: cred.id,
            rawId: toB64url(cred.rawId),
            type: cred.type,
            response: {
                clientDataJSON: toB64url(r.clientDataJSON),
                authenticatorData: toB64url(r.authenticatorData),
                signature: toB64url(r.signature),
                userHandle: r.userHandle ? toB64url(r.userHandle) : null,
            },
        },
    });
}

/** Creates a passkey for the signed-in user. */
export async function registerPasskey(name) {
    const o = await authPost('/auth/passkey/register/options');
    let cred;
    try {
        cred = await navigator.credentials.create({
            publicKey: {
                challenge: toBuffer(o.challenge),
                rp: o.rp,
                user: { id: toBuffer(o.user.id), name: o.user.name, displayName: o.user.displayName },
                pubKeyCredParams: o.pubKeyCredParams,
                timeout: o.timeout,
                attestation: o.attestation,
                authenticatorSelection: o.authenticatorSelection,
                excludeCredentials: (o.excludeCredentials || []).map((c) => ({ ...c, id: toBuffer(c.id) })),
            },
        });
    } catch (e) {
        throw { message: explain(e), cancelled: e && e.name === 'NotAllowedError' };
    }
    const r = cred.response;
    return authPost('/auth/passkey/register', {
        name,
        credential: {
            id: cred.id,
            rawId: toB64url(cred.rawId),
            type: cred.type,
            response: {
                clientDataJSON: toB64url(r.clientDataJSON),
                attestationObject: toB64url(r.attestationObject),
                transports: typeof r.getTransports === 'function' ? r.getTransports() : [],
            },
        },
    });
}
