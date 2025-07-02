package com.geeson.geesonsaga.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geeson.geesonsaga.config.CustomStateMachinePersister;
import com.geeson.geesonsaga.enums.OrderSagaEvent;
import com.geeson.geesonsaga.enums.OrderSagaState;
import com.geeson.geesonsaga.event.event.PaymentFailedEvent;
import com.geeson.geesonsaga.event.event.PaymentSucceedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.persist.StateMachinePersister;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentRequestListener {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StateMachineFactory<OrderSagaState, OrderSagaEvent> stateMachineFactory;
    private final CustomStateMachinePersister stateMachinePersister;

    @KafkaListener(topics = "ord-pay-req-succ-evt", groupId = "order-saga")
    public void handlePaymentSuccess(String message) throws Exception {
        // 1. Kafka 메시지 파싱
        PaymentSucceedEvent event = objectMapper.readValue(message, PaymentSucceedEvent.class);
        String sagaId = event.sagaId();

        StateMachine<OrderSagaState, OrderSagaEvent> stateMachine = stateMachineFactory.getStateMachine(sagaId);
        stateMachinePersister.restore(stateMachine, sagaId);

        // 3. Saga 상태 전이
        stateMachine.sendEvent(
                Mono.just(MessageBuilder
                    .withPayload(OrderSagaEvent.PAYMENT_SUCCESS)
                    .setHeader("sagaId", sagaId)
                    .build()
                )
            ).doOnComplete(() -> {
                try {
                    stateMachinePersister.persist(stateMachine, sagaId);
                } catch (Exception e) {
                    log.error("Error persisting state machine {}", sagaId, e);
                }
            })
            .subscribe();
    }

    // KafkaListener는 병렬성이 있는 경우 groupId 필수
    @KafkaListener(topics = "ord-pay-req-fail-evt", groupId = "order-saga")
    public void handlePaymentFailure(String message) throws Exception {
        // 1. Kafka 메시지 파싱
        PaymentFailedEvent event = objectMapper.readValue(message, PaymentFailedEvent.class);
        String sagaId = event.sagaId();

        StateMachine<OrderSagaState, OrderSagaEvent> stateMachine = stateMachineFactory.getStateMachine(sagaId);
        stateMachinePersister.restore(stateMachine, sagaId);

        // 3. Saga 상태 전이
        stateMachine.sendEvent(
                Mono.just(MessageBuilder
                    .withPayload(OrderSagaEvent.PAYMENT_FAILURE)
                    .setHeader("sagaId", sagaId)
                    .build()
                )
            ).doOnComplete(() -> {
                try {
                    // 4. 상태 저장
                    stateMachinePersister.persist(stateMachine, sagaId);
                }catch (Exception e) {
                    throw new RuntimeException("StateMachine persist failed", e);
                }
            })
            .subscribe();

        // 5. 실패 알림 및 보상 트랜잭션 트리거 등 후처리 가능
        System.out.println("Payment failed for sagaId: " + sagaId + ". Saga transitioned to FAILED.");
    }
}
