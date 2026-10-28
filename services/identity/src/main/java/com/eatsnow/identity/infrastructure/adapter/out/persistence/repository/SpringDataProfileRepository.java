package com.eatsnow.identity.infrastructure.adapter.out.persistence.repository;

import com.eatsnow.identity.infrastructure.adapter.out.persistence.entity.ProfileEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpringDataProfileRepository extends JpaRepository<ProfileEntity, UUID> {

    Optional<ProfileEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT p FROM ProfileEntity p WHERE (:role IS NULL OR p.roles LIKE %:role%) AND (:cursorId IS NULL OR p.id > :cursorId) ORDER BY p.id ASC")
    List<ProfileEntity> findWithCursorAndRole(
            @Param("role") String role,
            @Param("cursorId") UUID cursorId,
            Pageable pageable
    );
}
