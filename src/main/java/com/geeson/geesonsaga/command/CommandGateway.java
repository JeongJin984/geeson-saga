package com.geeson.geesonsaga.command;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geeson.geesonsaga.command.payload.PayInvCompPayload;
import com.geeson.geesonsaga.entity.OutboxEventEntity;
import com.geeson.geesonsaga.entity.SagaInstanceEntity;
import com.geeson.geesonsaga.entity.SagaStepEntity;
import com.geeson.geesonsaga.entity.repository.OutboxEventJpaRepository;
import com.geeson.geesonsaga.entity.repository.SagaInstanceJpaRepository;
import com.geeson.geesonsaga.entity.repository.SagaStepJpaRepository;
import com.geeson.geesonsaga.enums.OrderSagaEvent;
import com.geeson.geesonsaga.enums.OrderSagaState;
import com.geeson.geesonsaga.command.payload.PaymentRequestPayload;
import com.geeson.geesonsaga.support.UuidGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.statemachine.action.Action;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommandGateway {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final SagaInstanceJpaRepository sagaInstanceJpaRepository;
    private final SagaStepJpaRepository sagaStepJpaRepository;
    private final UuidGenerator uuidGenerator;
    private final ObjectMapper mapper = new ObjectMapper();
    private final OutboxEventJpaRepository outboxEventJpaRepository;

    public Action<OrderSagaState, OrderSagaEvent> inventoryReserveRequest() {
        return context -> {
            String sagaId = context.getStateMachine().getId();
            kafkaTemplate.send("ord-inv-dec-cmd", sagaId);
        };
    }

    public Action<OrderSagaState, OrderSagaEvent> inventoryFailurePaymentCompensateAction() {
        return context -> {
            String sagaId = context.getStateMachine().getId();

            SagaInstanceEntity sagaInstance = sagaInstanceJpaRepository.findById(sagaId)
                .orElseThrow(() -> new IllegalStateException("No saga instance found for sagaId: " + sagaId));

            PayInvCompPayload payload = new PayInvCompPayload(
                context.getMessageHeader()
            );
            if (payload == null) {
                throw new IllegalStateException("Missing 'payment-request-payload' in message header");
            }

            String stringPayload;
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

            outboxEventJpaRepository.save(
                OutboxEventEntity.builder()
                    .id(String.valueOf(uuidGenerator.nextId()))
                    .aggregateType("payment")
                    .aggregateId(paymentId)
                    .eventType("inventory-failure-payment-compensate")
                    .messageType(OutboxEventEntity.MessageType.COMMAND)
                    .payload(stringPayload)
                    .status(OutboxEventEntity.EventStatus.PUBLISHED)
                    .createdAt(LocalDateTime.now())
                    .publishedAt(LocalDateTime.now())
                    .build()
            );

            kafkaTemplate.send("ord-pay-inv-comp-req", event stringPayload);
        };
    }

    public Action<OrderSagaState, OrderSagaEvent> inventoryFailureInventoryCompensateAction() {
        return context -> {
            String sagaId = context.getStateMachine().getId();

            kafkaTemplate.send("ord-inv-inv-comp-req", sagaId);
        };
    }

    public Action<OrderSagaState, OrderSagaEvent> paymentRequestCommand() {
        return context -> {
            String sagaId = context.getStateMachine().getId();
            String paymentId = String.valueOf(uuidGenerator.nextId());

            SagaInstanceEntity sagaInstance = sagaInstanceJpaRepository.findById(sagaId)
                .orElseThrow(() -> new IllegalStateException("No saga instance found for sagaId: " + sagaId));

            PaymentRequestPayload payload = (PaymentRequestPayload) context.getMessageHeader("payload");
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

            kafkaTemplate.send("ord-pay-req-cmd", stringPayload)
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
