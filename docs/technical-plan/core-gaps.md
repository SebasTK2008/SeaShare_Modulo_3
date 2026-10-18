# Huecos del núcleo compartido (GAPs, choques y stubs)

Alcance: Etapas A–F de la rama `feature/core-compartido` (dominio + aplicación + infraestructura + migraciones Flyway + pruebas).
Convenciones de marcado: `// GAP:` (desviación/derivación en código Java), `-- GAP:` (migraciones), `-- CHOQUE` (contradicción entre fuentes), `// STUB:` (clase de modelo reservada al UC dueño), `[CONV]` (decisión del núcleo sin fuente documental).

Totales: **10 GAPs en migraciones** (2 resueltos como choques en §2), **13 stubs** de modelo. Las derivaciones `[CONV]` de adaptadores, errores y build se detallan en §1.6–§1.7.

## 1. GAPs

### 1.1 Migraciones (`src/main/resources/db/migration/`)

| Archivo | GAP |
|---|---|
| `V1__core_shared_tables.sql` | El núcleo no publica DDL literal de `operational_failure`/`outbox_message`/`shedlock`; se deriva de general-plan §4. |
| `V1` | `operational_failure`: sin índices documentados. |
| `V1` | Índice parcial de `outbox_message`: columnas no fijadas por §4; se usa `(created_at) WHERE published_at IS NULL`. |
| `V1` | `shedlock`: §4 solo lista `name`; se usan las 4 columnas canónicas (name, lock_until, locked_at, locked_by). |
| `V1` | Privilegios por rol no documentados en ningún plan. |
| `V1–V8` | **Decisión de la rama (Bloque B)**: editadas *in-place* (columnas de trazabilidad, `UNIQUE` por FK, índices) en lugar de una migración de `ALTER`s. Invalida los checksums de Flyway: **recrear la BD local al rebasar el commit `527d398`**. Congeladas desde entonces; los cambios futuros van en `V10+`. |
| `V2__financial_parameters.sql` | La primera versión (version_number = 1) la crea el PUT de UC11, no la migración. |
| `V4__charge_intent_and_charge_record.sql` | Privilegios del usuario de aplicación sobre las tablas `*_record` (denegar UPDATE/DELETE por GRANT/REVOKE) no documentados; el trigger los refuerza. |
| `V5`/`V6` | Columnas de trazabilidad (`refund_intent.created_at`; `settlement_intent.status_detail/external_reference/dispute_id/created_at`) exigidas por los flujos de UC09/UC10 (plan UC10 T012a) pero no publicadas en su DDL; derivadas. |
| `V9__v_financial_records_view.sql` | Columnas de la vista derivadas de UC12 §contrato (`record_type` como texto, `external_reference` nulo para registros que no la tienen). |

### 1.2 Puertos de entrada (26) — `application/port/in/`

Firma no publicada por el plan en la mayoría; derivada del nombre del caso de uso (patrón homogéneo):
`CalculateReservationValueUseCase`, `EstimateBatchUseCase`, `EstimateSingleUseCase`, `ExportFinancialReportUseCase`, `GetFinancialReportUseCase`, `GetPaymentConfirmationUseCase`, `HandleDisputeNotificationUseCase`, `HandleReservationStatusUseCase`, `LoadFinancialParametersUseCase` (devuelve la versión vigente), `OpenDepositTrackingUseCase`, `ProcessChargeUseCase`, `ProvideBaseRateUseCase`, `QueryFinancialRecordsUseCase`, `ReconcileChargeIntentsUseCase`, `ReconcileRefundIntentsUseCase`, `RegisterChargeResultUseCase`/`RegisterRefundResultUseCase`/`RegisterSettlementResultUseCase` (derivan el resultado de la pasarela como entrada), `RegisterReservationInformationUseCase` (nombre del método no publicado; retorno `void` documentado), `RelayOutboxUseCase` (núcleo), `RequestRefundUseCase`, `RequestSettlementUseCase`, `SaveFinancialParametersUseCase`, `SendChargeToGatewayUseCase`, `SendRefundToGatewayUseCase` y `GatewayWebhookHandler` (núcleo: dispatch por `type`).
Particulares:
- `OpenDepositTrackingUseCase`: tipo de retorno no publicado; se usa `DepositTrackingResult`.
- `HandleDisputeNotificationUseCase`: firma no publicada (se usa `DisputeNotificationCommand`).
- `SendChargeToGatewayUseCase`/`SendRefundToGatewayUseCase`: etapas internas de UC05/UC09 [CONV], para que el worker y el job de conciliación compartan flujo.

### 1.3 Puertos de salida (25) — `application/port/out/`

