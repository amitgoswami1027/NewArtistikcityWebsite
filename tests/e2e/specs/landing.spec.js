// Landing Page Management: the public page (desktop + phone), the editor (create, sections, payments, publish) and the
// checkout inside the page (quick sign-in with an e-mail code, only the methods that page allows, test payment, result).
const { test, expect } = require('@playwright/test');
const { signIn, api } = require('./helpers');

const PAGE = '/landing/watercolour-landscapes-for-beginners';
const ADMIN = { email: 'admin@artistikcity.com', password: 'password' };

async function adminSignIn(page) {
    await page.goto('/admin/login');
    await page.fill('input[name=email]', ADMIN.email);
    await page.fill('input[name=password]', ADMIN.password);
    await page.locator('button[type=submit]').click();
    await page.waitForURL(/\/admin\/dashboard/);
}
const noSideScroll = (page) => page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);

test.describe('Public landing page', () => {
    test('hero, price, curriculum, FAQ, gallery and the sticky enrol bar', async ({ page }) => {
        await page.goto(PAGE);
        await expect(page.locator('h1')).toHaveText('Master the Art of Watercolour Landscapes');
        await expect(page.locator('.lp-hero')).toContainText('₹3,999');
        await expect(page.locator('#lp-sticky')).not.toHaveClass(/is-on/);
        // curriculum modules expand
        const mod = page.locator('details.lp-module').nth(1);
        await mod.locator('summary').click();
        await expect(mod).toHaveAttribute('open', '');
        // FAQ answers open with the keyboard too
        const q = page.locator('.lp-faq details').first();
        await q.locator('summary').focus();
        await page.keyboard.press('Enter');
        await expect(q).toHaveAttribute('open', '');
        // full-screen artwork, closed with Escape
        await page.locator('.lp-gallery__item').first().click();
        await expect(page.locator('#lp-lightbox')).toBeVisible();
        await page.keyboard.press('Escape');
        await expect(page.locator('#lp-lightbox')).toBeHidden();
        // the enrol bar appears once the hero is out of view
        await page.evaluate(() => window.scrollTo(0, document.querySelector('.lp-hero').offsetHeight + 400));
        await expect(page.locator('#lp-sticky')).toHaveClass(/is-on/);
        expect(await noSideScroll(page)).toBeLessThanOrEqual(1);
    });

    test('a guest enrols without leaving the page: e-mail code, details, test payment, result', async ({ page }) => {
        await page.goto(PAGE);
        await page.locator('.lp-hero a[data-cta=hero]').click();
        const dlg = page.getByTestId('lp-checkout');
        await expect(dlg).toBeVisible();
        await expect(page).toHaveURL(new RegExp(`${PAGE}#enroll$`));            // still the landing page
        const email = `e2e.${Date.now()}@example.com`;
        await page.fill('#lp-co-email', email);
        await page.getByTestId('lp-co-send').click();
        const text = await dlg.locator('.lp-co__note').first().textContent();     // development: the code is shown to a local browser
        await page.getByTestId('lp-co-code').fill(text.match(/(\d{6})/)[1]);   // six digits submit by themselves
        await expect(page.getByTestId('lp-co-pay')).toBeVisible();
        await expect(page.locator('.lp-top__end .lp-top__link')).toHaveText('My studio');
        await page.fill('#lp-co-fname', 'E2E Buyer');
        await page.fill('#lp-co-fphone', '9876543210');
        await page.getByTestId('lp-co-terms').check();
        await page.getByTestId('lp-co-pay').click();
        await page.getByTestId('lp-co-test-complete').click();
        await expect(page.getByTestId('lp-co-result')).toHaveAttribute('data-state', 'success');
        await expect(page).toHaveURL(new RegExp(`${PAGE}#enroll$`));
        // closing the dialog leaves the visitor on the page
        await page.keyboard.press('Escape');
        await expect(dlg).toBeHidden();
        await expect(page).toHaveURL(new RegExp(`${PAGE}$`));
    });

    test('Log in in the header signs in on the page', async ({ page }) => {
        await page.goto(PAGE);
        await page.locator('.lp-top__end [data-signin]').click();
        await expect(page.getByTestId('lp-checkout')).toBeVisible();
        await expect(page.locator('#lp-co-h')).toHaveText('Sign in');
        await expect(page.getByTestId('lp-co-switch')).toBeVisible();          // e-mail or mobile number
    });

    test('@mobile phone layout: artwork first, fits the screen, sticky enrol bar', async ({ page, isMobile }) => {
        test.skip(!isMobile, 'phone layout');
        await page.goto(PAGE);
        expect(await noSideScroll(page)).toBeLessThanOrEqual(1);
        const art = await page.locator('.lp-hero__media').boundingBox();
        const title = await page.locator('h1').boundingBox();
        expect(art.y).toBeLessThan(title.y);
        await expect(page.locator('#lp-sticky')).not.toHaveClass(/is-on/);
        await page.evaluate(() => window.scrollTo(0, document.querySelector('.lp-hero').offsetHeight + 400));
        await expect(page.locator('#lp-sticky')).toHaveClass(/is-on/);
        const bar = await page.locator('#lp-sticky').boundingBox();
        expect(bar.y + bar.height).toBeLessThanOrEqual(page.viewportSize().height + 1);
    });
});

