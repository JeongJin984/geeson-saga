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
import saga.order.domain.repository.SagaInstanceJpaRepository;
import saga.order.domain.repository.SagaStepJpaRepository;
import saga.order.enums.OrderSagaEvent;
import saga.order.enums.OrderSagaState;
import support.event.event.InvInvCompSuccessEvent;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@Transactional
public class InvPayCompListener {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StateMachineFactory<OrderSagaState, OrderSagaEvent> stateMachineFactory;
    private final StateMachinePersister<OrderSagaState, OrderSagaEvent, String> stateMachinePersister;

    private final SagaStepJpaRepository sagaStepJpaRepository;
    private final SagaInstanceJpaRepository sagaInstanceRepository;

    @KafkaListener(topics = "ord-pay-inv-comp-succ-evt", groupId = "order-saga")
    public void handlePayInvCompSuccessEvent(String message) throws Exception {
        log.info("PayInvComp success message received : {}", message);

        InvInvCompSuccessEvent event = null;
        try {
            event = objectMapper.readValue(message, InvInvCompSuccessEvent.class);
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
                            .withPayload(OrderSagaEvent.INVENTORY_COMPENSATED)
                            .setHeader("sagaId", sagaId)
                            .build()
                    ))
                    .doOnComplete(() -> {
                        try {
                            stateMachinePersister.persist(stateMachine, sagaId);
                            saga.setStatus(OrderSagaState.COMPENSATED);
                            compensateSteps.forEach(step -> step.setStatus(SagaStepEntity.StepStatus.COMPENSATED));
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

    @KafkaListener(topics = "ord-pay-inv-comp-fail-evt", groupId = "order-saga")
    public void handlePayInvCompFailEvent(String message) throws Exception {
        log.info("PayInvComp failure message received : {}", message);

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
                            .withPayload(OrderSagaEvent.PAYMENT_COMPENSATED)
                            .setHeader("sagaId", sagaId)
                            .build()
                    ))
                    .doOnComplete(() -> {
                        try {
                            stateMachinePersister.persist(stateMachine, sagaId);
                            saga.setStatus(OrderSagaState.COMPENSATING_INVENTORY);
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
