package saga.order;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import static org.springframework.boot.SpringApplication.run;

@SpringBootApplication
@ComponentScan(basePackages = {"saga.order", "queue.kafka.order", "infra.uuid"})
public class OrderSagaMain {
    public static void main(String[] args) {
        run(OrderSagaMain.class, args);
    }
}
