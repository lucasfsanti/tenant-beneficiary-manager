# Feature Specification: User Self-Registration (Bootstrap Entrypoint)

**Feature Branch**: `006-user-self-registration`

**Created**: 2026-08-19

**Status**: Draft

**Input**: User description: "I need yuo to create a  \"Create User' page, accessible from users not logged in, From this page, the user may select its roles. It must serve as an antrypoint to the system for when there is no data seeded"

## Clarifications

### Session 2026-08-19

- Q: Should the account-creation page (and specifically its ability to grant elevated access) remain reachable and usable once the platform already has at least one existing user account, or must it become unavailable/restricted at that point? → A: The page stays reachable and usable permanently, for any number of existing accounts. However, only the very first account ever created (on an empty platform) is granted System Admin; every account created after that point is always created with the platform's simplest, no-elevated-access role — elevating such an account afterward requires an existing administrator's explicit action through the platform's existing administrative capabilities.
- Q: When a visitor is choosing a role during account creation on a platform that has no Tenants yet, which role options are actually offered — System Admin only, or also an option that creates a new Tenant and grants Tenant Admin standing for it? → A: There is no role choice at all. The very first account is automatically granted System Admin; every account created afterward is automatically the platform's simplest role (Normal, no Tenant membership). Creating a new Tenant is out of scope for this feature.
- Q: Is "the platform currently has zero user accounts" sufficient justification on its own to grant elevated access through this page, or must the visitor also supply some additional, out-of-band proof? → A: No additional proof is required — "the platform currently has zero existing user accounts" is sufficient on its own to grant the first account System Admin standing.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Bootstrap the very first account (Priority: P1)

As someone standing up a brand-new deployment of the platform with no data seeded at all, I want to create the very first user account from a page I can reach without already being logged in, and have that account automatically granted the platform's highest level of access, so I can start configuring the system (creating tenants, inviting other people) without needing pre-existing credentials or manual database intervention.

**Why this priority**: This is the entire reason this feature exists — today, a freshly-provisioned, empty system has no way for anyone to log in at all, which is a dead end. This story removes that dead end.

**Independent Test**: On a freshly migrated, completely empty database (zero existing accounts), open the account-creation page without being logged in, submit a valid username and password, and confirm the resulting account can log in and perform an action that requires the platform's highest level of access (e.g., creating a Tenant).

**Acceptance Scenarios**:

1. **Given** a platform with zero existing user accounts, **When** a visitor who is not logged in opens the account-creation page and submits a valid username and password, **Then** a new account is created and automatically granted the platform's highest level of access (System Admin), with no role choice presented.
2. **Given** the account just created, **When** that person logs in with the credentials they just submitted, **Then** they are authenticated and can perform an action reserved for the platform's highest level of access.
3. **Given** a username that is already taken, **When** someone attempts to create an account using that same username, **Then** the system rejects the request with a clear, actionable error and no account is created.

---

### User Story 2 - Self-registration for everyone after the first account (Priority: P1)

As a new person needing access to the platform after it's already up and running, I want to create my own account from the same account-creation page, so I don't need someone else to create it for me — starting out with the platform's simplest role until an administrator elevates me if needed.

**Why this priority**: Equal priority to User Story 1. The platform's existing administrative capabilities can only grant or revoke elevated standing on accounts that *already exist* — nothing lets an administrator create a brand-new account on someone else's behalf. Without this story, nobody except the very first bootstrap admin could ever obtain a login at all.

**Independent Test**: On a platform that already has at least one existing account, open the account-creation page without being logged in, submit a valid, unused username and password, and confirm the resulting account can log in but only has the platform's simplest, no-elevated-access role.

**Acceptance Scenarios**:

1. **Given** a platform that already has at least one existing user account, **When** a visitor who is not logged in submits a valid username and password on the account-creation page, **Then** a new account is created with the platform's simplest role (Normal), with no elevated access and no Tenant membership, and with no role choice presented.
2. **Given** such a newly self-registered account, **When** an existing administrator later grants it elevated standing (System Admin or Tenant Admin) through the platform's existing administrative capabilities, **Then** the account's access reflects that change on its next login, exactly as it would for any other account.

---

### User Story 3 - Elevated access can never be self-granted after bootstrap (Priority: P1)

As the person responsible for an already-running platform, I want it to be impossible for anyone to obtain elevated access through the account-creation page once the platform's first account already exists, so a permanently-open, unauthenticated page can never be used to compromise a live deployment — regardless of what a visitor submits.

**Why this priority**: Equal priority to User Stories 1 and 2 — it's what makes keeping the page permanently open (per User Story 2) safe. Without this guarantee, an always-reachable, unauthenticated page that can create accounts would be a standing security risk.

**Independent Test**: On a platform that already has at least one existing account, attempt to create an account while tampering with the submission to claim elevated access (e.g., including a role or admin field in the request), and confirm the resulting account still only ever gets the platform's simplest role.

**Acceptance Scenarios**:

