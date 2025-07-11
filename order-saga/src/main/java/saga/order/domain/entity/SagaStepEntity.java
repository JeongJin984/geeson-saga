package saga.order.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "saga_step", schema = "saga_db")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SagaStepEntity {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saga_id", nullable = false)
    private SagaInstanceEntity sagaInstance;

    private String stepName;

    private String aggregateId;

    private String aggregateType;

    @Enumerated(EnumType.STRING)
    private StepType stepType; // FORWARD, COMPENSATION

    @Enumerated(EnumType.STRING)
    private StepStatus status;

    private int executionOrder;

    @Column(columnDefinition = "json")
    private String command;

    @Column(columnDefinition = "json")
    private String eventResponse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compensates_step_id")
    private SagaStepEntity compensatesStep;

    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    public enum StepType {
        FORWARD,        // 정방향 트랜잭션 (ex: 결제 요청, 재고 차감)
        COMPENSATION    // 보상 트랜잭션 (ex: 결제 취소, 재고 복원)
    }

    public enum StepStatus {
        PENDING,        // 아직 실행되지 않음
        IN_PROGRESS,    // 실행 중
        DONE,           // 정상 완료
        FAILED,         // 실패
        COMPENSATING,
        COMPENSATED     // 보상 완료 (보상 Step은 DONE 대신 COMPENSATED를 사용)
    }
}
