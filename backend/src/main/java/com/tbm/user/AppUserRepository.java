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
}
