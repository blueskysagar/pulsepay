package com.pulsepay.wallet_service.messaging.consumer;

import com.pulsepay.wallet_service.dto.event.TransferCompletedEvent;
import com.pulsepay.wallet_service.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TransferCompletedEventConsumer {
    private final ObjectMapper objectMapper;
    private final ProcessedEventService processedEventService;
    //Hey spring keep watching this kafka topic, when message arrives give it to my methods.
    @KafkaListener(
            topics = "${pulsepay.kafka.topics.transfer-completed}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    //We want entire kafka message so we use ConsumerRecord
    public void consume(ConsumerRecord<String,String>record){
        //Now we need to find the header which producer added
        Header eventIdHeader = record.headers().lastHeader("event-id");
        if(eventIdHeader==null){
            throw new IllegalStateException(
                    "Missing event-id Kafka header"
            );
        }
        UUID eventId = UUID.fromString(new String(
                eventIdHeader.value(),
                StandardCharsets.UTF_8
        ));
        TransferCompletedEvent event=
                objectMapper.readValue(
                        record.value(),
                        TransferCompletedEvent.class
                );
        processedEventService.process(
                eventId,
                "TRANSFER_COMPLETED",
                event
        );


    }


}
