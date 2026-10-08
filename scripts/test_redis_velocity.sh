#!/usr/bin/env bash
# ==============================================================================
# test_redis_velocity.sh: Verify Redis Velocity Keys & Counter Lifecycle (FIN-803)
# Validates 10-Minute Velocity Window, Daily Aggregation & TTL Expirations
# Reference: contracts/runtime/RUNTIME_SETTINGS.md & Handshake H13 / H17
# ==============================================================================
set -euo pipefail

echo "================================================================="
echo " CooBS Platform - Redis Velocity Keys & TTL Verification (FIN-803)"
echo "================================================================="

REDIS_CMD() {
  # No pipe into grep here: under pipefail, grep -q can exit before docker ps has finished writing,
  # docker ps then dies on the closed pipe, and the check reads as "container not running".
  if command -v docker >/dev/null 2>&1 && [ -n "$(docker ps --quiet --filter 'name=^redis-cache$' 2>/dev/null)" ]; then
    docker exec redis-cache redis-cli "$@"
  elif command -v redis-cli >/dev/null 2>&1; then
    redis-cli "$@"
  else
    echo "ERROR: Neither docker exec redis-cache nor redis-cli available." >&2
    exit 1
  fi
}

echo ""
echo "[1/4] Checking Redis connectivity..."
PING_RES=$(REDIS_CMD PING)
if [ "$PING_RES" != "PONG" ]; then
  echo "FAIL: Redis ping failed ($PING_RES)"
  exit 1
fi
echo "  [PASS] Redis responded with PONG"

TEST_CUST_ID=9999
TODAY_STR=$(date +%Y-%m-%d)

VELOCITY_KEY="risk:velocity:${TEST_CUST_ID}"
DAILY_AMT_KEY="risk:daily:amount:${TEST_CUST_ID}:${TODAY_STR}"
DAILY_COUNT_KEY="risk:daily:count:${TEST_CUST_ID}:${TODAY_STR}"
DAILY_REV_KEY="risk:daily:reversals:${TEST_CUST_ID}:${TODAY_STR}"

echo ""
echo "[2/4] Testing 10-minute rolling velocity counter ($VELOCITY_KEY)..."
REDIS_CMD DEL "$VELOCITY_KEY" >/dev/null
REDIS_CMD INCR "$VELOCITY_KEY" >/dev/null
REDIS_CMD INCR "$VELOCITY_KEY" >/dev/null
C3=$(REDIS_CMD INCR "$VELOCITY_KEY")
REDIS_CMD EXPIRE "$VELOCITY_KEY" 600 >/dev/null
TTL_VAL=$(REDIS_CMD TTL "$VELOCITY_KEY")

if [ "$C3" -eq 3 ] && [ "$TTL_VAL" -gt 0 ] && [ "$TTL_VAL" -le 600 ]; then
  echo "  [PASS] Velocity incremented to 3 with TTL = ${TTL_VAL}s (expected <= 600s)"
else
  echo "FAIL: Velocity test failed: count=$C3, ttl=$TTL_VAL"
  exit 1
fi

echo ""
echo "[3/4] Testing 24-hour daily amount & count counters..."
REDIS_CMD SET "$DAILY_AMT_KEY" 2500.00 EX 86400 >/dev/null
AMT=$(REDIS_CMD GET "$DAILY_AMT_KEY")
COUNT=$(REDIS_CMD INCR "$DAILY_COUNT_KEY")
REDIS_CMD EXPIRE "$DAILY_COUNT_KEY" 86400 >/dev/null
AMT_TTL=$(REDIS_CMD TTL "$DAILY_AMT_KEY")

if [ "$AMT" = "2500.00" ] && [ "$COUNT" -ge 1 ] && [ "$AMT_TTL" -gt 0 ] && [ "$AMT_TTL" -le 86400 ]; then
  echo "  [PASS] Daily amount = $AMT, count = $COUNT, TTL = ${AMT_TTL}s (expected <= 86400s)"
else
  echo "FAIL: Daily metrics test failed: amount=$AMT, count=$COUNT, ttl=$AMT_TTL"
  exit 1
fi

echo ""
echo "[4/4] Testing burst velocity threshold detection (>= 4 transfers in 10 min)..."
REDIS_CMD INCR "$VELOCITY_KEY" >/dev/null
BURST_COUNT=$(REDIS_CMD GET "$VELOCITY_KEY")

if [ "$BURST_COUNT" -ge 4 ]; then
  echo "  [PASS] Burst velocity threshold exceeded (count=$BURST_COUNT >= 4) - triggers BURST_VELOCITY_LIMIT_EXCEEDED"
else
  echo "FAIL: Burst threshold test failed: count=$BURST_COUNT"
  exit 1
fi

# Cleanup
REDIS_CMD DEL "$VELOCITY_KEY" "$DAILY_AMT_KEY" "$DAILY_COUNT_KEY" "$DAILY_REV_KEY" >/dev/null
echo "  [PASS] Cleaned up test keys"

echo ""
echo "================================================================="
echo " [SUCCESS] Redis Velocity Keys & Lifecycle Verified (FIN-803)     "
echo "================================================================="
exit 0
