package com.pulsepay.wallet_service.messaging.outbox;

import com.pulsepay.wallet_service.entity.OutboxEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
//OutboxEvent Dispatcher does nothing but searches the list of event definitions
//and give the correct definition.
@Component
@RequiredArgsConstructor
public class OutboxEventDispatcher {
    //Spring sees:
    //
    //TransferCompletedEventDefinition
    //        implements EventDefinition
    //        @Component and automatically put it into the list.
    private final List<EventDefinition> eventDefinitions;
    public EventDefinition dispatch(OutboxEvent outboxEvent){
        return eventDefinitions.stream()
                //Ask each registered event definition whether it supports this event.
                .filter(definition -> definition.supports(outboxEvent))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No event definition found for event type:" + outboxEvent.getEventType()));
    }


}
