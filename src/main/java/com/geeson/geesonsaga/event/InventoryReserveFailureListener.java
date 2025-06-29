package com.geeson.geesonsaga.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geeson.geesonsaga.entity.OutboxEventEntity;
import com.geeson.geesonsaga.entity.repository.OutboxEventJpaRepository;
import com.geeson.geesonsaga.enums.OrderSagaEvent;
import com.geeson.geesonsaga.enums.OrderSagaState;
import com.geeson.geesonsaga.event.event.InventoryReserveFailedEvent;
import com.geeson.geesonsaga.event.event.PaymentFailedEvent;
import com.geeson.geesonsaga.support.UuidGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cglib.core.Local;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.persist.StateMachinePersister;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class InventoryReserveFailureListener {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StateMachineFactory<OrderSagaState, OrderSagaEvent> stateMachineFactory;
    private final StateMachinePersister<OrderSagaState, OrderSagaEvent, String> stateMachinePersister;
    private final OutboxEventJpaRepository outboxEventJpaRepository;
    private final UuidGenerator uuidGenerator;

    @KafkaListener(topics = "inventory.reserve.failure.event", groupId = "order-saga")
    public void handleInventoryReserveFailure(String message) throws Exception {

        InventoryReserveFailedEvent event = objectMapper.readValue(message, InventoryReserveFailedEvent.class);

        outboxEventJpaRepository.save(new OutboxEventEntity(
            String.valueOf(uuidGenerator.nextId()),
            "inventory",
            event.getInventoryId(),
            OrderSagaEvent.INVENTORY_FAILURE,
            objectMapper.writeValueAsString(event),
            OutboxEventEntity.EventStatus.FAILED,
            LocalDateTime.now(),
            LocalDateTime.now()
        ));

        // 1. Kafka 메시지 파싱
        String sagaId = event.getSagaId();

        StateMachine<OrderSagaState, OrderSagaEvent> stateMachine = stateMachineFactory.getStateMachine(sagaId);

        // 3. Saga 상태 전이
        stateMachine
            .sendEvent(
                Mono.just(MessageBuilder.withPayload(OrderSagaEvent.PAYMENT_FAILURE).build())
            )
            .subscribe();

        // 4. 상태 저장
        stateMachinePersister.persist(stateMachine, sagaId);

        System.out.println("Inventory reservation failed for sagaId: " + sagaId);
    }
}

