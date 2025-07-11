package saga.order.sagacommand;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;
import org.springframework.stereotype.Service;
import saga.order.domain.entity.OutboxEventEntity;
import saga.order.domain.entity.SagaInstanceEntity;
import saga.order.domain.entity.SagaStepEntity;
import saga.order.domain.repository.OutboxEventJpaRepository;
import saga.order.domain.repository.SagaInstanceJpaRepository;
import saga.order.domain.repository.SagaStepJpaRepository;
import saga.order.enums.OrderSagaEvent;
import saga.order.enums.OrderSagaState;
import support.command.CommandGateway;
import support.command.payload.CommandPayload;
import support.command.payload.InvInvCompPayload;
import support.command.payload.InventoryReservePayload;
import support.event.event.OrderCreatedEvent;
import support.uuid.UuidGenerator;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.util.StringUtils.hasText;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
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

            SagaInstanceEntity sagaInstance = sagaInstanceJpaRepository.findByIdWithStepsOrdered(sagaId)
                .orElseThrow(() -> new IllegalStateException("No saga instance found for sagaId: " + sagaId));

            OrderCreatedEvent request;
            try {
                request = mapper.readValue(sagaInstance.getContext(), OrderCreatedEvent.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }

            int executionOrder = sagaInstance.getSagaSteps() == null ? 1 : sagaInstance.getSagaSteps().size();
            for(OrderCreatedEvent.OrderItem item : request.items()) {
                String reservationId = String.valueOf(uuidGenerator.nextId());

                InventoryReservePayload payload = new InventoryReservePayload(
                    reservationId,
                    item.productId(),
                    request.orderId(),
                    item.quantity()
                );

                SagaStepEntity sagaStep = saveSagaStep(sagaInstance, SagaStepEntity.StepType.FORWARD, SagaStepEntity.StepStatus.IN_PROGRESS, "inventoryReserve", reservationId, "inventory", payload, executionOrder++);
                OutboxEventEntity outboxEvent = saveOutboxEvent("inventoryReserve", reservationId, sagaStep.getCommand(), OutboxEventEntity.EventStatus.PENDING);
                log.info("Sending inventory reserve command to kafka topic: {}", sagaStep.getCommand());
                kafkaTemplate.send("ord-inv-dec-cmd", sagaStep.getCommand())
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.info("Successfully sent inventory reserve command to kafka topic: {}", sagaStep.getCommand());
                            outboxEventJpaRepository.updateStatusNative(outboxEvent.getId(), OutboxEventEntity.EventStatus.PUBLISHED.name(), LocalDateTime.now());
                        } else {
                            log.error("Failed to send inventory reserve command to kafka topic: {}", sagaStep.getCommand());
                            outboxEventJpaRepository.updateStatusNative(outboxEvent.getId(), OutboxEventEntity.EventStatus.FAILED.name(), LocalDateTime.now());
                        }
                    });
            }
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
                InvInvCompPayload payload = new InvInvCompPayload(iid);
                int executionOrder = sagaInstance.getSagaSteps() == null ? 1 : sagaInstance.getSagaSteps().size();

                SagaStepEntity sagaStep = saveSagaStep(sagaInstance, SagaStepEntity.StepType.COMPENSATION, SagaStepEntity.StepStatus.COMPENSATING,"inventoryFailureInventoryCompensate", iid, "inventory", payload, executionOrder);
                OutboxEventEntity outboxEvent = saveOutboxEvent("InventoryFailureInventoryCompensate", iid, sagaStep.getCommand(), OutboxEventEntity.EventStatus.PENDING);
                log.info("Sending inventory failure compensation command to kafka topic: {}", sagaStep.getCommand());
                kafkaTemplate.send("ord-inv-inv-comp-req", sagaStep.getCommand())
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.info("Successfully sent inventory failure compensation command to kafka topic: {}", sagaStep.getCommand());
                            outboxEventJpaRepository.updateStatusNative(outboxEvent.getId(), OutboxEventEntity.EventStatus.PUBLISHED.name(), LocalDateTime.now());
                        } else {
                            log.error("Failed to send inventory failure compensation command to kafka topic: {}", sagaStep.getCommand());
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

    private SagaStepEntity saveSagaStep(SagaInstanceEntity sagaInstance, SagaStepEntity.StepType stepType, SagaStepEntity.StepStatus stepStatus, String stepName, String aggregateId, String aggregateType, CommandPayload command, int executionOrder) {
        command.setSagaId(sagaInstance.getId());
        command.setStepId(String.valueOf(uuidGenerator.nextId()));

        String stringCommand = serializePayload(command);

        return sagaStepJpaRepository.saveAndFlush(
            SagaStepEntity.builder()
                .id(command.getStepId())
                .sagaInstance(sagaInstance)
                .stepName(stepName)
                .aggregateId(aggregateId)
                .aggregateType(aggregateType)
                .stepType(stepType)
                .status(stepStatus)
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
