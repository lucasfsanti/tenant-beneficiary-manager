# Quickstart: Validate the Liquibase Migration

Validates the feature against `spec.md`'s User Stories and Success Criteria.
References `data-model.md` for changeset details and `research.md` for the
underlying decisions — not duplicated here.

## Prerequisites

- Docker and Docker Compose (same as the project's normal prerequisites)
- No pre-existing `db-data` volume from a previous Flyway-managed run (see
  "One-time cutover step" below if you have one)

## One-time cutover step (existing local checkouts only)

If you have previously run this project with `docker-compose up` before this
change, remove the old Flyway-managed volume once:

```bash
docker-compose down -v
```

Skip this on a fresh clone — there is no existing volume to remove.

## Scenario 1 — Zero-touch startup produces the same schema and seed data (US1, SC-001, SC-002)

```bash
docker-compose up --build
```

Expected: all three services (`db`, `backend`, `frontend`) report healthy, with
no manual command run beyond `docker-compose up`. Then verify schema and seed
data:

```bash
docker-compose exec db psql -U tbm -d tbm -c "\dt"
# Expect: app_user, beneficiario, databasechangelog, databasechangeloglock,
#         pessoa, tenant, user_tenant_membership

docker-compose exec db psql -U tbm -d tbm -c "SELECT count(*) FROM tenant;"
# Expect: 2

docker-compose exec db psql -U tbm -d tbm -c "SELECT count(*) FROM beneficiario;"
# Expect: 4
```

Also confirm the demo login still works end-to-end (unchanged app behavior):
log into `http://localhost:8081` as `ana` / `demo123` and confirm both
"Tenant Alfa" and "Tenant Beta" are selectable (per `V2__seed_demo_data.sql`
membership rows, now applied via Liquibase).

## Scenario 2 — Restart does not re-apply or fail (US1 acceptance scenario 3, SC-004)

```bash
docker-compose restart backend
docker-compose logs backend --tail=50
```

Expected: backend starts healthy again; logs show Liquibase reporting no new
changesets to apply (no `CREATE TABLE`/`INSERT` statements re-run), and no
startup error.

**Extended check (US1 acceptance scenario 4)**: add a throwaway third changeset
to `db.changelog-master.yaml` (e.g., a harmless `SELECT 1;` formatted-SQL file)
and restart again. Expected: only the new changeset is applied — the first two
are not re-run — and the backend starts healthy. Remove the throwaway changeset
afterward.

## Scenario 3 — Flyway is fully removed (US2, SC-003)

Search scope is the current working tree only (source files and build output),
not version-control history — see spec.md SC-003.

```bash
grep -ri "flyway" backend/pom.xml backend/src/main/resources/application.yml
# Expect: no matches

find backend/src/main/resources/db -iname "V*__*.sql"
# Expect: no matches (old db/migration files gone)

find backend/src/main/resources/db/changelog -type f
# Expect: db.changelog-master.yaml, 001-schema.sql, 002-seed-demo-data.sql
```

## Scenario 4 — Failure is loud, not silent (FR-005)

Manually edit `backend/src/main/resources/db/changelog/001-schema.sql` after at
least one successful startup (e.g., change a comment inside the already-applied
changeset), then:

```bash
docker-compose restart backend
docker-compose logs backend --tail=50
```

Expected: backend container fails to become healthy; logs show a Liquibase
checksum-validation error, and the application context fails to start (not a
silent partial startup). Revert the edit afterward.

## Scenario 5 — Documentation matches reality (US3, acceptance scenario 2)

`README.md` is currently the only project documentation that describes how the
database schema is managed (confirmed by a repo-wide search); if that changes
in the future, every such location must be checked, not just this one.

```bash
grep -i "flyway" README.md
# Expect: no matches

grep -i "liquibase" README.md
# Expect: at least one match, describing schema/seed startup behavior
```

## Cleanup

```bash
docker-compose down -v
```
