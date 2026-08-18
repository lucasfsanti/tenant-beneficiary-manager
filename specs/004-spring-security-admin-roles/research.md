# Research: Migrate Admin Role Verification to Spring Security Authorization

## §1. Representing System Admin standing

**Decision**: A global `GrantedAuthority` (`ROLE_SYSTEM_ADMIN`), granted per request by
`JwtAuthenticationFilter` after a fresh `AppUserRepository.findById(userId)` lookup, in addition
to the existing `ROLE_USER` authority every authenticated request already receives.

**Rationale**: System Admin is platform-wide, so it maps naturally onto a single static role —
exactly what `hasRole('SYSTEM_ADMIN')` is for in `@PreAuthorize`/`authorizeHttpRequests`. Doing
the lookup in the filter (rather than at login/token-issuance time) preserves the
per-request freshness the Clarifications session (2026-08-18) and FR-004 require: a revocation
takes effect on the very next request, not on next login.

**Alternatives considered**:
- *Embed the role as a JWT claim at login.* Rejected — the JWT is valid for 480 minutes
  (`app.jwt.expiration-minutes`, `JwtService`); a claim fixed at issuance would let a revoked
  System Admin keep acting until their token expires, directly regressing the freshness
  guarantee the clarification settled on.
- *A full `UserDetailsService`-backed `AuthenticationProvider`.* Rejected as more machinery than
  a single boolean flag needs — authentication here is JWT-based, not a login-time
  username/password lookup, so there's no natural place for `UserDetailsService` to plug in
  without restructuring the existing filter chain.

## §2. Representing Tenant Admin standing

**Decision**: A small `@Component` (`TenantAuthorization`), with a method
`isTenantAdmin(UUID tenantId)` that reads the caller's id from the current
`SecurityContextHolder` authentication and delegates to the existing
`UserTenantMembershipRepository.existsByUser_IdAndTenant_IdAndIsTenantAdminTrue(userId, tenantId)`
query. Referenced from `@PreAuthorize` SpEL as
`hasRole('SYSTEM_ADMIN') or @tenantAuthorization.isTenantAdmin(#tenantId)`.

**Rationale**: Tenant Admin standing is scoped to one tenant per membership row, so it cannot be
a single static authority the way System Admin can — the check needs the specific tenant id being
acted on. Spring Security's own docs recommend either implementing `PermissionEvaluator` or
referencing a plain bean method from SpEL for exactly this kind of resource-scoped check; the
plain-bean route needs no new interface implementation, keeps the method name as the
documentation (`isTenantAdmin`), and reuses a repository query this codebase already has.

**Alternatives considered**:
- *Implement `PermissionEvaluator`.* Rejected as more generic machinery
  (`hasPermission(Authentication, Serializable, String, Object)`) than a single permission type
  needs.
- *Grant one authority per tenant the caller administers (e.g. `TENANT_ADMIN_<uuid>`), computed
  at authentication time.* Rejected — same staleness problem as §1's rejected alternative (fixed
  at login/token time, not fresh per request), plus an unbounded-size authority list.

## §3. Declaring the checks: `@PreAuthorize` at the service layer

**Decision**: Enable `@EnableMethodSecurity` (default `prePostEnabled = true`) on the existing
`SecurityConfig` class, and apply `@PreAuthorize` directly on the 12 service methods that
currently perform a manual check — see the Protected Operations table in `data-model.md` for the
full method-by-method mapping.

