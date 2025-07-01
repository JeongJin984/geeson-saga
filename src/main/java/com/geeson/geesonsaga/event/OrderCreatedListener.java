package com.geeson.geesonsaga.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geeson.geesonsaga.command.payload.PaymentRequestPayload;
import com.geeson.geesonsaga.entity.SagaInstanceEntity;
import com.geeson.geesonsaga.entity.repository.SagaInstanceJpaRepository;
import com.geeson.geesonsaga.enums.OrderSagaEvent;
import com.geeson.geesonsaga.enums.OrderSagaState;
import com.geeson.geesonsaga.event.event.OrderCreatedEvent;
import com.geeson.geesonsaga.support.UuidGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.persist.StateMachinePersister;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;

@Component
@Slf4j
@RequiredArgsConstructor
public class OrderCreatedListener {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StateMachineFactory<OrderSagaState, OrderSagaEvent> stateMachineFactory;
    private final StateMachinePersister<OrderSagaState, OrderSagaEvent, String> stateMachinePersister;
    private final UuidGenerator uuidGenerator;

    private final SagaInstanceJpaRepository sagaInstanceJpaRepository;

    @KafkaListener(topics = "ord-ord-req-succ-event", groupId = "order-saga")
    public void handleOrderCreated(String message) throws Exception {
        // 1. Kafka 메시지 파싱
        OrderCreatedEvent event = objectMapper.readValue(message, OrderCreatedEvent.class);
        String sagaId = String.valueOf(uuidGenerator.nextId());

        StateMachine<OrderSagaState, OrderSagaEvent> stateMachine = stateMachineFactory.getStateMachine(sagaId);

        sagaInstanceJpaRepository.save(new SagaInstanceEntity(
            sagaId,
            "ORDER",
            OrderSagaState.ORDER_CREATED,
            message,
            LocalDateTime.now(),
            LocalDateTime.now(),
            new ArrayList<>()
        ));

        try {
            stateMachinePersister.restore(stateMachine, sagaId);
        } catch (Exception ignore) {
            log.info("restore state machine failed for sagaId: " + sagaId);
        }

        // 3. Saga 상태 전이
        stateMachine
            .startReactively()
            .thenMany(
                stateMachine
                    .sendEvent(
                        Mono.just(MessageBuilder
                            .withPayload(OrderSagaEvent.START_ORDER)
                            .setHeader("sagaId", sagaId)
                            .setHeader("payload", new PaymentRequestPayload(
                                event.orderId(),
                                event.customerId(),
                                String.valueOf(uuidGenerator.nextId()),
                                event.totalPrice(),
                                event.paymentMethodId()
                            ))
                            .build()
                        )
                    )
            )
            .doOnComplete(() -> {
                try {
                    stateMachinePersister.persist(stateMachine, sagaId);
                }catch (Exception e) {
                    throw new RuntimeException("StateMachine persist failed", e);
                }
            })
            .subscribe();
    }
}
