# Feature: Async Click Ingestion via Outbox → Broker

## Decision

**Owner module**: clicks | **Event-coupled**: Y

Forwards `ClickLoggedEvent` to an external broker (--profile broker). The type lives in clicks.
links already publishes it from LinkService on redirect. @ApplicationModuleListener and the
JDBC Event Publication Registry (bootstrapped in the seed) already run the click write off the
redirect thread. This feature externalises that in-process publication. Consumers are idempotent.

**Traps**:  the seed publication is in-process — the broker path is a structural change (the Compose
broker profile is a RabbitMQ stub, with AMQP still unwired); broker-down must leave the 307 in
place; at-least-once delivery needs idempotency on the consumer (eventId already exists for the
in-process listeners).

**Technology hints**: Spring Modulith ApplicationEventPublicationRegistry; RabbitMQ
or Kafka with--profile broker; @TransactionalEventListener(AFTER_COMMIT).

---

- **Weight:** 21
- **Event-coupled:** Y

---

## Later (after the 2 October agreement)

- **ADR:** [0008](../adr/0008-click-logged-broker.md)

### Acceptance criteria

- [ ] `ClickLoggedEvent` is externalized to the broker (`--profile broker`) after the redirect transaction commits.
- [ ] `GET /{hash}` still returns 307 when the broker is down.
- [ ] A repeated `eventId` is ingested once by the click log and by the stats counter.
- [ ] Weight 21 is not met by the in-process listener alone. The event must travel through the durable broker.

### Scale evidence

Not delivered in the 27 November prototype. The delivered Level 4 feature is [0006](0006-redirect_cache_cross_replica.md). The shared k6 run on 2026-10-17 had 0 failed checks.

### Qualities

| Quality | Assessed | How to test |
| --- | --- | --- |
| **Correctness** | 0 | Click ingestion is still the in-process seed listener. |
| **Scalability** | 0 | No broker-down or externalized-click run for this feature. |
| **Engineering** | 0 | ADR [0008](../adr/0008-click-logged-broker.md) is filed; the feature code is not implemented yet, so `./gradlew check` does not cover the broker path. |

**Indicative total:** 0 / 10

### AI disclosure

- **Tools / skills:** Cursor agent.
- **Used for:** writing these acceptance criteria. The feature itself is not implemented.
- **Human-reviewed:** pending.
