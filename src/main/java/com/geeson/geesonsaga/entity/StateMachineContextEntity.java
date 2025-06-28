package com.geeson.geesonsaga.entity;

import jakarta.persistence.*;
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
    @Lob
    @Column(nullable = false)
    private String contextJson;
}
