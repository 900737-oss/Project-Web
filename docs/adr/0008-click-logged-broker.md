# ADR 0008: Send ClickLoggedEvent through RabbitMQ

- **Status:** Accepted
- **Date:** 2026-10-21
- **Deciders:** Team / feature card [0008](../features/0008-async_click_ingestion.md)

## Context

When someone opens a short URL, `links` publishes a `ClickLoggedEvent` (the type belongs to `clicks`). The seed project already saves the click and updates the stats in the background, using `@ApplicationModuleListener` and Modulith’s event registry, so the redirect does not wait for that work. The problem is that this only happens inside the same JVM. It does not reach the other replica, and that is not enough for Level 4 (weight 21) on feature [0008](../features/0008-async_click_ingestion.md).

The default Compose product starts RabbitMQ, and application replicas run with `docker,broker`. Other grown features already use Modulith `@Externalized` with queues that keep messages if a replica is down. If the same click is delivered twice, consumers must ignore the duplicate (`eventId`). If RabbitMQ is down, the user must still get a 307 redirect.

## Modules and event

| Role                    | Module      | Responsibility                                                                                                                       |
| ----------------------- | ----------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| Publisher               | `links`     | Publishes `ClickLoggedEvent` from `LinkService.redirect` after a successful lookup                                                   |
| Event owner / click log | `clicks`    | Owns `ClickLoggedEvent`, durable queue `urlshortener.clicks.click-logged` → `RecordClick`, in-process `ClickRecorder` when `!broker` |
| Stats consumer          | `analytics` | Durable queue `urlshortener.analytics.click-logged` → update `LinkStats`; in-process `InProcessClickLoggedListener` when `!broker`   |

**Event:** existing `clicks.ClickLoggedEvent` (`hash`, `occurredAt`, `eventId`) — not a new type.  
**Broker:** RabbitMQ topic exchange `urlshortener.click-logged`, routing key `clicks.ClickLoggedEvent` (`@Externalized`).  
**Libraries:** no new dependency, reuses Spring Modulith event publication + Spring AMQP already in the product stack.

## Decision

1. Keep the current `ClickLoggedEvent` in the `clicks` package (`hash`, `occurredAt`, `eventId`). We do not create a second event just for the broker. Level 4 is about sending this event over RabbitMQ, not renaming it.
2. Mark `ClickLoggedEvent` with `@Externalized`, same idea as `ShortUrlUpdatedEvent`. Use a topic exchange such as `urlshortener.click-logged` and a routing key like `clicks.ClickLoggedEvent`.
3. `links` still publishes the event from `LinkService.redirect` during the redirect transaction. With the `broker` profile, Modulith sends it to RabbitMQ after the transaction commits. The HTTP handler does not wait for RabbitMQ. If the broker is down, `GET /{hash}` still returns 307. Incomplete `ClickLoggedEvent` rows stay in the JDBC publication registry; `IncompleteClickLoggedRepublisher` (broker profile) resubmits them on a short fixed delay, and `republish-outstanding-events-on-restart` covers app restarts.
4. Avoid handling the same click twice by splitting listeners with Spring profiles:
   - `!broker`: keep the seed listeners inside the process (`ClickRecorder` in `clicks`, `InProcessClickLoggedListener` in `analytics`).
   - `broker`: turn those off; give each module its own durable queue (`clicks` writes the log, `analytics` updates `LinkStats`) and read messages with `@RabbitListener`.
5. Keep the idempotency we already have ([ADR 0002](0002-atomic-upserts.md)): the same `eventId` is stored once in the click log and counted once in the stats.
6. Do not add a new Modulith module. `clicks` owns the event type and the log, `analytics` owns the totals, and `links` keeps publishing. The hexagon rules and `ApplicationModules.verify()` must stay green.
7. `bootRun` without the `broker` profile keeps the in-process path. The Compose product (`docker compose up`) runs apps with `docker,broker`, so clicks cross replicas through RabbitMQ by default.

## Related tests

| Test / script | What it proves for this ADR |
| --- | --- |
| `ClickInProcessIngestionTests` | `!broker`: 307 + stats still work in-process |
| `ClickUnknownHashTests` | Unknown hash → 404; no click / stats bump |
| `ClickLoggedBrokerTests` | Durable broker delivery; 307 without waiting for stats; same `eventId` ingested once |
| `ClickBrokerDowntimeTests` | 307 while Rabbit is down; automatic outbox resubmit restores ingestion |
| `scripts/test-async-click-ingestion.ps1` | Cross-replica Compose: redirect on `app1`, `totalClicks=1` on `app2` |
| `ModularityTests` | Module boundaries and hexagonal rules stay green |

## Consequences

- With the broker profile, a click can be processed by the replica that did not handle the redirect and the message is not lost if that replica was briefly down.
- If we forget the profile split, both the local listener and RabbitMQ would count the same click twice. Using `@Profile("!broker")` and `@Profile("broker")` is required.
- Redirect speed does not depend on RabbitMQ being up. Duplicate deliveries are still filtered with `eventId`. Incomplete publishes are retried by `IncompleteClickLoggedRepublisher` when the broker returns.
- Feature card 0008 points to this ADR. Defence evidence is the broker tests plus the Compose demo (queues, cross-replica script, broker-down recovery).
- CI keeps the seed tests green and adds `broker`-profile tests (Rabbit via Testcontainers) for the durable path.

## Alternatives considered

| Option                                                          | Why rejected                                                                                                                   |
| --------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| Stay with only `@ApplicationModuleListener` in the same process | Does not reach the other app replica, not enough for weight 21 / Level 4 on feature 0008                                       |
| Create a brand-new event only for the broker                    | Not needed. The feature asks us to send `ClickLoggedEvent` and it already has `eventId`                                        |
| Use Kafka instead of RabbitMQ                                   | Compose and the other grown features already use RabbitMQ                                                                      |
| One shared queue for `clicks` and `analytics`                   | If one consumer is slow or stuck, the other waits too. Separate queues per module match what we did for `ShortUrlUpdatedEvent` |
| Wait for RabbitMQ before returning the redirect                 | Breaks the rule that the broker being down must still leave the 307 in place                                                   |
| Save the click inside `LinkService.redirect` before answering   | Makes the redirect slower and ties it to DB/stats work, the seed already moved that off the request                            |

## Evidence (defence)

- [x] Code on branch `feature/0008-async-click-ingestion` (`@Externalized` on `ClickLoggedEvent`, broker adapters in `clicks` and `analytics`, `IncompleteClickLoggedRepublisher`).
- [x] Broker tests: `ClickLoggedBrokerTests` (delivery + idempotent `eventId`), `ClickBrokerDowntimeTests` (307 while down; automatic resubmit restores `totalClicks` without a second redirect).
- [x] Compose: `docker compose up --build -d --wait` shows durable queues `urlshortener.clicks.click-logged` and `urlshortener.analytics.click-logged` (2 consumers each); `scripts/test-async-click-ingestion.ps1` PASS (hash `1006`); manual broker stop/start recovered hash `100C` to `totalClicks: 1` without replaying the redirect.
- [x] `./gradlew check` green; `ModularityTests` still passes (no illegal imports between modules).
