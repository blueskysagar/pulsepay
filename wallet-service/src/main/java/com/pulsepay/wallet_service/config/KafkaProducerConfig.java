package com.pulsepay.wallet_service.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;


import java.util.HashMap;
import java.util.Map;
//ProducerFactory is the one who knows how to create and configure kafka producers.

@Configuration //This contains the instructions/configuration for creating the objects that the application needs and contain bean definition.
public class KafkaProducerConfig {
    // ============================================================
    // CREATE PRODUCER FACTORY
    // ============================================================
    // ProducerFactory knows how to create Kafka producers.
    //Spring go back to my application configuration and give me the value of$ after that
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    @Bean
    public ProducerFactory<String,String> producerFactory(){
        //KafkaProducer has many configuration Properties
        Map<String,Object> config = new HashMap<>();
        //Kafka, use these brokers when establishing the intital connection.
        config.put(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers
        );
        // Serializer converts string (KAFKA transferId) to bytes
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class);
        //We are doing JSONpayload which is in string use StringSerializer and converts to byte and send to kafka.
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class);
        // This tells kafka dont consider this message successfully acknowledged until all currently in sync replicas acknowledge it.
        config.put(
                ProducerConfig.ACKS_CONFIG,
                "all"
        );
        //This helps kafka prevent duplicate records caused by producer retries.
        config.put(
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,
                true
        );
        // DefaultKafkaProducerFactory basically have box of config or rules and settings which translate it so that java code can talk to kafka and open physical connections to kafka.
        return new DefaultKafkaProducerFactory<>(config);




    }
    // We are constructing KafkaTemplate and giving producer Factory, so when you ask me to send somthing use this producer factory configuration and settings.
    @Bean

    public KafkaTemplate<String,String> kafkaTemplate(){
        return new KafkaTemplate<>(producerFactory());
    }


}
