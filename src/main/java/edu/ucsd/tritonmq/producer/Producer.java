package edu.ucsd.tritonmq.producer;

import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import static edu.ucsd.tritonmq.common.GlobalConfig.*;

/**
 * Created by dangyi on 5/28/17.
 */
public class Producer<T> {
    private int numRetry;
    private int timeout;
    private int maxInFlight;
    private String zkAddr;
    private SendThread<T> sendThread;

    /**
     * Create a producer
     *
     * @param configs producer configs including zk address etc
     */
    public Producer(Properties configs) {
        int nr = Integer.valueOf(configs.getProperty("numRetry"));
        int mif = Integer.valueOf(configs.getProperty("maxInFlight"));
        this.timeout = Integer.valueOf(configs.getProperty("timeout"));
        this.zkAddr = configs.getProperty("zkAddr");
        this.numRetry = Integer.min(5, Integer.max(nr, 0));
        this.maxInFlight = Integer.min(10, Integer.max(mif, 0));
        this.sendThread = new SendThread<>(timeout, numRetry, maxInFlight, zkAddr);
        sendThread.start();
    }

    /**
     * Asynchronously publish the message to broker
     * and return a future to user
     *
     * @param record producer generated record
     */
    public CompletableFuture<ProducerMetaRecord> publish(ProducerRecord<T> record) {
        // Find group number
        int groupId = record.topic().hashCode() % NumBrokerGroups;
        record.setGroupId(groupId);

        // Construct future
        CompletableFuture<ProducerMetaRecord> future = new CompletableFuture<>();

        // Append to buffer queue
        sendThread.send(record, future);

        // Return future
        return future;
    }

    /**
     * Close the producer connection
     */
    public void close() {
        try {
            sendThread.interrupt();
            sendThread.close();
            sendThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
