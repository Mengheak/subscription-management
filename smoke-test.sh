#!/usr/bin/env bash
# End-to-end smoke test against a running `docker compose up` stack.
# Exercises onboarding, subscription lifecycle, Kafka usage ingestion, invoicing,
# proration and - most importantly - cross-tenant isolation.
set -uo pipefail

BASE="${BASE:-http://localhost:8080/api/v1}"
STAMP=$(date +%s)
PASS=0
FAIL=0

j() { python -c "import sys,json;d=json.load(sys.stdin)['data'];print(eval(sys.argv[1]))" "$1" 2>/dev/null; }

check() { # check <description> <actual> <expected>
  if [ "$2" = "$3" ]; then
    echo "  PASS  $1 ($2)"; PASS=$((PASS+1))
  else
    echo "  FAIL  $1 -- expected '$3', got '$2'"; FAIL=$((FAIL+1))
  fi
}

echo "== 1. Register two tenants =="
A=$(curl -s -X POST "$BASE/auth/register" -H 'Content-Type: application/json' -d "{
  \"tenantName\":\"Acme Corporation\",\"slug\":\"acme-$STAMP\",
  \"adminEmail\":\"admin@acme-$STAMP.example\",\"adminFullName\":\"Dara Sok\",
  \"adminPassword\":\"S3cure-Passw0rd!\"}")
TOKEN_A=$(echo "$A" | j "d['accessToken']")
TENANT_A=$(echo "$A" | j "d['tenantId']")

B=$(curl -s -X POST "$BASE/auth/register" -H 'Content-Type: application/json' -d "{
  \"tenantName\":\"Globex\",\"slug\":\"globex-$STAMP\",
  \"adminEmail\":\"admin@globex-$STAMP.example\",\"adminFullName\":\"Sokha Chan\",
  \"adminPassword\":\"An0ther-Passw0rd!\"}")
TOKEN_B=$(echo "$B" | j "d['accessToken']")

check "tenant A got a token" "$([ -n "$TOKEN_A" ] && echo yes)" "yes"
check "tenant B got a token" "$([ -n "$TOKEN_B" ] && echo yes)" "yes"
check "role is TENANT_ADMIN" "$(echo "$A" | j "d['role']")" "TENANT_ADMIN"

echo "== 2. Login round trip =="
LOGIN=$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"admin@acme-$STAMP.example\",\"password\":\"S3cure-Passw0rd!\"}")
check "login returns the same tenant" "$(echo "$LOGIN" | j "d['tenantId']")" "$TENANT_A"

BAD=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"admin@acme-$STAMP.example\",\"password\":\"wrong-password\"}")
check "wrong password is 401" "$BAD" "401"

echo "== 3. Subscribe tenant A to PRO, quantity 2 =="
SUB=$(curl -s -X POST "$BASE/subscriptions" -H "Authorization: Bearer $TOKEN_A" \
  -H 'Content-Type: application/json' -d '{"planCode":"PRO","quantity":2,"skipTrial":true}')
SUB_ID=$(echo "$SUB" | j "d['id']")
check "status is ACTIVE"      "$(echo "$SUB" | j "d['status']")"   "ACTIVE"
check "plan is PRO"           "$(echo "$SUB" | j "d['planCode']")" "PRO"
check "quantity is 2"         "$(echo "$SUB" | j "d['quantity']")" "2"

DUP=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/subscriptions" \
  -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' \
  -d '{"planCode":"STARTER","quantity":1,"skipTrial":true}')
check "second live subscription rejected" "$DUP" "409"

echo "== 4. TENANT ISOLATION: B must not see A's subscription =="
ISO=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/subscriptions/$SUB_ID" -H "Authorization: Bearer $TOKEN_B")
check "cross-tenant read is 404, not 403" "$ISO" "404"

LIST_B=$(curl -s "$BASE/subscriptions" -H "Authorization: Bearer $TOKEN_B")
check "B's subscription list is empty" "$(echo "$LIST_B" | j "d['totalElements']")" "0"

NOAUTH=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/subscriptions/$SUB_ID")
check "unauthenticated read is 401" "$NOAUTH" "401"

echo "== 5. Report usage through Kafka =="
USAGE=$(curl -s -w '\n%{http_code}' -X POST "$BASE/usage/events" -H "Authorization: Bearer $TOKEN_A" \
  -H 'Content-Type: application/json' \
  -d "{\"metric\":\"API_CALLS\",\"quantity\":612500,\"idempotencyKey\":\"smoke-$STAMP-1\",\"source\":\"smoke-test\"}")
check "usage accepted (202)" "$(echo "$USAGE" | tail -1)" "202"

