### Test Case Specification: UC09 Reembolsos · HU3 · E2 La Pasarela de Pago reporta el rechazo o fallo del reembolso
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/009-reembolsar-dinero-arrendatario/spec.md](../../009-reembolsar-dinero-arrendatario/spec.md)  **Status** : Draft  **Execution** : Pendiente para esta revisión

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC09-HU3-E2
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, cuando la Pasarela de Pago reporta el rechazo o fallo de un reembolso previamente solicitado, el sistema registra el estado externo y su detalle en la `IntenciónDeReembolso` **sin** marcar la operación como completada y **sin** crear un `RegistroDeReembolso`, dejando el resultado disponible internamente.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC09 | Specification Document | [spec.md](../../009-reembolsar-dinero-arrendatario/spec.md) |
| Contrato webhook de resultados | External contract | [pasarela-webhook-resultados.md](../../../technical-plan/contracts/external/pasarela-webhook-resultados.md) |
| Contrato pasarela — comando de reembolso | External contract | [pasarela-comando-reembolso.md](../../../technical-plan/contracts/external/pasarela-comando-reembolso.md) |
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

##### Section 1: Rechazo o fallo del reembolso

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Rechazo de negocio al enviar | Modo A · `SendRefundToGatewayServiceTest#businessRejectionStopsTheRetries` y `MercadoPagoRefundAdapterTest#badRequestIsANonRetryableBusinessRejection` | HTTP 400 simulado | Intención REJECTED, sin registro ni reintento técnico. | [ ] |  |
| 2 | Resultado negativo del reembolso | Modo A · `RegisterRefundResultServiceTest#nonCompletedResultOnlyUpdatesTheIntent` y `#anExpiredResultClosesTheIntentWithoutRecord` | Fixtures `rejected` y `expired` | Se actualiza el estado de la intención y se crean cero registros de reembolso. | [ ] |  |
| 3 | Error técnico y consulta sin coincidencia | Modo A · `SendRefundToGatewayServiceTest`, `RefundWebhookHandlerTest`, `MercadoPagoRefundAdapterTest` | Fixtures HTTP 5xx, 404 y reembolso ajeno | El fallo técnico no se toma como devolución confirmada; un reembolso ajeno no genera registro local. | [ ] |  |
| 4 | Validación del webhook | Modo A · `GatewayWebhookControllerTest` | Firma inválida, cuerpo incompleto y fallo de persistencia | HTTP 401, 400 y 500 respectivamente, conforme al fixture. | [ ] |  |

##### Section 2: Errores y casos extremos de este escenario

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Ejecutar las comprobaciones del caso | `.\mvnw.cmd "-Dtest=MercadoPagoRefundAdapterTest,SendRefundToGatewayServiceTest,RegisterRefundResultServiceTest,RefundWebhookHandlerTest,GatewayWebhookControllerTest" test` | Fixtures incluidos en las clases | Pruebas seleccionadas aprobadas; guardar el resultado de Surefire. | [ ] |  |

##### Edge Cases & Boundary Conditions

* **EC-001**. Los fixtures preparan sus propias intenciones. El comando cubre `rejected` y `expired`; el resultado `cancelled` contemplado por el contrato requiere una comprobación adicional si se exige esa variante.

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
