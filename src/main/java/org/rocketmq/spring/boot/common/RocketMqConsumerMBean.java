package org.rocketmq.spring.boot.common;

import java.util.List;

public class RocketMqConsumerMBean{

    private List<AbstractRocketMqConsumer> consumers;

    public List<AbstractRocketMqConsumer> getConsumers() {
        return consumers;
    }

    public void setConsumers(List<AbstractRocketMqConsumer> consumers) {
        this.consumers = consumers;
    }

}
