package com.group2.fse.ledger_service.service;

import com.group2.fse.ledger_service.entity.OutboxEvent;

public interface OutboxService {

    /**
     * Appends an event to the Oracle OUTBOX table in the current database transaction.
     */
    OutboxEvent appendEvent(String topic, String eventKey, Object payload);
}
