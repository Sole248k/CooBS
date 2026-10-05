#!/usr/bin/env python3
"""
CooBS seed generator - one source for every datastore.

Run from the repository root:

    python database/seed/generate_seed.py

It writes:

    database/next/oracle/            rebuilt Oracle schema + seed   (not mounted yet)
    database/next/postgres/          on-premises PostgreSQL: audit store, batch schema + seed
    database/postgres-cloud/02..05     identity_db, customer_db, transaction_db, notification_db
    database/seed/SEED_REFERENCE.md    the ID table and scenario map everyone builds against

Nothing here is typed twice. Customers, accounts and the money history are defined once
below; balances, ledger rows, audit rows, timelines and notifications are derived from
them, and the generator refuses to write if its invariants do not hold.

Owner: Dev 5. Timestamps are relative to the moment the database is created, so
"due today" items are always due and "last 30 days" always has data.
"""
import os
import shutil
import uuid
import hashlib
from decimal import Decimal as D

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DB = os.path.join(ROOT, "database")

# BCrypt of the shared demo password. Same hash the existing seed uses.
PWD_HASH = "$2a$10$i3NuBhqny.i6sVVFkD1aDuXRLkZJe.b4/BK3bt/VRpkZQTZEFkcUy"
OUR_BANK = "COOBS"
PARTNER_BANK = "BANKB"
DAY = 1440  # minutes

# ----------------------------------------------------------------------------
# 1. SOURCE DATA
# ----------------------------------------------------------------------------
# Staff: user_id is what the token carries as userId for staff.
STAFF = [
    # user_id, username, role, email, status
    (1, "admin", "ROLE_ADMIN", "admin@corebank.local", "ACTIVE"),
    (2, "teller_alice", "ROLE_TELLER", "alice@corebank.local", "ACTIVE"),
    (3, "teller_bob", "ROLE_TELLER", "bob@corebank.local", "ACTIVE"),
]
ROLES = [(1, "ROLE_ADMIN"), (2, "ROLE_TELLER"), (3, "ROLE_CUSTOMER"), (4, "ROLE_SERVICE")]

# Customers. customer_id is what the token carries as userId for customers.
# Customers 1-3 are unchanged from the original seed so existing tests keep working.
CUSTOMERS = [
    dict(id=1, username="john_doe", first="John", mi="D", last="Doe",
         address="123 Ayala Avenue, Makati City, Metro Manila", civil="SINGLE",
         occupation="Software Engineer", mobile="+639171234567", email="john.doe@example.com",
         tier="TIER_3_FULL", risk="0.15", credit=750, limit="100000.00", login="ACTIVE",
         scenario="Baseline sender. Untouched balances for the existing test suites."),
    dict(id=2, username="maria_santos", first="Maria", mi="S", last="Santos",
         address="456 BGC Boulevard, Taguig City, Metro Manila", civil="MARRIED",
         occupation="Finance Manager", mobile="+639189876543", email="maria.santos@example.com",
         tier="TIER_3_FULL", risk="0.10", credit=790, limit="100000.00", login="ACTIVE",
         scenario="Baseline recipient."),
    dict(id=3, username="david_kim", first="David", mi="K", last="Kim",
         address="789 Ortigas Center, Pasig City, Metro Manila", civil="SINGLE",
         occupation="Data Architect", mobile="+639205551234", email="david.kim@example.com",
         tier="TIER_3_FULL", risk="0.18", credit=735, limit="100000.00", login="ACTIVE",
         scenario="High balance. Used by the JMeter concurrency test."),
    dict(id=4, username="juan_delacruz", first="Juan", mi="P", last="Dela Cruz",
         address="12 Mabini Street, Brgy. Poblacion, Quezon City, Metro Manila", civil="MARRIED",
         occupation="Sari-sari Store Owner", mobile="+639175550104", email="juan.delacruz@example.com",
         tier="TIER_3_FULL", risk="0.12", credit=780, limit="100000.00", login="ACTIVE",
         scenario="Customer 360 showcase: savings, current and loan accounts, 30 days of history, "
                  "beneficiaries, a standing instruction and a loan instalment due today."),
    dict(id=5, username="andrea_reyes", first="Andrea", mi="L", last="Reyes",
         address="88 Colon Street, Cebu City, Cebu", civil="SINGLE",
         occupation="Online Seller", mobile="+639285550105", email="andrea.reyes@example.com",
         tier="TIER_3_FULL", risk="0.92", credit=540, limit="100000.00", login="ACTIVE",
         scenario="Risk score above 0.85: every transfer is declined as HIGH_RISK."),
    dict(id=6, username="jose_bautista", first="Jose Mari", mi="C", last="Bautista",
         address="5 Rizal Avenue, Brgy. San Roque, Antipolo City, Rizal", civil="MARRIED",
         occupation="Tricycle Operator", mobile="+639065550106", email="jose.bautista@example.com",
         tier="TIER_3_FULL", risk="0.72", credit=640, limit="100000.00", login="ACTIVE",
         scenario="Risk score in the 0.60 to 0.85 band: declined as REVIEW_REQUIRED. "
                  "Has a pending KYC update request."),
    dict(id=7, username="liza_mercado", first="Liza", mi="M", last="Mercado",
         address="27 Magsaysay Drive, Olongapo City, Zambales", civil="SINGLE",
         occupation="Call Center Agent", mobile="+639455550107", email="liza.mercado@example.com",
         tier="TIER_3_FULL", risk="0.20", credit=610, limit="100000.00", login="ACTIVE",
         scenario="Low balance: insufficient funds. Her loan instalment and standing instruction "
                  "due today both fail in the end-of-day batch."),
    dict(id=8, username="ramon_villanueva", first="Ramon", mi="T", last="Villanueva",
         address="14 Burgos Street, Bacolod City, Negros Occidental", civil="WIDOWED",
         occupation="Retired Seafarer", mobile="+639195550108", email="ramon.villanueva@example.com",
         tier="TIER_3_FULL", risk="0.25", credit=700, limit="100000.00", login="ACTIVE",
         scenario="Frozen account with an active flag: ACCOUNT_NOT_ACTIVE, and closure is blocked."),
    dict(id=9, username="grace_pangilinan", first="Grace", mi="A", last="Pangilinan",
         address="31 MacArthur Highway, San Fernando City, Pampanga", civil="SINGLE",
         occupation="College Student", mobile="+639665550109", email="grace.pangilinan@example.com",
         tier="TIER_1_BASIC", risk="0.22", credit=600, limit="5000.00", login="ACTIVE",
         scenario="Basic tier with a PHP 5,000 daily limit: LIMIT_EXCEEDED. "
                  "Has an approved KYC update request and an inactive beneficiary."),
    dict(id=10, username="carmela_garcia", first="Carmela", mi="R", last="Garcia",
         address="9 Session Road, Baguio City, Benguet", civil="MARRIED",
         occupation="Overseas Filipino Worker", mobile="+639275550110", email="carmela.garcia@example.com",
         tier="TIER_3_FULL", risk="0.14", credit=720, limit="100000.00", login="ACTIVE",
         scenario="Dormant account: charged the service fee by the end-of-day batch."),
    dict(id=11, username="paolo_navarro", first="Paolo", mi="G", last="Navarro",
         address="63 J.P. Laurel Avenue, Davao City, Davao del Sur", civil="SINGLE",
         occupation="Freelance Graphic Designer", mobile="+639155550111", email="paolo.navarro@example.com",
         tier="TIER_3_FULL", risk="0.16", credit=705, limit="100000.00", login="ACTIVE",
         scenario="One closed account (closure approved) and one empty account with a pending closure request."),
    dict(id=12, username="teresita_ramos", first="Teresita", mi="B", last="Ramos",
         address="40 Real Street, Iloilo City, Iloilo", civil="WIDOWED",
         occupation="Public School Teacher", mobile="+639985550112", email="teresita.ramos@example.com",
         tier="TIER_3_FULL", risk="0.11", credit=765, limit="100000.00", login="ACTIVE",
         scenario="A transfer in flight with a RESERVED hold, and one transfer that was reversed."),
    dict(id=13, username="ricardo_magbanua", first="Ricardo", mi="N", last="Magbanua",
         address="7 Rizal Street, Puerto Princesa City, Palawan", civil="MARRIED",
         occupation="Fisherman", mobile="+639505550113", email="ricardo.magbanua@example.com",
         tier="TIER_2_STANDARD", risk="0.19", credit=660, limit="25000.00", login="ACTIVE",
         scenario="New member: no beneficiaries and no outgoing history. First-time beneficiary case."),
    dict(id=14, username="lourdes_salazar", first="Lourdes", mi="V", last="Salazar",
         address="22 Zamora Street, Tacloban City, Leyte", civil="MARRIED",
         occupation="Market Vendor", mobile="+639355550114", email="lourdes.salazar@example.com",
         tier="TIER_3_FULL", risk="0.17", credit=690, limit="100000.00", login="INACTIVE",
         scenario="Login is INACTIVE: sign-in is refused."),
]
BANK_CUSTOMER_ID = 900  # internal owner of the GL accounts; has no login and no KYC row

# Accounts: id, customer, number, type, status, opening amount, opened N days ago
ACCOUNTS = [
    (1, 1, "ACC_10000001", "SAVINGS", "ACTIVE", "50000.00", 180),
    (2, 2, "ACC_10000002", "CHECKING", "ACTIVE", "25000.00", 180),
    (3, 3, "ACC_10000003", "SAVINGS", "ACTIVE", "100000.00", 180),
    (4, 4, "ACC_10000004", "SAVINGS", "ACTIVE", "85000.00", 150),
    (5, 4, "ACC_10000005", "CURRENT", "ACTIVE", "40000.00", 150),
    (6, 4, "LN_10000006", "LOAN", "ACTIVE", "125000.00", 70),
    (7, 5, "ACC_10000007", "SAVINGS", "ACTIVE", "60000.00", 120),
    (8, 6, "ACC_10000008", "SAVINGS", "ACTIVE", "45000.00", 120),
    (9, 7, "ACC_10000009", "SAVINGS", "ACTIVE", "150.00", 90),
    (10, 7, "LN_10000010", "LOAN", "ACTIVE", "30000.00", 30),
    (11, 8, "ACC_10000011", "SAVINGS", "FROZEN", "20000.00", 200),
    (12, 9, "ACC_10000012", "SAVINGS", "ACTIVE", "30000.00", 60),
    (13, 10, "ACC_10000013", "SAVINGS", "DORMANT", "8050.00", 400),
    (14, 11, "ACC_10000014", "CHECKING", "CLOSED", None, 300),
    (15, 11, "ACC_10000015", "SAVINGS", "ACTIVE", None, 45),
    (16, 12, "ACC_10000016", "SAVINGS", "ACTIVE", "35000.00", 110),
    (17, 13, "ACC_10000017", "SAVINGS", "ACTIVE", "12500.00", 28),
    (18, 14, "ACC_10000018", "SAVINGS", "ACTIVE", "5000.00", 100),
]
# Internal GL accounts. Seeded with a large opening balance because BALANCE forbids
# a negative available balance and these accounts are debited.
GL_OPENING = D("1000000000.00")
GL = {
    "CASH": (9001, "GL_CASH_VAULT"),
    "INTEREST_EXPENSE": (9002, "GL_INTEREST_EXPENSE"),
    "FEE_INCOME": (9003, "GL_FEE_INCOME"),
    "LOAN_RECEIVABLE": (9004, "GL_LOAN_RECEIVABLE"),
    "SETTLEMENT_BANKB": (9005, "GL_SETTLEMENT_" + PARTNER_BANK),
}

