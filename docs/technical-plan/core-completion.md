# Núcleo compartido — informe de cierre (Etapas A–F)

Rama: `feature/core-compartido`. Fecha: 2026-10-17.
Estado: **completado y verificado**; listo para que UC01–UC13 trabajen en paralelo sobre este commit sin tocar el núcleo.

## 1. Verificación

| Comprobación | Comando | Resultado |
|---|---|---|
| Compilación + pruebas | `./mvnw -B verify` | **BUILD SUCCESS** — 77 pruebas, 0 fallos, 11 omitidas |
| Reglas ArchUnit (§3.4) | `arch/ArchitectureTest` | 7/7 en verde |
| Pruebas de integración (Testcontainers) | `FlywaySchemaTest` (10) + `SeashareM3ApplicationTests` (1) | **Omitidas en esta máquina** (daemon Docker apagado); CI las ejecuta |

- Las 11 omitidas usan `@Testcontainers(disabledWithoutDocker = true)`; no hay verificación local de Flyway/JPA contra PostgreSQL real. La primera ejecución de CI (`.github/workflows/ci.yml` con `./mvnw -B verify`) es la validación pendiente del núcleo.
- Commits de la rama (orden): `a4b00aa` (dominio + puertos/DTOs + V1–V8) → `bcbaf8d` (build: stack de pruebas, Docker, CI) → `527d398` (esquema: columnas de trazabilidad, unicidad de registros, vista) → `7195582` (dominio completo) → `25f5c4d` (puertos del webhook/relay/outbox) → `88732f4` (adaptadores, topología AMQP, scheduler, errores) → `008a4cd` (ArchUnit, fakes, pruebas).

## 2. Qué entrega el núcleo (congelado)

### 2.1 Dominio (`domain/`, sin Spring/JPA/Jackson — ArchUnit regla 1)

- **valueobject (29)**: `Money` (escala interna 4, salida HALF_UP a 2), `Percentage` (ZERO/HUNDRED), ids tipados (`ReservationId`, `BoatId`, `OwnerId`, `DisputeId`), `IdempotencyKey`, `FrozenParameters`, `ReservationValue`, `BaseRate`, `HighSeasonWindow`, `PaymentMethodReference` y los enums de estado/operación (`ChargeIntentStatus`, `RefundIntentStatus`, `SettlementStatus`, `DepositDispositionStatus`, `DisputeStatus`, `ReservationStatus`, `FinancialOperationType`, `RefundOperationType`, `RefundScope`, `RefundTrigger`, `SettlementScope`, `PaymentConfirmationStatus`, `Periodicity`, `ReportScope`, `ReportPeriod`, `PlannedOperation`).
- **`SettlementTrigger` congelado**: `COMPLETED`, `CANCELLED_MODERATE`, `CANCELLED_STRICT`, `DISPUTE_COMPLETED` (UC08 usa el último con `scope = DEPOSIT` + `dispute_id`).
- **model (13)**: `ReservationInformation` (create/withBaseRate/withCalculated con invariantes), `ChargeIntent` (máquina de estados [CONV] del Bloque C), `FinancialParameters`; el resto son stubs reservados al UC dueño (ver `core-gaps.md` §3).
- **exception (5)**: `DomainException`, `BaseRateException` (+`BaseRateFailureReason` con `isTransient`), `ReservationValueNotCalculatedException`, `InvalidPaymentTokenException`, `FinancialParametersNotConfiguredException`.

### 2.2 Aplicación (`application/`, solo depende de domain — ArchUnit regla 2)

- **26 puertos de entrada** (`port/in`): los 13 UC + `RelayOutboxUseCase`, `GatewayWebhookHandler`, jobs de conciliación (`ReconcileChargeIntentsUseCase`, `ReconcileRefundIntentsUseCase`), etapas internas de UC05/UC09 (`SendChargeToGatewayUseCase`, `RegisterChargeResultUseCase`, `ProcessChargeUseCase`, `SendRefundToGatewayUseCase`, `RegisterRefundResultUseCase`, `RegisterSettlementResultUseCase`) y las entradas de UC11/UC03.
- **25 puertos de salida** (`port/out`): repositorios de agregados, `FinancialParametersRepository` (+`findById`), puertos de consulta (`ChargeRecordQueryPort`, `FinancialRecordQueryPort`, `FinancialReportQueryPort`), pasarelas (`ChargeGatewayPort`, `RefundGatewayPort`, `SettlementGatewayPort`), `FleetRatePort` y los técnicos: `ClockPort`, `FailureRecorderPort`, `OutboxPort`, `OutboxMessageRepository`, `MessageBrokerPort`, `ReservationLockPort`.
- **39 DTOs** (`dto/`): commands/results de todos los UC y de los contratos gateway (`GatewayWebhookNotification`, `ChargeGatewayRequest/Result`, etc.) y `PendingOutboxMessage`.
- **Errores del contrato**: `ErrorCode` (catálogo completo de `contracts/README §3.4`), `ContractException` (code + detalle + `errors[]`) y `ApiFieldError`. [CONV] viven en `application/exception` (no en infrastructure) para que los servicios de UC puedan lanzarlos sin romper la regla §3.4-2.

