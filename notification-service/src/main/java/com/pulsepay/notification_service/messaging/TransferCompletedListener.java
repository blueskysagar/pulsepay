package com.pulsepay.notification_service.messaging;

import com.pulsepay.notification_service.messaging.event.TransferCompletedEvent;
import com.pulsepay.notification_service.service.NotificationProcessingService;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;

import java.nio.charset.StandardCharsets;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class TransferCompletedListener {
    //LoggerFactory creates logger and logger is writing application messages.
    private static final Logger log =
            LoggerFactory.getLogger(TransferCompletedListener.class);
    private final ObjectMapper objectMapper;
    private final NotificationProcessingService notificationProcessingService;
    @KafkaListener(
            topics = "${pulsepay.kafka.topics.transfer-completed}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    //Why consumerRecord because it is kafka message record , it gives acess to more than message body.
    //Just think that kafka is the message sent via email, payload is the content and eventIdHeader is the shipping labels in the outside, telling about trackingdetails.
    public void onTransferCompleted(ConsumerRecord<String,String> record) throws JacksonException{
        String payload = record.value();
        Header eventIdHeader = record.headers().lastHeader("event_id");
        if(eventIdHeader==null){
            throw new IllegalStateException(
                    "Transfer-completed event is missing event_id header"
            );
        }
        //The EventIdheader value is in byte means we couldnt understand so we convert into human readable text format
        String eventId = new String(
                eventIdHeader.value(),
                StandardCharsets.UTF_8
        );
        //Now we convert JsonText into java object
        TransferCompletedEvent event = objectMapper.readValue(payload, TransferCompletedEvent.class);
        notificationProcessingService.processTransferCompleted(event,eventId);
        log.info(
                "Processed completed tranfer: transferId={}, eventId={}",
                event.transferId(),
                eventId
        );
    }


}
