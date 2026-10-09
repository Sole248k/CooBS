#!/usr/bin/env python3
"""Derives the Azure SQL (SQL Server) cloud scripts from the PostgreSQL ones.

    python database/seed/pg_cloud_to_tsql.py

Reads   database/postgres-cloud/02..05_*.sql   (the source of truth while both engines exist)
Writes  database/azure-sql/01..05_*.sql

The PostgreSQL scripts use a small, regular dialect, and this script knows exactly that dialect.
Anything it does not recognise stops the run with the file and line, so a new construct in the
PostgreSQL scripts is never translated by guesswork. Add a rule here when that happens.

What changes, and why:

  TIMESTAMPTZ              DATETIME2(6) in identity_db and customer_db, whose entities are LocalDateTime;
                           DATETIMEOFFSET(6) in transaction_db and notification_db, whose entities are
                           Instant and are checked by ddl-auto=validate.
  now() -/+ interval 'n u'  nested DATEADD(<unit>, -/+n, <clock>); units are milliseconds to days
  BOOLEAN, TRUE, FALSE     BIT, 1, 0
  UUID                     UNIQUEIDENTIFIER
  GENERATED ... IDENTITY   IDENTITY(start, 1), with SET IDENTITY_INSERT around seed rows that give ids
  setval(..., n, false)    DBCC CHECKIDENT(..., RESEED, n - 1), so the next id is n
  UNIQUE on a nullable     a filtered unique index. SQL Server's UNIQUE allows one NULL only;
  column                   PostgreSQL allows many.
  CREATE ... IF NOT EXISTS a guard on OBJECT_ID, sys.schemas or sys.indexes
  \\connect <db>            dropped. Azure SQL has no USE across databases: connect to the database.
  TEXT                     VARCHAR(MAX). SQL Server refuses TEXT under a UTF-8 collation (error 4188).
                           SagaInstance declares columnDefinition = "TEXT", but validate compares the
                           JDBC type and takes any character type for a String.
"""
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SRC = os.path.join(ROOT, "database", "postgres-cloud")
OUT = os.path.join(ROOT, "database", "azure-sql")

# One database per file. The clock and the timestamp type follow the Java type of the entities.
FILES = {
    "02_identity_db.sql": dict(db="identity_db", ts="DATETIME2(6)", clock="SYSUTCDATETIME()"),
    "03_customer_db.sql": dict(db="customer_db", ts="DATETIME2(6)", clock="SYSUTCDATETIME()"),
    "04_transaction_db.sql": dict(db="transaction_db", ts="DATETIMEOFFSET(6)", clock="SYSDATETIMEOFFSET()"),
    "05_notification_db.sql": dict(db="notification_db", ts="DATETIMEOFFSET(6)", clock="SYSDATETIMEOFFSET()"),
}

# UTF-8 so VARCHAR columns keep characters such as the peso sign, which notification-service writes.
# Case-insensitive, unlike PostgreSQL: usernames, emails and references compare without regard to case.
COLLATION = "Latin1_General_100_CI_AS_SC_UTF8"

# Constraints PostgreSQL accepts and SQL Server does not, rewritten by hand.
CHECK_REWRITES = {
    "CHECK ((user_type = 'CUSTOMER') = (customer_id IS NOT NULL))":
        "CHECK ((user_type = 'CUSTOMER' AND customer_id IS NOT NULL) OR (user_type <> 'CUSTOMER' AND customer_id IS NULL))",
}

COLUMN_TYPE = re.compile(r"^(BIGINT|INT|VARCHAR\(\d+\)|NUMERIC\(\d+, ?\d+\)|TIMESTAMPTZ|BOOLEAN|UUID|TEXT)(?=\s|$)")


class Unknown(Exception):
    pass


def split_literals(text):
    """Splits SQL into ('code', s) and ('str', s) pieces; a string piece keeps its quotes."""
    parts, buf, i = [], "", 0
    while i < len(text):
        if text[i] != "'":
            buf += text[i]
            i += 1
            continue
        if buf:
            parts.append(("code", buf))
            buf = ""
        j = i + 1
        while True:
            if j >= len(text):
                raise Unknown("unterminated string literal")
            if text[j] == "'":
                if text[j + 1:j + 2] == "'":
                    j += 2
                    continue
                break
            j += 1
        parts.append(("str", text[i:j + 1]))
        i = j + 1
    if buf:
        parts.append(("code", buf))
    return parts


UNITS = {"millisecond": "MILLISECOND", "second": "SECOND", "minute": "MINUTE", "hour": "HOUR", "day": "DAY"}
INTERVAL_START = re.compile(r"now\(\)\s*([+-])\s*interval\s*$", re.IGNORECASE)
INTERVAL_NEXT = re.compile(r"\s*([+-])\s*interval\s*$", re.IGNORECASE)
INTERVAL_VALUE = re.compile(r"'(\d+) (millisecond|second|minute|hour|day)s?'", re.IGNORECASE)


