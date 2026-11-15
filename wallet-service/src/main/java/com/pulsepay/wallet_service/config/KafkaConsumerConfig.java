package com.pulsepay.wallet_service.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration //It contains rules and instructions to create the object our application needed and it also contains bean definitions.
public class KafkaConsumerConfig {
    //Get Broker address
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    @Value("${spring.kafka.consumer.group-id}")
    private String consumerGroupId;
    @Bean
    public ConsumerFactory<String,String> consumerFactory(){
        Map<String,Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG,
                consumerGroupId);
        // Imagine new brand consumer group comes, kafka asks where should this consumer start reading
        // if this group has no committed offset then it can start with earliest available message.
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(config);

    }
    @Bean
    //DeadLetterPublishingRecoverer after we are done retrying , send the failed kafka message to DLT
    //It takes Kafka template as parameter
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(KafkaTemplate<String,String> kafkaTemplate){
        //When you need to publish Failed message use this kafka template
        //When the message fails give me the failed record and exception send them to this topic and partition.
        return new DeadLetterPublishingRecoverer(kafkaTemplate,(record,exception) ->
                new TopicPartition(
                        record.topic() + ".DLT",
                        record.partition()
                ));



    }
    @Bean
    //DLT means we have kept the failed message or record after retries to their for investigation in the future.
    public DefaultErrorHandler kafkaErrorHandler(DeadLetterPublishingRecoverer recoverer){
        FixedBackOff fixedBackOff = new FixedBackOff(2000L,2);
        //My Kafka Listener failed so what do i do then it waits retires then at the end recover takes control.
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer,
                fixedBackOff);
        //We use addNotRetryableException only when we have temporary db connectivity failure, temporary network failure,downstream service unavailable.
        errorHandler.addNotRetryableExceptions(
                IllegalArgumentException.class
        );
        return errorHandler;
    }

    @Bean
    //ConcurrentKafkaListenerContainerFactory that creates the background workers behind @KafkaListener annotations and it uses consumer
            //Factory to grab all those networkconnections and configurations so that listeners know how to connect and talk to kafka.
    //COncurrentKafkaListenerContainerFactory is the one who runs and manage @KafkaListener, that creates Listener container which manages the consumers and their polls, listener container needs consumer factory which creates the consumer now this consumer keeps polling and kafka broker gives the record
    //Listener container factory invokes @kafkaListener it receives record from consumer now it tells i have record which method should i handle then it goes to consume.
    public ConcurrentKafkaListenerContainerFactory<String,String> kafkaListenerContainerFactory(
            ConsumerFactory<String,String> consumerFactory,DefaultErrorHandler kafkaErrorHandler){
        //Create the factory
        //ConcurentKafkaListenerContainerFactory is the one who uses consumer factory to create kafka consumer
        ConcurrentKafkaListenerContainerFactory<String,String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        //Listener use the consumerFactory we just configured.
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;

    }



}
