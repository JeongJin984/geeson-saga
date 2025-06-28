package com.geeson.geesonsaga.entity.repository;

import com.geeson.geesonsaga.entity.StateMachineContextEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StateMachineContextJpaRepository extends JpaRepository<StateMachineContextEntity, String> {
}
