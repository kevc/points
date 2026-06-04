# Points

Count arbitrary things and see how the count changes over time.

Track anything by count — days since quitting a habit, times you meditated, times you got angry —
with an **offline-first** experience that syncs across devices and stays additive even when you
increment on multiple devices while offline.

## Status

Early scaffolding (Milestone 1). See [issues](https://github.com/kevc/points/issues) for the roadmap.

## Architecture

Kotlin Multiplatform monorepo with a strong shared core and thin, native frontends.

```
core/
  domain/        Pure-Kotlin entities, ports, and fun-interface use cases (no platform deps)
  database/      SQLDelight — the local append-only event ledger
  network/       Ktor client + API service
  data/          Repository implementations + offline-first sync
  presentation/  Decompose components + MVIKotlin stores (shared UI logic)
shared/
  contract/      Wire DTOs shared between client and backend
apps/
  android/       Android app (Jetpack Compose)
  ios/           iOS app (SwiftUI, consuming the shared framework via SKIE)
backend/         Ktor server (H2 in dev, PostgreSQL in prod)
```

**Offline-first model:** every increment/decrement is an immutable, client-UUID-keyed event in an
append-only ledger. The current value is the sum of deltas, so concurrent offline edits merge
conflict-free (a PN-counter style CRDT).

## Contributing / conventions

See [`CLAUDE.md`](./CLAUDE.md) for the architecture, DI, testing, and commit conventions used in this repo.
