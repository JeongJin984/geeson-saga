package com.geeson.geesonsaga.config;

import com.geeson.geesonsaga.enums.OrderEvent;
import com.geeson.geesonsaga.enums.OrderState;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.statemachine.action.Action;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.state.State;
import org.springframework.stereotype.Service;

import java.util.EnumSet;

@Service
public class OrderStateMachineConfig extends EnumStateMachineConfigurerAdapter<OrderState, OrderEvent> {

    private KafkaTemplate<String, String> kafkaTemplate;

    public OrderStateMachineConfig(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void configure(StateMachineStateConfigurer<OrderState, OrderEvent> states) throws Exception {
        states
            .withStates()
            .initial(OrderState.NEW)
            .states(EnumSet.allOf(OrderState.class));
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<OrderState, OrderEvent> transitions) throws Exception {
        transitions
            .withExternal().source(OrderState.NEW).target(OrderState.PAYMENT_PENDING)
            .event(OrderEvent.CREATE_ORDER).action(kafkaAction("order.created"))
            .and()
            .withExternal().source(OrderState.PAYMENT_PENDING).target(OrderState.PAYMENT_COMPLETED)
            .event(OrderEvent.PAYMENT_SUCCESS).action(kafkaAction("payment.success"))
            .and()
            .withExternal().source(OrderState.PAYMENT_PENDING).target(OrderState.PAYMENT_FAILED)
            .event(OrderEvent.PAYMENT_ERROR).action(kafkaAction("payment.error"))
            .and()
            .withExternal().source(OrderState.PAYMENT_COMPLETED).target(OrderState.INVENTORY_PENDING)
            .event(OrderEvent.RESERVE_INVENTORY).action(kafkaAction("inventory.check"))
            .and()
            .withExternal().source(OrderState.INVENTORY_PENDING).target(OrderState.COMPLETED)
            .event(OrderEvent.INVENTORY_SUCCESS).action(kafkaAction("order.completed"))
            .and()
            .withExternal().source(OrderState.INVENTORY_PENDING).target(OrderState.INVENTORY_FAILED)
            .event(OrderEvent.INVENTORY_ERROR).action(kafkaAction("inventory.failed"));
    }

    @Override
    public void configure(StateMachineConfigurationConfigurer<OrderState, OrderEvent> config) throws Exception {
        config
            .withConfiguration()
            .listener(new StateMachineListenerAdapter<>() {
                @Override
                public void stateChanged(State<OrderState, OrderEvent> from, State<OrderState, OrderEvent> to) {
                    if (to != null) {
                        kafkaTemplate.send("state", to.getId().name());
                    }
                }
            });
    }

    private Action<OrderState, OrderEvent> kafkaAction(String message) {
        return context -> kafkaTemplate.send("events", message);
    }
}
