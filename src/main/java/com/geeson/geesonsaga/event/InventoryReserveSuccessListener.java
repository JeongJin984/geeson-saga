package com.geeson.geesonsaga.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geeson.geesonsaga.enums.OrderSagaEvent;
import com.geeson.geesonsaga.enums.OrderSagaState;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.persist.StateMachinePersister;

import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class InventoryReserveSuccessListener {
    private final StateMachineFactory<OrderSagaState, OrderSagaEvent> stateMachineFactory;
    private final StateMachinePersister<OrderSagaState, OrderSagaEvent, String> stateMachinePersister;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = "inventory.reserve.success.event", groupId = "order-saga")
    public void handleInventoryReserveSuccess(String message) throws Exception {
        Map<String, String> event = objectMapper.readValue(message, new TypeReference<>() {});
        String sagaId = event.get("sagaId");

        StateMachine<OrderSagaState, OrderSagaEvent> stateMachine = stateMachineFactory.getStateMachine(sagaId);
        stateMachinePersister.restore(stateMachine, sagaId);

        boolean transitioned = stateMachine.sendEvent(OrderSagaEvent.INVENTORY_SUCCESS);
        if (!transitioned) {
            throw new IllegalStateException("Failed to transition state for sagaId: " + sagaId);
        }

        stateMachinePersister.persist(stateMachine, sagaId);
        System.out.println("Inventory reserved for sagaId: " + sagaId);
    }
}