### 2.3 Infraestructura (adaptadores técnicos que ya funcionan)

| Componente | Comportamiento |
|---|---|
| `SystemClockAdapter` | `ClockPort.today()` en `seashare.timezone` (America/Bogota) |
| `TimeConfig` | Bean `Clock` en UTC para instantes persistidos/publicados |
| `OperationalFailurePersistenceAdapter` | Inserta en `operational_failure` con `REQUIRES_NEW` (sobrevive al rollback del llamador) |
| `OutboxPersistenceAdapter` | `OutboxPort.publish` en `MANDATORY` (dentro de la transacción del cambio de estado) + lectura/marcado del relay |
| `RabbitMessagePublisher` | Publicación persistente con *publisher confirms* + detección de no enrutables (`seashare.outbox.publish-confirm-timeout`, por defecto 5s) |
| `RabbitTopologyConfig` | Declara exchange `seashare.reservations` (topic) + 3 colas consumidoras quorum + DLQ, exchange `seashare.finance.commands` (direct) [CONV] + 3 colas de comando + DLQ, bindings y `JacksonJsonMessageConverter` (Jackson 3) |
| `OutboxRelayService` + `OutboxRelayJob` | Relay cada `seashare.outbox.relay-interval` (5s) con lote `seashare.outbox.batch-size` (100), ShedLock sobre tabla `shedlock` con hora de BD; entrega at-least-once; bean construido por `OutboxConfig` (application sigue libre de Spring) |
| `SchedulingConfig` | `@EnableScheduling` + ShedLock (`lockAtMostFor` 5 min / `lockAtLeastFor` 1 min por defecto) |
| `PostgresReservationLockAdapter` | `pg_advisory_xact_lock` por reserva (D-15); [CONV] trunca msb/lsb a int (colisión = bloqueo más grueso); exige transacción activa del llamador |
| `CorrelationIdFilter` | Toma/genera `X-Correlation-Id`, lo devuelve y lo publica en MDC (`logging.pattern.level` lo imprime) |
| `GlobalExceptionHandler` + `ApiProblemDetailsFactory` | Problem Details (RFC 9457) con `type=about:blank`, `title`, `status`, `detail`, `code`, `retryable` y `errors[]` solo en validación; traduce `ContractException`, validaciones de body/parámetros, JSON mal formado, 405/415/406, `BaseRateException`, `FinancialParametersNotConfiguredException`, `InvalidPaymentTokenException`; 5xx genérico + log con correlación; deja pasar intactas las respuestas del framework (p. ej. 404 de ruta) |
| `JacksonConfig` | `BigDecimal` → string decimal (nunca número JSON; §3.1). [CONV] los clientes de pasarela construidos aparte deben configurar su propio mapper si Mercado Pago exige números |

### 2.4 Base de datos (Flyway V1–V9, **congeladas**)

- Orden D-31: núcleo (V1) → UC11 (V2) → UC03 (V3) → UC05 (V4) → UC09 (V5) → UC10 (V6) → UC08 (V7) → UC07 (V8) → vista UC12 (V9). Fila D-31 de `general-plan.md` corregida para incluir UC07.
- **Decisión de la rama**: V1–V8 se editaron *in-place* (sin migración V9 de `ALTER`s). ⚠️ **Al rebasar este commit, recrear la base local**: los checksums de Flyway cambian (`docker compose down -v` o `DROP SCHEMA public CASCADE`). Desde ahora V1–V9 no se tocan; cambios futuros van en migraciones `V10+`.
- Endurecimientos del Bloque B: `UNIQUE` por FK en `charge_record`/`refund_record`/`settlement_record` (un registro por intención), columnas de trazabilidad (`refund_intent.created_at`; `settlement_intent.status_detail/external_reference/dispute_id/created_at`), índices por `external_reference` en las 3 intenciones e índices de `operational_failure`.

## 3. Contratos congelados (nadie los redefine)