# Money history, oldest first. mins = minutes before database creation.
# kind: POSTED | REVERSED | FAILED | INFLIGHT
HISTORY = [
    dict(ref="TX-SEED-0001", mins=25 * DAY + 600, src=4, dst=7, amount="2500.00", category="TRANSFER",
         channel="APP", remarks="Bayad sa hiniram", kind="POSTED"),
    dict(ref="TX-SEED-0002", mins=20 * DAY + 540, src=4, dst=17, amount="1200.00", category="BILLS",
         channel="APP", remarks="Hati sa kuryente at tubig", kind="POSTED"),
    dict(ref="TX-SEED-0003", mins=15 * DAY + 480, src=5, dst=8, amount="3800.00", category="TRANSFER",
         channel="APP", remarks="Bayad sa paninda", kind="POSTED"),
    dict(ref="EOD-SEED-LOAN-0001", mins=10 * DAY + 60, src=4, dst=GL["LOAN_RECEIVABLE"][0], amount="5450.00",
         category="LOAN_REPAYMENT", channel="BATCH", remarks="Loan instalment 1 of 24", kind="POSTED",
         memo=dict(src=6, dst=GL["LOAN_RECEIVABLE"][0], amount="5000.00", ref="EOD-SEED-LOANP-0001")),
    dict(ref="TX-SEED-0004", mins=7 * DAY + 420, src=4, dst=5, amount="10000.00", category="SAVINGS",
         channel="APP", remarks="Lipat sa current account", kind="POSTED"),
    dict(ref="EOD-SEED-INT-0001", mins=6 * DAY + 50, src=GL["INTEREST_EXPENSE"][0], dst=4, amount="11.64",
         category="OTHER", channel="BATCH", remarks="Daily savings interest", kind="POSTED"),
    dict(ref="EOD-SEED-FEE-0001", mins=6 * DAY + 49, src=13, dst=GL["FEE_INCOME"][0], amount="50.00",
         category="FEES", channel="BATCH", remarks="Dormant account service fee", kind="POSTED"),
    dict(ref="TX-SEED-0005", mins=5 * DAY + 300, src=16, dst=5, amount="1500.00", category="TRANSFER",
         channel="APP", remarks="Padala para sa handaan", kind="REVERSED",
         reversal=dict(ref="RV-SEED-0001", mins=4 * DAY + 200, reason="Customer dispute approved: sent to the wrong account", actor=2)),
    dict(ref="TX-SEED-0006", mins=3 * DAY + 240, src=7, dst=4, amount="5000.00", category="TRANSFER",
         channel="APP", remarks="Bayad sa order", kind="FAILED", fail_stage=4, fail_code="RISK_DECLINED",
         fail_detail="HIGH_RISK: score 0.92"),
    dict(ref="TX-SEED-0007", mins=3 * DAY + 180, src=8, dst=4, amount="4000.00", category="TRANSFER",
         channel="APP", remarks="Bayad sa pamasahe", kind="FAILED", fail_stage=4, fail_code="RISK_DECLINED",
         fail_detail="REVIEW_REQUIRED: score 0.72"),
    dict(ref="TX-SEED-0008", mins=2 * DAY + 360, src=9, dst=2, amount="1000.00", category="TRANSFER",
         channel="APP", remarks="Ambag sa regalo", kind="FAILED", fail_stage=6, fail_code="INSUFFICIENT_FUNDS",
         fail_detail="Available balance 150.00"),
    dict(ref="TX-SEED-0009", mins=2 * DAY + 120, src=12, dst=4, amount="8000.00", category="TRANSFER",
         channel="APP", remarks="Bayad sa matrikula", kind="FAILED", fail_stage=5, fail_code="LIMIT_EXCEEDED",
         fail_detail="Daily limit 5000.00"),
    dict(ref="TX-SEED-0010", mins=2 * DAY + 60, src=11, dst=4, amount="500.00", category="TRANSFER",
         channel="TELLER", remarks="Bayad sa renta", kind="FAILED", fail_stage=2, fail_code="ACCOUNT_NOT_ACTIVE",
         fail_detail="Source account is FROZEN", actor=2),
    dict(ref="IB-SEED-IN-0001", mins=1 * DAY + 500, src=GL["SETTLEMENT_BANKB"][0], dst=4, amount="7500.00",
         category="TRANSFER", channel="INTERBANK", remarks="Padala mula sa BANKB", kind="POSTED",
         interbank=dict(direction="INBOUND", message_id="5d2f0c5e-0c1b-4f6b-9a1e-000000000001",
                        counterparty_account="2000-3000-4000", counterparty_name="Rosalinda Dizon")),
    dict(ref="TX-SEED-0011", mins=1 * DAY + 400, src=5, dst=GL["SETTLEMENT_BANKB"][0], amount="2000.00",
         category="TRANSFER", channel="APP", remarks="Padala sa kapatid", kind="POSTED",
         interbank=dict(direction="OUTBOUND", message_id="5d2f0c5e-0c1b-4f6b-9a1e-000000000002",
                        counterparty_account="1000-2000-3000", counterparty_name="Ana Lim")),
    dict(ref="TX-SEED-0012", mins=1 * DAY + 90, src=5, dst=GL["CASH"][0], amount="3000.00", category="OTHER",
         channel="TELLER", remarks="Over-the-counter withdrawal", kind="POSTED", actor=2, single="DEBIT"),
    dict(ref="TX-SEED-0013", mins=35, src=16, dst=9, amount="3000.00", category="TRANSFER",
         channel="APP", remarks="Tulong sa gastusin", kind="INFLIGHT"),
]

# Flags, closure requests, KYC update requests (existing account-service features)
ACCOUNT_FLAGS = [
    dict(account=11, reason="Reported lost passbook; frozen pending identity re-verification",
         status="ACTIVE", flagged_by=2, mins=9 * DAY),
    dict(account=8, reason="Unusual number of small incoming transfers; reviewed and cleared",
         status="REMOVED", flagged_by=3, removed_by=1, mins=40 * DAY, removed_mins=38 * DAY),
]
CLOSURE_REQUESTS = [
    dict(account=14, reason="Consolidating into one account", status="APPROVED", approved_by=1,
         mins=12 * DAY, reviewed_mins=11 * DAY),
    dict(account=15, reason="No longer needed", status="PENDING", mins=1 * DAY),
    dict(account=11, reason="Moving abroad", status="REJECTED", approved_by=1,
         rejection="Account has an active flag", mins=8 * DAY, reviewed_mins=7 * DAY),
]
KYC_UPDATES = [
    dict(customer=6, new_address="18 Sumulong Highway, Brgy. Mayamot, Antipolo City, Rizal",
         status="PENDING", mins=2 * DAY),
    dict(customer=9, new_mobile="+639665559109", status="APPROVED", approved_by=2,
         mins=14 * DAY, reviewed_mins=13 * DAY),
]

# customer_db
BENEFICIARIES = [
    # customer, account_number, nickname, bank, active
    (1, "ACC_10000002", "Maria", OUR_BANK, True),
    (2, "ACC_10000001", "John", OUR_BANK, True),
    (4, "ACC_10000007", "Andrea (supplier)", OUR_BANK, True),
    (4, "ACC_10000008", "Mang Jose", OUR_BANK, True),
    (4, "ACC_10000017", "Kuya Ricardo", OUR_BANK, True),
    (4, "1000-2000-3000", "Ate Ana (BANKB)", PARTNER_BANK, True),
    (12, "ACC_10000005", "Juan Dela Cruz", OUR_BANK, True),
    (12, "ACC_10000009", "Liza", OUR_BANK, True),
    (9, "ACC_10000004", "Tito Juan", OUR_BANK, False),
    (7, "ACC_10000002", "Ma'am Maria", OUR_BANK, True),
]

# batch schema
STANDING = [
    # id, source, destination, amount, frequency, next run (days from today), active, note
    (1, 4, 5, "2000.00", "MONTHLY", 0, True, "Due today: executes"),
    (2, 9, 2, "500.00", "MONTHLY", 0, True, "Due today: fails, insufficient funds"),
    (3, 1, 2, "1000.00", "MONTHLY", 12, True, "Not due yet"),
    (4, 16, 4, "750.00", "WEEKLY", 0, False, "Inactive: skipped"),
]
LOAN_SCHEDULES = [
    # id, loan account, repayment account, due (days from today), principal, interest, status
    (1, 6, 4, -10, "5000.00", "450.00", "PAID"),
    (2, 6, 4, 0, "5000.00", "450.00", "DUE"),
    (3, 6, 4, 30, "5000.00", "450.00", "PENDING"),
    (4, 6, 4, 60, "5000.00", "450.00", "PENDING"),
    (5, 10, 9, 0, "2500.00", "250.00", "DUE"),
    (6, 10, 9, 30, "2500.00", "250.00", "PENDING"),
]
STEP_NAMES = ["INTEREST_ACCRUAL", "SERVICE_FEES", "STANDING_INSTRUCTIONS", "LOAN_AMORTIZATION",
              "GL_RECONCILIATION", "AUDIT_SEAL"]

STAGES = ["INITIATED", "VALIDATED", "AUTHENTICATED", "FRAUD_CHECK", "LIMIT_CHECK", "FUNDS_CHECK",
          "AUTHORIZED", "POSTED", "LEDGER_UPDATE", "NOTIFICATION", "RECONCILIATION"]

# ----------------------------------------------------------------------------
# 2. LEDGER SIMULATION
# ----------------------------------------------------------------------------
cust = {c["id"]: c for c in CUSTOMERS}
acct = {a[0]: dict(id=a[0], customer=a[1], number=a[2], type=a[3], status=a[4],
                   opening=D(a[5]) if a[5] else D("0"), opened=a[6]) for a in ACCOUNTS}
for key, (aid, number) in GL.items():
    acct[aid] = dict(id=aid, customer=BANK_CUSTOMER_ID, number=number, type="GL", status="ACTIVE",
                     opening=GL_OPENING, opened=730, gl=True)

ledger = {aid: (a["opening"] if a.get("gl") else D("0")) for aid, a in acct.items()}
held = {aid: D("0") for aid in acct}
txn_rows = []       # Oracle "TRANSACTION"
transfer_reqs = []  # Oracle TRANSFER_REQUEST
holds = []          # Oracle HOLD
outbox = []         # Oracle OUTBOX
is_gl = lambda aid: acct[aid].get("gl", False)


def add_row(account, ref, ttype, amount, mins, actor=None, reverses=None):
    prev = ledger[account]
    new = prev - amount if ttype in ("DEBIT", "REVERSAL_DEBIT") else prev + amount
    assert new >= 0, f"{ref}: account {account} would go negative ({new})"
    ledger[account] = new
    row = dict(id=len(txn_rows) + 1, account=account, ref=ref, type=ttype, amount=amount,
               prev=prev, new=new, mins=mins, actor=actor, reverses=reverses)
    txn_rows.append(row)
    return row


def post(ref, src, dst, amount, mins, actor=None):
    """One balanced posting: debit src, credit dst. Leg references follow the ledger's -D / -C rule."""
    d = add_row(src, ref + "-D", "DEBIT", amount, mins, actor)
    c = add_row(dst, ref + "-C", "CREDIT", amount, mins, actor)
    return d, c


# Opening deposits: cash GL -> customer account, oldest first.
for a in sorted([x for x in acct.values() if not x.get("gl")], key=lambda x: -x["opened"]):
    if a["opening"] > 0:
        n = a["id"]
        mins = a["opened"] * DAY
        if a["type"] == "LOAN":
            # Loan account balance = outstanding principal (memo). Booked against loan receivable.
            add_row(GL["LOAN_RECEIVABLE"][0], f"REF-LOAN-{n:03d}-D", "DEBIT", a["opening"], mins, 1)
            add_row(n, f"REF-LOAN-{n:03d}", "CREDIT", a["opening"], mins, 1)
        else:
            add_row(GL["CASH"][0], f"REF-INIT-{n:03d}-D", "DEBIT", a["opening"], mins, 1)
            add_row(n, f"REF-INIT-{n:03d}", "INITIAL_DEPOSIT", a["opening"], mins, 1)

NS = uuid.UUID("6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11")
for h in HISTORY:
    h["amount"] = D(h["amount"])
    h["transaction_id"] = str(uuid.uuid5(NS, h["ref"]))
    h["customer"] = None if is_gl(h["src"]) else acct[h["src"]]["customer"]
    if h["customer"] is None:
        h["customer"] = acct[h["dst"]]["customer"]

events = sorted(
    [("post", h["mins"], h) for h in HISTORY if h["kind"] in ("POSTED", "REVERSED")]
    + [("reverse", h["reversal"]["mins"], h) for h in HISTORY if h["kind"] == "REVERSED"]
    + [("hold", h["mins"], h) for h in HISTORY if h["kind"] == "INFLIGHT"],
    key=lambda e: -e[1])

for what, mins, h in events:
    if what == "post":
        d, c = post(h["ref"], h["src"], h["dst"], h["amount"], mins, h.get("actor"))
        h["debit_row"], h["credit_row"] = d, c
        if "memo" in h:
            m = h["memo"]
            post(m["ref"], m["src"], m["dst"], D(m["amount"]), mins)
        if h["channel"] != "BATCH" and not h.get("single"):
            transfer_reqs.append(dict(src=h["src"], dst=h["dst"], amount=h["amount"], ref=h["ref"],
                                      key=h["transaction_id"], status="COMPLETED", mins=mins,
                                      actor=h.get("actor")))
        outbox.append(dict(topic="ledger.transfer.completed.v1", key=str(h["src"]), ref=h["ref"],
                           txn=h["transaction_id"], mins=mins))
    elif what == "reverse":
        r = h["reversal"]
        rc = add_row(h["src"], r["ref"] + "-C", "REVERSAL_CREDIT", h["amount"], mins, r["actor"], h["debit_row"]["id"])
        rd = add_row(h["dst"], r["ref"] + "-D", "REVERSAL_DEBIT", h["amount"], mins, r["actor"], h["credit_row"]["id"])
        r["rows"] = (rc, rd)
        r["transaction_id"] = str(uuid.uuid5(NS, r["ref"]))
    elif what == "hold":
        assert ledger[h["src"]] - held[h["src"]] >= h["amount"]
        held[h["src"]] += h["amount"]
        holds.append(dict(id=len(holds) + 1, account=h["src"], txn=h["transaction_id"], ref=h["ref"],
                          amount=h["amount"], status="RESERVED", mins=mins, resolved=None))
        h["hold_id"] = len(holds)

# Historical holds that were captured or released, so every hold status is present.
for h in HISTORY:
    if h["kind"] in ("POSTED", "REVERSED") and h["channel"] in ("APP", "TELLER") and not h.get("single") \
            and not is_gl(h["src"]):
        holds.append(dict(id=len(holds) + 1, account=h["src"], txn=h["transaction_id"], ref=h["ref"],
                          amount=h["amount"], status="POSTED", mins=h["mins"], resolved=h["mins"]))
        h["hold_id"] = len(holds)
holds.append(dict(id=len(holds) + 1, account=4, txn=str(uuid.uuid5(NS, "TX-SEED-RELEASED")), ref="TX-SEED-RELEASED",
                  amount=D("900.00"), status="CANCELLED", mins=9 * DAY, resolved=9 * DAY - 1))

available = {aid: ledger[aid] - held[aid] for aid in acct}

# ----------------------------------------------------------------------------
# 3. INVARIANTS - refuse to write a seed that does not add up
# ----------------------------------------------------------------------------
DEBITS = ("DEBIT", "REVERSAL_DEBIT")
total_debit = sum(r["amount"] for r in txn_rows if r["type"] in DEBITS)
total_credit = sum(r["amount"] for r in txn_rows if r["type"] not in DEBITS)
assert total_debit == total_credit, f"debits {total_debit} != credits {total_credit}"
for aid in acct:
    assert available[aid] >= 0 and ledger[aid] >= available[aid], aid
