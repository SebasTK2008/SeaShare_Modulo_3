package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.domain.model.DisputeEvent;

/**
 * Persistencia de dispute_event_log (UC08) con deduplicación por (reservation_id, dispute_id, event_key).
 * Fuente: UC08 plan T008.
 * GAP: firma no publicada; se deriva la inserción idempotente del evento.
 */
public interface DisputeEventLogRepository {

    boolean insertIfAbsent(DisputeEvent event);
}
