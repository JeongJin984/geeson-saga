package com.geeson.geesonsaga.command;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geeson.geesonsaga.command.payload.CommandPayload;
import com.geeson.geesonsaga.command.payload.PayInvCompPayload;
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
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.util.StringUtils.hasText;

@Service
@Transactional
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
            final String sagaId = getSagaId(context);
            final String paymentId = String.valueOf(uuidGenerator.nextId());

            SagaInstanceEntity sagaInstance = sagaInstanceJpaRepository.findByIdWithStepsOrdered(sagaId)
                .orElseThrow(() -> new IllegalStateException("No saga instance found for sagaId: " + sagaId));

            PaymentRequestPayload payload = (PaymentRequestPayload) context.getMessageHeader("payload");
            if (payload == null) {
                throw new IllegalStateException("Missing 'payment-request-payload' in message header");
            }

            int executionOrder = sagaInstance.getSagaSteps() == null ? 1 : sagaInstance.getSagaSteps().size();

            SagaStepEntity sagaStep = saveSagaStep(sagaInstance, "paymentRequestCommand", paymentId, "payment", payload, executionOrder);
            OutboxEventEntity outboxEvent = saveOutboxEvent("PaymentRequest", paymentId, sagaStep.getCommand(), OutboxEventEntity.EventStatus.PENDING);
            kafkaTemplate.send("ord-pay-req-cmd", sagaStep.getCommand())
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        outboxEventJpaRepository.updateStatusNative(outboxEvent.getId(), OutboxEventEntity.EventStatus.PUBLISHED.name(), LocalDateTime.now());
                    } else {
                        outboxEventJpaRepository.updateStatusNative(outboxEvent.getId(), OutboxEventEntity.EventStatus.FAILED.name(), LocalDateTime.now());
                    }
                });
        };
    }

    public Action<OrderSagaState, OrderSagaEvent> inventoryFailurePaymentCompensateCommand() {
        return context -> {
            final String sagaId = getSagaId(context);

            SagaInstanceEntity sagaInstance = sagaInstanceJpaRepository.findByIdWithStepsOrdered(sagaId)
                .orElseThrow(() -> new IllegalStateException("No saga instance found for sagaId: " + sagaId));

            List<String> paymentId = sagaInstance.getSagaSteps().stream()
                .filter(step -> step.getStepName().equals("inventoryFailurePaymentCompensate"))
                .map(SagaStepEntity::getAggregateId)
                .toList();

            for(String pid : paymentId) {
                PayInvCompPayload payload = new PayInvCompPayload(pid);
                int executionOrder = sagaInstance.getSagaSteps() == null ? 1 : sagaInstance.getSagaSteps().size();

                SagaStepEntity sagaStep = saveSagaStep(sagaInstance, "inventoryFailurePaymentCompensate", pid, "payment", payload, executionOrder);
                OutboxEventEntity outboxEvent = saveOutboxEvent("inventoryFailurePaymentCompensate", pid, sagaStep.getCommand(), OutboxEventEntity.EventStatus.PENDING);
                kafkaTemplate.send("ord-pay-inv-comp-req", sagaStep.getCommand())
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            outboxEventJpaRepository.updateStatusNative(outboxEvent.getId(), OutboxEventEntity.EventStatus.PUBLISHED.name(), LocalDateTime.now());
                        } else {
                            outboxEventJpaRepository.updateStatusNative(outboxEvent.getId(), OutboxEventEntity.EventStatus.FAILED.name(), LocalDateTime.now());
                        }
                    });
            }
        };
    }

    public Action<OrderSagaState, OrderSagaEvent> paymentInventoryCompensateFailDLQ() {
        return context -> {};
    }

    private String getSagaId(StateContext<OrderSagaState, OrderSagaEvent> context) {
        String sagaId = context.getStateMachine().getId();
        if(!hasText(sagaId)) {
            sagaId = context.getMessageHeader("sagaId").toString();
        }
        return sagaId;
    }

    private String serializePayload(Object payload) {
        try {
            return mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize payload to JSON string: " + payload, e);
        }
    }

    private SagaStepEntity saveSagaStep(SagaInstanceEntity sagaInstance, String stepName, String aggregateId, String aggregateType, CommandPayload command, int executionOrder) {
        command.setSagaId(sagaInstance.getId());
        command.setStepId(String.valueOf(uuidGenerator.nextId()));

        String stringCommand = serializePayload(command);

        return sagaStepJpaRepository.saveAndFlush(
            SagaStepEntity.builder()
                .id(String.valueOf(uuidGenerator.nextId()))
                .sagaInstance(sagaInstance)
                .stepName(stepName)
                .aggregateId(aggregateId)
                .aggregateType(aggregateType)
                .stepType(SagaStepEntity.StepType.FORWARD)
                .status(SagaStepEntity.StepStatus.IN_PROGRESS)
                .executionOrder(executionOrder)
                .command(stringCommand)
                .build()
        );
    }

    private OutboxEventEntity saveOutboxEvent(String eventType, String aggregateId, String payload, OutboxEventEntity.EventStatus status) {
        return outboxEventJpaRepository.saveAndFlush(
            OutboxEventEntity.builder()
                .id(String.valueOf(uuidGenerator.nextId()))
                .aggregateId(aggregateId)
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
