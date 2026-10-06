#!/usr/bin/env bash
set -euo pipefail

# Accept image name from Jenkins (e.g., the-commit-crew:42)
APP_IMAGE="${1:-the-commit-crew:latest}"
ENV=${2:-dev}
ENV_FILE="${3:-.env.${ENV}}"

if [ ! -f "$ENV_FILE" ]; then
  echo "ERROR: $ENV_FILE not found"
  exit 1
fi

# Load environment variables directly from file (avoid bash interpretation of special chars)
POSTGRES_DB=$(grep "^POSTGRES_DB=" "$ENV_FILE" | cut -d'=' -f2 | tr -d '\r')
POSTGRES_PASSWORD=$(grep "^POSTGRES_PASSWORD=" "$ENV_FILE" | cut -d'=' -f2 | tr -d '\r')

NETWORK=the-commit-crew_default
APP_CONTAINER=the-commit-crew-app
APP_PORT=8081

# Accept postgres container name from parameter
POSTGRES_CONTAINER="${4:-the_commit_crew-db-1}"

echo "Using postgres container: $POSTGRES_CONTAINER"

# Verify postgres container exists
if ! docker ps --filter "name=${POSTGRES_CONTAINER}" --quiet >/dev/null 2>&1; then
  echo "ERROR: Postgres container '${POSTGRES_CONTAINER}' not found or not running"
  echo "Running containers:"
  docker ps
  exit 1
fi

# Get the network postgres is on
POSTGRES_NETWORK=$(docker inspect "$POSTGRES_CONTAINER" --format='{{range $k,$v := .NetworkSettings.Networks}}{{$k}}{{end}}')
echo "Postgres is on network: $POSTGRES_NETWORK"

# Wait for postgres to be ready
echo "== Waiting for Postgres to be ready =="
for i in $(seq 1 30); do
  if docker exec "$POSTGRES_CONTAINER" pg_isready -U postgres >/dev/null 2>&1; then
    echo "Postgres is ready"
    break
  fi
  if [ $i -lt 30 ]; then
    echo "Waiting for postgres... (attempt $i/30)"
    sleep 1
  fi
done

cleanup() {
  echo "== Teardown =="
  docker rm -f "$APP_CONTAINER" >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "== Stage: Run Container =="
docker run -d --name "$APP_CONTAINER" --network "$POSTGRES_NETWORK" -p "$APP_PORT:$APP_PORT" \
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://${POSTGRES_CONTAINER}:5432/${POSTGRES_DB}" \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD="${POSTGRES_PASSWORD}" \
  -e SPRING_PROFILES_ACTIVE=test \
  -e AUTH_ENABLED=false \
  "$APP_IMAGE"

sleep 2

echo "== Stage: Wait for Service Ready =="
for i in $(seq 1 60); do
  code=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:$APP_PORT/accounts/ACC-1001" || true)
  if [ "$code" != "000" ]; then 
    echo "Service is ready (HTTP $code)"
    break
  fi
  if [ $i -lt 60 ]; then
    echo "Waiting... (attempt $i/60, got HTTP $code)"
    sleep 2
  fi
done

echo "== Stage: Application Logs =="
docker logs "$APP_CONTAINER" 2>&1 | tail -100

echo "== Stage: Test Account Retrieval =="
RESPONSE=$(curl -s "http://localhost:$APP_PORT/accounts/ACC-1001")
echo "Full response: $RESPONSE"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:$APP_PORT/accounts/ACC-1001")
echo "HTTP code: $HTTP_CODE"
echo "$RESPONSE" | grep -q '"status":"ACTIVE"' || { echo "FAIL: account not found"; exit 1; }
echo "PASS: account retrieved from database"

echo "== Stage: Test Get Account Balance =="
BALANCE_RESPONSE=$(curl -s "http://localhost:$APP_PORT/accounts/ACC-1001/balance")
echo "Balance response: $BALANCE_RESPONSE"
echo "$BALANCE_RESPONSE" | grep -q '"accountId":"ACC-1001"' || { echo "FAIL: balance endpoint failed"; exit 1; }
echo "$BALANCE_RESPONSE" | grep -q '"cashBalance"' || { echo "FAIL: cashBalance not in response"; exit 1; }
echo "PASS: account balance retrieved"

echo "== Stage: Test Place Order - Validation =="
CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "http://localhost:$APP_PORT/api/v1/orders" \
  -H "Content-Type: application/json" \
  -d '{"symbol":"","quantity":-10,"price":0}')
