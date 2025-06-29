package com.geeson.geesonsaga.entity.repository;

import com.geeson.geesonsaga.entity.SagaStepEntity;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SagaStepJpaRepository extends JpaRepository<SagaStepEntity, String> {
    List<SagaStepEntity> findBySagaInstanceIdAndStepType(String sagaId, SagaStepEntity.StepType stepType);

    @Modifying
    @Transactional
    @Query("UPDATE SagaStepEntity s SET s.status = :status WHERE s.id = :stepId")
    int updateStatusByStepId(@Param("stepId") String stepId, @Param("status") SagaStepEntity.StepStatus status);
}
