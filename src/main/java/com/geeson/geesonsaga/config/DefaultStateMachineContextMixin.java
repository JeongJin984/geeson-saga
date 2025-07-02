package com.geeson.geesonsaga.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.statemachine.ExtendedState;
import org.springframework.statemachine.StateMachineContext;

import java.util.List;
import java.util.Map;

public abstract class DefaultStateMachineContextMixin<S, E> {

    @JsonCreator
    public DefaultStateMachineContextMixin(
            @JsonProperty("childs") List<StateMachineContext<S, E>> childs,
            @JsonProperty("childRefs") List<String> childRefs,
            @JsonProperty("state") S state,
            @JsonProperty("historyStates") Map<S, S> historyStates,
            @JsonProperty("event") E event,
            @JsonProperty("eventHeaders") Map<String, Object> eventHeaders,
            @JsonProperty("extendedState") ExtendedState extendedState,
            @JsonProperty("id") String id
    ) {
        // Mixin 클래스이므로 본문은 비워둠
    }
}