package saga.order.config;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.persist.StateMachinePersister;
import org.springframework.statemachine.state.State;
import saga.order.config.statemachine.CustomStateMachinePersister;
import saga.order.config.statemachine.JpaPersistingStateMachinePersist;
import saga.order.enums.OrderSagaEvent;
import saga.order.enums.OrderSagaState;
import saga.order.sagacommand.InventoryCommandGateway;
import saga.order.sagacommand.PaymentCommandGateway;

import java.util.EnumSet;

import static saga.order.enums.OrderSagaEvent.*;
import static saga.order.enums.OrderSagaState.*;

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
            .states(EnumSet.allOf(OrderSagaState.class))
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
            .target(INVENTORY_RESRVING)
            .action(inventoryCommandGateway.inventoryReserveCommand())

            .and()
            .withExternal()
            .source(PAYMENT_REQUESTED)
            .event(PAYMENT_FAILURE)
            .target(FAILED)

            .and()
            .withExternal()
            .source(INVENTORY_RESRVING)
            .event(INVENTORY_SUCCESS)
            .target(ORDER_COMPLETED)

            .and()
            .withExternal()
            .source(INVENTORY_RESRVING)
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
        ;
    }

    @Override
    public void configure(StateMachineConfigurationConfigurer<OrderSagaState, OrderSagaEvent> config) throws Exception {
        config
            .withConfiguration()
            .machineId("order-saga-machine")
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
        return new CustomStateMachinePersister(persist);
    }

}
