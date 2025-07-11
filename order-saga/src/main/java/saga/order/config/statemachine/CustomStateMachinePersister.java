package saga.order.config.statemachine;

import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineContext;
import org.springframework.statemachine.persist.AbstractStateMachinePersister;
import org.springframework.statemachine.region.Region;
import org.springframework.statemachine.state.AbstractState;
import org.springframework.statemachine.state.HistoryPseudoState;
import org.springframework.statemachine.state.PseudoState;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.support.AbstractStateMachine;
import org.springframework.stereotype.Component;
import saga.order.enums.OrderSagaEvent;
import saga.order.enums.OrderSagaState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Component
public class CustomStateMachinePersister extends AbstractStateMachinePersister<OrderSagaState, OrderSagaEvent, String>  {
    /**
     * Instantiates a new abstract state machine persister.
     *
     * @param persist the state machine persist
     */
    public CustomStateMachinePersister(JpaPersistingStateMachinePersist persist) {
        super(persist);
    }

    @Override
    protected StateMachineContext<OrderSagaState, OrderSagaEvent> buildStateMachineContext(StateMachine<OrderSagaState, OrderSagaEvent> stateMachine) {
        CustomExtendedState extendedState = new CustomExtendedState();
        extendedState.getVariables().putAll(stateMachine.getExtendedState().getVariables());

        ArrayList<StateMachineContext<OrderSagaState, OrderSagaEvent>> childs = new ArrayList<>();
        OrderSagaState id;
        State<OrderSagaState, OrderSagaEvent> state = stateMachine.getState();
        if (state.isSubmachineState()) {
            StateMachine<OrderSagaState, OrderSagaEvent> submachine = ((AbstractState<OrderSagaState, OrderSagaEvent>) state).getSubmachine();
            id = submachine.getState().getId();
            childs.add(buildStateMachineContext(submachine));
        } else if (state.isOrthogonal()) {
            Collection<Region<OrderSagaState, OrderSagaEvent>> regions = ((AbstractState<OrderSagaState, OrderSagaEvent>)state).getRegions();
            for (Region<OrderSagaState, OrderSagaEvent> r : regions) {
                StateMachine<OrderSagaState, OrderSagaEvent> rsm = (StateMachine<OrderSagaState, OrderSagaEvent>) r;
                childs.add(buildStateMachineContext(rsm));
            }
            id = state.getId();
        } else {
            id = state.getId();
        }

        // building history state mappings
        Map<OrderSagaState, OrderSagaState> historyStates = new HashMap<>();
        PseudoState<OrderSagaState, OrderSagaEvent> historyState = ((AbstractStateMachine<OrderSagaState, OrderSagaEvent>) stateMachine).getHistoryState();
        if (historyState != null && ((HistoryPseudoState<OrderSagaState, OrderSagaEvent>)historyState).getState() != null) {
            historyStates.put(null, ((HistoryPseudoState<OrderSagaState, OrderSagaEvent>) historyState).getState().getId());
        }
        Collection<State<OrderSagaState, OrderSagaEvent>> states = stateMachine.getStates();
        for (State<OrderSagaState, OrderSagaEvent> ss : states) {
            if (ss.isSubmachineState()) {
                StateMachine<OrderSagaState, OrderSagaEvent> submachine = ((AbstractState<OrderSagaState, OrderSagaEvent>) ss).getSubmachine();
                PseudoState<OrderSagaState, OrderSagaEvent> ps = ((AbstractStateMachine<OrderSagaState, OrderSagaEvent>) submachine).getHistoryState();
                if (ps != null) {
                    State<OrderSagaState, OrderSagaEvent> pss = ((HistoryPseudoState<OrderSagaState, OrderSagaEvent>)ps).getState();
                    if (pss != null) {
                        historyStates.put(ss.getId(), pss.getId());
                    }
                }
            }
        }
        return new CustomStateMachineContext(childs, id, null, null, extendedState, historyStates, stateMachine.getId());
    }

}
