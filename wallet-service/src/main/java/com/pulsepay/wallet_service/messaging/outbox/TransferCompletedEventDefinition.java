package com.pulsepay.wallet_service.messaging.outbox;

import com.pulsepay.wallet_service.entity.OutboxEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TransferCompletedEventDefinition implements EventDefinition {
    private static final String EVENT_TYPE="TRANSFER_COMPLETED";
    private final String topic;
    public TransferCompletedEventDefinition(@Value("${pulsepay.kafka.topics.transfer-completed}") String topic){
        this.topic=topic;
    }
    @Override
    public boolean supports(OutboxEvent outboxEvent){
        return EVENT_TYPE.equals(outboxEvent.getEventType());
    }
    @Override
    public String getTopic(OutboxEvent outboxEvent){
        return topic;
    }
    @Override
    public String getKey(OutboxEvent outboxEvent){
        return outboxEvent.getAggregateId().toString();
    }


}
