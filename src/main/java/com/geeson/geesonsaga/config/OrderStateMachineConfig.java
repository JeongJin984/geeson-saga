package com.geeson.geesonsaga.config;

import com.geeson.geesonsaga.command.InventoryCommandGateway;
import com.geeson.geesonsaga.command.PaymentCommandGateway;
import com.geeson.geesonsaga.enums.OrderSagaEvent;
import com.geeson.geesonsaga.enums.OrderSagaState;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.statemachine.config.*;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.persist.DefaultStateMachinePersister;
import org.springframework.statemachine.persist.StateMachinePersister;
import org.springframework.statemachine.state.State;

import static com.geeson.geesonsaga.enums.OrderSagaEvent.*;
import static com.geeson.geesonsaga.enums.OrderSagaState.*;

@Configuration
@EnableStateMachineFactory
@RequiredArgsConstructor
public class OrderStateMachineConfig extends EnumStateMachineConfigurerAdapter<OrderSagaState, OrderSagaEvent> {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final PaymentCommandGateway paymentCommandGatewayGW;
    private final InventoryCommandGateway inventoryCommandGateway;

    @Override
    public void configure(StateMachineStateConfigurer<OrderSagaState, OrderSagaEvent> states) throws Exception {
        states
            .withStates()
            .initial(ORDER_CREATED)
            .end(ORDER_COMPLETED)
            .end(FAILED)
            .end(COMPENSATED)
        ;
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<OrderSagaState, OrderSagaEvent> transitions) throws Exception {
        transitions
            .withExternal()
            .source(ORDER_CREATED)
            .event(START_ORDER)
            .target(PAYMENT_REQUESTED)
            .action(paymentCommandGatewayGW.paymentRequestCommand())

            .and()
            .withExternal()
            .source(PAYMENT_REQUESTED)
            .event(PAYMENT_SUCCESS)
            .target(PAYMENT_COMPLETED)
            .action(inventoryCommandGateway.inventoryReserveCommand())

            .and()
            .withExternal()
            .source(PAYMENT_REQUESTED)
            .event(PAYMENT_FAILURE)
            .target(FAILED)

            .and()
            .withExternal()
            .source(PAYMENT_COMPLETED)
            .event(INVENTORY_SUCCESS)
            .target(INVENTORY_RESERVED)

            .and()
            .withExternal()
            .source(PAYMENT_COMPLETED)
            .event(INVENTORY_FAILURE)
            .target(COMPENSATING_PAYMENT)
            .action(paymentCommandGatewayGW.inventoryFailurePaymentCompensateCommand())

            .and()
            .withExternal()
            .source(COMPENSATING_PAYMENT)
            .event(PAYMENT_COMPENSATED)
            .target(COMPENSATING_INVENTORY)
            .action(inventoryCommandGateway.inventoryFailureInventoryCompensateCommand())

            .and()
            .withExternal()
            .source(COMPENSATING_INVENTORY)
            .event(INVENTORY_COMPENSATED)
            .target(COMPENSATED)

            .and()
            .withExternal()
            .source(COMPENSATING_PAYMENT)
            .event(PAYMENT_COMPENSATE_FAIL)
            .target(FAILED)
            .action(paymentCommandGatewayGW.paymentInventoryCompensateFailDLQ())

            .and()
            .withExternal()
            .source(COMPENSATING_INVENTORY)
            .event(INVENTORY_COMPENSATE_FAIL)
            .target(FAILED)
            .action(inventoryCommandGateway.inventoryInventoryCompensateFailDLQ())

            .and()
            .withExternal()
            .source(INVENTORY_RESERVED)
            .event(COMPLETE)
            .target(ORDER_COMPLETED);
    }

    @Override
    public void configure(StateMachineConfigurationConfigurer<OrderSagaState, OrderSagaEvent> config) throws Exception {
        config
            .withConfiguration()
            .autoStartup(false)
            .listener(new StateMachineListenerAdapter<>() {
                @Override
                public void stateChanged(State<OrderSagaState, OrderSagaEvent> from, State<OrderSagaState, OrderSagaEvent> to) {
                    if (to != null) {
                        kafkaTemplate.send("state", to.getId().name());
                    }
                }
            });
    }



    @Bean
    public StateMachinePersister<OrderSagaState, OrderSagaEvent, String> stateMachinePersister(
        JpaPersistingStateMachinePersist persist) {
        return new DefaultStateMachinePersister<>(persist);
    }

}
