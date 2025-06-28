package com.geeson.geesonsaga.support;

import org.springframework.stereotype.Component;

@Component
public interface UuidGenerator {
    long nextId();
}
