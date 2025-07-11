package saga.order.sagaevent;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import support.event.event.InvInvCompFailEvent;
import support.event.event.InvInvCompSuccessEvent;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@Transactional
public class InvInvCompListener {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StateMachineFactory<OrderSagaState, OrderSagaEvent> stateMachineFactory;
    private final StateMachinePersister<OrderSagaState, OrderSagaEvent, String> stateMachinePersister;

    private final SagaStepJpaRepository sagaStepJpaRepository;
    private final SagaInstanceJpaRepository sagaInstanceRepository;
    private final OutboxEventJpaRepository outboxEventJpaRepository;

    @KafkaListener(topics = "ord-inv-inv-comp-succ-evt", groupId = "order-saga")
    public void handleInvInvCompSuccessEvent(String message) throws Exception {
        log.info("InvInvComp success message received : {}", message);

        InvInvCompSuccessEvent event = null;
        try {
            event = objectMapper.readValue(message, InvInvCompSuccessEvent.class);
            sagaStepJpaRepository.updateStatusByStepId(event.stepId(), SagaStepEntity.StepStatus.COMPENSATED);

            String sagaId = event.sagaId();

            SagaStepEntity sagaStep = sagaStepJpaRepository.findById(event.stepId())
                .orElseThrow(() -> new IllegalStateException("Saga step not found"));
            sagaStep.setStatus(SagaStepEntity.StepStatus.COMPENSATED);

            List<SagaStepEntity> compensateSteps = sagaStepJpaRepository
                .findBySagaInstanceIdAndStepName(sagaId, "inventoryFailurePaymentCompensate");

            boolean allSuccess = compensateSteps.stream()
                .allMatch(step -> step.getStatus() == SagaStepEntity.StepStatus.COMPENSATED);

            if (allSuccess) {
                SagaInstanceEntity saga = sagaInstanceRepository.findByIdWithStepsOrdered(sagaId)
                    .orElseThrow(() -> new IllegalStateException("Saga not found"));

                // Optionally trigger statemachine event to move to COMPENSATED state
                StateMachine<OrderSagaState, OrderSagaEvent> stateMachine = stateMachineFactory.getStateMachine(sagaId);
                stateMachinePersister.restore(stateMachine, sagaId);

                stateMachine.sendEvent(Mono.just(
                        MessageBuilder
                            .withPayload(OrderSagaEvent.INVENTORY_COMPENSATED)
                            .setHeader("sagaId", sagaId)
                            .build()
                    ))
                    .doOnComplete(() -> {
                        try {
                            stateMachinePersister.persist(stateMachine, sagaId);
                            saga.setStatus(OrderSagaState.COMPENSATED);
                        }catch (Exception e) {
                            throw new RuntimeException("StateMachine persist failed", e);
                        }
                    })
                    .subscribe();
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "ord-inv-inv-comp-fail-evt", groupId = "order-saga")
    public void handleInvInvCompFailEvent(String message) throws Exception {
        log.info("InvInvComp failure message received : {}", message);

        InvInvCompFailEvent event = null;
        try {
            event = objectMapper.readValue(message, InvInvCompFailEvent.class);
            sagaStepJpaRepository.updateStatusByStepId(event.stepId(), SagaStepEntity.StepStatus.COMPENSATED);

            String sagaId = event.sagaId();

            List<SagaStepEntity> compensateSteps = sagaStepJpaRepository
                .findBySagaInstanceIdAndStepName(sagaId, "inventoryFailurePaymentCompensate");

            boolean allSuccess = compensateSteps.stream()
                .allMatch(step -> step.getStatus() == SagaStepEntity.StepStatus.COMPENSATED);

            if (allSuccess) {
                SagaInstanceEntity saga = sagaInstanceRepository.findByIdWithStepsOrdered(sagaId)
                    .orElseThrow(() -> new IllegalStateException("Saga not found"));

                // Optionally trigger statemachine event to move to COMPENSATED state
                StateMachine<OrderSagaState, OrderSagaEvent> stateMachine = stateMachineFactory.getStateMachine(sagaId);
                stateMachinePersister.restore(stateMachine, sagaId);

                stateMachine.sendEvent(Mono.just(
                        MessageBuilder
                            .withPayload(OrderSagaEvent.INVENTORY_COMPENSATE_FAIL)
                            .setHeader("sagaId", sagaId)
                            .build()
                    ))
                    .doOnComplete(() -> {
                        try {
                            stateMachinePersister.persist(stateMachine, sagaId);
                            saga.setStatus(OrderSagaState.FAILED);
                            compensateSteps.forEach(step -> step.setStatus(SagaStepEntity.StepStatus.FAILED));
                        }catch (Exception e) {
                            throw new RuntimeException("StateMachine persist failed", e);
                        }
                    })
                    .subscribe();
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

    }
}
