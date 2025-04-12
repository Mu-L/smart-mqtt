/*
 * Copyright (C) [2022] smartboot [zhengjunweimail@163.com]
 *
 *  企业用户未经smartboot组织特别许可，需遵循AGPL-3.0开源协议合理合法使用本项目。
 *
 *  Enterprise users are required to use this project reasonably
 *  and legally in accordance with the AGPL-3.0 open source agreement
 *  without special permission from the smartboot organization.
 */

package org.smartboot.mqtt.common.message;

import org.smartboot.mqtt.common.enums.MqttVersion;
import org.smartboot.mqtt.common.message.payload.MqttPublishPayload;
import org.smartboot.mqtt.common.message.variable.MqttPublishVariableHeader;
import org.smartboot.mqtt.common.message.variable.properties.PublishProperties;
import org.smartboot.mqtt.common.util.TopicByteTree;
import org.smartboot.socket.DecoderException;

import java.nio.ByteBuffer;

/**
 * @author 三刀
 * @version V1.0 , 2018/4/22
 */
public class MqttPublishMessage extends MqttPacketIdentifierMessage<MqttPublishVariableHeader> {
    private static final MqttPublishPayload EMPTY_BYTES = new MqttPublishPayload(new byte[0]);
    private MqttPublishPayload payload;

    public MqttPublishMessage(MqttFixedHeader mqttFixedHeader) {
        super(mqttFixedHeader);
    }

    public MqttPublishMessage(MqttFixedHeader mqttFixedHeader, MqttPublishVariableHeader mqttPublishVariableHeader, byte[] payload) {
        super(mqttFixedHeader);
        setVariableHeader(mqttPublishVariableHeader);
        this.payload = new MqttPublishPayload(payload);
    }

    @Override
    public void decodeVariableHeader0(ByteBuffer buffer) {
        final TopicByteTree topic = TopicByteTree.DEFAULT_INSTANCE.search(buffer);
        //PUBLISH 报文中的主题名不能包含通配符
        if (topic.isWildcards()) {
            throw new DecoderException("invalid publish topic name: " + topic + " (contains wildcards)");
        }
        int packetId = -1;
        //只有当 QoS 等级是 1 或 2 时，报文标识符（Packet Identifier）字段才能出现在 PUBLISH 报文中。
        if (fixedHeader.getQosLevel().value() > 0) {
            packetId = decodeMessageId(buffer);
        }
        MqttPublishVariableHeader variableHeader;
        if (version == MqttVersion.MQTT_5) {
            PublishProperties properties = new PublishProperties();
            properties.decode(buffer);
            variableHeader = new MqttPublishVariableHeader(packetId, topic.getTopicName(), properties);
        } else {
            variableHeader = new MqttPublishVariableHeader(packetId, topic.getTopicName(), null);
        }

        setVariableHeader(variableHeader);
    }

    @Override
    public void decodePlayLoad(ByteBuffer buffer) {
        int readLength = getRemainingLength() - getVariableHeaderLength();
        if (readLength == 0) {
            payload = EMPTY_BYTES;
        } else {
            byte[] bytes = new byte[readLength];
            buffer.get(bytes);
            payload = new MqttPublishPayload(bytes);
        }
    }


    @Override
    public MqttPublishPayload getPayload() {
        return payload;
    }
}
