# UC04 — Solicitar el valor calculado de la reserva

| Campo | Valor |
|---|---|
| Caso de uso | UC04 Solicitar el valor calculado de la reserva (HU1) |
| SPEC | `docs/features/004-solicitar-el-valor-calculado-de-la-reserva/spec.md` |
| Dirección | Sistema de Reservas y Operaciones → sistema |
| ¿Responde? | Sí (síncrono) |
| Quién puede llamarlo | Solo el Sistema de Reservas y Operaciones [SPEC RF-001] |
| Efectos secundarios | Actualiza la `InformaciónDeReserva` con los montos calculados [SPEC RF-008] — por eso es `POST` |

Leyenda y convenciones comunes: [`../README.md`](../README.md).

## 1. Propósito

Reservas pide el valor definitivo de una reserva ya registrada (UC03). El sistema recupera la información registrada, calcula el monto de alquiler, el seguro náutico y el depósito de garantía, devuelve el desglose con el valor total y deja los montos disponibles para "Procesar cobro" [SPEC HU1].

## 2. Petición

`POST /api/v1/reservations/{reservation_id}/calculated-value` **[CONV]** (OQ-02). **Sin cuerpo**: la reserva se identifica solo por su identificador [SPEC RF-001, `SolicitudValorReserva`].

### Headers

| Header | Obligatorio | Valor |
|---|---|---|
| `Authorization` | Sí | `Bearer <token>` [PEND OQ-01] |
| `Accept` | No | `application/json` |
| `X-Correlation-Id` | No | Cadena libre |

### Parámetros de ruta

| Parámetro | Tipo | Oblig. | Descripción | Origen |
|---|---|---|---|---|
| `reservation_id` | UUID | Sí | Identificador de la reserva | [SPEC RF-001]; tipo [PEND] OQ-05 |

## 3. Reglas de procesamiento

1. Se recupera la `InformaciónDeReserva` registrada por UC03: tarifa base vigente, fecha de inicio, fecha de fin y pasajeros [SPEC RF-002].
2. **Monto de alquiler** = `tarifa base registrada × días inclusivos` (inicio = fin ⇒ 1 día; del día 10 al 12 ⇒ 3 días). Es el precio bruto: no incluye seguro, depósito ni comisión [SPEC HU1, RF-003].
3. **Seguro náutico** = `tarifa de seguro × pasajeros registrados` [SPEC RF-004].
4. **Depósito de garantía** = 10 % de la tarifa base diaria; **no** se multiplica por duración, pasajeros ni daño [SPEC HU1, RF-005]. Observación 5 del plan §12.2 (tarifa registrada vs. sin ajuste dinámico).
5. **Valor total** = alquiler + seguro + depósito; es el monto que se cobra en "Procesar cobro" [SPEC RF-006].
6. Los montos se **guardan** en la `InformaciónDeReserva` [SPEC RF-008]. Si UC03 vuelve a registrar la reserva, los montos se invalidan y deben recalcularse antes de un nuevo cobro [SPEC RF-008, UC03 casos extremos].
7. **Solicitud repetida**: se devuelve el mismo desglose y la actualización interna es idempotente [SPEC casos extremos]. Si los parámetros cambiaron entre dos solicitudes sin que UC03 invalidara los montos, se devuelven los ya registrados (D-17, [PEND] OQ-07).
8. No se recalcula ni se vuelve a consultar la tarifa dinámica ni la tarifa base [SPEC CE-002].
9. No existe información registrada → error controlado, sin ningún monto parcial [SPEC RF-009, casos extremos].
10. Información registrada incompleta (por ejemplo, sin tarifa base) → error controlado, sin cálculo parcial [SPEC RNF-003, casos extremos].
11. Tarifa del seguro sin configurar → error controlado; no se calcula ni el seguro ni un total parcial [SPEC casos extremos].

## 4. Respuesta exitosa

`200 OK` — `Content-Type: application/json`

| Campo | Tipo | Descripción | Origen |
|---|---|---|---|
| `reservation_id` | UUID | Identificador de la reserva | [SPEC `DesgloseValorReserva`] |
| `rental_amount` | string decimal | Monto de alquiler bruto | [SPEC RF-007] |
| `insurance_amount` | string decimal | Monto del seguro náutico | [SPEC RF-007] |
| `guarantee_deposit_amount` | string decimal | Monto del depósito de garantía | [SPEC RF-007] |
| `total_amount` | string decimal | Valor total de la reserva | [SPEC RF-007] |

```json
{
  "reservation_id": "b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10",
  "rental_amount": "900000.00",
  "insurance_amount": "60000.00",
  "guarantee_deposit_amount": "30000.00",
  "total_amount": "990000.00"
}
```

*(Ilustración: tarifa base 300000.00, reserva del día 10 al 12 = 3 días, seguro 15000.00 × 4 pasajeros, depósito 10 % de 300000.00.)*

## 5. Respuestas de error

| HTTP | `code` | Cuándo | `retryable` | Origen |
|---|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `reservation_id` no tiene formato válido | No | [CONV] |
| 401 | `UNAUTHENTICATED` | Credencial ausente o inválida | No | [PEND] OQ-01 |
| 403 | `FORBIDDEN` | El llamador no es el Sistema de Reservas y Operaciones | No | [SPEC RF-001] |
| 404 | `RESERVATION_INFO_NOT_FOUND` | No existe información registrada para esa reserva | Sí (OQ-06) | [SPEC RF-009] |
| 422 | `RESERVATION_INFO_INCOMPLETE` | La información registrada no permite calcular (por ejemplo, sin tarifa base) | No | [SPEC RNF-003] |
| 503 | `FINANCIAL_PARAMETERS_NOT_CONFIGURED` | La tarifa del seguro náutico no está configurada | No | [SPEC casos extremos] |
| 500 | `INTERNAL_ERROR` | Error no previsto | Sí | [CONV] |

```json
{
  "type": "about:blank",
  "title": "Reserva sin información registrada",
  "status": 404,
  "detail": "No existe información registrada para la reserva b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10.",
  "code": "RESERVATION_INFO_NOT_FOUND",
  "retryable": true
}
```

## 6. Idempotencia y reintentos

Idempotente: devuelve siempre el mismo desglose mientras la información de la reserva no cambie [SPEC casos extremos]. Reintentar `RESERVATION_INFO_NOT_FOUND` es razonable si UC03 aún está en cola (OQ-06).

## 7. Trazabilidad

HU1 (escenario 1) · RF-001 a RF-009 · RNF-001, RNF-002, RNF-003 · CE-001 a CE-004.