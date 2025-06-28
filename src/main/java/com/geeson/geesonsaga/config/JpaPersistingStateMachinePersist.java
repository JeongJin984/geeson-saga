package com.geeson.geesonsaga.config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geeson.geesonsaga.entity.StateMachineContextEntity;
import com.geeson.geesonsaga.entity.StateMachineContextJpaRepository;
import com.geeson.geesonsaga.enums.OrderSagaEvent;
import com.geeson.geesonsaga.enums.OrderSagaState;
import lombok.RequiredArgsConstructor;
import org.springframework.statemachine.StateMachineContext;
import org.springframework.statemachine.StateMachineContextRepository;
import org.springframework.statemachine.StateMachinePersist;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
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
    @SuppressWarnings("unchecked")
    public StateMachineContext<OrderSagaState, OrderSagaEvent> read(String sagaId) throws Exception {
        return repository.findById(sagaId)
            .map(entity -> {
                try {
                    JavaType javaType = objectMapper.getTypeFactory()
                        .constructParametricType(StateMachineContext.class, OrderSagaState.class, OrderSagaEvent.class);

                    return (StateMachineContext<OrderSagaState, OrderSagaEvent>)
                        objectMapper.readValue(entity.getContextJson(), javaType);

                } catch (IOException e) {
                    throw new IllegalStateException("Failed to deserialize StateMachineContext for sagaId: " + sagaId, e);
                }
            })
            .orElse(null);
    }
}