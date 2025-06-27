package com.geeson.geesonsaga.command;

import com.geeson.geesonsaga.enums.OrderSagaEvent;
import com.geeson.geesonsaga.enums.OrderSagaState;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.statemachine.action.Action;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommandGateway {
    private final KafkaTemplate<String, String> kafkaTemplate;

    public Action<OrderSagaState, OrderSagaEvent> inventoryReserveRequest() {
        return context -> {
            String sagaId = context.getStateMachine().getId();
            kafkaTemplate.send("inventory.reserve.request", sagaId);
        };
    }

    public Action<OrderSagaState, OrderSagaEvent> inventoryFailureCompensateAction() {
        return context -> {
            String sagaId = context.getStateMachine().getId();
            kafkaTemplate.send("inventory.compensate.request", sagaId);
        };
    }

}
