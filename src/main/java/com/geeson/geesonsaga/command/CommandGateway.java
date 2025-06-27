package com.geeson.geesonsaga.command;

import com.geeson.geesonsaga.enums.OrderSagaEvent;
import com.geeson.geesonsaga.enums.OrderSagaState;
import org.springframework.statemachine.action.Action;
import org.springframework.stereotype.Service;

@Service
public class CommandGateway {
    public Action<OrderSagaState, OrderSagaEvent> inventoryReserveRequest() {

    }

    public Action<OrderSagaState, OrderSagaEvent> inventoryFailureCompensateAction() {

    }

}