assert ledger[14] == 0 and ledger[15] == 0, "closure accounts must be empty"
assert ledger[1] == D("50000") and ledger[2] == D("25000") and ledger[3] == D("100000"), "baseline accounts changed"
assert ledger[9] == D("150"), "low-balance account changed"
assert len({r["ref"] for r in txn_rows}) == len(txn_rows), "duplicate reference_no"
assert all(len(r["ref"]) <= 36 for r in txn_rows)
assert len({c["username"] for c in CUSTOMERS}) == len(CUSTOMERS)
for b in BENEFICIARIES:
    if b[3] == OUR_BANK:
        assert any(a["number"] == b[1] for a in acct.values()), b
for s in STANDING:
    assert s[1] in acct and s[2] in acct
for ls in LOAN_SCHEDULES:
    assert acct[ls[1]]["type"] == "LOAN" and acct[ls[2]]["customer"] == acct[ls[1]]["customer"]

# ----------------------------------------------------------------------------
# 4. SQL HELPERS
# ----------------------------------------------------------------------------
def q(s):
    return "NULL" if s is None else "'" + str(s).replace("'", "''") + "'"

def m4(v):
    return f"{D(v):.4f}"

def m2(v):
    return f"{D(v):,.2f}"

def ora_ts(mins):
    return "SYSTIMESTAMP" if not mins else f"SYSTIMESTAMP - NUMTODSINTERVAL({int(mins)}, 'MINUTE')"

def pg_ts(mins):
    return "now()" if not mins else f"now() - interval '{int(mins)} minutes'"

def n(v):
    return "NULL" if v is None else str(v)

def display_name(c):
    return f"{c['first']} {c['last'][0]}."

def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text.rstrip("\n") + "\n")
    print("wrote", os.path.relpath(path, ROOT))

HEADER = "-- GENERATED by database/seed/generate_seed.py. Do not edit by hand; change the generator and re-run.\n"

