package com.chheang.mengheak.subscriptionmanager.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BillingApplicationIT {
  @Container
  static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:16-alpine");

  @Container static final KafkaContainer BROKER = new KafkaContainer("apache/kafka:3.9.1");

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
    registry.add("spring.datasource.username", DATABASE::getUsername);
    registry.add("spring.datasource.password", DATABASE::getPassword);
    registry.add("spring.kafka.bootstrap-servers", BROKER::getBootstrapServers);
    registry.add(
        "app.security.jwt.secret",
        () -> "aW50ZWdyYXRpb24tdGVzdC1vbmx5LXNlY3JldC1rZXktMzItYnl0ZXM=");
  }

  @Autowired TestRestTemplate http;
  @Autowired JdbcTemplate jdbc;
  @Autowired com.chheang.mengheak.subscriptionmanager.service.MerchantService merchantService;

  @Test
  void billingLifecycleWithKafkaAndTenantIsolation() {
    JsonNode owner = register();
    JsonNode other = register();
    String token = owner.path("accessToken").asText();
    String otherToken = other.path("accessToken").asText();
    JsonNode emptyCurrent =
        data(call(HttpMethod.GET, "/subscriptions/current", null, otherToken), 200);
    assertThat(emptyCurrent.isMissingNode() || emptyCurrent.isNull()).isTrue();
    JsonNode subscription =
        data(
            call(
                HttpMethod.POST,
                "/subscriptions",
                Map.of("planCode", "PRO", "quantity", 2, "skipTrial", true),
                token),
            201);
    String id = subscription.path("id").asText();
    assertThat(subscription.path("status").asText()).isEqualTo("ACTIVE");
    assertThat(
            data(call(HttpMethod.GET, "/subscriptions/current", null, token), 200)
                .path("id")
                .asText())
        .isEqualTo(id);
    subscription = data(call(HttpMethod.GET, "/subscriptions/" + id, null, token), 200);
    error(call(HttpMethod.GET, "/subscriptions/" + id, null, otherToken), 404);
    error(
        call(
            HttpMethod.POST,
            "/subscriptions/" + id + "/cancel",
            Map.of("atPeriodEnd", false),
            otherToken),
        404);
    assertThat(
            data(call(HttpMethod.GET, "/subscriptions", null, otherToken), 200)
                .path("totalElements")
                .asInt())
        .isZero();
    error(
        call(
            HttpMethod.POST,
            "/subscriptions",
            Map.of("planCode", "PRO", "quantity", 1, "skipTrial", true),
            token),
        409);
    String key = UUID.randomUUID().toString();
    Map<String, Object> usage =
        Map.of("metric", "API_CALLS", "quantity", 612500, "idempotencyKey", key);
    data(call(HttpMethod.POST, "/usage/events", usage, token), 202);
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(summary(id, token).path("estimatedOverageTotal").decimalValue())
                    .isEqualByComparingTo("16.88"));
    data(call(HttpMethod.POST, "/usage/events", usage, token), 202);
    // A second distinct event on the same partition acts as a barrier for the duplicate.
    data(
        call(
            HttpMethod.POST,
            "/usage/events",
            Map.of(
                "metric",
                "API_CALLS",
                "quantity",
                1,
                "idempotencyKey",
                UUID.randomUUID().toString()),
            token),
        202);
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              JsonNode metrics = summary(id, token).path("metrics");
              for (JsonNode metric : metrics)
                if (metric.path("metric").asText().equals("API_CALLS")) {
                  assertThat(metric.path("totalQuantity").decimalValue())
                      .isEqualByComparingTo("612501");
                  return;
                }
              throw new AssertionError("API_CALLS counter missing");
            });
    error(call(HttpMethod.GET, "/usage/subscriptions/" + id + "/summary", null, otherToken), 404);
    JsonNode invoice =
        data(call(HttpMethod.POST, "/invoices/generate?subscriptionId=" + id, null, token), 201);
    assertThat(invoice.path("total").decimalValue()).isEqualByComparingTo("214.88");
    assertThat(invoice.path("lineItems").size()).isEqualTo(2);
    String invoiceId = invoice.path("id").asText();
    error(call(HttpMethod.GET, "/invoices/" + invoiceId, null, otherToken), 404);
    error(call(HttpMethod.POST, "/invoices/" + invoiceId + "/pay", null, otherToken), 404);
    error(call(HttpMethod.POST, "/invoices/generate?subscriptionId=" + id, null, token), 409);
    assertThat(
            data(call(HttpMethod.POST, "/invoices/" + invoiceId + "/pay", null, token), 200)
                .path("status")
                .asText())
        .isEqualTo("PAID");
    error(call(HttpMethod.POST, "/invoices/" + invoiceId + "/pay", null, token), 409);
    JsonNode upgraded =
        data(
            call(
                HttpMethod.POST,
                "/subscriptions/" + id + "/change-plan",
                Map.of("planCode", "ENTERPRISE"),
                token),
            200);
    assertThat(upgraded.path("planCode").asText()).isEqualTo("ENTERPRISE");
    assertThat(upgraded.path("currentPeriodEnd")).isEqualTo(subscription.path("currentPeriodEnd"));
    assertThat(
            data(call(HttpMethod.GET, "/invoices", null, token), 200).path("totalElements").asInt())
        .isEqualTo(2);
    assertThat(
            data(
                    call(
                        HttpMethod.POST,
                        "/subscriptions/" + id + "/cancel",
                        Map.of("atPeriodEnd", true),
                        token),
                    200)
                .path("cancelAtPeriodEnd")
                .asBoolean())
        .isTrue();
    assertThat(
            data(
                    call(
                        HttpMethod.POST,
                        "/subscriptions/" + id + "/cancel",
                        Map.of("atPeriodEnd", false),
                        token),
                    200)
                .path("status")
                .asText())
        .isEqualTo("CANCELED");
  }

  @Test
  void authenticationAndCurrentAccountState() {
    JsonNode user = register();
    String token = user.path("accessToken").asText();
    UUID userId = UUID.fromString(user.path("userId").asText());
    UUID tenantId = UUID.fromString(user.path("tenantId").asText());
    String email =
        jdbc.queryForObject("select email from app_users where id = ?", String.class, userId);
    assertThat(
            data(
                    call(
                        HttpMethod.POST,
                        "/auth/login",
                        Map.of("email", email, "password", "S3cure-Passw0rd!"),
                        null),
                    200)
                .path("tenantId")
                .asText())
        .isEqualTo(tenantId.toString());
    error(
        call(
            HttpMethod.POST,
            "/auth/login",
            Map.of("email", email, "password", "bad-password"),
            null),
        401);
    data(call(HttpMethod.GET, "/tenants/me", null, token), 200);
    jdbc.update("update app_users set role = 'TENANT_MEMBER' where id = ?", userId);
    error(
        call(HttpMethod.POST, "/subscriptions", Map.of("planCode", "PRO", "quantity", 1), token),
        403);
    jdbc.update("update app_users set enabled = false where id = ?", userId);
    error(call(HttpMethod.GET, "/tenants/me", null, token), 401);
    jdbc.update("update app_users set enabled = true where id = ?", userId);
    jdbc.update("update tenants set status = 'SUSPENDED' where id = ?", tenantId);
    error(call(HttpMethod.GET, "/tenants/me", null, token), 401);
  }

  @Test
  void apiContractAndValidation() {
    data(call(HttpMethod.GET, "/plans", null, null), 200);
    data(call(HttpMethod.GET, "/plans/PRO", null, null), 200);
    error(call(HttpMethod.GET, "/tenants/me", null, null), 401);
    error(call(HttpMethod.GET, "/tenants/me", null, "invalid"), 401);
    JsonNode user = register();
    String token = user.path("accessToken").asText();
    JsonNode invalid =
        error(
            call(HttpMethod.POST, "/subscriptions", Map.of("planCode", "", "quantity", 0), token),
            400);
    assertThat(invalid.path("fieldErrors").has("planCode")).isTrue();
    error(call(HttpMethod.POST, "/auth/register", "{", null), 400);
    error(call(HttpMethod.GET, "/subscriptions/not-a-uuid", null, token), 400);
    error(call(HttpMethod.GET, "/subscriptions?sort=doesNotExist", null, token), 400);
    error(call(HttpMethod.POST, "/invoices/generate", null, token), 400);
    error(call(HttpMethod.GET, "/missing", null, token), 404);
    error(call(HttpMethod.DELETE, "/plans", null, token), 405);
    ResponseEntity<JsonNode> spec = http.getForEntity("/v3/api-docs", JsonNode.class);
    assertThat(spec.getStatusCode().value()).isEqualTo(200);
    assertThat(spec.getBody().path("components").path("schemas").toString())
        .contains("success", "message");
  }

  @Test
  void corsAllowsConfiguredOriginsOnly() {
    HttpHeaders headers = new HttpHeaders();
    headers.setOrigin("http://localhost:3000");
    headers.setAccessControlRequestMethod(HttpMethod.POST);
    var allowed =
        http.exchange(
            "/api/v1/subscriptions", HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class);
    assertThat(allowed.getHeaders().getAccessControlAllowOrigin())
        .isEqualTo("http://localhost:3000");
    headers.setOrigin("https://untrusted.example");
    var denied =
        http.exchange(
            "/api/v1/subscriptions", HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class);
    assertThat(denied.getStatusCode().value()).isEqualTo(403);
    assertThat(denied.getHeaders().getAccessControlAllowOrigin()).isNull();
  }

  private JsonNode summary(String id, String token) {
    return data(call(HttpMethod.GET, "/usage/subscriptions/" + id + "/summary", null, token), 200);
  }

  @Test
  void merchantCompanyCanSellAndIntegrateWithoutCrossTenantAccess() {
    JsonNode owner = register();
    String token = owner.path("accessToken").asText();
    error(call(HttpMethod.POST, "/merchant/products", Map.of("name", "Project app"), token), 409);
    assertThat(
            data(call(HttpMethod.GET, "/merchant/onboarding", null, token), 200)
                .path("unlocked")
                .asBoolean())
        .isFalse();
    data(
        call(
            HttpMethod.POST,
            "/subscriptions",
            Map.of("planCode", "PRO", "quantity", 1, "skipTrial", true),
            token),
        201);
    assertThat(
            data(call(HttpMethod.GET, "/merchant/onboarding", null, token), 200)
                .path("unlocked")
                .asBoolean())
        .isTrue();
    JsonNode product =
        data(
            call(
                HttpMethod.POST,
                "/merchant/products",
                Map.of("name", "Project app", "description", "Acme's product"),
                token),
            201);
    String productId = product.path("id").asText();
    JsonNode price =
        data(
            call(
                HttpMethod.POST,
                "/merchant/prices",
                Map.of(
                    "productId",
                    productId,
                    "name",
                    "Business",
                    "amount",
                    10,
                    "currency",
                    "USD",
                    "billingCycle",
                    "MONTHLY",
                    "trialDays",
                    0,
                    "features",
                    java.util.List.of("projects", "exports"),
                    "meteredRates",
                    java.util.List.of(
                        Map.of("metric", "API_CALLS", "includedQuantity", 100, "unitPrice", 0.1))),
                token),
            201);
    JsonNode key =
        data(
            call(
                HttpMethod.POST,
                "/merchant/api-keys",
                Map.of(
                    "name",
                    "Website backend",
                    "expiresInDays",
                    30,
                    "scopes",
                    java.util.List.of(
                        "CATALOG_READ",
                        "CUSTOMERS_READ",
                        "CUSTOMERS_WRITE",
                        "SUBSCRIPTIONS_READ",
                        "SUBSCRIPTIONS_WRITE",
                        "ACCESS_READ",
                        "USAGE_WRITE",
                        "INVOICES_READ")),
                token),
            201);
    String secret = key.path("secret").asText();
    assertThat(secret).startsWith("sm_live_");
    assertThat(data(call(HttpMethod.GET, "/merchant/api-keys", null, token), 200).toString())
        .doesNotContain(secret, "tokenHash");
    assertThat(
            jdbc.queryForObject(
                "select token_hash from merchant_api_keys where id=?",
                String.class,
                UUID.fromString(key.path("key").path("id").asText())))
        .isNotEqualTo(secret);
    error(machine(HttpMethod.POST, "/merchant/products", Map.of("name", "Forbidden"), secret), 403);
    JsonNode customer =
        data(
            machine(
                HttpMethod.POST,
                "/merchant/customers",
                Map.of("externalId", "user-42", "name", "Jane", "email", "jane@example.com"),
                secret),
            201);
    Map<String, Object> request =
        Map.of(
            "customerId",
            customer.path("id").asText(),
            "priceId",
            price.path("id").asText(),
            "quantity",
            2,
            "skipTrial",
            true,
            "idempotencyKey",
            "sub-42");
    JsonNode sub = data(machine(HttpMethod.POST, "/merchant/subscriptions", request, secret), 201);
    String id = sub.path("id").asText();
    assertThat(
            data(machine(HttpMethod.POST, "/merchant/subscriptions", request, secret), 201)
                .path("id")
                .asText())
        .isEqualTo(id);
    assertThat(
            data(
                    machine(
                        HttpMethod.GET,
                        "/merchant/access?customerExternalId=user-42&productId="
                            + productId
                            + "&feature=exports",
                        null,
                        secret),
                    200)
                .path("allowed")
                .asBoolean())
        .isTrue();
    assertThat(
            data(
                    machine(
                        HttpMethod.GET,
                        "/merchant/access?customerExternalId=user-42&productId="
                            + productId
                            + "&feature=unlisted",
                        null,
                        secret),
                    200)
                .path("allowed")
                .asBoolean())
        .isFalse();
    Map<String, Object> event =
        Map.of(
            "subscriptionId",
            id,
            "metric",
            "API_CALLS",
            "quantity",
            150,
            "idempotencyKey",
            "event-42");
    data(machine(HttpMethod.POST, "/merchant/usage/events", event, secret), 200);
    data(machine(HttpMethod.POST, "/merchant/usage/events", event, secret), 200);
    assertThat(
            data(
                    machine(
                        HttpMethod.GET, "/merchant/subscriptions/" + id + "/usage", null, secret),
                    200)
                .path("estimatedOverageTotal")
                .decimalValue())
        .isEqualByComparingTo("5.00");
    error(
        machine(
            HttpMethod.POST,
            "/merchant/usage/events",
            Map.of(
                "subscriptionId",
                id,
                "metric",
                "API_CALLS",
                "quantity",
                151,
                "idempotencyKey",
                "event-42"),
            secret),
        409);
    JsonNode bills =
        data(machine(HttpMethod.GET, "/merchant/invoices", null, secret), 200).path("content");
    assertThat(bills.size()).isEqualTo(1);
    assertThat(bills.get(0).path("total").decimalValue()).isEqualByComparingTo("20.00");
    String invoiceId = bills.get(0).path("id").asText();
    error(machine(HttpMethod.POST, "/merchant/invoices/" + invoiceId + "/pay", null, secret), 403);
    data(call(HttpMethod.POST, "/merchant/invoices/" + invoiceId + "/pay", null, token), 200);
    String other = register().path("accessToken").asText();
    assertThat(
            data(
                    machine(
                        HttpMethod.GET,
                        "/merchant/customers/lookup?externalId=user-42",
                        null,
                        secret),
                    200)
                .path("id")
                .asText())
        .isEqualTo(customer.path("id").asText());
    assertThat(
            data(
                    machine(
                        HttpMethod.GET,
                        "/merchant/invoices?customerId=" + customer.path("id").asText(),
                        null,
                        secret),
                    200)
                .path("totalElements")
                .asInt())
        .isEqualTo(1);
    JsonNode limitedKey =
        data(
            call(
                HttpMethod.POST,
                "/merchant/api-keys",
                Map.of(
                    "name",
                    "Access only",
                    "expiresInDays",
                    1,
                    "scopes",
                    java.util.List.of("ACCESS_READ")),
                token),
            201);
    String limitedSecret = limitedKey.path("secret").asText();
    error(machine(HttpMethod.GET, "/merchant/customers", null, limitedSecret), 403);
    data(
        machine(
            HttpMethod.GET,
            "/merchant/access?customerExternalId=user-42&productId=" + productId,
            null,
            limitedSecret),
        200);
    error(machine(HttpMethod.GET, "/merchant/api-keys", null, limitedSecret), 403);
    jdbc.update(
        "update merchant_api_keys set expires_at=now()-interval '1 hour' where id=?",
        UUID.fromString(limitedKey.path("key").path("id").asText()));
    error(
        machine(
            HttpMethod.GET,
            "/merchant/access?customerExternalId=user-42&productId=" + productId,
            null,
            limitedSecret),
        401);
    data(
        call(
            HttpMethod.POST,
            "/subscriptions",
            Map.of("planCode", "PRO", "quantity", 1, "skipTrial", true),
            other),
        201);
    error(
        call(HttpMethod.GET, "/merchant/customers/" + customer.path("id").asText(), null, other),
        404);
    error(call(HttpMethod.GET, "/merchant/subscriptions/" + id, null, other), 404);
    error(call(HttpMethod.GET, "/merchant/invoices/" + invoiceId, null, other), 404);
    data(
        machine(
            HttpMethod.POST,
            "/merchant/subscriptions/" + id + "/cancel",
            Map.of("atPeriodEnd", false),
            secret),
        200);
    assertThat(
            data(
                    machine(
                        HttpMethod.GET,
                        "/merchant/access?customerExternalId=user-42&productId=" + productId,
                        null,
                        secret),
                    200)
                .path("allowed")
                .asBoolean())
        .isFalse();
    assertThat(
            data(machine(HttpMethod.GET, "/merchant/invoices", null, secret), 200)
                .path("totalElements")
                .asInt())
        .isEqualTo(2);
    data(
        call(
            HttpMethod.DELETE,
            "/merchant/api-keys/" + key.path("key").path("id").asText(),
            null,
            token),
        200);
    error(machine(HttpMethod.GET, "/merchant/customers", null, secret), 401);
    error(machine(HttpMethod.GET, "/tenants/me", null, secret), 401);
  }

  private ResponseEntity<JsonNode> machine(
      HttpMethod method, String path, Object body, String key) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("X-API-Key", key);
    return http.exchange("/api/v1" + path, method, new HttpEntity<>(body, headers), JsonNode.class);
  }

  @Test
  void merchantTrialsRenewalCancellationAndOverdueAccessAreEnforced() {
    JsonNode owner = register();
    String token = owner.path("accessToken").asText();
    UUID tenant = UUID.fromString(owner.path("tenantId").asText());
    data(
        call(
            HttpMethod.POST,
            "/subscriptions",
            Map.of("planCode", "PRO", "quantity", 1, "skipTrial", true),
            token),
        201);
    JsonNode product =
        data(
            call(HttpMethod.POST, "/merchant/products", Map.of("name", "Lifecycle product"), token),
            201);
    String productId = product.path("id").asText();
    JsonNode price =
        data(
            call(
                HttpMethod.POST,
                "/merchant/prices",
                Map.of(
                    "productId",
                    productId,
                    "name",
                    "Monthly with trial",
                    "amount",
                    5,
                    "currency",
                    "USD",
                    "billingCycle",
                    "MONTHLY",
                    "trialDays",
                    1,
                    "features",
                    java.util.List.of("exports"),
                    "meteredRates",
                    java.util.List.of()),
                token),
            201);
    JsonNode customer =
        data(
            call(
                HttpMethod.POST,
                "/merchant/customers",
                Map.of(
                    "externalId",
                    "lifecycle-customer",
                    "name",
                    "Lifecycle",
                    "email",
                    "lifecycle@example.com"),
                token),
            201);
    JsonNode sub =
        data(
            call(
                HttpMethod.POST,
                "/merchant/subscriptions",
                Map.of(
                    "customerId",
                    customer.path("id").asText(),
                    "priceId",
                    price.path("id").asText(),
                    "quantity",
                    1,
                    "skipTrial",
                    false,
                    "idempotencyKey",
                    "trial-one"),
                token),
            201);
    UUID id = UUID.fromString(sub.path("id").asText());
    assertThat(sub.path("status").asText()).isEqualTo("TRIALING");
    assertThat(
            data(call(HttpMethod.GET, "/merchant/invoices", null, token), 200)
                .path("totalElements")
                .asInt())
        .isZero();
    jdbc.update(
        "update customer_subscriptions set period_start=now()-interval '2"
            + " days',period_end=now()-interval '1 day' where id=?",
        id);
    merchantService.advance(tenant, id);
    assertThat(
            data(call(HttpMethod.GET, "/merchant/subscriptions/" + id, null, token), 200)
                .path("status")
                .asText())
        .isEqualTo("ACTIVE");
    merchantService.advance(tenant, id); // Another worker pass cannot double-bill a period.
    JsonNode bills =
        data(call(HttpMethod.GET, "/merchant/invoices", null, token), 200).path("content");
    assertThat(bills.size()).isEqualTo(1);
    UUID invoiceId = UUID.fromString(bills.get(0).path("id").asText());
    jdbc.update("update customer_invoices set due_at=now()-interval '1 day' where id=?", invoiceId);
    assertThat(
            data(
                    call(
                        HttpMethod.GET,
                        "/merchant/access?customerExternalId=lifecycle-customer&productId="
                            + productId,
                        null,
                        token),
                    200)
                .path("allowed")
                .asBoolean())
        .isFalse();
    data(call(HttpMethod.POST, "/merchant/invoices/" + invoiceId + "/pay", null, token), 200);
    assertThat(
            data(
                    call(
                        HttpMethod.GET,
                        "/merchant/access?customerExternalId=lifecycle-customer&productId="
                            + productId,
                        null,
                        token),
                    200)
                .path("allowed")
                .asBoolean())
        .isTrue();
    data(
        call(
            HttpMethod.POST,
            "/merchant/subscriptions/" + id + "/cancel",
            Map.of("atPeriodEnd", true),
            token),
        200);
    jdbc.update(
        "update customer_subscriptions set period_start=now()-interval '2"
            + " days',period_end=now()-interval '1 day' where id=?",
        id);
    merchantService.advance(tenant, id);
    assertThat(
            data(call(HttpMethod.GET, "/merchant/subscriptions/" + id, null, token), 200)
                .path("status")
                .asText())
        .isEqualTo("CANCELED");
    assertThat(
            data(call(HttpMethod.GET, "/merchant/invoices", null, token), 200)
                .path("totalElements")
                .asInt())
        .isEqualTo(1);
  }

  private JsonNode register() {
    String suffix = UUID.randomUUID().toString();
    return data(
        call(
            HttpMethod.POST,
            "/auth/register",
            Map.of(
                "tenantName",
                "Integration tenant",
                "slug",
                "tenant-" + suffix,
                "adminEmail",
                suffix + "@example.com",
                "adminFullName",
                "Test Admin",
                "adminPassword",
                "S3cure-Passw0rd!"),
            null),
        201);
  }

  private ResponseEntity<JsonNode> call(HttpMethod method, String path, Object body, String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (token != null) headers.setBearerAuth(token);
    return http.exchange("/api/v1" + path, method, new HttpEntity<>(body, headers), JsonNode.class);
  }

  private JsonNode data(ResponseEntity<JsonNode> response, int status) {
    assertThat(response.getStatusCode().value()).as(response.toString()).isEqualTo(status);
    assertThat(response.getBody().path("success").asBoolean()).isTrue();
    assertThat(response.getHeaders().getFirst("X-Request-ID")).isNotBlank();
    return response.getBody().path("data");
  }

  private JsonNode error(ResponseEntity<JsonNode> response, int status) {
    assertThat(response.getStatusCode().value()).as(response.toString()).isEqualTo(status);
    assertThat(response.getBody().path("success").asBoolean()).isFalse();
    assertThat(response.getBody().path("data").path("errorCode").asText()).isNotBlank();
    return response.getBody().path("data");
  }
}
