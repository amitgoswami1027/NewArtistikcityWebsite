// Studio Stories (/studio-stories): every published story from Admin > Content > Studio Stories, with topics, search and story pages.
const { test, expect } = require('@playwright/test');

test.describe('Studio Stories journal', () => {
    test('lists published stories, filters by topic and opens a story', async ({ page }) => {
        await page.goto('/studio-stories');
        await expect(page.getByRole('heading', { level: 1 })).toContainText('Studio');
        const tiles = page.locator('section[aria-labelledby="sty-all"] .sty-tile');
        await expect(tiles.first()).toBeVisible();
        const topic = page.getByRole('tablist', { name: 'Topics' }).getByRole('tab').nth(1);
        await topic.click();
        await expect(page).toHaveURL(/[?&]category=/);
        await expect(topic).toHaveAttribute('aria-selected', 'true');
        await page.getByRole('tab', { name: 'All stories' }).click();
        await page.locator('#sty-q').fill('zzzz-no-such-story');
        await expect(page.getByText('No stories match')).toBeVisible();
        await page.locator('#sty-q').fill('');
        const first = tiles.first().locator('h3 a');
        const title = (await first.innerText()).trim();
        await first.click();
        await expect(page).toHaveURL(/\/studio-stories\/[a-z0-9-]+$/);
        await expect(page.getByRole('heading', { level: 1 })).toHaveText(title);
        await expect(page.getByRole('heading', { name: 'Keep reading' })).toBeVisible();
    });

    test('old blog addresses move to Studio Stories', async ({ page }) => {
        await page.goto('/blog/posts');
        await expect(page).toHaveURL(/\/studio-stories$/);
    });

    test('an unknown story is a 404', async ({ page }) => {
        const r = await page.goto('/studio-stories/no-such-story-anywhere');
        expect(r.status()).toBe(404);
    });
});