# ----------------------------------------------------------------------------
# 5. ORACLE DDL DEFINITION (Master System of Record - Complete 22-Table Architecture)
# ----------------------------------------------------------------------------
ORACLE_DDL = HEADER + """-- ==============================================================================
-- 02_create_tables.sql: Oracle Database DDL Schema (Complete 22-Table Architecture)
-- Architecture Role: Master System of Record (SoR) & Pessimistic Concurrency Engine
-- Reference: integrated-capstone-plans / CooBS Master Architecture
-- ==============================================================================
ALTER SESSION SET CONTAINER = XEPDB1;
ALTER SESSION SET CURRENT_SCHEMA = CORE_USER;

-- 1. ROLE TABLE
CREATE TABLE ROLE (
    role_id NUMBER(19) GENERATED BY DEFAULT ON NULL AS IDENTITY (START WITH 100) PRIMARY KEY,
    role_name VARCHAR2(50) NOT NULL UNIQUE
);

-- 2. CUSTOMER TABLE
CREATE TABLE CUSTOMER (
    customer_id NUMBER(19) GENERATED BY DEFAULT ON NULL AS IDENTITY (START WITH 1000) PRIMARY KEY,
    kyc_status VARCHAR2(20) DEFAULT 'VERIFIED' NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 3. APP_USER TABLE (Login Credentials & Staff / Customer Identity)
CREATE TABLE APP_USER (
    user_id NUMBER(19) GENERATED BY DEFAULT ON NULL AS IDENTITY (START WITH 5000) PRIMARY KEY,
    role_id NUMBER(19) NOT NULL,
    user_type VARCHAR2(20) NOT NULL CHECK (user_type IN ('CUSTOMER', 'STAFF', 'SERVICE')),
    customer_id NUMBER(19),
    username VARCHAR2(100) NOT NULL UNIQUE,
    password_hash VARCHAR2(255) NOT NULL,
    email VARCHAR2(150) NOT NULL UNIQUE,
    mfa_secret_ref VARCHAR2(255),
    status VARCHAR2(20) DEFAULT 'ACTIVE' NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_app_user_role FOREIGN KEY (role_id) REFERENCES ROLE(role_id),
    CONSTRAINT fk_app_user_customer FOREIGN KEY (customer_id) REFERENCES CUSTOMER(customer_id)
);

-- 4. KYC TABLE (Master Legal Record)
CREATE TABLE KYC (
    kyc_id NUMBER(19) GENERATED BY DEFAULT ON NULL AS IDENTITY (START WITH 1000) PRIMARY KEY,
    first_name VARCHAR2(100) NOT NULL,
    middle_initial VARCHAR2(10),
    last_name VARCHAR2(100) NOT NULL,
    address VARCHAR2(255) NOT NULL,
    civil_status VARCHAR2(50),
    occupation VARCHAR2(100),
    mobile_number VARCHAR2(30) NOT NULL,
    status VARCHAR2(20) DEFAULT 'VERIFIED' NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    customer_id NUMBER(19) NOT NULL UNIQUE,
    CONSTRAINT fk_kyc_customer FOREIGN KEY (customer_id) REFERENCES CUSTOMER(customer_id)
);

-- 5. CHANNEL_PROFILE TABLE (Digital Banking & Omnichannel Profile)
CREATE TABLE CHANNEL_PROFILE (
    customer_id NUMBER(19) PRIMARY KEY,
    membership_number VARCHAR2(20) NOT NULL UNIQUE,
    display_name VARCHAR2(100) NOT NULL,
    preferred_language VARCHAR2(10) DEFAULT 'en' NOT NULL,
    kyc_tier VARCHAR2(20) NOT NULL,
    kyc_status VARCHAR2(20) DEFAULT 'VERIFIED' NOT NULL,
    risk_score NUMBER(4, 2) NOT NULL CHECK (risk_score BETWEEN 0 AND 1),
    credit_score NUMBER(5) NOT NULL CHECK (credit_score BETWEEN 300 AND 850),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    username VARCHAR2(100),
    email VARCHAR2(150),
    daily_limit NUMBER(18, 4) DEFAULT 100000.0000,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_chan_profile_cust FOREIGN KEY (customer_id) REFERENCES CUSTOMER(customer_id)
);

-- 6. BENEFICIARY TABLE (Saved Transfer Recipients)
CREATE TABLE BENEFICIARY (
    beneficiary_id NUMBER(19) GENERATED BY DEFAULT ON NULL AS IDENTITY (START WITH 1000) PRIMARY KEY,
    customer_id NUMBER(19) NOT NULL,
    account_number VARCHAR2(34) NOT NULL,
    nickname VARCHAR2(60) NOT NULL,
    bank_code VARCHAR2(8) NOT NULL,
    active BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_beneficiary_cust FOREIGN KEY (customer_id) REFERENCES CHANNEL_PROFILE(customer_id),
    CONSTRAINT uq_beneficiary UNIQUE (customer_id, account_number, bank_code)
);

-- 7. ACCOUNT TABLE (Deposit, Checking, Loan & General Ledger Accounts)
CREATE TABLE ACCOUNT (
    account_id NUMBER(19) GENERATED BY DEFAULT ON NULL AS IDENTITY (START WITH 100000) PRIMARY KEY,
    customer_id NUMBER(19) NOT NULL,
    account_number VARCHAR2(34) NOT NULL UNIQUE,
    account_type VARCHAR2(30) NOT NULL,
    currency VARCHAR2(3) DEFAULT 'PHP' NOT NULL,
    status VARCHAR2(20) DEFAULT 'ACTIVE' NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_account_customer FOREIGN KEY (customer_id) REFERENCES CUSTOMER(customer_id)
);

-- 8. BALANCE TABLE (Pessimistic Concurrency Balance Cache)
CREATE TABLE BALANCE (
    balance_id NUMBER(19) GENERATED BY DEFAULT ON NULL AS IDENTITY (START WITH 100000) PRIMARY KEY,
    account_id NUMBER(19) NOT NULL UNIQUE,
    available_balance NUMBER(18, 4) DEFAULT 0.0000 NOT NULL,
    ledger_balance NUMBER(18, 4) DEFAULT 0.0000 NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_balance_account FOREIGN KEY (account_id) REFERENCES ACCOUNT(account_id),
    CONSTRAINT chk_ora_bal_non_neg CHECK (available_balance >= 0),
    CONSTRAINT chk_ora_ledger_ge_avail CHECK (ledger_balance >= available_balance)
);

-- 9. HOLD TABLE (Two-Phase Commitment Holds)
CREATE TABLE HOLD (
    hold_id NUMBER(19) GENERATED BY DEFAULT ON NULL AS IDENTITY (START WITH 100000) PRIMARY KEY,
    account_id NUMBER(19) NOT NULL,
    transaction_id VARCHAR2(36) NOT NULL,
    reference_no VARCHAR2(36) NOT NULL UNIQUE,
    amount NUMBER(18, 4) NOT NULL,
    status VARCHAR2(20) DEFAULT 'RESERVED' NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    resolved_at TIMESTAMP,
    CONSTRAINT fk_hold_account FOREIGN KEY (account_id) REFERENCES ACCOUNT(account_id),
    CONSTRAINT chk_ora_hold_pos CHECK (amount > 0)
);

-- 10. TRANSFER_REQUEST TABLE (Idempotent Orchestration Requests)
CREATE TABLE TRANSFER_REQUEST (
    request_id NUMBER(19) GENERATED BY DEFAULT ON NULL AS IDENTITY (START WITH 100000) PRIMARY KEY,
    source_account_id NUMBER(19) NOT NULL,
    destination_account_number VARCHAR2(34) NOT NULL,
    destination_bank_code VARCHAR2(8) NOT NULL,
    amount NUMBER(18, 4) NOT NULL,
    channel VARCHAR2(20) NOT NULL,
    status VARCHAR2(20) NOT NULL,
    id_reference_no VARCHAR2(36) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_transfer_req_account FOREIGN KEY (source_account_id) REFERENCES ACCOUNT(account_id),
    CONSTRAINT chk_ora_transfer_amt_pos CHECK (amount > 0)
);

-- 11. "TRANSACTION" TABLE (Immutable Posting Journal)
CREATE TABLE "TRANSACTION" (
    transaction_id NUMBER(19) GENERATED BY DEFAULT ON NULL AS IDENTITY (START WITH 100000) PRIMARY KEY,
    account_id NUMBER(19) NOT NULL,
    reference_no VARCHAR2(36) NOT NULL UNIQUE,
    transaction_type VARCHAR2(30) NOT NULL,
    amount NUMBER(18, 4) NOT NULL,
    previous_balance NUMBER(18, 4) NOT NULL,
    new_balance NUMBER(18, 4) NOT NULL,
    status VARCHAR2(20) DEFAULT 'COMPLETED' NOT NULL,
    reverses_transaction_id NUMBER(19),
    approved_by NUMBER(19),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_txn_account FOREIGN KEY (account_id) REFERENCES ACCOUNT(account_id),
    CONSTRAINT fk_txn_reverses FOREIGN KEY (reverses_transaction_id) REFERENCES "TRANSACTION"(transaction_id),
    CONSTRAINT uq_txn_reversed_once UNIQUE (reverses_transaction_id),
    CONSTRAINT chk_ora_txn_amount_pos CHECK (amount > 0),
    CONSTRAINT chk_ora_txn_new_bal_non_neg CHECK (new_balance >= 0)
);

-- 12. OUTBOX TABLE (Transactional Event Relay)
CREATE TABLE OUTBOX (
    outbox_id NUMBER(19) GENERATED BY DEFAULT ON NULL AS IDENTITY (START WITH 1000) PRIMARY KEY,
    topic VARCHAR2(100) NOT NULL,
    event_key VARCHAR2(100) NOT NULL,
    payload CLOB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    published_at TIMESTAMP
);

-- 13. TXN_TRANSACTION TABLE (Omnichannel Orchestrated Payment Life Cycle)
CREATE TABLE TXN_TRANSACTION (
    transaction_id VARCHAR2(36) PRIMARY KEY,
    reference_no VARCHAR2(36) NOT NULL UNIQUE,
    idempotency_key VARCHAR2(100) UNIQUE,
    channel VARCHAR2(20) NOT NULL,
    customer_id NUMBER(19),
    source_account_id NUMBER(19),
    destination_account_id NUMBER(19),
    destination_account_number VARCHAR2(34) NOT NULL,
    destination_bank_code VARCHAR2(8) NOT NULL,
    amount NUMBER(18, 4) NOT NULL,
    currency VARCHAR2(3) DEFAULT 'PHP' NOT NULL,
    category VARCHAR2(40) NOT NULL,
    remarks VARCHAR2(255),
    status VARCHAR2(20) NOT NULL,
    failure_code VARCHAR2(60),
    reverses_transaction_id VARCHAR2(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    completed_at TIMESTAMP
);

-- 14. SAGA_INSTANCE TABLE (Distributed Orchestration State)
CREATE TABLE SAGA_INSTANCE (
    saga_id VARCHAR2(36) PRIMARY KEY,
    transaction_id VARCHAR2(36) NOT NULL UNIQUE,
    reference_no VARCHAR2(40),
    state VARCHAR2(30) NOT NULL,
    hold_id NUMBER(19),
    risk_decision VARCHAR2(20),
    failure_reason VARCHAR2(255),
    failure_code VARCHAR2(60),
    payload CLOB,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_saga_txn FOREIGN KEY (transaction_id) REFERENCES TXN_TRANSACTION(transaction_id)
);

-- 15. TXN_STAGE_EVENT TABLE (Seven-Stage Pipeline Audit)
CREATE TABLE TXN_STAGE_EVENT (
    stage_event_id NUMBER(19) GENERATED BY DEFAULT ON NULL AS IDENTITY PRIMARY KEY,
    transaction_id VARCHAR2(36) NOT NULL,
    stage_no NUMBER(2) NOT NULL,
    stage_name VARCHAR2(40) NOT NULL,
    outcome VARCHAR2(20) NOT NULL,
    detail VARCHAR2(255),
    occurred_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_stage_txn FOREIGN KEY (transaction_id) REFERENCES TXN_TRANSACTION(transaction_id)
);

-- 16. LOAN_SCHEDULE TABLE (Amortization & Repayment Schedules)
CREATE TABLE LOAN_SCHEDULE (
    schedule_id NUMBER(19) GENERATED BY DEFAULT ON NULL AS IDENTITY (START WITH 1000) PRIMARY KEY,
    loan_account_id NUMBER(19) NOT NULL,
    repayment_account_id NUMBER(19) NOT NULL,
    due_date DATE NOT NULL,
    principal_due NUMBER(18, 4) NOT NULL,
    interest_due NUMBER(18, 4) NOT NULL,
    status VARCHAR2(20) NOT NULL,
    paid_at TIMESTAMP,
    CONSTRAINT fk_loan_sched_loan_acc FOREIGN KEY (loan_account_id) REFERENCES ACCOUNT(account_id),
    CONSTRAINT fk_loan_sched_repay_acc FOREIGN KEY (repayment_account_id) REFERENCES ACCOUNT(account_id)
);

-- 17. STANDING_INSTRUCTION TABLE (Recurring Automated Transfers)
CREATE TABLE STANDING_INSTRUCTION (
    instruction_id NUMBER(19) GENERATED BY DEFAULT ON NULL AS IDENTITY (START WITH 1000) PRIMARY KEY,
    source_account_id NUMBER(19) NOT NULL,
    destination_account_id NUMBER(19) NOT NULL,
    amount NUMBER(18, 4) NOT NULL,
    frequency VARCHAR2(20) NOT NULL,
    next_run_date DATE NOT NULL,
    last_run_date DATE,
    active BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_standing_src_acc FOREIGN KEY (source_account_id) REFERENCES ACCOUNT(account_id),
    CONSTRAINT fk_standing_dst_acc FOREIGN KEY (destination_account_id) REFERENCES ACCOUNT(account_id)
);

-- 18. BATCH_RUN TABLE (Nightly Settlement & Interest Capitalization Runs)
CREATE TABLE BATCH_RUN (
    batch_run_id NUMBER(19) GENERATED BY DEFAULT ON NULL AS IDENTITY PRIMARY KEY,
    business_date DATE NOT NULL,
    triggered_by NUMBER(19) NOT NULL,
    status VARCHAR2(20) NOT NULL,
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    finished_at TIMESTAMP
);

-- 19. BATCH_STEP TABLE (Nightly EOD Step-by-Step Journal)
CREATE TABLE BATCH_STEP (
    batch_step_id NUMBER(19) GENERATED BY DEFAULT ON NULL AS IDENTITY PRIMARY KEY,
    batch_run_id NUMBER(19) NOT NULL,
    step_no NUMBER(2) NOT NULL,
    step_name VARCHAR2(40) NOT NULL,
    status VARCHAR2(20) NOT NULL,
    items_processed NUMBER(10) DEFAULT 0 NOT NULL,
    total_amount NUMBER(18, 4) DEFAULT 0 NOT NULL,
    duration_ms NUMBER(10),
    message VARCHAR2(255),
    CONSTRAINT fk_batch_step_run FOREIGN KEY (batch_run_id) REFERENCES BATCH_RUN(batch_run_id)
);

-- 20. LEDGER_MUTATION_AUDIT TABLE (Cryptographic SHA-256 Tamper-Evident Ledger Journal)
CREATE TABLE LEDGER_MUTATION_AUDIT (
    audit_id NUMBER(19) GENERATED BY DEFAULT ON NULL AS IDENTITY PRIMARY KEY,
    transaction_id NUMBER(19) NOT NULL,
    account_id NUMBER(19) NOT NULL,
    reference_no VARCHAR2(36) NOT NULL,
    transaction_type VARCHAR2(30) NOT NULL,
    amount NUMBER(18, 4) NOT NULL,
    old_balance NUMBER(18, 4) NOT NULL,
    old_ibalanance NUMBER(18, 4) GENERATED ALWAYS AS (old_balance + 0) VIRTUAL,
    new_balance NUMBER(18, 4) NOT NULL,
    previous_hash VARCHAR2(64) NOT NULL,
    current_hash VARCHAR2(64) NOT NULL UNIQUE,
    actor_id NUMBER(19),
    client_ip VARCHAR2(45) DEFAULT '127.0.0.1' NOT NULL,
    event_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 21. DAILY_AUDIT_SEAL TABLE (EOD Forensic Immutability Seal)
CREATE TABLE DAILY_AUDIT_SEAL (
    seal_id NUMBER(19) GENERATED BY DEFAULT ON NULL AS IDENTITY PRIMARY KEY,
    business_date DATE NOT NULL UNIQUE,
    last_audit_id NUMBER(19) NOT NULL,
    seal_hash VARCHAR2(64) NOT NULL UNIQUE,
    sealed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_seal_audit FOREIGN KEY (last_audit_id) REFERENCES LEDGER_MUTATION_AUDIT(audit_id)
);

-- 22. NOTIFICATIONS TABLE (Customer Alerts & Event Feed)
CREATE TABLE NOTIFICATIONS (
    id NUMBER(19) GENERATED BY DEFAULT ON NULL AS IDENTITY PRIMARY KEY,
    customer_id NUMBER(19) NOT NULL,
    title VARCHAR2(150) NOT NULL,
    message VARCHAR2(500) NOT NULL,
    channel VARCHAR2(20) DEFAULT 'IN_APP' NOT NULL,
    is_read BOOLEAN DEFAULT FALSE NOT NULL,
    event_id VARCHAR2(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    read_at TIMESTAMP
);

-- ==============================================================================
-- OPERATIONAL COMPATIBILITY TABLES, VIEWS & TRIGGERS
-- ==============================================================================
CREATE TABLE ACCOUNT_FLAG (
    flag_id NUMBER(19) GENERATED BY DEFAULT ON NULL AS IDENTITY (START WITH 1000) PRIMARY KEY,
    account_id NUMBER(19) NOT NULL,
    reason VARCHAR2(255) NOT NULL,
    status VARCHAR2(20) DEFAULT 'FLAGGED' NOT NULL,
    flagged_by NUMBER(19) NOT NULL,
    removed_by NUMBER(19),
    flagged_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    removed_at TIMESTAMP,
    CONSTRAINT fk_account_flag_account FOREIGN KEY (account_id) REFERENCES ACCOUNT(account_id)
);

CREATE TABLE TRANSACTION_FLAG (
    flag_id NUMBER(19) GENERATED BY DEFAULT ON NULL AS IDENTITY (START WITH 1000) PRIMARY KEY,
    transaction_id NUMBER(19) NOT NULL,
    reason VARCHAR2(255) NOT NULL,
    status VARCHAR2(20) DEFAULT 'FLAGGED' NOT NULL,
    flagged_by NUMBER(19) NOT NULL,
    removed_by NUMBER(19),
    flagged_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    removed_at TIMESTAMP,
    CONSTRAINT fk_txn_flag_txn FOREIGN KEY (transaction_id) REFERENCES "TRANSACTION"(transaction_id)
);

CREATE TABLE SYSTEM_LOG (
    log_id NUMBER(19) GENERATED BY DEFAULT ON NULL AS IDENTITY PRIMARY KEY,
    user_id NUMBER(19),
    action VARCHAR2(255) NOT NULL,
    service_name VARCHAR2(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE KYC_UPDATE_REQUEST (
    kyc_request_id NUMBER(19) GENERATED BY DEFAULT ON NULL AS IDENTITY (START WITH 1000) PRIMARY KEY,
    kyc_id NUMBER(19) NOT NULL,
    new_first_name VARCHAR2(100),
    new_middle_initial VARCHAR2(10),
    new_last_name VARCHAR2(100),
    new_address VARCHAR2(255),
    new_mobile_number VARCHAR2(30),
    new_civil_status VARCHAR2(50),
    new_occupation VARCHAR2(100),
    status VARCHAR2(20) DEFAULT 'PENDING' NOT NULL,
    approved_by NUMBER(19),
    requested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    approved_at TIMESTAMP,
    CONSTRAINT fk_kyc_req_kyc FOREIGN KEY (kyc_id) REFERENCES KYC(kyc_id)
);

CREATE TABLE ACCOUNT_CLOSURE_REQUEST (
    closure_request_id NUMBER(19) GENERATED BY DEFAULT ON NULL AS IDENTITY (START WITH 1000) PRIMARY KEY,
    account_id NUMBER(19) NOT NULL,
    reason VARCHAR2(255),
    status VARCHAR2(20) DEFAULT 'PENDING' NOT NULL,
    approved_by NUMBER(19),
    rejection_reason VARCHAR2(255),
    requested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    reviewed_at TIMESTAMP,
    CONSTRAINT fk_closure_req_acc FOREIGN KEY (account_id) REFERENCES ACCOUNT(account_id)
);

CREATE TABLE INTERBANK_MESSAGE (
    message_id VARCHAR2(36) PRIMARY KEY,
    direction VARCHAR2(10) NOT NULL,
    partner_bank_code VARCHAR2(8) NOT NULL,
    transaction_id VARCHAR2(36) NOT NULL,
    counterparty_account VARCHAR2(34) NOT NULL,
    counterparty_name VARCHAR2(120),
    status VARCHAR2(20) NOT NULL,
    reason_code VARCHAR2(40),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_ib_txn FOREIGN KEY (transaction_id) REFERENCES TXN_TRANSACTION(transaction_id)
);

-- Compatibility Views
CREATE OR REPLACE VIEW "USER" AS SELECT * FROM APP_USER;
CREATE OR REPLACE VIEW TRANSACTION_AUDIT AS 
SELECT audit_id, transaction_id, account_id, transaction_type, amount, old_balance, new_balance, event_timestamp 
FROM LEDGER_MUTATION_AUDIT;

-- Compatibility Triggers
CREATE OR REPLACE TRIGGER trg_saga_failure_sync
BEFORE INSERT OR UPDATE ON SAGA_INSTANCE
FOR EACH ROW
BEGIN
    IF :NEW.failure_reason IS NULL AND :NEW.failure_code IS NOT NULL THEN
        :NEW.failure_reason := :NEW.failure_code;
    ELSIF :NEW.failure_code IS NULL AND :NEW.failure_reason IS NOT NULL THEN
        :NEW.failure_code := SUBSTR(:NEW.failure_reason, 1, 60);
    END IF;
END;
/

CREATE OR REPLACE TRIGGER trg_ledger_audit_hash
BEFORE INSERT ON LEDGER_MUTATION_AUDIT
FOR EACH ROW
DECLARE
    v_last_hash VARCHAR2(64);
    v_payload VARCHAR2(1000);
BEGIN
    IF :NEW.current_hash IS NULL THEN
        BEGIN
            SELECT current_hash INTO v_last_hash
            FROM (
                SELECT current_hash
                FROM LEDGER_MUTATION_AUDIT
                WHERE account_id = :NEW.account_id
                ORDER BY audit_id DESC
            ) WHERE ROWNUM = 1;
        EXCEPTION
            WHEN NO_DATA_FOUND THEN
                v_last_hash := '0000000000000000000000000000000000000000000000000000000000000000';
        END;

        :NEW.previous_hash := v_last_hash;

        v_payload := v_last_hash || '|' ||
                     :NEW.transaction_id || '|' ||
                     :NEW.account_id || '|' ||
                     NVL(:NEW.reference_no, '') || '|' ||
                     :NEW.transaction_type || '|' ||
                     TO_CHAR(:NEW.amount, 'FM99999999999990.0000') || '|' ||
                     TO_CHAR(:NEW.new_balance, 'FM99999999999990.0000');

        SELECT STANDARD_HASH(v_payload, 'SHA256') INTO :NEW.current_hash FROM DUAL;
    END IF;
END;
/

-- Performance & Integrity Indexes
CREATE INDEX idx_outbox_unpublished ON OUTBOX(published_at, outbox_id);
CREATE INDEX idx_txn_account ON "TRANSACTION"(account_id);
CREATE INDEX idx_txn_tx_src ON TXN_TRANSACTION(source_account_id);
CREATE INDEX idx_txn_tx_dst ON TXN_TRANSACTION(destination_account_id);
CREATE INDEX idx_notif_customer ON NOTIFICATIONS(customer_id);
CREATE INDEX idx_audit_account ON LEDGER_MUTATION_AUDIT(account_id);
CREATE INDEX idx_audit_timestamp ON LEDGER_MUTATION_AUDIT(event_timestamp);
CREATE INDEX idx_batch_step_run ON BATCH_STEP(batch_run_id);
CREATE INDEX idx_loan_sched_acc ON LOAN_SCHEDULE(loan_account_id);
CREATE INDEX idx_standing_src ON STANDING_INSTRUCTION(source_account_id);
"""

# 6. ON-PREMISES POSTGRESQL - audit store + batch schema (database/next/postgres)
# ----------------------------------------------------------------------------
tp = os.path.join(DB, "next", "postgres")
os.makedirs(tp, exist_ok=True)
for f in ["01_init_db.sql", "02_create_tables.sql", "03_audit_triggers.sql"]:
    shutil.copyfile(os.path.join(DB, "postgres", f), os.path.join(tp, f))

a = [HEADER, "-- Audit rows for every seeded ledger entry, in posting order. The trigger computes the hash chain.",
     "SET search_path TO audit_store, public;", ""]
for r in txn_rows:
    a.append("INSERT INTO audit_store.ledger_mutation_audit (transaction_id, account_id, reference_no, transaction_type, amount, old_balance, new_balance, actor_id, client_ip, event_timestamp)\n"
             f"VALUES ({r['id']}, {r['account']}, {q(r['ref'])}, {q(r['type'])}, {m4(r['amount'])}, {m4(r['prev'])}, {m4(r['new'])}, {n(r['actor'])}, '127.0.0.1', {pg_ts(r['mins'])});")
