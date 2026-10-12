# Architecture rebuild

The reference is the local `lost-and-found-java` project. Its application structure is
adapted to the existing subscription domain; its lost-property features and infrastructure
are not dependencies of this service.

| Reference pattern | Subscription implementation |
| --- | --- |
| Thin controllers and service interfaces | All six controllers delegate to transactional service implementations |
| Shared `ApiResponse` / `PageResponse` | `common` owns success, failure and pagination contracts |
| Global exception advice | One HTTP error path for validation, business rules and security failures |
| JWT filter and persisted account checks | Signed claims plus current user, role and tenant status |
| `logging.RequestLog` | Generated response request ID, timing and guaranteed MDC cleanup |
| Profile-specific YAML | Base + dev/docker/prod; production requires external secrets |
| PostgreSQL/Flyway with DTO mapping | Existing migrations and MapStruct billing aggregates retained |
| Container-backed HTTP tests | Failsafe exercises real PostgreSQL and Kafka on random ports |

## Compatibility

All `/api/v1` routes and success HTTP statuses remain. Successful JSON payloads move under
`data`. Errors change from RFC 9457 ProblemDetail to `success=false`, a readable `message`,
and `data` containing `errorCode`, `path`, `timestamp`, optional `fieldErrors`/`errorId`.
This is a breaking wire-format change and requires updating API clients together with deployment.
The shell smoke test has been migrated. Generated OpenAPI describes controller envelopes.

JWTs issued before the migration remain usable if they are valid and their account/tenant
is active. Current database roles replace token-carried roles for authorization. Invalid
bearer tokens fail with 401 before controllers run; login/registration authenticate from
their request body. JWT errors cannot retry downstream requests. The application adds
one user and one tenant lookup per bearer-authenticated request.

Browser origins must be explicitly configured. Compose requires a JWT key in the environment
or `.env`; `.env` is excluded from Git and Docker build context. Dev credentials exist only
in the default dev profile. Existing V1/V2 migrations are unchanged, and automatic Flyway
baselining is disabled to avoid adopting an unknown nonempty schema silently.

## Verification and maintenance

Run `mvnw test` for fast regression tests and `mvnw clean verify` for the complete suite.
Failsafe's `*IT` tests launch isolated PostgreSQL/Kafka containers, migrate an empty schema,
start a real HTTP server, and exercise the public and protected API. Missing Docker is a
failure, not a skipped test. The tests do not start or modify the user's Compose services.

Kafka is pinned to 3.9.1 in Compose and integration tests. The 3.9.0 image failed startup
under Testcontainers with a nonroutable advertised-listener error during this rebuild.
Upstream reports: https://github.com/testcontainers/testcontainers-java/issues/9506

Use focused Conventional Commits: `deps` for dependencies, `feat` for behavior, `fix` for
regressions, `test` for coverage, `chore` for tooling/runtime configuration, and `docs` for
written documentation. The rebuild uses the repository's configured author and committer.
