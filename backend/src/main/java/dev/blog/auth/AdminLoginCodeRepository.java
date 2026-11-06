package dev.blog.auth;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface AdminLoginCodeRepository extends JpaRepository<AdminLoginCode, Long> {

    /** Newest usable code, row-locked so concurrent guesses can't race the attempt counter. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AdminLoginCode> findFirstByConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(Instant now);

    @Modifying
    @Query("update AdminLoginCode c set c.consumedAt = :now where c.consumedAt is null")
    int invalidateActive(Instant now);

    @Modifying
    @Query("delete from AdminLoginCode c where c.createdAt < :cutoff")
    int deleteCreatedBefore(Instant cutoff);
}