write(os.path.join(tp, "04_seed_data.sql"), "\n".join(a))

BATCH_DDL = HEADER + """-- ==============================================================================
-- Batch schema (owned by batch-service) and the daily audit seal.
-- Lives beside audit_store on the on-premises PostgreSQL. audit_store stays append-only.
-- ==============================================================================
CREATE SCHEMA IF NOT EXISTS batch;

-- status: RUNNING, COMPLETED, HALTED, FAILED
CREATE TABLE IF NOT EXISTS batch.batch_run (
    batch_run_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    business_date DATE NOT NULL,
    triggered_by BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ
);

-- status: PENDING, RUNNING, COMPLETED, FAILED, SKIPPED
CREATE TABLE IF NOT EXISTS batch.batch_step (
    batch_step_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    batch_run_id BIGINT NOT NULL REFERENCES batch.batch_run(batch_run_id),
    step_no INT NOT NULL CHECK (step_no BETWEEN 1 AND 6),
    step_name VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL,
    items_processed INT NOT NULL DEFAULT 0,
    duration_ms INT,
    UNIQUE (batch_run_id, step_no)
);

CREATE TABLE IF NOT EXISTS batch.standing_instruction (
    instruction_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    source_account_id BIGINT NOT NULL,
    destination_account_id BIGINT NOT NULL,
    amount NUMERIC(18, 4) NOT NULL CHECK (amount > 0),
    frequency VARCHAR(20) NOT NULL,
    next_run_date DATE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

-- status: PENDING, DUE, PAID, MISSED
CREATE TABLE IF NOT EXISTS batch.loan_schedule (
    schedule_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    loan_account_id BIGINT NOT NULL,
    due_date DATE NOT NULL,
    principal_due NUMERIC(18, 4) NOT NULL,
    interest_due NUMERIC(18, 4) NOT NULL,
    status VARCHAR(20) NOT NULL
);

CREATE TABLE IF NOT EXISTS audit_store.daily_audit_seal (
    seal_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    business_date DATE NOT NULL UNIQUE,
    last_audit_id BIGINT NOT NULL REFERENCES audit_store.ledger_mutation_audit(audit_id),
    seal_hash VARCHAR(64) NOT NULL,
    sealed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
"""
write(os.path.join(tp, "05_batch_schema.sql"), BATCH_DDL)

b = [HEADER, "SET search_path TO batch, audit_store, public;", ""]
b.append("-- Run 1: completed six days ago. Run 2: halted thirteen days ago by a reconciliation variance.")
b.append(f"INSERT INTO batch.batch_run (batch_run_id, business_date, triggered_by, status, started_at, finished_at) VALUES (1, CURRENT_DATE - 13, 1, 'HALTED', {pg_ts(13 * DAY)}, {pg_ts(13 * DAY - 1)});")
b.append(f"INSERT INTO batch.batch_run (batch_run_id, business_date, triggered_by, status, started_at, finished_at) VALUES (2, CURRENT_DATE - 6, 1, 'COMPLETED', {pg_ts(6 * DAY + 50)}, {pg_ts(6 * DAY + 49)});")
halted = [("COMPLETED", 3, "12.2400", 810, None), ("COMPLETED", 1, "50.0000", 640, None),
          ("COMPLETED", 1, "2000.0000", 900, None), ("COMPLETED", 0, "0.0000", 300, "No instalments due"),
          ("FAILED", 0, "0.0000", 1150, "Variance detected: debits 2062.24, credits 2012.24"),
          ("SKIPPED", 0, "0.0000", None, "Not run: reconciliation failed")]
done = [("COMPLETED", 1, "11.6400", 820, None), ("COMPLETED", 1, "50.0000", 640, None),
        ("COMPLETED", 0, "0.0000", 410, "No instructions due"), ("COMPLETED", 0, "0.0000", 380, "No instalments due"),
        ("COMPLETED", 0, "0.0000", 1200, "Debits equal credits"), ("COMPLETED", 1, "0.0000", 700, "Seal written")]
sid = 1
for run, steps in ((1, halted), (2, done)):
    for i, (st, items, _total, dur, _msg) in enumerate(steps, 1):
        b.append("INSERT INTO batch.batch_step (batch_step_id, batch_run_id, step_no, step_name, status, items_processed, duration_ms)\n"
                 f"VALUES ({sid}, {run}, {i}, {q(STEP_NAMES[i - 1])}, {q(st)}, {items}, {n(dur)});\n")
        sid += 1
b.append("\n-- Standing instructions")
for s in STANDING:
    b.append("INSERT INTO batch.standing_instruction (instruction_id, source_account_id, destination_account_id, amount, frequency, next_run_date, active)\n"
             f"VALUES ({s[0]}, {s[1]}, {s[2]}, {m4(s[3])}, {q(s[4])}, CURRENT_DATE + {s[5]}, {'TRUE' if s[6] else 'FALSE'});  -- {s[7]}")
b.append("\n-- Loan schedules")
for ls in LOAN_SCHEDULES:
    b.append("INSERT INTO batch.loan_schedule (schedule_id, loan_account_id, due_date, principal_due, interest_due, status)\n"
             f"VALUES ({ls[0]}, {ls[1]}, CURRENT_DATE + ({ls[3]}), {m4(ls[4])}, {m4(ls[5])}, {q(ls[6])});")
seal_row = max((r for r in txn_rows if r["mins"] >= 6 * DAY + 49), key=lambda r: r["id"])
b.append("\n-- Daily seal written by run 2: the hash of the business date and the last audit hash of that day")
b.append("INSERT INTO audit_store.daily_audit_seal (seal_id, business_date, last_audit_id, seal_hash, sealed_at)\n"
         f"SELECT 1, CURRENT_DATE - 6, a.audit_id, encode(digest((CURRENT_DATE - 6)::text || '|' || a.current_hash, 'sha256'), 'hex'), {pg_ts(6 * DAY + 49)}\n"
         f"FROM audit_store.ledger_mutation_audit a WHERE a.transaction_id = {seal_row['id']};")
b.append("\n-- Runtime IDs start above the seeded range")
for tbl, col, start in [("batch.batch_run", "batch_run_id", 100), ("batch.batch_step", "batch_step_id", 1000),
                        ("batch.standing_instruction", "instruction_id", 100), ("batch.loan_schedule", "schedule_id", 100),
                        ("audit_store.daily_audit_seal", "seal_id", 100)]:
    b.append(f"SELECT setval(pg_get_serial_sequence('{tbl}', '{col}'), {start}, false);")
write(os.path.join(tp, "06_seed_batch.sql"), "\n".join(b))

# ----------------------------------------------------------------------------
# 7. CLOUD POSTGRESQL (database/postgres-cloud)
# ----------------------------------------------------------------------------
pc = os.path.join(DB, "postgres-cloud")

# ---- identity_db
i = [HEADER, "\\connect identity_db", "", """-- Login identity only. No legal name, address or mobile number.
CREATE TABLE IF NOT EXISTS role (
    role_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    role_name VARCHAR(50) NOT NULL UNIQUE
);

-- user_type: CUSTOMER or STAFF. For a customer the token's userId is customer_id;
-- for staff it is user_id. status: ACTIVE, INACTIVE.
CREATE TABLE IF NOT EXISTS app_user (
    user_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    role_id BIGINT NOT NULL REFERENCES role(role_id),
    user_type VARCHAR(20) NOT NULL CHECK (user_type IN ('CUSTOMER', 'STAFF')),
    customer_id BIGINT UNIQUE,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    mfa_secret_ref VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((user_type = 'CUSTOMER') = (customer_id IS NOT NULL))
);
"""]
for rid, name in ROLES:
    i.append(f"INSERT INTO role (role_id, role_name) VALUES ({rid}, {q(name)});")
role_id = {name: rid for rid, name in ROLES}
i.append("\n-- Staff. Password for every seeded login: Password123!")
for uid, uname, role, email, status in STAFF:
    i.append("INSERT INTO app_user (user_id, role_id, user_type, customer_id, username, password_hash, email, status)\n"
             f"VALUES ({uid}, {role_id[role]}, 'STAFF', NULL, {q(uname)}, {q(PWD_HASH)}, {q(email)}, {q(status)});")
i.append("\n-- Customers. user_id = 1000 + customer_id")
for c in CUSTOMERS:
    i.append("INSERT INTO app_user (user_id, role_id, user_type, customer_id, username, password_hash, email, status)\n"
             f"VALUES ({1000 + c['id']}, {role_id['ROLE_CUSTOMER']}, 'CUSTOMER', {c['id']}, {q(c['username'])}, {q(PWD_HASH)}, {q(c['email'])}, {q(c['login'])});")
i.append("\nSELECT setval(pg_get_serial_sequence('role', 'role_id'), 100, false);")
i.append("SELECT setval(pg_get_serial_sequence('app_user', 'user_id'), 5000, false);")
write(os.path.join(pc, "02_identity_db.sql"), "\n".join(i))

# ---- customer_db
cdb = [HEADER, "\\connect customer_db", "", """-- Channel profile only. The KYC master (legal name, address, mobile) stays in Oracle.
CREATE TABLE IF NOT EXISTS channel_profile (
    customer_id BIGINT PRIMARY KEY,
    membership_number VARCHAR(20) NOT NULL UNIQUE,
    username VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL,
    preferred_language VARCHAR(10) NOT NULL DEFAULT 'en',
    kyc_tier VARCHAR(20) NOT NULL,
    kyc_status VARCHAR(20) NOT NULL DEFAULT 'VERIFIED',
    risk_score NUMERIC(4, 2) NOT NULL CHECK (risk_score BETWEEN 0 AND 1),
    credit_score INT NOT NULL CHECK (credit_score BETWEEN 300 AND 850),
    daily_limit NUMERIC(18, 4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS beneficiary (
    beneficiary_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES channel_profile(customer_id),
    account_number VARCHAR(34) NOT NULL,
    nickname VARCHAR(60) NOT NULL,
    bank_code VARCHAR(8) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (customer_id, account_number, bank_code)
);
"""]
for c in CUSTOMERS:
    lang = "fil" if c["id"] in (4, 6, 10, 13, 14) else "en"
    cdb.append("INSERT INTO channel_profile (customer_id, membership_number, username, display_name, email, preferred_language, kyc_tier, kyc_status, risk_score, credit_score, daily_limit)\n"
               f"VALUES ({c['id']}, {q('MBR-%06d' % c['id'])}, {q(c['username'])}, {q(display_name(c))}, {q(c['email'])}, {q(lang)}, {q(c['tier'])}, 'VERIFIED', {c['risk']}, {c['credit']}, {m4(c['limit'])});")
cdb.append("")
for n_, (cid, number, nick, bank, active) in enumerate(BENEFICIARIES, 1):
    cdb.append("INSERT INTO beneficiary (beneficiary_id, customer_id, account_number, nickname, bank_code, active)\n"
               f"VALUES ({n_}, {cid}, {q(number)}, {q(nick)}, {q(bank)}, {'TRUE' if active else 'FALSE'});")
cdb.append("\nSELECT setval(pg_get_serial_sequence('beneficiary', 'beneficiary_id'), 1000, false);")
write(os.path.join(pc, "03_customer_db.sql"), "\n".join(cdb))

# ---- transaction_db
t = [HEADER, "\\connect transaction_db", "", """CREATE SCHEMA IF NOT EXISTS txn;   -- transaction-service
CREATE SCHEMA IF NOT EXISTS saga;  -- orchestration-service

-- status: INITIATED, PROCESSING, POSTED, FAILED, REVERSED
-- channel: APP, TELLER, INTERBANK, BATCH
CREATE TABLE IF NOT EXISTS txn.txn_transaction (
    transaction_id UUID PRIMARY KEY,
    reference_no VARCHAR(36) NOT NULL UNIQUE,
    idempotency_key VARCHAR(64) UNIQUE,
    channel VARCHAR(20) NOT NULL,
    customer_id BIGINT,
    source_account_id BIGINT,
    destination_account_id BIGINT,
    destination_account_number VARCHAR(34),
    destination_bank_code VARCHAR(8) NOT NULL,
    amount NUMERIC(18, 4) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'PHP',
    category VARCHAR(30) NOT NULL DEFAULT 'TRANSFER',
    remarks VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    failure_code VARCHAR(60),
    reverses_transaction_id UUID REFERENCES txn.txn_transaction(transaction_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_txn_customer_created ON txn.txn_transaction(customer_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_txn_source ON txn.txn_transaction(source_account_id);
CREATE INDEX IF NOT EXISTS idx_txn_destination ON txn.txn_transaction(destination_account_id);

-- One row per stage that has happened. outcome: PASSED, FAILED.
CREATE TABLE IF NOT EXISTS txn.txn_stage_event (
    stage_event_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES txn.txn_transaction(transaction_id),
    stage_no INT NOT NULL CHECK (stage_no BETWEEN 1 AND 11),
    stage_name VARCHAR(30) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    detail VARCHAR(255),
    occurred_at TIMESTAMPTZ NOT NULL,
    UNIQUE (transaction_id, stage_no)
);

-- Not on the ERD: needed so a repeated inter-bank messageId returns the first answer.
-- direction: INBOUND, OUTBOUND. status: ACCEPTED, REJECTED.
CREATE TABLE IF NOT EXISTS txn.interbank_message (
    message_id UUID PRIMARY KEY,
    direction VARCHAR(10) NOT NULL,
    partner_bank_code VARCHAR(8) NOT NULL,
    transaction_id UUID NOT NULL REFERENCES txn.txn_transaction(transaction_id),
    counterparty_account VARCHAR(34) NOT NULL,
    counterparty_name VARCHAR(120),
    status VARCHAR(20) NOT NULL,
    reason_code VARCHAR(40),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- state: STARTED, RISK_APPROVED, FUNDS_RESERVED, POSTED, COMPLETED, FAILED, COMPENSATING, COMPENSATED
-- transaction_id is the same value as txn.txn_transaction; no foreign key across owners.
CREATE TABLE IF NOT EXISTS saga.saga_instance (
    saga_id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL UNIQUE,
    reference_no VARCHAR(40),
    state VARCHAR(30) NOT NULL,
    hold_id BIGINT,
    risk_decision VARCHAR(20),
    failure_code VARCHAR(60),
    payload TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
"""]

