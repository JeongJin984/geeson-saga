# Order Saga - Simplified State Machine Flow

This diagram focuses on the core state machine transitions, events, and commands in the Order Saga system.

## State Machine Transitions Overview

```mermaid
stateDiagram-v2
    [*] --> ORDER_CREATED
    
    ORDER_CREATED --> PAYMENT_REQUESTED : START_ORDER
    PAYMENT_REQUESTED --> INVENTORY_RESRVING : PAYMENT_SUCCESS
    PAYMENT_REQUESTED --> FAILED : PAYMENT_FAILURE
    
    INVENTORY_RESRVING --> ORDER_COMPLETED : INVENTORY_SUCCESS
    INVENTORY_RESRVING --> COMPENSATING_PAYMENT : INVENTORY_FAILURE
    
    COMPENSATING_PAYMENT --> COMPENSATING_INVENTORY : PAYMENT_COMPENSATED
    COMPENSATING_PAYMENT --> FAILED : PAYMENT_COMPENSATE_FAIL
    
    COMPENSATING_INVENTORY --> COMPENSATED : INVENTORY_COMPENSATED
    COMPENSATING_INVENTORY --> FAILED : INVENTORY_COMPENSATE_FAIL
    
    ORDER_COMPLETED --> [*]
    FAILED --> [*]
    COMPENSATED --> [*]
```

## Simplified Sequence Flow

```mermaid
sequenceDiagram
    participant Event as Event Listener
    participant StateMachine as State Machine
    participant Command as Command Gateway
    participant Kafka as Kafka Topics

    Note over Event,Kafka: Happy Path Flow
    
    Event->>StateMachine: OrderCreatedEvent → START_ORDER
    Note over StateMachine: State: ORDER_CREATED → PAYMENT_REQUESTED
    StateMachine->>Command: Execute paymentRequestCommand()
    Command->>Kafka: Send PaymentRequestPayload
    
    Event->>StateMachine: PaymentSucceedEvent → PAYMENT_SUCCESS
    Note over StateMachine: State: PAYMENT_REQUESTED → INVENTORY_RESRVING
    StateMachine->>Command: Execute inventoryReserveCommand()
    Command->>Kafka: Send InventoryReservePayload
    
    Event->>StateMachine: InventoryReserveSucceedEvent → INVENTORY_SUCCESS
    Note over StateMachine: State: INVENTORY_RESRVING → ORDER_COMPLETED
```

## Failure and Compensation Flow

```mermaid
sequenceDiagram
    participant Event as Event Listener
    participant StateMachine as State Machine
    participant Command as Command Gateway
    participant Kafka as Kafka Topics

    Note over Event,Kafka: Failure and Compensation Flow
    
    Event->>StateMachine: OrderCreatedEvent → START_ORDER
    Note over StateMachine: State: ORDER_CREATED → PAYMENT_REQUESTED
    StateMachine->>Command: Execute paymentRequestCommand()
    Command->>Kafka: Send PaymentRequestPayload
    
    Event->>StateMachine: PaymentSucceedEvent → PAYMENT_SUCCESS
    Note over StateMachine: State: PAYMENT_REQUESTED → INVENTORY_RESRVING
    StateMachine->>Command: Execute inventoryReserveCommand()
    Command->>Kafka: Send InventoryReservePayload
    
    Event->>StateMachine: InventoryReserveFailedEvent → INVENTORY_FAILURE
    Note over StateMachine: State: INVENTORY_RESRVING → COMPENSATING_PAYMENT
    StateMachine->>Command: Execute inventoryFailurePaymentCompensateCommand()
    Command->>Kafka: Send PayInvCompPayload
    
    Event->>StateMachine: PayInvCompSuccessEvent → PAYMENT_COMPENSATED
    Note over StateMachine: State: COMPENSATING_PAYMENT → COMPENSATING_INVENTORY
    StateMachine->>Command: Execute inventoryFailureInventoryCompensateCommand()
    Command->>Kafka: Send InvInvCompPayload
    
    Event->>StateMachine: InvInvCompSuccessEvent → INVENTORY_COMPENSATED
    Note over StateMachine: State: COMPENSATING_INVENTORY → COMPENSATED
```

## Event → State → Command Mapping

| Event | State Transition | Command Executed |
|-------|------------------|------------------|
| `OrderCreatedEvent` | `ORDER_CREATED` → `PAYMENT_REQUESTED` | `paymentRequestCommand()` |
| `PaymentSucceedEvent` | `PAYMENT_REQUESTED` → `INVENTORY_RESRVING` | `inventoryReserveCommand()` |
| `PaymentFailedEvent` | `PAYMENT_REQUESTED` → `FAILED` | None (End State) |
| `InventoryReserveSucceedEvent` | `INVENTORY_RESRVING` → `ORDER_COMPLETED` | None (End State) |
| `InventoryReserveFailedEvent` | `INVENTORY_RESRVING` → `COMPENSATING_PAYMENT` | `inventoryFailurePaymentCompensateCommand()` |
| `PayInvCompSuccessEvent` | `COMPENSATING_PAYMENT` → `COMPENSATING_INVENTORY` | `inventoryFailureInventoryCompensateCommand()` |
| `PayInvCompFailEvent` | `COMPENSATING_PAYMENT` → `FAILED` | `paymentInventoryCompensateFailDLQ()` |
| `InvInvCompSuccessEvent` | `COMPENSATING_INVENTORY` → `COMPENSATED` | None (End State) |
| `InvInvCompFailEvent` | `COMPENSATING_INVENTORY` → `FAILED` | `inventoryInventoryCompensateFailDLQ()` |

## Key Commands and Their Payloads

| Command | Payload | Kafka Topic |
|---------|---------|-------------|
| `paymentRequestCommand()` | `PaymentRequestPayload` | `ord-pay-req-cmd` |
| `inventoryReserveCommand()` | `InventoryReservePayload` | `ord-inv-dec-cmd` |
| `inventoryFailurePaymentCompensateCommand()` | `PayInvCompPayload` | `ord-pay-inv-comp-req` |
| `inventoryFailureInventoryCompensateCommand()` | `InvInvCompPayload` | `ord-inv-inv-comp-req` |

## State Machine Configuration Summary

The state machine is configured with:
- **Initial State**: `ORDER_CREATED`
- **End States**: `ORDER_COMPLETED`, `FAILED`, `COMPENSATED`
- **Transitions**: 9 transitions based on events
- **Actions**: Commands executed during state transitions
- **Persistence**: JPA-based state machine persister 