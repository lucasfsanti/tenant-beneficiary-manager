# Quickstart: Clearer, Expanded Demo Seed Data

Validates that the relabeled/expanded demo data seeds correctly, relationships are preserved
(spec FR-005), and demo-data seeding is genuinely optional (spec FR-014/015).

## Prerequisites

- This feature edits an already-applied changeset (`003-role-system.sql`) and adds new ones —
  anyone with an existing local `tenant-beneficiary-manager_db-data` Docker volume must recreate
  it once (research.md §2). From the repo root: `docker compose down -v`.
- `curl` and `jq`/`python3 -m json.tool` for the manual checks below.

## 1. Default startup keeps demo data (spec FR-015, SC-005)

```bash
docker compose up -d --build
timeout 60 bash -c 'until curl -sf http://localhost:8080/actuator/health >/dev/null; do sleep 1; done'
```

Log in as each renamed/new user and confirm access matches research.md §3/§4's table:

```bash
curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"username":"User 3 - ADMIN","password":"demo123"}'
```

**Expected**: `200` with a token for every seeded user (`User 1 - NORMAL` through
`User 6 - NORMAL`), all using password `demo123`.

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"username":"User 3 - ADMIN","password":"demo123"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")
curl -s http://localhost:8080/api/tenants -H "Authorization: Bearer $TOKEN"
```

**Expected**: `200` with exactly 4 tenants named `"Tenant 1"` through `"Tenant 4"`.

## 2. Relationships are unchanged (spec FR-005)

- `User 2 - TENANT ADMIN` can update Tenant 1 but not Tenant 2, Tenant 3, or Tenant 4 (still
  exactly the standing `bruno` had before the rename).
- `User 1 - NORMAL` is a plain member of Tenant 1 and Tenant 2 only (still exactly `ana`'s
  standing).

## 3. Automated regression pass

```bash
cd backend
JAVA_HOME=~/.local/opt/jdk-21.0.4+7 mvn clean test -Dnet.bytebuddy.experimental=true
```

**Expected**: full suite green — same count as before this feature, since ids/relationships are
unchanged and only 3 files needed a literal-value edit (data-model.md).

## 4. Starting without demo data (spec FR-014)

```bash
docker compose down
SPRING_PROFILES_ACTIVE=no-demo docker compose up -d
timeout 60 bash -c 'until curl -sf http://localhost:8080/actuator/health >/dev/null; do sleep 1; done'
curl -s http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"username":"User 3 - ADMIN","password":"demo123"}'
```

**Expected**: the app starts healthy (schema fully migrated), but the login attempt fails — no
demo users exist. This confirms demo-data changesets were skipped while structural changesets
still ran.

## 5. Tear down

```bash
docker compose down
```
