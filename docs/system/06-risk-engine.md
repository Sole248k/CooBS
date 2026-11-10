# 6. Risk engine

`risk-engine` (port 8092, internal only) decides whether a transfer may proceed and holds the member's daily-limit reservation. It is called only by `orchestration-service`, with a 150 ms time limit; the design target is p95 < 40 ms.

Code: `backend/risk-engine/src/main/java/com/group2/fse/risk_engine/service/RiskEvaluator.java`, `VelocityCounter.java`, `LimitPolicy.java`.

## 6.1 Inputs (R1 `POST /internal/v1/risk/evaluate`)

The orchestrator builds the request from the transfer and the member's risk profile (C7 from `customer-service`):

```json
{
  "transactionId": "6f1c0e0a-…",
  "customerId": 1,
  "amount": "2500.00",
  "currency": "PHP",
  "channel": "APP",
  "customerRiskScore": "0.15",
  "kycTier": "TIER_3_FULL",
  "dailyLimit": "100000.00",
  "isNewBeneficiary": false
}
```

Optional inputs the model also understands (used by tests and the scenario scripts): `transfersInLast10Min`, `reversalsToday`, `isNewDevice`, `deviceFingerprint`, `latitude`, `longitude`, `isDormantAccount`, `daysSinceLastActivity`, `isStructuringSuspected`, `avgTransactionAmount`, `stdDevTransactionAmount`.

## 6.2 Daily limit

The limit is the profile's `dailyLimit` if given, otherwise by KYC tier:

| Tier | Daily limit (PHP) |
| --- | --- |
| `TIER_1_BASIC` | 5,000.00 |
| `TIER_2_STANDARD` (default) | 50,000.00 |
| `TIER_3_FULL` | 500,000.00 |

(Seeded members carry explicit limits: 100,000 for most `TIER_3_FULL`, 25,000 for `ricardo_magbanua`, 5,000 for `grace_pangilinan`.)

The check `usedToday + amount <= dailyLimit` is first read, then **enforced atomically by the reservation** (6.5). A transaction that already holds a reservation (an interrupted saga asking again) passes the limit check without counting twice. The business day is the date in `Asia/Manila`.

## 6.3 Fraud score

```
S = 0.35·S_amount + 0.35·S_velocity + 0.30·S_tier
    + S_beneficiary + S_reversal + S_device + S_geo + S_dormant + S_behavioural
```

| Component | Value | Reason code added |
| --- | --- | --- |
| `S_amount` | `min(1, amount / dailyLimit)`; 1.00 if amount ≥ ₱500,000 | `RULE_AMLA_MANDATORY_THRESHOLD` (≥ ₱500,000) |
| `S_velocity` (transfers in last 10 min) | 0 → 0.00, 1 → 0.25, 2 → 0.50, 3 → 0.75, ≥ 4 → 1.00 | `VELOCITY_ELEVATED_10MIN` (2–3), `BURST_VELOCITY_LIMIT_EXCEEDED` (≥ 4) |
| `S_tier` | `TIER_3`/`FULL` 0.10, `TIER_2` 0.50, `TIER_1`/`BASIC` 1.00 (default 0.50) | — |
| `S_beneficiary` | +0.15 if first transfer to this account | `NEW_BENEFICIARY_FLAG` |
| `S_reversal` | +0.25 if ≥ 2 reversals today | `RULE_EXCESSIVE_DAILY_REVERSALS` |
| `S_device` | +0.20 if new or unrecognised device fingerprint | `UNRECOGNIZED_DEVICE_HARDWARE` |
| `S_geo` | +0.35 if travel from the last location implies > 800 km/h; score floored at 0.75 | `IMPOSSIBLE_TRAVEL_ANOMALY` |
| `S_dormant` | +0.30 if dormant (flag or ≥ 90 days idle) and amount ≥ ₱5,000 | `DORMANT_ACCOUNT_SUDDEN_ACTIVITY` |
| Structuring | Amount ₱400,000–₱499,999.99 with any recent velocity, or pushing the day's total past ₱500,000: `S_amount = 1`, final score 1.00 | `RULE_AMLA_STRUCTURING_SUSPECTED` |
| `S_behavioural` | +0.25 if amount is ≥ 3σ above the member's mean (σ defaults to 40% of the mean) | `BEHAVIORAL_AMOUNT_ANOMALY` |

The score is clamped to [0, 1]. Then the **member's own risk score is a floor**: the transfer is never rated safer than the member who sends it (reason `CUSTOMER_RISK_RATING` when that floor is ≥ 0.60).

