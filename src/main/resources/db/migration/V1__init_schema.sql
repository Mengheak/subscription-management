
CREATE TABLE tenants
(
    id            UUID         NOT NULL,
    name          VARCHAR(150) NOT NULL,
    slug          VARCHAR(80)  NOT NULL,
    contact_email VARCHAR(255) NOT NULL,
    status        VARCHAR(30)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_tenants PRIMARY KEY (id),
    CONSTRAINT uk_tenants_slug UNIQUE (slug),
    CONSTRAINT ck_tenants_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED'))
);

CREATE TABLE app_users
(
    id            UUID         NOT NULL,
    tenant_id     UUID         NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(120) NOT NULL,
    full_name     VARCHAR(150) NOT NULL,
    role          VARCHAR(40)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_app_users PRIMARY KEY (id),
    CONSTRAINT fk_app_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT uk_app_users_email UNIQUE (email),
    CONSTRAINT ck_app_users_role CHECK (role IN ('TENANT_ADMIN', 'TENANT_MEMBER'))
);

CREATE INDEX ix_app_users_tenant ON app_users (tenant_id);

CREATE TABLE plans
(
    id            UUID           NOT NULL,
    code          VARCHAR(60)    NOT NULL,
    name          VARCHAR(120)   NOT NULL,
    description   VARCHAR(500),
    billing_cycle VARCHAR(20)    NOT NULL,
    base_price    NUMERIC(19, 4) NOT NULL,
    currency      VARCHAR(3)     NOT NULL,
    trial_days    INTEGER        NOT NULL DEFAULT 0,
    active        BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version       BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_plans PRIMARY KEY (id),
    CONSTRAINT uk_plans_code UNIQUE (code),
    CONSTRAINT ck_plans_cycle CHECK (billing_cycle IN ('MONTHLY', 'QUARTERLY', 'YEARLY')),
    CONSTRAINT ck_plans_base_price CHECK (base_price >= 0),
    CONSTRAINT ck_plans_trial_days CHECK (trial_days >= 0)
);

CREATE TABLE plan_metered_rates
(
    id                 UUID           NOT NULL,
    plan_id            UUID           NOT NULL,
    metric             VARCHAR(40)    NOT NULL,
    included_quantity  NUMERIC(19, 4) NOT NULL DEFAULT 0,
    unit_price         NUMERIC(19, 6) NOT NULL,
    CONSTRAINT pk_plan_metered_rates PRIMARY KEY (id),
    CONSTRAINT fk_plan_metered_rates_plan FOREIGN KEY (plan_id) REFERENCES plans (id) ON DELETE CASCADE,
    CONSTRAINT uk_plan_metered_rates UNIQUE (plan_id, metric),
    CONSTRAINT ck_plan_metered_rates_metric CHECK (metric IN ('API_CALLS', 'STORAGE_MB', 'BANDWIDTH_GB', 'SEATS')),
    CONSTRAINT ck_plan_metered_rates_included CHECK (included_quantity >= 0),
    CONSTRAINT ck_plan_metered_rates_price CHECK (unit_price >= 0)
);

CREATE TABLE subscriptions
(
    id                   UUID        NOT NULL,
    tenant_id            UUID        NOT NULL,
    plan_id              UUID        NOT NULL,
    status               VARCHAR(30) NOT NULL,
    quantity             INTEGER     NOT NULL DEFAULT 1,
    started_at           TIMESTAMPTZ NOT NULL,
    current_period_start TIMESTAMPTZ NOT NULL,
    current_period_end   TIMESTAMPTZ NOT NULL,
    trial_end            TIMESTAMPTZ,
    cancel_at_period_end BOOLEAN     NOT NULL DEFAULT FALSE,
    canceled_at          TIMESTAMPTZ,
    ended_at             TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    version              BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_subscriptions PRIMARY KEY (id),
    CONSTRAINT fk_subscriptions_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_subscriptions_plan FOREIGN KEY (plan_id) REFERENCES plans (id),
    CONSTRAINT ck_subscriptions_status CHECK (status IN ('TRIALING', 'ACTIVE', 'PAST_DUE', 'CANCELED', 'EXPIRED')),
    CONSTRAINT ck_subscriptions_quantity CHECK (quantity > 0),
    CONSTRAINT ck_subscriptions_period CHECK (current_period_end > current_period_start)
);

CREATE INDEX ix_subscriptions_tenant ON subscriptions (tenant_id);
CREATE INDEX ix_subscriptions_period_end ON subscriptions (current_period_end) WHERE status IN ('TRIALING', 'ACTIVE');

CREATE UNIQUE INDEX uk_subscriptions_one_live_per_tenant
    ON subscriptions (tenant_id)
    WHERE status IN ('TRIALING', 'ACTIVE', 'PAST_DUE');