def values(line, clock):
    """Translates the VALUES part of an INSERT: clock arithmetic, booleans and non-ASCII literals."""
    parts = split_literals(line)
    out = []
    i = 0
    while i < len(parts):
        kind, text = parts[i]
        if kind == "str":
            out.append(("N" if any(ord(ch) > 127 for ch in text) else "") + text)
            i += 1
            continue
        # now() - interval '5 minutes' + interval '80 milliseconds'  becomes nested DATEADD calls.
        first = INTERVAL_START.search(text)
        if first and i + 1 < len(parts) and parts[i + 1][0] == "str":
            expr, sign, j = clock, first.group(1), i + 1
            while True:
                amount = INTERVAL_VALUE.fullmatch(parts[j][1])
                if not amount:
                    raise Unknown("interval not known to the translator: " + parts[j][1])
                unit = UNITS[amount.group(2).lower()]
                expr = f"DATEADD({unit}, {'-' if sign == '-' else ''}{amount.group(1)}, {expr})"
                more = INTERVAL_NEXT.fullmatch(parts[j + 1][1]) if j + 1 < len(parts) and parts[j + 1][0] == "code" else None
                if more and j + 2 < len(parts) and parts[j + 2][0] == "str":
                    sign, j = more.group(1), j + 2
                    continue
                break
            out.append(text[:first.start()] + expr)
            i = j + 1
            continue
        if "::" in text or "interval" in text.lower():
            raise Unknown("cast or interval outside the known form")
        text = text.replace("now()", clock)
        text = re.sub(r"\bTRUE\b", "1", text)
        text = re.sub(r"\bFALSE\b", "0", text)
        out.append(text)
        i += 1
    return "".join(out)


def table(name, body, cfg, identity):
    """Translates the lines between CREATE TABLE ( and );"""
    out, indexes = [], []
    for raw in body:
        line = raw.rstrip()
        if not line.strip() or line.strip().startswith("--"):
            out.append(line)
            continue
        comma = "," if line.endswith(",") else ""
        text = line[:-1] if comma else line
        stripped = text.strip()
        indent = text[:len(text) - len(text.lstrip())]

        if stripped.startswith("CHECK ("):
            if stripped in CHECK_REWRITES:
                stripped = CHECK_REWRITES[stripped]
            elif ") = (" in stripped:
                raise Unknown("a CHECK that compares two conditions needs a rewrite: " + stripped)
            out.append(indent + stripped + comma)
            continue
        if stripped.startswith("UNIQUE ("):
            out.append(indent + stripped + comma)
            continue

        column, _, rest = stripped.partition(" ")
        if not COLUMN_TYPE.match(rest):
            raise Unknown(f"column type not known to the translator: {stripped}")

        start = re.search(r"GENERATED BY DEFAULT AS IDENTITY(?: \(START WITH (\d+)\))?", rest)
        if start:
            identity[name.lower()] = column.lower()
            rest = rest.replace(start.group(0), f"IDENTITY({start.group(1) or 1},1)")
        rest = re.sub(r"^TIMESTAMPTZ\b", cfg["ts"], rest)
        rest = re.sub(r"^BOOLEAN\b", "BIT", rest)
        rest = re.sub(r"^UUID\b", "UNIQUEIDENTIFIER", rest)
        rest = re.sub(r"^TEXT\b", "VARCHAR(MAX)", rest)
        rest = rest.replace("DEFAULT now()", "DEFAULT " + cfg["clock"])
        rest = rest.replace("DEFAULT TRUE", "DEFAULT 1").replace("DEFAULT FALSE", "DEFAULT 0")
        if "now()" in rest or "::" in rest:
            raise Unknown("default not known to the translator: " + stripped)

        if re.search(r"\bUNIQUE\b", rest) and "NOT NULL" not in rest and "PRIMARY KEY" not in rest:
            rest = re.sub(r"\s*\bUNIQUE\b", "", rest)
            index = f"uq_{name.split('.')[-1]}_{column}"
            indexes.append(
                f"IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'{index}' AND object_id = OBJECT_ID(N'{name}'))\n"
                f"    CREATE UNIQUE INDEX {index} ON {name}({column}) WHERE {column} IS NOT NULL;")
        out.append(indent + column + " " + rest + comma)

    text = [f"IF OBJECT_ID(N'{name}', N'U') IS NULL", f"CREATE TABLE {name} ("] + out + [");"]
    if indexes:
        text += ["-- PostgreSQL's UNIQUE lets every row be NULL; SQL Server needs a filtered index for that."] + indexes
    return text


