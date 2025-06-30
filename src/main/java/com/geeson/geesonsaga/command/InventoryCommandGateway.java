package com.geeson.geesonsaga.command;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geeson.geesonsaga.command.payload.CommandPayload;
import com.geeson.geesonsaga.command.payload.InvInvCompPayload;
import com.geeson.geesonsaga.command.payload.InventoryReservePayload;
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
public class InventoryCommandGateway implements CommandGateway {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final SagaInstanceJpaRepository sagaInstanceJpaRepository;
    private final SagaStepJpaRepository sagaStepJpaRepository;
    private final UuidGenerator uuidGenerator;
    private final ObjectMapper mapper = new ObjectMapper();
    private final OutboxEventJpaRepository outboxEventJpaRepository;

    public Action<OrderSagaState, OrderSagaEvent> inventoryReserveCommand() {
        return context -> {
            String sagaId = context.getStateMachine().getId();

            String inventoryId = String.valueOf(uuidGenerator.nextId());

            SagaInstanceEntity sagaInstance = sagaInstanceJpaRepository.findById(sagaId)
                .orElseThrow(() -> new IllegalStateException("No saga instance found for sagaId: " + sagaId));

            InventoryReservePayload payload = (InventoryReservePayload) context.getMessageHeader("payload");
            if (payload == null) {
                throw new IllegalStateException("Missing 'inventory-reserve-payload' in message header");
            }

            int executionOrder = sagaInstance.getSagaSteps().size();

            SagaStepEntity sagaStep = saveSagaStep(sagaInstance, "inventoryReserve", inventoryId, "inventory", payload, executionOrder);

            kafkaTemplate.send("ord-pay-req-cmd", sagaStep.getCommand())
                .whenComplete((result, ex) -> {
                    OutboxEventEntity.EventStatus status = (ex == null)
                        ? OutboxEventEntity.EventStatus.PUBLISHED
                        : OutboxEventEntity.EventStatus.FAILED;

                    saveOutboxEvent("PaymentRequest", sagaStep.getCommand(), status);
                });

            kafkaTemplate.send("ord-inv-dec-cmd", sagaId);
        };
    }

    public Action<OrderSagaState, OrderSagaEvent> inventoryFailureInventoryCompensateCommand() {
        return context -> {
            String sagaId = context.getStateMachine().getId();

            SagaInstanceEntity sagaInstance = sagaInstanceJpaRepository.findById(sagaId)
                .orElseThrow(() -> new IllegalStateException("No saga instance found for sagaId: " + sagaId));

            List<String> inventoryId = sagaInstance.getSagaSteps().stream()
                .filter(step -> step.getStepName().equals("inventoryReserve"))
                .map(SagaStepEntity::getAggregateId)
                .toList();

            for(String iid : inventoryId) {
                int executionOrder = sagaInstance.getSagaSteps().size();

                SagaStepEntity sagaStep = saveSagaStep(sagaInstance, "inventoryFailureInventoryCompensate", iid, "inventory", new InvInvCompPayload(iid), executionOrder);

                kafkaTemplate.send("ord-inv-inv-comp-req", sagaStep.getCommand())
                    .whenComplete((result, ex) -> {
                        OutboxEventEntity.EventStatus status = (ex == null)
                            ? OutboxEventEntity.EventStatus.PUBLISHED
                            : OutboxEventEntity.EventStatus.FAILED;

                        saveOutboxEvent("inventoryFailureInventoryCompensate", sagaStep.getCommand(), status);
                    });
            }
        };
    }

    public Action<OrderSagaState, OrderSagaEvent> inventoryInventoryCompensateFailDLQ() {
        return context -> {};
    }

    private String serializePayload(Object payload) {
        try {
            return mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize payload to JSON string: " + payload, e);
        }
    }

    private SagaStepEntity saveSagaStep(SagaInstanceEntity sagaInstance, String stepName, String aggregateId, String aggregateType, CommandPayload command, int executionOrder) {
        String stepId = String.valueOf(uuidGenerator.nextId());

        command.setSagaId(sagaInstance.getId());
        command.setStepId(stepId);

        String stringPayload = serializePayload(command);

        return sagaStepJpaRepository.save(
            SagaStepEntity.builder()
                .id(stepId)
                .sagaInstance(sagaInstance)
                .stepName(stepName)
                .aggregateId(aggregateId)
                .aggregateType(aggregateType)
                .stepType(SagaStepEntity.StepType.FORWARD)
                .status(SagaStepEntity.StepStatus.IN_PROGRESS)
                .executionOrder(executionOrder)
                .command(stringPayload)
                .build()
        );
    }

    private OutboxEventEntity saveOutboxEvent(String eventType, String payload, OutboxEventEntity.EventStatus status) {
        String eventId = String.valueOf(uuidGenerator.nextId());
        return outboxEventJpaRepository.save(
            OutboxEventEntity.builder()
                .id(eventId)
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
