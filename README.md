# Subscription Manager

A Java 17 / Spring Boot 3.5 backend for multi-tenant SaaS billing, rebuilt around the
layered architecture used by `lost-and-found-java`. PostgreSQL owns durable state,
Flyway owns the schema, MapStruct maps read models, and Kafka carries metered usage.

## Architecture

`controller -> service interface -> service.impl -> repository -> entity`

Controllers validate input and return `common.ApiResponse<T>`. Services own transactions
and billing decisions. Repositories enforce tenant-scoped lookups. DTOs are immutable
records, and MapStruct never exposes persistence entities through the API.

| Package | Responsibility |
| --- | --- |
| `common` | API envelope, pagination and global exception advice |
| `common.exception`, `common.enums`, `common.constant` | Domain failures and shared vocabulary |
| `controller`, `dto`, `mapper` | HTTP contracts, validation and response mapping |
| `service`, `service.impl`, `service.billing` | Use cases and money/period calculations |
| `repository`, `entity` | PostgreSQL persistence and tenant ownership |
| `security` | JWT validation, current-account checks and tenant context |
| `messaging` | Kafka acknowledgement, consumption, retry and dead-letter handling |
| `logging` | Request IDs, response status and duration |
| `config`, `config.properties` | Wiring and validated environment configuration |

See [architecture migration](docs/ARCHITECTURE_REBUILD.md) for the reference mapping
and compatibility changes, and [system design](docs/SYSTEM_DESIGN.md) for billing flows.

## Run locally

Requirements: JDK 17+, Docker with Linux containers, and internet access for initial
Maven dependencies/container images. Maven is provided by the wrapper.

1. Copy `.env.example` to `.env`.
2. Set `JWT_SECRET` to a fresh base64-encoded key of at least 32 random bytes
   (for example, `openssl rand -base64 48`).
3. Run `docker compose up --build`.

The API is at `http://localhost:8080/api/v1`, Swagger at
`http://localhost:8080/swagger-ui.html`, and health at
`http://localhost:8080/actuator/health`. Set `SERVER_PORT` or `POSTGRES_PORT` in `.env`
when those host ports are occupied. Kafka is available on `localhost:29092` for host
clients and `kafka:9092` inside Compose.

For an IDE/host process, start dependencies with `docker compose up -d postgres kafka`,
then run `./mvnw spring-boot:run` (Windows: `.\mvnw.cmd spring-boot:run`). The default
`dev` profile supplies local development credentials and a development-only JWT key.
Spring Boot does not automatically load Compose's `.env`; export overrides in the
shell or configure them in the IDE, especially if you changed the database password/port.

Never edit previously applied Flyway migrations. Add the next versioned migration.
If an existing database has checksum drift, inspect its migration history and restore
the original scripts or apply a reviewed database migration; do not delete its volume.

## Profiles and configuration

| Profile | Purpose |
| --- | --- |
| `dev` (default) | Local PostgreSQL, host Kafka port 29092, development JWT key |
| `docker` | Compose hostnames; DB credentials and JWT key supplied by Compose |
| `prod` | Required external credentials, explicit CORS origins, Swagger disabled |

For production set `SPRING_PROFILES_ACTIVE=prod`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`,
`KAFKA_BOOTSTRAP_SERVERS`, `JWT_SECRET`, and `CORS_ALLOWED_ORIGINS` (comma-separated exact
origins). The base configuration has no database or JWT credential fallback. Do not
combine `dev` with `prod`. The bundled Compose stack is for local development.

Custom settings use validated `@ConfigurationProperties` records: `app.security`,
`app.cors`, `app.billing`, and `app.kafka`. Billing defaults are USD, two money decimals,
HALF_UP rounding, 14-day invoice terms and zero tax. Browser origins default to
`http://localhost:3000` and `http://localhost:5173` outside production.

## API contract

All application endpoints return `application/json` with this envelope:

```json
{"success": true, "message": "Success", "data": {"accessToken": "..."}}
```

Failures keep the meaningful HTTP status and use the same envelope:

```json
{"success": false, "message": "One or more fields are invalid", "data": {
  "errorCode": "VALIDATION_FAILED", "fieldErrors": {"planCode": "must not be blank"},
  "path": "/api/v1/subscriptions", "timestamp": "2026-10-09T00:00:00Z"
}}
```

