package com.geeson.geesonsaga.config;

import com.geeson.geesonsaga.command.CommandGateway;
import com.geeson.geesonsaga.enums.OrderSagaEvent;
import com.geeson.geesonsaga.enums.OrderSagaState;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.statemachine.StateMachineContext;
import org.springframework.statemachine.StateMachinePersist;
import org.springframework.statemachine.action.Action;
import org.springframework.statemachine.config.EnableStateMachine;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.config.model.StateMachineModelFactory;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.persist.DefaultStateMachinePersister;
import org.springframework.statemachine.persist.StateMachinePersister;
import org.springframework.statemachine.state.State;
import org.springframework.stereotype.Service;
import static com.geeson.geesonsaga.enums.OrderSagaEvent.*;
import static com.geeson.geesonsaga.enums.OrderSagaState.*;

import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@RequiredArgsConstructor
@EnableStateMachine
public class OrderStateMachineConfig extends EnumStateMachineConfigurerAdapter<OrderSagaState, OrderSagaEvent> {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final CommandGateway commandGateway;

    @Override
    public void configure(StateMachineStateConfigurer<OrderSagaState, OrderSagaEvent> states) throws Exception {
        states
            .withStates()
            .initial(ORDER_CREATED)
            .states(EnumSet.allOf(OrderSagaState.class));
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<OrderSagaState, OrderSagaEvent> transitions) throws Exception {
        transitions
            .withExternal()
            .source(ORDER_CREATED).target(PAYMENT_REQUESTED).event(START_ORDER)

            .and()
            .withExternal()
            .source(PAYMENT_REQUESTED).target(PAYMENT_COMPLETED).event(PAYMENT_SUCCESS)
            .action(commandGateway.inventoryReserveRequest())

            .and()
            .withExternal()
            .source(PAYMENT_REQUESTED).target(FAILED).event(PAYMENT_FAILURE)

            .and()
            .withExternal()
            .source(PAYMENT_COMPLETED).target(INVENTORY_RESERVED).event(INVENTORY_SUCCESS)

            .and()
            .withExternal()
            .source(OrderSagaState.PAYMENT_COMPLETED)
            .target(OrderSagaState.COMPENSATING)
            .event(OrderSagaEvent.INVENTORY_FAILURE)
            .action(commandGateway.inventoryFailureCompensateAction())

            .and()
            .withExternal()
            .source(INVENTORY_RESERVED).target(ORDER_COMPLETED).event(OrderSagaEvent.valueOf("COMPLETE"));
    }

    @Override
    public void configure(StateMachineConfigurationConfigurer<OrderSagaState, OrderSagaEvent> config) throws Exception {
        config
            .withConfiguration()
            .listener(new StateMachineListenerAdapter<>() {
                @Override
                public void stateChanged(State<OrderSagaState, OrderSagaEvent> from, State<OrderSagaState, OrderSagaEvent> to) {
                    if (to != null) {
                        kafkaTemplate.send("state", to.getId().name());
                    }
                }
            });
    }
}