test.describe('Landing Page Management: build, configure payments, publish, buy', () => {
    test('an admin builds a page in the editor and a student can only pay the way that page allows', async ({ page, browser }) => {
        test.setTimeout(150_000);
        const slug = `e2e-workshop-${Date.now().toString(36)}`;
        await adminSignIn(page);
        await page.goto('/admin/landing/pages/new');
        await page.getByTestId('lp-course').selectOption('4');
        await page.getByTestId('lp-name').fill('E2E workshop page');
        await page.getByTestId('lp-slug').fill(slug);
        await page.locator('.lpa-template', { hasText: 'Creative Workshop' }).click();
        await page.getByTestId('lp-create-submit').click();
        await page.waitForURL(/\/admin\/landing\/pages\/\d+\/sections/);
        const pageId = page.url().match(/pages\/(\d+)/)[1];

        // sections: hide the reviews, move the FAQ up, edit the hero tagline
        const reviews = page.locator('[data-testid=lp-section][data-type=testimonials]');
        await reviews.locator('label.adm-toggle').click();
        await expect(reviews).toHaveClass(/is-off/);
        await page.locator('[data-testid=lp-section][data-type=faq]').getByRole('button', { name: /Move .* up/ }).click();
        await page.locator('[data-testid=lp-section][data-type=hero] .lpa-sec__name').click();
        await page.getByLabel('Tagline').fill('Paint a bouquet in one weekend');
        await page.getByTestId('lp-save-draft').click();
        await expect(page.getByTestId('lp-save-draft')).toHaveText(/Draft saved/);

        // payments for this page only: UPI only
        await page.locator('a[data-step=payments]').click();
        await page.getByTestId('paymode-CUSTOM').check();
        for (const m of ['upi_intent', 'upi_qr', 'gpay', 'phonepe', 'paytm', 'credit_card', 'debit_card', 'netbanking', 'intl_cards', 'paypal']) {
            const t = page.locator(`[data-method=${m}] input[role=switch]`);
            if (await t.isChecked()) await page.locator(`[data-method=${m}] label.adm-toggle`).click();
        }
        await page.getByTestId('payments-save').click();
        await expect(page.locator('[data-method=upi] .lpa-method__state')).toContainText('Shown to buyers');
        await expect(page.locator('[data-method=credit_card] .lpa-method__state')).toContainText('Off');
        await expect(page.locator('[data-method=visa] input[role=switch]')).not.toBeChecked();     // networks follow the cards

        // publish from the last step
        await page.locator('a[data-step=publish]').click();
        await expect(page.getByTestId('lp-checklist')).toContainText('Buyers can pay with: UPI');
        await page.getByTestId('lp-publish').click();
        await expect(page.locator('.lpa-ehead__meta')).toContainText('Published');

        // the public page reflects the draft
        const visitor = await browser.newPage();
        await visitor.goto(`/landing/${slug}`);
        await expect(visitor.locator('.lp-hero')).toContainText('Paint a bouquet in one weekend');
        await expect(visitor.locator('body')).not.toContainText('What students say');
        await visitor.close();

        // a student sees UPI only, and pays (test mode)
        const buyer = await browser.newPage();
        await signIn(buyer);
        await buyer.goto(`/landing/${slug}/enroll`);
        await expect(buyer).toHaveURL(new RegExp(`/landing/${slug}#enroll$`));
        await expect(buyer.getByTestId('lp-checkout')).toBeVisible();
        await expect(buyer.locator('#lp-co [data-method]')).toHaveCount(1);
        await expect(buyer.locator('#lp-co [data-method]')).toHaveAttribute('data-method', 'upi');
        if (await buyer.getByTestId('lp-co-pay').count()) {
            await buyer.locator('#lp-co-fphone').fill('9876543210');
            await buyer.getByTestId('lp-co-terms').check();
            await buyer.getByTestId('lp-co-pay').click();
            await buyer.getByTestId('lp-co-test-complete').click();
            await expect(buyer.getByTestId('lp-co-result')).toHaveAttribute('data-state', /paid|success/);
        }
        await buyer.close();

        // tidy up: archive (pages people bought through are kept, never deleted)
        await api(page, 'POST', `/admin/api/landing/pages/${pageId}/unpublish`);
        await api(page, 'POST', `/admin/api/landing/pages/${pageId}/archive`);
    });

    test('@mobile the editor and checkout fit a phone', async ({ page, isMobile }) => {
        test.skip(!isMobile, 'phone layout');
        await signIn(page, { email: 'diya@example.com', password: 'password' });     // not enrolled in course 1
        await page.goto(`${PAGE}/enroll`);
        await expect(page.getByTestId('lp-checkout')).toBeVisible();
        await expect(page.locator('#lp-co [data-method]').first()).toBeVisible();
        const panel = await page.locator('.lp-co__panel').boundingBox();
        expect(panel.width).toBeLessThanOrEqual(page.viewportSize().width + 1);   // full screen sheet on a phone
        expect(await page.evaluate(() => { const b = document.getElementById('lp-co-body'); return b.scrollWidth - b.clientWidth; })).toBeLessThanOrEqual(1);
    });
});
