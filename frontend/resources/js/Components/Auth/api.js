import axios from 'axios';

/**
 * POST/DELETE helpers for the /auth/* JSON endpoints. axios sends the XSRF-TOKEN cookie back as the
 * X-XSRF-TOKEN header automatically (CSRF protection). Errors are normalised to
 * { status, message, fieldErrors, retryAfter, reauth }.
 */
export async function authPost(url, body = {}) {
    try {
        const res = await axios.post(url, body, { headers: { Accept: 'application/json' } });
        return res.data;
    } catch (e) {
        throw normalise(e);
    }
}

export async function authDelete(url) {
    try {
        const res = await axios.delete(url, { headers: { Accept: 'application/json' } });
        return res.data;
    } catch (e) {
        throw normalise(e);
    }
}

function normalise(e) {
    const status = e.response ? e.response.status : 0;
    const data = (e.response && e.response.data) || {};
    const fieldErrors = {};
    if (data.errors) {
        Object.keys(data.errors).forEach((k) => {
            const v = data.errors[k];
            fieldErrors[k] = Array.isArray(v) ? v[0] : v;
        });
    }
    let message = data.message;
    if (!message) {
        message = status === 0 ? 'No connection. Check your internet and try again.'
            : status === 419 ? 'Your session expired. Refresh the page and try again.'
            : 'Something went wrong. Please try again.';
    }
    if (status === 422 && Object.keys(fieldErrors).length) {
        message = fieldErrors[Object.keys(fieldErrors)[0]];
    }
    return { status, message, fieldErrors, retryAfter: data.retryAfter, reauth: !!data.reauth, data };
}

/** Full page navigation after signing in (new cookies, fresh shared props). */
export function goTo(url) {
    window.location.assign(url || '/dashboard');
}
