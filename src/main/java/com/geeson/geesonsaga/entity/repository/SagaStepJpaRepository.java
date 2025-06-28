package com.geeson.geesonsaga.entity.repository;

import com.geeson.geesonsaga.entity.SagaStepEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SagaStepJpaRepository extends JpaRepository<SagaStepEntity, String> {
}
