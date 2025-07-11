package saga.order.enums;

public enum OrderSagaState {
    ORDER_CREATED,
    PAYMENT_REQUESTED,
    INVENTORY_RESRVING,
    INVENTORY_RESERVED,
    ORDER_COMPLETED,

    COMPENSATING_PAYMENT,
    COMPENSATED_PAYMENT,

    COMPENSATING_INVENTORY,
    COMPENSATED_INVENTORY,

    COMPENSATED,

    FAILED
    ;

    public static OrderSagaEvent resolve(OrderSagaState state) {
        return switch (state) {
            case ORDER_CREATED -> OrderSagaEvent.START_ORDER;

            // 실패/성공 여부 알 수 없으므로 START_ORDER 또는 별도 복구 로직 필요
            // 또는 재시도용 PAYMENT_REQUEST_COMMAND
            case PAYMENT_REQUESTED -> OrderSagaEvent.PAYMENT_SUCCESS;

            // 또는 재시도용 INVENTORY_RESERVE_COMMAND
            case INVENTORY_RESRVING -> OrderSagaEvent.INVENTORY_SUCCESS;

            case INVENTORY_RESERVED -> OrderSagaEvent.COMPLETE;

            case COMPENSATING_PAYMENT -> OrderSagaEvent.PAYMENT_COMPENSATED;

            case COMPENSATING_INVENTORY -> OrderSagaEvent.INVENTORY_COMPENSATED;

            // 아래 상태는 더 이상 재시도 불가능
            case FAILED, COMPENSATED, ORDER_COMPLETED -> null;

            default -> throw new IllegalStateException("Unexpected value: " + state);
        };
    }
}