Métodos no publicados; derivados del flujo de cada UC:
- Repositorios de intenciones/registros: `ChargeIntentRepository` (save; `findByExternalReference`; `findByStatusIn` para conciliación), `ChargeRecordRepository`, `ChargeRecordQueryPort`, `RefundIntentRepository` (+búsquedas), `RefundRecordRepository`, `SettlementIntentRepository` (+búsquedas), `SettlementRecordRepository`, `CommissionRecordRepository`.
- Pasarelas: `ChargeGatewayPort` (send + fetchPayment), `RefundGatewayPort` (send + consulta de detalle), `SettlementGatewayPort` (send + consulta de detalle).
- `FinancialParametersRepository` (+`findById`).
- Técnicos: `ClockPort`, `FailureRecorderPort`, `OutboxPort`, `OutboxMessageRepository`, `MessageBrokerPort`, `ReservationLockPort`, `DepositDispositionRepository` (transición CAS), `DisputeEventLogRepository` (inserción idempotente), `ReservationStatusLogRepository` (registro idempotente), `FinancialRecordQueryPort`, `FinancialReportQueryPort`, `FleetRatePort`, `ReservationInformationRepository`.

### 1.4 DTOs (39) — `application/dto/`

Composición no publicada; se usan los campos mínimos citados en los contratos:
`ChargeGatewayRequest`/`ChargeGatewayResult`, `RefundGatewayRequest`/`RefundGatewayResult`, `GatewaySettlementRequest`/`GatewaySettlementResult`, `DisputeNotificationCommand`, `OpenDepositTrackingCommand`, `SettlementCommand`, `SettlementResultCommand`, `FinancialParametersCommand`/`FinancialParametersResult`, `QueryFinancialRecordsCommand`, `FinancialRecordResult`, `FinancialReportResult`, `FinancialReportExportResult`, `GetFinancialReportCommand`, `GatewayWebhookNotification`, `PendingOutboxMessage`.
Casos vacíos por diseño (los completa el UC dueño): `DepositTrackingResult`, `SettlementRequestResult`.

### 1.5 Value objects (29 archivos)

| Archivo | GAP |
|---|---|
| `SettlementTrigger.java` | Catálogo sin documentar; **congelado por el núcleo**: `COMPLETED`, `CANCELLED_MODERATE`, `CANCELLED_STRICT`, `DISPUTE_COMPLETED`. |
| `PlannedOperation.java` | Tipo del desencadenante no documentado; se conserva su valor textual. |
| `ReportPeriod.java` | Composición exacta no documentada; se usa periodicidad + valor canónico. |
| `DepositDispositionStatus.java` | Documentación parcial; el catálogo completo solo aparece en la columna `disposition` de §4. |
| `HighSeasonWindow`, `BaseRate`, `FrozenParameters`, `ReservationValue`, `PaymentMethodReference` | Composición derivada de las reglas de UC02/UC04; los UC dueños pueden enriquecer la semántica, no el contrato publicado. |

### 1.6 Adaptadores, errores y configuración (Bloques E–F) — `[CONV]`

| Componente | Decisión |
|---|---|
| `ErrorCode`, `ContractException`, `ApiFieldError` | Viven en `application/exception` (no en infrastructure): los servicios de UC deben lanzarlos sin romper la regla §3.4-2. El catálogo replica `contracts/README §3.4` completo (18 códigos; `UNAUTHENTICATED` solo para el webhook). |
| `GlobalExceptionHandler` | `@Order(LOWEST_PRECEDENCE)` para que los UC puedan especializarlo; las respuestas del framework (`ErrorResponse`, p. ej. 404 de ruta desconocida) pasan intactas; `Exception` inesperada → 500 `INTERNAL_ERROR` genérico + log con `correlationId`. El detalle aportado por el UC se usa tal cual en 4xx/5xx (regla §4.3-9 es responsabilidad del autor). |
| `ApiProblemDetailsFactory` | `type=about:blank` explícito; `errors[]` solo si hay violaciones. |
| `JacksonConfig` | `BigDecimal` → string decimal global (API y AMQP). Los clientes HTTP de pasarela con mapper propio deben replicar la configuración si Mercado Pago exige números. |
| `CorrelationIdFilter` | `HIGHEST_PRECEDENCE`; genera UUID si falta; `logging.pattern.level` con `%X{correlationId}`. |
| `RabbitMessagePublisher` | *Confirm* con timeout `seashare.outbox.publish-confirm-timeout` (def. 5s) y detección de retorno; exige `publisher-confirm-type=correlated`, `publisher-returns=true`, `template.mandatory=true` (ya en `application.properties`). |
| `RabbitTopologyConfig` | Exchange de comandos `seashare.finance.commands` (direct) y claves `charge.command`/`refund.command`/`settlement.command` son `[CONV]`: §3.5 lista las colas pero no el exchange. Colas de trabajo quorum con DLX a `""` y DLQ `<cola>.dlq` durables. |
| `OutboxPersistenceAdapter` | `publish` en `MANDATORY` (transacción del llamador); `markPublished` idempotente. |
| `OutboxRelayService` / `OutboxRelayJob` | Semántica at-least-once (fallo del marcado → republica); job cada 5s, `lockAtMostFor=PT4M`, `lockAtLeastFor=PT1S`, ShedLock con hora de BD; el bean lo construye `OutboxConfig` para que `application` no importe Spring. |
| `PostgresReservationLockAdapter` | `pg_advisory_xact_lock(int, int)` con msb/lsb truncados del UUID (colisión = bloqueo más grueso); requiere transacción activa del llamador. |
| `SystemClockAdapter` / `TimeConfig` | Fechas de negocio en `seashare.timezone`; instantes con `Clock` UTC. |
| Ignorados en este núcleo | Jobs de conciliación por UC y sus endpoints admin no se implementan aquí (los define cada UC). |