def txn_insert(tid, ref, h, status, mins, completed, failure=None, reverses=None, channel=None, category=None, remarks=None, key=True):
    src = None if is_gl(h["src"]) and h.get("interbank", {}).get("direction") == "INBOUND" else h["src"]
    dst_is_partner = h.get("interbank", {}).get("direction") == "OUTBOUND"
    dst = None if dst_is_partner else h["dst"]
    dst_no = h["interbank"]["counterparty_account"] if dst_is_partner else acct[h["dst"]]["number"]
    bank = PARTNER_BANK if dst_is_partner else OUR_BANK
    return ("INSERT INTO txn.txn_transaction (transaction_id, reference_no, idempotency_key, channel, customer_id, source_account_id, destination_account_id, destination_account_number, destination_bank_code, amount, currency, category, remarks, status, failure_code, reverses_transaction_id, created_at, completed_at)\n"
            f"VALUES ({q(tid)}, {q(ref)}, {q('seed-' + ref) if key else 'NULL'}, {q(channel or h['channel'])}, {n(h['customer'])}, {n(src)}, {n(dst)}, {q(dst_no)}, {q(bank)}, {m4(h['amount'])}, 'PHP', {q(category or h['category'])}, {q(remarks or h['remarks'])}, {q(status)}, {q(failure)}, {q(reverses)}, {pg_ts(mins)}, {pg_ts(completed) if completed is not None else 'NULL'});")

stage_id = [0]
all_txns_meta = []
all_stages_meta = []
all_ib_meta = []

def txn_insert(tid, ref, h, status, mins, completed, failure=None, reverses=None, channel=None, category=None, remarks=None, key=True):
    all_txns_meta.append((tid, ref, h, status, mins, completed, failure, reverses, channel, category, remarks, key))
    src = None if is_gl(h["src"]) and h.get("interbank", {}).get("direction") == "INBOUND" else h["src"]
    dst_is_partner = h.get("interbank", {}).get("direction") == "OUTBOUND"
    dst = None if dst_is_partner else h["dst"]
    dst_no = h["interbank"]["counterparty_account"] if dst_is_partner else acct[h["dst"]]["number"]
    bank = PARTNER_BANK if dst_is_partner else OUR_BANK
    return ("INSERT INTO txn.txn_transaction (transaction_id, reference_no, idempotency_key, channel, customer_id, source_account_id, destination_account_id, destination_account_number, destination_bank_code, amount, currency, category, remarks, status, failure_code, reverses_transaction_id, created_at, completed_at)\n"
            f"VALUES ({q(tid)}, {q(ref)}, {q('seed-' + ref) if key else 'NULL'}, {q(channel or h['channel'])}, {n(h['customer'])}, {n(src)}, {n(dst)}, {q(dst_no)}, {q(bank)}, {m4(h['amount'])}, 'PHP', {q(category or h['category'])}, {q(remarks or h['remarks'])}, {q(status)}, {q(failure)}, {q(reverses)}, {pg_ts(mins)}, {pg_ts(completed) if completed is not None else 'NULL'});")

def stages(tid, mins, upto, fail_at=None, fail_detail=None, details=None):
    out = []
    for no in range(1, upto + 1):
        stage_id[0] += 1
        failed = fail_at == no
        detail = fail_detail if failed else (details or {}).get(no)
        all_stages_meta.append((stage_id[0], tid, no, STAGES[no - 1], "FAILED" if failed else "PASSED", detail, mins))
        out.append("INSERT INTO txn.txn_stage_event (stage_event_id, transaction_id, stage_no, stage_name, outcome, detail, occurred_at)\n"
                   f"VALUES ({stage_id[0]}, {q(tid)}, {no}, {q(STAGES[no - 1])}, {q('FAILED' if failed else 'PASSED')}, {q(detail)}, {pg_ts(mins)} + interval '{no * 80} milliseconds');")
    return out

sagas, ib = [], []
for h in HISTORY:
    tid = h["transaction_id"]
    risk = cust[h["customer"]]["risk"] if h["customer"] in cust else None
    det = {4: f"score {risk}"} if risk else {}
    if h["kind"] == "POSTED":
        t.append(txn_insert(tid, h["ref"], h, "POSTED", h["mins"], h["mins"] - 1))
        if h["channel"] == "INTERBANK":
            det = {3: "not applicable", 4: "not applicable", 5: "not applicable", 6: "not applicable", 7: "not applicable"}
        t += stages(tid, h["mins"], 11, details=det)
        sagas.append((tid, h["ref"], "COMPLETED", h.get("hold_id"), None if h["channel"] in ("INTERBANK", "BATCH") else "APPROVE", None, h["mins"] - 1))
    elif h["kind"] == "REVERSED":
        r = h["reversal"]
        t.append(txn_insert(tid, h["ref"], h, "REVERSED", h["mins"], h["mins"] - 1))
        t += stages(tid, h["mins"], 11, details=det)
        sagas.append((tid, h["ref"], "COMPLETED", h.get("hold_id"), "APPROVE", None, h["mins"] - 1))
        rev = dict(h, src=h["dst"], dst=h["src"], customer=h["customer"])
        t.append(txn_insert(r["transaction_id"], r["ref"], rev, "POSTED", r["mins"], r["mins"] - 1, reverses=tid,
                            channel="TELLER", remarks=r["reason"]))
        t += stages(r["transaction_id"], r["mins"], 11, details={4: "not applicable", 5: "not applicable", 6: "not applicable"})
        sagas.append((r["transaction_id"], r["ref"], "COMPLETED", None, None, None, r["mins"] - 1))
    elif h["kind"] == "FAILED":
        t.append(txn_insert(tid, h["ref"], h, "FAILED", h["mins"], h["mins"] - 1, failure=h["fail_code"]))
        t += stages(tid, h["mins"], h["fail_stage"], fail_at=h["fail_stage"], fail_detail=h["fail_detail"], details=det)
        if h["fail_stage"] >= 4:
            decision = "DECLINE" if h["fail_stage"] in (4, 5) else "APPROVE"
            sagas.append((tid, h["ref"], "FAILED", None, decision, h["fail_code"], h["mins"] - 1))
    elif h["kind"] == "INFLIGHT":
        t.append(txn_insert(tid, h["ref"], h, "PROCESSING", h["mins"], None))
        t += stages(tid, h["mins"], 6, details=det)
        sagas.append((tid, h["ref"], "FUNDS_RESERVED", h["hold_id"], "APPROVE", None, h["mins"]))
    if "interbank" in h:
        x = h["interbank"]
        all_ib_meta.append((x["message_id"], x["direction"], PARTNER_BANK, tid, x["counterparty_account"], x["counterparty_name"], "ACCEPTED", h["mins"]))
        ib.append("INSERT INTO txn.interbank_message (message_id, direction, partner_bank_code, transaction_id, counterparty_account, counterparty_name, status, created_at)\n"
                  f"VALUES ({q(x['message_id'])}, {q(x['direction'])}, {q(PARTNER_BANK)}, {q(tid)}, {q(x['counterparty_account'])}, {q(x['counterparty_name'])}, 'ACCEPTED', {pg_ts(h['mins'])});")
t.append("")
t += ib
t.append("")
for tid, ref, state, hold, decision, failure, mins in sagas:
    t.append("INSERT INTO saga.saga_instance (saga_id, transaction_id, reference_no, state, hold_id, risk_decision, failure_code, payload, updated_at)\n"
             f"VALUES ({q(str(uuid.uuid5(NS, 'saga-' + tid)))}, {q(tid)}, {q(ref)}, {q(state)}, {n(hold)}, {q(decision)}, {q(failure)}, NULL, {pg_ts(mins)});")
t.append("\nSELECT setval(pg_get_serial_sequence('txn.txn_stage_event', 'stage_event_id'), 10000, false);")
write(os.path.join(pc, "04_transaction_db.sql"), "\n".join(t))

# ---- notification_db
nt = [HEADER, "\\connect notification_db", "", """CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    title VARCHAR(150) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    channel VARCHAR(30) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    event_id VARCHAR(100) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_notifications_customer ON notifications(customer_id);
"""]
notes = []
def note(customer, title, message, mins, key, read):
    if customer in cust:
        notes.append((customer, title, message, mins, key, read))

for h in HISTORY:
    amt = f"PHP {m2(h['amount'])}"
    old = h["mins"] > 3 * DAY
    s_cust = None if is_gl(h["src"]) else acct[h["src"]]["customer"]
    d_cust = None if is_gl(h["dst"]) else acct[h["dst"]]["customer"]
    if h["kind"] in ("POSTED", "REVERSED"):
        if h["channel"] == "BATCH":
            target = s_cust or d_cust
            note(target, "Account update", f"{h['remarks']}: {amt}. Ref {h['ref']}.", h["mins"], h["ref"] + "-B", old)
        elif h.get("interbank", {}).get("direction") == "INBOUND":
            note(d_cust, "Money received", f"You received {amt} from {h['interbank']['counterparty_name']} ({PARTNER_BANK}). Ref {h['ref']}.", h["mins"], h["ref"] + "-R", old)
        elif h.get("interbank", {}).get("direction") == "OUTBOUND":
            note(s_cust, "Transfer sent", f"You sent {amt} to {h['interbank']['counterparty_account']} at {PARTNER_BANK}. Ref {h['ref']}.", h["mins"], h["ref"] + "-S", old)
        elif h.get("single"):
            note(s_cust, "Withdrawal", f"{amt} was withdrawn over the counter. Ref {h['ref']}.", h["mins"], h["ref"] + "-S", old)
        else:
            note(s_cust, "Transfer sent", f"You sent {amt} to {acct[h['dst']]['number']}. Ref {h['ref']}.", h["mins"], h["ref"] + "-S", old)
            if d_cust != s_cust:
                note(d_cust, "Money received", f"You received {amt} from {acct[h['src']]['number']}. Ref {h['ref']}.", h["mins"], h["ref"] + "-R", old)
        if h["kind"] == "REVERSED":
            r = h["reversal"]
            note(s_cust, "Transfer reversed", f"{amt} was returned to your account. Ref {r['ref']}.", r["mins"], r["ref"] + "-S", False)
            note(d_cust, "Transfer reversed", f"{amt} was taken back from your account. Ref {r['ref']}.", r["mins"], r["ref"] + "-R", False)
    elif h["kind"] == "FAILED":
        note(s_cust, "Transfer failed", f"Your transfer of {amt} could not be completed. Ref {h['ref']}.", h["mins"], h["ref"] + "-F", False)
note(11, "Account closed", "Your account ACC_10000014 has been closed.", 11 * DAY, "seed-closure-14", True)
note(9, "Profile update approved", "Your request to update your mobile number was approved.", 13 * DAY, "seed-kyc-9", True)
note(6, "Profile update received", "We received your request to update your address.", 2 * DAY, "seed-kyc-6", False)
for k, (customer, title, message, mins, key, read) in enumerate(sorted(notes, key=lambda x: -x[3]), 1):
    nt.append("INSERT INTO notifications (id, customer_id, title, message, channel, is_read, event_id, created_at, read_at)\n"
              f"VALUES ({k}, {customer}, {q(title)}, {q(message)}, 'IN_APP', {'TRUE' if read else 'FALSE'}, {q('evt_seed_' + key)}, {pg_ts(mins)}, {pg_ts(mins - 30) if read else 'NULL'});")
nt.append("\nSELECT setval(pg_get_serial_sequence('notifications', 'id'), 1000, false);")
write(os.path.join(pc, "05_notification_db.sql"), "\n".join(nt))

# ----------------------------------------------------------------------------
# 7B. ORACLE SEED DATA GENERATION (All 22 Tables & Operational Features)
# ----------------------------------------------------------------------------
# Compute forensic audit hash chain for Oracle LEDGER_MUTATION_AUDIT
GENESIS_HASH = "0" * 64
account_hashes = {}
ora_audit = []
for r in txn_rows:
    aid = r["account"]
    prev_h = account_hashes.get(aid, GENESIS_HASH)
    payload = f"{prev_h}|{r['id']}|{aid}|{r['ref']}|{r['type']}|{m4(r['amount'])}|{m4(r['new'])}"
    curr_h = hashlib.sha256(payload.encode("utf-8")).hexdigest()
    account_hashes[aid] = curr_h
    ora_audit.append(dict(r, prev_hash=prev_h, curr_hash=curr_h))

seal_audit_row = next(a for a in ora_audit if a["id"] == seal_row["id"])
seal_payload = f"DAY-6|{seal_audit_row['curr_hash']}"
seal_hash = hashlib.sha256(seal_payload.encode("utf-8")).hexdigest()

o = [HEADER, "ALTER SESSION SET CONTAINER = XEPDB1;", "ALTER SESSION SET CURRENT_SCHEMA = CORE_USER;", ""]

o.append("-- 1. ROLES")
for rid, name in ROLES:
    o.append(f"INSERT INTO ROLE (role_id, role_name) VALUES ({rid}, {q(name)});")

