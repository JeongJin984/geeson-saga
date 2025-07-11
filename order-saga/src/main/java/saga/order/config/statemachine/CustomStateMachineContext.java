package saga.order.config.statemachine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.statemachine.ExtendedState;
import org.springframework.statemachine.StateMachineContext;
import saga.order.enums.OrderSagaEvent;
import saga.order.enums.OrderSagaState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomStateMachineContext implements StateMachineContext<OrderSagaState, OrderSagaEvent> {
    private String id;
    private List<StateMachineContext<OrderSagaState, OrderSagaEvent>> childs = new ArrayList<>();
    private List<String> childRefs = new ArrayList<>();
    private OrderSagaState state;
    private Map<OrderSagaState, OrderSagaState> historyStates;
    private OrderSagaEvent event;
    private Map<String, Object> eventHeaders;
    private CustomExtendedState extendedState;

    /**
     * Instantiates a new default state machine context.
     *
     * @param state the state
     * @param event the event
     * @param eventHeaders the event headers
     * @param extendedState the extended state
     */
    public CustomStateMachineContext(OrderSagaState state, OrderSagaEvent event, Map<String, Object> eventHeaders, CustomExtendedState extendedState) {
        this(new ArrayList<StateMachineContext<OrderSagaState, OrderSagaEvent>>(), state, event, eventHeaders, extendedState);
    }

    /**
     * Instantiates a new default state machine context.
     *
     * @param state the state
     * @param event the event
     * @param eventHeaders the event headers
     * @param extendedState the extended state
     * @param historyStates the history state mappings
     */
    public CustomStateMachineContext(OrderSagaState state, OrderSagaEvent event, Map<String, Object> eventHeaders, CustomExtendedState extendedState,
                                      Map<OrderSagaState, OrderSagaState> historyStates) {
        this(new ArrayList<StateMachineContext<OrderSagaState, OrderSagaEvent>>(), state, event, eventHeaders, extendedState, historyStates);
    }

    /**
     * Instantiates a new default state machine context.
     *
     * @param state the state
     * @param event the event
     * @param eventHeaders the event headers
     * @param extendedState the extended state
     * @param historyStates the history state mappings
     * @param id the machine id
     */
    public CustomStateMachineContext(OrderSagaState state, OrderSagaEvent event, Map<String, Object> eventHeaders, CustomExtendedState extendedState,
                                      Map<OrderSagaState, OrderSagaState> historyStates, String id) {
        this(new ArrayList<StateMachineContext<OrderSagaState, OrderSagaEvent>>(), state, event, eventHeaders, extendedState, historyStates, id);
    }

    /**
     * Instantiates a new default state machine context.
     *
     * @param childs the child state machine contexts
     * @param state the state
     * @param event the event
     * @param eventHeaders the event headers
     * @param extendedState the extended state
     */
    public CustomStateMachineContext(List<StateMachineContext<OrderSagaState, OrderSagaEvent>> childs, OrderSagaState state, OrderSagaEvent event,
                                      Map<String, Object> eventHeaders, CustomExtendedState extendedState) {
        this(childs, state, event, eventHeaders, extendedState, null);
    }

    /**
     * Instantiates a new default state machine context.
     *
     * @param childs the child state machine contexts
     * @param state the state
     * @param event the event
     * @param eventHeaders the event headers
     * @param extendedState the extended state
     * @param historyStates the history state mappings
     */
    public CustomStateMachineContext(List<StateMachineContext<OrderSagaState, OrderSagaEvent>> childs, OrderSagaState state, OrderSagaEvent event,
                                      Map<String, Object> eventHeaders, CustomExtendedState extendedState, Map<OrderSagaState, OrderSagaState> historyStates) {
        this(childs, state, event, eventHeaders, extendedState, historyStates, null);
    }

    /**
     * Instantiates a new default state machine context.
     *
     * @param childs the child state machine contexts
     * @param state the state
     * @param event the event
     * @param eventHeaders the event headers
     * @param extendedState the extended state
     * @param historyStates the history state mappings
     * @param id the machine id
     */
    public CustomStateMachineContext(List<StateMachineContext<OrderSagaState, OrderSagaEvent>> childs, OrderSagaState state, OrderSagaEvent event,
                                      Map<String, Object> eventHeaders, CustomExtendedState extendedState, Map<OrderSagaState, OrderSagaState> historyStates, String id) {
        this.childs = childs;
        this.childRefs = new ArrayList<>();
        this.state = state;
        this.event = event;
        this.eventHeaders = eventHeaders;
        this.extendedState = extendedState;
        this.historyStates = historyStates != null ? historyStates : new HashMap<OrderSagaState, OrderSagaState>();
        this.id = id;
    }

    /**
     * Instantiates a new default state machine context.
     *
     * @param childRefs the child state machine context refs
     * @param childs the child state machine contexts
     * @param state the state
     * @param event the event
     * @param eventHeaders the event headers
     * @param extendedState the extended state
     * @param historyStates the history state mappings
     * @param id the machine id
     */
    public CustomStateMachineContext(List<String> childRefs, List<StateMachineContext<OrderSagaState, OrderSagaEvent>> childs, OrderSagaState state, OrderSagaEvent event,
                                      Map<String, Object> eventHeaders, CustomExtendedState extendedState, Map<OrderSagaState, OrderSagaState> historyStates, String id) {
        this.childs = childs;
        this.childRefs = childRefs;
        this.state = state;
        this.event = event;
        this.eventHeaders = eventHeaders;
        this.extendedState = extendedState;
        this.historyStates = historyStates != null ? historyStates : new HashMap<OrderSagaState, OrderSagaState>();
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public List<StateMachineContext<OrderSagaState, OrderSagaEvent>> getChilds() {
        return childs;
    }

    @Override
    public List<String> getChildReferences() {
        return childRefs;
    }

    @Override
    public OrderSagaState getState() {
        return state;
    }

    @Override
    public Map<OrderSagaState, OrderSagaState> getHistoryStates() {
        return historyStates;
    }

    @Override
    public OrderSagaEvent getEvent() {
        return event;
    }

    @Override
    public Map<String, Object> getEventHeaders() {
        return eventHeaders;
    }

    @Override
    public ExtendedState getExtendedState() {
        return extendedState;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((childRefs == null) ? 0 : childRefs.hashCode());
        result = prime * result + ((childs == null) ? 0 : childs.hashCode());
        result = prime * result + ((event == null) ? 0 : event.hashCode());
        result = prime * result + ((eventHeaders == null) ? 0 : eventHeaders.hashCode());
        result = prime * result + ((extendedState == null) ? 0 : extendedState.hashCode());
        result = prime * result + ((historyStates == null) ? 0 : historyStates.hashCode());
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + ((state == null) ? 0 : state.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        CustomStateMachineContext other = (CustomStateMachineContext) obj;
        if (childRefs == null) {
            if (other.childRefs != null) {
                return false;
            }
        } else if (!childRefs.equals(other.childRefs)) {
            return false;
        }
        if (childs == null) {
            if (other.childs != null) {
                return false;
            }
        } else if (!childs.equals(other.childs)) {
            return false;
        }
        if (event == null) {
            if (other.event != null) {
                return false;
            }
        } else if (!event.equals(other.event)) {
            return false;
        }
        if (eventHeaders == null) {
            if (other.eventHeaders != null) {
                return false;
            }
        } else if (!eventHeaders.equals(other.eventHeaders)) {
            return false;
        }
        if (extendedState == null) {
            if (other.extendedState != null) {
                return false;
            }
        } else if (!extendedState.equals(other.extendedState)) {
            return false;
        }
        if (historyStates == null) {
            if (other.historyStates != null) {
                return false;
            }
        } else if (!historyStates.equals(other.historyStates)) {
            return false;
        }
        if (id == null) {
            if (other.id != null) {
                return false;
            }
        } else if (!id.equals(other.id)) {
            return false;
        }
        if (state == null) {
            if (other.state != null) {
                return false;
            }
        } else if (!state.equals(other.state)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "DefaultStateMachineContext [id=" + id + ", childs=" + childs + ", childRefs=" + childRefs + ", state="
            + state + ", historyStates=" + historyStates + ", event=" + event + ", eventHeaders=" + eventHeaders
            + ", extendedState=" + extendedState + "]";
    }
}