package saga.order.sagaevent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.persist.StateMachinePersister;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import saga.order.domain.entity.SagaInstanceEntity;
import saga.order.domain.entity.SagaStepEntity;
import saga.order.domain.repository.OutboxEventJpaRepository;
import saga.order.domain.repository.SagaInstanceJpaRepository;
import saga.order.domain.repository.SagaStepJpaRepository;
import saga.order.enums.OrderSagaEvent;
import saga.order.enums.OrderSagaState;
import support.event.event.InventoryReserveFailedEvent;
import support.event.event.PaymentFailedEvent;

import java.util.List;

import static saga.order.enums.OrderSagaState.COMPENSATING_PAYMENT;

@Component
@RequiredArgsConstructor
@Slf4j
@Transactional
public class InventoryReserveListener {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StateMachineFactory<OrderSagaState, OrderSagaEvent> stateMachineFactory;
    private final StateMachinePersister<OrderSagaState, OrderSagaEvent, String> stateMachinePersister;
    private final OutboxEventJpaRepository outboxEventJpaRepository;
    private final SagaInstanceJpaRepository sagaInstanceJpaRepository;
    private final SagaStepJpaRepository sagaStepJpaRepository;

    @KafkaListener(topics = "ord-inv-dec-succ-evt", groupId = "order-saga")
    public void handleInventoryReserveSuccess(String message) throws Exception {
        log.info("inventory reserve success message received : {}", message);

        // 1. Kafka 메시지 파싱
        PaymentFailedEvent event = objectMapper.readValue(message, PaymentFailedEvent.class);
        String sagaId = event.sagaId();

        SagaStepEntity sagaStep = sagaStepJpaRepository.findById(event.stepId())
            .orElseThrow(() -> new IllegalStateException("Saga step not found for stepId : " + event.stepId() + " in sagaId : " + sagaId + " and message : " + message));
        sagaStep.setStatus(SagaStepEntity.StepStatus.DONE);

        List<SagaStepEntity> inventoryReserveStep = sagaStepJpaRepository
            .findBySagaInstanceIdAndStepName(sagaId, "inventoryReserve");

        boolean allSuccess = inventoryReserveStep.stream()
            .allMatch(step -> step.getStatus() == SagaStepEntity.StepStatus.DONE);

        if(allSuccess) {
            SagaInstanceEntity sagaInstance = sagaInstanceJpaRepository.findByIdWithStepsOrdered(sagaId)
                .orElseThrow(() -> new IllegalStateException("Saga not found for sagaId : " + sagaId));

            StateMachine<OrderSagaState, OrderSagaEvent> stateMachine = stateMachineFactory.getStateMachine(sagaId);
            stateMachinePersister.restore(stateMachine, sagaId);

            // 3. Saga 상태 전이
            stateMachine.sendEvent(
                    Mono.just(MessageBuilder
                        .withPayload(OrderSagaEvent.INVENTORY_SUCCESS)
                        .setHeader("sagaId", sagaId)
                        .build()
                    )
                ).doOnComplete(() -> {
                    try {
                        stateMachinePersister.persist(stateMachine, sagaId);
                        sagaInstance.setStatus(OrderSagaState.ORDER_COMPLETED);
                    }catch (Exception e) {
                        throw new RuntimeException("StateMachine persist failed", e);
                    }
                })
                .subscribe();
        }



        System.out.println("Inventory reserved for sagaId: " + sagaId);
    }


    @KafkaListener(topics = "ord-inv-dec-fail-evt", groupId = "order-saga")
    public void handleInventoryReserveFailure(String message) throws Exception {
        log.info("inventory reserve failure message received : {}", message);

        InventoryReserveFailedEvent event = objectMapper.readValue(message, InventoryReserveFailedEvent.class);

        // 1. Kafka 메시지 파싱
        String sagaId = event.sagaId();

        StateMachine<OrderSagaState, OrderSagaEvent> stateMachine = stateMachineFactory.getStateMachine(sagaId);
        stateMachinePersister.restore(stateMachine, sagaId);

        SagaInstanceEntity sagaInstance = sagaInstanceJpaRepository.findByIdWithStepsOrdered(sagaId)
            .orElseThrow(() -> new IllegalStateException("Saga not found for InstanceId : " + sagaId));

        SagaStepEntity sagaStep = sagaStepJpaRepository.findById(event.stepId())
                .orElseThrow(() -> new IllegalStateException("Saga step not found for stepId : " + event.stepId()));

        // 3. Saga 상태 전이
        stateMachine.sendEvent(
                Mono.just(
                    MessageBuilder
                        .withPayload(OrderSagaEvent.INVENTORY_FAILURE)
                        .build()
                )
            ).doOnComplete(() -> {
                try {
                    stateMachinePersister.persist(stateMachine, sagaId);
                    sagaInstance.setStatus(COMPENSATING_PAYMENT);
                    sagaStep.setStatus(SagaStepEntity.StepStatus.FAILED);
                }catch (Exception e) {
                    throw new RuntimeException("StateMachine persist failed", e);
                }
            })
            .subscribe();

        System.out.println("Inventory reservation failed for sagaId: " + sagaId);
    }
}
