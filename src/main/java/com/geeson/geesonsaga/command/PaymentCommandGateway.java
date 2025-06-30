package com.geeson.geesonsaga.command;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geeson.geesonsaga.command.payload.PaymentRequestPayload;
import com.geeson.geesonsaga.entity.OutboxEventEntity;
import com.geeson.geesonsaga.entity.SagaInstanceEntity;
import com.geeson.geesonsaga.entity.SagaStepEntity;
import com.geeson.geesonsaga.entity.repository.OutboxEventJpaRepository;
import com.geeson.geesonsaga.entity.repository.SagaInstanceJpaRepository;
import com.geeson.geesonsaga.entity.repository.SagaStepJpaRepository;
import com.geeson.geesonsaga.enums.OrderSagaEvent;
import com.geeson.geesonsaga.enums.OrderSagaState;
import com.geeson.geesonsaga.support.UuidGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.statemachine.action.Action;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentCommandGateway implements CommandGateway{
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final SagaInstanceJpaRepository sagaInstanceJpaRepository;
    private final SagaStepJpaRepository sagaStepJpaRepository;
    private final UuidGenerator uuidGenerator;
    private final ObjectMapper mapper = new ObjectMapper();
    private final OutboxEventJpaRepository outboxEventJpaRepository;

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

            String stringPayload = serializePayload(payload);
            int executionOrder = sagaInstance.getSagaSteps().size();

            saveSagaStep(sagaInstance, "paymentRequestCommand", paymentId, "payment", stringPayload, executionOrder);

            kafkaTemplate.send("ord-pay-req-cmd", stringPayload)
                .whenComplete((result, ex) -> {
                    OutboxEventEntity.EventStatus status = (ex == null)
                        ? OutboxEventEntity.EventStatus.PUBLISHED
                        : OutboxEventEntity.EventStatus.FAILED;

                    saveOutboxEvent("PaymentRequest", stringPayload, status);
                });
        };
    }

    public Action<OrderSagaState, OrderSagaEvent> inventoryFailurePaymentCompensateCommand() {
        return context -> {
            String sagaId = context.getStateMachine().getId();

            SagaInstanceEntity sagaInstance = sagaInstanceJpaRepository.findById(sagaId)
                .orElseThrow(() -> new IllegalStateException("No saga instance found for sagaId: " + sagaId));

            List<String> paymentId = sagaInstance.getSagaSteps().stream()
                .filter(step -> step.getStepName().equals("paymentRequest"))
                .map(SagaStepEntity::getAggregateId)
                .toList();

            for(String pid : paymentId) {
                String stringPayload = pid;
                int executionOrder = sagaInstance.getSagaSteps().size();

                saveSagaStep(sagaInstance, "inventoryFailurePaymentCompensate", pid, "payment", stringPayload, executionOrder);

                kafkaTemplate.send("ord-pay-inv-comp-req", stringPayload)
                    .whenComplete((result, ex) -> {
                        OutboxEventEntity.EventStatus status = (ex == null)
                            ? OutboxEventEntity.EventStatus.PUBLISHED
                            : OutboxEventEntity.EventStatus.FAILED;

                        saveOutboxEvent("inventoryFailurePaymentCompensate", stringPayload, status);
                    });
            }
        };
    }

    public Action<OrderSagaState, OrderSagaEvent> paymentInventoryCompensateFailDLQ() {
        return context -> {};
    }


    private String serializePayload(Object payload) {
        try {
            return mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize payload to JSON string: " + payload, e);
        }
    }

    private void saveSagaStep(SagaInstanceEntity sagaInstance, String stepName, String aggregateId, String aggregateType, String command, int executionOrder) {
        sagaStepJpaRepository.save(
            SagaStepEntity.builder()
                .id(String.valueOf(uuidGenerator.nextId()))
                .sagaInstance(sagaInstance)
                .stepName(stepName)
                .aggregateId(aggregateId)
                .aggregateType(aggregateType)
                .stepType(SagaStepEntity.StepType.FORWARD)
                .status(SagaStepEntity.StepStatus.IN_PROGRESS)
                .executionOrder(executionOrder)
                .command(command)
                .build()
        );
    }

    private void saveOutboxEvent(String eventType, String payload, OutboxEventEntity.EventStatus status) {
        outboxEventJpaRepository.save(
            OutboxEventEntity.builder()
                .id(String.valueOf(uuidGenerator.nextId()))
                .aggregateType("payment")
                .eventType(eventType)
                .messageType(OutboxEventEntity.MessageType.COMMAND)
                .payload(payload)
                .status(status)
                .createdAt(LocalDateTime.now())
                .publishedAt(LocalDateTime.now())
                .build()
        );
    }
}