o.append("\n-- 2. CUSTOMERS")
o.append(f"INSERT INTO CUSTOMER (customer_id, kyc_status, created_at) VALUES ({BANK_CUSTOMER_ID}, 'VERIFIED', {ora_ts(730 * DAY)});")
for c in CUSTOMERS:
    opened = max(a["opened"] for a in acct.values() if a["customer"] == c["id"]) + 1
    o.append(f"INSERT INTO CUSTOMER (customer_id, kyc_status, created_at) VALUES ({c['id']}, 'VERIFIED', {ora_ts(opened * DAY)});")

o.append("\n-- 3. APP_USERS (Staff + Members)")
for uid, uname, role, email, status in STAFF:
    o.append("INSERT INTO APP_USER (user_id, role_id, user_type, customer_id, username, password_hash, email, status)\n"
             f"VALUES ({uid}, {role_id[role]}, 'STAFF', NULL, {q(uname)}, {q(PWD_HASH)}, {q(email)}, {q(status)});")
for c in CUSTOMERS:
    o.append("INSERT INTO APP_USER (user_id, role_id, user_type, customer_id, username, password_hash, email, status)\n"
             f"VALUES ({1000 + c['id']}, {role_id['ROLE_CUSTOMER']}, 'CUSTOMER', {c['id']}, {q(c['username'])}, {q(PWD_HASH)}, {q(c['email'])}, {q(c['login'])});")

o.append("\n-- 4. KYC RECORDS (Legal Master)")
for c in CUSTOMERS:
    opened = max(a["opened"] for a in acct.values() if a["customer"] == c["id"]) + 1
    o.append("INSERT INTO KYC (kyc_id, customer_id, first_name, middle_initial, last_name, address, civil_status, occupation, mobile_number, status, created_at)\n"
             f"VALUES ({c['id']}, {c['id']}, {q(c['first'])}, {q(c['mi'])}, {q(c['last'])}, {q(c['address'])}, {q(c['civil'])}, {q(c['occupation'])}, {q(c['mobile'])}, 'VERIFIED', {ora_ts(opened * DAY)});")

o.append("\n-- 5. CHANNEL PROFILES")
for c in CUSTOMERS:
    lang = "fil" if c["id"] in (4, 6, 10, 13, 14) else "en"
    o.append("INSERT INTO CHANNEL_PROFILE (customer_id, membership_number, username, display_name, email, preferred_language, kyc_tier, kyc_status, risk_score, credit_score, daily_limit)\n"
             f"VALUES ({c['id']}, {q('MBR-%06d' % c['id'])}, {q(c['username'])}, {q(display_name(c))}, {q(c['email'])}, {q(lang)}, {q(c['tier'])}, 'VERIFIED', {c['risk']}, {c['credit']}, {m4(c['limit'])});")

o.append("\n-- 6. BENEFICIARIES")
for n_, (cid, number, nick, bank, active) in enumerate(BENEFICIARIES, 1):
    o.append("INSERT INTO BENEFICIARY (beneficiary_id, customer_id, account_number, nickname, active)\n"
             f"VALUES ({n_}, {cid}, {q(number)}, {q(nick)}, {'TRUE' if active else 'FALSE'});")

o.append("\n-- 7. ACCOUNTS")
for aid in sorted(acct.keys()):
    a = acct[aid]
    o.append("INSERT INTO ACCOUNT (account_id, customer_id, account_number, account_type, currency, status, created_at)\n"
             f"VALUES ({aid}, {a['customer']}, {q(a['number'])}, {q(a['type'])}, 'PHP', {q(a['status'])}, {ora_ts(a['opened'] * DAY)});")

o.append("\n-- 8. BALANCES")
for aid in sorted(acct.keys()):
    avail = available[aid]
    ledg = ledger[aid]
    o.append("INSERT INTO BALANCE (balance_id, account_id, available_balance, ledger_balance)\n"
             f"VALUES ({aid}, {aid}, {m4(avail)}, {m4(ledg)});")

o.append("\n-- 9. HOLDS")
for h in holds:
    res = ora_ts(h["resolved_mins"]) if "resolved_mins" in h else "NULL"
    o.append("INSERT INTO HOLD (hold_id, account_id, reference_no, amount, status, created_at, resolved_at)\n"
             f"VALUES ({h['id']}, {h['account']}, {q(h['ref'])}, {m4(h['amount'])}, {q(h['status'])}, {ora_ts(h['mins'])}, {res});")

transfer_reqs = []
req_id = 1
for h in HISTORY:
    if is_gl(h["src"]) and h.get("interbank", {}).get("direction") == "INBOUND":
        continue
    dst_is_partner = h.get("interbank", {}).get("direction") == "OUTBOUND"
    dst_no = h["interbank"]["counterparty_account"] if dst_is_partner else acct[h["dst"]]["number"]
    bank = PARTNER_BANK if dst_is_partner else OUR_BANK
    st = "COMPLETED" if h["kind"] == "POSTED" else ("PENDING" if h["kind"] == "INFLIGHT" else ("FAILED" if h["kind"] == "FAILED" else "REVERSED"))
    transfer_reqs.append(dict(id=req_id, account=h["src"], dst=dst_no, bank=bank, amount=h["amount"], channel=h["channel"], status=st, ref=h["ref"], mins=h["mins"]))
    req_id += 1

o.append("\n-- 10. TRANSFER_REQUESTS")
for r in transfer_reqs:
    o.append("INSERT INTO TRANSFER_REQUEST (transfer_request_id, source_account_id, destination_account_number, amount, channel, status, id_reference_no, created_at)\n"
             f"VALUES ({r['id']}, {r['account']}, {q(r['dst'])}, {m4(r['amount'])}, {q(r['channel'])}, {q(r['status'])}, {q(r['ref'])}, {ora_ts(r['mins'])});")

o.append("\n-- 11. TRANSACTION (Posting Journal)")
for r in txn_rows:
    rev = n(r.get("reverses"))
    app = n(r.get("approver") or r.get("actor"))
    o.append("INSERT INTO \"TRANSACTION\" (transaction_id, account_id, reference_no, transaction_type, amount, previous_balance, new_balance, status, reverses_transaction_id, approved_by, created_at)\n"
             f"VALUES ({r['id']}, {r['account']}, {q(r['ref'])}, {q(r['type'])}, {m4(r['amount'])}, {m4(r['prev'])}, {m4(r['new'])}, 'COMPLETED', {rev}, {app}, {ora_ts(r['mins'])});")

o.append("\n-- 12. OUTBOX")
for i, e in enumerate(outbox, 1):
    payload = '{\"transferReference\":\"%s\",\"transactionId\":\"%s\",\"seed\":true}' % (e["ref"], e["txn"])
    o.append("INSERT INTO OUTBOX (outbox_id, topic, event_key, payload, created_at, published_at)\n"
             f"VALUES ({i}, {q(e['topic'])}, {q(e['key'])}, {q(payload)}, {ora_ts(e['mins'])}, {ora_ts(e['mins'])});")

o.append("\n-- 13. LEDGER_MUTATION_AUDIT (Cryptographic Forensic Log)")
for r in ora_audit:
    o.append("INSERT INTO LEDGER_MUTATION_AUDIT (audit_id, transaction_id, account_id, reference_no, transaction_type, amount, old_balance, new_balance, previous_hash, current_hash, actor_id, client_ip, event_timestamp)\n"
             f"VALUES ({r['id']}, {r['id']}, {r['account']}, {q(r['ref'])}, {q(r['type'])}, {m4(r['amount'])}, {m4(r['prev'])}, {m4(r['new'])}, {q(r['prev_hash'])}, {q(r['curr_hash'])}, {n(r['actor'])}, '127.0.0.1', {ora_ts(r['mins'])});")

o.append("\n-- 14. DAILY_AUDIT_SEAL")
o.append("INSERT INTO DAILY_AUDIT_SEAL (seal_id, business_date, last_audit_id, seal_hash, sealed_at)\n"
         f"VALUES (1, TRUNC(CURRENT_DATE) - 6, {seal_row['id']}, {q(seal_hash)}, {ora_ts(6 * DAY)});")

o.append("\n-- 15. TXN_TRANSACTION")
for tid, ref, h, status, mins, completed, failure, reverses, channel, category, remarks, key in all_txns_meta:
    src = None if is_gl(h["src"]) and h.get("interbank", {}).get("direction") == "INBOUND" else h["src"]
    dst_is_partner = h.get("interbank", {}).get("direction") == "OUTBOUND"
    dst = None if dst_is_partner else h["dst"]
    dst_no = h["interbank"]["counterparty_account"] if dst_is_partner else acct[h["dst"]]["number"]
    bank = PARTNER_BANK if dst_is_partner else OUR_BANK
    o.append("INSERT INTO TXN_TRANSACTION (transaction_id, reference_no, idempotency_key, channel, customer_id, source_account_id, destination_account_id, destination_account_number, amount, currency, category, remarks, status, failure_code, reverses_transaction_id, created_at, completed_at)\n"
             f"VALUES ({q(tid)}, {q(ref)}, {q('seed-' + ref) if key else 'NULL'}, {q(channel or h['channel'])}, {n(h['customer'])}, {n(src)}, {n(dst)}, {q(dst_no)}, {m4(h['amount'])}, 'PHP', {q(category or h['category'])}, {q(remarks or h['remarks'])}, {q(status)}, {q(failure)}, {q(reverses)}, {ora_ts(mins)}, {ora_ts(completed) if completed is not None else 'NULL'});")

o.append("\n-- 16. TXN_STAGE_EVENT")
for stage_ev_id, tid, no, name, outcome, detail, mins in all_stages_meta:
    time_expr = f"{ora_ts(mins)} + NUMTODSINTERVAL({no * 0.08:.3f}, 'SECOND')" if mins else f"SYSTIMESTAMP + NUMTODSINTERVAL({no * 0.08:.3f}, 'SECOND')"
    o.append(f"INSERT INTO TXN_STAGE_EVENT (stage_event_id, transaction_id, stage_no, stage_name, outcome, detail, occurred_at)\n"
             f"VALUES ({stage_ev_id}, {q(tid)}, {no}, {q(name)}, {q(outcome)}, {q(detail)}, {time_expr});")

o.append("\n-- 17. SAGA_INSTANCE")
for tid, ref, state, hold, decision, failure, mins in sagas:
    o.append("INSERT INTO SAGA_INSTANCE (saga_id, transaction_id, reference_no, state, hold_id, risk_decision, failure_reason, failure_code, payload, updated_at)\n"
             f"VALUES ({q(str(uuid.uuid5(NS, 'saga-' + tid)))}, {q(tid)}, {q(ref)}, {q(state)}, {n(hold)}, {q(decision)}, {q(failure)}, {q(failure)}, NULL, {ora_ts(mins)});")

o.append("\n-- 18. INTERBANK_MESSAGE")
for msg_id, direction, p_bank, tid, c_acct, c_name, status, mins in all_ib_meta:
    o.append("INSERT INTO INTERBANK_MESSAGE (message_id, direction, partner_bank_code, transaction_id, counterparty_account, counterparty_name, status, created_at)\n"
             f"VALUES ({q(msg_id)}, {q(direction)}, {q(p_bank)}, {q(tid)}, {q(c_acct)}, {q(c_name)}, {q(status)}, {ora_ts(mins)});")

o.append("\n-- 19. STANDING_INSTRUCTION")
for s in STANDING:
    o.append("INSERT INTO STANDING_INSTRUCTION (instruction_id, source_account_id, destination_account_id, amount, frequency, next_run_date, active)\n"
             f"VALUES ({s[0]}, {s[1]}, {s[2]}, {m4(s[3])}, {q(s[4])}, TRUNC(CURRENT_DATE) + {s[5]}, {'TRUE' if s[6] else 'FALSE'});")

o.append("\n-- 20. LOAN_SCHEDULE")
for ls in LOAN_SCHEDULES:
    paid = ora_ts(10 * DAY + 60) if ls[6] == "PAID" else "NULL"
    o.append("INSERT INTO LOAN_SCHEDULE (schedule_id, loan_account_id, due_date, principal_due, interest_due, status, paid_at)\n"
             f"VALUES ({ls[0]}, {ls[1]}, TRUNC(CURRENT_DATE) + ({ls[3]}), {m4(ls[4])}, {m4(ls[5])}, {q(ls[6])}, {paid});")

o.append("\n-- 21. BATCH_RUN & BATCH_STEP")
o.append(f"INSERT INTO BATCH_RUN (batch_run_id, business_date, triggered_by, status, started_at, finished_at) VALUES (1, TRUNC(CURRENT_DATE) - 13, 1, 'HALTED', {ora_ts(13 * DAY)}, {ora_ts(13 * DAY - 1)});")
o.append(f"INSERT INTO BATCH_RUN (batch_run_id, business_date, triggered_by, status, started_at, finished_at) VALUES (2, TRUNC(CURRENT_DATE) - 6, 1, 'COMPLETED', {ora_ts(6 * DAY + 50)}, {ora_ts(6 * DAY + 49)});")

sid = 1
for run, steps in ((1, halted), (2, done)):
    for i, (st, items, total, dur, msg) in enumerate(steps, 1):
        o.append("INSERT INTO BATCH_STEP (batch_step_id, batch_run_id, step_no, step_name, status, items_processed, total_amount, duration_ms, message)\n"
                 f"VALUES ({sid}, {run}, {i}, {q(STEP_NAMES[i - 1])}, {q(st)}, {items}, {total}, {n(dur)}, {q(msg)});")
        sid += 1

o.append("\n-- 22. NOTIFICATIONS")
for k, (customer, title, message, mins, key, read) in enumerate(sorted(notes, key=lambda x: -x[3]), 1):
    o.append("INSERT INTO NOTIFICATIONS (id, customer_id, title, message, channel, is_read, event_id, created_at, read_at)\n"
             f"VALUES ({k}, {customer}, {q(title)}, {q(message)}, 'IN_APP', {'TRUE' if read else 'FALSE'}, {q('evt_seed_' + key)}, {ora_ts(mins)}, {ora_ts(mins - 30) if read else 'NULL'});")

