# Company integration: from platform plan to your first customer

The complete merchant flow is implemented in the dashboard and `/api/v1/merchant` API.
Two separate subscriptions exist: your company's subscription to this platform, and
subscriptions your customers buy from your company. Their catalogs, invoices, and usage
are separate.

## Use the dashboard

1. Register/sign in at http://localhost:3000. Open **Plans** and subscribe your company
   to a platform plan. Active plans and unexpired trials unlock merchant tools.
2. Open **Get started** to track all five setup steps.
3. Open **Products**. Create a product, then add an immutable price: recurring amount,
   currency, monthly/quarterly/yearly cycle, optional trial, feature keys such as
   `exports`, and optional metered rules such as `API_CALLS`, 100 included units,
   and USD 0.10 per extra unit. Multiple metered rules are supported.
4. Open **Integration → Create API key**. Select scopes and an expiry (1–365 days).
   Save the full secret immediately: it is shown once and stored only as a SHA-256 hash.
   The key belongs on your website's backend, not in browser code.
5. Open **Customers → Add customer**. Use the account ID from your website as the
   immutable external ID. Create a subscription using that customer and your price.
6. Use **Integration → Test a customer's access** to check a product/feature. Your
   website's backend uses the same access endpoint before serving protected content.
7. Report customer usage from your backend or **Customer subscriptions → Report usage**.
   View totals and estimated overage using **View usage**.
8. Open **Customer invoices** to see base charges and finalized usage charges. Record
   payment only after receiving it externally. This does not charge a card.

List screens are paginated. Form suggestions show records on the current list page;
use the copy-ID actions and paste an ID when selecting a record from another page.

## Interactive API reference

- Java Swagger: http://localhost:8080/swagger-ui/index.html
- Through the client proxy: http://localhost:3000/swagger-ui/index.html
- OpenAPI: http://localhost:8080/v3/api-docs

Swagger supports bearer login for human administration and `X-API-Key` for merchant
backend integration. Use one authentication scheme per request. Do not send an API key
to platform-account routes. OpenAPI/Swagger are disabled by the production profile;
publish a reviewed reference separately if required.

## Merchant API

All paths in this table are relative to `/api/v1/merchant`. Paginated responses use
`data.content`, `page`, `size`, `totalElements`, `totalPages`, `first`, and `last`.

| Method and route | Purpose | API-key scope |
| --- | --- | --- |
| GET `/onboarding` | Setup progress and platform entitlement | Human only |
| GET `/products`, `/products/{id}` | Read your catalog | CATALOG_READ |
| POST `/products`, `/products/{id}/archive` | Create/archive products | Human admin only |
| GET `/prices` | Read your immutable prices | CATALOG_READ |
| POST `/prices`, `/prices/{id}/archive` | Create/archive prices | Human admin only |
| GET `/customers`, `/customers/{id}` | Read your customers | CUSTOMERS_READ |
| GET `/customers/lookup?externalId=...` | Find your website's customer | CUSTOMERS_READ |
| POST `/customers` | Create a customer; same external ID/payload is retry-safe | CUSTOMERS_WRITE |
| PUT `/customers/{id}` | Update name/email; activate/deactivate | CUSTOMERS_WRITE |
| GET `/subscriptions?customerId=...` | List customer subscriptions; optional customer filter | SUBSCRIPTIONS_READ |
| GET `/subscriptions/{id}` | Read one customer subscription | SUBSCRIPTIONS_READ |
| POST `/subscriptions` | Subscribe a customer to a price | SUBSCRIPTIONS_WRITE |
| POST `/subscriptions/{id}/cancel` | Immediate or period-end cancellation | SUBSCRIPTIONS_WRITE |
| GET `/access?customerExternalId=...&productId=...&feature=...` | Check access; feature is optional | ACCESS_READ |
| POST `/usage/events` | Persist customer usage synchronously | USAGE_WRITE |
| GET `/subscriptions/{id}/usage` | Current-period usage and overage | SUBSCRIPTIONS_READ |
| GET `/invoices?customerId=...`, `/invoices/{id}` | Retrieve customer invoices; optional customer filter | INVOICES_READ |
| POST `/invoices/{id}/pay` | Record full manual settlement | Human admin only |
| GET/POST `/api-keys`, DELETE `/api-keys/{id}` | List/create/revoke backend credentials | Human admin only |

Members can read merchant records and test access. Administrators can perform merchant
writes. Keys receive only their explicit scopes, and cannot create more keys, edit
prices/products, or mark invoices paid. Listing/revoking keys remains available to
administrators after their platform subscription lapses. Other merchant operations
require an active platform account. All lookups enforce tenant ownership; foreign IDs
return 404. Inactive, expired, or revoked keys return 401.

## Backend integration example

Configure your application's server environment:

