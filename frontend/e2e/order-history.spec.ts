import { expect, test, type Page } from '@playwright/test';

const ordersUrl = '**/accounts/ACC-F92249B8/orders';

async function seedSession(page: Page): Promise<void> {
  await page.addInitScript(() => {
    const session = {
      accessToken: 'test-access-token',
      refreshToken: 'test-refresh-token',
      expiresAt: Date.now() + 60 * 60 * 1000,
      accountId: 'ACC-F92249B8',
      role: 'USER'
    };
    localStorage.setItem('auth.accountId', 'ACC-F92249B8');
    sessionStorage.setItem('auth_session', JSON.stringify(session));
  });
}

test('shows order history rows and statuses', async ({ page }) => {
  await seedSession(page);

  await page.route(ordersUrl, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        {
          orderId: '11111111-1111-1111-1111-111111111111',
          accountId: 'ACC-F92249B8',
          symbol: 'AAPL',
          side: 'BUY',
          quantity: 10,
          price: 150.25,
          status: 'FILLED',
          createdOn: '2026-10-12T12:00:00'
        },
        {
          orderId: '22222222-2222-2222-2222-222222222222',
          accountId: 'ACC-F92249B8',
          symbol: 'TSLA',
          side: 'SELL',
          quantity: 5,
          price: 245.10,
          status: 'NEW',
          createdOn: '2026-10-12T11:00:00'
        }
      ])
    });
  });

  await page.goto('/orders');

  await expect(page.getByRole('heading', { name: 'Order history' })).toBeVisible();
  await expect(page.getByText('AAPL')).toBeVisible();
  await expect(page.getByText('TSLA')).toBeVisible();
  await expect(page.getByText('FILLED')).toBeVisible();
  await expect(page.getByText('NEW')).toBeVisible();
});

test('shows error state when API fails', async ({ page }) => {
  await seedSession(page);

  await page.route(ordersUrl, async (route) => {
    await route.fulfill({ status: 500, body: 'error' });
  });

  await page.goto('/orders');

  await expect(page.getByText('Could not load order history.')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Retry' })).toBeVisible();
});