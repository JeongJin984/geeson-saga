package com.geeson.geesonsaga.entity.repository;

import com.geeson.geesonsaga.entity.SagaInstanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SagaInstanceJpaRepository extends JpaRepository<SagaInstanceEntity, String> {
}
