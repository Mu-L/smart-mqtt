package org.smartboot.mqtt.broker.openapi.to;

/**
 * @author 三刀（zhengjunweimail@163.com）
 * @version V1.0 , 2023/1/24
 */
public class SubscriptionTO {
    /**
     * 客户端ID
     */
    private String clientId;

    /**
     * 订阅主题
     */
    private String topic;

    /**
     * Broker IP地址
     */
    private String brokerIpAddress;

    /**
     * 消息质量
     */
    private int qos;

    /**
     * 订阅选项
     */
    private int options;

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public int getQos() {
        return qos;
    }

    public void setQos(int qos) {
        this.qos = qos;
    }

    public int getOptions() {
        return options;
    }

    public void setOptions(int options) {
        this.options = options;
    }

    public String getBrokerIpAddress() {
        return brokerIpAddress;
    }

    public void setBrokerIpAddress(String brokerIpAddress) {
        this.brokerIpAddress = brokerIpAddress;
    }
}