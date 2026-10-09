# Cloud databases on Azure SQL

The four cloud databases (`identity_db`, `customer_db`, `transaction_db`, `notification_db`) as SQL Server scripts, for Azure SQL Database and for a local SQL Server 2022 container.

**Status: first load attempted on 9 October 2026; it stopped at `payload TEXT` in `04_transaction_db.sql`, which is fixed. No complete load yet.**

## Where the scripts come from

They are generated. Do not edit them.

```
python database/seed/pg_cloud_to_tsql.py
```

The translator reads `database/postgres-cloud/02..05_*.sql` and writes this folder. While both engines exist, the PostgreSQL scripts are the source of truth: change those, then re-run. The translator stops with the file and line if it meets a construct it does not know, so nothing is translated by guesswork.

| File | Database | Run in Azure |
| --- | --- | --- |
| `01_create_databases.sql` | Creates the four databases | No. The Bicep creates them |
| `02_identity_db.sql` | `identity_db` | Yes |
| `03_customer_db.sql` | `customer_db` | Yes |
| `04_transaction_db.sql` | `transaction_db`, schemas `txn` and `saga` | Yes |
| `05_notification_db.sql` | `notification_db` | Yes |

## Loading

Each script must run while connected to its own database. Azure SQL has no `USE` across databases, so the scripts contain none.

```
sqlcmd -S <server> -U <login> -P <password> -I -b -x -f 65001 -d identity_db     -i database/azure-sql/02_identity_db.sql
sqlcmd -S <server> -U <login> -P <password> -I -b -x -f 65001 -d customer_db     -i database/azure-sql/03_customer_db.sql
sqlcmd -S <server> -U <login> -P <password> -I -b -x -f 65001 -d transaction_db  -i database/azure-sql/04_transaction_db.sql
sqlcmd -S <server> -U <login> -P <password> -I -b -x -f 65001 -d notification_db -i database/azure-sql/05_notification_db.sql
```

`-I` turns on quoted identifiers, which the filtered unique indexes need (the scripts also set it themselves), `-b` stops at the first error, `-x` turns off `$(...)` substitution, and `-f 65001` reads the files as UTF-8. For a local container, run `01_create_databases.sql` first with no `-d`, and add `-C` to trust its self-signed certificate.

The scripts load into empty databases. Like the PostgreSQL ones, they are not meant to be run twice: the seed rows would collide. Rebuild from empty on any schema change; there are no migrations.

## The databases must use a UTF-8 collation

Create each database with `Latin1_General_100_CI_AS_SC_UTF8`. `01_create_databases.sql` does this locally; in Azure it is the `collation` property of the database in the Bicep.

- **Why UTF-8:** the columns are `VARCHAR`, which is what Hibernate expects for a Java `String`. With the default collation a `VARCHAR` cannot hold the peso sign that notification-service writes; it would be stored as `?`.
- **What changes from PostgreSQL:** this collation is case-insensitive. Usernames, emails and reference numbers now compare and stay unique without regard to case.

## What differs from the PostgreSQL scripts

| PostgreSQL | Here | Reason |
| --- | --- | --- |
| `TIMESTAMPTZ` in `identity_db`, `customer_db` | `DATETIME2(6)` | The entities are `LocalDateTime` |
| `TIMESTAMPTZ` in `transaction_db`, `notification_db` | `DATETIMEOFFSET(6)` | The entities are `Instant`, and these services run `ddl-auto=validate` |
| `UUID` | `UNIQUEIDENTIFIER` | Hibernate's type for `UUID` on SQL Server |
| `BOOLEAN` | `BIT` | |
| `UNIQUE` on a nullable column | A filtered unique index | SQL Server's `UNIQUE` allows a single `NULL`; three columns rely on many: `app_user.customer_id`, `txn_transaction.idempotency_key`, `notifications.event_id` |
| Identity with seeded ids | `IDENTITY` plus `SET IDENTITY_INSERT`, then `DBCC CHECKIDENT` | The next generated id is the same as in PostgreSQL |
| A `CHECK` comparing two conditions | Rewritten with `AND` and `OR` | T-SQL has no boolean equality |
| `saga.saga_instance.payload TEXT` | `VARCHAR(MAX)` | SQL Server refuses `TEXT` in a database with a UTF-8 collation (error 4188, found on the first load). `SagaInstance` still declares `columnDefinition = "TEXT"`; `validate` compares the JDBC type, and any character type matches a `String` |

## Not verified

- That every script loads. Nothing here has been run against SQL Server.
- That `validate` passes in transaction-service, orchestration-service and notification-service against these tables.
- That auth-service and customer-service read and write `DATETIME2` columns correctly through `LocalDateTime`.
- That `role` is accepted as a table name without brackets.

## What still has to change elsewhere

- Each of the five services needs the SQL Server JDBC driver in its `pom.xml`. The Kubernetes manifests already supply the connection settings.
- Compose still starts PostgreSQL for these databases, and the pipeline's seed check loads the PostgreSQL scripts.
- `batch-service` opens its own PostgreSQL connection to `notification_db`; that step breaks once the database is SQL Server.
