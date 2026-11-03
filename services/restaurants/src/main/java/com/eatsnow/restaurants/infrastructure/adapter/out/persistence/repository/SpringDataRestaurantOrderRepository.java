package com.eatsnow.restaurants.infrastructure.adapter.out.persistence.repository;

import com.eatsnow.restaurants.infrastructure.adapter.out.persistence.entity.RestaurantOrderEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataRestaurantOrderRepository extends JpaRepository<RestaurantOrderEntity, UUID> {

    Optional<RestaurantOrderEntity> findByOrderIdAndRestaurantId(UUID orderId, UUID restaurantId);

    /** Keyset page ordered by (createdAt, orderId); START (epoch, nil UUID) selects the first page. */
    @Query("""
            select e from RestaurantOrderEntity e
             where e.restaurantId = :restaurantId
               and (e.createdAt > :at or (e.createdAt = :at and e.orderId > :id))
             order by e.createdAt, e.orderId
            """)
    List<RestaurantOrderEntity> findPage(@Param("restaurantId") UUID restaurantId, @Param("at") Instant at,
                                         @Param("id") UUID id, Pageable pageable);

    @Query("""
            select e from RestaurantOrderEntity e
             where e.restaurantId = :restaurantId and e.status = :status
               and (e.createdAt > :at or (e.createdAt = :at and e.orderId > :id))
             order by e.createdAt, e.orderId
            """)
    List<RestaurantOrderEntity> findPageByStatus(@Param("restaurantId") UUID restaurantId,
                                                 @Param("status") String status, @Param("at") Instant at,
                                                 @Param("id") UUID id, Pageable pageable);

    /** WHERE orderId = ? AND version = expectedVersion: the optimistic concurrency of Spec 3.5. */
    @Modifying(clearAutomatically = true)
    @Query("""
            update RestaurantOrderEntity e
               set e.status = :status, e.reason = :reason, e.version = :newVersion, e.updatedAt = :now
             where e.orderId = :orderId and e.version = :expectedVersion
            """)
    int updateIfVersion(@Param("orderId") UUID orderId, @Param("expectedVersion") int expectedVersion,
                        @Param("status") String status, @Param("reason") String reason,
                        @Param("newVersion") int newVersion, @Param("now") Instant now);
}
