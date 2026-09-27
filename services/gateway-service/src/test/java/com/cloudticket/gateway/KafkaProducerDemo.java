package com.cloudticket.gateway;

import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;

public class KafkaProducerDemo {
    public static void main(String[] args) {
        // 1. 配置生产者属性
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        // 2. 创建生产者实例
        KafkaProducer<String, String> producer = new KafkaProducer<>(props);

        // 3. 发送消息（带回调）
        for (int i = 0; i < 10; i++) {
            ProducerRecord<String, String> record =
                    new ProducerRecord<>("my-topic", "key-" + i, "value-" + i);

            producer.send(record, new Callback() {
                @Override
                public void onCompletion(RecordMetadata metadata, Exception exception) {
                    if (exception != null) {
                        exception.printStackTrace();
                    } else {
                        System.out.printf("消息发送成功 -> topic: %s, partition: %d, offset: %d%n",
                                metadata.topic(), metadata.partition(), metadata.offset());
                    }
                }
            });
        }

        // 4. 关闭生产者（释放资源）
        producer.flush();
        producer.close();
    }
}