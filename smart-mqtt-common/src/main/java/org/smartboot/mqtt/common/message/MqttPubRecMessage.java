package org.smartboot.mqtt.common.message;

import org.smartboot.mqtt.common.enums.MqttMessageType;
import org.smartboot.mqtt.common.enums.MqttQoS;

/**
 * @author 三刀
 * @version V1.0 , 2018/4/22
 */
public class MqttPubRecMessage extends SingleByteFixedHeaderAndPacketIdMessage {
    public MqttPubRecMessage(MqttFixedHeader mqttFixedHeader) {
        super(mqttFixedHeader);
    }

    public MqttPubRecMessage(MqttFixedHeader mqttFixedHeader, int packetId) {
        super(mqttFixedHeader, packetId);
    }

    public MqttPubRecMessage() {
        super(new MqttFixedHeader(MqttMessageType.PUBREC, false, MqttQoS.AT_MOST_ONCE, false, 0));
    }
}