echo "   waiting for the consumer to fold the event into the counter..."
TOTAL=""
for _ in $(seq 1 20); do
  sleep 1
  SUMMARY=$(curl -s "$BASE/usage/subscriptions/$SUB_ID/summary" -H "Authorization: Bearer $TOKEN_A")
  TOTAL=$(echo "$SUMMARY" | j "[m for m in d['metrics'] if m['metric']=='API_CALLS'][0]['totalQuantity']")
  [ "$TOTAL" = "612500.0" ] && break
done
check "counter updated by the consumer" "$TOTAL" "612500.0"
check "overage computed"  "$(echo "$SUMMARY" | j "[m for m in d['metrics'] if m['metric']=='API_CALLS'][0]['overageQuantity']")" "112500.0"
check "overage cost 112500 x 0.00015" "$(echo "$SUMMARY" | j "d['estimatedOverageTotal']")" "16.88"

echo "== 6. IDEMPOTENCY: replay the same key =="
curl -s -o /dev/null -X POST "$BASE/usage/events" -H "Authorization: Bearer $TOKEN_A" \
  -H 'Content-Type: application/json' \
  -d "{\"metric\":\"API_CALLS\",\"quantity\":612500,\"idempotencyKey\":\"smoke-$STAMP-1\",\"source\":\"smoke-test\"}"
sleep 4
SUMMARY2=$(curl -s "$BASE/usage/subscriptions/$SUB_ID/summary" -H "Authorization: Bearer $TOKEN_A")
check "duplicate did not double-count" \
  "$(echo "$SUMMARY2" | j "[m for m in d['metrics'] if m['metric']=='API_CALLS'][0]['totalQuantity']")" "612500.0"

echo "== 7. Invoice the period: 99 x 2 base + 16.88 overage =="
INV=$(curl -s -X POST "$BASE/invoices/generate?subscriptionId=$SUB_ID" -H "Authorization: Bearer $TOKEN_A")
check "subtotal" "$(echo "$INV" | j "d['subtotal']")" "214.88"
check "total"    "$(echo "$INV" | j "d['total']")"    "214.88"
check "two lines" "$(echo "$INV" | j "len(d['lineItems'])")" "2"
INV_ID=$(echo "$INV" | j "d['id']")

AGAIN=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/invoices/generate?subscriptionId=$SUB_ID" \
  -H "Authorization: Bearer $TOKEN_A")
check "double-billing blocked" "$AGAIN" "409"

PAID=$(curl -s -X POST "$BASE/invoices/$INV_ID/pay" -H "Authorization: Bearer $TOKEN_A")
check "invoice paid" "$(echo "$PAID" | j "d['status']")" "PAID"
check "nothing outstanding" "$(echo "$PAID" | j "d['amountDue']")" "0.0"

echo "== 8. Upgrade mid-period: proration invoice =="
UP=$(curl -s -X POST "$BASE/subscriptions/$SUB_ID/change-plan" -H "Authorization: Bearer $TOKEN_A" \
  -H 'Content-Type: application/json' -d '{"planCode":"ENTERPRISE","quantity":2}')
check "moved to ENTERPRISE" "$(echo "$UP" | j "d['planCode']")" "ENTERPRISE"

PRORATION=$(curl -s "$BASE/invoices?size=1" -H "Authorization: Bearer $TOKEN_A")
check "a proration invoice was issued" \
  "$(echo "$PRORATION" | j "d['content'][0]['total'] > 0")" "True"

echo "== 9. Cancel at period end =="
CANCEL=$(curl -s -X POST "$BASE/subscriptions/$SUB_ID/cancel" -H "Authorization: Bearer $TOKEN_A" \
  -H 'Content-Type: application/json' -d '{"atPeriodEnd":true,"reason":"smoke test"}')
check "still live until period end" "$(echo "$CANCEL" | j "d['status']")" "ACTIVE"
check "flagged for cancellation"    "$(echo "$CANCEL" | j "d['cancelAtPeriodEnd']")" "True"

echo "== 10. Error contract =="
V=$(curl -s -X POST "$BASE/subscriptions" -H "Authorization: Bearer $TOKEN_B" \
  -H 'Content-Type: application/json' -d '{"planCode":"","quantity":0}')
check "validation envelope carries an errorCode" "$(echo "$V" | j "d['errorCode']")" "VALIDATION_FAILED"
NF=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/does-not-exist" -H "Authorization: Bearer $TOKEN_A")
check "unknown endpoint is 404, not 500" "$NF" "404"

echo
echo "==================================="
echo "  passed: $PASS    failed: $FAIL"
echo "==================================="
[ "$FAIL" -eq 0 ]