### 1.7 Build (Bloque A) — `[CONV]`

| Tema | Decisión |
|---|---|
| AOP | `spring-boot-starter-aop` no está en el BOM de Boot 4.1.1; se usa `spring-boot-starter-aspectj`. |
| Web | `spring-boot-starter-web` deprecado; se usa `spring-boot-starter-webmvc` (+ `webmvc-test` en pruebas). |
| Jackson | Boot 4.1.1 usa Jackson 3 (`tools.jackson`); conversor AMQP `JacksonJsonMessageConverter` (`Jackson2JsonMessageConverter` deprecado). |
| Resilience4j | `resilience4j-spring-boot4` 2.4.0 elegida por compatibilidad con Boot 4; los UC de pasarela la configuran. |
| ShedLock | 7.10.1 (`JdbcTemplateLockProvider` + `usingDbTime()` sobre la tabla V1). |
| Pruebas | Testcontainers (postgres:16 + `rabbitmq:3.13-management`), WireMock 3.13.2, ArchUnit 1.5.1. |

## 2. Choques documentales (resueltos)

1. **`SettlementScope`**: contratos/SPEC mencionaban `DEPOSIT_DISPUTE`; general-plan §4 publica `STANDARD | CANCELLATION_COMPENSATION | DEPOSIT`. Aplicado §4.
2. **Historial Flyway**: cada plan declara su propio archivo de migración; D-31 exige historial único ordenado por fase. Aplicado V1–V9: núcleo, UC11, UC03, UC05, UC09, UC10, UC08, UC07 y la vista de UC12. Fila D-31 de `general-plan.md` **corregida para incluir UC07** (Etapa de cierre).
3. **`operational_failure.reservation_id`**: el ER §4.1 lo dibuja con FK; UC11 T046 y UC07 §Storage exigen nullable y sin FK. Aplicado sin FK.
4. **`dispute_id`**: el ER §4.1 lo declara `string`; los planes lo tratan como identificador de disputa. Tipado `UUID` en `refund_intent`, `refund_record`, `deposit_disposition` y `dispute_event_log`.
5. **`charge_intent.status_detail`/`requested_amount`**: D-UC05-02 indica que la DDL exacta los documenta y T031 deja pendiente reflejarlos en §4. Incluidos en V4; el stub de dominio aún no los modela.
6. **VOs de UC08/UC13** (`DisputeStatus`, `ReportPeriod`, `SettlementTrigger`, `PlannedOperation`, `DepositDispositionStatus`): ubicados en `domain/valueobject` como conceptos compartidos; los servicios del UC dueño los consumirán.
7. **UC02**: firma publicada `provideBaseRates(ProvideBaseRateCommand) → List<BaseRateResult>` (no `Map<BoatId, BaseRate>`); implementada la publicada junto a su command/result.
8. **`refund_intent`/`settlement_intent` sin trazabilidad**: resuelto en el Bloque B — `created_at` en ambas, `status_detail`/`external_reference`/`dispute_id` en `settlement_intent` (UC10 asocia el webhook por idempotencia y por `external_reference`).
9. **`spring-boot-starter-aop`/`-web` y Jackson 2 en Boot 4.1.1**: sustituidos por `-aspectj`, `-webmvc` y `JacksonJsonMessageConverter` (ver §1.7).
10. **Tabla de registros únicos**: el ER dibuja `|--o|` (una intención → cero o un registro) sin declarar `UNIQUE`; el Bloque B lo materializa con `UNIQUE (charge_intent_id)` y equivalentes, reforzado por triggers de inmutabilidad.

## 3. Stubs (13)

| Clase (`domain/model/`) | UC dueño |
|---|---|
| `ReservationInformation` | UC03 |
| `ChargeIntent`, `ChargeRecord` | UC05 |
| `DepositDisposition`, `DisputeEvent` | UC08 |
| `RefundIntent`, `RefundRecord` | UC09 |
| `SettlementIntent`, `SettlementRecord`, `CommissionRecord` | UC10 |
| `FinancialParameters` | UC11 |
| `FinancialRecord` | UC12 |
| `PeriodComparison` | UC13 |

## 4. Verificación

- `./mvnw -B verify`: **BUILD SUCCESS** — 77 pruebas, 0 fallos, 11 omitidas (`FlywaySchemaTest` 10 + `SeashareM3ApplicationTests` 1).
- **Sin Docker** en el entorno de la rama: la migración real contra PostgreSQL/RabbitMQ no se ejecutó localmente; las pruebas Testcontainers quedan para CI (`disabledWithoutDocker`).
- `ArchitectureTest`: 7 reglas §3.4 en verde sobre el código de producción.
- Detalle del cierre en `core-completion.md`.
