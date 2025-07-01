package com.geeson.geesonsaga.entity.repository;

import com.geeson.geesonsaga.entity.SagaInstanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SagaInstanceJpaRepository extends JpaRepository<SagaInstanceEntity, String> {
    @Query("""
    SELECT DISTINCT si FROM SagaInstanceEntity si
    LEFT JOIN FETCH si.sagaSteps ss
    WHERE si.id = :sagaId
    ORDER BY ss.executionOrder
""")
    Optional<SagaInstanceEntity> findByIdWithStepsOrdered(@Param("sagaId") String sagaId);
}
