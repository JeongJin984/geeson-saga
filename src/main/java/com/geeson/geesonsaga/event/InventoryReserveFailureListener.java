package com.geeson.geesonsaga.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geeson.geesonsaga.enums.OrderSagaEvent;
import com.geeson.geesonsaga.enums.OrderSagaState;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.persist.StateMachinePersister;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class InventoryReserveFailureListener {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StateMachine<String, Map<String, String>> stateMachine;

    @KafkaListener(topics = "inventory.reserve.failure.event", groupId = "order-saga")
    public void handleInventoryReserveFailure(String message) throws Exception {
        Map<String, String> event = objectMapper.readValue(message, new TypeReference<>() {});
        String sagaId = event.get("sagaId");

        Disposable transitioned = 		stateMachine
            .sendEvent(Mono.just(MessageBuilder
                .withPayload(event).build()))
            .subscribe();
        if (transitioned.isDisposed()) {
            throw new IllegalStateException("Failed to transition state for sagaId: " + sagaId);
        }

        System.out.println("Inventory reservation failed for sagaId: " + sagaId);
    }
}
