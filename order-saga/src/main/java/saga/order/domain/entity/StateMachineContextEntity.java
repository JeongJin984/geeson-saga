package saga.order.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "saga_state_machine")
@Getter
@Setter
@NoArgsConstructor
public class StateMachineContextEntity {
    @Id
    private String id;
    @Column(nullable = false, columnDefinition = "json")
    private String contextJson;
}
