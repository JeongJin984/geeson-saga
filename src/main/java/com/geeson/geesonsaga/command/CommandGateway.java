package com.geeson.geesonsaga.command;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geeson.geesonsaga.entity.SagaInstanceEntity;
import com.geeson.geesonsaga.entity.SagaStepEntity;
import com.geeson.geesonsaga.entity.repository.SagaInstanceJpaRepository;
import com.geeson.geesonsaga.entity.repository.SagaStepJpaRepository;
import com.geeson.geesonsaga.enums.OrderSagaEvent;
import com.geeson.geesonsaga.enums.OrderSagaState;
import com.geeson.geesonsaga.payload.PaymentRequestPayload;
import com.geeson.geesonsaga.support.UuidGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.statemachine.action.Action;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommandGateway {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final SagaInstanceJpaRepository sagaInstanceJpaRepository;
    private final SagaStepJpaRepository sagaStepJpaRepository;
    private final UuidGenerator uuidGenerator;
    private final ObjectMapper mapper = new ObjectMapper();

    public Action<OrderSagaState, OrderSagaEvent> inventoryReserveRequest() {
        return context -> {
            String sagaId = context.getStateMachine().getId();
            kafkaTemplate.send("inventory.reserve.request", sagaId);
        };
    }

    public Action<OrderSagaState, OrderSagaEvent> inventoryFailurePaymentCompensateAction() {
        return context -> {
            String sagaId = context.getStateMachine().getId();
            kafkaTemplate.send("order-pay-inv-comp-req", sagaId);
        };
    }

    public Action<OrderSagaState, OrderSagaEvent> inventoryFailureInventoryCompensateAction() {
        return context -> {
            String sagaId = context.getStateMachine().getId();
            kafkaTemplate.send("order-inv-inv-comp-req", sagaId);
        };
    }

    public Action<OrderSagaState, OrderSagaEvent> paymentRequestCommand() {
        return context -> {
            String sagaId = context.getStateMachine().getId();
            String paymentId = String.valueOf(uuidGenerator.nextId());

            SagaInstanceEntity sagaInstance = sagaInstanceJpaRepository.findById(sagaId)
                .orElseThrow(() -> new IllegalStateException("No saga instance found for sagaId: " + sagaId));

            PaymentRequestPayload payload = (PaymentRequestPayload) context.getMessageHeader("payment-request-payload");
            if (payload == null) {
                throw new IllegalStateException("Missing 'payment-request-payload' in message header");
            }

            String stringPayload = null;
            try {
                stringPayload = mapper.writeValueAsString(payload);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Failed to serialize payload to JSON string: " + payload, e);
            }

            sagaStepJpaRepository.save(
                SagaStepEntity.builder()
                    .sagaInstance(sagaInstance)
                    .stepName("payment-request-command")
                    .aggregateId(paymentId)
                    .aggregateType("payment")
                    .stepType(SagaStepEntity.StepType.FORWARD)
                    .status(SagaStepEntity.StepStatus.IN_PROGRESS)
                    .executionOrder(sagaInstance.getSagaSteps().getLast().getExecutionOrder() + 1)
                    .command(stringPayload)
                    .build()
            );

            kafkaTemplate.send("ordersaga-payment-request-command", stringPayload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send Kafka command", ex);
                    } else {
                        log.info("Kafka command sent: {}", result.getRecordMetadata());
                    }
                });
        };
    }

    public Action<OrderSagaState, OrderSagaEvent> paymentInventoryCompensateFailDLQ() {
        return context -> {};
    }

    public Action<OrderSagaState, OrderSagaEvent> inventoryCompensateFailDLQ() {
        return context -> {};
    }
}
