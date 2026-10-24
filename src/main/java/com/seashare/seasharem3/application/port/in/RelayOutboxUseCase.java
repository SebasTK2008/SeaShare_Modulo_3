package com.seashare.seasharem3.application.port.in;

/**
 * Puerto del job de relay del outbox: publica los mensajes pendientes en el broker y los marca publicados.
 * Fuente: UC11 plan T046 (núcleo compartido) y general-plan D-08/§9 (patrón outbox + job programado).
 * [CONV] núcleo compartido: único job compartido además de los jobs de conciliación de cada UC.
 */
public interface RelayOutboxUseCase {

    /**
     * Publica hasta {@code seashare.outbox.batch-size} mensajes pendientes y devuelve cuántos publicó.
     * Debe protegerse con ShedLock (una sola instancia activa).
     */
    int relay();
}