**Rationale**: Verified (not assumed) by inspecting `backend/pom.xml`: `spring-boot-starter-security`
is already a direct dependency, and `spring-security-config` — the module that provides
`@EnableMethodSecurity`/`@PreAuthorize` — is one of its transitive dependencies, so no `pom.xml`
change is required to use it. Placing
the annotation directly on each method keeps the required standing visible at the method
signature, satisfying FR-008 ("discoverable without reading service method bodies"). Method-level
placement (rather than URL-pattern rules in `SecurityConfig`'s `authorizeHttpRequests`) is
necessary for 7 of the 12 methods, which need the specific `tenantId` being acted on — a
comparison `authorizeHttpRequests` cannot express against a static URL pattern.

**Alternatives considered**:
- *Mix URL-pattern rules (for the 5 System-Admin-only methods) with method security (for the
  other 7).* Rejected for consistency — splitting the same concept (admin verification) across
  two different mechanisms and two different files would undermine FR-008's "single, central"
  framing rather than serve it, even though both halves would still work correctly.

**Verification note (not a decision, confirm during implementation)**: `@PreAuthorize("...
#tenantId ...")` requires the real parameter name to be available via reflection. Spring Boot's
`spring-boot-starter-parent` (in use, 3.3.4) sets `<parameters>true</parameters>` on
`maven-compiler-plugin` by default, so this should resolve without extra annotations. If a build
shows otherwise, add `@P("tenantId")` to the affected parameters — a zero-risk, purely additive
fallback.

## §4. Mapping authorization denials to the existing error contract

**Decision**: Add a `@ExceptionHandler(AccessDeniedException.class)` method to the existing
`ApiExceptionHandler` (`@RestControllerAdvice`), producing the same RFC 7807 `ProblemDetail`
shape — `403`, title "Acesso negado" — that `ApiExceptionHandler.handleForbidden(ForbiddenException)`
produced before this migration. `ForbiddenException` and its old mapping are removed: a repo-wide
search confirmed the three services being migrated (`TenantService`, `MembershipService`,
`AppUserService`) were its only callers.

**Rationale (corrected during implementation — see note below)**: `@PreAuthorize` denials throw
`org.springframework.security.access.AccessDeniedException`. Every `@PreAuthorize` check in this
app guards a service method invoked *during* MVC dispatch (called from a controller method), so
the exception is thrown while Spring MVC's `ExceptionHandlerExceptionResolver` — which is what
`@RestControllerAdvice`/`@ExceptionHandler` methods plug into — is still on the stack. That
resolver gets first chance at it, resolves it via the specific `AccessDeniedException` handler
below, and the request completes normally from the servlet container's point of view. It never
propagates back out to Spring Security's filter-level `ExceptionTranslationFilter`, so a
filter-level `AccessDeniedHandler` bean on `SecurityConfig` would never actually run for any
denial this app produces — `ExceptionTranslationFilter` only sees exceptions that escape the
DispatcherServlet unhandled (e.g., from `authorizeHttpRequests`-based URL rules evaluated before
MVC dispatch begins, which this app doesn't use for role checks).

**Implementation note**: the original version of this decision (written during planning)
registered a filter-level `AccessDeniedHandler` bean on `SecurityConfig` instead. That compiled
and looked correct, but every denial-path integration test failed with `500` instead of `403`
once implemented — `ApiExceptionHandler`'s existing catch-all `@ExceptionHandler(Exception.class)`
was intercepting `AccessDeniedException` first (any exception is an `Exception`), routing it to
the generic 500 handler before the filter-level handler ever got a chance to run. The fix was to
add the specific `AccessDeniedException` handler *inside* `ApiExceptionHandler` itself — Spring
picks the most specific matching `@ExceptionHandler` in a class, so it now wins over the catch-all
— which is also more consistent with how every other exception in this codebase is already mapped
(one handler method per exception type, all in one class), rather than splitting the same
"denial" concept across two different mechanisms in two different files.

**Alternatives considered**:
- *Keep `ForbiddenException` and have the new handler delegate to `ApiExceptionHandler`'s logic.*
  Rejected as needless indirection once nothing in the codebase throws `ForbiddenException`
  anymore — the two handlers would produce identical output through two different paths for no
  reason.
- *A filter-level `AccessDeniedHandler` bean on `SecurityConfig`.* Tried first; rejected once
  implementation showed it never runs while `ApiExceptionHandler`'s catch-all handler exists (see
  implementation note above) — keeping it would have been dead code.

## §5. Simplifying `TenantContextFilter`'s System-Admin bypass

**Decision**: `TenantContextFilter`'s existing bypass condition (skip the per-tenant membership
check when the caller is a System Admin) switches from its own
`appUserRepository.findById(...).map(AppUser::isSystemAdmin)` lookup to reading the
`ROLE_SYSTEM_ADMIN` authority already present on the request's `Authentication` (populated by
`JwtAuthenticationFilter`, §1).

**Rationale**: Keeps a single source of truth for "is this request's caller a System Admin" per
request (FR-001/FR-008) and removes a duplicate database query on every
`/api/beneficiarios/**` request. The filter's *other* branch — rejecting a caller with no
membership in the requested tenant at all — is a data-isolation concern (Principle I), not an
admin-standing check, and is unchanged and out of scope per the spec's Assumptions.

**Alternatives considered**:
- *Leave the filter's own lookup as-is.* Rejected — while not incorrect, a second,
  independently-implemented System-Admin check sitting next to the new centralized one
  contradicts FR-001/FR-008's "single determination" framing, even though both would agree.

## §6. Dropping the now-unused `callerId` parameter

**Decision**: Remove the `callerId` parameter from all 12 migrated service methods
(`TenantService.list/create/get/update/delete`,
`MembershipService.listMembers/addMember/removeMember/grantTenantAdmin/revokeTenantAdmin`,
`AppUserService.grantSystemAdmin/revokeSystemAdmin`). In every one of these methods, `callerId`
was used only by the manual check being removed — never for any other logic — so keeping it would
leave a parameter nothing reads. `@PreAuthorize` resolves the caller from Spring Security's
`SecurityContext` directly; controllers stop passing `principal.userId()` into these calls.

**Rationale**: Constitution Principle V explicitly rules out designing for hypothetical future
needs; an unused parameter is exactly that. It can be reintroduced trivially (e.g., for future
audit logging) if a later feature actually needs it.

**Alternatives considered**:
- *Keep `callerId` "just in case" a future feature needs it.* Rejected as speculative per
  Principle V.

## §7. Last-System-Admin invariant — explicitly unaffected

**Decision**: `AppUserService.revokeSystemAdmin`'s pessimistic-lock check
(`findAllSystemAdminsForUpdate`, guaranteeing at least one System Admin always remains) is left
completely untouched by this migration.

**Rationale**: This is a data-integrity business rule about the *resulting state* of a write
("would this leave zero System Admins?"), not an admin-*standing* check about the *caller*
("is this caller allowed to do this?"). FR-006 requires it keep working, and it needs no change
to keep working — it runs after the new `@PreAuthorize("hasRole('SYSTEM_ADMIN')")` check has
already confirmed the caller may attempt the revoke at all.
