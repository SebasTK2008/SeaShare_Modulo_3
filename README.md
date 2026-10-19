# SeaShare — Módulo 3 (Finanzas)

Módulo de gestión financiera del sistema SeaShare: estimaciones de alquiler, tarifas base, cobros, reembolsos, liquidaciones a propietarios, depósito de garantía e informe financiero. Un solo servicio desplegable que expone sus casos de uso por REST y consume eventos/comandos por RabbitMQ.

## Documentación (orden de lectura)

| # | Documento | Contenido |
|---|---|---|
| 1 | [`docs/technical-plan/general-plan.md`](docs/technical-plan/general-plan.md) | Plan técnico maestro: arquitectura, modelo de datos, mensajería, decisiones D‑01…D‑34, fases |
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
# postgres: localhost:5432 · rabbitmq: localhost:5672 · consola: http://localhost:15672
```

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

## Configuración y entornos

- **Perfiles de Spring**: `dev` (por defecto) y `prod`.
  - `application-dev.properties`: defaults locales alineados con `docker-compose.yml`.
  - `application-prod.properties`: **sin defaults** para credenciales, URLs de entorno ni secretos; se activa con `SPRING_PROFILES_ACTIVE=prod` y falla ruidosamente si falta una variable.
- **Secretos**: copia `.env.example` a `.env` (ignorado por git) para el stack local. En producción los valores se aportan por variables de entorno o Docker secrets; nunca se versionan valores reales.

| Variable | Uso | Default dev |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Perfil activo | `dev` |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | PostgreSQL | `jdbc:postgresql://localhost:5432/seashare` / `seashare` |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` / `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | RabbitMQ | `localhost` / `5672` / `seashare` |
| `SEASHARE_TIMEZONE` | Zona horaria de negocio | `America/Bogota` |
| `FLEET_BASE_URL` | Sistema de Gestión de Flota | `http://localhost:8081` |
| `GATEWAY_BASE_URL` | Pasarela de pago | `https://api.mercadopago.com` |
| `GATEWAY_ACCESS_TOKEN` / `GATEWAY_WEBHOOK_SECRET` | Pasarela (secretos) | vacío en dev; obligatorio en prod |
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
