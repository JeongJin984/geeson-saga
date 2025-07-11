package saga.order.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import saga.order.domain.entity.StateMachineContextEntity;

public interface StateMachineContextJpaRepository extends JpaRepository<StateMachineContextEntity, String> {
}
