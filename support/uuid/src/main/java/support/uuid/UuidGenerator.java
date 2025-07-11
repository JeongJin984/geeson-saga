package support.uuid;

import org.springframework.stereotype.Component;

@Component
public interface UuidGenerator {
    long nextId();
}
