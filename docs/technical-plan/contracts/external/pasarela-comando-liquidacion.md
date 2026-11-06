# Libro interno de liquidación (simulado)

| Campo | Valor |
|---|---|
| Caso de uso | UC10 Liquidar fondos de alquiler |
| SPEC | `docs/features/010-liquidar-fondos-alquiler/spec.md` |
| Dirección | Worker de UC10 → libro interno simulado |
| ¿Responde? | Sí (respuesta síncrona técnica) |
| Responsable de implementarlo | `SimulatedSettlementGatewayAdapter` (adaptador de salida) |

Leyenda y convenciones comunes: [`../README.md`](../README.md).

## 1. Propósito

UC10 registra la liquidación de una reserva en un **libro interno simulado**. Esta entrega **no
dispersa fondos reales en Mercado Pago**: el cobro y el reembolso usan la Payments API, pero la
liquidación al Propietario se resuelve dentro del sistema para mantener el flujo de UC10 completo y
auditable. El marketplace real de Mercado Pago (split de pagos y cuentas vinculadas de los
Propietarios) queda como **fase futura no implementada** (D-UC10-18).

El registro que produce (`settlement_record` y, en la liquidación estándar, `commission_record`) es
un **asiento contable interno**: acredita cuánto corresponde al Propietario, pero **no transfiere
dinero** ni acredita fondos en su cuenta de Mercado Pago.

El puerto de aplicación es `SettlementGatewayPort.send(GatewaySettlementRequest)` y su única
implementación es `SimulatedSettlementGatewayAdapter`. La capacidad persistida en
`settlement_intent.gateway_capability` es siempre `STANDARD_SETTLEMENT`; la migración inicial
`V1__initial_schema.sql` no admite la capacidad de dispersión, que no existe en la integración real.

## 2. Petición (puerto de salida)

El worker invoca el puerto con el comando `GatewaySettlementRequest`:

| Campo | Tipo | Oblig. | Descripción | Origen |
|---|---|---|---|---|
| `reservationId` | UUID | Sí | Reserva liquidada | `settlement_intent.reservation_id` |
| `amount` | decimal (4 decimales internos) | Sí | Monto neto de la liquidación; el desglose queda en la intención y en el registro | `settlement_intent.amount` |
| `operationKey` | string | Sí | Clave idempotente UUID v4 del intento, persistida en `settlement_intent.idempotency_key` | Generada una vez al crear la intención y reutilizada en los reintentos |

No hay endpoint HTTP, headers, `Authorization` ni payload JSON: el adaptador simulado no contacta a
Mercado Pago. Tampoco existen datos de cuenta destino ni de comisión dentro del comando; la comisión
se calcula antes, en el dominio (`SettlementCalculator`), y se conserva en la intención.

## 3. Reglas de procesamiento

1. **Referencia determinista**: el adaptador devuelve
   `external_reference = "sim-settlement-" + operationKey`. Un reintento del worker con la misma
   clave produce exactamente la misma referencia, de modo que `settlement_record` sigue siendo único
   por intención y la conciliación puede reparar un registro incompleto sin duplicarlo.
2. **Resultado síncrono**: el libro interno responde `approved` con el detalle
   `simulated internal settlement`. No existe una pasarela remota que pueda dejar la operación en
   `pending` ni un webhook de liquidación que la confirme después.
3. **Sin dispersión real**: el adaptador no mueve dinero, no valida cuentas de Mercado Pago y no
   usa credenciales de marketplace. La capacidad
   `STANDARD_SETTLEMENT` describe el libro interno, no una operación de split payments.
4. **Idempotencia**: la clave UUID v4 se persiste al crear la intención y no cambia en los
   reintentos técnicos. La identidad de negocio que decide si se crea una intención nueva sigue
   siendo `reservation_id` + `status` + tipo de operación + clave; un cambio de estado de la reserva
   es un intento nuevo y recibe una clave nueva (D-UC10-10).
5. **Modo único**: `seashare.gateway.settlement-mode` solo admite `simulated`. Cualquier otro valor
   impide arrancar la aplicación (`SettlementConfiguration`); no hay un camino real que se pueda
   activar por configuración en esta entrega.

## 4. Respuesta exitosa (técnica)

| Campo | Tipo | Descripción | Origen |
|---|---|---|---|
| `status` | string | Siempre `approved` en el libro interno | `GatewaySettlementResult.status` |
| `statusDetail` | string | `simulated internal settlement` | `GatewaySettlementResult.statusDetail` |
| `externalId` | string | `sim-settlement-<idempotency_key>`; se persiste en `settlement_intent.external_reference` antes de registrar el resultado | `GatewaySettlementResult.externalId` |
| `amount` | decimal | Monto de la liquidación | `GatewaySettlementResult.amount` |

### Conciliación

No existe una consulta remota. `ReconcileSettlementIntentsUseCase`:

- republica una intención `PENDING_SEND` sin referencia externa;
- repara una intención con referencia externa reenviando el resultado simulado determinista a
  `RegisterSettlementResultUseCase`;
- registra una sola vez en `operational_failure` una intención sin referencia que ya avanzó de
  estado, porque el libro interno no puede confirmarla.

Una intención `COMPLETED` que ya tiene su `settlement_record` se omite.

## 5. Manejo de errores

El adaptador simulado no produce rechazos ni fallos de comunicación en esta entrega. El worker
conserva igualmente la clasificación para una futura implementación real: un
`GatewayBusinessRejectionException` deja la intención en `REJECTED` sin reintento y un
`GatewayCommunicationException` la deja en `COMMUNICATION_ERROR` y relanza para que la cola
reintente con la misma clave. Una respuesta sin `externalId` también se trata como fallo transitorio
y no se registra.

## 6. Trazabilidad

HU1 a HU3 · RF-005, RF-006, RF-008, RF-010, RF-016 · RNF-001 a RNF-003 · D-UC10-10, D-UC10-18.
