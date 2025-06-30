package com.geeson.geesonsaga.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geeson.geesonsaga.entity.OutboxEventEntity;
import com.geeson.geesonsaga.entity.repository.OutboxEventJpaRepository;
import com.geeson.geesonsaga.enums.OrderSagaEvent;
import com.geeson.geesonsaga.enums.OrderSagaState;
import com.geeson.geesonsaga.event.event.InventoryReserveFailedEvent;
import com.geeson.geesonsaga.event.event.PaymentFailedEvent;
import com.geeson.geesonsaga.support.UuidGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.persist.StateMachinePersister;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class InventoryReserveListener {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StateMachineFactory<OrderSagaState, OrderSagaEvent> stateMachineFactory;
    private final StateMachinePersister<OrderSagaState, OrderSagaEvent, String> stateMachinePersister;
    private final OutboxEventJpaRepository outboxEventJpaRepository;

    @KafkaListener(topics = "ord-inv-dec-succ-evt", groupId = "order-saga")
    public void handleInventoryReserveSuccess(String message) throws Exception {
        // 1. Kafka 메시지 파싱
        PaymentFailedEvent event = objectMapper.readValue(message, PaymentFailedEvent.class);
        String sagaId = event.sagaId();

        StateMachine<OrderSagaState, OrderSagaEvent> stateMachine = stateMachineFactory.getStateMachine(sagaId);

        // 3. Saga 상태 전이
        stateMachine
            .sendEvent(
                Mono.just(MessageBuilder.withPayload(OrderSagaEvent.PAYMENT_FAILURE).build())
            )
            .subscribe();

        // 4. 상태 저장
        stateMachinePersister.persist(stateMachine, sagaId);

        System.out.println("Inventory reserved for sagaId: " + sagaId);
    }


    @KafkaListener(topics = "ord-inv-dec-fail-evt", groupId = "order-saga")
    public void handleInventoryReserveFailure(String message) throws Exception {

        InventoryReserveFailedEvent event = objectMapper.readValue(message, InventoryReserveFailedEvent.class);

        // 1. Kafka 메시지 파싱
        String sagaId = event.sagaId();

        StateMachine<OrderSagaState, OrderSagaEvent> stateMachine = stateMachineFactory.getStateMachine(sagaId);

        // 3. Saga 상태 전이
        stateMachine
            .sendEvent(
                Mono.just(
                    MessageBuilder
                        .withPayload(OrderSagaEvent.PAYMENT_FAILURE)
                        .setHeader("sagaId", sagaId)
                        .setHeader("stepId", event.stepId())
                        .setHeader("orderId", event.orderId())
                        .setHeader("inventoryId", event.inventoryId())
                        .setHeader("reason", event.reason())
                        .build()
                )
            )
            .subscribe();

        // 4. 상태 저장
        stateMachinePersister.persist(stateMachine, sagaId);

        System.out.println("Inventory reservation failed for sagaId: " + sagaId);
    }
}
