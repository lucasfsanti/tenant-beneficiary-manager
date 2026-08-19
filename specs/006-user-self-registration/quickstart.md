# Quickstart: User Self-Registration (Bootstrap Entrypoint)

Validates that the account-creation page/endpoint correctly bootstraps the first System Admin,
correctly restricts every later account to the simplest role, and correctly resists tampering —
per spec FR-001–011 and Edge Cases.

## Prerequisites

- A way to start the stack with **no** demo data seeded, so the platform is genuinely empty —
  feature 005's toggle: `SPRING_PROFILES_ACTIVE=no-demo docker compose up -d`.
- `curl` and `jq`/`python3 -m json.tool` for the manual checks below.

## 1. Bootstrap: the very first account becomes System Admin (spec FR-003, US1)

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"first-operator","password":"a-real-password"}'
```

**Expected**: `204`.

```bash
curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"username":"first-operator","password":"a-real-password"}'
```

**Expected**: `200` with a token whose `user.isSystemAdmin` is `true` and `user.tenants` is an
empty array.

## 2. Every account after the first is Normal (spec FR-005, US2)

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"second-person","password":"another-real-password"}'
curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"username":"second-person","password":"another-real-password"}'
```

**Expected**: registration `204`; login `200` with `user.isSystemAdmin: false` and
`user.tenants: []`.

## 3. Duplicate usernames are rejected (spec FR-002, SC-002)

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"second-person","password":"irrelevant"}'
```

**Expected**: `409`.

## 4. Client-supplied role hints are ignored (spec FR-011, US3)

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"attempted-admin","password":"whatever","isSystemAdmin":true,"role":"ADMIN"}'
curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"username":"attempted-admin","password":"whatever"}'
```

**Expected**: registration `204` (extra fields are simply not part of the accepted request shape
and are ignored); login `200` with `user.isSystemAdmin: false` — never `true`.

## 5. Concurrent bootstrap race (spec Edge Cases)

On a **freshly emptied** database (`docker compose down -v` and restart with no-demo, so
`app_user` is genuinely empty again), fire two registrations at once:

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" -d '{"username":"racer-a","password":"pw"}' &
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" -d '{"username":"racer-b","password":"pw"}' &
wait
```

**Expected**: both return `204`; logging in as each afterward shows **exactly one** of the two
with `isSystemAdmin: true` and the other with `isSystemAdmin: false` — never both `true`.

## 6. Automated regression pass

```bash
cd backend
JAVA_HOME=~/.local/opt/jdk-21.0.4+7 mvn clean test -Dnet.bytebuddy.experimental=true
```

**Expected**: full suite green, including the new `UserSelfRegistrationTest`.

## 7. Tear down

```bash
docker compose down
```
