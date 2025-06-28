package com.geeson.geesonsaga.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Command<T> {

    private String commandId;
    private String sagaId;
    private String stepId;

    private String aggregateId;
    private String aggregateType;

    private T payload;  // 도메인에 따라 유연한 타입
}