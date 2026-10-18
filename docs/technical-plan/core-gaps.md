# Huecos del núcleo compartido (GAPs, choques y stubs)

Alcance: Etapas A–D de la rama `feature/core-compartido` (dominio + aplicación + migraciones Flyway).
Convenciones de marcado: `// GAP:` (desviación/derivación en código Java), `-- GAP:` (migraciones), `-- CHOQUE` (contradicción entre fuentes), `// STUB:` (clase de modelo reservada al UC dueño).

Totales: **59 GAPs en Java**, **7 GAPs en migraciones**, **2 choques en migraciones**, **13 stubs**.

## 1. GAPs

### 1.1 Migraciones (`src/main/resources/db/migration/`)

| Archivo | GAP |
|---|---|
| `V1__core_shared_tables.sql` | El núcleo no publica DDL literal de `operational_failure`/`outbox_message`/`shedlock`; se deriva de general-plan §4. |
| `V1` | `operational_failure`: sin índices documentados. |
| `V1` | Índice parcial de `outbox_message`: columnas no fijadas por §4; se usa `(created_at) WHERE published_at IS NULL`. |
| `V1` | `shedlock`: §4 solo lista `name`; se usan las 4 columnas canónicas (name, lock_until, locked_at, locked_by). |
| `V1` | Privilegios por rol no documentados en ningún plan. |
| `V2__financial_parameters.sql` | La primera versión (version_number = 1) la crea el PUT de UC11, no la migración. |
| `V4__charge_intent_and_charge_record.sql` | Privilegios del usuario de aplicación sobre las tablas `*_record` (denegar UPDATE/DELETE por GRANT/REVOKE) no documentados; el trigger los refuerza. |

### 1.2 Puertos de entrada (19) — `application/port/in/`

Firma no publicada por el plan; derivada del nombre del caso de uso (patrón homogéneo):
`CalculateReservationValueUseCase`, `EstimateBatchUseCase`, `EstimateSingleUseCase`, `ExportFinancialReportUseCase`, `GetFinancialReportUseCase`, `GetPaymentConfirmationUseCase`, `HandleDisputeNotificationUseCase`, `LoadFinancialParametersUseCase` (devuelve la versión vigente), `QueryFinancialRecordsUseCase`, `ReconcileChargeIntentsUseCase`, `ReconcileRefundIntentsUseCase`, `RegisterChargeResultUseCase` / `RegisterRefundResultUseCase` / `RegisterSettlementResultUseCase` (derivan el resultado de la pasarela como entrada), `RegisterReservationInformationUseCase` (nombre del método no publicado; retorno `void` documentado), `SaveFinancialParametersUseCase`, `SendChargeToGatewayUseCase`, `SendRefundToGatewayUseCase`.
Particulares:
- `OpenDepositTrackingUseCase`: tipo de retorno no publicado; se usa `DepositTrackingResult`.
- `HandleDisputeNotificationUseCase`: firma no publicada (se usa `DisputeNotificationCommand`).

### 1.3 Puertos de salida (17) — `application/port/out/`

Métodos no publicados; derivados del flujo de cada UC:
- Repositorios de intenciones/registros: `ChargeIntentRepository` (save; búsqueda por clave idempotente), `ChargeRecordRepository`, `ChargeRecordQueryPort` (lectura por intención), `RefundIntentRepository`, `RefundRecordRepository`, `SettlementIntentRepository`, `SettlementRecordRepository`, `CommissionRecordRepository`.
- Pasarelas: `ChargeGatewayPort` (send + fetchPayment, worker y webhook), `RefundGatewayPort` (send + consulta de detalle), `SettlementGatewayPort` (send + consulta de detalle).
- Técnicas: `DepositDispositionRepository` (lectura y transición de estado/CAS), `DisputeEventLogRepository` (inserción idempotente), `ReservationStatusLogRepository` (registro idempotente por reserva/estado/tipo/clave), `FinancialRecordQueryPort` (consulta paginada con el command), `FinancialReportQueryPort` (consulta con el command).

