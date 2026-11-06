package dev.blog.auth;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RecoveryCode> findFirstByCodeHashAndUsedAtIsNull(String codeHash);

    long countByUsedAtIsNull();
}