CREATE TABLE invoices
(
    id              UUID           NOT NULL,
    tenant_id       UUID           NOT NULL,
    subscription_id UUID           NOT NULL,
    invoice_number  VARCHAR(40)    NOT NULL,
    status          VARCHAR(30)    NOT NULL,
    currency        VARCHAR(3)     NOT NULL,
    subtotal        NUMERIC(19, 4) NOT NULL DEFAULT 0,
    tax_amount      NUMERIC(19, 4) NOT NULL DEFAULT 0,
    total           NUMERIC(19, 4) NOT NULL DEFAULT 0,
    amount_paid     NUMERIC(19, 4) NOT NULL DEFAULT 0,
    period_start    TIMESTAMPTZ    NOT NULL,
    period_end      TIMESTAMPTZ    NOT NULL,
    issued_at       TIMESTAMPTZ,
    due_at          TIMESTAMPTZ,
    paid_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version         BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_invoices PRIMARY KEY (id),
    CONSTRAINT fk_invoices_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_invoices_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (id),
    CONSTRAINT uk_invoices_number UNIQUE (invoice_number),
    CONSTRAINT ck_invoices_status CHECK (status IN ('DRAFT', 'OPEN', 'PAID', 'VOID', 'UNCOLLECTIBLE'))
);

CREATE INDEX ix_invoices_tenant ON invoices (tenant_id);
CREATE INDEX ix_invoices_tenant_status ON invoices (tenant_id, status);

CREATE TABLE invoice_line_items
(
    id           UUID           NOT NULL,
    tenant_id    UUID           NOT NULL,
    invoice_id   UUID           NOT NULL,
    line_type    VARCHAR(30)    NOT NULL,
    description  VARCHAR(255)   NOT NULL,
    metric       VARCHAR(40),
    quantity     NUMERIC(19, 4) NOT NULL,
    unit_price   NUMERIC(19, 6) NOT NULL,
    amount       NUMERIC(19, 4) NOT NULL,
    period_start TIMESTAMPTZ,
    period_end   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version      BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_invoice_line_items PRIMARY KEY (id),
    CONSTRAINT fk_invoice_line_items_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_invoice_line_items_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id) ON DELETE CASCADE,
    CONSTRAINT ck_invoice_line_items_type CHECK (line_type IN
                                                 ('SUBSCRIPTION', 'USAGE', 'PRORATION_CHARGE', 'PRORATION_CREDIT',
                                                  'ADJUSTMENT'))
);

CREATE INDEX ix_invoice_line_items_invoice ON invoice_line_items (invoice_id);

CREATE TABLE usage_events
(
    id              UUID           NOT NULL,
    tenant_id       UUID           NOT NULL,
    subscription_id UUID           NOT NULL,
    metric          VARCHAR(40)    NOT NULL,
    quantity        NUMERIC(19, 4) NOT NULL,
    idempotency_key VARCHAR(120)   NOT NULL,
    source          VARCHAR(60),
    occurred_at     TIMESTAMPTZ    NOT NULL,
    recorded_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version         BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_usage_events PRIMARY KEY (id),
    CONSTRAINT fk_usage_events_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_usage_events_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (id),
    CONSTRAINT uk_usage_events_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT ck_usage_events_metric CHECK (metric IN ('API_CALLS', 'STORAGE_MB', 'BANDWIDTH_GB', 'SEATS')),
    CONSTRAINT ck_usage_events_quantity CHECK (quantity >= 0)
);

CREATE INDEX ix_usage_events_tenant_occurred ON usage_events (tenant_id, occurred_at);
CREATE INDEX ix_usage_events_subscription_metric ON usage_events (subscription_id, metric);

CREATE TABLE usage_counters
(
    id              UUID           NOT NULL,
    tenant_id       UUID           NOT NULL,
    subscription_id UUID           NOT NULL,
    metric          VARCHAR(40)    NOT NULL,
    period_start    TIMESTAMPTZ    NOT NULL,
    period_end      TIMESTAMPTZ    NOT NULL,
    total_quantity  NUMERIC(19, 4) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version         BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_usage_counters PRIMARY KEY (id),
    CONSTRAINT fk_usage_counters_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_usage_counters_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (id),
    CONSTRAINT uk_usage_counters UNIQUE (subscription_id, metric, period_start),
    CONSTRAINT ck_usage_counters_metric CHECK (metric IN ('API_CALLS', 'STORAGE_MB', 'BANDWIDTH_GB', 'SEATS')),
    CONSTRAINT ck_usage_counters_quantity CHECK (total_quantity >= 0)
);

CREATE INDEX ix_usage_counters_tenant ON usage_counters (tenant_id);

CREATE SEQUENCE invoice_number_seq START WITH 1 INCREMENT BY 1;
