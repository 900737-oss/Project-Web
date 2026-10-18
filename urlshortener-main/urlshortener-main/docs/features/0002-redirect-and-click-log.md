# Feature: Redirect and click log

## Decision

**Owner module**: `links` (redirect) + `clicks` (log) | **Event-coupled**: Y (`ClickLoggedEvent`)

Look up a short URL by hash, return **307** to the target, and append a permanent click-log row via `ClickLoggedEvent` → `ClickRecorder`. Unknown hashes return **404**.

**Traps**: stateless redirect (no `HttpSession`); click logging must not block the redirect path; `ClickLoggedEvent` is an in-process event — cross-replica delivery requires Level 4.

**Technology hints**: `ClickRecorder` as `@ApplicationModuleListener`; `307 Temporary Redirect` with `Location` header; Spring Modulith event in `clicks` base package.

---

- **Weight:** 0 (seed — not in agreement)
- **Event-coupled:** Y

---

## Later

- **ADR:** [`docs/adr/0001-modulith-and-hexagon.md`](../adr/0001-modulith-and-hexagon.md), [`docs/adr/0002-atomic-upserts.md`](../adr/0002-atomic-upserts.md)

### Acceptance criteria

- [x] `GET /{hash}` → **307** with `Location` = target — `LinkFlowTests`
- [x] Unknown hash → **404** — `LinkFlowTests`
- [x] Successful redirect leads to click side-effect — `LinkFlowTests` reaches `totalClicks=1` (via analytics)
- [x] A redelivered `ClickLoggedEvent` (same `eventId`) is logged once — `RecordClickIdempotency(Postgres)Tests`

### Scale evidence

- [x] Level 3 — in-process `ClickLoggedEvent` event
- [x] `docker compose --profile load run --rm k6` on 2026-10-17: 5 VUs, 30s, 640 iterations, 1280 checks, 0 failed, `http_req_failed` 0%. Exit code 0.

### Qualities (self-assessed)

| Quality | Assessed | How to test |
| --- | --- | --- |
| **Correctness** | **5** | `LinkFlowTests`: 307 with `Location`, unknown hash → 404, and the click side-effect via stats. |
| **Scalability** | **2** | k6 through the load balancer: 0 failed checks and 0% HTTP failures. |
| **Engineering** | **1.5** | ADR `0001` + `ModularityTests` green; `./gradlew check` green; AI disclosure present. |

**Indicative total:** 5 + 2 + 1.5 = **8.5 / 10**.

### AI disclosure

- **Used for:** Adapting the seed feature from 25-26 course code to the 26-27 course code.
- **Human-reviewed:** The instructor reviewed the code and verified the correctness of the adaptation.
- **Cursor agent (Claude):** `eventId` on `ClickLoggedEvent`, idempotent `RecordClickService` (unique `click.event_id`), `RecordClickIdempotency(Postgres)Tests`.
