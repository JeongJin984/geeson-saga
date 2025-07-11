package infra.uuid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import support.uuid.UuidGenerator;

/**
 * Snowflake ID Generator implementation.
 * Generates unique IDs based on Twitter's Snowflake algorithm.
 * 
 * The ID is composed of:
 * - 41 bits for timestamp (milliseconds since custom epoch)
 * - 10 bits for node ID (machine/worker identifier)
 * - 12 bits for sequence number (per millisecond counter)
 */
@Component("snowflake")
public class SnowflakeIdGenerator implements UuidGenerator {
    // Custom epoch (January 1, 2023 00:00:00 UTC)
    private static final long EPOCH = 1672531200000L;
    
    // Bit allocation
    private static final int NODE_ID_BITS = 10;
    private static final int SEQUENCE_BITS = 12;
    
    // Maximum values
    private static final long MAX_NODE_ID = (1L << NODE_ID_BITS) - 1;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
    
    // Bit shifts
    private static final int TIMESTAMP_SHIFT = NODE_ID_BITS + SEQUENCE_BITS;
    private static final int NODE_ID_SHIFT = SEQUENCE_BITS;
    
    // Instance variables
    private final long nodeId;
    private long lastTimestamp = -1L;
    private long sequence = 0L;
    
    /**
     * Constructor with node ID.
     * 
     * @param nodeId the node ID (0-1023)
     * @throws IllegalArgumentException if nodeId is invalid
     */
    public SnowflakeIdGenerator(@Value("${uuid.node-id}") Long nodeId) {
        if (nodeId < 0 || nodeId > MAX_NODE_ID) {
            throw new IllegalArgumentException(
                    String.format("Node ID must be between 0 and %d", MAX_NODE_ID));
        }
        this.nodeId = nodeId;
    }
    
    /**
     * Generates a new unique ID.
     * 
     * @return a new unique ID
     */
    @Override
    public synchronized long nextId() {
        long currentTimestamp = getCurrentTimestamp();
        
        // Handle clock moving backwards
        if (currentTimestamp < lastTimestamp) {
            throw new IllegalStateException(
                    "Clock moved backwards. Refusing to generate ID for " + 
                    (lastTimestamp - currentTimestamp) + " milliseconds");
        }
        
        // Handle multiple requests within the same millisecond
        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            // Sequence overflow in the same millisecond
            if (sequence == 0) {
                // Wait until next millisecond
                currentTimestamp = waitForNextMillisecond(lastTimestamp);
            }
        } else {
            // Reset sequence for new millisecond
            sequence = 0;
        }
        
        lastTimestamp = currentTimestamp;
        
        // Compose the ID from the components
        return ((currentTimestamp - EPOCH) << TIMESTAMP_SHIFT) |
               (nodeId << NODE_ID_SHIFT) |
               sequence;
    }
    
    /**
     * Get current timestamp in milliseconds.
     */
    private long getCurrentTimestamp() {
        return System.currentTimeMillis();
    }
    
    /**
     * Wait until the next millisecond.
     * 
     * @param lastTimestamp the last timestamp
     * @return the next timestamp
     */
    private long waitForNextMillisecond(long lastTimestamp) {
        long timestamp = getCurrentTimestamp();
        while (timestamp <= lastTimestamp) {
            timestamp = getCurrentTimestamp();
        }
        return timestamp;
    }
}