1. **Given** a platform that already has at least one existing user account, **When** a visitor submits an account-creation request that includes anything suggesting elevated access should be granted, **Then** the system ignores it and creates the account with the platform's simplest role regardless.
2. **Given** a platform where the very first account has already been created, **When** any subsequent account-creation request is submitted, **Then** the system never re-evaluates or repeats the "platform has zero accounts" condition in a way that could grant System Admin a second time.

---

### Edge Cases

- What happens if two people submit the account-creation page at nearly the same moment while the platform is still empty? At most one of them may end up with System Admin standing as a result of this race; the other MUST end up with the platform's simplest role, exactly as if they had registered after the first account already existed.
- What happens if someone submits the account-creation form with a blank username or password? The system MUST reject the submission with a clear, actionable error and create no account.
- What happens if a visitor's submitted request includes a role, admin flag, or similar elevated-access hint, on a platform that already has at least one existing account? The system MUST ignore it entirely and create the account with the platform's simplest role regardless of what was submitted (see User Story 3).
- What happens if a person navigates to the account-creation page while already logged in? This page is defined for unauthenticated visitors; an already-authenticated visitor is out of scope for this feature.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide a page reachable by a visitor who is not logged in, where they can create a new user account by supplying at minimum a username and a password.
- **FR-002**: The system MUST reject an account-creation attempt that reuses a username already registered to an existing account, with a clear, actionable error, and MUST create no account in that case.
- **FR-003**: The system MUST automatically grant the platform's highest level of access (System Admin standing) to the very first account created while the platform has zero existing user accounts, with no role choice presented to the visitor.
- **FR-004**: The system MUST keep the account-creation page reachable and usable at all times, regardless of how many user accounts already exist on the platform — it MUST NOT be disabled or hidden once the platform is no longer empty.
- **FR-005**: For every account created after the platform's very first account already exists, the system MUST NOT present any role choice — every such account MUST be created with the platform's simplest, least-privileged role (Normal), with no elevated access and no Tenant membership.
- **FR-006**: The system MUST require nothing beyond "the platform currently has zero existing user accounts" to grant the first account System Admin standing — no additional out-of-band proof (e.g., a setup code) is required.
- **FR-007**: The system MUST protect every password created through this page using the same protection already applied to every other stored password on the platform (never stored in plain text).
- **FR-008**: The system MUST validate that both the username and password fields are non-empty before creating an account, and MUST surface a clear, actionable error otherwise.
- **FR-009**: After an account is successfully created, the system MUST direct the person to the platform's existing login page so they can authenticate with the credentials they just created.
- **FR-010**: Elevating an account created through this page beyond the platform's simplest role (granting System Admin or Tenant Admin standing, or adding it to a Tenant) MUST only be possible afterward, through the platform's existing administrative capabilities — never through this page itself.
- **FR-011**: The system MUST decide which role to grant (System Admin vs. the simplest role) based solely on whether any account already exists at the moment of creation — nothing supplied by the visitor in their submission may influence which role is actually granted.

### Key Entities

- **User account**: A login identity on the platform (username, password, and level of access). This feature creates new instances of this entity; the entity itself already exists in the system.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On a freshly-provisioned, completely empty platform, an operator can go from "no way to log in at all" to "successfully authenticated with the platform's highest level of access" in under 5 minutes, without needing any pre-existing credentials or manual database intervention.
- **SC-002**: 100% of account-creation attempts that reuse an existing username are rejected, and none of them result in a duplicate or partially-created account.
- **SC-003**: Once the platform already has at least one existing account, 100% of accounts created through this page come out with the platform's simplest role — 0% end up with System Admin or Tenant Admin standing, or any Tenant membership, regardless of what the visitor submitted.
- **SC-004**: Every password created through this page is protected to the same standard already applied to every other account's password on the platform, with no measurable reduction in that standard.
- **SC-005**: Anyone other than the very first account holder can obtain a working login on the platform on their own, without requiring an existing administrator to manually create the account for them first.

## Assumptions

- After an account is successfully created, the person is taken to the platform's existing login page to authenticate, rather than being signed in automatically — consistent with the platform's existing pattern of never establishing a session without an explicit login step.
- Password requirements for accounts created through this page match whatever minimum validation the platform already applies to account credentials elsewhere; this feature does not introduce new password-complexity rules.
- "No data seeded" / "platform has zero existing user accounts" refers specifically to the absence of any user account — independent of whether demo-data seeding (as covered by a prior feature) happened to run or not, since what actually matters for this feature is whether an account already exists to log in with.
- Besides this page, the platform currently has no other way to create a brand-new user account — its existing administrative capabilities only grant or revoke elevated standing on accounts that already exist. This is why this page is treated as a permanently-available onboarding entrypoint (Clarifications, Q1) rather than a one-time setup wizard: without it, nobody but the first bootstrap admin could ever get a login.
- This feature is delivered as a page in the platform's existing web front end, backed by the platform's existing backend — no new client application is introduced.
