import { expect, test, type Page } from '@playwright/test';

const ordersUrl = '**/api/v1/accounts/ACC-F92249B8/orders';

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

  await page.route('**/api/auth/validate', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        status: 'SUCCESS',
        data: {
          valid: true,
          username: 'playwright-user',
          accountId: 'ACC-F92249B8',
          role: 'USER'
        }
      })
    });
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
          executionPrice: 149.75,
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
  await expect(page.getByRole('heading', { name: 'All orders' })).toBeVisible();
  const overviewCards = page.locator('.overview-card');
  await expect(overviewCards).toHaveCount(4);
  await expect(overviewCards.nth(0)).toContainText('Total orders');
  await expect(overviewCards.nth(0)).toContainText('2');
  await expect(overviewCards.nth(1)).toContainText('Filled orders');
  await expect(overviewCards.nth(1)).toContainText('1');
  await expect(overviewCards.nth(1)).toContainText('50.0% completion rate');
  await expect(overviewCards.nth(2)).toContainText('Open orders');
  await expect(overviewCards.nth(2)).toContainText('1');
  await expect(overviewCards.nth(3)).toContainText('Canceled orders');
  await expect(overviewCards.nth(3)).toContainText('0');
  const totalValue = page.locator('.total-value');
  await expect(totalValue).toBeVisible();
  await expect(totalValue).toContainText('Total price');
  await expect(totalValue).toContainText('$2,723.00');
  await expect(page.getByRole('columnheader', { name: 'Order / Date' })).toBeVisible();
  await expect(page.getByRole('columnheader', { name: 'Quantity' })).toBeVisible();
  await expect(page.getByRole('columnheader', { name: 'Price' })).toBeVisible();
  await expect(page.getByRole('columnheader', { name: 'Execution price' })).toBeVisible();
  await expect(page.getByRole('columnheader', { name: 'Total price' })).toBeVisible();
  await expect(page.getByText('AAPL')).toBeVisible();
  await expect(page.getByText('TSLA')).toBeVisible();
  await expect(page.getByText('$1,497.50')).toBeVisible();
  await expect(page.getByText('$149.75')).toBeVisible();
  await expect(page.getByText('$1,225.50')).toBeVisible();
  await expect(page.getByText('$2,723.00')).toBeVisible();
  const orderTable = page.getByRole('table');
  await expect(orderTable.getByText('FILLED', { exact: true })).toBeVisible();
  await expect(orderTable.getByText('NEW', { exact: true })).toBeVisible();
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