package com.pulsepay.wallet_service.messaging.outbox;

import com.pulsepay.wallet_service.entity.OutboxEvent;

public interface EventDefinition {
    //Is this eventDefinition responsible for this particular outbox event. for example
    //transfer definition will say yes wallet definition will say no.
    boolean supports(OutboxEvent outboxEvent);
    // Receive this event" means "Which topic should Kafka deposit this event into?"
    String getTopic(OutboxEvent outboxEvent);
    //Which kafka key should this event use
    String getKey(OutboxEvent outboxEvent);
}
