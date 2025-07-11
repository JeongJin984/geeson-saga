package saga.order.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import saga.order.domain.entity.OutboxEventEntity;

import java.time.LocalDateTime;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, String> {
    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW) // ← 중요!
    @Query(value = """
        UPDATE outbox_event
        SET status = :status,
            published_at = :publishedAt
        WHERE id = :id
    """, nativeQuery = true)
    void updateStatusNative(
        @Param("id") String id,
        @Param("status") String status,
        @Param("publishedAt") LocalDateTime publishedAt
    );
}
