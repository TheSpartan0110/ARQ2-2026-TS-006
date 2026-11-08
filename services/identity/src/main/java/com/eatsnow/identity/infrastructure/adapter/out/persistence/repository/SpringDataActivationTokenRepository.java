package com.eatsnow.identity.infrastructure.adapter.out.persistence.repository;

import com.eatsnow.identity.infrastructure.adapter.out.persistence.entity.ActivationTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface SpringDataActivationTokenRepository extends JpaRepository<ActivationTokenEntity, String> {

    @Query("SELECT t FROM ActivationTokenEntity t WHERE t.tokenHash = :hash AND t.usedAt IS NULL AND t.expiresAt > :now")
    Optional<ActivationTokenEntity> findUsable(@Param("hash") String tokenHash, @Param("now") Instant now);

    /** Conditional update: only one concurrent activation can consume the token. */
    @Modifying
    @Query("UPDATE ActivationTokenEntity t SET t.usedAt = :now "
            + "WHERE t.tokenHash = :hash AND t.usedAt IS NULL AND t.expiresAt > :now")
    int markUsed(@Param("hash") String tokenHash, @Param("now") Instant now);
}
