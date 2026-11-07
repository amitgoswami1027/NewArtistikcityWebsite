// The three showcase landing pages built with Landing Page Management (LandingShowcase), each at its own address:
// /landing/how-it-works, /landing/watercolour-landscapes-for-beginners and /landing/commission (which hands the chosen
// style to the unchanged commission wizard, steps 2-4). The site's original pages stay as they are.
const path = require('path');
const { test, expect } = require('@playwright/test');

const noSideScroll = (page) => page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
const PHOTO = path.join(__dirname, '..', '..', '..', 'storage', 'uploads', 'artworks', '1', 'artwork.jpg');

test.describe('How it works (information page)', () => {
    test('hero, journey, real courses and artists, and every call to action goes somewhere real', async ({ page }) => {
        await page.goto('/landing/how-it-works');
        await expect(page).toHaveTitle('How ArtistikCity Works | Discover Art, Artists and Creative Learning');
        await expect(page.locator('h1')).toHaveText('Discover, Learn and Create with ArtistikCity');
        await expect(page.locator('link[rel=canonical]')).toHaveAttribute('href', /\/landing\/how-it-works$/);
        await expect(page.locator('.lp-pricebox, .lp-sticky, .lp-pricing')).toHaveCount(0);       // nothing is sold here
        await expect(page.locator('.lp-hero a[data-cta=hero]')).toHaveAttribute('href', '/courses?type=all');
        await expect(page.locator('.lp-step')).toHaveCount(5);
        // courses come from the catalogue: the watercolour card opens its landing page
        await expect(page.locator('.lp-coursecard a', { hasText: 'Watercolour Landscapes for Beginners' })).toHaveAttribute('href', '/landing/watercolour-landscapes-for-beginners');
        await expect(page.locator('.lp-artistcard a', { hasText: 'View profile' }).first()).toHaveAttribute('href', /^\/teacher\//);
        const final = page.locator('.lp-final .lp-ctas a');
        await expect(final).toHaveText(['Explore Courses', 'Discover Artists', 'Commission Artwork']);
        await expect(final.nth(2)).toHaveAttribute('href', '/commission/step-1');
        await final.nth(1).click();
        await expect(page.locator('#meet-artists')).toBeInViewport();
        // second hero button scrolls to the journey
        await page.locator('.lp-hero a[data-cta=hero-secondary]').click();
        await expect(page.locator('#how-steps')).toBeInViewport();
        // FAQ structured data
        const ld = JSON.parse(await page.locator('script[type="application/ld+json"]').textContent());
        expect(ld['@graph'].map((x) => x['@type'])).toEqual(expect.arrayContaining(['WebPage', 'FAQPage']));
        expect(await noSideScroll(page)).toBeLessThanOrEqual(1);
    });

    test('the original pages are untouched', async ({ page }) => {
        await page.goto('/how-it-works');
        await expect(page.locator('.lp-hero')).toHaveCount(0);
        await expect(page.locator('.hiw-timeline > li')).toHaveCount(7);
        await page.goto('/commission/step-1');
        await expect(page.locator('[data-commission-form]')).toHaveCount(0);
        await expect(page.locator('input[name=theme]')).toHaveCount(4);
    });

    test('@mobile fits a phone', async ({ page }) => {
        await page.goto('/landing/how-it-works');
        expect(await noSideScroll(page)).toBeLessThanOrEqual(1);
        await expect(page.locator('.lp-hero a[data-cta=hero]')).toBeVisible();
    });
});

test.describe('Watercolour Landscapes for Beginners (course page)', () => {
    test('verified facts, the course price, curriculum and Enroll goes to this page\'s checkout', async ({ page }) => {
        await page.goto('/landing/watercolour-landscapes-for-beginners');
        await expect(page).toHaveTitle('Watercolour Landscapes for Beginners | ArtistikCity');
        await expect(page.locator('h1')).toHaveText('Master the Art of Watercolour Landscapes');
        await expect(page.locator('body')).toHaveClass(/lp--tex-wash/);
        await expect(page.locator('.lp-hero a[data-cta=hero]')).toHaveAttribute('href', '/landing/watercolour-landscapes-for-beginners/enroll');
        // no preview video on this course: the second button never promises one
        const second = page.locator('.lp-hero a[data-cta=hero-secondary]');
        if (await page.locator('.lp-video').count()) await expect(second).toHaveText(/Watch Course Preview/);
        else await expect(second).toHaveText('Explore the course');
        const mod = page.locator('details.lp-module').nth(1);
        await mod.locator('summary').click();
        await expect(mod).toHaveAttribute('open', '');
        await expect(page.locator('.lp-pricecard .lp-paychip').first()).toBeVisible();
        await expect(page.locator('h2', { hasText: 'Who should join' })).toBeVisible();
    });
});

test.describe('Commission (information page with the step-1 form)', () => {
    test('validation, then the chosen style carries into the existing steps 2 and 3 (upload)', async ({ page }) => {
        await page.goto('/landing/commission');
        await expect(page).toHaveTitle('Custom Art Commissions | Personalized Artwork | ArtistikCity');
        await expect(page.locator('h1')).toHaveText('Turn Your Memories Into Timeless Art');
        await page.locator('.lp-hero a[data-cta=hero]').click();
        await expect(page.locator('#commission-start')).toBeInViewport();
        await expect(page.locator('.lp-theme')).toHaveCount(4);
        await expect(page.locator('.lp-cform__steps li[aria-current=step]')).toHaveText(/Theme/);
        // nothing chosen: same message as the wizard, no navigation
        await page.locator('[data-commission-form] button[type=submit]').click();
        await expect(page.locator('.lp-cform__msg')).toHaveText('Select a theme to continue.');
        await expect(page).toHaveURL(/\/landing\/commission/);
        // prices come from the commission price list
        await expect(page.locator('.lp-cprices li')).toHaveCount(3);
        // choose watercolour and go on
        await page.locator('.lp-theme[data-theme=WATERCOLOR]').click();
        await page.locator('[data-commission-form] button[type=submit]').click();
        await page.waitForURL(/\/commission\/step-2$/);
        const state = await page.evaluate(() => JSON.parse(sessionStorage.getItem('artistikcity.commission.v1')));
        expect(state.theme).toBe('WATERCOLOR');
        // step 2 of the original wizard
        await page.getByText('Digital File Only').click();
        await page.getByRole('button', { name: /Next: Upload Photo/ }).click();
        await page.waitForURL(/\/commission\/step-3$/);
        await page.locator('input[type=file]').setInputFiles(PHOTO);
        await expect.poll(() => page.evaluate(() => JSON.parse(sessionStorage.getItem('artistikcity.commission.v1')).photoUrl), { timeout: 15_000 })
            .toMatch(/^\/storage\/uploads\/commissions\//);
        // back on the landing page the choice is kept
        await page.goto('/landing/commission');
        await expect(page.locator('.lp-theme[data-theme=WATERCOLOR] input')).toBeChecked();
    });

    test('@mobile the request form fits a phone', async ({ page }) => {
        await page.goto('/landing/commission#commission-start');
        expect(await noSideScroll(page)).toBeLessThanOrEqual(1);
        await page.locator('.lp-theme[data-theme=CHARCOAL]').click();
        await expect(page.locator('.lp-theme[data-theme=CHARCOAL] input')).toBeChecked();
    });
});
