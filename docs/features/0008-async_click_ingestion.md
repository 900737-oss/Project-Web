# Feature: Async Click Ingestion via Outbox → Broker

## Decision

**Owner module**: clicks | **Event-coupled**: Y

Would forward `ClickLoggedEvent` to an external broker. The type lives in `clicks`.
links already publishes it from LinkService on redirect. @ApplicationModuleListener and the
JDBC Event Publication Registry (bootstrapped in the seed) already run the click write off the
redirect thread. This feature externalises that in-process publication. Consumers are idempotent.

**Traps**:  the seed publication is in-process — the broker path is a structural change (the Compose
RabbitMQ is already wired for preview and link-update events, but this feature has not yet
externalized `ClickLoggedEvent`; broker-down must leave the 307 in place; at-least-once delivery
needs idempotency on the consumer (`eventId` already exists for the in-process listeners).

**Technology hints**: Spring Modulith ApplicationEventPublicationRegistry; RabbitMQ or Kafka;
`@TransactionalEventListener(AFTER_COMMIT)`.

---

- **Weight:** 21
- **Event-coupled:** Y

---

## Later (after the 2 October agreement)

- **ADR:** [0008](../adr/0008-click-logged-broker.md)

### Acceptance criteria

- [x] `ClickLoggedEvent` is externalized to the broker after the redirect transaction commits.
- [x] `GET /{hash}` still returns 307 when the broker is down.
- [x] A repeated `eventId` is ingested once by the click log and by the stats counter.
- [x] Weight 21 is not met by the in-process listener alone. The event must travel through the durable broker.

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

- [ ] The same `eventId` leaves `totalClicks` at 1, including when the redirect and the stats read hit different replicas

#### Events

- [x] One redirect increases `totalClicks` by 1. Delivering the same `eventId` twice leaves the count unchanged
