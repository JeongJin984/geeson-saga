package com.geeson.geesonsaga.entity.repository;

import com.geeson.geesonsaga.entity.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, String> {
}
