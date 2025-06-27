package com.geeson.geesonsaga.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geeson.geesonsaga.enums.OrderSagaEvent;
import com.geeson.geesonsaga.enums.OrderSagaState;
import com.geeson.geesonsaga.event.event.PaymentFailedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.persist.StateMachinePersister;
import org.springframework.stereotype.Component;

import java.util.Map;

@RequiredArgsConstructor
@Component
public class PaymentFailureListener {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StateMachine<String, Map<String, String>> stateMachine;

    // KafkaListener는 병렬성이 있는 경우 groupId 필수
    @KafkaListener(topics = "payment.failed.event", groupId = "order-saga")
    public void handlePaymentFailure(String message) throws Exception {
        // 1. Kafka 메시지 파싱
        PaymentFailedEvent event = objectMapper.readValue(message, PaymentFailedEvent.class);
        String sagaId = event.getSagaId();

        // 3. Saga 상태 전이
        boolean transitioned = stateMachine.sendEvent(OrderSagaEvent.PAYMENT_FAILURE);
        if (!transitioned) {
            throw new IllegalStateException("Failed to transition state for sagaId: " + sagaId);
        }

        // 4. 상태 저장
        stateMachinePersister.persist(stateMachine, sagaId);

        // 5. 실패 알림 및 보상 트랜잭션 트리거 등 후처리 가능
        System.out.println("Payment failed for sagaId: " + sagaId + ". Saga transitioned to FAILED.");
    }
}
