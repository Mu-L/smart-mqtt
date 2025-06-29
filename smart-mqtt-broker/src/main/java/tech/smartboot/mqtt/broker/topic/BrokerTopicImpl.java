/*
 * Copyright (C) [2022] smartboot [zhengjunweimail@163.com]
 *
 *  企业用户未经smartboot组织特别许可，需遵循AGPL-3.0开源协议合理合法使用本项目。
 *
 *  Enterprise users are required to use this project reasonably
 *  and legally in accordance with the AGPL-3.0 open source agreement
 *  without special permission from the smartboot organization.
 */

package tech.smartboot.mqtt.broker.topic;

import tech.smartboot.feat.core.common.logging.Logger;
import tech.smartboot.feat.core.common.logging.LoggerFactory;
import tech.smartboot.mqtt.common.TopicToken;
import tech.smartboot.mqtt.common.exception.MqttException;
import tech.smartboot.mqtt.common.message.MqttCodecUtil;
import tech.smartboot.mqtt.plugin.spec.BrokerTopic;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;

/**
 * MQTT Broker端的主题管理类，负责处理主题的订阅、消息分发和共享订阅等功能。
 * <p>
 * 该类是MQTT Broker中主题管理的核心实现，主要功能包括：
 * <ul>
 *   <li>管理主题的订阅者，包括普通订阅和共享订阅</li>
 *   <li>处理消息的异步推送和分发</li>
 *   <li>维护主题的保留消息</li>
 *   <li>支持MQTT 5.0的共享订阅特性</li>
 * </ul>
 * </p>
 * <p>
 * 主题管理采用分组策略：
 * <ul>
 *   <li>默认订阅组（defaultGroup）- 处理普通的主题订阅</li>
 *   <li>共享订阅组（shareSubscribers）- 处理MQTT 5.0的共享订阅，支持负载均衡</li>
 * </ul>
 * </p>
 * <p>
 * 消息推送采用异步机制，通过ExecutorService和AsyncTask实现，确保消息处理的高效性和可靠性。
 * 使用信号量（Semaphore）控制消息推送的并发，避免消息堆积和资源耗尽。
 * </p>
 *
 * @author 三刀（zhengjunweimail@163.com）
 * @version V1.0 , 2018/5/3
 */
public class BrokerTopicImpl extends TopicToken implements BrokerTopic, Runnable {
    private static final int FLAG_ENABLED = 1 << 1;
    private static final int FLAG_LOCK = 1 << 2;
    private static final int FLAG_UPDATE = 1 << 3;
    private static final Logger LOGGER = LoggerFactory.getLogger(BrokerTopicImpl.class);
    private static final Map<String, SharedDeliverGroup> INITIAL_SUBSCRIBERS = Collections.emptyMap();
    /**
     * 默认订阅组，用于管理普通的主题订阅。
     * <p>
     * 当客户端进行普通订阅（非共享订阅）时，订阅信息将存储在此组中。
     * 每个订阅者都会收到发布到该主题的所有消息。
     * </p>
     */
    private final DeliverGroup defaultGroup = new DeliverGroup();
    /**
     * 共享订阅组映射，用于支持MQTT 5.0的共享订阅特性。
     * <p>
     * Key为共享订阅的主题过滤器，Value为对应的订阅组。
     * 共享订阅允许多个订阅者以负载均衡的方式接收消息，适用于集群环境。
     * </p>
     */
    private volatile Map<String, SharedDeliverGroup> sharedGroup = INITIAL_SUBSCRIBERS;
    private final ExecutorService executorService;

    private volatile int flag;
    private final byte[] encodedTopic;


    /**
     * 消息队列，用于存储待处理的消息。
     * <p>
     * 支持内存队列（MemoryMessageStoreQueue）或其他自定义的队列实现，
     * 用于临时存储待推送的消息，确保消息的可靠传递。
     * </p>
     */
    private final MemoryMessageStoreQueue messageQueue;
    /**
     * 当前主题的活跃订阅者队列。
     * <p>
     * 存储当前正在等待接收消息的订阅者，使用ConcurrentLinkedQueue确保
     * 在多线程环境下的安全访问。订阅者按照FIFO顺序处理。
     * </p>
     */
    private final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>();

    private static final Runnable BREAK = () -> {
    };

    public BrokerTopicImpl(String topic) {
        this(topic, 64, null);
    }

    public BrokerTopicImpl(String topic, int queueLength, ExecutorService executorService) {
        super(topic);
        this.executorService = executorService;
        this.messageQueue = new MemoryMessageStoreQueue(queueLength);
        this.encodedTopic = MqttCodecUtil.encodeUTF8(topic);
        setFlag(FLAG_ENABLED);
    }


