package com.geeson.geesonsaga.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geeson.geesonsaga.enums.OrderSagaEvent;
import com.geeson.geesonsaga.enums.OrderSagaState;
import com.geeson.geesonsaga.event.event.OrderCreatedEvent;
import com.geeson.geesonsaga.event.event.PaymentFailedEvent;
import com.geeson.geesonsaga.payload.PaymentRequestPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.persist.StateMachinePersister;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class OrderCreatedListener {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StateMachineFactory<OrderSagaState, OrderSagaEvent> stateMachineFactory;
    private final StateMachinePersister<OrderSagaState, OrderSagaEvent, String> stateMachinePersister;

    @KafkaListener(topics = "payment.request.failure.event", groupId = "order-saga")
    public void handlePaymentFailure(String message) throws Exception {
        // 1. Kafka 메시지 파싱
        ObjectMapper objectMapper = new ObjectMapper();
        OrderCreatedEvent event = objectMapper.readValue(message, OrderCreatedEvent.class);
        String sagaId = event.getSagaId();

        StateMachine<OrderSagaState, OrderSagaEvent> stateMachine = stateMachineFactory.getStateMachine(sagaId);

        try {
            stateMachinePersister.restore(stateMachine, sagaId);
        } catch (Exception ignore) {}

        // 3. Saga 상태 전이
        stateMachine
            .startReactively()
            .thenMany(
                stateMachine
                    .sendEvent(
                        Mono.just(MessageBuilder
                            .withPayload(OrderSagaEvent.PAYMENT_FAILURE)
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
