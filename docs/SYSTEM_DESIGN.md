> Architecture update: see [ARCHITECTURE_REBUILD.md](ARCHITECTURE_REBUILD.md) for the shared API envelope, current-account authentication, profiles, and container tests.

# System Design — Multi-Tenant SaaS Billing & Subscription Engine

Complete structural and behavioural documentation: UML models, data-flow diagrams, and a
step-by-step narration of every path through the system.

All diagrams are Mermaid and render natively on GitHub/GitLab. In an IDE, use the
Mermaid preview plugin.

---

## Table of contents

1. [Notation legend](#1-notation-legend)
2. [System context — DFD Level 0](#2-system-context--dfd-level-0)
3. [Deployment view](#3-deployment-view)
4. [UML — package structure](#4-uml--package-structure)
5. [UML — domain class diagram](#5-uml--domain-class-diagram)
6. [UML — application layer class diagram](#6-uml--application-layer-class-diagram)
7. [UML — security and multi-tenancy class diagram](#7-uml--security-and-multi-tenancy-class-diagram)
8. [UML — messaging class diagram](#8-uml--messaging-class-diagram)
9. [Entity-relationship model](#9-entity-relationship-model)
10. [DFD Level 1 — the whole system](#10-dfd-level-1--the-whole-system)
11. [DFD Level 2.1 — tenant onboarding](#111-dfd-level-21--tenant-onboarding)
12. [DFD Level 2.2 — authentication and request admission](#112-dfd-level-22--authentication-and-request-admission)
13. [DFD Level 2.3 — subscription lifecycle](#113-dfd-level-23--subscription-lifecycle)
14. [DFD Level 2.4 — usage capture](#114-dfd-level-24--usage-capture-producer-side)
15. [DFD Level 2.5 — usage ingestion](#115-dfd-level-25--usage-ingestion-consumer-side)
16. [DFD Level 2.6 — invoicing](#116-dfd-level-26--invoicing)
17. [Sequence diagrams](#12-sequence-diagrams)
18. [State machines](#13-state-machines)
19. [Multi-tenant isolation walkthrough](#14-multi-tenant-isolation-walkthrough)
20. [Application startup sequence](#15-application-startup-sequence)
21. [Error handling flow](#16-error-handling-flow)
22. [Data dictionary](#17-data-dictionary)

---

## 1. Notation legend

### Data-flow diagrams

Mermaid has no native DFD type, so Yourdon–DeMarco elements are mapped onto flowchart shapes:

| DFD element | Meaning | Shape used here |
|---|---|---|
| **External entity** | Actor or system outside the boundary; source/sink of data | `[Rectangle]` |
| **Process** | A transformation of data, numbered `n.m` | `((Circle))` |
| **Data store** | Persistent or durable data at rest, labelled `Dn` | `[(Cylinder)]` |
| **Data flow** | Movement of data, labelled with what moves | `-->` with label |
| **Trust boundary** | Subgraph enclosing the application | `subgraph` |

Every process is numbered. `1.0` denotes a Level-1 process; `1.1`, `1.2` … denote its
Level-2 decomposition.

### UML

Standard UML 2.5 notation. In class diagrams:

- `<|--` generalisation (extends) · `<|..` realisation (implements)
- `*--` composition · `o--` aggregation · `-->` directed association · `..>` dependency
- `<<interface>>`, `<<record>>`, `<<enumeration>>`, `<<abstract>>` stereotypes
- Multiplicities on association ends: `"1"`, `"0..1"`, `"0..*"`

---

## 2. System context — DFD Level 0

The single-process view of the entire engine and everything it talks to.

```mermaid
flowchart TB
    subgraph EXT_LEFT [" "]
        direction TB
        TA["Tenant Administrator<br/>(human)"]
        TM["Tenant Member<br/>(human)"]
        CS["Customer System<br/>(API gateway, metering agent)"]
    end

    P0((("0.0<br/>Billing &amp;<br/>Subscription<br/>Engine")))

    subgraph EXT_RIGHT [" "]
        direction TB
        OPS["Platform Operator<br/>(health, metrics, DLT)"]
    end

    DB[("PostgreSQL<br/>billing schema")]
    MQ[("Apache Kafka<br/>billing.usage-events.v1")]

    TA -->|"registration, credentials,<br/>subscription commands,<br/>invoice actions"| P0
    P0 -->|"access token, subscription state,<br/>invoices, API envelope errors"| TA

    TM -->|"credentials, usage reports,<br/>read queries"| P0
    P0 -->|"usage summary, plan catalogue"| TM

    CS -->|"metered usage events<br/>(metric, quantity, occurredAt, idempotencyKey)"| P0
    P0 -->|"202 Accepted<br/>(topic, partition, offset)"| CS

    P0 <-->|"tenant-scoped SQL<br/>(reads and writes)"| DB
    P0 <-->|"publish / consume<br/>usage events, DLT"| MQ

    P0 -->|"health, readiness, metrics"| OPS
    OPS -->|"configuration, deployment"| P0

    style P0 fill:#e8f0fe,stroke:#1a56db,stroke-width:2px
    style EXT_LEFT fill:none,stroke:none
    style EXT_RIGHT fill:none,stroke:none
```

### Context boundary — what is in and what is out

| Actor / system | Role | Inside boundary? |
|---|---|---|
| Tenant Administrator | Manages subscriptions, invoices; full control of own tenant | No |
| Tenant Member | Reports usage, reads own tenant's data | No |
| Customer System | Machine client reporting metered consumption | No |
| PostgreSQL | System of record for all billing state | Yes (owned) |
| Apache Kafka | Durable buffer for the usage pipeline | Yes (owned) |
| Payment gateway | **Not integrated.** `markPaid` records state only | Out of scope |
| Renewal scheduler | **Not wired.** Repository support exists | Out of scope |

---

## 3. Deployment view

```mermaid
flowchart TB
    subgraph HOST["Docker host — network: billing-net"]
        subgraph APP["billing-app :8080"]
            direction TB
            A1["Spring Boot 3.5<br/>JRE 17 (Alpine, non-root)"]
            A2["Tomcat<br/>HTTP + Security filter chain"]
            A3["Kafka listener container<br/>concurrency = 3"]
            A4["HikariCP pool<br/>max 20 connections"]
        end

        subgraph PG["billing-postgres :5432"]
            direction TB
            P1["PostgreSQL 16-alpine"]
            P2["Flyway-managed schema<br/>V1 + V2"]
            P3["volume: postgres-data"]
        end

        subgraph KF["billing-kafka :9092 / :29092"]
            direction TB
            K1["Apache Kafka 3.9<br/>KRaft — no ZooKeeper"]
            K2["broker + controller<br/>node id 1"]
            K3["volume: kafka-data"]
        end
    end

    CLIENT["HTTP client"] -->|":8080"| APP
    HOSTTOOL["Host tooling"] -->|":29092 PLAINTEXT_HOST"| KF

    APP -->|"JDBC — postgres:5432"| PG
    APP -->|"PLAINTEXT — kafka:9092"| KF

    APP -.->|"depends_on: service_healthy"| PG
    APP -.->|"depends_on: service_healthy"| KF
```

**Startup ordering.** `app` waits on both health checks. Without that gate Flyway would race
an unready database, and the listener container would attach to a broker still forming its
KRaft quorum.

---

## 4. UML — package structure

```mermaid
flowchart TB
    CTRL["<b>controller</b><br/>REST adapters<br/>Auth · Tenant · Plan<br/>Subscription · Invoice · Usage"]
    DTO["<b>dto</b><br/>request/response records<br/>+ Bean Validation"]
    MAP["<b>mapper</b><br/>MapStruct<br/>entity → DTO"]
    SVC["<b>service</b><br/>interfaces + impl<br/>+ service.billing"]
    REPO["<b>repository</b><br/>Spring Data JPA"]
    ENT["<b>entity</b><br/>JPA aggregates"]
    SEC["<b>security</b><br/>JWT · TenantContext<br/>· TenantFilterAspect"]
    MSG["<b>messaging</b><br/>producer · consumer<br/>· event contract"]
    CFG["<b>config</b><br/>@ConfigurationProperties<br/>Kafka · JPA · Security"]
    COM["<b>common</b><br/>enums · constants<br/>· exceptions"]

    CTRL --> SVC
    CTRL --> DTO
    SVC --> REPO
    SVC --> MAP
    SVC --> MSG
    SVC --> DTO
    REPO --> ENT
    MAP --> ENT
    MAP --> DTO
    MSG --> SVC
    SEC -.-> ENT
    CFG -.-> SEC
    CFG -.-> MSG

    CTRL -.-> COM
    SVC -.-> COM
    ENT -.-> COM
    SEC -.-> COM

    style CTRL fill:#e3f2fd,stroke:#1565c0
    style SVC fill:#e8f5e9,stroke:#2e7d32
    style REPO fill:#fff3e0,stroke:#ef6c00
    style ENT fill:#fce4ec,stroke:#c2185b
    style SEC fill:#ffebee,stroke:#c62828
    style MSG fill:#f3e5f5,stroke:#7b1fa2
```

**Dependency rule.** Arrows point inward/downward only. `entity` depends on nothing but
`common` and `security` (for the tenant stamp). `controller` never reaches `repository`
directly. `messaging` calls into `service`, never the reverse for ingestion — the producer is
the one exception, and it is injected into `service` as an outbound port.

---

## 5. UML — domain class diagram

```mermaid
classDiagram
    class BaseEntity {
        <<abstract>>
        -UUID id
        -Instant createdAt
        -Instant updatedAt
        -Long version
        +equals(Object) boolean
        +hashCode() int
    }

    class TenantAwareEntity {
        <<abstract>>
        -UUID tenantId
        ~applyTenantOnPersist() void
    }

    class Tenant {
        -String name
        -String slug
        -String contactEmail
        -TenantStatus status
        +isActive() boolean
    }

    class AppUser {
        -String email
        -String passwordHash
        -String fullName
        -UserRole role
        -boolean enabled
    }

    class Plan {
        -String code
        -String name
        -String description
        -BillingCycle billingCycle
        -BigDecimal basePrice
        -String currency
        -int trialDays
        -boolean active
        +rateFor(UsageMetric) Optional~PlanMeteredRate~
        +addMeteredRate(PlanMeteredRate) void
    }

    class PlanMeteredRate {
        -UUID id
        -UsageMetric metric
        -BigDecimal includedQuantity
        -BigDecimal unitPrice
    }

    class Subscription {
        -SubscriptionStatus status
        -int quantity
        -Instant startedAt
        -Instant currentPeriodStart
        -Instant currentPeriodEnd
        -Instant trialEnd
        -boolean cancelAtPeriodEnd
        -Instant canceledAt
        -Instant endedAt
        +isLive() boolean
        +isTrialing(Instant) boolean
    }

    class Invoice {
        -String invoiceNumber
        -InvoiceStatus status
        -String currency
        -BigDecimal subtotal
        -BigDecimal taxAmount
        -BigDecimal total
        -BigDecimal amountPaid
        -Instant periodStart
        -Instant periodEnd
        -Instant issuedAt
        -Instant dueAt
        -Instant paidAt
        +addLineItem(InvoiceLineItem) void
        +amountDue() BigDecimal
    }

    class InvoiceLineItem {
        -InvoiceLineType lineType
        -String description
        -UsageMetric metric
        -BigDecimal quantity
        -BigDecimal unitPrice
        -BigDecimal amount
        -Instant periodStart
        -Instant periodEnd
    }

    class UsageEvent {
        -UsageMetric metric
        -BigDecimal quantity
        -String idempotencyKey
        -String source
        -Instant occurredAt
        -Instant recordedAt
    }

    class UsageCounter {
        -UsageMetric metric
        -Instant periodStart
        -Instant periodEnd
        -BigDecimal totalQuantity
        +increment(BigDecimal) void
    }

    BaseEntity <|-- TenantAwareEntity
    BaseEntity <|-- Tenant
    BaseEntity <|-- Plan

    TenantAwareEntity <|-- AppUser
    TenantAwareEntity <|-- Subscription
    TenantAwareEntity <|-- Invoice
    TenantAwareEntity <|-- InvoiceLineItem
    TenantAwareEntity <|-- UsageEvent
    TenantAwareEntity <|-- UsageCounter

    Plan "1" *-- "0..*" PlanMeteredRate : prices
    Subscription "0..*" --> "1" Plan : subscribes to
    Invoice "0..*" --> "1" Subscription : bills
    Invoice "1" *-- "1..*" InvoiceLineItem : contains
    UsageEvent "0..*" --> "1" Subscription : attributed to
    UsageCounter "0..*" --> "1" Subscription : rolls up

    Tenant "1" <.. "0..*" TenantAwareEntity : tenant_id discriminator
```

### Design notes carried by this model

| Element | Why it is shaped this way |
|---|---|
| `TenantAwareEntity` | Everything tenant-scoped inherits `tenantId` + the `@PrePersist` stamp + the `@FilterDef`. A new entity is isolated by extending one class. |
| `Tenant` extends `BaseEntity`, not `TenantAwareEntity` | The tenant **is** the discriminator; it cannot carry a foreign one. |
| `Plan` extends `BaseEntity` | Plans are a platform-wide catalogue, offered to every tenant. Per-tenant copies would make a price change an N-row migration. |
| `Invoice *-- InvoiceLineItem` | Composition: a line has no existence outside its invoice (`cascade = ALL`, `orphanRemoval = true`). |
| `Plan *-- PlanMeteredRate` | Same composition; rates are a value collection of the plan. |
| `UsageCounter` | A materialised roll-up of `UsageEvent`, so invoicing is O(metrics) instead of O(events). |
| `version` on `BaseEntity` | Plan change and invoice settlement are read-modify-write on the same aggregate; a lost update mis-bills a customer. |

### Enumerations

```mermaid
classDiagram
    class SubscriptionStatus {
        <<enumeration>>
        TRIALING
        ACTIVE
        PAST_DUE
        CANCELED
        EXPIRED
        +isLive() boolean
        +liveStatuses()$ Set
    }
    class InvoiceStatus {
        <<enumeration>>
        DRAFT
        OPEN
        PAID
        VOID
        UNCOLLECTIBLE
        +isTerminal() boolean
    }
    class InvoiceLineType {
        <<enumeration>>
        SUBSCRIPTION
        USAGE
        PRORATION_CHARGE
        PRORATION_CREDIT
        ADJUSTMENT
    }
    class BillingCycle {
        <<enumeration>>
        MONTHLY
        QUARTERLY
        YEARLY
        +months() int
        +advance(Instant) Instant
        +retreat(Instant) Instant
    }
    class UsageMetric {
        <<enumeration>>
        API_CALLS
        STORAGE_MB
        BANDWIDTH_GB
        SEATS
    }
    class TenantStatus {
        <<enumeration>>
        ACTIVE
        SUSPENDED
        CLOSED
    }
    class UserRole {
        <<enumeration>>
        TENANT_ADMIN
        TENANT_MEMBER
        +authority() String
    }
```

---

## 6. UML — application layer class diagram

```mermaid
classDiagram
    class AuthService {
        <<interface>>
        +register(RegisterTenantRequest) AuthResponse
        +login(LoginRequest) AuthResponse
    }
    class SubscriptionService {
        <<interface>>
        +create(CreateSubscriptionRequest) SubscriptionResponse
        +changePlan(UUID, ChangePlanRequest) SubscriptionResponse
        +cancel(UUID, CancelSubscriptionRequest) SubscriptionResponse
        +get(UUID) SubscriptionResponse
        +list(Pageable) PageResponse
    }
    class InvoiceService {
        <<interface>>
        +generateForCurrentPeriod(UUID) InvoiceResponse
        +get(UUID) InvoiceResponse
        +list(InvoiceStatus, Pageable) PageResponse
        +markPaid(UUID) InvoiceResponse
    }
    class UsageService {
        <<interface>>
        +record(RecordUsageRequest) UsageAcceptedResponse
        +currentPeriodSummary(UUID) UsageSummaryResponse
    }
    class UsageIngestionService {
        <<interface>>
        +ingest(UsageEventMessage) void
    }
    class PlanService {
        <<interface>>
        +listActivePlans() List
        +getByCode(String) PlanResponse
    }
    class TenantService {
        <<interface>>
        +currentTenant() TenantResponse
    }

    class AuthServiceImpl
    class SubscriptionServiceImpl
    class InvoiceServiceImpl
    class UsageServiceImpl
    class UsageIngestionServiceImpl
    class PlanServiceImpl
    class TenantServiceImpl

    class ProrationCalculator {
        -MathContext RATIO_CONTEXT$
        -BillingProperties billingProperties
        +calculate(Subscription, Plan, int, Instant) ProrationResult
        -remainingRatio(Instant, Instant, Instant) BigDecimal
    }
    class ProrationResult {
        <<record>>
        +BigDecimal creditAmount
        +BigDecimal chargeAmount
        +BigDecimal netAmount
        +BigDecimal remainingRatio
        +Instant effectiveAt
        +Instant periodEnd
        +isCharge() boolean
        +isZero() boolean
    }
    class InvoiceAssembler {
        +openInvoice(Subscription, Instant, Instant) Invoice
        +addSubscriptionLine(Invoice, Subscription) void
        +addUsageLines(Invoice, Subscription, Collection) void
        +addProrationLines(Invoice, Subscription, Plan, int, ProrationResult) void
        +finalise(Invoice) Invoice
        +nextInvoiceNumber() String
    }
    class BillingPeriodResolver {
        -int MAX_STEPS$
        +resolve(Subscription, Instant) BillingPeriod
    }
    class BillingPeriod {
        <<record>>
        +Instant start
        +Instant end
        +contains(Instant) boolean
    }

    AuthService <|.. AuthServiceImpl
    SubscriptionService <|.. SubscriptionServiceImpl
    InvoiceService <|.. InvoiceServiceImpl
    UsageService <|.. UsageServiceImpl
    UsageIngestionService <|.. UsageIngestionServiceImpl
    PlanService <|.. PlanServiceImpl
    TenantService <|.. TenantServiceImpl

    SubscriptionServiceImpl --> ProrationCalculator
    SubscriptionServiceImpl --> InvoiceAssembler
    InvoiceServiceImpl --> InvoiceAssembler
    UsageIngestionServiceImpl --> BillingPeriodResolver
    ProrationCalculator ..> ProrationResult : creates
    BillingPeriodResolver ..> BillingPeriod : creates
    InvoiceAssembler ..> ProrationResult : consumes
```

**Why `service.billing` exists.** Two independent flows produce invoices — the periodic
billing run and an immediate proration settlement on a plan change. Both must agree on
numbering, rounding, tax and due dates. `InvoiceAssembler` is the single place that knows how
an invoice is put together; duplicating that across two services is how two subtly different
definitions of "total" end up in one system.

### MapStruct mapper hierarchy

```mermaid
classDiagram
    class CentralMapperConfig {
        <<MapperConfig>>
        unmappedTargetPolicy = ERROR
        unmappedSourcePolicy = IGNORE
        injectionStrategy = CONSTRUCTOR
        nullValueIterableMappingStrategy = RETURN_DEFAULT
    }
    class TenantMapper {
        <<interface>>
        +toResponse(Tenant) TenantResponse
    }
    class PlanMapper {
        <<interface>>
        +toResponse(Plan) PlanResponse
        +toResponses(List) List
        +toRateResponse(PlanMeteredRate) MeteredRateResponse
    }
    class SubscriptionMapper {
        <<interface>>
        +toResponse(Subscription) SubscriptionResponse
    }
    class InvoiceMapper {
        <<interface>>
        +toResponse(Invoice) InvoiceResponse
        +toSummaryResponse(Invoice) InvoiceResponse
        +toLineItemResponse(InvoiceLineItem) InvoiceLineItemResponse
    }
    class UsageEventMapper {
        <<interface>>
        +toMessage(RecordUsageRequest, UUID, UUID, Instant, String) UsageEventMessage
        +toAcceptedResponse(UsageEventMessage, String, int, long) UsageAcceptedResponse
    }

    CentralMapperConfig <.. TenantMapper : config
    CentralMapperConfig <.. PlanMapper : config
    CentralMapperConfig <.. SubscriptionMapper : config
    CentralMapperConfig <.. InvoiceMapper : config
    CentralMapperConfig <.. UsageEventMapper : config
```

Mappers are **read-side only**. Entities are constructed explicitly in services, because
building an aggregate involves invariants — period arithmetic, status transitions, tenant
stamping — that a field-by-field mapper cannot express. `unmappedTargetPolicy = ERROR` turns
"someone added a response field and forgot to wire it" into a compile error rather than a
`null` in a billing payload.

`UsageEventMapper.toMessage` takes `tenantId` and `subscriptionId` as **separate parameters**,
not from the request body. That signature is what makes it impossible for a client to report
usage against another tenant.

---

## 7. UML — security and multi-tenancy class diagram

```mermaid
classDiagram
    class TenantContextHolder {
        <<utility>>
        -ThreadLocal~UUID~ CURRENT_TENANT$
        +setTenantId(UUID)$ void
        +getTenantId()$ Optional~UUID~
        +requireTenantId()$ UUID
        +isSet()$ boolean
        +clear()$ void
        +runAs(UUID, Supplier)$ T
    }

    class JwtService {
        -int MINIMUM_KEY_BYTES$
        -SecretKey signingKey
        -Clock clock
        -io.jsonwebtoken.Clock jwtClock
        +issue(AppUserPrincipal) IssuedToken
        +parse(String) AppUserPrincipal
        -resolveRole(Claims) UserRole
        -buildSigningKey(String)$ SecretKey
    }

    class IssuedToken {
        <<record>>
        +String token
        +Instant expiresAt
    }

    class JwtAuthenticationFilter {
        +doFilterInternal(req, res, chain) void
        -extractBearerToken(req) String
    }

    class AppUserPrincipal {
        <<record>>
        +UUID userId
        +UUID tenantId
        +String email
        +String passwordHash
        +UserRole role
        +boolean enabled
        +forAuthentication(AppUser)$ AppUserPrincipal
        +fromToken(UUID, UUID, String, UserRole)$ AppUserPrincipal
        +getAuthorities() Collection
    }

    class AppUserDetailsService {
        +loadUserByUsername(String) UserDetails
    }

    class TenantFilterAspect {
        +int ORDER$ = 100
        -EntityManager entityManager
        +enableTenantFilter(ProceedingJoinPoint) Object
    }

    class SecurityConfig {
        +securityFilterChain(HttpSecurity) SecurityFilterChain
        +jwtFilterServletRegistration(filter) FilterRegistrationBean
        +passwordEncoder() PasswordEncoder
        +authenticationManager(uds, encoder) AuthenticationManager
        +corsConfigurationSource() CorsConfigurationSource
    }

    class RestAuthenticationEntryPoint {
        +commence(req, res, ex) void
    }
    class RestAccessDeniedHandler {
        +handle(req, res, ex) void
    }

    class OncePerRequestFilter {
        <<abstract>>
    }
    class UserDetails {
        <<interface>>
    }
    class UserDetailsService {
        <<interface>>
    }

    OncePerRequestFilter <|-- JwtAuthenticationFilter
    UserDetails <|.. AppUserPrincipal
    UserDetailsService <|.. AppUserDetailsService

    JwtAuthenticationFilter --> JwtService : parse
    JwtAuthenticationFilter --> TenantContextHolder : set / clear
    JwtService ..> IssuedToken : creates
    JwtService ..> AppUserPrincipal : rebuilds
    AppUserDetailsService ..> AppUserPrincipal : creates
    TenantFilterAspect --> TenantContextHolder : reads
    SecurityConfig --> JwtAuthenticationFilter : registers
    SecurityConfig --> RestAuthenticationEntryPoint
    SecurityConfig --> RestAccessDeniedHandler
```

### The three layers of tenant isolation

```mermaid
flowchart LR
    REQ["Request with<br/>Bearer token"] --> L1

    subgraph L1["Layer 1 — Reads"]
        F1["TenantFilterAspect enables<br/>Hibernate @Filter"]
        F2["Every generated SELECT gains<br/>tenant_id = :tenantId"]
        F1 --> F2
    end

    subgraph L2["Layer 2 — Writes"]
        W1["@PrePersist stamps tenantId<br/>from TenantContextHolder"]
        W2["tenant_id is updatable = false<br/>→ no re-parenting"]
        W1 --> W2
    end

    subgraph L3["Layer 3 — Schema"]
        S1["FK tenant_id → tenants(id)"]
        S2["Composite unique constraints<br/>scoped per tenant"]
        S1 --> S2
    end

    L1 --> DBX[("PostgreSQL")]
    L2 --> DBX
    L3 --> DBX

    GAP["Known gap:<br/>filters do NOT apply to findById"]
    MIT["Mitigation: always resolve through<br/>...AndTenantId repository methods"]
    GAP --> MIT
    MIT --> L1

    style L1 fill:#e8f5e9,stroke:#2e7d32
    style L2 fill:#e3f2fd,stroke:#1565c0
    style L3 fill:#fff3e0,stroke:#ef6c00
    style GAP fill:#ffebee,stroke:#c62828
```

**Verified SQL.** With Postgres statement logging on, a subscription lookup emits:

```sql
select s1_0.id from subscriptions s1_0
where s1_0.tenant_id = $1        -- Layer 1: Hibernate @Filter
  and s1_0.tenant_id = $2        -- Layer 2: explicit ...AndTenantId predicate
  and s1_0.status in ($3,$4,$5)
fetch first $6 rows only
```

**Advice ordering is load-bearing.** `@EnableTransactionManagement(order = 0)` keeps the
transaction interceptor *outside* `TenantFilterAspect` (`@Order(100)`). Reversed, the aspect
would enable the filter on a session the transaction then replaces — a silent, total loss of
isolation with no error anywhere.

---

## 8. UML — messaging class diagram

```mermaid
classDiagram
    class UsageEventMessage {
        <<record>>
        +UUID tenantId
        +UUID subscriptionId
        +UsageMetric metric
        +BigDecimal quantity
        +String idempotencyKey
        +String source
        +Instant occurredAt
        +partitionKey() String
    }

    class UsageEventProducer {
        -Duration SEND_TIMEOUT$
        -KafkaTemplate template
        +publish(UsageEventMessage) SendResult
    }

    class PublishFailedException {
        HTTP 503
        USAGE_PUBLISH_FAILED
    }

    class UsageEventConsumer {
        +onUsageEvent(payload, partition, offset) void
        -validate(msg, partition, offset) void
    }

    class NonRetryableUsageEventException

    class KafkaTopicResolver {
        +usageEvents() String
        +usageEventsDeadLetter() String
    }

    class KafkaConfig {
        +usageEventsTopic() NewTopic
        +usageEventsDeadLetterTopic() NewTopic
        +usageEventProducerFactory() ProducerFactory
        +usageEventKafkaTemplate(pf) KafkaTemplate
        +usageEventConsumerFactory() ConsumerFactory
        +usageEventListenerContainerFactory(cf, eh) ListenerContainerFactory
        +usageEventErrorHandler(template) DefaultErrorHandler
        +deadLetterKafkaTemplate() KafkaTemplate
    }

    class KafkaTopicProperties {
        <<record>>
        +Topic usageEvents
        +String deadLetterSuffix
        +int maxDeliveryAttempts
        +Duration retryBackoff
        +usageEventsDeadLetterTopic() String
    }

    UsageEventProducer ..> UsageEventMessage : publishes
    UsageEventProducer ..> PublishFailedException : throws
    UsageEventConsumer ..> UsageEventMessage : consumes
    UsageEventConsumer ..> NonRetryableUsageEventException : throws
    UsageEventConsumer --> UsageIngestionService : delegates
    UsageEventConsumer --> TenantContextHolder : seeds / clears
    KafkaConfig --> KafkaTopicProperties : reads
    KafkaTopicResolver --> KafkaTopicProperties : reads
    UsageEventConsumer ..> KafkaTopicResolver : SpEL topic name
```

**Why the message carries its own `tenantId`.** A consumer thread has no HTTP request and
therefore no inherited `TenantContext`. The payload is the only trustworthy source of tenancy
at that point — and it was written by the authenticated producer side, from a verified claim.

---

## 9. Entity-relationship model

```mermaid
erDiagram
    TENANTS ||--o{ APP_USERS : "employs"
    TENANTS ||--o{ SUBSCRIPTIONS : "holds"
    TENANTS ||--o{ INVOICES : "is billed"
    TENANTS ||--o{ INVOICE_LINE_ITEMS : "scopes"
    TENANTS ||--o{ USAGE_EVENTS : "scopes"
    TENANTS ||--o{ USAGE_COUNTERS : "scopes"

    PLANS ||--o{ PLAN_METERED_RATES : "prices"
    PLANS ||--o{ SUBSCRIPTIONS : "is chosen by"

    SUBSCRIPTIONS ||--o{ INVOICES : "generates"
    SUBSCRIPTIONS ||--o{ USAGE_EVENTS : "accrues"
    SUBSCRIPTIONS ||--o{ USAGE_COUNTERS : "aggregates"

    INVOICES ||--|{ INVOICE_LINE_ITEMS : "itemises"

    TENANTS {
        uuid id PK
        varchar name
        varchar slug UK
        varchar contact_email
        varchar status "ACTIVE SUSPENDED CLOSED"
        timestamptz created_at
        bigint version
    }

    APP_USERS {
        uuid id PK
        uuid tenant_id FK
        varchar email UK "globally unique"
        varchar password_hash "BCrypt cost 12"
        varchar full_name
        varchar role "TENANT_ADMIN TENANT_MEMBER"
        boolean enabled
    }

    PLANS {
        uuid id PK
        varchar code UK
        varchar name
        varchar billing_cycle "MONTHLY QUARTERLY YEARLY"
        numeric base_price "19 scale 4"
        varchar currency "ISO-4217"
        integer trial_days
        boolean active
    }

    PLAN_METERED_RATES {
        uuid id PK
        uuid plan_id FK
        varchar metric
        numeric included_quantity "19 scale 4"
        numeric unit_price "19 scale 6"
    }

    SUBSCRIPTIONS {
        uuid id PK
        uuid tenant_id FK
        uuid plan_id FK
        varchar status
        integer quantity
        timestamptz started_at
        timestamptz current_period_start
        timestamptz current_period_end
        timestamptz trial_end
        boolean cancel_at_period_end
        timestamptz canceled_at
        timestamptz ended_at
        bigint version
    }

    INVOICES {
        uuid id PK
        uuid tenant_id FK
        uuid subscription_id FK
        varchar invoice_number UK
        varchar status
        varchar currency
        numeric subtotal "19 scale 4"
        numeric tax_amount "19 scale 4"
        numeric total "19 scale 4"
        numeric amount_paid "19 scale 4"
        timestamptz period_start
        timestamptz period_end
        timestamptz issued_at
        timestamptz due_at
        timestamptz paid_at
    }

    INVOICE_LINE_ITEMS {
        uuid id PK
        uuid tenant_id FK
        uuid invoice_id FK
        varchar line_type
        varchar description
        varchar metric "nullable"
        numeric quantity "19 scale 4"
        numeric unit_price "19 scale 6"
        numeric amount "negative for credits"
    }

    USAGE_EVENTS {
        uuid id PK
        uuid tenant_id FK
        uuid subscription_id FK
        varchar metric
        numeric quantity "19 scale 4"
        varchar idempotency_key "UK with tenant_id"
        varchar source
        timestamptz occurred_at "decides the period"
        timestamptz recorded_at "ingest time"
    }

    USAGE_COUNTERS {
        uuid id PK
        uuid tenant_id FK
        uuid subscription_id FK
        varchar metric
        timestamptz period_start "UK part"
        timestamptz period_end
        numeric total_quantity "19 scale 4"
        bigint version
    }
```

### Constraints that carry business rules

| Constraint | Rule it enforces |
|---|---|
| `uk_subscriptions_one_live_per_tenant` — partial unique index on `tenant_id` `WHERE status IN ('TRIALING','ACTIVE','PAST_DUE')` | A tenant may hold at most one live subscription, even under concurrent requests |
| `uk_usage_events_idempotency` — unique `(tenant_id, idempotency_key)` | At-least-once Kafka redelivery cannot double-charge |
| `uk_usage_counters` — unique `(subscription_id, metric, period_start)` | Exactly one roll-up row per metric per period |
| `uk_app_users_email` — globally unique | Login can resolve the tenant before one is known |
| `uk_invoices_number` | Document numbers are unique across the platform |
| `ck_subscriptions_period` — `current_period_end > current_period_start` | No degenerate period can reach the proration maths |
| `invoice_number_seq` | Transaction-independent numbering; concurrent billing runs cannot collide |

**Money columns are `NUMERIC(19,4)`** and are only ever read into `BigDecimal`. Per-unit prices
use `NUMERIC(19,6)` because they are frequently fractions of a cent (e.g. `$0.000150` per API
call); rounding to two decimals happens once, on the resulting line amount.

---

## 10. DFD Level 1 — the whole system

```mermaid
flowchart TB
    ADMIN["Tenant<br/>Administrator"]
    MEMBER["Tenant<br/>Member"]
    SYSTEM["Customer<br/>System"]
    PROSPECT["Prospective<br/>Customer"]

    subgraph BOUNDARY["Application trust boundary"]
        P1((("1.0<br/>Tenant<br/>Onboarding")))
        P2((("2.0<br/>Authentication<br/>&amp; Admission")))
        P3((("3.0<br/>Subscription<br/>Lifecycle")))
        P4((("4.0<br/>Usage<br/>Capture")))
        P5((("5.0<br/>Usage<br/>Ingestion")))
        P6((("6.0<br/>Invoicing")))
        P7((("7.0<br/>Plan<br/>Catalogue")))
    end

    D1[("D1 tenants")]
    D2[("D2 app_users")]
    D3[("D3 plans +<br/>plan_metered_rates")]
    D4[("D4 subscriptions")]
    D5[("D5 invoices +<br/>invoice_line_items")]
    D6[("D6 usage_events")]
    D7[("D7 usage_counters")]
    D8[("D8 Kafka topic<br/>billing.usage-events.v1")]
    D9[("D9 Kafka DLT<br/>...v1.DLT")]

    ADMIN -->|"registration details"| P1
    P1 -->|"tenant record"| D1
    P1 -->|"admin user + BCrypt hash"| D2
    P1 -->|"access token"| ADMIN

    ADMIN -->|"credentials"| P2
    MEMBER -->|"credentials"| P2
    P2 -->|"lookup by email"| D2
    P2 -->|"tenant status check"| D1
    P2 -->|"signed JWT with tid claim"| ADMIN
    P2 -->|"signed JWT with tid claim"| MEMBER

    P2 ==>|"verified tenantId<br/>→ TenantContext"| P3
    P2 ==>|"verified tenantId"| P4
    P2 ==>|"verified tenantId"| P6

    ADMIN -->|"create / change plan / cancel"| P3
    P3 -->|"read plan + rates"| D3
    P3 -->|"read / write subscription"| D4
    P3 -->|"proration settlement invoice"| D5
    P3 -->|"subscription state"| ADMIN

    SYSTEM -->|"usage report"| P4
    MEMBER -->|"usage report"| P4
    P4 -->|"resolve live subscription"| D4
    P4 -->|"publish UsageEventMessage<br/>key = tenant:subscription"| D8
    P4 -->|"202 + topic/partition/offset"| SYSTEM

    D8 -->|"poll records"| P5
    P5 -->|"idempotency check + append"| D6
    P5 -->|"locked read-modify-write"| D7
    P5 -->|"read subscription + period"| D4
    P5 -->|"exhausted retries / invalid"| D9

    ADMIN -->|"generate / pay invoice"| P6
    P6 -->|"read subscription + plan"| D4
    P6 -->|"read plan rates"| D3
    P6 -->|"read counters"| D7
    P6 -->|"write invoice + lines"| D5
    P6 -->|"invoice document"| ADMIN

    MEMBER -->|"usage summary query"| P6
    P6 -->|"projected overage"| MEMBER

    PROSPECT -->|"browse catalogue"| P7
    P7 -->|"read active plans"| D3
    P7 -->|"plan list / detail"| PROSPECT

    style BOUNDARY fill:#f8f9fa,stroke:#495057,stroke-width:2px
    style D8 fill:#f3e5f5,stroke:#7b1fa2
    style D9 fill:#ffebee,stroke:#c62828
```

### Level-1 process register

| # | Process | Trigger | Reads | Writes | Output |
|---|---|---|---|---|---|
| 1.0 | Tenant Onboarding | `POST /auth/register` | D1, D2 (uniqueness) | D1, D2 | Access token |
| 2.0 | Authentication & Admission | `POST /auth/login`, then every request | D2, D1 | — | JWT / `TenantContext` |
| 3.0 | Subscription Lifecycle | `POST /subscriptions`, `/change-plan`, `/cancel` | D3, D4 | D4, D5 | Subscription state |
| 4.0 | Usage Capture | `POST /usage/events` | D4 | D8 | 202 Accepted |
| 5.0 | Usage Ingestion | Kafka poll | D8, D4 | D6, D7, D9 | (none — async) |
| 6.0 | Invoicing | `POST /invoices/generate`, `/pay`, summary query | D3, D4, D7, D5 | D5 | Invoice / summary |
| 7.0 | Plan Catalogue | `GET /plans` | D3 | — | Plan list |

---

## 11. Level-2 decompositions

### 11.1 DFD Level 2.1 — Tenant onboarding

```mermaid
flowchart TB
    ADMIN["Tenant<br/>Administrator"]

    P11(("1.1<br/>Validate<br/>payload"))
    P12(("1.2<br/>Check slug<br/>uniqueness"))
    P13(("1.3<br/>Check email<br/>uniqueness"))
    P14(("1.4<br/>Create<br/>tenant"))
    P15(("1.5<br/>Hash password<br/>BCrypt cost 12"))
    P16(("1.6<br/>Create admin<br/>user"))
    P17(("1.7<br/>Issue<br/>JWT"))

    D1[("D1 tenants")]
    D2[("D2 app_users")]
    ERR["API envelope<br/>400 / 409"]

    ADMIN -->|"RegisterTenantRequest"| P11
    P11 -->|"constraint violations"| ERR
    P11 -->|"valid payload"| P12
    P12 -->|"existsBySlug"| D1
    P12 -->|"slug taken"| ERR
    P12 -->|"free"| P13
    P13 -->|"existsByEmailIgnoreCase"| D2
    P13 -->|"email taken"| ERR
    P13 -->|"free"| P14
    P14 -->|"INSERT tenant<br/>status = ACTIVE"| D1
    P14 -->|"tenant id"| P15
    P15 -->|"password hash"| P16
    P16 -->|"INSERT app_user<br/>tenant_id explicit, role TENANT_ADMIN"| D2
    P16 -->|"principal"| P17
    P17 -->|"AuthResponse<br/>token, expiresAt, tenantId, slug, role"| ADMIN

    style ERR fill:#ffebee,stroke:#c62828
```

**Step by step**

| Step | Action | Detail |
|---|---|---|
| 1.1 | Bean Validation on `RegisterTenantRequest` | Slug must match `^[a-z0-9]+(-[a-z0-9]+)*$`; password 12–72 chars (BCrypt truncates past 72 bytes) |
| 1.2 | `tenantRepository.existsBySlug` | Failure → `DuplicateResourceException` → **409** |
| 1.3 | `appUserRepository.existsByEmailIgnoreCase` | Failure → **409** |
| 1.4 | Insert `Tenant` with `status = ACTIVE` | Hibernate assigns the UUID before insert |
| 1.5 | `passwordEncoder.encode` | BCrypt strength 12 (~250 ms — invisible per login, expensive to brute-force) |
| 1.6 | Insert `AppUser` | `tenantId` set **explicitly**: the request is not authenticated, so no `TenantContext` exists to read from |
| 1.7 | `jwtService.issue` | Claims: `sub`, `iss`, `iat`, `exp`, `jti`, `tid`, `roles`, `email` |

Steps 1.4 and 1.6 run in **one transaction**. A tenant with no administrator is unreachable —
nobody could ever log in — and would need manual cleanup.

---

### 11.2 DFD Level 2.2 — Authentication and request admission

```mermaid
flowchart TB
    CLIENT["Client"]

    subgraph LOGIN["Login — POST /auth/login"]
        P21(("2.1<br/>Authenticate<br/>credentials"))
        P22(("2.2<br/>Verify tenant<br/>is ACTIVE"))
        P23(("2.3<br/>Issue<br/>token"))
    end

    subgraph ADMIT["Every subsequent request"]
        P24(("2.4<br/>Extract<br/>Bearer token"))
        P25(("2.5<br/>Verify signature,<br/>issuer, expiry"))
        P26(("2.6<br/>Rebuild<br/>principal"))
        P27(("2.7<br/>Bind Security +<br/>Tenant context"))
        P28(("2.8<br/>Authorise<br/>role"))
        P29(("2.9<br/>Clear both<br/>contexts"))
    end

    D2[("D2 app_users")]
    D1[("D1 tenants")]
    E401["401<br/>AUTHENTICATION_FAILED"]
    E403["403<br/>ACCESS_DENIED"]
    HANDLER["Controller →<br/>Service"]

    CLIENT -->|"LoginRequest"| P21
    P21 -->|"loadUserByUsername"| D2
    P21 -->|"bad credentials"| E401
    P21 -->|"principal"| P22
    P22 -->|"findById"| D1
    P22 -->|"SUSPENDED / CLOSED → 409"| E401
    P22 --> P23
    P23 -->|"AuthResponse"| CLIENT

    CLIENT -->|"Authorization: Bearer ..."| P24
    P24 -->|"absent → anonymous"| P28
    P24 -->|"token"| P25
    P25 -->|"JwtException"| E401
    P25 -->|"verified claims"| P26
    P26 -->|"AppUserPrincipal<br/>no passwordHash"| P27
    P27 -->|"tid claim → ThreadLocal"| P28
    P28 -->|"@PreAuthorize fails"| E403
    P28 -->|"granted"| HANDLER
    HANDLER --> P29

    style E401 fill:#ffebee,stroke:#c62828
    style E403 fill:#ffebee,stroke:#c62828
```

**Step by step**

| Step | Action | Detail |
|---|---|---|
| 2.1 | `AuthenticationManager.authenticate` | `DaoAuthenticationProvider` with `hideUserNotFoundExceptions = true` — uniform timing so the endpoint is not an account-enumeration oracle |
| 2.2 | Tenant status gate | Login and every bearer-authenticated request check current tenant status |
| 2.3 | Mint HS256 JWT | 30-minute TTL limits token lifetime; current account and tenant state are checked on every request |
| 2.4 | Read `Authorization` header | Missing/blank → proceed anonymously; the authorization filter yields the 401 |
| 2.5 | `Jwts.parser().verifyWith(key).requireIssuer(...).clock(jwtClock)` | Signature verified **before** any claim is read. Clock is injected, so expiry is deterministic and testable |
| 2.6 | Build `AppUserPrincipal` from claims | **No database read** — this is what keeps request handling free of a round trip |
| 2.7 | Set `SecurityContextHolder` **and** `TenantContextHolder` | Both from the same verified token |
| 2.8 | `@PreAuthorize("hasRole('TENANT_ADMIN')")` on mutations | Reads are open to any authenticated tenant member |
| 2.9 | `finally` → clear both | **Load-bearing.** Tomcat reuses request threads; a leaked tenant would be inherited by an unrelated later request |

---

### 11.3 DFD Level 2.3 — Subscription lifecycle

```mermaid
flowchart TB
    ADMIN["Tenant<br/>Administrator"]

    P31(("3.1<br/>Guard: one live<br/>subscription"))
    P32(("3.2<br/>Resolve active<br/>plan by code"))
    P33(("3.3<br/>Compute trial<br/>and period"))
    P34(("3.4<br/>Persist<br/>subscription"))

    P35(("3.5<br/>Validate change<br/>is legal"))
    P36(("3.6<br/>Compute<br/>proration"))
    P37(("3.7<br/>Issue settlement<br/>invoice"))
    P38(("3.8<br/>Apply new plan<br/>and quantity"))

    P39(("3.9<br/>Cancel:<br/>now or at period end"))

    D3[("D3 plans")]
    D4[("D4 subscriptions")]
    D5[("D5 invoices")]
    ERR["API envelope<br/>404 / 409"]

    ADMIN -->|"CreateSubscriptionRequest"| P31
    P31 -->|"existsByTenantIdAndStatusIn"| D4
    P31 -->|"already live → 409"| ERR
    P31 --> P32
    P32 -->|"findWithRatesByCode + active"| D3
    P32 -->|"unknown / inactive → 404"| ERR
    P32 --> P33
    P33 -->|"trial? end = now + trialDays<br/>else end = cycle.advance(now)"| P34
    P34 -->|"INSERT status TRIALING or ACTIVE"| D4
    P34 -->|"SubscriptionResponse"| ADMIN

    ADMIN -->|"ChangePlanRequest"| P35
    P35 -->|"findByIdAndTenantId"| D4
    P35 -->|"not live / pending cancel /<br/>no change / currency mismatch → 409"| ERR
    P35 --> P36
    P36 -->|"r = remaining seconds / total seconds"| P37
    P37 -->|"skip if trialing or zero"| P38
    P37 -->|"INSERT invoice<br/>PRORATION_CREDIT + PRORATION_CHARGE"| D5
    P38 -->|"UPDATE plan_id, quantity<br/>period unchanged"| D4
    P38 -->|"SubscriptionResponse"| ADMIN

    ADMIN -->|"CancelSubscriptionRequest"| P39
    P39 -->|"atPeriodEnd: set flag, status unchanged<br/>immediate: CANCELED + canceledAt + endedAt"| D4
    P39 -->|"SubscriptionResponse"| ADMIN

    style ERR fill:#ffebee,stroke:#c62828
```

**Create — step by step**

| Step | Action | Failure mode |
|---|---|---|
| 3.1 | `existsByTenantIdAndStatusIn(tenantId, liveStatuses())` | 409 `SUBSCRIPTION_ALREADY_EXISTS`. Backed by the partial unique index for concurrent requests |
| 3.2 | `findWithRatesByCode(...).filter(Plan::isActive)` | 404 `Active plan` |
| 3.3 | `startsInTrial = !skipTrial && plan.trialDays > 0` | — |
| 3.3a | If trialing: `trialEnd = now + trialDays`, `currentPeriodEnd = trialEnd` | The trial **is** the first period; it ends when the first real invoice becomes due |
| 3.3b | Else `currentPeriodEnd = billingCycle.advance(now)` | Calendar arithmetic (`plusMonths`), not fixed days — a cycle starting on the 31st lands on the last day of a shorter month instead of drifting |
| 3.4 | Insert with `status = TRIALING` or `ACTIVE`, `cancelAtPeriodEnd = false` | — |

**Change plan — step by step**

| Step | Action | Detail |
|---|---|---|
| 3.5a | Load via `findByIdAndTenantId` | 404 if absent **or owned by another tenant** — deliberately not 403, which would confirm the id exists elsewhere |
| 3.5b | Reject if `!isLive()` | 409 `SUBSCRIPTION_NOT_LIVE` |
| 3.5c | Reject if `cancelAtPeriodEnd` | 409 `SUBSCRIPTION_PENDING_CANCELLATION` |
| 3.5d | Reject same plan **and** same quantity | 409 `NO_CHANGE_REQUESTED` |
| 3.5e | Reject currency change | 409 `CURRENCY_MISMATCH` — crossing currencies mid-period needs an FX rate and a policy for which rate applies to the credit; refuse rather than guess |
| 3.6 | `ProrationCalculator.calculate(sub, newPlan, newQty, now)` | Skipped entirely while `TRIALING` — nothing has been charged, so there is nothing to credit |
| 3.7 | If non-zero: assemble invoice over `[now, currentPeriodEnd)` | Separate document from the periodic bill, so the recurring invoice stays a clean statement of the period |
| 3.8 | `setPlan`, `setQuantity` | **Period boundaries do not move.** A different billing cycle takes effect at renewal |

**Proration formula**

```
r      = (periodEnd − effectiveAt) / (periodEnd − periodStart)   clamped to [0, 1]
credit = oldPrice × oldQuantity × r        → written as a NEGATIVE line
charge = newPrice × newQuantity × r        → written as a POSITIVE line
net    = charge − credit                   → positive on upgrade, negative on downgrade
```

| Decision | Reason |
|---|---|
| `BigDecimal` throughout | `double` cannot represent 0.1 exactly; a cent lost per proration is a reconciliation problem that surfaces months later |
| Ratio keeps 16 significant digits | Rounding `r` to the money scale first would distort every derived amount. Rounding happens **once**, per final line |
| Elapsed **seconds**, not days | Day-granularity truncation silently favours whichever side it falls on, and breaks across leap days |
| Credit and charge stay **separate lines** | A single netted figure is impossible for a customer, or an auditor, to verify |
| Ratio clamped to `[0, 1]` | Clock skew between instances could otherwise refund more than the customer paid |

**Cancel — step by step**

| Mode | State change | Rationale |
|---|---|---|
| `atPeriodEnd = true` | `cancelAtPeriodEnd = true`; status **unchanged** | The customer keeps service already paid for; renewal simply does not happen, so no refund is due |
| `atPeriodEnd = false` | `status = CANCELED`, `canceledAt = now`, `endedAt = now` | Service stops immediately. **No credit note** is issued for the unused remainder (documented limitation) |

---

### 11.4 DFD Level 2.4 — Usage capture (producer side)

```mermaid
flowchart TB
    SYSTEM["Customer System /<br/>Tenant Member"]

    P41(("4.1<br/>Validate<br/>request"))
    P42(("4.2<br/>Resolve tenant's<br/>live subscription"))
    P43(("4.3<br/>Default and guard<br/>occurredAt"))
    P44(("4.4<br/>Resolve<br/>idempotency key"))
    P45(("4.5<br/>Build<br/>UsageEventMessage"))
    P46(("4.6<br/>Publish and BLOCK<br/>for acks=all"))

    D4[("D4 subscriptions")]
    D8[("D8 Kafka topic")]
    E409["409 NO_LIVE_SUBSCRIPTION<br/>409 USAGE_TIMESTAMP_IN_FUTURE"]
    E503["503<br/>USAGE_PUBLISH_FAILED"]

    SYSTEM -->|"RecordUsageRequest"| P41
    P41 -->|"metric null, quantity negative → 400"| E409
    P41 --> P42
    P42 -->|"findFirstByTenantIdAndStatusIn"| D4
    P42 -->|"none → 409"| E409
    P42 --> P43
    P43 -->|"more than 5 min ahead → 409"| E409
    P43 --> P44
    P44 -->|"client key, else random UUID"| P45
    P45 -->|"tenantId from JWT<br/>subscriptionId server-resolved"| P46
    P46 -->|"key = tenantId:subscriptionId"| D8
    P46 -->|"timeout / broker error"| E503
    P46 -->|"202 Accepted<br/>topic, partition, offset"| SYSTEM

    style E409 fill:#fff3e0,stroke:#ef6c00
    style E503 fill:#ffebee,stroke:#c62828
```

**Step by step**

| Step | Action | Detail |
|---|---|---|
| 4.1 | Bean Validation | `metric` required, `quantity >= 0` with `@Digits(15,4)` |
| 4.2 | `findFirstByTenantIdAndStatusIn(tenantId, liveStatuses())` | The subscription is **resolved server-side**, never accepted from the client — that is what removes cross-tenant validation from every write path |
| 4.3 | `occurredAt = request.occurredAt ?: now` | Rejected beyond 5 minutes in the future: a far-future timestamp would park usage in a period that has not started, quietly removing revenue from the current invoice |
| 4.4 | Client key, or a server-generated UUID | A server key still guarantees exactly-once *processing* of this publish, but cannot deduplicate a client that retries the HTTP call — hence the documentation nudge to supply one |
| 4.5 | `UsageEventMapper.toMessage` | `tenantId` from the verified JWT, `subscriptionId` from 4.2 |
| 4.6 | `kafkaTemplate.send(...).get(10s)` | **Synchronous by design** — see below |

**Why the send blocks.** Usage is revenue. Answering `202 Accepted` for a record that never
reached a broker would silently under-bill the customer, and the client has no way to discover
the loss. Blocking until `acks=all` confirms the write turns a failure into a retryable `503`
while it can still be retried safely — the idempotency key makes that retry harmless. The cost
is request latency bounded by 10 s; a high-throughput ingest path would instead batch
asynchronously behind a durable local write-ahead log.

**Why the partition key is `tenantId:subscriptionId`.** All events for one subscription land on
a single partition. That gives per-subscription ordering and, more importantly, means
concurrent consumer threads never contend on the same `usage_counters` row.

---

### 11.5 DFD Level 2.5 — Usage ingestion (consumer side)

```mermaid
flowchart TB
    D8[("D8 Kafka topic<br/>6 partitions")]

    P51(("5.1<br/>Poll and<br/>deserialize"))
    P52(("5.2<br/>Structural<br/>validation"))
    P53(("5.3<br/>Seed TenantContext<br/>from payload"))
    P54(("5.4<br/>Idempotency<br/>pre-check"))
    P55(("5.5<br/>Resolve<br/>subscription"))
    P56(("5.6<br/>Resolve billing<br/>period for occurredAt"))
    P57(("5.7<br/>Append to<br/>event ledger"))
    P58(("5.8<br/>Increment counter<br/>under row lock"))
    P59(("5.9<br/>Commit offset,<br/>clear TenantContext"))

    D4[("D4 subscriptions")]
    D6[("D6 usage_events")]
    D7[("D7 usage_counters")]
    D9[("D9 DLT")]

    RETRY["DefaultErrorHandler<br/>FixedBackOff 2s × 3 attempts"]

    D8 -->|"ConsumerRecord"| P51
    P51 -->|"ErrorHandlingDeserializer<br/>malformed → null + header"| RETRY
    P51 --> P52
    P52 -->|"null tenant / subscription / metric<br/>negative quantity / no occurredAt"| RETRY
    P52 --> P53
    P53 --> P54
    P54 -->|"existsByTenantIdAndIdempotencyKey"| D6
    P54 -->|"duplicate → no-op, commit"| P59
    P54 --> P55
    P55 -->|"findByIdAndTenantId"| D4
    P55 -->|"not in this tenant → non-retryable"| RETRY
    P55 --> P56
    P56 -->|"walk cycles from current period"| P57
    P57 -->|"INSERT usage_event"| D6
    P57 --> P58
    P58 -->|"SELECT ... FOR UPDATE,<br/>create if absent, add quantity"| D7
    P58 --> P59

    RETRY -->|"retryable: redeliver"| P51
    RETRY -->|"budget exhausted or<br/>NonRetryableUsageEventException"| D9

    style RETRY fill:#fff3e0,stroke:#ef6c00
    style D9 fill:#ffebee,stroke:#c62828
```

**Step by step**

| Step | Action | Detail |
|---|---|---|
| 5.1 | Listener container polls; `ErrorHandlingDeserializer` wraps `JsonDeserializer` | Without the wrapper a malformed payload throws **inside the poll loop**, before any error handler can see it, and the consumer retries the same offset forever — the classic "poison pill stalls the partition" outage |
| 5.2 | `validate(...)` | Missing tenant/subscription/metric, negative quantity, or absent `occurredAt` → `NonRetryableUsageEventException`. These will never become valid, so spending the retry budget only delays every other record on the partition |
| 5.3 | `TenantContextHolder.setTenantId(message.tenantId())` | The consumer thread never passed through the auth filter; the payload is the only tenancy source |
| 5.4 | `existsByTenantIdAndIdempotencyKey` | Cheap fast path. The **guarantee** is the unique constraint, which also covers two simultaneous in-flight deliveries |
| 5.5 | `findByIdAndTenantId` | A subscription not in this tenant will not appear on retry → non-retryable |
| 5.6 | `BillingPeriodResolver.resolve(subscription, occurredAt)` | See below |
| 5.7 | Insert `UsageEvent` | Records both clocks: `occurredAt` (billable) and `recordedAt` (durability) |
| 5.8 | `findBy...(PESSIMISTIC_WRITE)` or create, then `increment` | A pessimistic lock serialises contended increments; optimistic locking would surface as a stream of retryable failures on a hot row |
| 5.9 | Listener returns → container commits offset; `finally` clears the context | At-least-once delivery. **Mandatory clear** — listener threads are pooled and long-lived |

Steps 5.7 and 5.8 are **one transaction**. Writing the event without incrementing the counter
under-bills; incrementing without recording the event leaves a total nobody can explain.

**Billing period resolution (step 5.6).** Usage is billed to the period in which it *occurred*,
not the one it was ingested in. Consumer lag, a client batching yesterday's meter readings, or
a DLT replay can all deliver an event predating the current window.

```mermaid
flowchart LR
    START["occurredAt"] --> Q1{"before<br/>currentPeriodStart?"}
    Q1 -->|yes| BACK["end = start<br/>start = cycle.retreat(start)"]
    BACK --> Q1
    Q1 -->|no| Q2{"at or after<br/>periodEnd?"}
    Q2 -->|yes| FWD["start = end<br/>end = cycle.advance(end)"]
    FWD --> Q2
    Q2 -->|no| OUT["BillingPeriod[start, end)"]

    Q1 -.->|"steps ≥ 120"| FALLBACK["log warning,<br/>use current period"]
    Q2 -.->|"steps ≥ 120"| FALLBACK
```

Periods are **half-open** `[start, end)`, which is what makes consecutive periods tile without
overlap — an event exactly on a boundary is billed once, not twice. The 120-step bound stops a
nonsensical timestamp (epoch zero, far future) from spinning.

---

### 11.6 DFD Level 2.6 — Invoicing

```mermaid
flowchart TB
    ADMIN["Tenant<br/>Administrator"]

    P61(("6.1<br/>Load subscription<br/>for tenant"))
    P62(("6.2<br/>Guard against<br/>double billing"))
    P63(("6.3<br/>Open DRAFT<br/>invoice"))
    P64(("6.4<br/>Add recurring<br/>fee line"))
    P65(("6.5<br/>Add metered<br/>overage lines"))
    P66(("6.6<br/>Total, tax,<br/>issue as OPEN"))
    P67(("6.7<br/>Mark<br/>paid"))

    D3[("D3 plan rates")]
    D4[("D4 subscriptions")]
    D5[("D5 invoices +<br/>line items")]
    D7[("D7 usage_counters")]
    SEQ[("invoice_number_seq")]
    ERR["404 / 409"]

    ADMIN -->|"subscriptionId"| P61
    P61 -->|"findByIdAndTenantId"| D4
    P61 -->|"not found → 404"| ERR
    P61 --> P62
    P62 -->|"existsBy...PeriodStartAndStatusNot(VOID)"| D5
    P62 -->|"already invoiced → 409"| ERR
    P62 --> P63
    P63 -->|"nextval"| SEQ
    P63 -->|"zeroed totals, status DRAFT"| P64
    P64 -->|"skip if period wholly in trial"| P65
    P64 -->|"basePrice × quantity"| P65
    P65 -->|"read counters for periodStart"| D7
    P65 -->|"read includedQuantity, unitPrice"| D3
    P65 -->|"overage = total − included, if > 0"| P66
    P66 -->|"INSERT invoice + lines, status OPEN"| D5
    P66 -->|"InvoiceResponse"| ADMIN

    ADMIN -->|"invoiceId"| P67
    P67 -->|"terminal status → 409"| ERR
    P67 -->|"amountPaid = total, status PAID, paidAt"| D5
    P67 -->|"InvoiceResponse"| ADMIN

    style ERR fill:#ffebee,stroke:#c62828
```

**Step by step**

| Step | Action | Detail |
|---|---|---|
| 6.1 | `findByIdAndTenantId` | Tenant-scoped by construction |
| 6.2 | Duplicate guard | A non-`VOID` invoice covering the same `periodStart` blocks a second. The operation is externally triggered, so a retried request must not produce a duplicate bill. Proration invoices use `periodStart = effectiveAt`, so they never collide with the periodic one |
| 6.3 | `openInvoice` | `DRAFT` with zeroed totals — a partially assembled document can never be mistaken for a payable one. Number from the DB sequence, so concurrent runs cannot collide (gaps from rollbacks are acceptable in an invoice series) |
| 6.4 | `addSubscriptionLine` | Skipped when the trial covers the whole period. Metered overage is still charged in that case |
| 6.5 | `addUsageLines` | One line per metric **in overage**. Metrics inside their allowance produce **no line at all** — an invoice should list what is being charged; full consumption is on the summary endpoint. Metrics the plan does not price are free |
| 6.6 | `finalise` | `subtotal = Σ rounded line amounts`; `tax` only on a positive subtotal; `status = OPEN`, `issuedAt = now`, `dueAt = now + invoiceDueDays` |
| 6.7 | `markPaid` | Rejects terminal statuses (`PAID`, `VOID`, `UNCOLLECTIBLE`) with 409 |

**Rounding discipline.** Each line is rounded once, as it is created, and the invoice total is
the sum of already-rounded lines. Summing unrounded lines and rounding at the end produces a
total that does not equal the visible lines — indefensible on a customer-facing document.

**No negative tax.** A net-credit invoice (the usual outcome of a downgrade) must not generate
negative tax; that is a tax authority's problem to settle, not something an application invents.

**Worked example** — PRO plan, quantity 2, 612,500 API calls against a 500,000 allowance at
`$0.000150`:

| Line | Quantity | Unit price | Amount |
|---|---|---|---|
| `SUBSCRIPTION` — Professional | 2 | 99.0000 | **198.00** |
| `USAGE` — API_CALLS overage | 112,500 | 0.000150 | **16.88** |
| | | Subtotal | **214.88** |
| | | Tax @ 0% | 0.00 |
| | | **Total** | **214.88** |

---

## 12. Sequence diagrams

### 12.1 Tenant registration

```mermaid
sequenceDiagram
    actor Admin
    participant AC as AuthController
    participant AS as AuthServiceImpl
    participant TR as TenantRepository
    participant UR as AppUserRepository
    participant PE as PasswordEncoder
    participant JS as JwtService
    participant DB as PostgreSQL

    Admin->>AC: POST /auth/register
    AC->>AC: @Valid RegisterTenantRequest
    AC->>AS: register(request)
    activate AS
    Note over AS: @Transactional begins.<br/>No TenantContext yet — this<br/>is what establishes tenancy.

    AS->>TR: existsBySlug(slug)
    TR->>DB: SELECT
    DB-->>TR: false
    AS->>UR: existsByEmailIgnoreCase(email)
    UR->>DB: SELECT
    DB-->>UR: false

    AS->>TR: save(Tenant ACTIVE)
    TR->>DB: INSERT tenants
    DB-->>TR: tenant(id)

    AS->>PE: encode(password)
    PE-->>AS: BCrypt hash

    AS->>UR: save(AppUser tenantId explicit)
    UR->>DB: INSERT app_users
    Note over AS,DB: Both rows commit together.<br/>A tenant without an admin<br/>would be unreachable.

    AS->>JS: issue(principal)
    JS-->>AS: IssuedToken(jwt, expiresAt)
    AS-->>AC: AuthResponse
    deactivate AS
    AC-->>Admin: 201 Created + token
```

### 12.2 Authenticated request — tenant context lifecycle

```mermaid
sequenceDiagram
    actor Client
    participant F as JwtAuthenticationFilter
    participant JS as JwtService
    participant TC as TenantContextHolder
    participant SC as SecurityContextHolder
    participant CTRL as Controller
    participant TX as TransactionInterceptor
    participant ASP as TenantFilterAspect
    participant SVC as ServiceImpl
    participant HB as Hibernate Session
    participant DB as PostgreSQL

    Client->>F: GET /subscriptions<br/>Authorization: Bearer ...
    activate F
    F->>F: extractBearerToken
    F->>JS: parse(token)
    JS->>JS: verify signature, issuer, exp<br/>(injected clock)
    JS-->>F: AppUserPrincipal(userId, tenantId, role)
    F->>SC: setAuthentication
    F->>TC: setTenantId(tenantId)

    F->>CTRL: doFilter
    CTRL->>TX: list(pageable)
    activate TX
    Note over TX: order = 0 → OUTERMOST.<br/>Transaction and Session open here.
    TX->>ASP: proceed
    activate ASP
    Note over ASP: @Order(100) → runs INSIDE the tx
    ASP->>TC: requireTenantId()
    TC-->>ASP: tenantId
    ASP->>HB: enableFilter("tenantFilter")<br/>.setParameter(tenantId)
    ASP->>SVC: proceed
    activate SVC
    SVC->>DB: SELECT ... WHERE tenant_id = $1<br/>AND tenant_id = $2
    Note over SVC,DB: $1 = Hibernate filter<br/>$2 = ...AndTenantId predicate
    DB-->>SVC: rows (this tenant only)
    SVC-->>ASP: PageResponse
    deactivate SVC
    deactivate ASP
    TX-->>CTRL: commit
    deactivate TX
    CTRL-->>F: 200 OK

    Note over F: finally — MANDATORY
    F->>TC: clear()
    F->>SC: clearContext()
    deactivate F
    F-->>Client: 200 OK
```

### 12.3 Usage reporting — producer

```mermaid
sequenceDiagram
    actor Sys as Customer System
    participant UC as UsageController
    participant US as UsageServiceImpl
    participant SR as SubscriptionRepository
    participant MAP as UsageEventMapper
    participant PROD as UsageEventProducer
    participant K as Kafka Broker

    Sys->>UC: POST /usage/events
    UC->>US: record(request)
    activate US
    US->>US: TenantContextHolder.requireTenantId()
    US->>SR: findFirstByTenantIdAndStatusIn(live)
    SR-->>US: Subscription
    alt no live subscription
        US-->>Sys: 409 NO_LIVE_SUBSCRIPTION
    end
    US->>US: occurredAt = request ?: now
    alt occurredAt > now + 5 min
        US-->>Sys: 409 USAGE_TIMESTAMP_IN_FUTURE
    end
    US->>US: idempotencyKey = client ?: UUID
    US->>MAP: toMessage(request, tenantId, subId, occurredAt, key)
    MAP-->>US: UsageEventMessage
    US->>PROD: publish(message)
    activate PROD
    PROD->>K: send(key = tenant:subscription)<br/>acks = all
    Note over PROD,K: BLOCKS up to 10s.<br/>Acking usage that was never<br/>durably stored would under-bill.
    alt broker acknowledges
        K-->>PROD: RecordMetadata(partition, offset)
        PROD-->>US: SendResult
        US-->>UC: UsageAcceptedResponse
        UC-->>Sys: 202 Accepted
    else timeout / error
        K--xPROD: failure
        PROD-->>Sys: 503 USAGE_PUBLISH_FAILED<br/>(retry with same key)
    end
    deactivate PROD
    deactivate US
```

### 12.4 Usage ingestion — consumer

```mermaid
sequenceDiagram
    participant K as Kafka Broker
    participant LC as Listener Container
    participant CON as UsageEventConsumer
    participant TC as TenantContextHolder
    participant IS as UsageIngestionServiceImpl
    participant ER as UsageEventRepository
    participant SR as SubscriptionRepository
    participant BPR as BillingPeriodResolver
    participant CR as UsageCounterRepository
    participant EH as DefaultErrorHandler
    participant DLT as Dead Letter Topic

    K->>LC: poll → ConsumerRecord
    LC->>LC: ErrorHandlingDeserializer
    LC->>CON: onUsageEvent(message, partition, offset)
    activate CON
    CON->>CON: validate(message)
    alt structurally invalid
        CON->>EH: NonRetryableUsageEventException
        EH->>DLT: publish immediately
        Note over EH,DLT: Registered as not-retryable:<br/>it will never become valid.
    end

    CON->>TC: setTenantId(message.tenantId)
    CON->>IS: ingest(message)
    activate IS
    Note over IS: @Transactional begins

    IS->>ER: existsByTenantIdAndIdempotencyKey
    alt already applied
        ER-->>IS: true
        IS-->>CON: return (no-op)
        Note over IS: At-least-once redelivery absorbed
    end

    IS->>SR: findByIdAndTenantId(subId, tenantId)
    alt not in this tenant
        IS->>EH: NonRetryableUsageEventException
        EH->>DLT: publish
    end
    SR-->>IS: Subscription

    IS->>BPR: resolve(subscription, occurredAt)
    BPR-->>IS: BillingPeriod[start, end)

    IS->>ER: save(UsageEvent)
    Note over IS,ER: Unique (tenant_id, idempotency_key)<br/>is the real guarantee

    IS->>CR: findBy...(PESSIMISTIC_WRITE)
    alt counter absent
        IS->>CR: saveAndFlush(new counter, total = 0)
    end
    IS->>CR: counter.increment(quantity)
    Note over IS,CR: Both writes commit together
    deactivate IS

    CON->>TC: clear()  [finally — pooled threads]
    deactivate CON
    LC->>K: commit offset (AckMode.RECORD)
```

### 12.5 Mid-period upgrade with proration

```mermaid
sequenceDiagram
    actor Admin
    participant SC as SubscriptionController
    participant SS as SubscriptionServiceImpl
    participant PR as PlanRepository
    participant PC as ProrationCalculator
    participant IA as InvoiceAssembler
    participant IR as InvoiceRepository
    participant DB as PostgreSQL

    Admin->>SC: POST /subscriptions/{id}/change-plan
    SC->>SS: changePlan(id, request)
    activate SS
    SS->>DB: findByIdAndTenantId
    DB-->>SS: Subscription (PRO, qty 2)

    SS->>SS: guard: live? not pending cancel?<br/>actually changing? same currency?
    SS->>PR: findWithRatesByCode("ENTERPRISE")
    PR-->>SS: Plan

    alt prorate && not trialing
        SS->>PC: calculate(sub, newPlan, newQty, now)
        activate PC
        PC->>PC: r = remaining sec / total sec, clamp [0,1]
        PC->>PC: credit = oldPrice × oldQty × r
        PC->>PC: charge = newPrice × newQty × r
        PC-->>SS: ProrationResult(credit, charge, net, r)
        deactivate PC

        alt result is non-zero
            SS->>IA: openInvoice(sub, now, currentPeriodEnd)
            IA->>IR: nextInvoiceSequenceValue()
            IR->>DB: SELECT nextval
            IA-->>SS: DRAFT invoice
            SS->>IA: addProrationLines(...)
            Note over IA: CREDIT written NEGATIVE,<br/>CHARGE written POSITIVE.<br/>Both lines always kept.
            SS->>IA: finalise(invoice)
            Note over IA: subtotal = Σ rounded lines<br/>tax only if subtotal > 0
            SS->>IR: save(invoice)
            IR->>DB: INSERT invoice + 2 lines
        end
    else trialing
        Note over SS: Skip — nothing charged yet,<br/>so nothing to credit
    end

    SS->>SS: setPlan(newPlan) then setQuantity(newQty)
    Note over SS: Period boundaries UNCHANGED.<br/>New cycle applies at renewal.
    SS->>DB: UPDATE subscriptions (optimistic version check)
    SS-->>SC: SubscriptionResponse
    deactivate SS
    SC-->>Admin: 200 OK
```

### 12.6 Invoice generation

```mermaid
sequenceDiagram
    actor Admin
    participant IC as InvoiceController
    participant IS as InvoiceServiceImpl
    participant SR as SubscriptionRepository
    participant IR as InvoiceRepository
    participant CR as UsageCounterRepository
    participant IA as InvoiceAssembler
    participant DB as PostgreSQL

    Admin->>IC: POST /invoices/generate?subscriptionId=...
    IC->>IS: generateForCurrentPeriod(id)
    activate IS
    IS->>SR: findByIdAndTenantId
    SR-->>IS: Subscription + Plan + rates

    IS->>IR: existsBy...PeriodStartAndStatusNot(VOID)
    alt already invoiced
        IR-->>IS: true
        IS-->>Admin: 409 PERIOD_ALREADY_INVOICED
    end

    IS->>IA: openInvoice(sub, periodStart, periodEnd)
    IA->>IR: nextInvoiceSequenceValue()
    IA-->>IS: Invoice(DRAFT, INV-000042)

    alt period not wholly within trial
        IS->>IA: addSubscriptionLine(invoice, sub)
        Note over IA: basePrice × quantity, rounded once
    end

    IS->>CR: findAllByTenantIdAndSubscriptionIdAndPeriodStart
    CR->>DB: SELECT counters
    DB-->>CR: [API_CALLS 612500]
    IS->>IA: addUsageLines(invoice, sub, counters)
    Note over IA: overage = 612500 − 500000 = 112500<br/>112500 × 0.000150 = 16.875 → 16.88<br/>metrics within allowance produce no line

    IS->>IA: finalise(invoice)
    Note over IA: subtotal 214.88, tax 0.00,<br/>total 214.88, status OPEN,<br/>dueAt = now + 14 days

    IS->>IR: save(invoice)
    IR->>DB: INSERT invoice + line items
    IS-->>IC: InvoiceResponse
    deactivate IS
    IC-->>Admin: 201 Created
```

---

## 13. State machines

### 13.1 Subscription lifecycle

```mermaid
stateDiagram-v2
    [*] --> TRIALING : create with plan.trialDays > 0<br/>and skipTrial = false
    [*] --> ACTIVE : create with skipTrial = true<br/>or plan has no trial

    TRIALING --> ACTIVE : trial ends, first payment succeeds
    TRIALING --> CANCELED : cancel(atPeriodEnd = false)
    TRIALING --> EXPIRED : trial ends, not converted

    ACTIVE --> ACTIVE : changePlan()<br/>period boundaries unchanged
    ACTIVE --> PAST_DUE : invoice unpaid past due date
    ACTIVE --> CANCELED : cancel(atPeriodEnd = false)
    ACTIVE --> EXPIRED : cancelAtPeriodEnd = true<br/>and period elapses

    PAST_DUE --> ACTIVE : payment received
    PAST_DUE --> CANCELED : cancel or dunning exhausted

    CANCELED --> [*]
    EXPIRED --> [*]

    note right of TRIALING
        isLive() = true
        Occupies the tenant's single
        live-subscription slot.
        Proration is skipped here.
    end note

    note right of PAST_DUE
        isLive() = true
        Still counts against the
        partial unique index.
    end note

    note right of CANCELED
        Terminal. canceledAt and
        endedAt both set.
        No credit note issued for
        the unused remainder.
    end note
```

`cancelAtPeriodEnd` is a **flag, not a state**. A subscription scheduled for cancellation stays
`ACTIVE` — the customer keeps service already paid for — and simply is not renewed.

### 13.2 Invoice lifecycle

```mermaid
stateDiagram-v2
    [*] --> DRAFT : openInvoice()<br/>zeroed totals, not payable

    DRAFT --> OPEN : finalise()<br/>totals computed, issuedAt + dueAt set
    DRAFT --> VOID : cancelled before issue

    OPEN --> PAID : markPaid()<br/>amountPaid = total, paidAt set
    OPEN --> VOID : issued in error
    OPEN --> UNCOLLECTIBLE : written off after dunning

    PAID --> [*]
    VOID --> [*]
    UNCOLLECTIBLE --> [*]

    note right of DRAFT
        isTerminal() = false
        Only DRAFT and OPEN may
        still be mutated.
    end note

    note right of VOID
        The double-billing guard
        ignores VOID invoices, so a
        voided period can be re-billed.
    end note
```

---

## 14. Multi-tenant isolation walkthrough

Every step from an inbound token to a tenant-scoped row.

```mermaid
flowchart TB
    S1["1 — Client sends<br/>Authorization: Bearer &lt;jwt&gt;"]
    S2["2 — JwtAuthenticationFilter<br/>extracts the token"]
    S3["3 — JwtService verifies signature,<br/>issuer and expiry BEFORE reading claims"]
    S4["4 — tid claim → AppUserPrincipal.tenantId"]
    S5["5 — TenantContextHolder.setTenantId<br/>(plain ThreadLocal, not inheritable)"]
    S6["6 — TransactionInterceptor (order 0)<br/>opens tx + Hibernate Session"]
    S7["7 — TenantFilterAspect (order 100)<br/>runs INSIDE the transaction"]
    S8["8 — session.enableFilter('tenantFilter')<br/>.setParameter(tenantId)"]
    S9["9 — Repository method also passes<br/>tenantId explicitly"]
    S10["10 — SQL: WHERE tenant_id = $1 AND tenant_id = $2"]
    S11["11 — Writes: @PrePersist stamps tenantId;<br/>column is updatable = false"]
    S12["12 — Schema: FK + composite unique<br/>constraints reject cross-tenant rows"]
    S13["13 — finally: TenantContextHolder.clear()<br/>and SecurityContextHolder.clearContext()"]

    S1 --> S2 --> S3 --> S4 --> S5 --> S6 --> S7 --> S8 --> S9 --> S10 --> S11 --> S12 --> S13

    N1["A caller cannot name a tenant:<br/>no tenant header, path segment<br/>or body field exists anywhere"]
    N2["Cross-tenant reads answer 404,<br/>never 403 — 403 would confirm<br/>the id exists in another tenant"]
    N3["Threads are pooled. Without step 13<br/>the next request on this thread<br/>inherits a stale tenant."]

    S4 -.-> N1
    S10 -.-> N2
    S13 -.-> N3

    style N1 fill:#e8f5e9,stroke:#2e7d32
    style N2 fill:#e8f5e9,stroke:#2e7d32
    style N3 fill:#ffebee,stroke:#c62828
```

### Deliberate exceptions — where no tenant is bound

| Operation | Why it runs unfiltered | What keeps it safe |
|---|---|---|
| `POST /auth/register` | No tenant exists yet | Scoped by globally unique slug and e-mail |
| `POST /auth/login` | The tenant is not known until the user is found | E-mail is globally unique (`uk_app_users_email`) |
| `GET /plans` | Platform catalogue, not tenant data | Plans carry no `tenant_id` at all |
| Renewal job (not wired) | Operates across every tenant by design | Would use `TenantContextHolder.runAs` per tenant |

`TenantFilterAspect` intentionally no-ops when no tenant is bound **or** no transaction is
active. Unwrapping a `Session` outside a transaction would open a short-lived one that the
subsequent transaction discards — setting the filter on the wrong session.

---

## 15. Application startup sequence

```mermaid
sequenceDiagram
    participant JVM
    participant SB as Spring Boot
    participant CP as @ConfigurationProperties
    participant FW as Flyway
    participant HB as Hibernate
    participant KA as KafkaAdmin
    participant LC as Listener Container

    JVM->>SB: SubscriptionManagerApplication.main
    SB->>CP: bind app.security / app.billing / app.kafka
    Note over CP: @Validated records.<br/>A missing or invalid value aborts<br/>startup, not the first request.
    CP-->>SB: SecurityProperties, BillingProperties,<br/>KafkaTopicProperties

    SB->>SB: JwtService constructor
    Note over SB: Key < 256 bits → IllegalStateException.<br/>Weak signing material must never<br/>reach production traffic.

    SB->>FW: migrate()
    FW->>FW: V1__init_schema.sql
    FW->>FW: V2__seed_plan_catalogue.sql
    FW-->>SB: schema at version 2

    SB->>HB: build SessionFactory
    HB->>HB: ddl-auto = validate
    Note over HB: Mapping vs schema mismatch<br/>aborts startup. This caught a real<br/>CHAR(3) vs varchar(3) defect.
    HB->>HB: register @FilterDef "tenantFilter"
    HB-->>SB: EntityManagerFactory

    SB->>KA: declare NewTopic beans
    KA->>KA: billing.usage-events.v1 (6 partitions)
    KA->>KA: billing.usage-events.v1.DLT (1 partition)

    SB->>LC: start listener containers
    LC->>LC: join group, 3 consumers,<br/>partitions assigned
    LC-->>SB: running

    SB-->>JVM: /actuator/health/readiness = UP
```

**Fail-fast order.** Configuration binding → key strength → schema migration → mapping
validation → topic declaration → consumer start. Every gate that can detect a misconfiguration
does so before the application accepts a single request.

---

## 16. Error handling flow

```mermaid
flowchart TB
    EX["Exception thrown"]

    EX --> Q1{"Type?"}

    Q1 -->|"ApplicationException"| A1["Status + errorCode<br/>carried on the exception"]
    Q1 -->|"MethodArgumentNotValid<br/>ConstraintViolation"| A2["400 VALIDATION_FAILED<br/>+ fieldErrors map"]
    Q1 -->|"HttpMessageNotReadable<br/>TypeMismatch<br/>MissingParameter"| A3["400 MALFORMED_REQUEST"]
    Q1 -->|"NoResourceFound"| A4["404 ENDPOINT_NOT_FOUND"]
    Q1 -->|"MethodNotSupported"| A5["405 + supportedMethods"]
    Q1 -->|"MediaTypeNotSupported"| A6["415"]
    Q1 -->|"AuthenticationException"| A7["401 AUTHENTICATION_FAILED"]
    Q1 -->|"AccessDeniedException"| A8["403 ACCESS_DENIED"]
    Q1 -->|"OptimisticLockingFailure"| A9["409 CONCURRENT_MODIFICATION<br/>caller may retry"]
    Q1 -->|"DataIntegrityViolation"| A10["409 DATA_INTEGRITY_VIOLATION"]
    Q1 -->|"anything else"| A11["500 INTERNAL_ERROR<br/>+ generated errorId"]

    A1 --> PD["ApiResponse with success=false"]
    A2 --> PD
    A3 --> PD
    A4 --> PD
    A5 --> PD
    A6 --> PD
    A7 --> PD
    A8 --> PD
    A9 --> PD
    A10 --> PD
    A11 --> PD

    PD --> OUT["type · title · status · detail<br/>instance · errorCode · timestamp"]

    SEC["Security filter chain<br/>failures"] -->|"HandlerExceptionResolver"| Q1
    Note1["Routing security failures back through<br/>MVC keeps ONE error format —<br/>not a second, divergent one."]
    SEC -.-> Note1

    A11 --> LOG["Stack trace logged with errorId.<br/>Never returned — no SQL, entity<br/>names or traces reach the client."]

    style A11 fill:#ffebee,stroke:#c62828
    style Note1 fill:#e8f5e9,stroke:#2e7d32
```

### Exception hierarchy

```mermaid
classDiagram
    class RuntimeException {
        <<Java>>
    }
    class ApplicationException {
        <<abstract>>
        -HttpStatus status
        -String errorCode
    }
    class ResourceNotFoundException {
        404 RESOURCE_NOT_FOUND
    }
    class BusinessRuleException {
        409 BUSINESS_RULE_VIOLATION
    }
    class DuplicateResourceException {
        409 RESOURCE_ALREADY_EXISTS
    }
    class TenantContextMissingException {
        500 TENANT_CONTEXT_MISSING
    }
    class PublishFailedException {
        503 USAGE_PUBLISH_FAILED
    }

    RuntimeException <|-- ApplicationException
    ApplicationException <|-- ResourceNotFoundException
    ApplicationException <|-- BusinessRuleException
    ApplicationException <|-- DuplicateResourceException
    ApplicationException <|-- TenantContextMissingException
    ApplicationException <|-- PublishFailedException
```

Carrying status and a stable `errorCode` **on the exception** lets one handler render every
failure consistently, instead of a growing chain of `instanceof` checks.

`TenantContextMissingException` maps to **500, not 401** — deliberately. It means tenant-scoped
work was attempted with no tenant bound, which is a *bug* (a service called outside the request
scope, or a listener that forgot to seed the context), not a client error. The engine fails
closed rather than silently querying across every tenant.

---

## 17. Data dictionary

### Principal data flows

| Flow | From → To | Payload |
|---|---|---|
| `RegisterTenantRequest` | Admin → 1.0 | tenantName, slug, adminEmail, adminFullName, adminPassword |
| `AuthResponse` | 1.0/2.0 → Client | accessToken, tokenType, expiresAt, userId, tenantId, tenantSlug, role |
| `CreateSubscriptionRequest` | Admin → 3.0 | planCode, quantity, skipTrial |
| `ChangePlanRequest` | Admin → 3.0 | planCode, quantity?, prorate? |
| `CancelSubscriptionRequest` | Admin → 3.0 | atPeriodEnd, reason |
| `SubscriptionResponse` | 3.0 → Admin | id, tenantId, plan fields, status, quantity, period window, trialEnd, cancel flags, audit stamps |
| `RecordUsageRequest` | System → 4.0 | metric, quantity, occurredAt?, idempotencyKey?, source? |
| `UsageEventMessage` | 4.0 → D8 → 5.0 | tenantId, subscriptionId, metric, quantity, idempotencyKey, source, occurredAt |
| `UsageAcceptedResponse` | 4.0 → System | subscriptionId, metric, quantity, occurredAt, idempotencyKey, topic, partition, offset |
| `UsageSummaryResponse` | 6.0 → Member | subscriptionId, planCode, period window, currency, per-metric summaries, estimatedOverageTotal |
| `InvoiceResponse` | 6.0 → Admin | id, subscriptionId, invoiceNumber, status, currency, subtotal, taxAmount, total, amountPaid, amountDue, period, issuedAt, dueAt, paidAt, lineItems |
| `ApiResponse` | any → Client | success, message, data { errorCode, path, timestamp, optional fieldErrors / errorId } |

### Data stores

| ID | Store | Kind | Retention |
|---|---|---|---|
| D1 | `tenants` | Relational, permanent | Indefinite |
| D2 | `app_users` | Relational, permanent | Indefinite |
| D3 | `plans`, `plan_metered_rates` | Relational, reference data | Migration-managed |
| D4 | `subscriptions` | Relational, mutable aggregate | Indefinite |
| D5 | `invoices`, `invoice_line_items` | Relational, immutable once issued | Indefinite (financial record) |
| D6 | `usage_events` | Relational, append-only ledger | Indefinite; grows fastest |
| D7 | `usage_counters` | Relational, roll-up | One row per subscription × metric × period |
| D8 | `billing.usage-events.v1` | Kafka topic, 6 partitions | 7 days (`app.kafka.usage-events.retention`) |
| D9 | `billing.usage-events.v1.DLT` | Kafka topic, 1 partition | 7 days |

### Configuration inputs

| Prefix | Binding type | Controls |
|---|---|---|
| `app.security` | `SecurityProperties` | JWT secret, issuer, TTL, clock skew, claim names, public paths |
| `app.billing` | `BillingProperties` | Currency, money/usage scale, rounding mode, proration toggle, tax rate, invoice terms |
| `app.kafka` | `KafkaTopicProperties` | Topic name, partitions, replicas, retention, retry budget, DLT suffix |

All three are `@Validated` records bound by constructor. **No `@Value` appears anywhere in the
codebase** — a misconfigured environment fails at startup rather than at the first request that
happens to need the missing value.

---

## 18. Cross-references to source

| Concern | Where it lives |
|---|---|
| Tenant discriminator + write stamp | `entity/TenantAwareEntity.java` |
| Filter activation and advice ordering | `security/TenantFilterAspect.java`, `config/JpaConfig.java` |
| Token issue/verify, tenant claim | `security/JwtService.java` |
| Context binding and mandatory clear | `security/JwtAuthenticationFilter.java`, `security/TenantContextHolder.java` |
| Proration arithmetic | `service/billing/ProrationCalculator.java` |
| Invoice assembly and rounding | `service/billing/InvoiceAssembler.java` |
| Late-event period attribution | `service/billing/BillingPeriodResolver.java` |
| Blocking publish + 503 semantics | `messaging/UsageEventProducer.java` |
| Idempotent ingestion | `service/impl/UsageIngestionServiceImpl.java` |
| Poison-pill containment, DLT | `config/KafkaConfig.java` |
| Schema, constraints, indexes | `resources/db/migration/V1__init_schema.sql` |
| Error contract | `common/GlobalExceptionHandler.java` |
