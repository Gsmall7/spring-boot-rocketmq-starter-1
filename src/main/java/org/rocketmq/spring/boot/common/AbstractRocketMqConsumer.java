package org.rocketmq.spring.boot.common;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.rocketmq.spring.boot.constants.ConsumeMode;
import org.springframework.util.Assert;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractRocketMqConsumer {
    /**
     * is started.
     *
     */
    private boolean isStarted;

    /**
     * min consume thread.
     *
     */
    private Integer consumeThreadMin;

    /**
     * max consume thread.
     *
     */
    private Integer consumeThreadMax;

    /**
     * consume from where, default is
     * @see ConsumeFromWhere
     * @value ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET
     *
     */
    private ConsumeFromWhere consumeFromWhere = ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET;

    /**
     * Message consume retry strategy<br>
     * -1, no retry,put into DLQ directly<br>
     * 0, broker control retry frequency<br>
     * >0, client control retry frequency
     *
     */
    private int delayLevelWhenNextConsume = 0;

    private long suspendCurrentQueueTimeMillis = -1;

    /**
     * consume message mode, default is CLUSTERING
     * @see MessageModel
     * @value MessageModel.CLUSTERING
     *
     */
    private MessageModel messageModel = MessageModel.CLUSTERING;

    /**
     * consume mode, default is CONCURRENTLY
     * @see ConsumeMode
     * @value ConsumeMode.CONCURRENTLY
     *
     */
    private ConsumeMode consumeMode = ConsumeMode.CONCURRENTLY;

    /**
     * consumer holder.
     *
     */
    private DefaultMQPushConsumer consumer;

    /**
     * subscribeTopicTags for specific consumer.
     *
     * @return subscribeTopicTags
     */
    public abstract Map<String, Set<String>> subscribeTopicTags();

    /**
     * specific consumer group.
     *
     * @return consumer group
     */
    public abstract String getConsumerGroup();

    /**
     * consumer msg.
     *
     * @param content cntent
     * @param msg msg
     * @return consume result
     */
    public abstract void consumeMsg(MessageExt msg);

    @PostConstruct
    @SuppressWarnings("unchecked")
    public void init() throws MQClientException {
        if (this.isStarted()) {
            throw new IllegalStateException("container already started. " + this.toString());
        }
        initRocketMQPushConsumer();
    }

    @PreDestroy
    public void destroy() throws Exception {
        if (Objects.nonNull(consumer)) {
            consumer.shutdown();
        }
        log.info("consumer shutdown, {}", this.toString());
    }

    public class DefaultMessageListenerConcurrently implements MessageListenerConcurrently {

        @Override
        @SuppressWarnings("unchecked")
        public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
            for (MessageExt messageExt : msgs) {
                try {
                    long now = System.currentTimeMillis();
                    consumeMsg(messageExt);
                    long costTime = System.currentTimeMillis() - now;
                    log.error("消费消息 {} 花费: {} 毫秒", new String(messageExt.getBody()), costTime);
                } catch (Exception e) {
                    log.warn("consume message failed. messageExt:{}", messageExt);
                    log.error(e.getMessage(),e);
                    context.setDelayLevelWhenNextConsume(delayLevelWhenNextConsume);
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            }

            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        }
    }

    public class DefaultMessageListenerOrderly implements MessageListenerOrderly {

        @Override
        @SuppressWarnings("unchecked")
        public ConsumeOrderlyStatus consumeMessage(List<MessageExt> msgs, ConsumeOrderlyContext context) {
            for (MessageExt messageExt : msgs) {
                try {
                    long now = System.currentTimeMillis();
                    consumeMsg(messageExt);
                    long costTime = System.currentTimeMillis() - now;
                    log.debug("consume {} cost: {} ms", messageExt.getMsgId(), costTime);
                } catch (Exception e) {
                    log.warn("consume message failed. messageExt:{}", messageExt, e);
                    context.setSuspendCurrentQueueTimeMillis(suspendCurrentQueueTimeMillis);
                    return ConsumeOrderlyStatus.SUSPEND_CURRENT_QUEUE_A_MOMENT;
                }
            }

            return ConsumeOrderlyStatus.SUCCESS;
        }
    }

    private void initRocketMQPushConsumer() throws MQClientException {

        Assert.notNull(getConsumerGroup(), "Property 'consumerGroup' is required");
        Assert.notEmpty(subscribeTopicTags(), "subscribeTopicTags method can't be empty");

        consumer = new DefaultMQPushConsumer(getConsumerGroup());
        if (consumeThreadMax != null) {
            consumer.setConsumeThreadMax(consumeThreadMax);
        }
        if (consumeThreadMax != null && consumeThreadMax < consumer.getConsumeThreadMin()) {
            consumer.setConsumeThreadMin(consumeThreadMax);
        }

        consumer.setConsumeFromWhere(consumeFromWhere);
        consumer.setMessageModel(messageModel);

        switch (consumeMode) {
            case Orderly:
                consumer.setMessageListener(new DefaultMessageListenerOrderly());
                break;
            case CONCURRENTLY:
                consumer.setMessageListener(new DefaultMessageListenerConcurrently());
                break;
            default:
                throw new IllegalArgumentException("Property 'consumeMode' was wrong.");
        }

    }

    public Integer getConsumeThreadMin() {
        return consumeThreadMin;
    }

    public void setConsumeThreadMin(Integer consumeThreadMin) {
        this.consumeThreadMin = consumeThreadMin;
    }

    public Integer getConsumeThreadMax() {
        return consumeThreadMax;
    }

    public void setConsumeThreadMax(Integer consumeThreadMax) {
        this.consumeThreadMax = consumeThreadMax;
    }

    public boolean isStarted() {
        return isStarted;
    }

    public void setStarted(boolean started) {
        isStarted = started;
    }

    public ConsumeFromWhere getConsumeFromWhere() {
        return consumeFromWhere;
    }

    public void setConsumeFromWhere(ConsumeFromWhere consumeFromWhere) {
        this.consumeFromWhere = consumeFromWhere;
    }

    public int getDelayLevelWhenNextConsume() {
        return delayLevelWhenNextConsume;
    }

    public void setDelayLevelWhenNextConsume(int delayLevelWhenNextConsume) {
        this.delayLevelWhenNextConsume = delayLevelWhenNextConsume;
    }

    public long getSuspendCurrentQueueTimeMillis() {
        return suspendCurrentQueueTimeMillis;
    }

    public void setSuspendCurrentQueueTimeMillis(long suspendCurrentQueueTimeMillis) {
        this.suspendCurrentQueueTimeMillis = suspendCurrentQueueTimeMillis;
    }

    public MessageModel getMessageModel() {
        return messageModel;
    }

    public void setMessageModel(MessageModel messageModel) {
        this.messageModel = messageModel;
    }

    public ConsumeMode getConsumeMode() {
        return consumeMode;
    }

    public void setConsumeMode(ConsumeMode consumeMode) {
        this.consumeMode = consumeMode;
    }

    public DefaultMQPushConsumer getConsumer() {
        return consumer;
    }
}