### 1.4 DTOs de contratos de pasarela/resultados (19) — `application/dto/`

Composición no publicada; se usan los campos mínimos citados en los contratos:
`ChargeGatewayRequest`/`ChargeGatewayResult`, `RefundGatewayRequest`/`RefundGatewayResult`, `GatewaySettlementRequest`/`GatewaySettlementResult`, `DisputeNotificationCommand`, `OpenDepositTrackingCommand`, `SettlementCommand`, `SettlementResultCommand`, `FinancialParametersCommand`/`FinancialParametersResult`, `QueryFinancialRecordsCommand`, `FinancialRecordResult`, `FinancialReportResult`, `FinancialReportExportResult`, `GetFinancialReportCommand`.
Casos vacíos por diseño (los completa el UC dueño): `DepositTrackingResult`, `SettlementRequestResult`.

### 1.5 Value objects (4 archivos, 5 marcas)

| Archivo | GAP |
|---|---|
| `SettlementTrigger.java` | Catálogo de valores sin documentar; enum vacío. |
| `PlannedOperation.java` | Tipo del desencadenante no documentado; se conserva su valor textual. |
| `ReportPeriod.java` | Composición exacta no documentada; se usa periodicidad + valor canónico. |
| `DepositDispositionStatus.java` | Documentación parcial; el catálogo completo solo aparece en la columna `disposition` de §4. |

## 2. Choques documentales (resueltos)

1. **`SettlementScope`**: contratos/SPEC mencionaban `DEPOSIT_DISPUTE`; general-plan §4 publica `STANDARD | CANCELLATION_COMPENSATION | DEPOSIT`. Aplicado §4.
2. **Historial Flyway**: cada plan declara su propio archivo de migración; D-31 exige historial único ordenado por fase. Aplicado V1–V8: núcleo, UC11, UC03, UC05, UC09, UC10, UC08 y UC07 (`reservation_status_log` como última, al no declarar UC07 fase propia).
3. **`operational_failure.reservation_id`**: el ER §4.1 lo dibuja con FK; UC11 T046 y UC07 §Storage exigen nullable y sin FK. Aplicado sin FK.
4. **`dispute_id`**: el ER §4.1 lo declara `string`; los planes lo tratan como identificador de disputa. Tipado `UUID` en `refund_intent`, `refund_record`, `deposit_disposition` y `dispute_event_log`.
5. **`charge_intent.status_detail`/`requested_amount`**: D-UC05-02 indica que la DDL exacta los documenta y T031 deja pendiente reflejarlos en §4. Incluidos en V4; el stub de dominio aún no los modela.
6. **VOs de UC08/UC13** (`DisputeStatus`, `ReportPeriod`, `SettlementTrigger`, `PlannedOperation`, `DepositDispositionStatus`): ubicados en `domain/valueobject` como conceptos compartidos; los servicios del UC dueño los consumirán.
7. **UC02**: firma publicada `provideBaseRates(ProvideBaseRateCommand) → List<BaseRateResult>` (no `Map<BoatId, BaseRate>`); implementada la publicada junto a su command/result.
8. **`refund_intent`/`settlement_intent`**: sin `created_at` (decisión del núcleo) y `settlement_intent` sin `external_reference`; UC10 asocia el resultado del webhook por idempotencia. Documentado como limitación.
9. **UC12**: `record_type` llega como texto desde la vista de lectura (fuera de alcance); no se crea un enum compartido.

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

- `./mvnw -q clean test -Dtest='Money*,Percentage*,BaseRate*,DomainIds*'`: **OK** (unitarias de dominio).
- **Sin Docker** en el entorno: las pruebas de integración (Testcontainers PostgreSQL/Flyway, Flyway migrate real) **no se ejecutaron**; quedan para CI/entorno con Docker.
- Vista `v_financial_records`, adaptadores, servicios y controllers: fuera de alcance del núcleo.
