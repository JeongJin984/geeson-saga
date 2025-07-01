package com.geeson.geesonsaga.command;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class InventoryCommandGateway implements CommandGateway {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final SagaInstanceJpaRepository sagaInstanceJpaRepository;
    private final SagaStepJpaRepository sagaStepJpaRepository;
    private final UuidGenerator uuidGenerator;
    private final ObjectMapper mapper = new ObjectMapper();
    private final OutboxEventJpaRepository outboxEventJpaRepository;

    public Action<OrderSagaState, OrderSagaEvent> inventoryReserveCommand() {
        return context -> {
            final String sagaId = getSagaId(context);
            String inventoryId = String.valueOf(uuidGenerator.nextId());

            SagaInstanceEntity sagaInstance = sagaInstanceJpaRepository.findByIdWithStepsOrdered(sagaId)
                .orElseThrow(() -> new IllegalStateException("No saga instance found for sagaId: " + sagaId));

            InventoryReservePayload payload = (InventoryReservePayload) context.getMessageHeader("payload");
            if (payload == null) {
                throw new IllegalStateException("Missing 'inventory-reserve-payload' in message header");
            }

            String stringPayload =  serializePayload(payload);
            int executionOrder = sagaInstance.getSagaSteps() == null ? 1 : sagaInstance.getSagaSteps().size();

            saveSagaStep(sagaInstance, "inventoryReserve", inventoryId, "inventory", stringPayload, executionOrder);
            OutboxEventEntity outboxEvent = saveOutboxEvent("inventoryReserve", inventoryId, stringPayload, OutboxEventEntity.EventStatus.PENDING);
            kafkaTemplate.send("ord-inv-dec-cmd", stringPayload)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        outboxEventJpaRepository.updateStatusNative(outboxEvent.getId(), OutboxEventEntity.EventStatus.PUBLISHED.name(), LocalDateTime.now());
                    } else {
                        outboxEventJpaRepository.updateStatusNative(outboxEvent.getId(), OutboxEventEntity.EventStatus.FAILED.name(), LocalDateTime.now());
                    }
                });
        };
    }

    public Action<OrderSagaState, OrderSagaEvent> inventoryFailureInventoryCompensateCommand() {
        return context -> {
            final String sagaId = getSagaId(context);

            SagaInstanceEntity sagaInstance = sagaInstanceJpaRepository.findByIdWithStepsOrdered(sagaId)
                .orElseThrow(() -> new IllegalStateException("No saga instance found for sagaId: " + sagaId));

            List<String> inventoryId = sagaInstance.getSagaSteps().stream()
                .filter(step -> step.getStepName().equals("inventoryReserve"))
                .map(SagaStepEntity::getAggregateId)
                .toList();

            for(String iid : inventoryId) {
                String stringPayload = iid;
                int executionOrder = sagaInstance.getSagaSteps() == null ? 1 : sagaInstance.getSagaSteps().size();

                saveSagaStep(sagaInstance, "inventoryFailureInventoryCompensate", iid, "inventory", stringPayload, executionOrder);
                OutboxEventEntity outboxEvent = saveOutboxEvent("InventoryFailureInventoryCompensate", iid, stringPayload, OutboxEventEntity.EventStatus.PENDING);
                kafkaTemplate.send("ord-inv-inv-comp-req", stringPayload)
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

    public Action<OrderSagaState, OrderSagaEvent> inventoryInventoryCompensateFailDLQ() {
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

    private SagaStepEntity saveSagaStep(SagaInstanceEntity sagaInstance, String stepName, String aggregateId, String aggregateType, String command, int executionOrder) {
        return sagaStepJpaRepository.save(
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

    private OutboxEventEntity saveOutboxEvent(String eventType, String aggregateId, String payload, OutboxEventEntity.EventStatus status) {
        return outboxEventJpaRepository.save(
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
