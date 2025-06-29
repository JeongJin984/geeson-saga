package com.geeson.geesonsaga.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geeson.geesonsaga.entity.SagaInstanceEntity;
import com.geeson.geesonsaga.entity.SagaStepEntity;
import com.geeson.geesonsaga.entity.repository.SagaInstanceJpaRepository;
import com.geeson.geesonsaga.entity.repository.SagaStepJpaRepository;
import com.geeson.geesonsaga.enums.OrderSagaEvent;
import com.geeson.geesonsaga.enums.OrderSagaState;
import com.geeson.geesonsaga.event.event.InvInvCompFailEvent;
import com.geeson.geesonsaga.event.event.InvInvCompSuccessEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.persist.StateMachinePersister;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
public class InvInvCompListener {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StateMachineFactory<OrderSagaState, OrderSagaEvent> stateMachineFactory;
    private final StateMachinePersister<OrderSagaState, OrderSagaEvent, String> stateMachinePersister;

    private final SagaStepJpaRepository sagaStepJpaRepository;
    private final SagaInstanceJpaRepository sagaInstanceRepository;

    @KafkaListener(topics = "order-inv-inv-comp-ok-event", groupId = "order-saga")
    public void handleInvInvCompSuccessEvent(String message) {
        InvInvCompSuccessEvent event = null;
        try {
            event = objectMapper.readValue(message, InvInvCompSuccessEvent.class);
            sagaStepJpaRepository.updateStatusByStepId(event.getStepId(), SagaStepEntity.StepStatus.COMPENSATED);
            checkAndFinalizeSaga(event.getSagaId());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "order-inv-inv-comp-fail-event", groupId = "order-saga")
    public void handleInvInvCompFailEvent(String message) {
        InvInvCompFailEvent event = null;
        try {
            event = objectMapper.readValue(message, InvInvCompFailEvent.class);
            sagaStepJpaRepository.updateStatusByStepId(event.getStepId(), SagaStepEntity.StepStatus.COMPENSATED);
            checkAndFinalizeSaga(event.getSagaId());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

    }

    private void checkAndFinalizeSaga(String sagaId) {
        List<SagaStepEntity> compensateSteps = sagaStepJpaRepository
            .findBySagaInstanceIdAndStepType(sagaId, SagaStepEntity.StepType.COMPENSATION);

        boolean allSuccess = compensateSteps.stream()
            .allMatch(step -> step.getStatus() == SagaStepEntity.StepStatus.COMPENSATED);

        if (allSuccess) {
            SagaInstanceEntity saga = sagaInstanceRepository.findById(sagaId)
                .orElseThrow(() -> new IllegalStateException("Saga not found"));

            saga.setStatus(OrderSagaState.COMPENSATED);
            sagaInstanceRepository.save(saga);

            // Optionally trigger statemachine event to move to COMPENSATED state
            StateMachine<OrderSagaState, OrderSagaEvent> sm = stateMachineFactory.getStateMachine(sagaId);
            sm
                .sendEvent(Mono.just(MessageBuilder.withPayload(OrderSagaEvent.INVENTORY_COMPENSATED).build()))
                .subscribe();
        }
    }
}