Clients upgrading from the original API must read payloads under `data`, including
`data.content` for paginated results and `data.errorCode` for failures. The previous
ProblemDetail error format is replaced. HTTP statuses and endpoint paths are retained.
Actuator, OpenAPI documents, and framework-level CORS rejections retain their native formats.
Responses carry an `X-Request-ID`; logs record that ID, method, path, status and duration
without logging request bodies, passwords or bearer tokens.

| Endpoint | Access / purpose |
| --- | --- |
| `POST /auth/register` | Public; create tenant and first administrator (201) |
| `POST /auth/login` | Public; exchange credentials for a JWT |
| `GET /plans`, `GET /plans/{code}` | Public catalogue |
| `GET /tenants/me` | Current tenant profile |
| `POST /subscriptions` | Tenant admin; create subscription (201) |
| `GET /subscriptions`, `GET /subscriptions/{id}` | Current tenant's subscriptions |
| `POST /subscriptions/{id}/change-plan` | Tenant admin; prorated plan/quantity change |
| `POST /subscriptions/{id}/cancel` | Tenant admin; immediate or scheduled cancellation |
| `POST /usage/events` | Authenticated; accepts usage after Kafka acknowledgement (202) |
| `GET /usage/subscriptions/{id}/summary` | Current-period usage and estimated overage |
| `POST /invoices/generate?subscriptionId={id}` | Tenant admin; invoice current period (201) |
| `GET /invoices`, `GET /invoices/{id}` | Current tenant's invoices |
| `POST /invoices/{id}/pay` | Tenant admin; record settlement |

Paths in the table are relative to `/api/v1`. Send `Authorization: Bearer <token>`.
Registration requires `tenantName`, `slug`, `adminEmail`, `adminFullName`, `adminPassword`.
Subscribe with `{"planCode":"PRO","quantity":2,"skipTrial":true}`. Report usage with
`{"metric":"API_CALLS","quantity":612500,"idempotencyKey":"unique-event-key"}`.

## Security and billing invariants

JWT signatures, issuer and expiry are verified before loading the current user by both
user ID and tenant ID. Every authenticated request checks enabled status, current role
and active tenant status. Role changes and account suspension therefore apply on the
next request. Registration/login use their supplied credentials without inheriting a
bearer token's tenant. Individual token logout/revocation is not implemented.

Tenant identity comes only from authentication. Explicit tenant predicates protect
primary-key reads; a Hibernate transaction filter adds defense for queries, and
immutable tenant IDs plus composite foreign keys protect writes. Cross-tenant resource
requests return 404. Thread-local tenant/security state is cleared after every request.

All money uses `BigDecimal`. Mid-period changes issue separate credit and charge lines,
retain period boundaries, and skip proration during trials. Kafka records are keyed by
subscription, duplicates are constrained per tenant/idempotency key, and consumption
updates the ledger and counter in one transaction. A 202 means broker acknowledgement,
not completed consumption. Poison events are routed to the configured dead-letter topic.

## Verification

```bash
./mvnw test          # Unit/regression tests; no containers
./mvnw clean verify  # Unit tests plus real PostgreSQL/Kafka integration tests
bash smoke-test.sh  # Additional check against the running local stack
```

Use `mvnw.cmd` in Windows PowerShell. `verify` requires Docker and fails when Docker is
unavailable, so CI cannot silently skip integration coverage. Tests allocate random
ports and temporary containers; they do not use existing application databases.
The GitHub Actions workflow runs the same `clean verify` command with Java 17.

Integration coverage includes registration/login, current-account authorization, CORS,
validation/error contracts, OpenAPI, tenant isolation, Kafka delivery and duplicate
suppression, invoicing, settlement, proration and cancellation. Unit tests cover token
handling, request-filter cleanup, billing periods and money arithmetic.

## Scope boundaries

Payment settlement records state; no payment gateway is integrated. There is no automatic
renewal scheduler, and scheduled cancellation requires a future lifecycle worker to take
effect at the boundary. Immediate cancellation does not issue a credit note. Storage and
seats are additive metrics, and included allowances are flat per subscription. Refresh
tokens, password recovery, Redis, RabbitMQ, object storage and chat from the reference
project are outside this billing architecture migration.
