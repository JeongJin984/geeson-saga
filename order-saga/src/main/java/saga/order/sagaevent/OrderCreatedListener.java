package saga.order.sagaevent;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import saga.order.config.statemachine.CustomStateMachinePersister;
import saga.order.domain.entity.SagaInstanceEntity;
import saga.order.domain.repository.SagaInstanceJpaRepository;
import saga.order.enums.OrderSagaEvent;
import saga.order.enums.OrderSagaState;
import support.command.payload.PaymentRequestPayload;
import support.event.event.OrderCreatedEvent;
import support.uuid.UuidGenerator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
@Transactional
public class OrderCreatedListener {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StateMachineFactory<OrderSagaState, OrderSagaEvent> stateMachineFactory;
    private final CustomStateMachinePersister stateMachinePersister;
    private final UuidGenerator uuidGenerator;

    private final SagaInstanceJpaRepository sagaInstanceJpaRepository;

    @KafkaListener(topics = "ord-ord-req-succ-event", groupId = "order-saga")
    public void handleOrderCreated(String message) throws Exception {
        // 1. Kafka 메시지 파싱
        OrderCreatedEvent event = objectMapper.readValue(message, OrderCreatedEvent.class);
        final String sagaId = String.valueOf(uuidGenerator.nextId());

        StateMachine<OrderSagaState, OrderSagaEvent> stateMachine = stateMachineFactory.getStateMachine(sagaId);
        Optional<SagaInstanceEntity> sagaInstance = sagaInstanceJpaRepository.findByIdWithStepsOrdered(sagaId);

        if(sagaInstance.isPresent()) {
            stateMachinePersister.restore(stateMachine, sagaId);

            // TODO : 복구 로직 필요함
        } else {
            sagaInstance = Optional.of(sagaInstanceJpaRepository.save(new SagaInstanceEntity(
                sagaId,
                "ORDER",
                OrderSagaState.ORDER_CREATED,
                message,
                LocalDateTime.now(),
                LocalDateTime.now(),
                new ArrayList<>()
            )));

            // 3. Saga 상태 전이
            stateMachine
                .startReactively()
                .thenMany(
                    stateMachine.sendEvent(
                        Mono.just(MessageBuilder
                            .withPayload(OrderSagaEvent.START_ORDER)
                            .setHeader("sagaId", sagaId)
                            .setHeader("payload", new PaymentRequestPayload(
                                event.orderId(),
                                event.customerId(),
                                String.valueOf(uuidGenerator.nextId()),
                                event.paymentKey(),
                                event.totalPrice(),
                                event.paymentMethodId(),
                                event.currency()
                            ))
                            .build()
                        )
                    )
                ).doOnComplete(() -> {
                    try {
                        stateMachinePersister.persist(stateMachine, sagaId);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .blockLast();
        }


    }
}
