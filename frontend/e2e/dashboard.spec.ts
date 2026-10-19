import { expect, test, type Page } from '@playwright/test';

const accountId = 'ACC-F92249B8';
const balanceUrl = `**/api/v1/accounts/${accountId}/balance`;
const positionsUrl = `**/api/v1/accounts/${accountId}/positions`;
const ordersUrl = `**/api/v1/accounts/${accountId}/orders`;
const instrumentsUrl = '**/api/v1/instruments**';
const placeOrderUrl = '**/api/v1/orders';

const balance = {
  accountId,
  cashBalance: { amount: '10000.00', currency: 'USD' }
};

const positions = [
  {
    symbol: 'AAPL',
    quantity: 100,
    averageCost: 150,
    currentPrice: 195.5,
    marketValue: 19550,
    unrealizedPnL: 4550,
    unrealizedPnLPercent: 30.3333
  }
];

const orders = [
  {
    orderId: '11111111-1111-1111-1111-111111111111',
    accountId,
    symbol: 'AAPL',
    side: 'BUY',
    quantity: 10,
    price: 150.25,
    status: 'NEW',
    createdOn: '2026-10-12T12:00:00'
  }
];

const instruments = [
  {
    symbol: 'BTC',
    name: 'Bitcoin',
    assetClass: 'CRYPTO',
    currency: 'USD',
    tradable: true
  }
];

async function seedSession(page: Page): Promise<void> {
  await page.addInitScript((session) => {
    localStorage.setItem('auth.accountId', session.accountId);
    sessionStorage.setItem('auth_session', JSON.stringify(session));
  }, {
    accessToken: 'test-access-token',
    refreshToken: 'test-refresh-token',
    expiresAt: Date.now() + 60 * 60 * 1000,
    accountId,
    role: 'USER'
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

async function mockDashboardApi(page: Page): Promise<void> {
  await page.route(instrumentsUrl, (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(instruments)
  }));
  await page.route(balanceUrl, (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(balance)
  }));
  await page.route(positionsUrl, (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(positions)
  }));
  await page.route(ordersUrl, (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(orders)
  }));
}

test('shows portfolio overview and asset details', async ({ page }) => {
  const portfolioOverview = page.getByRole('region', {
    name: 'Portfolio overview'
  });
  await seedSession(page);
  await mockDashboardApi(page);

  await page.goto('/dashboard');

  await expect(page.getByRole('heading', { name: 'Trading overview' })).toBeVisible();
  await expect(portfolioOverview.getByText('$19,550.00')).toBeVisible();
  await expect(portfolioOverview.getByText('Available to trade')).toBeVisible();
  await expect(portfolioOverview.getByText('$10,000.00')).toBeVisible();
  await expect(portfolioOverview.getByText('+$4,550.00')).toHaveCount(2);
  await expect(page.getByText('AAPL')).toBeVisible();
  await expect(page.getByText('$195.50')).toBeVisible();
  await expect(page.getByText('Current price')).toBeVisible();
  await expect(page.getByText('Your assets')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Place order' })).toBeVisible();
});

test('validates and submits an order from the order ticket', async ({ page }) => {
  await seedSession(page);
  await mockDashboardApi(page);

  const submittedOrder = {
    orderId: '33333333-3333-3333-3333-333333333333',
    accountId,
    symbol: 'BTC',
    side: 'BUY',
    quantity: 2,
    price: 50000,
    status: 'NEW',
    createdOn: '2026-10-15T08:00:00'
  };

  const orderRequest = page.waitForRequest(placeOrderUrl);
  await page.route(placeOrderUrl, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(submittedOrder)
    });
  });

  await page.goto('/dashboard');

  const submitButton = page.getByRole('button', { name: 'Submit order' });
  await expect(submitButton).toBeDisabled();

  await page.getByLabel('Asset symbol').fill('btc');
  await expect(page.getByRole('listbox', { name: 'Available instruments' })).toBeVisible();
  await page.getByRole('option', { name: 'BTC' }).click();
  await page.getByLabel('Quantity').fill('2');
  await expect(submitButton).toBeEnabled();
  await submitButton.click();

  const request = await orderRequest;
  expect(request.postDataJSON()).toMatchObject({
    accountId,
    symbol: 'BTC',
    side: 'BUY',
    quantity: 2
  });
  await expect(page.getByRole('status')).toContainText('Order submitted: BUY 2 BTC.');
});

test('shows an error when assets cannot be loaded', async ({ page }) => {
  await seedSession(page);
  await page.route(balanceUrl, (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(balance)
  }));
  await page.route(positionsUrl, (route) => route.fulfill({ status: 500, body: 'error' }));
  await page.route(ordersUrl, (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify([])
  }));

  await page.goto('/dashboard');

  await expect(page.getByRole('alert')).toContainText('Unable to load your assets.');
});
