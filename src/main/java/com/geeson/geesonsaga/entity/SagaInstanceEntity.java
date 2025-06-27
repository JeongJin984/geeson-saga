package com.geeson.geesonsaga.entity;

import com.geeson.geesonsaga.enums.OrderSagaState;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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

    @OneToMany(mappedBy = "sagaInstance")
    private List<SagaStepEntity> sagaSteps = new ArrayList<>();

    public void updateStatus(final OrderSagaState newStatus) {
        this.status = newStatus;
    }
}
