-- Merchant catalogs and customer billing are separate from platform-account billing.
CREATE TABLE merchant_products (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenants(id), name varchar(120) NOT NULL,
 description varchar(500), active boolean NOT NULL DEFAULT true,
 created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL, version bigint NOT NULL DEFAULT 0,
 UNIQUE(id, tenant_id)
);
CREATE TABLE merchant_prices (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenants(id), product_id uuid NOT NULL,
 name varchar(120) NOT NULL, amount numeric(19,4) NOT NULL CHECK(amount >= 0), currency varchar(3) NOT NULL,
 billing_cycle varchar(20) NOT NULL CHECK(billing_cycle IN ('MONTHLY','QUARTERLY','YEARLY')),
 trial_days integer NOT NULL CHECK(trial_days BETWEEN 0 AND 365), active boolean NOT NULL DEFAULT true,
 features jsonb NOT NULL DEFAULT '[]', metered_rates jsonb NOT NULL DEFAULT '[]',
 created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL, version bigint NOT NULL DEFAULT 0,
 UNIQUE(id, tenant_id), FOREIGN KEY(product_id,tenant_id) REFERENCES merchant_products(id,tenant_id)
);
CREATE TABLE merchant_customers (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenants(id), external_id varchar(120) NOT NULL,
 name varchar(150) NOT NULL, email varchar(255) NOT NULL, active boolean NOT NULL DEFAULT true,
 created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL, version bigint NOT NULL DEFAULT 0,
 UNIQUE(id,tenant_id), UNIQUE(tenant_id,external_id)
);
CREATE TABLE customer_subscriptions (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenants(id), customer_id uuid NOT NULL,
 product_id uuid NOT NULL, price_id uuid NOT NULL, quantity integer NOT NULL CHECK(quantity BETWEEN 1 AND 100000),
 status varchar(20) NOT NULL CHECK(status IN ('ACTIVE','TRIALING','PAST_DUE','CANCELED')),
 period_start timestamptz NOT NULL, period_end timestamptz NOT NULL CHECK(period_end > period_start),
 cancel_at_period_end boolean NOT NULL DEFAULT false,
 idempotency_key varchar(120) NOT NULL, skip_trial boolean NOT NULL DEFAULT false,
 created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL, version bigint NOT NULL DEFAULT 0,
 UNIQUE(id,tenant_id), UNIQUE(tenant_id,idempotency_key),
 FOREIGN KEY(customer_id,tenant_id) REFERENCES merchant_customers(id,tenant_id),
 FOREIGN KEY(product_id,tenant_id) REFERENCES merchant_products(id,tenant_id),
 FOREIGN KEY(price_id,tenant_id) REFERENCES merchant_prices(id,tenant_id)
);
CREATE UNIQUE INDEX uk_customer_live_product ON customer_subscriptions(tenant_id,customer_id,product_id)
 WHERE status IN ('ACTIVE','TRIALING','PAST_DUE');
CREATE INDEX ix_customer_subscriptions_due ON customer_subscriptions(period_end) WHERE status IN ('ACTIVE','TRIALING');
CREATE TABLE customer_invoices (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenants(id), subscription_id uuid NOT NULL,
 invoice_number varchar(80) NOT NULL UNIQUE, currency varchar(3) NOT NULL, kind varchar(10) NOT NULL CHECK(kind IN ('BASE','USAGE')),
 base_amount numeric(19,4) NOT NULL, usage_amount numeric(19,4) NOT NULL DEFAULT 0,
 total numeric(19,4) NOT NULL CHECK(total >= 0), amount_paid numeric(19,4) NOT NULL DEFAULT 0,
 status varchar(20) NOT NULL CHECK(status IN ('OPEN','PAID','VOID')),
 period_start timestamptz NOT NULL, period_end timestamptz NOT NULL, due_at timestamptz NOT NULL, paid_at timestamptz,
 created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL, version bigint NOT NULL DEFAULT 0,
 FOREIGN KEY(subscription_id,tenant_id) REFERENCES customer_subscriptions(id,tenant_id),
 UNIQUE(tenant_id,subscription_id,period_start,kind)
);
CREATE TABLE customer_usage_events (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenants(id), subscription_id uuid NOT NULL,
 metric varchar(60) NOT NULL, quantity numeric(19,4) NOT NULL CHECK(quantity >= 0),
 occurred_at timestamptz NOT NULL, idempotency_key varchar(120) NOT NULL,
 created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL, version bigint NOT NULL DEFAULT 0,
 FOREIGN KEY(subscription_id,tenant_id) REFERENCES customer_subscriptions(id,tenant_id),
 UNIQUE(tenant_id,idempotency_key)
);
CREATE INDEX ix_customer_usage_period ON customer_usage_events(tenant_id,subscription_id,occurred_at);
CREATE TABLE merchant_api_keys (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenants(id), name varchar(120) NOT NULL,
 token_hash varchar(64) NOT NULL UNIQUE, prefix varchar(24) NOT NULL, scopes jsonb NOT NULL,
 expires_at timestamptz NOT NULL, revoked_at timestamptz,
 created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL, version bigint NOT NULL DEFAULT 0
);
CREATE INDEX ix_merchant_products_tenant ON merchant_products(tenant_id);
CREATE INDEX ix_merchant_prices_tenant ON merchant_prices(tenant_id);
CREATE INDEX ix_merchant_customers_tenant ON merchant_customers(tenant_id);
CREATE INDEX ix_customer_invoices_tenant ON customer_invoices(tenant_id,created_at DESC);
CREATE INDEX ix_merchant_api_keys_tenant ON merchant_api_keys(tenant_id);
