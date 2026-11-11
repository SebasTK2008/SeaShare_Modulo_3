### Test Case Specification: Procesar cobro · HU2 · E3 La autorización aprobada expira antes de la captura
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/005-procesar-cobro/spec.md](../../005-procesar-cobro/spec.md)  **Status** : Draft  **Execution** : Pendiente para esta revisión

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC05-HU2-E3
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Verifica que un resultado externo `expired` deja la intención en `EXPIRED` y disponible para UC06, sin crear un registro de cobro.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC05 | Specification Document | [spec.md](../../005-procesar-cobro/spec.md) |
| Contrato webhook de resultados | External contract | [pasarela-webhook-resultados.md](../../../technical-plan/contracts/external/pasarela-webhook-resultados.md) |
| Contrato pasarela — comando de cobro | External contract | [pasarela-comando-cobro.md](../../../technical-plan/contracts/external/pasarela-comando-cobro.md) |
| Contrato UC06 (estado devuelto) | REST contract | [UC06-confirmacion-pago.md](../../../technical-plan/contracts/rest/UC06-confirmacion-pago.md) |
| Catálogo de errores y política de colas | Conventions | [contracts/README.md](../../../technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* **Modo A**. Los resultados del caso se inyectan con los fixtures automáticos.

##### Dependencies & Assumptions
* JDK 25 y Maven Wrapper. Las clases preparan los datos necesarios para su ejecución.

##### Preconditions & Default Setup
* **Initial State**. Lo crea cada fixture de prueba; este caso se ejecuta de forma independiente.
* **Environment**. Suite local de Maven. Las pruebas de adaptador usan WireMock embebido.

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Registro de un pago expirado

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Consultar el pago expirado | Modo A · `MercadoPagoChargeAdapterTest#queriesTheExpiredPayment` | Fixture HTTP `status = expired` | El adaptador devuelve el resultado expirado y su referencia. | [ ] |  |
| 2 | Registrar la expiración | Modo A · `RegisterChargeResultServiceTest#registersTheExpirationWithoutAssumingFunds` | Intención en curso del fixture | Estado `EXPIRED` y detalle conservados, sin registro de cobro. | [ ] |  |
| 3 | Verificar mapeo y conciliación | Modo A · `PaymentConfirmationStatusTest`, `ChargePaymentWebhookHandlerTest`, `ReconcileChargeIntentsServiceTest` | Fixtures de estado, consulta y conciliación | UC06 mapea EXPIRED. Un 404 o fallo de consulta conserva el estado y registra la inconsistencia correspondiente. | [ ] |  |

##### Section 2: Errores y casos extremos de este escenario

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Ejecutar las comprobaciones del caso | `.\mvnw.cmd "-Dtest=MercadoPagoChargeAdapterTest#queriesTheExpiredPayment,RegisterChargeResultServiceTest#registersTheExpirationWithoutAssumingFunds,PaymentConfirmationStatusTest,ChargePaymentWebhookHandlerTest,ReconcileChargeIntentsServiceTest" test` | JDK 25, ejecutado desde la raíz del repo | Pruebas seleccionadas aprobadas; guardar el resultado de Surefire. | [ ] |  |

##### Edge Cases & Boundary Conditions

* **EC-001**. El MVP usa cobro directo con captura automática. Este TC adapta el escenario de la especificación al resultado externo `expired`. El titular de sandbox `EXPI` produce un rechazo por vencimiento de tarjeta.

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
