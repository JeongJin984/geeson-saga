package com.geeson.geesonsaga.entity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StateMachineContextJpaRepository extends JpaRepository<StateMachineContextEntity, String> {
}
