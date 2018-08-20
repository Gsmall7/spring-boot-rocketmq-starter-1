package org.rocketmq.spring.boot.constants;

public enum ConsumeMode {

    /**
     * receive asynchronously delivered messages concurrently
     */
    CONCURRENTLY,

    /**
     * receive asynchronously delivered messages orderly. one queue, one thread
     */
    Orderly

}
