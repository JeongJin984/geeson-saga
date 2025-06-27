package com.geeson.geesonsaga.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geeson.geesonsaga.enums.OrderSagaEvent;
import com.geeson.geesonsaga.enums.OrderSagaState;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.persist.StateMachinePersister;
import reactor.core.publisher.Mono;

import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class PaymentSuccessListener {
    private final StateMachine<String, Map<String, String>> stateMachine;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = "payment.success.event", groupId = "order-saga")
    public void handlePaymentSuccess(String message) throws Exception {
        Map<String, String> event = objectMapper.readValue(message, new TypeReference<>() {});
        String sagaId = event.get("sagaId");

        boolean transitioned = stateMachine.sendEvent(Mono.just(MessageBuilder.fromMessage(OrderSagaEvent.PAYMENT_SUCCESS)));
        if (!transitioned) {
            throw new IllegalStateException("Failed to transition state for sagaId: " + sagaId);
        }

        System.out.println("Payment success for sagaId: " + sagaId);
    }
}
