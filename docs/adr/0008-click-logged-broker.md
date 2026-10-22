# ADR 0008: Send ClickLoggedEvent through RabbitMQ

- **Status:** Proposed
- **Date:** 2026-10-21
- **Deciders:** Team / feature card [0008](../features/0008-async_click_ingestion.md)

## Context

When someone opens a short URL, `links` publishes a `ClickLoggedEvent` (the type belongs to `clicks`). The seed project already saves the click and updates the stats in the background, using `@ApplicationModuleListener` and Modulith’s event registry, so the redirect does not wait for that work. The problem is that this only happens inside the same JVM. It does not reach the other replica, and that is not enough for Level 4 (weight 21) on feature [0008](../features/0008-async_click_ingestion.md).

We already have RabbitMQ behind `docker compose --profile broker`, and other features use Modulith `@Externalized` with queues that keep messages if a replica is down. If the same click is delivered twice, consumers must ignore the duplicate (`eventId`). If RabbitMQ is down, the user must still get a 307 redirect.

## Decision

1. Keep the current `ClickLoggedEvent` in the `clicks` package (`hash`, `occurredAt`, `eventId`). We do not create a second event just for the broker. Level 4 is about sending this event over RabbitMQ, not renaming it.
2. Mark `ClickLoggedEvent` with `@Externalized`, same idea as `ShortUrlUpdatedEvent`. Use a topic exchange such as `urlshortener.click-logged` and a routing key like `clicks.ClickLoggedEvent`.
3. `links` still publishes the event from `LinkService.redirect` during the redirect transaction. With the `broker` profile, Modulith sends it to RabbitMQ after the transaction commits. The HTTP handler does not wait for RabbitMQ. If the broker is down, `GET /{hash}` still returns 307, and Modulith can retry the publish later.
4. Avoid handling the same click twice by splitting listeners with Spring profiles:
   - `!broker`**:** keep the seed listeners inside the process (`ClickRecorder` in `clicks`, `LinkStatsListener.onClick` in `analytics`).
   - `broker`**:** turn those off; give each module its own durable queue (`clicks` writes the log, `analytics` updates `LinkStats`) and read messages with `@RabbitListener`.
5. Keep the idempotency we already have ([ADR 0002](0002-atomic-upserts.md)): the same `eventId` is stored once in the click log and counted once in the stats.
6. Do not add a new Modulith module. `clicks` owns the event type and the log, `analytics` owns the totals, and `links` keeps publishing. The hexagon rules and `ApplicationModules.verify()` must stay green.
7. `bootRun` and plain `docker compose up` keep the in-process path. Sending clicks across replicas needs `docker compose --profile broker up`.

## Consequences

- With the broker profile, a click can be processed by the replica that did not handle the redirect and the message is not lost if that replica was briefly down.
- If we forget the profile split, both the local listener and RabbitMQ would count the same click twice. Using `@Profile("!broker")` and `@Profile("broker")` is required.
- Redirect speed does not depend on RabbitMQ being up. Duplicate deliveries are still filtered with `eventId`.
- Feature card 0008 should point to this ADR when we accept it. For the defence we need broker tests and a Compose demo showing the queues for `clicks` and `analytics`.
- CI must keep the normal seed tests green, and we add tests with the `broker` profile (Rabbit via Testcontainers) for the new path.

## Alternatives considered

| Option                                                          | Why rejected                                                                                                                   |
| --------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| Stay with only `@ApplicationModuleListener` in the same process | Does not reach the other app replica, not enough for weight 21 / Level 4 on feature 0008                                       |
| Create a brand-new event only for the broker                    | Not needed. The feature asks us to send `ClickLoggedEvent` and it already has `eventId`                                        |
| Use Kafka instead of RabbitMQ                                   | Compose and the other grown features already use the Rabbit `--profile broker` setup                                           |
| One shared queue for `clicks` and `analytics`                   | If one consumer is slow or stuck, the other waits too. Separate queues per module match what we did for `ShortUrlUpdatedEvent` |
| Wait for RabbitMQ before returning the redirect                 | Breaks the rule that the broker being down must still leave the 307 in place                                                   |
| Save the click inside `LinkService.redirect` before answering   | Makes the redirect slower and ties it to DB/stats work, the seed already moved that off the request                            |

## Evidence (defence)

- [ ] Code / PR on branch `feature/0008-async-click-ingestion` (`@Externalized` on `ClickLoggedEvent`, broker adapters in `clicks` and `analytics`).
- [ ] Broker tests (redirect updates log + stats after Rabbit delivery; same `eventId` applied once, redirect still 307 if the broker is down).
- [ ] Compose: `docker compose --profile broker up` shows the durable queues for the click log and click stats, manual redirect demo or k6 as needed.
- [ ] `./gradlew check` green; `ModularityTests` still passes (no illegal imports between modules).
