package saga.order.config.statemachine;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.StateMachineContext;
import org.springframework.statemachine.StateMachinePersist;
import org.springframework.stereotype.Component;
import saga.order.domain.entity.StateMachineContextEntity;
import saga.order.domain.repository.StateMachineContextJpaRepository;
import saga.order.enums.OrderSagaEvent;
import saga.order.enums.OrderSagaState;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JpaPersistingStateMachinePersist implements StateMachinePersist<OrderSagaState, OrderSagaEvent, String> {
    private final StateMachineContextJpaRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void write(StateMachineContext<OrderSagaState, OrderSagaEvent> context, String sagaId) throws Exception {
        String json = objectMapper.writeValueAsString(context);

        StateMachineContextEntity entity = new StateMachineContextEntity();
        entity.setId(sagaId);
        entity.setContextJson(json);

        repository.save(entity);
    }

    @Override
    public StateMachineContext<OrderSagaState, OrderSagaEvent> read(String sagaId) throws Exception {
        return repository.findById(sagaId)
            .map(entity -> {
                try {
                    log.info("Read state machine context entity: {}", entity);
                    return objectMapper.readValue(entity.getContextJson(), CustomStateMachineContext.class);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to deserialize StateMachineContext for sagaId: " + sagaId, e);
                }
            })
            .orElse(null);
    }
}