package saga.order.config.statemachine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.statemachine.ExtendedState;
import org.springframework.statemachine.support.ObservableMap;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@AllArgsConstructor
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomExtendedState implements ExtendedState {

    private final Map<Object, Object> variables;
    private ExtendedStateChangeListener listener;

    /**
     * Instantiates a new default extended state.
     */
    public CustomExtendedState() {
        this.variables = new ObservableMap<Object, Object>(new ConcurrentHashMap<Object, Object>(),
            new LocalMapChangeListener());
    }

    /**
     * Instantiates a new default extended state.
     *
     * @param variables the variables
     */
    public CustomExtendedState(Map<Object, Object> variables) {
        this.variables = variables;
    }

    @Override
    public Map<Object, Object> getVariables() {
        return variables;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T get(Object key, Class<T> type) {
        Object value = this.variables.get(key);
        if (value == null) {
            return null;
        }
        if (!type.isAssignableFrom(value.getClass())) {
            throw new IllegalArgumentException("Incorrect type specified for variable '" +
                key + "'. Expected [" + type + "] but actual type is [" + value.getClass() + "]");
        }
        return (T) value;
    }

    @Override
    public void setExtendedStateChangeListener(ExtendedStateChangeListener listener) {
        this.listener = listener;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((variables == null) ? 0 : variables.hashCode());
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
        CustomExtendedState other = (CustomExtendedState) obj;
        if (variables == null) {
            if (other.variables != null) {
                return false;
            }
        } else if (!variables.equals(other.variables)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "DefaultExtendedState [variables=" + variables + "]";
    }

    private class LocalMapChangeListener implements ObservableMap.MapChangeListener<Object, Object> {

        @Override
        public void added(Object key, Object value) {
            if (listener != null) {
                listener.changed(key, value);
            }
        }

        @Override
        public void changed(Object key, Object value) {
            if (listener != null) {
                listener.changed(key, value);
            }
        }

        @Override
        public void removed(Object key, Object value) {
            if (listener != null) {
                listener.changed(key, value);
            }
        }

    }

}
