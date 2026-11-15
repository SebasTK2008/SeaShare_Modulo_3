# UC04 — Solicitar el valor calculado de la reserva

| Campo | Valor |
|---|---|
| Caso de uso | UC04 Solicitar el valor calculado de la reserva (HU1) |
| SPEC | `docs/features/004-solicitar-el-valor-calculado-de-la-reserva/spec.md` |
| Dirección | Sistema de Reservas y Operaciones → sistema |
| ¿Responde? | Sí (síncrono) |
| Quién puede llamarlo | Solo el Sistema de Reservas y Operaciones (usuario esperado; el sistema no valida el llamador — seguridad fuera de alcance, general-plan §7.1) [SPEC RF-001] |
| Efectos secundarios | Actualiza la `InformaciónDeReserva` con los montos calculados y la referencia a la versión de parámetros usada (`frozen_parameters_id`) [SPEC RF-008] — por eso es `POST` |

Leyenda y convenciones comunes: [`../README.md`](../README.md).

## 1. Propósito

Reservas pide el valor definitivo de una reserva ya registrada (UC03). El sistema recupera la información registrada, calcula el monto de alquiler, el seguro náutico y el depósito de garantía, **congela la versión de parámetros usada (`frozen_parameters_id`), de la que se leen los valores aplicados**, devuelve el desglose con el valor total y deja los montos disponibles para "Procesar cobro" [SPEC HU1].

## 2. Petición

`POST /api/v1/reservations/{reservation_id}/calculated-value` **[CONV]**. **Sin cuerpo**: la reserva se identifica solo por su identificador [SPEC RF-001, `SolicitudValorReserva`].

### Headers

| Header | Obligatorio | Valor |
|---|---|---|
| `Accept` | No | `application/json` |
| `X-Correlation-Id` | No | Cadena libre |

### Parámetros de ruta

| Parámetro | Tipo | Oblig. | Descripción | Origen |
|---|---|---|---|---|
| `reservation_id` | UUID v4 | Sí | Identificador de la reserva | [SPEC RF-001]; tipo [CONV] |

## 3. Reglas de procesamiento

1. Se recupera la `InformaciónDeReserva` registrada por UC03: tarifa base vigente, fecha de inicio, fecha de fin y pasajeros [SPEC RF-002].
2. **Monto de alquiler** = `tarifa base registrada × días inclusivos` (inicio = fin ⇒ 1 día; del día 10 al 12 ⇒ 3 días). Es el precio bruto: no incluye seguro, depósito ni comisión [SPEC HU1, RF-003].
3. **Seguro náutico** = `tarifa de seguro × pasajeros registrados` [SPEC RF-004].
4. **Depósito de garantía** = 10 % de la tarifa diaria vigente registrada (la misma del alquiler, ya con la tarifa dinámica resuelta; general-plan D-29); **no** se multiplica por duración, pasajeros ni daño [SPEC HU1, RF-005].
5. **Valor total** = alquiler + seguro + depósito; es el monto que se cobra en "Procesar cobro" [SPEC RF-006].
6. Los montos y la **referencia a la versión** de `financial_parameters` usada (`frozen_parameters_id`) se **guardan** en la `InformaciónDeReserva`; los valores aplicados (comisión % y tarifa de seguro) **no se copian**: se leen de esa versión [SPEC RF-008]. Si UC03 vuelve a registrar la reserva, los montos y la referencia se invalidan y deben recalcularse antes de un nuevo cobro [SPEC RF-008, UC03 casos extremos].
7. **Solicitud repetida**: se devuelve el mismo desglose y la actualización interna es idempotente [SPEC casos extremos]. El recálculo usa la **versión referenciada** (`frozen_parameters_id`) y, por tanto, no cambia aunque se publique una versión vigente más nueva (general-plan D-17); si no hay referencia (primer cálculo o tras invalidación de UC03), usa la **versión vigente**.
8. No se recalcula ni se vuelve a consultar la tarifa dinámica ni la tarifa base [SPEC CE-002].
9. No existe información registrada → error controlado, sin ningún monto parcial [SPEC RF-009, casos extremos].
10. Información registrada incompleta (por ejemplo, sin tarifa base) → error controlado, sin cálculo parcial [SPEC RNF-003, casos extremos].
11. **Primer cálculo (sin versión referenciada)**: no existe **versión vigente** de los parámetros financieros o la tarifa del seguro no está configurada → error controlado; no se calcula ni el seguro ni un total parcial. Un recálculo con referencia (`frozen_parameters_id`) siempre resuelve la versión referenciada (FK; la tabla es append-only) [SPEC casos extremos].

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

*(Ilustración: tarifa diaria vigente registrada 300000.00, reserva del día 10 al 12 = 3 días, seguro 15000.00 × 4 pasajeros, depósito 10 % de 300000.00.)*

## 5. Respuestas de error

| HTTP | `code` | Cuándo | `retryable` | Origen |
|---|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `reservation_id` no tiene formato válido | No | [CONV] |
| 404 | `RESERVATION_INFO_NOT_FOUND` | No existe información registrada para esa reserva | Sí (posible carrera con UC03, que registra la información de forma asíncrona) | [SPEC RF-009] |
| 422 | `RESERVATION_INFO_INCOMPLETE` | La información registrada no permite calcular (por ejemplo, sin tarifa base) | No | [SPEC RNF-003] |
| 503 | `FINANCIAL_PARAMETERS_NOT_CONFIGURED` | No existe versión vigente de los parámetros (primer cálculo, sin referencia) o la tarifa del seguro náutico no está configurada | Sí (cuando el Administrador Financiero configure) | [SPEC casos extremos] |
| 500 | `INTERNAL_ERROR` | Error no previsto | Sí | [CONV] |

```json
{
  "type": "about:blank",
  "title": "Reservation information not found",
  "status": 404,
  "detail": "There is no registered information for reservation b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10.",
  "code": "RESERVATION_INFO_NOT_FOUND",
  "retryable": true
}
```

## 6. Idempotencia y reintentos

Idempotente: devuelve siempre el mismo desglose mientras la información de la reserva no cambie (usa la versión de parámetros referenciada; la vigente solo si no hay referencia) [SPEC casos extremos]. Reintentar `RESERVATION_INFO_NOT_FOUND` es razonable si UC03 aún está en cola; la carrera UC03/UC04 se serializa con el bloqueo asesor por reserva (general-plan D-15).

## 7. Trazabilidad

HU1 (escenario 1) · RF-001 a RF-009 · RNF-001, RNF-002, RNF-003 · CE-001 a CE-004.