1. **Errores REST**: usar `contracts/README §3.3–§3.4`; lanzar `ContractException(ErrorCode.X, detalle[, errors])` desde el UC; **prohibido** crear códigos nuevos sin pasar por la tabla de decisión §4.2. `UNAUTHENTICATED` (401) solo aplica a la firma del webhook.
2. **Dinero y porcentajes**: `Money`/`BigDecimal` como **string decimal** en JSON (el núcleo ya lo globaliza); escala interna 4, salida 2 HALF_UP.
3. **AMQP**: nombres en `AmqpNames`; eventos salen por outbox (at-least-once); consumidores deduplican por clave idempotente; reintentos de listener 5× backoff exponencial en `application.properties`; al agotarlos DLQ + `operational_failure`.
4. **Webhook de Pasarela**: el UC05 implementa el controller y despacha por `type` (`payment`→UC05, `refund`→UC09, `merchant_order`→UC10) sobre `GatewayWebhookHandler`; asociar SOLO por `data.id == external_reference`; firma inválida → `ContractException(UNAUTHENTICATED)`; tipo desconocido → 200 + `operational_failure`.
5. **Zona horaria**: fechas de negocio vía `ClockPort`; instantes con el bean `Clock` (UTC).
6. **Concurrencia por reserva**: `ReservationLockPort` (D-15) dentro de transacción; la libera el commit/rollback.
7. **Outbox**: publicar SIEMPRE con `OutboxPort` en la misma transacción del cambio de estado; el relay es automático (no crear otro job de relay).
8. **Trazabilidad**: `X-Correlation-Id` y MDC ya resueltos por el filtro (no duplicar).

## 4. Listo para paralelizar — qué tiene cada UC

| UC | Lo que el núcleo le da | Lo que el UC construye |
|---|---|---|
| UC01 | `EstimateSingle/BatchUseCase` + commands/results, `Money`/`Percentage`/`BaseRate`, `ClockPort`, errores (`INVALID_DATE_RANGE`, `BATCH_SIZE_EXCEEDED`, `BASE_RATE_NOT_AVAILABLE`, `FLEET_UNAVAILABLE`, `FINANCIAL_PARAMETERS_NOT_CONFIGURED`), handler web | Servicio de estimación, controller, `FleetRatePort` de UC02 por `port.in`, adapter Flota |
| UC02 | `ProvideBaseRateUseCase` + command/result, `FleetRatePort`, `FinancialParametersRepository`, `BaseRateException` (+motivos), `HighSeasonWindow` | Servicio del calendario/tarifa y adapter Flota compartido |
| UC03 | `RegisterReservationInformationUseCase` + command, `ReservationInformation`, `ReservationInformationRepository`, `FailureRecorderPort`, `OutboxPort`, cola `finance.reservation-info.v1`, `InMemoryFailureRecorder`/`InMemoryOutbox` | Listener AMQP, persistencia con *upsert* e invalidación de montos, publicación del evento |
| UC04 | `CalculateReservationValueUseCase` + command/result, `ReservationInformation.withCalculated`, `FrozenParameters`, `FinancialParametersRepository.findById`, errores (`RESERVATION_INFO_NOT_FOUND` retryable, `RESERVATION_INFO_INCOMPLETE`, `FINANCIAL_PARAMETERS_NOT_CONFIGURED`) | Servicio de cálculo, controller, persistencia de montos/versión congelada |
| UC05 | `ProcessChargeUseCase`, `Send/RegisterChargeResultUseCase`, `ReconcileChargeIntentsUseCase`, `ChargeIntent` (+máquina de estados), `ChargeIntentRepository` (+`findByExternalReference`/`findByStatusIn`), `ChargeRecordRepository`, `ChargeGatewayPort`, `OutboxPort`, `ReservationLockPort`, `GatewayWebhookHandler`, excepciones de token/valor, cola `finance.charge-command.v1` | Worker/listener, webhook, resiliencia (`resilience4j-spring-boot4` ya en el pom), adapter Mercado Pago (mapper propio si exige números), persistencia de intención/registro |
| UC06 | `GetPaymentConfirmationUseCase`, `ChargeIntentRepository`, `ChargeIntentStatus`, `PaymentConfirmationStatus`, error `CHARGE_INTENT_NOT_FOUND` (retryable) | Servicio de solo lectura + controller |
| UC07 | `HandleReservationStatusUseCase` + command, `ReservationStatusLogRepository`, `FinancialOperationType`, `ReservationLockPort`, invocación de UC05/UC09/UC10 por `port.in`, cola `finance.reservation-status.v1`, `SettlementTrigger` | Listener, orquestación de las 4 patas (cobro/reembolso/dispersión/depósito), idempotencia RF-009A |
| UC08 | `HandleDisputeNotificationUseCase`, `OpenDepositTrackingUseCase`, `DisputeNotificationCommand`, `DepositDisposition` (+status), `DisputeEvent`, repositorios de disposición/evento, `SettlementTrigger.DISPUTE_COMPLETED`, cola `finance.guarantee-dispute.v1` | Listener de disputas, transiciones CAS del depósito, orquestación de reembolso/dispersión |
| UC09 | `RequestRefundUseCase`, `Send/RegisterRefundResultUseCase`, `ReconcileRefundIntentsUseCase`, `RefundIntent` y enums de alcance/disparador, repositorios, `RefundGatewayPort`, `ChargeIntentRepository` | Worker, asociación del webhook `refund`, adapter de pasarela, persistencia |
| UC10 | `RequestSettlementUseCase`, `RegisterSettlementResultUseCase` + commands/result, `SettlementIntent`, `SettlementRecord`, `CommissionRecord`, repositorios, `SettlementGatewayPort`, `SettlementScope/Status` | Worker, webhook `merchant_order`, cálculo de comisión desde `FrozenParameters`, persistencia atómica registro+comisión |
| UC11 | `Load/SaveFinancialParametersUseCase`, `FinancialParameters`, `FinancialParametersRepository`, `Percentage`, errores (`PARAMETERS_NOT_CONFIGURED`, `PARAMETERS_SAVE_FAILED`, `VALIDATION_ERROR`), tabla append-only V2 | Servicio de versionado + controller |
| UC12 | `QueryFinancialRecordsUseCase` + commands/results, `FinancialRecordQueryPort`, `FinancialRecord`, vista `v_financial_records`, error `PAGE_OUT_OF_RANGE` | Adapter de consulta paginada + controller |
| UC13 | `GetFinancialReportUseCase`, `ExportFinancialReportUseCase`, `FinancialReportQueryPort`, `ReportPeriod`/`Periodicity`/`ReportScope`, `PeriodComparison`, error `INVALID_PERIOD` | Agregación sobre las 4 tablas, controladores JSON/CSV (§3.2: `text/csv`) |

