# Feature: Async Click Ingestion via Outbox → Broker

## Decision

**Owner module**: `clicks` | **Event-coupled**: Y

Externalizes `ClickLoggedEvent` (owned by `clicks`) to RabbitMQ after a successful
redirect commits. `links` still publishes the event from `LinkService.redirect`.
With the `broker` profile, Modulith sends it through the durable exchange
`urlshortener.click-logged`; `clicks` and `analytics` each consume from their own
queue. Without that profile, the seed in-process listeners stay active. Consumers
are idempotent on `eventId`. If RabbitMQ is down, `GET /{hash}` still returns 307;
`IncompleteClickLoggedRepublisher` resubmits incomplete click publications when the
broker returns. See [ADR 0008](../adr/0008-click-logged-broker.md).

**Traps**: the seed path alone is in-process and does not meet Level 4 / weight 21;
forgetting the `!broker` / `broker` split double-counts; broker-down must leave the
307 in place; at-least-once delivery needs idempotency (`eventId`).

**Technology hints**: Spring Modulith `@Externalized` + JDBC Event Publication
Registry; RabbitMQ; `@RabbitListener`; scheduled incomplete-publication resubmit.

---

- **Weight:** 21
- **Event-coupled:** Y

---



- **ADR:** [0008](../adr/0008-click-logged-broker.md)

### Acceptance criteria

- [x] `ClickLoggedEvent` is externalized to the broker after the redirect transaction commits. — `ClickLoggedBrokerTests`, Compose queues `urlshortener.clicks.click-logged` and `urlshortener.analytics.click-logged`.
- [x] `GET /{hash}` still returns 307 when the broker is down. — `ClickBrokerDowntimeTests`, Compose stop/start of `rabbit`.
- [x] A repeated `eventId` is ingested once by the click log and by the stats counter. — `ClickLoggedBrokerTests` redelivery case.
- [x] Weight 21 is not met by the in-process listener alone. The event must travel through the durable broker. — broker profile + Rabbit path above.

### Verification commands

- `./gradlew check`: lint, static analysis, tests, coverage and module boundaries.
- `./gradlew test --tests 'es.unizar.urlshortener.clicks.ClickInProcessIngestionTests' --tests 'es.unizar.urlshortener.clicks.ClickUnknownHashTests' --tests 'es.unizar.urlshortener.clicks.ClickLoggedBrokerTests' --tests 'es.unizar.urlshortener.clicks.ClickBrokerDowntimeTests'`: in-process path, unknown hash, broker delivery/idempotency and broker-down recovery (Docker required for broker tests).
- `docker compose up --build -d --wait`, then `pwsh -File scripts/test-async-click-ingestion.ps1`: cross-replica product acceptance (redirect on `app1`, `totalClicks=1` on `app2` / `app1` / `lb`).
- Manual broker-outage check: create a link with Rabbit up, `docker compose stop rabbit`, `GET /{hash}` → 307, `docker compose start rabbit`, wait for the republisher (~5–15 s), `GET /api/stats/{hash}` → `totalClicks` 1 without repeating the redirect.

### Verification evidence

1. `gradlew.bat check` passed on the feature branch (ktlint, detekt, tests, JaCoCo gate, `ModularityTests`).
2. Broker Testcontainers suite: `ClickLoggedBrokerTests` (307 then stats via Rabbit; same `eventId` ingested once) and `ClickBrokerDowntimeTests` (307 while Rabbit is down; automatic outbox resubmit restores ingestion without a second `GET /{hash}`).
3. In-process and contract checks: `ClickInProcessIngestionTests` (no broker profile) and `ClickUnknownHashTests` (404, no click rows).
4. `docker compose up --build -d --wait` started PostgreSQL, Redis, RabbitMQ, `app1`, `app2` and nginx. `rabbitmqctl list_queues` showed `urlshortener.clicks.click-logged` and `urlshortener.analytics.click-logged` with 2 consumers each.
5. `scripts/test-async-click-ingestion.ps1` passed: created hash `1006`, redirect on `app1`, `totalClicks=1` on `app2`/`app1`/`lb`, one click row and one distinct `event_id`.
6. Manual Compose recovery (hash `100C`): create with Rabbit up → stop Rabbit → `GET /100C` returned 307 with the target `Location` → start Rabbit → without repeating the redirect, `GET /api/stats/100C` reported `totalClicks: 1` after `IncompleteClickLoggedRepublisher` ran.

### Qualities

These are claimed grades supported by the verification recorded above.

| Quality | Assessed | How to test |
| --- | --- | --- |
| **Correctness** | 5 | `ClickInProcessIngestionTests`, `ClickUnknownHashTests`, `ClickLoggedBrokerTests`, `ClickBrokerDowntimeTests`: 307/404 contracts, broker delivery, idempotent `eventId`, recovery without manual replay. |
| **Scalability** | 3.5 | `scripts/test-async-click-ingestion.ps1` against two Compose replicas; durable queues with 2 consumers; broker-down Compose run with automatic outbox resubmit. |
| **Engineering** | 1.5 | ADR [0008](../adr/0008-click-logged-broker.md); `ModularityTests` green; `./gradlew check` green on this branch. |

**Indicative total:** 10 / 10

### AI disclosure

- **Tools / skills:** Cursor agent, Gemini
- **Used for:** feature planning and development roadmap; explaining Modulith/Rabbit click ingestion and related design choices; assistance with complex technical implementation to work more efficiently; improving adr and feature card wording.
- **Human-reviewed:** implementation, broker/outbox changes, tests, Compose verification and feature/adr documentation.

## Course guidance (instructor)

The acceptance criteria include the existing acceptance criteria that do not contradict the proposal.

### Acceptance criteria (instructor)

#### Success

- [x] Without the broker profile, `GET /{hash}` returns **307** and `GET /api/stats/{hash}` still reports `totalClicks`
- [x] An unknown hash returns **404** `ProblemDetails` and does not increase a count
- [x] `GET /{hash}` returns **307** with `Location` = target without waiting for `totalClicks` to increase
- [x] `GET /{hash}` returns **307** with `Location` = target while the broker is down
- [x] After the broker is back, `GET /api/stats/{hash}` shows `totalClicks` 1 for that redirect, with no manual replay

#### Both replicas

- [x] The same `eventId` leaves `totalClicks` at 1, including when the redirect and the stats read hit different replicas

#### Events

- [x] One redirect increases `totalClicks` by 1. Delivering the same `eventId` twice leaves the count unchanged
