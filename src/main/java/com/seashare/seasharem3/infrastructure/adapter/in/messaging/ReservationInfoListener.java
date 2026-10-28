package com.seashare.seasharem3.infrastructure.adapter.in.messaging;

import com.seashare.seasharem3.application.port.in.RegisterReservationInformationUseCase;
import com.seashare.seasharem3.domain.exception.BaseRateException;
import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.exception.ReservationInformationValidationException;
import com.seashare.seasharem3.infrastructure.adapter.in.messaging.dto.ReservationInfoMessage;
import com.seashare.seasharem3.infrastructure.adapter.in.messaging.dto.ReservationInfoMessageMapper;
import com.seashare.seasharem3.infrastructure.config.amqp.AmqpListenerFailurePolicy;
import com.seashare.seasharem3.infrastructure.config.amqp.AmqpNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Locale;
import java.util.UUID;

/**
 * Adaptador de entrada de UC03: consume {@code reservation.info.provided} de la cola
 * {@code finance.reservation-info.v1} y delega en el puerto de entrada.
 *
 * <p>El mensaje se parsea manualmente para poder clasificar un cuerpo mal formado como error
 * permanente ({@code ack} + registro) en vez de dejarlo llegar a la DLQ. Los fallos permanentes
 * (validación, datos incompletos, tarifa no disponible) se registran con la política compartida y se
 * confirman; los transitorios se propagan para que la retry policy los reintente y el recoverer los
 * registre y los rechace a la DLQ. Fuente: UC03 plan T011, RF-006, RNF-003; contracts/README §4.4.</p>
 */
@Component
public class ReservationInfoListener {

    private static final Logger log = LoggerFactory.getLogger(ReservationInfoListener.class);

    private static final String USE_CASE = "UC03";
    private static final String JSON_CONTENT_TYPE = "application/json";

    private final RegisterReservationInformationUseCase useCase;
    private final ReservationInfoMessageMapper mapper;
    private final AmqpListenerFailurePolicy failurePolicy;
    private final JsonMapper jsonMapper;

    public ReservationInfoListener(RegisterReservationInformationUseCase useCase,
            ReservationInfoMessageMapper mapper,
            AmqpListenerFailurePolicy failurePolicy,
            JsonMapper jsonMapper) {
        this.useCase = useCase;
        this.mapper = mapper;
        this.failurePolicy = failurePolicy;
        this.jsonMapper = jsonMapper;
    }

    @RabbitListener(queues = AmqpNames.QUEUE_RESERVATION_INFO)
    public void onMessage(Message message) {
        MessageProperties properties = message.getMessageProperties();
        String messageId = properties.getMessageId();
        String payloadRef = payloadRef(messageId);

        log.info("Received reservation info event (messageId={})", messageId);

        if (!isJson(properties.getContentType()) || messageId == null || messageId.isBlank()) {
            recordAndAcknowledge(null, new ReservationInformationValidationException(
                    "reservation.info.provided requires Content-Type application/json and Message-Id"), payloadRef);
            return;
        }

        ReservationInfoMessage payload;
        try {
            payload = jsonMapper.readValue(message.getBody(), ReservationInfoMessage.class);
        } catch (RuntimeException exception) {
            recordAndAcknowledge(reservationIdOrNull(message), new ReservationInformationValidationException(
                    "reservation.info.provided body is not valid JSON for the contract", exception), payloadRef);
            return;
        }
        if (payload == null) {
            recordAndAcknowledge(null, new ReservationInformationValidationException(
                    "reservation.info.provided body must be a JSON object"), payloadRef);
            return;
        }

        try {
            useCase.register(mapper.toCommand(payload));
        } catch (BaseRateException exception) {
            if (exception.isTransient()) {
                throw exception;
            }
            recordAndAcknowledge(payload.reservationId(), exception, payloadRef);
        } catch (DomainException exception) {
            recordAndAcknowledge(payload.reservationId(), exception, payloadRef);
        }
    }

    private static boolean isJson(String contentType) {
        if (contentType == null) {
            return false;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT).trim();
        return normalized.equals(JSON_CONTENT_TYPE) || normalized.startsWith(JSON_CONTENT_TYPE + ";");
    }

    private void recordAndAcknowledge(UUID reservationId, Throwable failure, String payloadRef) {
        failurePolicy.recordAndAcknowledge(USE_CASE, reservationId, failure, payloadRef);
    }

    private static String payloadRef(String messageId) {
        return "queue=" + AmqpNames.QUEUE_RESERVATION_INFO + (messageId != null ? ";messageId=" + messageId : "");
    }

    /** Identificador best effort para registrar un mensaje mal formado sin impedir el {@code ack}. */
    private UUID reservationIdOrNull(Message message) {
        try {
            JsonNode node = jsonMapper.readTree(message.getBody());
            JsonNode value = node.get("reservation_id");
            return value != null && value.isString() ? UUID.fromString(value.stringValue()) : null;
        } catch (RuntimeException malformed) {
            return null;
        }
    }
}