Worked example (seed member `john_doe`, ₱2,500, first transfer of the window, known beneficiary, tier 3, limit ₱100,000, member risk 0.15): `0.35·0.025 + 0 + 0.30·0.10 = 0.039` → floored to 0.15 → APPROVE.

## 6.4 Decision

| Score | Fraud outcome | Reason prefix |
| --- | --- | --- |
| < 0.60 | `PASSED` | — |
| 0.60 – 0.85 | `FAILED` | `REVIEW_REQUIRED` |
| > 0.85 | `FAILED` | `HIGH_RISK` |

`decision = APPROVE` only if the limit check passed **and** the fraud outcome is `PASSED`; otherwise `DECLINE`. The fraud outcome follows the score alone and the limit outcome the limit alone, so the saga can report `LIMIT_EXCEEDED` (stage 5) separately from `RISK_DECLINED` (stage 4). An approval carries no reasons except the AMLA threshold flag.

Response:

```json
{
  "evaluationId": "eval-1a2b3c4d",
  "transactionId": "6f1c0e0a-…",
  "decision": "APPROVE",
  "score": "0.18",
  "fraudCheck": { "outcome": "PASSED", "reasons": [] },
  "limitCheck": { "outcome": "PASSED", "usedToday": "5000.00", "dailyLimit": "100000.00" },
  "evaluatedInMs": 12
}
```

## 6.5 Counters in Redis

Money is counted in whole ten-thousandths of a peso (no floating point).

| Key | Holds | TTL |
| --- | --- | --- |
| `risk:velocity:{customerId}` | Approved transfers in a fixed 10-minute window that opens with the first one (`INCR`, expiry set on creation) | 10 min |
| `risk:daily:reserved:{customerId}:{date}` | Amount reserved today | 24 h |
| `risk:daily:count:{customerId}:{date}` | Transfers today | 24 h |
| `risk:daily:reversals:{customerId}:{date}` | Reversals today | 24 h |
| `risk:counted:{transactionId}` | `<date>|<units>|<customerId>` — what a release gives back | 24 h |
| `risk:reversed:{transactionId}` | Marker so a repeated reversal report is not counted twice | 24 h |
| `risk:devices:{customerId}` | Recognised device fingerprints | 90 days |
| `risk:geo:last:{customerId}` | Last location and time | 24 h |
| `risk:stats:{customerId}` | Running mean and standard deviation of amounts | 365 days |

**Reservation.** On APPROVE the amount is added first and the new total compared with the limit, so two simultaneous transfers cannot both take the last of the room: the one that goes over takes its amount back and is declined (`LIMIT_EXCEEDED`). A transaction is reserved once however often it is evaluated.

**Release (R2 `FAILED`).** Gives the reserved amount back; the velocity count stays (an attempt is an attempt). Repeating it changes nothing.

**Reversal (R2 `REVERSED`).** Adds one to today's reversal count for the sender; gives nothing back.

**Redis unreachable.** The engine throws `RiskUnavailableException` → HTTP 503 `RISK_UNAVAILABLE`. It never approves on the guess that nothing was used today. The saga treats this exactly like a timeout: transfer fails at stage 4, no hold.

## 6.6 Seeded risk scenarios

| Member | Profile | Result |
| --- | --- | --- |
| `andrea_reyes`, `gabriel_florendo` | Risk 0.92 / 0.91 | Every transfer `RISK_DECLINED`, `HIGH_RISK` |
| `jose_bautista`, `marveen_bernabe` | Risk 0.72 / 0.74 | `RISK_DECLINED`, `REVIEW_REQUIRED` |
| `grace_pangilinan` | Tier 1, limit 5,000 | Over 5,000 → `LIMIT_EXCEEDED` |
| Any member, 5th transfer in 10 minutes | Velocity ≥ 4 | Declined on score (`scripts/test_risk_scenarios.ps1 -Scenario Burst`) |

## 6.7 Offline model tuning

`scripts/risk_ml_batch_trainer.py` demonstrates a "shadow mode" offline pipeline that benchmarks and tunes the weights outside the critical path. It does not change the running engine; the weights above are fixed in code. `scripts/benchmark_risk_eval.ps1|.sh` measures R1 latency.

## 6.8 Authentication

R1 and R2 require `ROLE_SERVICE`, either from a service JWT or from the header `X-Internal-Secret`/`X-API-Key` matching `INTERNAL_SERVICE_SECRET`. The property has a default value in code; see [section 14](14-known-gaps-and-limitations.md).
