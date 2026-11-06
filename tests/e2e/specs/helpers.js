// Shared helpers for the browser tests.
const { expect } = require('@playwright/test');

const STUDENT = { email: 'student@artistikcity.com', password: 'password' };
/** A listing with canvas and poster editions in the demo data (seed.sql). */
const ARTWORK = 'the-old-mill-at-dawn';

// sign-in cookies per account, reused by every test and worker of a run: the log-in form allows 30 attempts per IP in
// 5 minutes, and a full run signs in more often than that
const fs = require('fs');
const os = require('os');
const path = require('path');
const SESSIONS = path.join(os.tmpdir(), 'artistikcity-e2e-sessions.json');
const readSessions = () => { try { return JSON.parse(fs.readFileSync(SESSIONS, 'utf8')); } catch (e) { return {}; } };

async function signIn(page, who = STUDENT) {
    const saved = readSessions()[who.email];
    if (saved) {
        await page.context().addCookies(saved);
        await page.goto('/dashboard');
        if (/\/dashboard/.test(page.url())) return;
    }
    await page.goto('/login');
    await page.getByText('Log in with a password instead').click();
    await page.fill('input[name=email]', who.email);
    await page.fill('input[name=password]', who.password);
    await page.locator('button[type=submit]', { hasText: 'Log in' }).click();
    await page.waitForURL(/\/dashboard|\/user\//);
    const all = readSessions();
    all[who.email] = (await page.context().cookies()).filter((c) => /^ac_/.test(c.name));
    try { fs.writeFileSync(SESSIONS, JSON.stringify(all)); } catch (e) { /* the next test signs in again */ }
}

async function xsrf(page) {
    const c = (await page.context().cookies()).find((x) => x.name === 'XSRF-TOKEN');
    return c ? decodeURIComponent(c.value) : '';
}

/** JSON call with the CSRF header, as the pages make it. */
async function api(page, method, url, body) {
    const r = await page.request.fetch(url, {
        method,
        headers: { 'X-XSRF-TOKEN': await xsrf(page), 'Content-Type': 'application/json', Accept: 'application/json' },
        data: body === undefined ? undefined : JSON.stringify(body),
    });
    let json = null;
    try { json = await r.json(); } catch (e) { /* not JSON */ }
    return { status: r.status(), json };
}

/** Empty the visitor's cart (keeps tests independent). */
async function emptyCart(page) {
    const { json } = await api(page, 'GET', '/api/v1/cart');
    for (const l of (json && json.lines) || []) await api(page, 'DELETE', `/api/v1/cart/items/${l.id}`);
    if (json && json.totals && json.totals.coupon) await api(page, 'DELETE', '/api/v1/cart/coupon');
}

/** Buy a poster through the API with the test payment; returns the order id. */
async function buyPoster(page, slugId = 2) {
    await emptyCart(page);
    let r = await api(page, 'POST', '/api/v1/cart/items', { paintingId: slugId, format: 'POSTER', size: 'S', finish: 'MATTE', frame: 'NONE', qty: 1 });
    expect(r.status, JSON.stringify(r.json)).toBe(200);
    r = await api(page, 'POST', '/api/v1/purchase/checkout', {
        gateway: 'RAZORPAY', name: 'Aarav Sharma', email: STUDENT.email, phone: '9876543210',
        line1: '12 MG Road', city: 'Bengaluru', state: 'Karnataka', postal: '560001', country: 'India',
    });
    expect(r.status, JSON.stringify(r.json)).toBe(200);
    expect(r.json.provider, 'the app must run with test payments (dev profile)').toBe('test');
    const orderId = r.json.orderId;
    r = await api(page, 'POST', '/api/v1/purchase/confirm', { orderId, provider: 'test' });
    expect(r.status, JSON.stringify(r.json)).toBe(200);
    return orderId;
}

module.exports = { STUDENT, ARTWORK, signIn, api, emptyCart, buyPoster };
