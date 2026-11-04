# SeaShare — Módulo 3 (Finanzas)

Módulo de gestión financiera del sistema SeaShare: estimaciones de alquiler, tarifas base, cobros, reembolsos, liquidaciones a propietarios, depósito de garantía e informe financiero. Un solo servicio desplegable que expone sus casos de uso por REST y consume eventos/comandos por RabbitMQ.

## Documentación (orden de lectura)

| # | Documento | Contenido |
|---|---|---|
| 1 | [`docs/technical-plan/general-plan.md`](docs/technical-plan/general-plan.md) | Plan técnico maestro: arquitectura, modelo de datos, mensajería, decisiones D‑01…D‑36, fases |
| 2 | [`docs/technical-plan/contracts/README.md`](docs/technical-plan/contracts/README.md) | Catálogo de errores, forma de escalares, idempotencia y convenciones AMQP |
| 3 | [`docs/features/`](docs/features) (`spec.md` y `plan.md` por caso de uso) | Comportamiento y plan de cada caso de uso |
| 4 | [`docs/technical-plan/contracts/`](docs/technical-plan/contracts) | Formato de cable (`rest/`, `events/`, `external/`) |
| 5 | [`docs/templates/`](docs/templates) | Plantillas de SPEC y plan |

> `docs/context/` es contexto del proyecto (no canónico) y `docs/diagrams/` está desactualizado: no se usan como referencia.

## Requisitos

- JDK 25 y el wrapper de Maven incluido (`./mvnw`; en Windows, `mvnw.cmd`).
- Docker y Docker Compose (stack local y pruebas de integración con Testcontainers).

## Puesta en marcha

**Infraestructura local** (PostgreSQL 16 y RabbitMQ 3.13 Management):

```bash
docker compose up -d
# postgres: localhost:5433 · rabbitmq: localhost:5672 · consola: http://localhost:15672
```

> El puerto del host es **5433** (`POSTGRES_HOST_PORT` en `.env`); dentro de Docker PostgreSQL sigue
> escuchando en 5432.

**Aplicación**

- Desde el IDE o consola con el perfil `dev` (por defecto), apuntando a los puertos anteriores.
- Contenerizada (stack completo):

```bash
docker compose --profile app up --build -d
curl -fsS http://localhost:8080/actuator/health
```

**Pruebas**

```bash
./mvnw clean verify
```

Los tests de integración usan Testcontainers: sin Docker se omiten automáticamente (los unitarios y ArchUnit siempre corren).

**Pagos — dos modos obligatorios**: la suite automática usa WireMock y el simulador (**modo A**, gate de CI). La aprobación de `release/0.1.0` exige además probar cobro, webhook `payment` y reembolso contra **Mercado Pago sandbox** con credenciales `TEST-*` (**modo B**); el checklist y la evidencia están en [`docs/qa/pagos-api-real.md`](docs/qa/pagos-api-real.md). El modo B no corre en `mvn verify` y no se sustituye por el simulador. Procedimiento paso a paso: [`docs/qa/runbook-sandbox-mercadopago.md`](docs/qa/runbook-sandbox-mercadopago.md). Vista de la integración: [`docs/technical-plan/mercadopago-integration.md`](docs/technical-plan/mercadopago-integration.md).

## Configuración y entornos

- **Perfiles de Spring**: `dev` (por defecto) y `prod`.
  - `application-dev.properties`: defaults locales alineados con `docker-compose.yml`.
  - `application-prod.properties`: **sin defaults** para credenciales, URLs de entorno ni secretos; se activa con `SPRING_PROFILES_ACTIVE=prod` y falla ruidosamente si falta una variable.
- **Secretos**: copia `.env.example` a `.env` (ignorado por git) para el stack local. En producción los valores se aportan por variables de entorno o Docker secrets; nunca se versionan valores reales.
- **Credenciales de Mercado Pago (sandbox)**: crea la aplicación en [Tus integraciones](https://www.mercadopago.com/developers/panel/app) con producto **Checkout API** y API **Payments**; copia el *access token* de prueba (prefijo `TEST-`) en `GATEWAY_ACCESS_TOKEN`. En **Webhooks → Configurar notificaciones** registra la URL HTTPS pública de `POST /api/v1/webhook/gateway` para el topic `payment` y copia la **firma secreta** en `GATEWAY_WEBHOOK_SECRET`. Sin esa firma el webhook responde `401 UNAUTHENTICATED`. No se versiona ninguno de los dos valores.
- **Base local**: si ya habías aplicado una versión anterior de `V1__initial_schema.sql`, recrea el volumen antes de arrancar (`docker compose down -v`); Flyway valida el checksum de la migración inicial.

| Variable | Uso | Default dev |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Perfil activo | `dev` |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | PostgreSQL | `jdbc:postgresql://localhost:5432/seashare` / `seashare` (si usas el PostgreSQL del compose, define `DB_URL=jdbc:postgresql://localhost:5433/seashare`) |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` / `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | RabbitMQ | `localhost` / `5672` / `seashare` |
| `SEASHARE_TIMEZONE` | Zona horaria de negocio | `America/Bogota` |
| `FLEET_BASE_URL` | Sistema de Gestión de Flota | `http://localhost:8081` |
| `GATEWAY_BASE_URL` | Pasarela de pago | `https://api.mercadopago.com` |
| `GATEWAY_ACCESS_TOKEN` / `GATEWAY_WEBHOOK_SECRET` | Pasarela (secretos) | vacío en dev; obligatorio en prod |
| `GATEWAY_SETTLEMENT_MODE` | Modo del libro interno de liquidaciones (UC10); solo `simulated` está implementado | `simulated`; obligatorio en prod |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | Credenciales del contenedor PostgreSQL | `seashare` |
| `APP_PORT` | Puerto publicado de la aplicación | `8080` |

Actuator: en `dev` expone `health,info,metrics,prometheus`; en `prod` solo `health,info`. Como la seguridad está fuera de alcance (D‑21), en producción conviene restringir el acceso por red (firewall/reverse proxy).

## Arquitectura (resumen)

Hexagonal (puertos y adaptadores) + dominio rico en un único módulo Maven:

- `domain`: modelos, value objects y excepciones, sin frameworks.
- `application`: puertos `in`/`out`, DTOs y servicios de caso de uso.
- `infrastructure`: adaptadores web/AMQP/persistencia, mensajería (outbox + publisher confirms), scheduler con ShedLock y configuración.

Las reglas de dependencia se verifican con ArchUnit en cada `verify`. REST para request/response y RabbitMQ para flujos unidireccionales, con entrega al‑menos‑una‑vez e idempotencia por claves de negocio (ver `general-plan.md` §3 y §9).

## Ramas y CI

- Git Flow: se trabaja en `feature/*`, `bugfix/*` o `hotfix/*`; los PR van a `develop`, y solo `release/*` o `hotfix/*` pasan a `main` (el workflow *Git Flow Enforcer* lo valida).
- CI: job `Maven verify` (compila y ejecuta los tests, con Testcontainers) y job `Docker build` (valida la construcción de la imagen, sin publicarla).

## Estado actual

Núcleo compartido completo: dominio, puertos, DTOs, manejo de errores RFC 9457, outbox, topología AMQP, migración inicial `V1` y reglas ArchUnit. Los 13 casos de uso se implementan por bloques siguiendo el orden de dependencias del plan general (§10).