```dotenv
BILLING_API_URL=http://localhost:8080
BILLING_API_KEY=the-one-time-secret-from-Integration
BILLING_PRODUCT_ID=your-product-uuid
BILLING_PRICE_ID=your-price-uuid
```

Do not prefix these secrets with `NEXT_PUBLIC_`. See the runnable server-side helper
in [examples/merchant-client.mjs](../examples/merchant-client.mjs).

```javascript
import { MerchantClient } from './merchant-client.mjs';
const billing = new MerchantClient();

// Derive accountId from your authenticated user, never from an untrusted request body.
const customer = await billing.createCustomer({
  externalId: account.id,
  name: account.name,
  email: account.billingEmail,
});
const subscription = await billing.subscribe({
  customerId: customer.id,
  priceId: process.env.BILLING_PRICE_ID,
  quantity: 1,
  skipTrial: false,
  idempotencyKey: `signup:${signupEvent.id}`,
});

const access = await billing.access({
  customerExternalId: account.id,
  productId: process.env.BILLING_PRODUCT_ID,
  feature: 'exports',
});
if (!access.allowed) {
  // Your server returns its own upgrade-required/permission-denied response.
  throw new Error('An active subscription with exports is required');
}

await billing.recordUsage({
  subscriptionId: subscription.id,
  metric: 'API_CALLS',
  quantity: 150,
  idempotencyKey: `usage:${durableUsageBatch.id}`,
});
const invoicePage = await billing.invoices(customer.id);
```

Keep integration credentials in a server secret manager. Restrict each key to the
operations your backend actually uses. Verify the signed-in user owns the external
customer account before checking access or returning invoices. The billing API isolates
merchants; your backend is responsible for isolating customers within your own website.

Persist event keys and payloads before delivery. Retry an uncertain result with the same
key and payload. Subscription and usage keys are unique per merchant; mismatched
payload reuse returns 409. Merchant usage is synchronous and timestamps are assigned by
the server, so these endpoints differ from the existing platform Kafka usage endpoint.
Missing/unknown metrics are rejected. The API never trusts client-reported totals.

## Access and billing behavior

- Prices are immutable. Archive and create a new price to change future purchases.
  Archiving blocks new sales but does not remove existing customer entitlements.
- Each customer can hold one live subscription per product and subscribe to multiple
  different products. Inactive customers cannot start subscriptions or access features.
- Active subscriptions and trials grant product access until their period end. A feature
  is granted only when its key appears in the selected price. Unknown features are denied.
  Overdue open invoices deny access even before a background status update.
- Base fees are invoiced upfront per quantity. A free trial issues no base invoice until
  it converts. Zero-total invoices are automatically settled.
- Usage allowances are flat per subscription. Overage is calculated as
  `max(total - included, 0) × extra-unit price`, rounded to two decimals per metric.
  Usage charges are issued in a separate invoice at period close or immediate cancellation.
  Trial usage is free. No card is charged automatically.
- A durable database-backed lifecycle pass runs every 60 seconds (configurable with
  `app.merchant.lifecycle-delay`). It converts trials, issues recurring base invoices,
  finalizes overage, and executes scheduled cancellation. Per-tenant database locks and
  unique period/kind invoice constraints protect against duplicate worker execution.
- Overdue unpaid invoices prevent renewal and access. After manual settlement, eligible
  subscriptions resume and catch up on a subsequent lifecycle pass. Cancellation is final;
  start a new subscription to resume a canceled product. Immediate cancellation does not
  refund prepaid base fees.
- The worker processes one period per subscription per pass, allowing catch-up after
  downtime. It skips companies without an active platform subscription. At a period
  boundary access can be denied briefly until the next pass completes.
- The existing **platform-account** subscription lifecycle remains separate: this change
  does not add payment capture or automated renewal to that original billing model.

## Operational scope

This completes the requested five-step merchant API/dashboard flow using manual invoice
settlement. Hosted checkout, card processing, taxes, refunds, customer self-service portal,
webhook delivery, and mid-subscription price changes are not implemented. Use server-side
access checks for current entitlement state; no webhook synchronization is implied.

## Verification

`mvn verify` runs unit tests and isolated PostgreSQL/Kafka HTTP tests, including merchant
plan gating, key permissions, key revocation, tenant isolation, price/customer/subscription
creation, feature checks, usage deduplication, invoices, trial conversion, renewal,
overdue access, and scheduled cancellation.

The Next.js project includes a real API browser journey. Start the Java API and allow
`http://localhost:3100` in CORS, build the client, then run in PowerShell:

```powershell
$env:LIVE_API_TESTS='1'
npm run test:e2e
```

The test creates a separate `merchant-test-*` workspace, executes the complete UI and
backend-key workflow, and revokes its test API key. It does not delete or modify existing
company data. Without the opt-in variable, the live test is skipped and the six existing
contract-fixture UI tests remain self-contained.
