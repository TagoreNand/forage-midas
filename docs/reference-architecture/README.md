# Reference Architecture — runnable scaffolding

These files are the **target hexagonal/DDD implementation** referenced throughout
[`ARCHITECTURE.md`](../../ARCHITECTURE.md) and [`ROADMAP.md`](../../ROADMAP.md). They are
deliberately placed **outside `src/main/java`** so the existing build stays green — they
are promoted into the source tree phase-by-phase rather than dropped in as a big bang.

## Why they live here (not in `src/main/java`)

They depend on Phase-2+ wiring (Redis, the outbox, optimistic locking columns) that isn't
configured yet. Committing them straight into the build path would either break compilation
or change the runtime behaviour of the Forage tests. Keeping them as reviewable reference
code lets the design be inspected now and adopted incrementally.

## Dependency direction (the rule the architecture enforces)

```
adapter.in ─▶ application.port.in ─▶ application.service ─▶ domain
                                          │
adapter.out ◀─ application.port.out ◀─────┘
```

`domain/` imports **nothing** from Spring, JPA, or Kafka. Adapters depend inward. An
ArchUnit test (Phase 1) fails the build if this is ever violated.

## How to promote (maps to ROADMAP phases)

| Promote in | Files |
|---|---|
| **Phase 1** (money + ledger) | `domain/model/Money.java`, `Account.java`, `Transfer.java`, `ledger/LedgerEntry.java`, `domain/event/TransferRecorded.java` |
| **Phase 1** (hexagon seams) | `application/port/**`, `application/service/ProcessTransferService.java` |
| **Phase 2** (ingest) | `adapter/in/messaging/TransactionKafkaListener.java`, `adapter/out/persistence/AccountJpaAdapter.java` |
| **Phase 3** (resilience) | `adapter/out/incentive/IncentiveRestAdapter.java` |
| **Phase 4** (read API) | `adapter/in/web/BalanceController.java` |

Each file is heavily commented to explain the pattern it demonstrates and the baseline
finding (F1–F16) it resolves.