def translate(src_name, cfg):
    with open(os.path.join(SRC, src_name), encoding="utf-8") as f:
        lines = [l.rstrip("\r\n") for l in f]

    out = [
        f"-- GENERATED by database/seed/pg_cloud_to_tsql.py from database/postgres-cloud/{src_name}.",
        "-- Do not edit by hand; change the PostgreSQL script or the translator and re-run.",
        f"-- Target: Azure SQL Database or SQL Server 2022, database {cfg['db']}. Connect to that database first.",
        "SET NOCOUNT ON;",
        "-- Needed by the filtered unique indexes; sqlcmd leaves it off by default.",
        "SET QUOTED_IDENTIFIER ON;",
        "",
    ]
    identity = {}      # table -> identity column, lower case
    open_table = None  # table with IDENTITY_INSERT currently ON

    def close_identity():
        nonlocal open_table
        if open_table:
            out.append(f"SET IDENTITY_INSERT {open_table} OFF;")
            open_table = None

    i = 0
    while i < len(lines):
        line = lines[i]
        where = f"{src_name}:{i + 1}"
        try:
            if line.startswith("-- GENERATED by"):
                pass
            elif not line.strip() or line.lstrip().startswith("--"):
                out.append(line)
            elif line.startswith("\\connect"):
                pass
            elif m := re.fullmatch(r"CREATE SCHEMA IF NOT EXISTS (\w+);(\s*--.*)?", line):
                close_identity()
                out.append(f"IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = N'{m.group(1)}') "
                           f"EXEC(N'CREATE SCHEMA {m.group(1)}');{m.group(2) or ''}")
            elif m := re.fullmatch(r"CREATE TABLE IF NOT EXISTS ([\w.]+) \(", line):
                close_identity()
                body = []
                i += 1
                while lines[i] != ");":
                    body.append(lines[i])
                    i += 1
                out += table(m.group(1), body, cfg, identity)
            elif m := re.fullmatch(r"CREATE INDEX IF NOT EXISTS (\w+) ON ([\w.]+)\((.+)\);", line):
                close_identity()
                out.append(f"IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'{m.group(1)}' "
                           f"AND object_id = OBJECT_ID(N'{m.group(2)}'))")
                out.append(f"    CREATE INDEX {m.group(1)} ON {m.group(2)}({m.group(3)});")
            elif m := re.match(r"INSERT INTO ([\w.]+) \(([^)]*)\)(.*)$", line):
                # The column list is on the first line; VALUES follow on it or on the next lines.
                name, columns = m.group(1), [c.strip().lower() for c in m.group(2).split(",")]
                if "VALUES" not in m.group(3) and not lines[i + 1].startswith("VALUES ("):
                    raise Unknown("INSERT with no VALUES on this line or the next")
                wants = name if identity.get(name.lower()) in columns else None
                if wants != open_table:
                    close_identity()
                    if wants:
                        out.append(f"SET IDENTITY_INSERT {wants} ON;")
                        open_table = wants
                out.append(f"INSERT INTO {name} ({m.group(2)})" + values(m.group(3), cfg["clock"]))
                last = line
                while not last.rstrip().endswith(");"):
                    i += 1
                    last = lines[i]
                    out.append(values(last, cfg["clock"]))
            elif m := re.fullmatch(r"SELECT setval\(pg_get_serial_sequence\('([\w.]+)', '(\w+)'\), (\d+), false\);", line):
                close_identity()
                if identity.get(m.group(1).lower()) != m.group(2).lower():
                    raise Unknown("setval on a column that is not the table's identity")
                out.append(f"DBCC CHECKIDENT (N'{m.group(1)}', RESEED, {int(m.group(3)) - 1}) WITH NO_INFOMSGS;")
            else:
                raise Unknown("statement not known to the translator")
        except Unknown as e:
            sys.exit(f"{where}: {e}\n    {line}")
        i += 1
    close_identity()
    return "\n".join(out).rstrip("\n") + "\n"


def create_databases():
    rows = [
        "-- GENERATED by database/seed/pg_cloud_to_tsql.py. Do not edit by hand.",
        "-- For a local SQL Server container only. In Azure the databases are created by the Bicep,",
        "-- with the same collation, and this file is not run.",
        "--",
        f"-- {COLLATION}: UTF-8, so VARCHAR columns keep every character the services write,",
        "-- and case-insensitive, which PostgreSQL is not.",
    ]
    for cfg in FILES.values():
        rows.append(f"IF DB_ID(N'{cfg['db']}') IS NULL CREATE DATABASE {cfg['db']} COLLATE {COLLATION};")
    return "\n".join(rows) + "\n"


def write(name, text):
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    print("wrote", os.path.relpath(path, ROOT).replace(os.sep, "/"))


if __name__ == "__main__":
    write("01_create_databases.sql", create_databases())
    for src_name, cfg in FILES.items():
        write(src_name, translate(src_name, cfg))
