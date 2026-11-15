package com.seashare.seasharem3.infrastructure.config.amqp;

/**
 * Nombres congelados de la topología AMQP (contracts/README §3.5). El exchange de eventos y sus tres
 * colas consumidoras son la única topología publicada; el exchange interno de comandos es [CONV] del
 * núcleo compartido (§3.5 lista las colas pero no el exchange).
 */
public final class AmqpNames {

    public static final String EXCHANGE_EVENTS = "seashare.reservations";
    public static final String EXCHANGE_COMMANDS = "seashare.finance.commands";

    public static final String QUEUE_RESERVATION_INFO = "finance.reservation-info.v1";
    public static final String QUEUE_RESERVATION_STATUS = "finance.reservation-status.v1";
    public static final String QUEUE_GUARANTEE_DISPUTE = "finance.guarantee-dispute.v1";
    public static final String QUEUE_CHARGE_COMMAND = "finance.charge-command.v1";
    public static final String QUEUE_REFUND_COMMAND = "finance.refund-command.v1";
    public static final String QUEUE_SETTLEMENT_COMMAND = "finance.settlement-command.v1";
    public static final String QUEUE_GATEWAY_NOTIFICATION = "finance.gateway-notification.v1";

    public static final String ROUTING_INFO_PROVIDED = "reservation.info.provided";
    public static final String ROUTING_STATUS_CHANGED = "reservation.status.changed";
    public static final String ROUTING_DISPUTE_UPDATED = "reservation.dispute.updated";

    public static final String ROUTING_CHARGE_COMMAND = "charge.command";
    public static final String ROUTING_REFUND_COMMAND = "refund.command";
    public static final String ROUTING_SETTLEMENT_COMMAND = "settlement.command";
    public static final String ROUTING_GATEWAY_NOTIFICATION = "gateway.notification";

    public static final String DLQ_SUFFIX = ".dlq";

    private AmqpNames() {
    }

    public static String deadLetterQueue(String queue) {
        return queue + DLQ_SUFFIX;
    }
}