o.append("\n-- Operational Feature Data (Account Flags, Closure Requests, KYC Updates, Logs)")
for i, f in enumerate(ACCOUNT_FLAGS, 1):
    o.append("INSERT INTO ACCOUNT_FLAG (flag_id, account_id, reason, status, flagged_by, removed_by, flagged_at, removed_at)\n"
             f"VALUES ({i}, {f['account']}, {q(f['reason'])}, {q(f['status'])}, {f['flagged_by']}, {n(f.get('removed_by'))}, {ora_ts(f['mins'])}, {ora_ts(f['removed_mins']) if 'removed_mins' in f else 'NULL'});")

flag_row = next(h for h in HISTORY if h["ref"] == "TX-SEED-0003")["debit_row"]
o.append("INSERT INTO TRANSACTION_FLAG (flag_id, transaction_id, reason, status, flagged_by, flagged_at)\n"
         f"VALUES (1, {flag_row['id']}, 'Amount unusual for this account; kept for review', 'FLAGGED', 2, {ora_ts(flag_row['mins'] - 30)});")

for i, c in enumerate(CLOSURE_REQUESTS, 1):
    o.append("INSERT INTO ACCOUNT_CLOSURE_REQUEST (closure_request_id, account_id, reason, status, approved_by, rejection_reason, requested_at, reviewed_at)\n"
             f"VALUES ({i}, {c['account']}, {q(c['reason'])}, {q(c['status'])}, {n(c.get('approved_by'))}, {q(c.get('rejection'))}, {ora_ts(c['mins'])}, {ora_ts(c['reviewed_mins']) if 'reviewed_mins' in c else 'NULL'});")

for i, k in enumerate(KYC_UPDATES, 1):
    o.append("INSERT INTO KYC_UPDATE_REQUEST (kyc_request_id, kyc_id, new_address, new_mobile_number, status, approved_by, requested_at, approved_at)\n"
             f"VALUES ({i}, {k['customer']}, {q(k.get('new_address'))}, {q(k.get('new_mobile'))}, {q(k['status'])}, {n(k.get('approved_by'))}, {ora_ts(k['mins'])}, {ora_ts(k['reviewed_mins']) if 'reviewed_mins' in k else 'NULL'});")

o.append("\nINSERT INTO SYSTEM_LOG (user_id, action, service_name) VALUES (1, 'SEED_DATABASE_INITIALIZATION', 'INTEGRATED_CAPSTONE_SEED');")
o.append("\nCOMMIT;")

# Write Oracle Seed to BOTH active (database/oracle) and next (database/next/oracle) directories
# (02_create_tables.sql is maintained manually or per user edit, do not overwrite)
# write(os.path.join(DB, "oracle", "02_create_tables.sql"), ORACLE_DDL)
write(os.path.join(DB, "oracle", "03_seed_data.sql"), "\n".join(o))
# write(os.path.join(DB, "next", "oracle", "02_create_tables.sql"), ORACLE_DDL)
write(os.path.join(DB, "next", "oracle", "03_seed_data.sql"), "\n".join(o))
shutil.copyfile(os.path.join(DB, "oracle", "01_init_user.sql"), os.path.join(DB, "next", "oracle", "01_init_user.sql"))


# ----------------------------------------------------------------------------
# 8. SEED_REFERENCE.md - the ID table everyone builds against (handshake H2)
# ----------------------------------------------------------------------------
md = ["# Seed Reference", "",
      "> GENERATED by `database/seed/generate_seed.py`. Do not edit by hand.", "",
      "Password for every seeded login: `Password123!`", "",
      "Timestamps are relative to the moment the database is created, so \"due today\" items are always due and the last 30 days always has history.", "",
      "## Staff logins", "", "| userId (token) | Username | Role | Status |", "| --- | --- | --- | --- |"]
for uid, uname, role, email, status in STAFF:
    md.append(f"| {uid} | `{uname}` | {role} | {status} |")
md += ["", "## Members", "",
       "For a customer the token's `userId` is the customer ID.", "",
       "| Customer ID | Username | Name | Tier | Risk | Credit | Daily limit (PHP) | Login | Scenario |",
       "| --- | --- | --- | --- | --- | --- | --- | --- | --- |"]
for c in CUSTOMERS:
    md.append(f"| {c['id']} | `{c['username']}` | {c['first']} {c['mi']}. {c['last']} | {c['tier']} | {c['risk']} | {c['credit']} | {m2(c['limit'])} | {c['login']} | {c['scenario']} |")
md += ["", "## Accounts", "",
       "| Account ID | Number | Type | Status | Owner | Ledger balance (PHP) | Held (PHP) | Available (PHP) |",
       "| --- | --- | --- | --- | --- | --- | --- | --- |"]
for aid in sorted(a for a in acct if not is_gl(a)):
    a_ = acct[aid]
    md.append(f"| {aid} | `{a_['number']}` | {a_['type']} | {a_['status']} | {a_['customer']} `{cust[a_['customer']]['username']}` | {m2(ledger[aid])} | {m2(held[aid])} | {m2(available[aid])} |")
md += ["", "For a `LOAN` account the balance is the outstanding principal.", "",
       "## Internal GL accounts", "",
       f"Owned by internal customer {BANK_CUSTOMER_ID}. Each opens with PHP {m2(GL_OPENING)} because `BALANCE` forbids a negative available balance and these accounts are debited. Services read these IDs from configuration.", "",
       "| Account ID | Number | Used for | Ledger balance (PHP) |", "| --- | --- | --- | --- |"]
uses = {"CASH": "Counter-leg of deposits, withdrawals and opening deposits", "INTEREST_EXPENSE": "Debited when interest is paid",
        "FEE_INCOME": "Credited when fees are charged", "LOAN_RECEIVABLE": "Loan booking and repayments",
        "SETTLEMENT_BANKB": f"Inter-bank settlement with partner `{PARTNER_BANK}`"}
for key, (aid, number) in GL.items():
    md.append(f"| {aid} | `{number}` | {uses[key]} | {m2(ledger[aid])} |")
md += ["", "## Seeded money history", "",
       "| Reference | When | From | To | Amount (PHP) | Category | Channel | Result |", "| --- | --- | --- | --- | --- | --- | --- | --- |"]
def when(mins):
    d_ = mins // DAY
    return "today" if d_ == 0 else f"{d_} day{'s' if d_ != 1 else ''} ago"
for h in HISTORY:
    result = {"POSTED": "Posted", "REVERSED": "Posted, then reversed", "INFLIGHT": "Processing: hold RESERVED",
              "FAILED": f"Failed at stage {h.get('fail_stage')}: {h.get('fail_code')}"}[h["kind"]]
    md.append(f"| `{h['ref']}` | {when(h['mins'])} | `{acct[h['src']]['number']}` | `{acct[h['dst']]['number']}` | {m2(h['amount'])} | {h['category']} | {h['channel']} | {result} |")
md += ["", f"Ledger totals: debits PHP {m2(total_debit)}, credits PHP {m2(total_credit)}. {len(txn_rows)} ledger rows, each with an audit row.", "",
       "## Scenario coverage", "", "| Scenario | Use | Expected |", "| --- | --- | --- |"]
cov = [
    ("Happy-path transfer", "`juan_delacruz`, `ACC_10000004` to `ACC_10000007`", "Posted; eleven stages"),
    ("Baseline suites", "`john_doe`, `maria_santos`, `david_kim`", "Balances 50,000 / 25,000 / 100,000 as before"),
    ("Customer 360 with every widget", "`juan_delacruz`", "Savings, current, loan, history, spending by category, beneficiaries"),
    ("Risk decline, high risk", "`andrea_reyes` sends anything", "`RISK_DECLINED`, reason `HIGH_RISK`"),
    ("Risk decline, review band", "`jose_bautista` sends anything", "`RISK_DECLINED`, reason `REVIEW_REQUIRED`"),
    ("Daily limit", "`grace_pangilinan` sends more than 5,000", "`LIMIT_EXCEEDED`"),
    ("Insufficient funds", "`liza_mercado` sends more than 150", "`INSUFFICIENT_FUNDS`; balance unchanged"),
    ("Frozen account", "Any transfer from or to `ACC_10000011`", "`ACCOUNT_NOT_ACTIVE`"),
    ("Dormant account", "`ACC_10000013`", "Charged the service fee at end of day"),
    ("Closed account", "`ACC_10000014`", "`ACCOUNT_NOT_ACTIVE`; closure request APPROVED"),
    ("Closure request pending", "`ACC_10000015`, balance zero", "Admin can approve"),
    ("Closure blocked by flag", "`ACC_10000011`", "`ACCOUNT_ACTIVE_FLAG_EXISTS`; one request already REJECTED"),
    ("Closure blocked by balance", "`ACC_10000012`", "`ACCOUNT_NON_ZERO_BALANCE`"),
    ("Hold in flight", "`teresita_ramos`, `TX-SEED-0013`", "Ledger 35,000, held 3,000, available 32,000; stages 1 to 6 passed"),
    ("Hold captured / released", "Any posted APP transfer / `TX-SEED-RELEASED`", "Hold `POSTED` / `CANCELLED`"),
    ("Reversal", "`TX-SEED-0005` reversed by `RV-SEED-0001`", "Original `REVERSED`; four ledger rows; chain still verifies"),
    ("Reverse again", "`TX-SEED-0005`", "`ALREADY_REVERSED`"),
    ("Inter-bank inbound", "`IB-SEED-IN-0001`", "Credited from settlement account 9005"),
    ("Inter-bank outbound", "`TX-SEED-0011`", "Debited to settlement account 9005"),
    ("Inter-bank duplicate", "Repeat message `…000000000001`", "Same answer; no second credit"),
    ("Counter withdrawal", "`TX-SEED-0012` by `teller_alice`", "Debit against the cash GL account"),
    ("EOD standing instruction", "Instruction 1", "Executes today"),
    ("EOD standing instruction fails", "Instruction 2 (`liza_mercado`)", "Item fails; step continues"),
    ("EOD loan instalment", "Schedule 2 (`juan_delacruz`)", "Collected today"),
    ("EOD loan instalment fails", "Schedule 5 (`liza_mercado`)", "Item fails; step continues"),
    ("EOD completed run", "Batch run 2", "Six steps `COMPLETED`; seal for that date"),
    ("EOD halted run", "Batch run 1", "Step 5 `FAILED`, step 6 `SKIPPED`, run `HALTED`"),
    ("First-time beneficiary", "`ricardo_magbanua`", "No beneficiaries yet"),
    ("Inactive beneficiary", "`grace_pangilinan`", "One beneficiary with `active = false`"),
    ("External beneficiary", "`juan_delacruz`", f"One beneficiary at `{PARTNER_BANK}`"),
    ("Inactive login", "`lourdes_salazar`", "Sign-in refused"),
    ("Staff acting for a customer", "`teller_alice`", "May send from any account"),
    ("KYC update pending / approved", "`jose_bautista` / `grace_pangilinan`", "Existing React console feature"),
    ("Flag removed", "`ACC_10000008`", "One flag with status `REMOVED`"),
    ("Notifications read and unread", "`juan_delacruz`", "Older ones read, recent ones unread"),
    ("Audit chain verification", "Any account", "Intact"),
    ("GL reconciliation", "Whole ledger", "Total debits equal total credits"),
]
for s, u, e in cov:
    md.append(f"| {s} | {u} | {e} |")
md += ["", "## Files", "",
       "| File | Loaded by | Status |", "| --- | --- | --- |",
       "| `database/postgres-cloud/01..05` | `postgres-cloud-db` (Compose profile `expansion`) | Live: nothing else uses these databases yet |",
       "| `database/oracle/` | `oracle-core-db` | Master System of Record (Rebuilt 22-Table Architecture) |",
       "| `database/next/oracle/` | Backup / Next mirror | Mirrors database/oracle/ |",
       "| `database/next/postgres/` | Not mounted yet | Replaces `database/postgres/` at the same moment; its audit rows match the rebuilt Oracle seed |",
       "", "## Switching the on-premises databases to the target set", "",
       "**While you are still moving your service (per developer, opt-in):**", "",
       "```bash",
       "docker compose -f docker-compose.yml -f docker-compose.target.yml --profile app --profile expansion up -d --build",
       "```", "",
       "`docker-compose.target.yml` mounts the target folders on separate data volumes, so your normal stack is untouched. A service that still maps `ROLE`, `\"USER\"` or the credential columns on `CUSTOMER` will fail against it; that is the signal it has not been moved yet.", "",
       "**For everyone (Dev 8, once `auth-service`, `account-service` and `ledger-service` all run on the target schema):**", "",
       "1. In `docker-compose.yml`, point the Oracle volume at `./database/next/oracle` and the audit PostgreSQL volume at `./database/next/postgres`.",
       "2. Delete `docker-compose.target.yml`, and move `database/next/*` over `database/oracle` and `database/postgres`.",
       "3. Everyone removes the old data volumes so the init scripts run again: `docker compose down -v`.", "",
       "## Additions beyond the ERD", "",
       "- `txn.interbank_message`: stores each inter-bank `messageId` so a repeat returns the first answer.",
       "- `channel_profile.username` and `email`: the profile response returns both, and `customer-service` cannot read `identity_db`.",
       "- `TRANSACTION.reverses_transaction_id` is unique, so the database itself refuses a second reversal.",
       "- Role `ROLE_SERVICE` for the service token on internal calls.", ""]
write(os.path.join(DB, "seed", "SEED_REFERENCE.md"), "\n".join(md))

print(f"\nOK: {len(CUSTOMERS)} members, {len(acct)} accounts, {len(txn_rows)} ledger rows, "
      f"debits = credits = PHP {m2(total_debit)}")
