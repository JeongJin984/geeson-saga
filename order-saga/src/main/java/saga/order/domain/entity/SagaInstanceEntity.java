package saga.order.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import saga.order.enums.OrderSagaState;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "saga_instance", schema = "saga_db")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SagaInstanceEntity {

    @Id
    private String id;

    @Column(name = "saga_type", nullable = false)
    private String sagaType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderSagaState status;

    @Column(columnDefinition = "json")
    private String context;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "sagaInstance", fetch = FetchType.LAZY)
    private List<SagaStepEntity> sagaSteps = new ArrayList<>();

    public void updateStatus(final OrderSagaState newStatus) {
        this.status = newStatus;
    }
}