    public DeliverGroup getSubscriberGroup(TopicToken topicToken) {
        if (topicToken.isShared()) {
            synchronized (this) {
                if (sharedGroup == INITIAL_SUBSCRIBERS) {
                    sharedGroup = new ConcurrentHashMap<>();
                }
                return sharedGroup.computeIfAbsent(topicToken.getTopicFilter(), s -> new SharedDeliverGroup(BrokerTopicImpl.this));
            }

        } else {
            return defaultGroup;
        }
    }

    public synchronized void removeShareGroup(String topicFilter) {
        sharedGroup.remove(topicFilter);
        if (sharedGroup.isEmpty()) {
            sharedGroup = INITIAL_SUBSCRIBERS;
        }
    }

    /**
     * 获取当前主题的订阅者总数。
     * <p>
     * 返回值包含默认订阅组和所有共享订阅组的订阅者数量之和。
     * 用于判断主题是否有活跃的订阅者，以及监控订阅状态。
     * </p>
     *
     * @return 订阅者总数
     */
    public int subscribeCount() {
        return sharedGroup.size() + defaultGroup.count();
    }

    /**
     * 注册一个新的消息推送任务。
     * <p>
     * 该方法将一个新的消息推送任务添加到当前主题的推送队列中。
     * 推送任务通常是一个Runnable对象，包含了具体的消息推送逻辑。
     * </p>
     *
     * @param subscriber 要添加的消息推送任务
     */
    public void registerMessageDeliver(BaseMessageDeliver subscriber) {
        queue.offer(subscriber);
    }

    public String getTopic() {
        return getTopicFilter();
    }

    @Override
    public TopicToken toTopicToken() {
        return this;
    }

    public byte[] encodedTopicBytes() {
        return encodedTopic;
    }

    public MemoryMessageStoreQueue getMessageQueue() {
        return messageQueue;
    }

    @Override
    public String toString() {
        return getTopic();
    }

    /**
     * 触发消息推送操作。
     * <p>
     * 当有新消息到达时，该方法会被调用以启动异步推送流程。推送过程：
     * <ul>
     *   <li>检查主题是否启用</li>
     *   <li>尝试获取推送信号量</li>
     *   <li>将推送任务提交到执行器进行异步处理</li>
     * </ul>
     * 使用信号量机制确保同一时刻只有一个推送任务在执行。
     * </p>
     */
    public void push() {
        if ((this.flag & (FLAG_ENABLED | FLAG_LOCK)) == FLAG_ENABLED) {
            //已加入推送队列
            synchronized (encodedTopic) {
                if (hasFlag(FLAG_LOCK)) {
                    return;
                }
                setFlag(FLAG_LOCK);
            }
            executorService.execute(this);
        }
    }

    public void addVersion() {
        if (hasFlag(FLAG_UPDATE)) {
            return;
        }
        setFlag(FLAG_UPDATE);
    }

    public void disable() {
        unsetFlag(FLAG_ENABLED);
    }

    public synchronized void setFlag(int v) {
        this.flag = this.flag | v;
    }

    public boolean hasFlag(int v) {
        return (this.flag & v) == v;
    }


    public synchronized void unsetFlag(int v) {
        this.flag = this.flag & ~v;
    }

    public void clear() {
        messageQueue.clear();
        //清除无效的订阅关系
        addVersion();
        push();
    }

    public void dump() {
//        System.out.println("默认订阅：");
//        defaultGroup.subscribers.forEach((session, topicConsumerRecord) -> {
//            System.out.println(" " + session.getClientId());
//        });
//        System.out.println("共享订阅：");
//        shareSubscribers.forEach((s, subscriberGroup) -> {
//            System.out.println(" " + s);
//            subscriberGroup.subscribers.forEach((session, topicConsumerRecord) -> {
//                System.out.println("  " + session.getClientId());
//            });
//        });
    }

    @Override
    public void run() {
        try {
            Runnable subscriber;
            queue.offer(BREAK);
            unsetFlag(FLAG_UPDATE);
            while ((subscriber = queue.poll()) != BREAK) {
                try {
                    subscriber.run();
                } catch (Exception e) {
                    LOGGER.error("batch publish exception:{}", e.getMessage(), e);
                }
            }
            unsetFlag(FLAG_LOCK);
            if (hasFlag(FLAG_UPDATE) && !queue.isEmpty()) {
                push();
            }
        } catch (MqttException e) {
            if (e.getCallback() != null) {
                e.getCallback().run();
            } else {
                LOGGER.error("execute async task exception", e);
            }
        } catch (Throwable throwable) {
            LOGGER.error("execute async task exception", throwable);
        }
    }
}
