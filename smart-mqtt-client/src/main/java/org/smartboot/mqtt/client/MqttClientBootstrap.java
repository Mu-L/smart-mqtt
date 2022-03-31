package org.smartboot.mqtt.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartboot.mqtt.common.enums.MqttQoS;

/**
 * @author 三刀
 * @version V1.0 , 2018/4/24
 */
public class MqttClientBootstrap {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttClientBootstrap.class);

    public static void main(String[] args) {
        MqttClient client = new MqttClient("10.30.30.32", 11883, "stw");
        client.connect();
        client.sub("test", MqttQoS.EXACTLY_ONCE);
        client.callBack(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {

            }

            @Override
            public void messageArrived(String topic, byte[] payload) throws Exception {
                LOGGER.info(new String(payload));
            }
        });
//        while (true){
//            client.pub("test", MqttQoS.EXACTLY_ONCE, "test".getBytes());
//            try {
//                Thread.sleep(3000);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
    }
}