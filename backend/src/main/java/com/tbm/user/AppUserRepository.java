package com.tbm.user;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByUsername(String username);

    /**
     * Locking read of every currently-System-Admin user, used by grant/revoke to enforce FR-011's
     * atomicity requirement (research.md §9). Locking only the target row would not prevent the
     * race (two concurrent revokes against two <em>different</em> admins would each lock a
     * different row and both still observe a stale count) — locking the whole matching row set
     * makes two such calls contend for the same rows. PostgreSQL's {@code SELECT ... FOR UPDATE}
     * re-checks each row's WHERE-clause membership after a blocking transaction commits, so the
     * second caller always observes the post-commit admin set, never a stale one.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM AppUser u WHERE u.isSystemAdmin = true")
    List<AppUser> findAllSystemAdminsForUpdate();

    /**
     * Used by self-registration (spec 006-user-self-registration FR-003/FR-011), together with
     * {@link #anyAccountExists()}, to decide atomically under concurrent requests whether the
     * account about to be created is the very first one on the platform. A row-level lock (as
     * {@link #findAllSystemAdminsForUpdate()} uses) cannot protect this check: when the table is
     * empty there is no row to lock. A PostgreSQL transaction-scoped advisory lock instead
     * serializes the "is this the first account?" decision itself, independent of how many rows
     * currently exist (research.md §1). The lock key (727310147) is an arbitrary constant,
     * unique to this one call site — no other advisory lock is taken anywhere else in this
     * codebase. The lock auto-releases when the caller's transaction commits or rolls back; no
     * manual unlock is needed.
     *
     * <p>Deliberately a separate statement/call from {@link #anyAccountExists()}, not one
     * combined query: under PostgreSQL's default READ COMMITTED isolation, a statement's MVCC
     * snapshot is taken when the statement <em>starts</em> executing, even if it then blocks
     * mid-statement waiting on a lock — resuming after the block does not refresh the snapshot.
     * A single combined statement (lock-then-check in one query) was verified empirically to let
     * two concurrent callers both see "no accounts yet": the second caller's blocked statement
     * resumes with the snapshot it started with, from before the first caller committed. Issuing
     * the existence check as its own, later statement gives it a fresh snapshot, taken only after
     * the lock has actually been acquired.
     */
    @Query(value = "SELECT pg_advisory_xact_lock(727310147)", nativeQuery = true)
    void acquireFirstAccountDecisionLock();

    @Query(value = "SELECT EXISTS (SELECT 1 FROM app_user)", nativeQuery = true)
    boolean anyAccountExists();
}
