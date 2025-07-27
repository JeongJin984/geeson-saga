package saga.order.sagaevent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import saga.order.config.statemachine.CustomStateMachinePersister;
import saga.order.domain.entity.SagaInstanceEntity;
import saga.order.domain.entity.SagaStepEntity;
import saga.order.domain.repository.SagaInstanceJpaRepository;
import saga.order.domain.repository.SagaStepJpaRepository;
import saga.order.enums.OrderSagaEvent;
import saga.order.enums.OrderSagaState;
import support.event.event.PaymentFailedEvent;
import support.event.event.PaymentSucceedEvent;

@Slf4j
@RequiredArgsConstructor
@Component
@Transactional
public class PaymentRequestListener {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StateMachineFactory<OrderSagaState, OrderSagaEvent> stateMachineFactory;
    private final CustomStateMachinePersister stateMachinePersister;
    private final SagaInstanceJpaRepository sagaInstanceJpaRepository;
    private final SagaStepJpaRepository sagaStepJpaRepository;

    @KafkaListener(topics = "ord-pay-req-succ-evt", groupId = "order-saga")
    public void handlePaymentSuccess(String message) throws Exception {
        log.info("payment success message received : {}", message);
        // 1. Kafka 메시지 파싱
        PaymentSucceedEvent event = objectMapper.readValue(message, PaymentSucceedEvent.class);
        String sagaId = event.sagaId();

        StateMachine<OrderSagaState, OrderSagaEvent> stateMachine = stateMachineFactory.getStateMachine(sagaId);
        stateMachinePersister.restore(stateMachine, sagaId);

        SagaInstanceEntity sagaInstance = sagaInstanceJpaRepository.findByIdWithStepsOrdered(sagaId)
            .orElseThrow(() -> new IllegalStateException("Saga not found for instanceId : " + sagaId));

        SagaStepEntity sagaStep = sagaStepJpaRepository.findById(event.stepId())
             .orElseThrow(() -> new IllegalStateException("Saga step not found for stepId : "+ event.stepId()));

        // 3. Saga 상태 전이
        stateMachine.sendEvent(
                Mono.just(MessageBuilder
                    .withPayload(OrderSagaEvent.PAYMENT_SUCCESS)
                    .setHeader("sagaId", sagaId)
                    .build()
                )
            ).doOnComplete(() -> {
                try {
                    stateMachinePersister.persist(stateMachine, sagaId);
                    sagaInstance.setStatus(OrderSagaState.INVENTORY_RESRVING);
                    sagaStep.setStatus(SagaStepEntity.StepStatus.DONE);
                } catch (Exception e) {
                    log.error("Error persisting state machine {}", sagaId, e);
                }
            })
            .subscribe();
    }

    // KafkaListener는 병렬성이 있는 경우 groupId 필수
    @KafkaListener(topics = "ord-pay-req-fail-evt", groupId = "order-saga")
    public void handlePaymentFailure(String message) throws Exception {
        log.info("payment failure message received : {}", message);

        // 1. Kafka 메시지 파싱
        PaymentFailedEvent event = objectMapper.readValue(message, PaymentFailedEvent.class);
        String sagaId = event.sagaId();

        StateMachine<OrderSagaState, OrderSagaEvent> stateMachine = stateMachineFactory.getStateMachine(sagaId);
        stateMachinePersister.restore(stateMachine, sagaId);

        SagaInstanceEntity sagaInstance = sagaInstanceJpaRepository.findByIdWithStepsOrdered(sagaId)
            .orElseThrow(() -> new IllegalStateException("Saga not found for instanceId : " + sagaId));

        SagaStepEntity sagaStep = sagaStepJpaRepository.findById(event.stepId())
                .orElseThrow(() -> new IllegalStateException("Saga step not found for stepId : " + event.stepId()));

        // 3. Saga 상태 전이
        stateMachine.sendEvent(
                Mono.just(MessageBuilder
                    .withPayload(OrderSagaEvent.PAYMENT_FAILURE)
                    .setHeader("sagaId", sagaId)
                    .build()
                )
            ).doOnComplete(() -> {
                try {
                    // 4. 상태 저장
                    stateMachinePersister.persist(stateMachine, sagaId);
                    sagaInstance.setStatus(OrderSagaState.FAILED);
                    sagaStep.setStatus(SagaStepEntity.StepStatus.FAILED);
                }catch (Exception e) {
                    throw new RuntimeException("StateMachine persist failed", e);
                }
            })
            .subscribe();

        // 5. 실패 알림 및 보상 트랜잭션 트리거 등 후처리 가능
//        System.out.println("Payment failed for sagaId: " + sagaId + ". Saga transitioned to FAILED.");
    }
}