if [ "$CODE" != "400" ]; then
  echo "FAIL: expected 400 for invalid data, got $CODE"
  exit 1
fi
echo "PASS: bean validation caught invalid request (400)"

echo "== Stage: Test Place Order - Success =="
ORDER_RESPONSE=$(curl -s -X POST "http://localhost:$APP_PORT/api/v1/orders" \
  -H "Content-Type: application/json" \
  -d "{\"accountId\":\"ACC-1001\",\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":10,\"price\":150.00,\"idempotencyKey\":\"order-$(date +%s%N)\"}"
echo "Order response: $ORDER_RESPONSE"

echo "$ORDER_RESPONSE" | grep -q '"status":"FILLED"' || { echo "FAIL: order was not created"; exit 1; }
echo "PASS: order placed and persisted"

echo "== Stage: Test Get Account Orders =="
ORDERS_RESPONSE=$(curl -s "http://localhost:$APP_PORT/accounts/ACC-1001/orders")
echo "Orders response (with data): $ORDERS_RESPONSE"
echo "$ORDERS_RESPONSE" | grep -q '"symbol":"AAPL"' || { echo "FAIL: order not in response"; exit 1; }
echo "PASS: orders endpoint returns placed order"

echo "== Stage: Test Get Account Positions =="
POSITIONS_RESPONSE=$(curl -s "http://localhost:$APP_PORT/accounts/ACC-1001/positions")
echo "Positions response (with data): $POSITIONS_RESPONSE"
echo "$POSITIONS_RESPONSE" | grep -q '"symbol":"AAPL"' || { echo "FAIL: position not in response"; exit 1; }
echo "$POSITIONS_RESPONSE" | grep -q '"averageCost"' || { echo "FAIL: averageCost not in response"; exit 1; }
echo "PASS: positions endpoint returns position"

echo "== Stage: Test Position Price and PnL Fields =="
if echo "$POSITIONS_RESPONSE" | grep -q '"currentPrice"'; then
  echo "PASS: currentPrice field present"
else
  echo "FAIL: currentPrice field missing"
  exit 1
fi

if echo "$POSITIONS_RESPONSE" | grep -q '"marketValue"'; then
  echo "PASS: marketValue field present"
else
  echo "FAIL: marketValue field missing"
  exit 1
fi

if echo "$POSITIONS_RESPONSE" | grep -q '"unrealizedPnL"'; then
  echo "PASS: unrealizedPnL field present"
else
  echo "FAIL: unrealizedPnL field missing"
  exit 1
fi

if echo "$POSITIONS_RESPONSE" | grep -q '"unrealizedPnLPercent"'; then
  echo "PASS: unrealizedPnLPercent field present"
else
  echo "FAIL: unrealizedPnLPercent field missing"
  exit 1
fi

echo "== Stage: Verify Data in Postgres =="
docker exec -e PGPASSWORD="${POSTGRES_PASSWORD}" "$POSTGRES_CONTAINER" psql -U postgres -d "${POSTGRES_DB}" -c \
  "SELECT account_id, symbol, quantity, average_cost FROM positions WHERE account_id="ACC-1001" AND symbol='AAPL';"

echo "== Stage: Verify Price History Data in Postgres =="
docker exec -e PGPASSWORD="${POSTGRES_PASSWORD}" "$POSTGRES_CONTAINER" psql -U postgres -d "${POSTGRES_DB}" -c \
  "SELECT symbol, price_date, close_price FROM price_history WHERE symbol='AAPL' ORDER BY price_date DESC LIMIT 1;"

echo "== ALL STAGES PASSED =="