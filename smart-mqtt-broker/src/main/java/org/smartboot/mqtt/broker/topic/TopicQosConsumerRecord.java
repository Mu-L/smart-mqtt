/*
 * Copyright (C) [2022] smartboot [zhengjunweimail@163.com]
 *
 *  企业用户未经smartboot组织特别许可，需遵循AGPL-3.0开源协议合理合法使用本项目。
 *
 *  Enterprise users are required to use this project reasonably
 *  and legally in accordance with the AGPL-3.0 open source agreement
 *  without special permission from the smartboot organization.
 */

package org.smartboot.mqtt.broker.topic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartboot.mqtt.broker.MqttSession;
import org.smartboot.mqtt.broker.TopicSubscriber;
import org.smartboot.mqtt.broker.eventbus.messagebus.Message;
import org.smartboot.mqtt.common.enums.MqttQoS;
import org.smartboot.mqtt.common.enums.MqttVersion;
import org.smartboot.mqtt.common.message.MqttPacketIdentifierMessage;
import org.smartboot.mqtt.common.message.variable.MqttPacketIdVariableHeader;
import org.smartboot.mqtt.common.message.variable.properties.PublishProperties;
import org.smartboot.mqtt.common.util.MqttMessageBuilders;
import org.smartboot.mqtt.common.util.ValidateUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Topic订阅者
 *
 * @author 三刀（zhengjunweimail@163.com）
 * @version V1.0 , 2022/3/25
 */
public class TopicQosConsumerRecord extends TopicConsumerRecord {
    private static final Logger LOGGER = LoggerFactory.getLogger(TopicQosConsumerRecord.class);

    protected final AtomicBoolean semaphore = new AtomicBoolean(false);


    public TopicQosConsumerRecord(BrokerTopic topic, MqttSession session, TopicSubscriber topicSubscriber, long nextConsumerOffset) {
        super(topic, session, topicSubscriber, nextConsumerOffset);
        ValidateUtils.isTrue(topicSubscriber.getMqttQoS() != MqttQoS.AT_MOST_ONCE, "invalid qos");
    }

    /**
     * 推送消息到客户端
     */
    public void pushToClient() {
        if (mqttSession.isDisconnect() || !enable) {
            return;
        }
        if (semaphore.compareAndSet(false, true)) {
            push0();
            mqttSession.flush();
        }
    }

    private void push0() {
        Message message = topic.getMessageQueue().get(nextConsumerOffset);
        if (message == null) {
            if (semaphore.compareAndSet(true, false)) {
                topic.addSubscriber(this);
                if (topic.getMessageQueue().get(nextConsumerOffset) != null) {
                    topic.push();
                }
            }
            return;
        }

        MqttMessageBuilders.PublishBuilder publishBuilder = MqttMessageBuilders.publish().payload(message.getPayload()).qos(mqttQoS).topic(message.getTopicBytes());
        if (mqttSession.getMqttVersion() == MqttVersion.MQTT_5) {
            publishBuilder.publishProperties(new PublishProperties());
        }

        nextConsumerOffset = message.getOffset() + 1;

        CompletableFuture<MqttPacketIdentifierMessage<? extends MqttPacketIdVariableHeader>> future = mqttSession.getInflightQueue().offer(publishBuilder, () -> {
            if (semaphore.compareAndSet(true, false)) {
                topic.addSubscriber(this);
            }
            topic.push();
        });
        if (future == null) {
            return;
        }
        future.whenComplete((mqttPacketIdentifierMessage, throwable) -> push0());

        push0();
    }

}
