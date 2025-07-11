package saga.order.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_event", schema = "saga_db")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEventEntity {

    @Id
    private String id;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType; // ex: Order, Payment

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType; // ex: PaymentRequested, InventoryRollback

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", columnDefinition = "varchar(20)", nullable = false, length = 20)
    private MessageType messageType;

    @Column(columnDefinition = "json", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    public enum EventStatus {
        PENDING,
        PUBLISHED,
        FAILED
    }

    public enum MessageType {
        COMMAND, EVENT, REPLY, ERROR
    }

}
