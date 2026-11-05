// Portfolio and certificate PDFs: made in memory, kept in private storage (S3 with STORAGE_DRIVER=s3), opened through
// short-lived links that only the owner gets.
const { test, expect } = require('@playwright/test');
const { signIn } = require('./helpers');

test.describe('Portfolio PDF', () => {
    test('a student creates a portfolio PDF and opens it from a private link', async ({ page }) => {
        await signIn(page);
        await page.goto('/dashboard/certificates');
        const [res] = await Promise.all([
            page.waitForResponse((r) => r.url().endsWith('/api/v1/studio/portfolio')),
            page.getByTestId('portfolio-create').click(),
        ]);
        expect(res.status()).toBe(200);
        const j = await res.json();
        expect(j.viewUrl).toBeTruthy();
        expect(j.downloadUrl).toBeTruthy();
        // on S3 these are presigned links (X-Amz-Signature, 10 minutes); locally the owner-checked /dashboard/documents/{id}/file
        expect(j.viewUrl.includes('X-Amz-Signature=') || j.viewUrl.startsWith('/dashboard/documents/')).toBe(true);
        await expect(page.getByTestId('portfolio-viewer')).toBeVisible();

        const pdf = await page.request.get(j.downloadUrl);
        expect(pdf.status()).toBe(200);
        expect(pdf.headers()['content-type']).toContain('application/pdf');
        expect((await pdf.body()).subarray(0, 5).toString()).toBe('%PDF-');
    });

    test('someone who is not signed in cannot open a student document', async ({ page, browser }) => {
        await signIn(page);
        await page.goto('/dashboard/certificates');
        const [res] = await Promise.all([
            page.waitForResponse((r) => r.url().endsWith('/api/v1/studio/portfolio')),
            page.getByTestId('portfolio-create').click(),
        ]);
        const id = (await res.json()).document.id;
        const other = await browser.newContext();
        const r = await other.request.get(`/dashboard/documents/${id}/file`, { maxRedirects: 0 });
        expect([302, 401, 403, 404]).toContain(r.status());
        expect(r.headers().location || '').not.toContain('X-Amz');
        await other.close();
    });

    test('phone: open and download buttons instead of an inline viewer @mobile', async ({ page }) => {
        await signIn(page);
        await page.goto('/dashboard/certificates');
        await page.getByTestId('portfolio-create').click();
        await expect(page.getByTestId('portfolio-download')).toBeVisible();
        await expect(page.locator('iframe')).toHaveCount(0);
        const overflow = await page.evaluate(() => Math.max(document.documentElement.scrollWidth, window.innerWidth) - document.documentElement.clientWidth);
        expect(overflow).toBeLessThanOrEqual(1);
    });
});