Regla de convivencia: los UC **no modifican** piezas del núcleo (puertos, enums, migraciones V1–V9, adaptadores técnicos). Si necesitan algo, registran el GAP en `core-gaps.md` y lo coordinan antes de tocar el núcleo. Los adaptadores propios de cada UC van en subpaquetes nuevos de `infrastructure/adapter/in|out`.

## 5. Herramientas de prueba compartidas

- `src/test/java/.../support`: `FixedClockPort` (fecha fija y movible), `InMemoryFailureRecorder`, `InMemoryOutbox` (pendientes/publicados y orden).
- `TestcontainersConfiguration`: PostgreSQL 16 + RabbitMQ 3.13 para pruebas de integración (patrón `@Testcontainers(disabledWithoutDocker = true)`).
- Pruebas del núcleo: dominio (Money/Percentage/ids/excepciones/modelos), `OutboxRelayServiceTest`, `SystemClockAdapterTest`, `ApiProblemDetailsFactoryTest`, `GlobalExceptionHandlerTest`, `JacksonConfigTest`, `CorrelationIdFilterTest` y `ArchitectureTest`.

## 6. Definition of Done del núcleo

- [x] `./mvnw -B verify` en verde (77/0/11; las omitidas requieren Docker).
- [x] 7 reglas de dependencia §3.4 verificadas por ArchUnit en CI.
- [x] Esquema único V1–V9 con vista de registros y `FlywaySchemaTest` (10 comprobaciones; pendiente de CI con Docker).
- [x] Puertos, DTOs, enums y errores congelados y documentados.
- [x] Adaptadores técnicos (reloj, fallos, outbox, broker, lock, scheduler, web).
- [x] Topología AMQP declarativa + reintentos/DLQ.
- [x] Herramientas de prueba para los UC (fakes).
- [x] `Dockerfile`, `docker-compose.yml` y CI.
- [x] Este informe y `core-gaps.md` actualizados.
- [ ] Verificación remota de CI (Testcontainers) al abrir el PR de la rama.

## 7. Huecos residuales

Ver `core-gaps.md`: derivaciones `[CONV]` de firmas, migraciones y adaptadores; choques documentales resueltos; stubs de modelo pendientes de cada UC dueño.
